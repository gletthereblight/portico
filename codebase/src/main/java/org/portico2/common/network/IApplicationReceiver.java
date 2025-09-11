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

import org.apache.logging.log4j.Logger;
import org.portico.lrc.compat.JException;
import org.portico.lrc.compat.JRTIinternalError;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.messaging.MessageContext;

/**
 * 该组件将从 {@link Connection} 接收数据消息和控制消息。
 */
public interface IApplicationReceiver
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	/**
	 * The application ultimately represents the context that a connection sits within.
	 * As such, we often want the connection framework to use the same logging infrastructure.
	 * This method lets the application provide a logger to a connection.
	 * 
	 * @return The logger that the connection framework should use
	 */
	public Logger getLogger();

	///////////////////////////////////////////////////////////////////////////////////////
	///  Message RECEIVING methods   //////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 对于某些连接类型，所有消息都以广播方式交换。<br>
     * 在这种情况下，RTI/LRC 可能会接收到并非发给它的消息。<br>
     * 我们希望尽早过滤掉这些消息。为此，可以让每个接收器预先批准消息，以便尽早将其丢弃。<br>
     * 此方法告知连接自身的 ID，连接可利用该 ID 辅助过滤。<br>
     * <p/>
     * 
     * 每个 {@link PorticoMessage} 都有一个目标值，可用于过滤。常见的取值包括：
     * <p/>
     * 
     * <ul>
     * <li>{@link PorticoConstants#RTI_HANDLE}: 消息发给 RTI</li>
     * <li>{@link PorticoConstants#TARGET_ALL_HANDLE}: 消息发给所有人</li>
     * <li>{@link PorticoConstants#TARGET_MANY_HANDLE}: 消息发给部分而非全部</li>
     * </ul>
     * 
     * 注意，此过滤仅适用于控制消息（ControlMessages）。
     * 
     * @param header 消息头
     * @returns 如果应处理该消息则返回 True，否则返回 False
     */
	public boolean isReceivable( Header header );

    /**
     * 从连接的另一端接收到了一条控制消息 {@link CallType}。<br>
     * 此消息需要响应，请填充提供的消息上下文对象，然后返回。<br>
     * 
     * @param context 包含请求的消息上下文对象
     * @throws JRTIinternalError
     */
	public void receiveControlRequest( MessageContext context ) throws JRTIinternalError;

    /**
     * 通知 {@link CallType#Notification} 是一种异步控制请求。与数据消息不同，它不会广播给所有人，也不应通过牺牲可靠性来换取速度的路径传输。<br>
     * 此类消息通常用于 RTI 发送给联邦成员（或多个联邦成员）的重要通知调用，例如对象发现回调（Object Discovery Callbacks）、同步点通知（sync point
     * notifications）、时间推进授权（time advance grants）等。<br>
     * 
     * @param message 收到的通知消息
     * @throws JRTIinternalError
     * 
     */
    public void receiveNotification( PorticoMessage message ) throws JRTIinternalError;
	
    /**
     * 从连接接收到一条广播/数据消息 {@link CallType#DataMessage}，应对其进行处理。<br>
     * 
     * 广播消息旨在发送给联邦内的所有联邦成员。尽管它们可能通过 RTI 进行路由，但它们不属于控制消息。<br>
     * 
     * 目前，它们的使用仅限于属性反射和交互。尽管这类消息只占所有可用消息的一小部分，但在任何特定的联邦中，它们通常会构成交换消息总量的绝大部分。
     * 
     * @param message 要发送给所有其他联邦成员的消息
     * @throws JException
     */
	public void receiveDataMessage( PorticoMessage message ) throws JException;
	
}
