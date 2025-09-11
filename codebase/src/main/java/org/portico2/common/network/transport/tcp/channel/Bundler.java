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
package org.portico2.common.network.transport.tcp.channel;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.Logger;
import org.portico.utils.StringUtils;
import org.portico2.common.network.CallType;
import org.portico2.common.network.Header;

/**
 * Bundler类负责将一系列字节缓冲并刷新到 {@link DataOutputStream} 中。<br>
 * 它作为调用者与流之间的中间层，会缓存消息，以便能够以更大的数据块发送，而不是逐条发送，从而更高效地利用可用的写入带宽。<br>
 * <p/>
 * 
 * Bundler具有三个属性，用于控制消息何时以及如何被刷新:<br>
 * 
 * <ol>
 * <li><b>Enabled</b>: 如果Bundler被禁用，则每次调用都会触发一次刷新。</li>
 *
 * <li><b>Max Size</b>: 这是在触发刷新前可缓冲的最大字节数。如果提交的数据导致缓冲区超过此限制，将立即触发一次刷新。</li>
 *
 * <li><b>Max Time</b>: 如果Bundler处于启用状态，这是数据在被刷新前可在Bundler中停留的最长时间。<br>
 * 这是为了确保消息不会无限期等待，直到凑够触发刷新所需的最后几个字节。<br>
 * 时间单位为毫秒。</li>
 * </ol>
 * 
 * <b>刷新缓冲区</b>
 * <p/>
 * 根据上述说明，只有当数据在缓冲区中停留时间过长，或缓冲区中的字节数超过了限制时（或者Bundler完全被禁用），才会触发刷新操作。
 * <p/>
 * 
 * 缓冲区的刷新将在<b>单独的线程</b>中进行</i>。因此，在调用 {@link #startBundler()} 之前， Bundler不会接收或处理任何消息。<br>
 */
public class Bundler
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private Logger logger;
	
	// message queuing
	private boolean isEnabled;      // bundle messages or not? if false, flush on every submit
	private int sizeLimit;          // max bytes to hold onto before release
	private int timeLimit;          // max amount of time to hold onto messages before release
	private ByteBuffer buffer;      // store incoming messages here prior to flush
	private int queuedMessages;     // number of messages we currently have queued
	private long oldestMessage;     // time (millis) when first message turned up in queue

	// output writing
	private DataOutputStream outstream; // connection to the router
	private Lock lock;                  // lock the send/receive processing
	private Condition armCondition;     // tell the timer that it should be ready to flush soon
	private Condition flushCondition;   // triggered when it is time to flush
	private Condition returnCondition;  // triggered when the flush is over
	private Thread senderThread;        // thread that will do all our sending work

	// metrics
	private Metrics metrics;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public Bundler( Logger logger )
	{
		this.logger = logger;

		// message queuing
		this.isEnabled = true;
		this.sizeLimit = 64000; // 64k
		this.timeLimit = 20; // 20ms
		this.buffer = ByteBuffer.allocate( (int)(sizeLimit*1.1) );
		this.queuedMessages = 0;
		this.oldestMessage = 0;

		// output writing
		this.lock = new ReentrantLock();
		this.armCondition = this.lock.newCondition();
		this.flushCondition = this.lock.newCondition();
		this.returnCondition = this.lock.newCondition();

		// metrics
		this.metrics = new Metrics();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	////////////////////////////////////////////////////////////////////////////////////////
	///  Lifecycle Management   ////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	/** Conencts to the given output stream and starts the sender thread */
	public void startBundler( DataOutputStream outstream )
	{
		if( logger == null )
			throw new IllegalStateException( "You must give the bundler a logger prior to start" );
		
		logger.debug( "[Bundler] Starting. Max bundle size="+StringUtils.getSizeString(sizeLimit)+
		              ", max bundle time="+timeLimit+"ms" );

		this.outstream = outstream;

		// start the sender
		this.senderThread = new Thread( new Sender(), "Bundler-Sender" );
		this.senderThread.setDaemon( true );
		this.senderThread.start();
	}
	
	public void stopBundler()
	{
		// NOTE: Disabling - we can't tell if the connection is open or not.
		//                   If we are stopping because of a disconnection then
		//                   flushing will trigger an exception (duh, connection closed).
		// flush whatever we have in the pipes currently
		//logger.trace( "Flushing "+queuedMessages+" stored messages" );
		//flush();
		if( queuedMessages > 0 )
			logger.warn( "Shutting down bundler with %d messages still queued", queuedMessages );

		// kill the sender thread
		try
		{
			logger.trace( "Shutting down bundler sending thread" );
			senderThread.interrupt();
			senderThread.join( 2000 );
		}
		catch( InterruptedException ie )
		{
			logger.warn( "Bundler sending thread did not shut down cleanly (2 sec wait)" );
		}

		logger.debug( "Bundler has been shut down" );
	}

	////////////////////////////////////////////////////////////////////////////////////////
	///  Bundling Methods   ////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 提交一条消息用于打包。
     * <p/>
     *
     * 如果提交后超出大小限制，将触发一次刷新，并且该方法会阻塞。<br>
     * 否则，它会将请求缓存并立即返回。如果这是上次刷新之后的第一条消息，将启动一个定时器，以便在可配置的时间内（默认为20毫秒）未达到大小上限时，仍然执行一次刷新。<br>
     * <p/>
     *
     * 该方法会在以下两种情况下阻塞：<br>
     * <ul>
     * <li>打包消息的总大小超过限制，从而触发刷新</li>
     * <li>当前正在进行一次刷新操作</li>
     * </ul>
     *
     * <b>禁用打包功能</b><br>
     * 打包功能可以通过配置禁用。如果已禁用，提交的消息将立即被刷新发送到路由器。<br>
     * <p/>
     *
     * <b>控制同步和控制响应消息</b><br>
     * 这两类消息可能对时间敏感。因此，如果你提交的消息包含其中任一类型的消息头，无论队列状态如何，都将立即触发一次刷新。<br>
     * <p/>
     *
     * @param message 要发送的消息正文
     */
	public void submit( byte[] message )
	{
		lock.lock();

		try
		{
			//
			// queue the message
			//
			growBufferIfNeeded( message.length );
			buffer.put( message );
			queuedMessages++; // metrics
			
//			growBufferIfNeeded( 9+message.length );
//			buffer.put( header.getByteValue() );
//			buffer.putInt( requestId );
//			buffer.putInt( message.length );
//			buffer.put( message );
//			queuedMessages++; // metrics
			
			// log that we've queued the message
			Header header = new Header( message, 0 );
			if( logger.isTraceEnabled() )
				logQueuedMessage( header, message );
			
			// if actual message bundling is turned off, flush right away
			//   -OR-
			// if the header is time critical (ControlSync, or ControlResp)
			if( this.isEnabled == false || header.getCallType() != CallType.DataMessage )
			{
				flush();
				return;
			}

			// check if we need to reset the time trigger
			if( this.oldestMessage == 0 )
			{
				this.oldestMessage = System.currentTimeMillis();
				// arm the timer
				armCondition.signalAll();
			}

			// check to see if we've hit the size trigger
			if( buffer.position() > sizeLimit )
			{
				flushCondition.signalAll();
				returnCondition.await();
			}
		}
		catch( InterruptedException ie )
		{
			// only when we're exiting - ignore
		}
		finally
		{
			lock.unlock();
		}
	}

	/*
	 * Should only be called if trace is enabled. Does an obscene amount of work just to
	 * generate some better logs.
	 */
	private final void logQueuedMessage( Header header, byte[] payload )
	{
		if( logger.isTraceEnabled() == false )
			return;

		// Log some generation information about the message. If it is a response message,
		// log the result. We can only do this if the message is unencrypted. If it isn't,
		// we have to fall back to the header-only encryption.
//		if( header.isEncrypted() == false && header.getCallType() == CallType.ControlResp )
//		{
//			ResponseMessage response = MessageHelpers.inflate2( payload, ResponseMessage.class );
//			logger.trace( "(outgoing) type=%s (id=%d), success=%s, result=%s, size=%d",
//			              header.getCallType(),
//			              header.getRequestId(),
//			              ""+response.isSuccess(),
//			              response.getResult(),
//			              payload.length );
//		}
//		else
//		{
			// we have the PorticoMessage original, let's log some stuff!
			logger.trace( "(outgoing) type=%s (id=%d), ptype=%s, from=%s, to=%s, size=%d",
			              header.getCallType(),
			              header.getRequestId(),
			              header.getMessageType(),
			              StringUtils.sourceHandleToString( header.getSourceFederate() ),
			              StringUtils.targetHandleToString( header.getTargetFederate()),
			              payload.length );
//		}
	}

	/**
	 * If the buffer does not have enough space to store the give amount of bytes, grow it
	 * so that it can (with some to spare - currently 10%).
	 */
	private final void growBufferIfNeeded( int spaceRequired )
	{
		if( buffer.remaining() < spaceRequired )
		{
			// Create a new buffer and copy the existing one over
			// This is expensive, so let's hope it is rare!
			int newsize = buffer.capacity() + spaceRequired;
			ByteBuffer newBuffer = ByteBuffer.allocate( (int)(newsize*1.1) ); // 10% elbow room

			// copy the contents of the old buffer over and replace it
			this.buffer.flip();
			newBuffer.put( buffer );
			this.buffer = newBuffer;
		}
	}
	
	/**
	 * Check what's been bundled up to see if it should be released. There are two trigger
	 * conditions for release:
	 * 
	 *   1. We've stored up more than `MAX_BYTES` bytes
	 *   2. We've held messages longer than `MAX_WAIT` time
	 * 
	 * This method can be called from the thread that invokes `submit()` when the size trigger is
	 * tripped, or from the Sender-thread when the time trigger is tripped.
	 */
	private void flush()
	{
		// grab the lock so that stuff isn't jumping into the buffer while we're working
		lock.lock();
		try
		{
			// down the loo!
			int bytes = buffer.position();
			outstream.writeInt( 0xcafe );
			outstream.writeInt( bytes );
			outstream.write( buffer.array(), 0, bytes );

			// metrics
			metrics.messagesSent += queuedMessages;
			metrics.bytesSent += bytes;
			
			if( logger.isTraceEnabled() )
				logger.trace( "(outgoing) {FLUSH} %d messages (%s) have been flushed", queuedMessages, bytes );
		}
		catch( IOException ioex )
		{
			logger.error( "Error while flushing bundler: "+ioex.getMessage(), ioex );
		}
		finally
		{
			// empty our buffer - it is used in submit() as well
			buffer.clear();
			this.queuedMessages = 0;
			this.oldestMessage = 0;

			this.returnCondition.signalAll();
			lock.unlock();
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////
	///  Accessors and Mutators   //////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	public boolean isEnabled()
	{
		return this.isEnabled;
	}
	
	public void setEnabled( boolean isEnabled )
	{
		this.isEnabled = isEnabled;
	}

	/** Max number of bytes held prior to flush (in bytes). Default is 64k. */
	public int getSizeLimit()
	{
		return this.sizeLimit;
	}

	/**
	 * Sets the max size that the buffer can be prior to triggering a flush.
	 * 
	 * @param bytes Max size in bytes
	 */
	public void setSizeLimit( int bytes )
	{
		this.sizeLimit = bytes;
	}
	
	/** Max period of time that bytes are held prior to flush (in millis). Default is 20ms. */
	public int getTimeLimit()
	{
		return this.timeLimit;
	}

	/**
	 * Set the max time that bytes can sit in the buffer prior to triggering a flush.
	 * 
	 * @param millis The max wait time in millis
	 */
	public void setTimeLimit( int millis )
	{
		this.timeLimit = millis;
	}

	public Metrics getMetrics()
	{
		return this.metrics;
	}

	/**
	 * Let someone specify a shared metrics object we should be using.
	 */
	public void setMetrics( Metrics metrics )
	{
		this.metrics = metrics;
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------

	//////////////////////////////////////////////////////////////////////////////////////
	////// Private Class: Sender   ///////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////
	private class Sender extends TimerTask implements Runnable
	{
		public void run()
		{
			lock.lock();
			try
			{
				logger.debug( "Sender thread has started up inside the Bundler" );
				
				while( true )
				{
					// Wait for someone to arm us for sending.
					// We don't want to just busy-loop - we only arm when there are messages
					armCondition.await();
					
					// The flush condition we were waiting for has triggered.
					// Either our wait time expired, or we reached our size threshold
					// and this condition was manually triggered
					//boolean triggered = flushCondition.await( timeLimit, TimeUnit.MILLISECONDS );
					flushCondition.await( timeLimit, TimeUnit.MILLISECONDS );

					//if( triggered )
					//	logger.trace( "Bundler triggered by busting our SIZE cap, flushing" );
					//else
					//	logger.trace( "Bundler triggered by busting our TIME cap, flushing" );
					
					// Do the actual work
					flush();
				}
			}
			catch( InterruptedException ie )
			{
				// We are shutting down and that's cool
				logger.debug( "Bundler Sender thread interrupted; shutting down" );
			}
			finally
			{
				lock.unlock();
			}
		}
	}
}
