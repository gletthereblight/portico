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

import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.compat.JRTIinternalError;
import org.portico2.common.network.configuration.protocol.ProtocolConfiguration;
import org.portico2.common.network.protocol.Protocol;
import org.portico2.common.network.transport.Transport;

/**
 * 负责管理其所包含的一组 {@link Protocol} 实现，确保每个协议组件都与其上下相邻的组件正确连接。
 * <p/>
 * 
 * 该类会确保栈中的最后一个组件<i>always</i>是此连接加载的 {@link Transport}。
 * <p/>
 * 
 * 它有两个数据流向：<br>
 * - down() 从 Application 到 Transport<br>
 * - up() 从 Transport 到 Application<br>
 * 
 * <pre>
 *   Application   | 
 *   Protocol #1   | 
 *   Protocol #2   |
 *   Transport     V
 * </pre>
 * 
 * 发送到网络的消息会沿着栈“向下”传递，按此处声明的顺序依次经过每个协议。<br>
 * 当从传输层接收到消息时，消息会沿着栈“向上”传递，消息会以相反的顺序（从栈底到栈顶）传递给每个协议。<br>
 * <p/>
 * 要将消息发送到网络，请将其传递给 {@link #down(Message)} 方法。<br>
 * 要将消息向上传递，请将其传递给 {@link #up(Message)} 方法。<br>
 */
public class ProtocolStack
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private Connection connection;
	private Protocol first;
	private Transport last;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	protected ProtocolStack( Connection connection )
	{
		this.connection = connection;
		this.first = new Connector();
		this.last = connection.transport;

		// Make it so that the connector and the transport cross-reference each other
		this.first.setNext( this.last );
		this.last.setPrevious( this.first );
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	////////////////////////////////////////////////////////////////////////////////////////
	///  Lifecycle Management Methods   ////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public void open() throws JRTIinternalError
	{
		Protocol current = this.first;
		do
		{
			if( current instanceof Transport == false )
				current.open();
			
			current = current.next();
		}
		while( current != null );
	}
	
	public void close()
	{
		Protocol current = this.first;
		do
		{
			if( current instanceof Transport == false )
			{
				try
				{
					current.close();
				}
				catch( Exception e )
				{
					connection.getLogger().warn( "Exception while closing protocol "+
					                             current.getName()+": "+e.getMessage(), e );					
				}
			}
			
			current = current.next();
		}
		while( current != null );
	}

	////////////////////////////////////////////////////////////////////////////////////////
	///  Message Management Methods   //////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public final void down( Message message )
	{
		first.down( message );
	}
	
	public void up( Message message )
	{
		last.up( message );
	}

	////////////////////////////////////////////////////////////////////////////////////////
	///  Protocol Management Methods   /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public void addBefore( Protocol protocol, Protocol aheadOf )
	{
		if( aheadOf == first )
			throw new JConfigurationException( "Cannot add protocol before implicit \"Application\"" );
		
		// check to make sure we don't already have a protocol by this name
		// and check to make sure we do have the one we want to add before
		Protocol current = this.first;
		boolean foundTarget = false;
		do
		{
			if( current == aheadOf )
				foundTarget = true;
			
			if( current.getName().equalsIgnoreCase(protocol.getName()) )
				throw new JConfigurationException( "Already have instance of protocol in stack: %s", protocol.getName() );
			
			current = current.next();
		}
		while( current.hasNext() );
		
		// make sure we have the protocol we're adding before
		if( foundTarget == false )
			throw new JConfigurationException( "Could not find protocol we are meant to add before: "+aheadOf );
		
		// slot the new one in before the given one
		Protocol after = aheadOf.next();
		protocol.setPrevious( aheadOf );  // give the new protocol its link to the one above it
		protocol.setNext( after );        // give the new protocol the link to the last in line
		aheadOf.setNext( protocol );      // tell the protocol above us that we are its next stop
		after.setPrevious( protocol );    // tell the transport that the new protocol is now above it
	}
	
	/**
	 * Adds the given protocol to the end of the stack, directly ahead of the Transport.
	 * This will also 
	 * 
	 * @param protocol The protocol implementation to add
	 * @throws JConfigurationException 
	 */
	public void addProtocol( Protocol protocol ) throws JConfigurationException
	{
		// check to amke sure we don't have this protocol already
		Protocol current = this.first;
		do
		{
			if( current.getName().equalsIgnoreCase(protocol.getName()) )
				throw new JConfigurationException( "Already have instance of protocol in stack: %s", protocol.getName() );
			
			current = current.next();
		}
		while( current != null );
		
		// poen the protocol if it isn't already
		if( connection.transport.isOpen() )
			protocol.open();

		// get reference to protocol that should be above us (the one last currently points to)
		Protocol above = last.previous();
		
		// link the up/down directions for the incoming protocol
		protocol.setPrevious( above );  // give the new protocol its link to the one above it
		protocol.setNext( last );       // give the new protocol the link to the last in line
		above.setNext( protocol );      // tell the protocol above us that we are its next stop
		last.setPrevious( protocol );   // tell the transport that the new protocol is now above it
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	///  Accessors and Mutators   //////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
	private final class Connector extends Protocol
	{
		public void open()  {}
		public void close() {}
		public String getName() { return "ApplicationConnector"; }
		protected void doConfigure( ProtocolConfiguration configuration, Connection hostConnection ) {}

		public final void down( Message message )
		{
			passDown( message );
		}

		public final void up( Message message )
		{
			// pass the message back to the main Connection class where
			// it can forward on to the AppReceiver
			connection.receive( message );
		}
	}
	
}
