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
package org.portico2.common.network.protocol;

import org.apache.logging.log4j.Logger;
import org.portico.lrc.compat.JConfigurationException;
import org.portico2.common.network.Connection;
import org.portico2.common.network.Message;
import org.portico2.common.network.ProtocolStack;
import org.portico2.common.network.configuration.protocol.ProtocolConfiguration;
import org.portico2.common.network.transport.Transport;

/**
 * {@link Protocol} 的实现位于一个 {@link ProtocolStack} 内部，而 {@link ProtocolStack} 又位于 一个 {@link Connection} 对象内部。<br>
 * 在栈中，Protocol 对象以链式结构排列， 每个对象都与下一个（向前和向后）对象相连。
 * </p>
 * 
 * 每个 Protocol 都连接到栈中的下一个和上一个实现，以便能够将消息向上或向下传递。<br>
 * 如果某个协议希望终止对某条消息的处理，它可以简单地不再将该消息继续传递。<br>
 * 它还可以生成额外的内部消息，以支持来自主机连接的请求（自动操作），甚至支持协议间的通信。
 * </p>
 * 
 * {@link Protocol} 实现应使用 {@link #passDown(Message)} 和 {@link #passUp(Message)} 方法来转发消息。这些方法会正确地处理前后节点的引用关系。
 * </p>
 * 
 * 
 * 因此，当从应用层接收到消息时，它会被传递给 ProtocolStack，ProtocolStack 再将其传递给其包含的第一个协议。 该协议处理完消息后，通过 {@link #passDown(Message)}
 * 将其沿链向下传递，如此往复，直到消息到达传输层并被发送到网络上。
 * <p/>
 * 
 * 反向过程也是如此。当从 {@link Transport} 接收到消息时，它会沿着栈向上传递，首先交给传输层之前的协议， 该协议再将其传递给前一个协议，依此类推。
 * </p>
 */
public abstract class Protocol
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	protected Connection hostConnection;
	protected Connection.Host hostType;
	protected Logger logger;
	
	private Protocol previous;
	private Protocol next;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	protected Protocol()
	{
		this.hostConnection = null;   // set in configure()
		this.hostType = null;         // set in configure()
		this.logger = null;           // set in configure()

		this.previous = null;         // set when added to ProtocolStack
		this.next = null;             // set when added to ProtocolStack
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	////////////////////////////////////////////////////////////////////////////////////////
	///  Lifecycle Management   ////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public final void configure( ProtocolConfiguration configuration, Connection hostConnection )
	{
		this.hostConnection = hostConnection;
		this.logger = hostConnection.getLogger();
		this.hostType = hostConnection.getHost();
		this.doConfigure( configuration, hostConnection );
	}

    /**
     * 此方法应在所有子类型中被重写，但永远不会被外部代码直接调用。<br>
     * 但是它会被 {@link #configure(ProtocolConfiguration, Connection)}
     * 调用，该方法首先提取对所有类型通用的必要配置，然后将执行流程传递给此方法，使每个具体协议有机会进行自身的配置。<br>
     *
     * @param configuration 配置对象，预期会被强制转换为某个子类型
     * @param hostConnection 该协议将被部署到的连接对象。也可由此获知我们当前处于LRC、RTI还是转发器中。
     * @throws JConfigurationException 如果提供的配置数据存在任何问题
     */
	protected abstract void doConfigure( ProtocolConfiguration configuration, Connection hostConnection )
	    throws JConfigurationException;

	/**
	 * 连接正在打开，因此 protocol 需要根据其配置确保自身已正确初始化和设置。
	 */
	public abstract void open();
	
	/**
	 * 连接正在关闭，此时开始进行常规的清理工作，如关闭已打开的附加连接、关闭线程或文件等。
	 */
	public abstract void close();

	////////////////////////////////////////////////////////////////////////////////////////
	///  Message Passing   /////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * A message has been received from the host component and is being passed down towards
	 * the network.
	 * 
	 * @param message The message we received
	 */
	public abstract void down( Message message );
	
	/**
	 * A message has been received from the network and is being passed up towards the host.
	 * 
	 * @param message The message that was recieved.
	 */
	public abstract void up( Message message );


	protected final void passUp( Message message )
	{
		previous.up( message );
	}
	
	protected final void passDown( Message message )
	{
		next.down( message );
	}

	////////////////////////////////////////////////////////////////////////////////////////
	///  Accessors and Mutators   //////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public abstract String getName();
	
	// Lets the protocol record who is before and after it 
	public void setNext( Protocol next ) { this.next = next; }
	public void setPrevious( Protocol previous ) { this.previous = previous; }
	public final Protocol next()     { return this.next; }
	public final Protocol previous() { return this.previous; }
	
	public final boolean hasNext() { return this.next != null; }


	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
