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

/**
 * Portico 中的消息大致分为两类：数据消息和控制消息。
 * <p/>
 * 
 * <b>控制消息</b>
 * <p/>
 * 是仅在单个联邦成员与 RTI 之间（任一方向）交换的消息。<br>
 * 这类消息始终使用可靠的传输方式，在语义上可视为点对点通信。其在网络上的具体实现方式取决于连接实现的具体细节。<br>
 * <p/>
 * 
 * <b>数据消息</b>
 * <p/>
 * 被定义为“高容量”的载荷消息。它在语义上更像是一种广播。<br>
 * 属性更新和反射是最常见（目前也是唯一）的数据消息类型。定义此类型是为了让连接能够高效地路由和过滤需要大量发送的消息，因为这些消息对性能的影响最大。<br>
 * <p/>
 * 
 * <b>通知</b>
 * <p/>
 * 实质上是一种异步控制消息（不需要响应）。<br>
 * 它通常发往联邦的一个子集，最常见的是仅发给一个联邦成员。通常也是由 RTI 发送给联邦成员，用于表示诸如发现通知等事件。<br>
 * 
 * 此枚举标识了正在发送的消息类型：<br>
 * <ul>
 * <li><b>DataMessage</b>: 应广播给所有参与者的消息</li>
 * <li><b>Notification</b>: 不需要响应的单向消息（通常是 RTI -> 联邦成员）</li>
 * <li><b>ControlRequest</b>: 需要响应的控制请求消息（通常是 联邦成员 -> RTI）</li>
 * <li><b>ControlResponseOK</b>: 对控制请求的成功响应</li>
 * <li><b>ControlResponseErr</b>:对控制请求的错误响应。</li>
 * </ul>
 * 
 */
public enum CallType
{
	//----------------------------------------------------------
	//                        VALUES
	//----------------------------------------------------------
	DataMessage (0),
	Notification(1),
	ControlRequest(2),
	ControlResponseOK(3),
	ControlResponseErr(4);
	
	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private int id; // Marshalling limit is 8!

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	private CallType( int id )
	{
		this.id = id;
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	public final int getId()
	{
		return id;
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
	public static CallType fromId( int id )
	{
		switch( id )
		{
			case 0: return DataMessage;
			case 1: return Notification;
			case 2: return ControlRequest;
			case 3: return ControlResponseOK;
			case 4: return ControlResponseErr;
			default: throw new IllegalArgumentException( "CallType id not known: "+id );
		}
	}
}
