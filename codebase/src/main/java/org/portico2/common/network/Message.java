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

import org.portico.lrc.utils.MessageHelpers;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.messaging.MessageType;
import org.portico2.common.messaging.ResponseMessage;

/**
 * <pre>
 * NEW Structure of a message:
 *   - Header {@link Header}
 *   - Payload
 *   - Auth Token (Optional) // 由认证协议添加
 *   - Nonce      (Optional) // 由加密协议添加
 * </pre>
 */
public class Message
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private CallType calltype;
	private int requestId;
	private MessageType messageType;
	
	// Cached versions of the inflated messages.
	// Deflated versions are only constructed when the buffer is first requested.
	private PorticoMessage request;
	private Header requestHeader; 
	private ResponseMessage response;
	
	// serialized version of the message
	private byte[] buffer;
	private Header header;
	
	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
    /**
     * 构造一个新的 {@link Message} 对象，该对象将被传递到协议栈中向下传输。<br>
     * 注意：此调用会<b>触发消息压缩（deflation）</b>，这是一个开销较大的过程。<br>
     * 构造完成后，该实例内部的缓冲区将包含序列化后的内容。<br>
     * 
     * @param request 发起的请求
     * @param calltype 调用的类型（将写入消息头）
     * @param requestId 该调用的请求ID（如果有，将写入消息头）
     */
	public Message( PorticoMessage request, CallType calltype, int requestId )
	{
		this.calltype = calltype;
		this.requestId = requestId;
		this.messageType = request.getType();

		this.request = request;
		this.requestHeader = null;    // set in deflateAndStoreResponse()
		this.response = null;         // set in deflateAndStoreResponse()                       

		// create a buffer big enough for the header and the message
		// deflate the message into it
		// populate the header in the buffer
		this.buffer = MessageHelpers.deflate2( request, calltype, requestId );
		this.header = new Header( buffer, 0 ); // FIXME
	}
	
	public Message( byte[] buffer )
	{
		this.buffer = buffer;
		this.header = new Header( buffer, 0 );
		this.requestId = header.getRequestId();
		this.calltype = this.header.getCallType();
		this.messageType = this.header.getMessageType();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	//////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 通知消息对象将其缓冲区中的内容解析并构造成一个完整的 {@link PorticoMessage} 对象，并返回结果。<br>
     * 请求对象采用延迟加载机制，因此如果你在此方法被调用前调用 {@link #getOriginalRequest()}，消息类会隐式地代表你调用此方法。<br>
     * 
     * @return 表示内部字节数组缓冲区（如果存在）的 {@link PorticoMessage} 对象。
     */
	public final PorticoMessage inflateAsPorticoMessage()
	{
		this.request = MessageHelpers.inflate2( buffer, PorticoMessage.class ); 
		return request;
	}
	
	public final <T extends PorticoMessage> T inflateAsPorticoMessage( Class<T> clazz )
	{
		return clazz.cast( inflateAsPorticoMessage() );
	}
	
	public final ResponseMessage inflateAsResponse()
	{
		return MessageHelpers.inflate2( buffer, ResponseMessage.class );
	}
	
	public final void deflateAndStoreResponse( ResponseMessage response )
	{
		if( this.request == null )
			throw new IllegalArgumentException( "You cannot deflate a ResponseMessage without a request" );
		
		this.response = response;
		this.requestHeader = new Header( buffer, 0 ); // store the old header
		this.replaceBuffer( MessageHelpers.deflate2(response,this.requestId,this.request) );
	}
	
	/**
	 * Replace the existing buffer with the given one. This will generate a new header
	 * based on the start of the new buffer and will update the buffer payload to be the
	 * appropriate size based on the new buffer.
	 * <p/>
	 * 
	 * This call is primariliy used by the encryption protocols.
	 * 
	 * @param buffer The new buffer we want to use.
	 */
	public final void replaceBuffer( byte[] buffer )
	{
		this.buffer = buffer;
		this.header = new Header( buffer, 0 );
		this.header.writePayloadLength( buffer.length-Header.HEADER_LENGTH );
	}


	////////////////////////////////////////////////////////////////////////////////////////
	///  Accessors and Mutators   //////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public final int getRequestId() { return this.requestId; }
	public final Header getHeader() { return this.header; }
	public final byte[] getBuffer() { return this.buffer; }
	public final CallType getCallType() { return this.header.getCallType(); }
	public final MessageType getMessageType() { return this.header.getMessageType(); }
	public final boolean hasRequest() { return this.request != null; }
	public final PorticoMessage getOriginalRequest() { return this.request; }
	
	/** @return Header for original request if this message now holds a response
	            as converted via {@link #deflateAndStoreResponse(ResponseMessage)}.
	            Returns <code>null</code> if this message is a request still. */
	public final Header getOriginalHeader() { return this.requestHeader; }

	public final boolean hasResponse() { return this.response != null; }
	public final ResponseMessage getResponse() { return this.response; }

	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
