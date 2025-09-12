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
package org.portico2.common.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.compat.JRTIinternalError;

/**
 * 核心的消息处理组件，负责接收、路由和处理各种类型的消息.
 * 
 * @author gaop
 * @date 2025/09/12
 */
public class MessageSink
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private String name;
	private Logger logger;

	private Map<MessageType,IMessageHandler> messageHandlers;
	private Set<MessageType> exclusive;
	private IMessageHandler defaultHandler;
	
	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public MessageSink( String name, Logger logger )
	{
		this.name = name;
		this.logger = logger;
		
		this.messageHandlers = new HashMap<>();
		this.exclusive = new HashSet<>();
		this.defaultHandler = new DefaultHandler();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
    /**
     * 消息处理和路由，当收到消息时，它会查找对应的 IMessageHandler，如果找不到则使用默认处理器 {@link DefaultHandler}
     * 
     * @param context
     */
    public void process(MessageContext context) {
        IMessageHandler handler = messageHandlers.get(context.getRequest().getType());
        if (handler == null)
            defaultHandler.process(context);
        else
            handler.process(context);
    }
	
	///////////////////////////////////////////////////////////////////////////////////////
	///  Handler Management Methods   /////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 调用时，将遍历其包含的所有 {@link IMessageHandler}，并调用其 {@link IMessageHandler#configure(Map)} 方法，传入给定的属性集合。
     *
     * @param properties 要传递给处理器的属性
     * @throws JConfigurationException 如果配置任何处理器时发生问题
     */
	public void configure( Map<String,Object> properties ) throws JConfigurationException
	{
		for( IMessageHandler handler : messageHandlers.values() )
			handler.configure( properties );
	}
	
    /**
     * 注册指定的处理器，用于处理特定{@link MessageType}类型的消息。<br>
     * 如果该消息类型已存在处理器，则会创建一个链式结构，使得每条消息将按照处理器的添加顺序依次传递给所有处理器。<br>
     *
     * @param type 处理器所希望处理的消息类型
     * @param handler 要使用的处理器
     * @throws JRTIinternalError 如果该消息类型已存在一个被注册为独占的处理器
     */
	public void register( MessageType type, IMessageHandler handler )
		throws JRTIinternalError
	{
		if( messageHandlers.containsKey(type) )
		{
			// Has someone got exclusive access?
			if( exclusive.contains(type) )
			{
				throw new JRTIinternalError( "Cannot add hander [%s]: [%s] has exclusive access for messages of type [%s]",
				                             handler.getName(), type, messageHandlers.get(type).getName() );
			}

			// If the existing handler is a list, just extend it
			// If it is a single handler, turn it into a list
			IMessageHandler existing = messageHandlers.get( type );
			if( existing instanceof ListHandler )
				ListHandler.class.cast(existing).handlers.add( existing );
			else
				messageHandlers.put( type, new ListHandler(existing,handler) );
		}
		else
		{
			this.messageHandlers.put( type, handler );
		}
	}

    /**
     * 注册指定的处理器来处理特定类型的消息，并授予该处理器对该消息类型的独占访问权。<br>
     * 如果该类型的消息已经注册了其他处理器，则会抛出异常。<br>
     * 如果之后有人尝试为已存在独占处理器的消息类型添加新的处理器，同样会抛出异常。<br>
     *
     * @param type 处理器所希望处理的消息类型
     * @param handler 要使用的处理器
     * @throws JRTIinternalError 如果该消息类型已注册了处理器，因此无法获得独占访问权
     */
	public void registerExclusive( MessageType type, IMessageHandler handler )
		throws JRTIinternalError
	{
		if( messageHandlers.containsKey(type) )
		{
			throw new JRTIinternalError( "Cannot give exclusive access of type [%s] to handler [%s]: another handler has already registered (%s)",
			                             type, handler.getName(), messageHandlers.get(type).getName() );
		}
		else
		{
			messageHandlers.put( type, handler );
			exclusive.add( type );
		}
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
	
	///////////////////////////////////////////////////////////////////////////////////////
	///  Private Inner Class: DefaultHandler   ////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
	private class DefaultHandler implements IMessageHandler
	{
		@Override public String getName() { return "DefaultHandler"; }
		@Override public void configure( Map<String,Object> properties ){}
		@Override public void process( MessageContext context ) throws JRTIinternalError
		{
			logger.warn( "(sink: %s) IGNORE MESSSAGE. No handler for type: %s",
			             name, context.getRequest().getType() );
			
			// TODO Do we need to throw an exception?
			//throw new JRTIinternalError( "(sink: %s) No handler for type: %s",
			//                             name, context.getRequest().getType() );
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	///  Private Inner Class: ListHandler   ///////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This class is used whenever someone tries to register more than one handler for a type
	 * of message. It holds a list of handlers which it passes each message to in order. The
	 * order is the order in which the handlers were registered.
	 * <p/>
	 * If any of the handlers throws an exception, an exception will be placed into the message
	 * context response slot and processing will stop. The only exception to this is a
	 * 
	 */
	private class ListHandler implements IMessageHandler
	{
		private List<IMessageHandler> handlers;
		
		public ListHandler( IMessageHandler... handlers )
		{
			this.handlers = new ArrayList<>();
			for( IMessageHandler handler : handlers )
				this.handlers.add( handler );
		}
		
		@Override public String getName() { return "HandlerList"; }
		@Override public void configure( Map<String,Object> properties )
		{
			handlers.forEach( handler -> handler.configure(properties) );
		}

		@Override public void process( MessageContext context ) throws JRTIinternalError
		{
			for( IMessageHandler handler : handlers )
			{
				try
				{
					handler.process( context );
				}
				catch( VetoException ve )
				{
					if( logger.isTraceEnabled() )
					{
    					logger.trace( "Message [%s] veto'd by handler [%s]: %s",
    					              context.getRequest().getType(),
    					              handler.getName(),
    					              ve.getMessage() );
					}
					
					// Processing has been veto'd. If there is no response, fill with a success. 
					// The veto is an intentional action, not an error
					if( context.hasResponse() == false )
						context.success();
					return;
				}
			}
		}
	}
	
}
