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

import java.util.Map;

import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.compat.JException;
import org.portico2.lrc.LRC;
import org.portico2.rti.RTI;
import org.portico2.rti.federation.Federation;

/**
 * 消息处理器用于消费和处理 {@link PorticoMessage} 的子类对象，执行相应的操作。<br>
 * 这些操作可能包括不进行任何处理、对消息进行某种转换、根据消息中包含的信息采取相应动作、填充一个 {@link ResponseMessage} 等等）。
 * <p/>
 * 
 * <b>配置</b><br>
 * 消息处理器被用于 Portico 架构的所有主要基础设施组件中（如 RTI、LRC、Forwarder）。<br>
 * 创建处理器时，会调用其 {@link #configure(Map)} 方法，并传入对其它关键组件的引用，具体引用内容取决于处理器运行的位置。<br>
 * 例如，若处理器在 RTI 内部运行，则会传入 RTI 对象本身以及该处理器所服务的联邦名称。<br>
 * <br>
 * 每个配置项或组件引用都通过特定的 ID 进行绑定，这些 ID 均在此接口中声明为静态常量。<br>
 * 只需从传入的 Map 中按需获取对应的组件，确认其非空后保存即可。<br>
 * <p/>
 * 
 * <b>其他支持</b><br>
 * <b>建议：</b> 除非有充分理由，否则强烈建议所有消息处理器都继承自 RTI 或 LRC 特定的消息处理器基类， 它们提供了便利的方法来提取常用组件并将其缓存到本地。<br>
 * <b>注意：</b>所有 {@link IMessageHandler} 的实现类都应没有构造函数，或者提供一个公共的无参构造函数。 这是因为处理器实例通常会通过反射机制创建。<br>
 */
public interface IMessageHandler
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	/** 处理器所在的 {@link RTI} 对象被绑定的键 */
	public static final String KEY_RTI                   = "portico.rti";
	/** 处理器所在的 {@link Federation} 对象被绑定的键 */
	public static final String KEY_RTI_FEDERATION        = "portico.rti.federation";
	/** 此处理器所在的 {@link LRC} 对象被绑定的键 */
	public static final String KEY_LRC                   = "portico.lrc";

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
    /**
     * 用于表示此处理器的某种名称，通常用于日志记录。
     * 
     * @return
     */
	public String getName();

    /**
     * {@link IMessageHandler} 接口被用于所有关键的 Portico 组件中，无论是 RTI、LRC 还是 Forwarder。<br>
     * 因此，我们需要一种通用的方式来传入处理器可能需要的任何重要基础设施引用 （这些引用会根据处理器运行的位置而有所不同）。
     * <p/>
     * 给定的属性集合中，应包含对这些基础设施组件的引用，每个引用通过上述常量之一指定的键进行绑定。<br>
     * 处理器将使用这些键来获取引用，然后将其转换为所需的类型。<br>
     *
     * @param properties 属性集合，其中应包含我们所需的所有组件
     * @throws JConfigurationException 如果缺少必要的组件或使用这些组件时出现问题
     */
	public void configure( Map<String,Object> properties ) throws JConfigurationException;
	
    /**
     * 已接收到需要处理的消息。请求和/或响应信息包含在给定的 {@link MessageContext} 对象中。<br>
     * 请采取相应的处理操作，并根据需要抛出兼容性库中的任何异常。<br>
     *
     * @param context 请求和/或响应的持有者
     * @throws JException 如果处理消息时出现问题
     */
	public void process( MessageContext context ) throws JException;
}
