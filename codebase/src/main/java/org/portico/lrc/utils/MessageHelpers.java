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
package org.portico.lrc.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.portico.lrc.LRC;
import org.portico.lrc.compat.JRTIinternalError;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.messaging.MessageType;
import org.portico2.common.messaging.ResponseMessage;
import org.portico2.common.network.CallType;
import org.portico2.common.network.Header;
import org.portico2.common.services.object.msg.SendInteraction;
import org.portico2.common.services.object.msg.UpdateAttributes;

public class MessageHelpers
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------

	///////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////  Message Marshalling and Unmarshalling  /////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 此方法将把给定的消息转换为一个 <code>byte[]</code>.
     * <p/>
     * 
     * 生成的字节数组将根据 Portico 标准包含一个消息头 （参见
     * {@link Header#writeHeader(byte[], int, PorticoMessage, CallType, int, int)}）
     * <p/>
     * 
     * <b>注意：</b><br>
     * 如果该消息支持手动序列化（即忽略基于反射的序列化过程，而将完全控制权交给消息类）， 则会优先使用该处理流程（从而调用 {@link PorticoMessage#marshal(java.io.ObjectOutput)}
     * 方法）。<br>
     * 除非你清楚自己在做什么，并且已将消息ID添加到硬编码的私有方法 {@link #manuallyUnmarshal(ObjectInputStream, LRC)} 中， 否则不应使用此功能。<br>
     * 
     * @param message 要编码的消息
     * @param calltype 此调用的类型(Data, ControlSync, ...)
     * @param requestId 如果是控制消息，则为此请求的ID（如果不是，则为0）
     */
	public static final byte[] deflate2( PorticoMessage message,
	                                     CallType calltype,
	                                     int requestId )
	{
		// Step 1. 先写入空的消息头（稍后更新），再写入完整的消息体
        //         我们首先写入消息体，因为需要知道其长度才能将其包含在消息头中。
        //         为了提高效率，我们先在载荷位置写入一个空的字节块，作为消息头的预留空间。
        //         稍后我们再将其覆盖（写入实际的消息头内容）。
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			// write the space for the header
			baos.write( Header.EMPTY_HEADER );
			
			// marshal the message
			ObjectOutput out = new ObjectOutputStream( baos );
			if( message.supportsManualMarshal() )
			{
				// TODO Move this into the header
				out.writeBoolean( true );
				out.writeShort( message.getType().getId() );
				message.marshal( out );
			}
			else
			{
				out.writeBoolean( false );
				out.writeObject( message );
			}
		}
		catch( IOException ioex )
		{
			throw new RuntimeException( "couldn't convert message ["+message.getType()+"] into byte[]", ioex );
		}
		
		// Step 2. 获取缓冲区。
        //         现在我们已将载荷写入缓冲区，接下来只需进行一些清理工作，
        //         并获取对该缓冲区的访问权限，以便我们可以覆盖（写入）消息头部分。
		byte[] buffer = baos.toByteArray();

		// Step 3. 写入消息头。
        //         收集所有需要的信息，并将消息头写入缓冲区中最初为载荷预留的空白区域。
		// create the output stream with the given size (or resizable if -1 is provided)
		int payloadLength = buffer.length - Header.HEADER_LENGTH;
		Header.writeHeader( buffer, 0, message, calltype, requestId, payloadLength );
		return buffer;
	}

    /**
     * 此方法仅调用 {@link #deflate2(ResponseMessage, PorticoMessage, int, int, int, int)}。 它从给定的请求消息中获取消息头所需的所有信息。
     * 
     * @param response 要压缩的响应消息
     * @param requestId 我们正在响应的请求的ID
     * @param request 原始的请求消息（从中获取联邦成员ID、源/目标等信息）
     * @return 准备好用于传输的字节数组（byte[]）格式的消息
     */
	public static final byte[] deflate2( ResponseMessage response,
	                                     int requestId,
	                                     PorticoMessage request )
	{
		return deflate2( response,
		                 request,
		                 requestId,
		                 request.getTargetFederation(),
		                 request.getTargetFederate(),   // 与请求中的方向相反
		                 request.getSourceFederate() ); // 与请求中的方向相反
	}
	
    /**
     * 压缩（Deflate）并打包给定的响应消息。响应消息比较特殊，与请求消息相比，它们不需要携带大量信息。<br>
     * 该方法仅接收响应消息以及需要打包到消息头中的元数据。<br>
     * 
     * @param response 要压缩的消息对象
     * @param requestId 正在响应的请求的ID
     * @param targetFederation 联邦的ID（打包进消息头）
     * @param sourceFederate 发送方联邦成员的ID（打包进消息头）
     * @param targetFederate 目标联邦成员的ID（打包进消息头）
     * @return 准备好用于传输的字节数组（byte[]）格式的消息
     */
	private static final byte[] deflate2( ResponseMessage response,
	                                      PorticoMessage request,
	                                      int requestId,
	                                      int targetFederation,
	                                      int sourceFederate,
	                                      int targetFederate )
	{
		// Step 1. 写入消息体
		//         我们首先写入消息体，因为需要知道其长度才能将其包含在消息头中。
		//         为了提高效率，我们先在载荷位置写入一个空的字节块，作为消息头的预留空间。
		//         稍后我们再将其覆盖（写入实际的消息头内容）。
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			// write the space for the header
			baos.write( Header.EMPTY_HEADER );
			
			// marshal the message
			ObjectOutput out = new ObjectOutputStream( baos );
			out.writeBoolean( false );
			out.writeObject( response );
		}
		catch( IOException ioex )
		{
			throw new RuntimeException( "couldn't convert object ["+response.getClass()+"] into byte[]", ioex );
		}
		
		// Step 2. 获取缓冲区。
		//         现在我们已将载荷写入缓冲区，接下来只需进行一些清理工作，
		//         并获取对该缓冲区的访问权限，以便我们可以覆盖（写入）消息头部分。
		byte[] buffer = baos.toByteArray();

		// Step 3. 写入消息头。
		//         收集所有需要的信息，并将消息头写入缓冲区中最初为载荷预留的空白区域。
		// create the output stream with the given size (or resizable if -1 is provided)
		int payloadLength = buffer.length - Header.HEADER_LENGTH;
		Header.writeResponseHeader( buffer, 0, requestId, response, request, payloadLength );
		return buffer;
	}

    /**
     * 此方法将获取给定的数据并将其转换为一个Java对象。转换完成后，会尝试将该对象强制转换为指定的类型再返回。
     * </p>
     * <b>注意：</b><br>
     * 如果该消息支持手动反序列化（即忽略基于反射的反序列化过程，而将完全控制权交给消息类），则会优先使用该处理流程（从而调用 {@link PorticoMessage#unmarshal(java.io.ObjectInput)}
     * 方法）。<br>
     * 除非你清楚自己在做什么，并且已将消息ID添加到硬编码的私有方法 {@link #manuallyUnmarshal(ObjectInputStream, LRC)} 中，否则不应使用此功能。
     */
	public static final <T> T inflate2( byte[] data, Class<T> expectedType )
	{
		return inflate2( data, expectedType, null );
	}
	
    /**
     * 此方法将获取给定的数据并将其转换为一个Java对象。转换完成后，会尝试将该对象强制转换为指定的类型再返回。<br>
     * 该方法还接受一个过滤器参数，如果消息使用手动序列化（marshalling），则会将该过滤器传递给消息。<br>
     * 消息可以利用此过滤器来判断是否需要进行开销较大的解包（inflation）操作，从而在不需要时跳过该过程。<br>
     * </p>
     * <b>注意：</b><br>
     * 如果该消息支持手动反序列化（即忽略基于反射的反序列化过程，而将完全控制权交给消息类），则会优先使用该处理流程（从而调用 {@link PorticoMessage#unmarshal(java.io.ObjectInput)}
     * 方法）。<br>
     * 除非你清楚自己在做什么，并且已将消息ID添加到硬编码的私有方法 {@link #manuallyUnmarshal(ObjectInputStream, LRC)} 中，否则不应使用此功能。
     */
	public static final <T> T inflate2( byte[] data, Class<T> expectedType, LRC lrc )
	{
		try
		{
			// create the stream we'll read from, skipping the header
			int length = data.length - Header.HEADER_LENGTH;
			ByteArrayInputStream bais = new ByteArrayInputStream( data, Header.HEADER_LENGTH, length );
			ObjectInputStream ois = new ObjectInputStream( bais );
			
			// find out whether of not manual marshaling was used
			boolean manuallyMarshaled = ois.readBoolean();
			if( manuallyMarshaled )
			{
				// create a new message from the specified id and let it unmarshal itself
				PorticoMessage message = manuallyUnmarshal( ois, lrc );
				return expectedType.cast( message ); // this is null safe
			}
			else
			{
				// phew, a sane person wrote this! use the default unmarhal
				Object theObject = ois.readObject();
				return expectedType.cast( theObject );
			}
		}
		catch( Exception e )
		{
			Header header = new Header( data, 0 );
			throw new JRTIinternalError( "Couldn't convert byte[] ("+header.getMessageType()+") into "+
			                             expectedType.getSimpleName(), e );
		}		
	}	
	
	///////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////// Array Manipulation Methods ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Returns a new byte[] that is equal to the old byte[] only without the first given x bytes
	 * Currently, this is implemented using an array *copy*. Obviously this isn't ideal in terms
	 * of efficiency and should be rewritten to just mask out the header at some point in the
	 * future (if this is even possible with Java).
	 */
	public static byte[] stripHeader( byte[] data, int amountToRemove )
	{
		int size = data.length - amountToRemove;
		byte[] buffer = new byte[size];
		
		System.arraycopy( data, amountToRemove, buffer, 0, size );
		return buffer;
	}

	/**
	 * Convert the provided int into a byte[] for transmission over the network.
	 */
	public static byte[] intToByteArray( int value )
	{
		return new byte[]{ (byte)(value >>> 24),
		                   (byte)(value >>> 16),
		                   (byte)(value >>> 8),
		                   (byte)value };
	}
	
	/**
	 * Convert the provided into into a byte[] and write the values into the given buffer starting
	 * at the provided offset (this will take up 4 bytes!)
	 */
	public static void intToByteArray( int value, byte[] buffer, int offset )
	{
		buffer[offset]   = (byte)(value >>> 24);
		buffer[offset+1] = (byte)(value >>> 16);
		buffer[offset+2] = (byte)(value >>> 8 );
		buffer[offset+3] = (byte)(value);
	}

	/**
	 * Turn the given byte[] into an int after it was received from the network. If the length
	 * of the byte[] is less than 4 bytes, an exception will be thrown
	 */
	public static int byteArrayToInt( byte[] array )
	{
		return (array[0] << 24) +
		       ((array[1] & 0xFF) << 16) +
		       ((array[2] & 0xFF) << 8) +
		       (array[3] & 0xFF);
	}

	/**
	 * Read 4 bytes from the provided array (starting at the given offset) and return them as a
	 * single int. This should just be used to reverse the intToByteArray(...) methods in this class
	 */
	public static int byteArrayToInt( byte[] array, int offset )
	{
		return (array[offset] << 24) +
		       ((array[offset+1] & 0xFF) << 16) +
		       ((array[offset+2] & 0xFF) << 8) +
		       (array[offset+3] & 0xFF);
	}

	///////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////// Message Inflation/Deflation Methods ///////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This method will take the given object and turn it into a <code>byte[]</code>
	 * that can be sent across a network connection, stored on some medium or used in whatever way
	 * an application wants to use it. To turn this bit-blob back into something useful, the
	 * {@link #inflate(byte[],Class)} method can be used.
	 * <p/>
	 * <b>Note:</b> If the message supports manual marshaling (where the reflection-based
	 * serialization is ignored, instead providing total control to the message class), then
	 * that process will be used in preference (resulting in
	 * {@link PorticoMessage#marshal(java.io.ObjectOutput)} being called). This should not be
	 * used unless you know what you are doing and have added the message id to the hardcoded
	 * private method {@link #manuallyUnmarshal(ObjectInputStream, LRC)}).
	 */
	@Deprecated
	public static byte[] deflate( PorticoMessage message )
	{
		// create the output stream with the given size (or resizable if -1 is provided)
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		// do the deflation
		try
		{
			ObjectOutputStream oos = new ObjectOutputStream( baos );
			
			// write the message
			if( message.supportsManualMarshal() )
			{
				// Use the manual marshalling method. First, write the signal that we are doing
				// do, then write the id of the message we are serializing (hashcode of its name),
				// then let the message write itself
				oos.writeBoolean( true );
				oos.writeInt( message.getClass().getSimpleName().hashCode() );
				message.marshal( oos );
			}
			else
			{
				// use the simple way of doing things, just write the message itself to the stream
				oos.writeBoolean( false );
				oos.writeObject( message );
			}
			
			return baos.toByteArray();
		}
		catch( IOException ioex )
		{
			throw new RuntimeException( "couldn't convert message ["+
			                            message.getClass()+"] into byte[]", ioex );
		}
	}

	/**
	 * This method will serialize a generic object into a byte[] for sending over the network.
	 * A standard ObjectOutputStream will be use for this serialization, however we will write
	 * the structure in a format compatible with the various {@link #inflate(byte[], Class)}
	 * methods of this class.
	 * 
	 * @param message The message object to deflate
	 * @return A byte[] representation of the object
	 */
	@Deprecated
	public static byte[] deflate( Object message )
	{
		// create the output stream with the given size (or resizable if -1 is provided)
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		// do the deflation
		try
		{
			ObjectOutputStream oos = new ObjectOutputStream( baos );
			oos.writeBoolean( false );
			oos.writeObject( message );
			
			return baos.toByteArray();
		}
		catch( IOException ioex )
		{
			throw new RuntimeException( "couldn't convert object ["+
			                            message.getClass()+"] into byte[]", ioex );
		}
	}
	
	/**
	 * This method will take the given data and convert it into a Java object. After doing so,
	 * it will attempt to cast the object to the given type before returning it.
	 * <p/>
	 * <b>Note:</b> If the message supports manual unmarshaling (where the reflection-based
	 * deserialization is ignored, instead providing total control to the message class), then
	 * that process will be used in preference (resulting in
	 * {@link PorticoMessage#unmarshal(java.io.ObjectInput)} being called). This should not be
	 * used unless you know what you are doing and have added the message id to the hardcoded
	 * private method {@link #manuallyUnmarshal(ObjectInputStream, LRC)}).
	 */
	@Deprecated
	public static <T> T inflate( byte[] data, Class<T> expectedType )
	{
		return inflate( data, expectedType, null );
	}
	
	/**
	 * This method will take the given data and convert it into a Java object. After doing so,
	 * it will attempt to cast the object to the given type before returning it. It also accepts
	 * a filter that will be passed on to the message if it uses manual marshalling. That filter
	 * can be used by the message to short-circuit potentially expensive inflation if it isn't
	 * needed (however the filter determines that).
	 * <p/>
	 * <b>Note:</b> If the message supports manual unmarshaling (where the reflection-based
	 * deserialization is ignored, instead providing total control to the message class), then
	 * that process will be used in preference (resulting in
	 * {@link PorticoMessage#unmarshal(java.io.ObjectInput)} being called). This should not be
	 * used unless you know what you are doing and have added the message id to the hardcoded
	 * private method {@link #manuallyUnmarshal(ObjectInputStream, LRC)}).
	 */
	@Deprecated
	public static <T> T inflate( byte[] data, Class<T> expectedType, LRC lrc )
	{
		try
		{
			ByteArrayInputStream bais = new ByteArrayInputStream( data );
			ObjectInputStream ois = new ObjectInputStream( bais );
			// find out whether of not manual marshaling was used
			boolean manuallyMarshaled = ois.readBoolean();
			if( manuallyMarshaled )
			{
				// create a new message from the specified id and let it unmarshal itself
				PorticoMessage message = manuallyUnmarshal( ois, lrc );
				return expectedType.cast( message ); // this is null safe
				
				//PorticoMessage message = newMessageForId( ois.readInt() );
				//message.unmarshal( ois );
				//return expectedType.cast( message );
			}
			else
			{
				// phew, a sane person wrote this! use the default unmarhal
				Object theObject = ois.readObject();
				return expectedType.cast( theObject );
			}
		}
		catch( Exception e )
		{
			throw new RuntimeException( "couldn't convert byte[] into "+expectedType.getSimpleName(), e );
		}		
	}
	
	/**
	 * This method will create the appropriate Portico message type based on data from the provided
	 * input stream. It uses the {@link LRC} to determine whether or not it should bother inflating
	 * the message at all. For example, for UpdateAttributes message, it won't bother if the object
	 * the update relates to hasn't been discovered by the local federate (if we were interested,
	 * we would have discovered it).
	 */
	private static PorticoMessage manuallyUnmarshal( ObjectInputStream ois, LRC lrc ) throws Exception
	{
		short messageType = ois.readShort();
		if( messageType == MessageType.UpdateAttributes.getId() )
		{
			int objectId = ois.readInt();
			
			UpdateAttributes update = new UpdateAttributes();
			update.setObjectId( objectId );
			update.unmarshal( ois );
			return update;
		}
		else if( messageType == MessageType.SendInteraction.getId() )
		{
			int interactionId = ois.readInt();
			
			SendInteraction interaction = new SendInteraction();
			interaction.setInteractionId( interactionId );
			interaction.unmarshal( ois );
			return interaction;
		}
		else
		{
			throw new RuntimeException( "Unknown manually marshaled message: class id="+messageType );
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////// Message Compression/Decompression Methods ////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////// Message Encryption/Decryption Methods //////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////

}
