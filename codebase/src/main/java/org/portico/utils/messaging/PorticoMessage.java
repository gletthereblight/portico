/*
 *   Copyright 2009 The Portico Project
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
package org.portico.utils.messaging;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.portico.lrc.compat.JRTIinternalError;
import org.portico.lrc.utils.MessageHelpers;
import org.portico2.common.PorticoConstants;
import org.portico2.common.messaging.MessageType;

/**
 * 所有 Portico 请求消息的父类，所有通过 Portico 框架发送的消息都应继承此类。<br>
 * 它包含多个用于标识消息源和目标联邦成员、与消息关联的时间戳、是否需要立即处理（即消息不应被排队）等设置。<br>
 * 
 * <p/>
 * <b>广播消息与定向消息:</b>
 * <p/>
 * 如果消息的目标联邦成员被设置为 {@link PorticoConstants#NULL_HANDLE}，则该消息被视为广播消息。<br>
 * 这是所有消息的默认状态。这些消息将被发送给所有联邦成员，而不仅仅是某个特定成员。<br>
 * 
 * <p/>
 * <b>立即处理标志:</b>
 * <p/>
 * 处理传入消息的一种常见方法是将其在某处排队。<br>
 * 为了向任何消息处理组件（或根据具体实现，可能是连接）表明某条消息需要立即处理，可以通过 {@link #setImmediateProcessingFlag(boolean)} 方法设置“需要立即处理”标志。
 * 
 * <p/>
 * <b>实现说明:</b>
 * <p/>
 * 尽管此类没有实现 <code>java.io.Externalizable</code> 接口，但它确实提供了所需方法的实现，以便子类在需要时可以调用它们（从而无需手动处理父类的数据）。<br>
 * 此外，它还实现了私有的 {@link #writeObject} 和 {@link #readObject} 方法，以便在子类未实现 Externalizable 时实现更快的序列化。
 * 
 * @author gaop
 * @date 2025/09/10
 */
public abstract class PorticoMessage implements Serializable, Cloneable
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	private static final long serialVersionUID = 98121116105109L;
	
	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	protected int sourceFederate;
	protected int targetFederate;
	protected int targetFederation;
	protected boolean isFromRti; // true if this message was generated or forwarded by the RTI
	protected boolean immediate; // does this message require immediate processing?

	protected double timestamp;
	
	protected Set<Integer> multipleTargets; // only used if we have multiple targets for a message

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------

	protected PorticoMessage()
	{
		super();
		this.isFromRti = false;
		this.sourceFederate = PorticoConstants.NULL_HANDLE;
		this.targetFederate = PorticoConstants.NULL_HANDLE;
		this.targetFederation = PorticoConstants.NULL_HANDLE;
		this.timestamp = PorticoConstants.NULL_TIME;
		this.immediate = false;
		
		this.multipleTargets = null; // only set when it has to be
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	/**
	 * @return The {@link MessageType} that identifies this particular class of message.
	 */
	public abstract MessageType getType();

	/**
	 * Should this message be sent asynchornously? If it should, this will return true.
	 * At the moment, only messages sent FROM the RTI are async. For all other messages,
	 * if they are control messages they are synchronous; if they are data messages then
	 * they are asynchronous.
	 * 
	 * @return true if this messages should be sent asynchronously; false otherwise
	 */
	public boolean isAsync()
	{
		return this.isFromRti;
	}

    /**
     * 消息可以由 RTI 生成或通过 RTI 进行路由。<br>
     * 消息中的“源联邦成员（source federate）”字段通常用于表示消息的原始来源/发起者或“所有者”，以便识别联邦成员。<br>
     * 为了正确路由消息，消息中设有一个特殊的布尔标志，用于指示该消息是否经过 RTI 路由。<br>
     * 
     * @return 如果消息是通过RTI传来的，则返回true，否则返回false
     */
	public boolean isFromRti()
	{
		return this.isFromRti;
	}
	
	public void setIsFromRti( boolean fromRti )
	{
		this.isFromRti = fromRti;
		this.setSourceFederateIfNull( PorticoConstants.RTI_HANDLE );
		//this.sourceFederate = PorticoConstants.RTI_HANDLE;
	}
	
	/**
	 * Defaults to {@link PorticoConstants#NULL_HANDLE} unless otherwise set.
	 */
	public int getSourceFederate()
	{
		return this.sourceFederate;
	}

	/**
	 * Set the source federate to the given handle. Will overwrite what exists (unlike
	 * {@link #setSourceFederateIfNull(int)} which will preserve any existing value).
	 * 
	 * @param federateHandle The handle to set the source federate to
	 */
	public void setSourceFederate( int federateHandle )
	{
		this.sourceFederate = federateHandle;
		if( federateHandle == PorticoConstants.RTI_HANDLE )
			setIsFromRti( true );
	}

	/**
	 * Set the source federate to the given handle, but only if it is currently not set.
	 * If it is set, don't do anything.
	 * 
	 * @param federateHandle The handle to set as the source federate of the message
	 */
	public void setSourceFederateIfNull( int federateHandle )
	{
		if( this.sourceFederate == PorticoConstants.NULL_HANDLE )
			this.sourceFederate = federateHandle;
	}

	public void setTargetFederate( int federateHandle )
	{
		this.targetFederate = federateHandle;
	}
	
    /**
     * 设置此消息的目标联邦成员。有以下三种通用选项：<br>
     * <ol>
     * <li>发送给所有成员：不提供任何参数，或使用 {@link PorticoConstants#TARGET_ALL_HANDLE}</li>
     * <li>S发送给单个成员：仅使用要发送目标联邦成员的句柄</li>
     * <li>发送给多个成员：包含您要发送的多个联邦成员的句柄</li>
     * </ol>
     * 
     * 如果这是发往多个目标的消息，则句柄将被设置为 {@link PorticoConstants#TARGET_MANY_HANDLE} 的符号值，而实际的目标句柄将被编码在其他位置。
     * 
     * @param federateHandles 要发送的目标联邦成员的句柄
     */
	public void setTargetFederates( int... federateHandles )
	{
		if( federateHandles.length == 0 )
		{
			this.targetFederate = PorticoConstants.TARGET_ALL_HANDLE;
		}
		else if( federateHandles.length == 1 )
		{
			this.targetFederate = federateHandles[0];
		}
		else
		{
			this.targetFederate = PorticoConstants.TARGET_MANY_HANDLE;
			this.multipleTargets = new HashSet<>();
			for( int i = 0; i < federateHandles.length; i++ )
				multipleTargets.add( federateHandles[i] );
		}
	}
	
    /**
     * @see #setTargetFederates(int...)
     * @param targets
     */
	public void setTargetFederates( Set<Integer> targets )
	{
		if( targets.size() == 0 )
		{
			this.targetFederate = PorticoConstants.TARGET_ALL_HANDLE;
		}
		else if( targets.size() == 1 )
		{
			this.targetFederate = targets.stream().findFirst().get();
		}
		else
		{
			this.targetFederate = PorticoConstants.TARGET_MANY_HANDLE;
			this.multipleTargets = new HashSet<>( targets );
		}
	}
	
	/**
	 * Defaults to {@link PorticoConstants#NULL_HANDLE} unless otherwise set.
	 */
	public int getTargetFederate()
	{
		return this.targetFederate;
	}

	public Set<Integer> getMultipleTargets()
	{
		if( this.multipleTargets == null )
		{
			Set<Integer> targets = new HashSet<>();
			targets.add( targetFederate );
			return targets;
		}
		else
			return this.multipleTargets;
	}

	/**
	 * Defaults to {@link PorticoConstants#TARGET_ALL_HANDLE} unless otherwise set.
	 */
	public int getTargetFederation()
	{
		return this.targetFederation;
	}

	public void setTargetFederation( int federationHandle )
	{
		this.targetFederation = federationHandle;
	}

	/**
	 * This method will return the current timestamp value for this message. By default it will
	 * return {@link PorticoConstants#NULL_TIME}. If you want a message to no longer have a
	 * timestamp, you should call {@link #setTimestamp(double)
	 * setTimestamp(PorticoConstants.NULL_TIME)}.
	 */
	public double getTimestamp()
	{
		return this.timestamp;
	}

	/**
	 * This method will return true if the current timestamp is not equal to
	 * {@link PorticoConstants#NULL_TIME}. If it is equal to that value, the message is not meant
	 * to be timestamped (thus, false will be returned).
	 */
	public void setTimestamp( double timestamp )
	{
		this.timestamp = timestamp;
	}

	/**
	 * Returns <code>true</code> if a timestamp has been set for this message.
	 */
	public boolean isTimestamped()
	{
		return this.timestamp != PorticoConstants.NULL_TIME;
	}

	public boolean isTimeAdvance()
	{
		return false;
	}

    /**
     * 是否此回调类型是否属于HLA规范中定义的“消息”。 规范对“消息”的定义如下
     * 
     * <pre>
     * 3.1.51 message: A change of object instance attribute value, an interaction, or a deletion
     *                 of an existing object instance, often associated with a particular point on
     *                 the High Level Architecture (HLA) time axis, as denoted by the associated
     *                 timestamp.
     * </pre>
     * 
     * @return 硬编码为返回 <code>false</code> ，对于属于此类别的任何消息类型，应重写此方法并返回 <code>true</code>
     */
	public boolean isSpecDefinedMessage()
	{
		return false;
	}
	
	/**
	 * Returns <code>true</code> if there is no target federate for this message
	 */
	public boolean isBroadcast()
	{
		return this.targetFederate == PorticoConstants.NULL_HANDLE;
	}
	
    /**
     * 如果需要立即处理此消息，则返回<code>true</code>。<br>
     * 如果该值为 <code>true</code>，则表示必须尽快处理该消息。通常情况下，这意味着消息不应放入任何队列中等待后续处理，而应立即进行处理。<br>
     * 例如，当带有此标志的消息被提交给 LRCMessageQueue 时，它会直接将该消息投入关联内核的传入消息接收器中。<br>
     */
    public boolean isImmediateProcessingRequired()
	{
		return this.immediate;
	}
	
	/**
	 * Set the "immediate processing required" flag to the given value.
	 */
	public void setImmediateProcessingFlag( boolean value )
	{
		this.immediate = value;
	}

	/**
	 * 返回实现类的简单（非限定）名称。
	 */
	public String getIdentifier()
	{
		return getClass().getSimpleName();
	}

	/////////////////////////////////////////////////////////////
	/////////////////////// Clone Methods ///////////////////////
	/////////////////////////////////////////////////////////////
	public Object clone() throws CloneNotSupportedException
	{
		return super.clone();
	}

	/**
	 * A type-safe version of clone that will call {@link #clone()} and cast it to the given type
	 * before returning it.
	 */
	public <T extends PorticoMessage> T clone( Class<T> expectedType ) throws JRTIinternalError
	{
		try
		{
			return expectedType.cast( this.clone() );
		}
		catch( CloneNotSupportedException cnse )
		{
			throw new JRTIinternalError( "Error performing clone()", cnse );
		}
	}
	
	/////////////////////////////////////////////////////////////
	/////////////////// Serialization Methods ///////////////////
	/////////////////////////////////////////////////////////////
	private void writeObject( ObjectOutputStream oos ) throws IOException
	{
		writeExternal( oos );
	}

	private void readObject( ObjectInputStream ois)  throws IOException, ClassNotFoundException
	{
		readExternal( ois );
	}
	
	public void readExternal( ObjectInput input ) throws IOException, ClassNotFoundException
	{
		this.isFromRti = input.readBoolean();
		this.sourceFederate = input.readInt();
		this.targetFederate = input.readInt();
		this.targetFederation = input.readInt();
		this.timestamp = input.readDouble();
		this.immediate = input.readBoolean();
	}
	
	public void writeExternal( ObjectOutput output ) throws IOException
	{
		// FIXME What about multiple targets?
		output.writeBoolean( isFromRti );
		output.writeInt( sourceFederate );
		output.writeInt( targetFederate );
		output.writeInt( targetFederation );
		output.writeDouble( timestamp );
		output.writeBoolean( immediate );
	}
	
	protected String bytesToString( byte[] bytes )
	{
		if( bytes != null )
			return new String(bytes);
		else
			return "";
	}
	
	protected byte[] stringToBytes( String string )
	{
		if( string == null )
			return "".getBytes();
		else
			return string.getBytes();
	}

	/////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////// Manual Marshaling Methods ///////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * If message types support manual marshaling, they should override this method to return
	 * <code>true</code>. By default it returns <code>false</code> and the automatic serialization
	 * mechanisms will be used by the {@link MessageHelpers}. If this method returns
	 * <code>true</code>, the {@link #marshal(ObjectOutput)} and {@link #unmarshal(ObjectInput)}
	 * methods will be used for optimized performance. PLEASE: only use this if you absolutely
	 * have to.
	 * <p/>
	 * <b>NOTE:</b> For this to work successfully, each message class must have a unique id (so
	 * that on unmarhsal, an appropriate instance can be created). Thus, there must be an entry
	 * for the class in the MessageHelpers.newMessageForId() private method. This method is hard
	 * coded so that you can really only make use of this facility if really, really want it. Yes,
	 * it's not clean or elegant, but that's also half the point in this case. Don't use this!
	 */
	public boolean supportsManualMarshal()
	{
		return false;
	}
	
	/**
	 * <i><b>NOTE</b>: These methods are provided to bypass the typical serialization process used
	 * to convert messages into byte[]'s for sending. Unless absolute, extreme, total performance
	 * is needed for a particular message, these methods should not be used. They are only for
	 * those methods that are high-volume (like interactions and reflections). These methods as
	 * they exist here do nothing, they are designed to be overridden by the subclass. By
	 * declaring them here, they can be invoked on a generic PorticoMessage, hence the reason they
	 * exist. See {@link MessageHelpers#deflate(PorticoMessage)}.</i>
	 * <p/>
	 * This method will marshal our local values into the given buffer.
	 */
	public void marshal( ObjectOutput buffer ) throws IOException
	{
	}
	
	/**
	 * <i><b>NOTE</b>: These methods are provided to bypass the typical serialization process used
	 * to convert messages into byte[]'s for sending. Unless absolute, extreme, total performance
	 * is needed for a particular message, these methods should not be used. They are only for
	 * those methods that are high-volume (like interactions and reflections). These methods as
	 * they exist here do nothing, they are designed to be overridden by the subclass. By
	 * declaring them here, they can be invoked on a generic PorticoMessage, hence the reason they
	 * exist. See {@link MessageHelpers#inflate(byte[],Class)}.</i>
	 * <p/>
	 * This method will unmarshal our local values from given buffer.
	 */
	public void unmarshal( ObjectInput buffer ) throws IOException, ClassNotFoundException
	{
	}
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
