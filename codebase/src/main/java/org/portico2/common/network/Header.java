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

import org.portico.utils.bithelpers.BitHelpers;
import org.portico.utils.bithelpers.BufferUnderflowException;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.messaging.MessageType;
import org.portico2.common.messaging.ResponseMessage;
import org.portico2.common.services.object.msg.SendInteraction;
import org.portico2.common.services.object.msg.UpdateAttributes;

/**
 * 通过 Portico 网络发送的所有消息都带有特定的消息头。此类表示一个消息头，并处理其与字节数组（byte[]）之间的编码和解码逻辑。.
 * <p/>
 * 
 * 注意，此类除了代表载荷（包含消息头）的字节数组外，不携带任何其他信息。<br>
 * 它仅查看该载荷的前 {@link #HEADER_LENGTH} 个字节。<br>
 * 每当调用一个 <code>getXxx()</code> 方法时，它都会重新从消息头中提取相应信息。<br>
 * 它不会存储或缓存任何信息。因此，它的构造非常轻量级：不进行复制，不解码。<br>
 * 如果你只需要获取其中一个值，可以安全地将载荷包装在该头类中，然后获取所需信息，而不会触发对消息头的额外处理。<br>
 * <p/>
 * 
 * <b>编码</b>
 * <p/>
 * 此类中的各个 <code>writeXxx()</code> 方法会将单个数据项写入消息头的相应位置。<br>
 * 还有两个静态方法，用于在构建最常见类型消息时，接收所需的信息。<br>
 * 注意，这些信息是直接写入载荷的，会覆盖原有内容，不会制作或保留数据的副本或缓存以供后续使用。
 * <p/>
 * 
 * <b>解码</b>
 * <p/>
 * 此类中的各个 <code>getXxx</code> 方法会从载荷的相应位置读取并返回适当的值。<br>
 * 注意，它们不会以任何形式存储或缓存这些值，每次调用时都会重新读取和解码。<br>
 */
public class Header
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	/** 消息头部固定部分的大小（以字节为单位）（始终存在的部分的大小） */ 
	public static final int HEADER_LENGTH = 12;
	public static final byte[] EMPTY_HEADER = new byte[HEADER_LENGTH];

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private byte[] buffer;
	private int offset;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public Header( byte[] buffer, int byteOffset )
	{
		if( buffer.length < HEADER_LENGTH )
			throw new BufferUnderflowException( "Header requires at least "+HEADER_LENGTH+" bytes; found "+buffer.length );
		
		this.buffer = buffer;
		this.offset = byteOffset;
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	//////////////////////////////////////////////////////////////////////////////////////
	///  Header Structure    /////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////
	// Source for "protocol" python command line utility
	//   0~3  四个字节共 32 位: "Flags:8, Payload Length:24"
	//   4~7  四个字节共 32 位: "CType:4, FedID:4, MessageType:8, RequestId/FilteringId:16"
	//   8~11 四个字节共 32 位: "Source Handle:16, Target Handle:16"
	//   "Payload...:128"
	//   "(Optional) Authentication Token:32,(Optional) Encryption Nonce:128"
	//
    //  0                   1                   2                   3
    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |     Flags     |                 Payload Length                |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // | CType | FedID |  MessageType  |     RequestId/FilteringId     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |         Source Handle         |         Target Handle         |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |                                                               |
    // +                                                               +
    // |                                                               |
    // +                           Payload...                          +
    // |                                                               |
    // +                                                               +
    // |                                                               |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |                (Optional) Authentication Token                |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |                                                               |
    // +                                                               +
    // |                                                               |
    // +                  (Optional) Encryption Nonce                  +
    // |                                                               |
    // +                                                               +
    // |                                                               |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	//
    //  == Lead Line ==
    //  (00-00)   | 1-bit  | Bundle?
    //  (01-01)   | 1-bit  | Encrypted? If true, Nonce/IV must be present
    //  (02-02)   | 1-bit  | Filtering data present?
    //  (03-03)   | 1-bit  | Object Handle (Filtering)? // 1=Object, 0=Interaction
    //  (04-04)   | 1-bit  | Authenticated? If sender is authenticated, a token is included in the body
    //  (05-05)   | 1-bit  | Manually Marshalled? If true, the message uses manual marshalling
    //  (06-06)   | 1-bit  | Spare
    //  (07-07)   | 1-bit  | Spare
    //  (08-31)   | 24-bit | Payload Length: Range=16,777,216 (16MB) {EXCLUDES HEADER SIZE}
    //
    //  == Identification Line ==
    //  (32-35)   | 4-bit  | Call Type: uint4, Enum, {DataMessage,Notification,ControlRequest,ControlResponseOK,ControlResponseErr}
    //  (36-39)   | 4-bit  | Federation ID: uint4, range=1-16 
    //  (40-47)   | 8-bit  | Message Type: uint8, ID for the specific message type (uint8)
    //  (48-63)   | 16-bit | Request ID: uint16, range=64k 
    //
    //  == Routing Line ==
    //  (64-79)   | 16-bit | Source Handle: uint16, handle of source federate 
    //  (80-95)   | 16-bit | Target Handle: uint16, handle of target federate
    //
    // == Payload ==
    //  (96-xxx)  | Varies | Payload Data, padded out to nearest 32-bit boundary.
    //                       If neither Auth or Encryption is set, this will start
    //                       earlier (as those headers won't be present).
	//
	
	////////////////////////////////////////////////////////////////////////////////////////
	///  Flags   ///////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	// Flags
	// 01 Bundle                       // adds guaranteed + ??
	// 01 Encrypted                    // adds guaranteed +128
	// 01 Authenticated                // adds guaranteed +32
	// 01 Manually Marshalled
	// 01 Filtering                    // Handle stored in RequestID/FilteringID combo field
	// 01 Object Handle (Filtering)    //
	// 01 Spare
	// 01 Spare
	public final boolean isBundle()
	{
		return BitHelpers.readBooleanBit( buffer, offset, 0 );
	}
	
	public final void writeIsBundle( boolean isBundle )
	{
		BitHelpers.putBooleanBit( isBundle, buffer, offset, 0 );
	}
	
	public final boolean isEncrypted()
	{
		return BitHelpers.readBooleanBit( buffer, offset, 1 );
	}
	
	public final void writeIsEncrypted( boolean isEncrypted )
	{
		BitHelpers.putBooleanBit( isEncrypted, buffer, offset, 1 );
	}
	
	public final boolean isAuthenticated()
	{
		return BitHelpers.readBooleanBit( buffer, offset, 2 );
	}
	
	public final void writeIsAuthenticated( boolean isAuthenticated )
	{
		BitHelpers.putBooleanBit( isAuthenticated, buffer, offset, 2 );
	}

	public final boolean isManualMarshal()
	{
		return BitHelpers.readBooleanBit( buffer, offset, 3 );
	}
	
	public final void writeIsManualMarshal( boolean isManualMarshal )
	{
		BitHelpers.putBooleanBit( isManualMarshal, buffer, offset, 3 );
	}
	
	public final boolean isFiltering()
	{
		return BitHelpers.readBooleanBit( buffer, offset, 4 );
	}
	
	public final void writeIsFiltering( boolean isFiltering )
	{
		BitHelpers.putBooleanBit( isFiltering, buffer, offset, 4 );
	}
	
	public final boolean isFilteringObjectClass()
	{
		return BitHelpers.readBooleanBit( buffer, offset, 5 );
	}
	
	public final void writeIsFilteringObjectClass( boolean isFilteringObjectClass )
	{
		BitHelpers.putBooleanBit( isFilteringObjectClass, buffer, offset, 5 );
	}

	////////////////////////////////////////////////////////////////////////////////////////
	///  Message/Header Length Methods   ///////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public final int getPayloadLength()
	{
		return (int)BitHelpers.readUint24( buffer, offset+1 );
	}
	
	public void writePayloadLength( int payloadLength )
	{
		BitHelpers.putUint24( payloadLength, buffer, offset+1 );
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	///  Identifier Line Methods   /////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	// 标识行是每个消息头固定部分中第二行的4个字节。
	// 它包含标识信息比如调用类型（Call Type），消息类型（Message Type），联邦ID（Federation ID）以及请求ID（如果是控制消息）或用于过滤的类句柄（如果是数据消息）
	public final CallType getCallType()
	{
		return CallType.fromId( BitHelpers.readUint4(buffer,offset+4,0) );
	}
	
	public final void writeCallType( CallType calltype )
	{
		BitHelpers.putUint4( (byte)calltype.getId(), buffer, offset+4, 0 );
	}
	
	public final int getFederation()
	{
		return BitHelpers.readUint4( buffer, offset+4, 4 );
	}
	
	public final void writeFederation( int federationHandle )
	{
		BitHelpers.putUint4( (byte)federationHandle, buffer, offset+4, 4 );
	}
	
	public final MessageType getMessageType()
	{
		return MessageType.fromId( BitHelpers.readUint8(buffer,offset+5) );
	}
	
	public final void writeMessageType( MessageType type )
	{
		BitHelpers.putUint8( type.getId(), buffer, offset+5 );
	}

	public final int getRequestId()
	{
		return BitHelpers.readUint16( buffer, offset+6 );    // caution, doubles up with FilterId
	}
	
	public final void writeRequestId( int requestId )
	{
		BitHelpers.putUint16( requestId, buffer, offset+6 ); // caution, doubles up with FilterId
	}

	public final int getFilteringId()
	{
		return BitHelpers.readUint16( buffer, offset+6 );    // caution, doubles up with RequestId
	}
	
	public final void writeFilteringId( int filteringId )
	{
		BitHelpers.putUint16( filteringId, buffer, offset+6 ); // caution, doubles up with RequestId
	}


	// Convenience Methods
	public final boolean isDataMessage()
	{
		return BitHelpers.readUint4(buffer,offset+4,0) == 0;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	///  Routing Line Methods   ////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public final int getSourceFederate()
	{
		return BitHelpers.readUint16( buffer, offset+8 );
	}
	
	public final int getTargetFederate()
	{
		return BitHelpers.readUint16( buffer, offset+10 );
	}

	public final void writeSourceAndTargetFederate( int sourceFederate, int targetFederate )
	{
		BitHelpers.putUint16( sourceFederate, buffer, offset+8 );
		BitHelpers.putUint16( targetFederate, buffer, offset+10 );
	}
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
	
	public static void writeHeader( byte[] buffer,           // buffer to write into
	                                int byteOffset,          // offset to start at within buffer
	                                PorticoMessage message,  // message with lots of info needed
	                                CallType calltype,       // the type of call this is
	                                int reqOrFilteringId,    // id for request correlation OR filtering
	                                int payloadLength )      // length of the payload only
	{
		Header header = new Header( buffer, byteOffset );

		//
		// Lead Line
		//
		// -- Flags --
		// Bundle Flag
		// This is not supported yet
		
		// Encryption Flag
		// This is written in by the EncryptionProtocol if it is used.
		
		// Authentication Flag
		// This is written in by the AuthenticationProtocol if it is used.

		// Manual Marshal Support
		header.writeIsManualMarshal( message.supportsManualMarshal() );

		// Filtering Flags
		if( calltype == CallType.DataMessage )
		{
			// If this is a data message, we must write in the Filtering ID/Handle information
			header.writeIsFiltering( true );
			switch( message.getType() )
			{
				case UpdateAttributes:
					header.writeIsFilteringObjectClass( true );
					header.writeFilteringId( ((UpdateAttributes)message).getObjectId() );
					break;
				case SendInteraction:
					header.writeIsFilteringObjectClass( false );
					header.writeFilteringId( ((SendInteraction)message).getInteractionId() );
					break;
				default:
					break;
			}
		}

		// Payload Length
		header.writePayloadLength( payloadLength );

		//
		// Identification Line
		//
		//    4-bit, Call Type     (enum)
		//    4-bit, Federation ID (uint4)
		//    8-bit, Message Type  (uint8)
		//   16-bit, Request ID    (uint16)
		header.writeCallType( calltype );
		header.writeFederation( message.getTargetFederation() );
		header.writeMessageType( message.getType() );
		header.writeRequestId( reqOrFilteringId );
		
		// Routing
		//   16-bit, Source FederateHandle (uint16)
		//   16-bit, Target FederateHandle (uint16)
		header.writeSourceAndTargetFederate( message.getSourceFederate(),
		                                     message.getTargetFederate() );
	}

	public static void writeResponseHeader( byte[] buffer,
	                                        int byteOffset,
	                                        int requestId,
	                                        ResponseMessage response,
	                                        PorticoMessage request,
	                                        int payloadLength )
	{
		Header header = new Header( buffer, byteOffset );
		// TODO Put something here to zero-out the header. It currently is zero'd out by
		//      virtue of the way MessageHelpers.deflate() works, but that isn't guaranteed
		//      to stay that way if we look at more efficient buffer use

		// Lead Line
		//
		// -- Flags --
		// Bundle Flag
		// This is not supported yet
		
		// Encryption Flag
		// This is written in by the EncryptionProtocol if it is used.
		
		// Authentication Flag
		// This is written in by the AuthenticationProtocol if it is used.

		// Manual Marshal Support
		header.writeIsManualMarshal( false );

		// Payload Length
		header.writePayloadLength( payloadLength );

		// Identification Line
		header.writeCallType( response.isSuccess() ? CallType.ControlResponseOK :
		                                             CallType.ControlResponseErr );
		header.writeFederation( request.getTargetFederation() );
		header.writeMessageType( request.getType() );
		header.writeRequestId( requestId ); // TODO Could this move into PorticoMessage?
		
		// Routing -- Flipped from request
		header.writeSourceAndTargetFederate( request.getTargetFederate(), request.getSourceFederate() );
	}
	
}
