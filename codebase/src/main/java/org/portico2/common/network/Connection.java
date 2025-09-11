/*
 *   Copyright 2018 The Portico Project
 *
 *   This file is part of portico.
 *
 *   portico is free software; you can redistribute it and/or modify
 *   it under the terms of the Common Developer and Distribution License (CDDL) 
 *   as published by Sun Microsystems. For more information see the LICENSE file.
 *   
 *   Use of this software is strictly AT YOUR OWN RISK!!!
 *   If something bad happens you do not have permission to come crying to me.
 *   (that goes for your lawyer as well)
 *
 */
package org.portico2.common.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.compat.JException;
import org.portico.lrc.compat.JRTIinternalError;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.messaging.MessageContext;
import org.portico2.common.messaging.ResponseMessage;
import org.portico2.common.network.configuration.ConnectionConfiguration;
import org.portico2.common.network.configuration.protocol.ProtocolConfiguration;
import org.portico2.common.network.protocol.Protocol;
import org.portico2.common.network.protocol.ProtocolFactory;
import org.portico2.common.network.protocol.ProtocolType;
import org.portico2.common.network.transport.Transport;
import org.portico2.common.services.federation.msg.RtiProbe;

/**
 * 是 Portico 框架其余部分与网络协议栈交互的主要接口。每个实例包含：<br>
 * 
 * <ul>
 * <li>一个 {@link ProtocolStack}: 负责处理发送和接收过程中的消息。</li>
 * <li>一个 {@link Transport}: 实际进行原始数据收发的网络组件。</li>
 * <li>其他杂项（例如，用于关联请求与响应的组件）...</li>
 * </ul>
 * </p>
 * 
 * <b>发送消息</b> <br>
 * 当有消息交给连接对象用于发送时，它会将消息沿协议栈 <i>向下</i> 传递，使每个 {@link Protocol} 都有机会处理该消息。<br>
 * 此过程结束后，消息会被推送到 {@link Transport} 进行实际发送。
 * </p>
 * 
 * <b>接收消息</b> <br>
 * 当从底层的 {@link Transport} 接收到消息时，它会沿协议栈 <i>向上</i> 传递，直到被连接对象接收。<br>
 * 如果该消息是一个控制响应，连接会尝试将其与任何未完成的请求进行匹配。<br>
 * 如果属于其他类型， 消息将被传递给 {@link IApplicationReceiver}，由 RTI 进行后续处理。<br>
 */
public class Connection
{
	//----------------------------------------------------------
	//                      ENUMERATIONS
	//----------------------------------------------------------
    /**
     * 每个连接都位于某种组件之中。此枚举用于标识该连接所嵌入的宿主组件。
     */
	public enum Host{ LRC, RTI, Forwarder; }

    /**
     * 每个连接可以处于三种状态之一：未连接到任何对象、已连接到RTI但尚未加入，或已连接并已加入。
     */
    public enum Status{ Disconnected, Connected, Joined; }

	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private Host host;
	private Object hostReference;
	private Status status;  // value must be set and managed _EXTERNALLY_ by host type

	private String name;
	private ConnectionConfiguration configuration;
	private IApplicationReceiver appReceiver;
	private Logger logger;

	protected Transport transport;
	private ProtocolStack protocolStack;
	private ResponseCorrelator<ResponseMessage> responseCorrelator;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
    /**
     * 创建一个新的连接，该连接将位于由 {@link Host} 枚举标识的宿主类型内部。<br>
     * 此外，我们还会传入对宿主对象本身的引用。<br>
     * 任何确实需要回访宿主的特殊操作，都可以使用 <code>Host.cast()</code> 方法将此引用强制转换为适当的类型。<br>
     * 
     * @param host 所属的宿主类型
     * @param hostReference 对宿主对象本身的引用
     */
	public Connection( Host host, Object hostReference )
	{
		this.host = host;
		this.hostReference = hostReference;
		this.status = Status.Disconnected;
		this.name = "unknown";       // set in configure()
		this.configuration = null;   // set in configure()
		this.appReceiver = null;     // set in configure()
		this.logger = null;          // set in configure()

		this.transport = null;       // set in configure()
		this.protocolStack = null;   // set in configure()
		this.responseCorrelator = new ResponseCorrelator<>();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	///////////////////////////////////////////////////////////////////////////////////////
	///  Connection Lifecycle Methods   ///////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 使用给定的配置数据对连接进行配置，并将其与提供的接收器关联。<br>
     * 
     * @param configuration 从RID中提取的配置数据
     * @param receiver 应接收来自网络介质的传入消息的组件
     * @throws JConfigurationException
     */
	public void configure( ConnectionConfiguration configuration, IApplicationReceiver appReceiver )
		throws JConfigurationException
	{
		// extract the properties we need to store
		this.name = configuration.getName();
		this.configuration = configuration;
		this.appReceiver = appReceiver;
		//this.logger = appReceiver.getLogger();
		this.logger = LogManager.getFormatterLogger( appReceiver.getLogger().getName()+"."+name );
		
		// create the transport
		this.transport = configuration.getTransportConfiguration().getTransportType().newTransport();
		this.transport.configure( configuration.getTransportConfiguration(), this );
		
		/////////////////////////////////
		// populate the protocol stack //
		/////////////////////////////////
		this.protocolStack = new ProtocolStack( this );
		for( ProtocolConfiguration config : configuration.getProtocolStackConfiguration().getProtocolList() )
		{
			ProtocolType type = config.getProtocolType();
			Protocol protocol = ProtocolFactory.instance().createProtocol( type, host );
			protocol.configure( config, this );
			protocolStack.addProtocol( protocol );
		}
	}

    /**
     * 当连接需要建立自身并开始处理时，将调用此方法。<br>
     * 在调用 {@link #connect()} 之前，连接应已完成配置但处于非活动状态。<br>
     * 
     * @throws JRTIinternalError If there is a problem encountered during connection
     */
	public void connect() throws JRTIinternalError
	{
		// log some initialization information
		logger.debug( "Opening connection [%s]", name );
		//logger.debug( BufferInformation );
		//logger.debug( ProtocolStack );
		//logger.debug( "Transport: "+this.transport.getType() );
		
		// tell the protocols that we're starting up
		logger.trace( "Opening protocol stack" );
		this.protocolStack.open();
		
		// open the transport and let the messages flow!
		logger.trace( "Opening transport [%s/%s]", name, this.transport.getType() );
		this.transport.open();
		logger.trace( "Transport is now open [%s/%s]", name, this.transport.getType() );
	}
	
    /**
     * 当所有者正在关闭时调用。所有活动的连接都应被关闭。
     * 
     * @throws JRTIinternalError
     */
	public void disconnect() throws JRTIinternalError
	{
		logger.debug( "Disconnecting..." );

		logger.trace( "Closing transport: %s", this.transport.getType() );
		this.transport.close();
	}

	/**
	 * 此方法将发送一个 {@link RtiProbe}。如果收到响应，则说明存在一个RTI；如果没有收到响应，则说明不存在RTI。
	 * 
	 * @return <code>true</code> if there is an RTI out there; <code>false</code> otherwise
	 */
	public boolean findRti()
	{
		MessageContext context = new MessageContext( new RtiProbe() );
		sendControlRequest( context );
		return context.isSuccessResponse();
	}

	///////////////////////////////////////////////////////////////////////////////////////
	///  Message SENDING methods   ////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 数据消息旨在发送给联邦内的所有联邦成员。<br>
     * 尽管它们可能通过RTI进行路由，但它们不属于控制消息。<br>
     * 数据消息不需要也不期望收到响应。<br>
     * <p/>
     * 目前，它们的使用仅限于属性反射和交互。<br>
     * 尽管这类消息只占所有可用消息的一小部分，但在任何特定的联邦中，它们通常会构成交换消息总量的绝大部分，因此常常可以使用比控制消息更快的网络路径。<br>
     * 
     * @param message 要发送给所有其他联邦成员的消息
     * @throws JException
     */
	public void sendDataMessage( PorticoMessage message ) throws JException
	{
		Message outgoing = new Message( message, CallType.DataMessage, 0 );
		protocolStack.down( outgoing );
	}

    /**
     * 构建一个通知消息并将其沿协议栈向下发送至传输层。<br>
     * 该消息将携带调用类型 {@link CallType#Notification}，以表明它是一个不需要响应的控制消息。<br>
     * 
     * @param message  要发送出去的消息，需填充目标联邦成员及任何其他适当的信息
     * @throws JException
     */
	public void sendNotification( PorticoMessage message ) throws JException
	{
		Message outgoing = new Message( message, CallType.Notification, 0 );
		protocolStack.down( outgoing );
	}
	
    /**
     * 控制消息是需要响应的消息。<br>
     * 当某个组件想要发送控制消息时，会将其打包到一个 {@link MessageContext} 中，并传递给连接。<br>
     * 此调用在等待响应期间会阻塞。在该调用结束时，响应应被填充到 {@link MessageContext} 中。<br>
     * 
     * @param context Contains the request. Response will be put in here.
     * @throws JRTIinternalError If there is a problem processing the request.
     */
	public void sendControlRequest( MessageContext context ) throws JRTIinternalError
	{
		// FIXME -- REMOVE ME -- Left here on purpose to flag an issue.
		if( context.getRequest().isAsync() )
			throw new JRTIinternalError( "Async Messages no longer supported - move to Notificatins" );
		
		// Get an ID for the request
		int requestId = responseCorrelator.register();
		
		// Send the message
		PorticoMessage request = context.getRequest();
		protocolStack.down( new Message(request,CallType.ControlRequest,requestId) );

		// Wait for the response
		ResponseMessage response = responseCorrelator.waitFor( requestId );

		// Package the response
		if( response != null )
			context.setResponse( response );
		else
			context.error( "No response received (request:"+request.getType()+") - RTI/Federates still running?" );
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	///  Message RECEIVING methods   //////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 处理已接收到的一条消息（并且已经沿协议栈向上传递完毕）。
     * 
     * @param message The message that was received.
     */
	protected void receive( Message message )
	{
		Header header = message.getHeader();
		switch( header.getCallType() )
		{
			case DataMessage:
				appReceiver.receiveDataMessage( message.inflateAsPorticoMessage() );
				break;
			case Notification:
				appReceiver.receiveNotification( message.inflateAsPorticoMessage() );
				break;
			case ControlRequest:
				receiveControlRequest( message );
				break;
			case ControlResponseOK:
			case ControlResponseErr:
				responseCorrelator.offer( header.getRequestId(), message.inflateAsResponse() );
				break;
			default: break;
		}
	}
	
	private final void receiveControlRequest( Message message )
	{
		// turn the message into a PorticoMessage
		PorticoMessage request = message.inflateAsPorticoMessage();
		
		// check to make sure we can actually work with this message
		if( appReceiver.isReceivable(message.getHeader()) == false )
			return;

		// fire the message to the receiver to see what happens
		MessageContext context = new MessageContext( request );
		appReceiver.receiveControlRequest( context );
		
		// check to make sure we got a response
		if( context.hasResponse() == false )
		{
			logger.warn( "No response received for Control Request "+request.getType() );
			return;
		}
		
		// if we need to return the response, do so now
		if( request.isAsync() == false )
		{
			// convert the message into a response (from the original request)
			message.deflateAndStoreResponse( context.getResponse() );
			protocolStack.down( message );
		}
	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////
	///  Accessors and Mutators   //////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public Host getHost()
	{
		return this.host;
	}
	
	public <T> T getHostReference( Class<T> clazz )
	{
		return clazz.cast( this.hostReference );
	}
	
	public ConnectionConfiguration getConfiguration()
	{
		return this.configuration;
	}

	public Logger getLogger()
	{
		return this.logger;
	}

	public ProtocolStack getProtocolStack()
	{
		return this.protocolStack;
	}

	public ResponseCorrelator<ResponseMessage> getResponseCorrelator()
	{
		return this.responseCorrelator;
	}

	public final Status getStatus()
	{
		return this.status;
	}
	
	public boolean isConnected()
	{
		return this.status != Status.Disconnected;
	}
	
	public boolean isJoined()
	{
		return this.status == Status.Joined;
	}
	
	public void setStatus( Status status )
	{
		this.status = status;
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
