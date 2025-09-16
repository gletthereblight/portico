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
package org.portico2.rti.federation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.portico.impl.HLAVersion;
import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.compat.JFederateNameAlreadyInUse;
import org.portico.lrc.compat.JFederateNotExecutionMember;
import org.portico.lrc.model.ObjectModel;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.PorticoConstants;
import org.portico2.common.configuration.RID;
import org.portico2.common.messaging.MessageContext;
import org.portico2.common.messaging.MessageSink;
import org.portico2.common.services.ddm.data.RegionStore;
import org.portico2.common.services.ownership.data.OwnershipManager;
import org.portico2.common.services.pubsub.data.InterestManager;
import org.portico2.rti.RTI;
import org.portico2.rti.RtiConnection;
import org.portico2.rti.services.RTIHandlerRegistry;
import org.portico2.rti.services.mom.data.FomModule;
import org.portico2.rti.services.mom.data.MomManager;
import org.portico2.rti.services.object.data.Repository;
import org.portico2.rti.services.sync.data.SyncPointManager;
import org.portico2.rti.services.time.data.TimeManager;

/**
 * 该类包含用于支持特定联邦的基础设施.
 */
public class Federation
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	private static final AtomicInteger FEDERATION_HANDLE_COUNTER = new AtomicInteger(0);

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	@SuppressWarnings("unused")
    private RTI         rti; // the RTI we exist in
	@SuppressWarnings("unused")
    private RID         rid; // RTI 配置
	private String      federationName;
	private int         federationHandle;
	private HLAVersion  federationVersion;
	private ObjectModel fom;
	
	private Logger logger;

    /* 联邦管理 */
	// 我们同时跟踪已连接的联邦成员（federates）以及它们所使用的唯一连接实例，确保即使有多个联邦成员通过同一连接与我们通信，我们也只通过该连接传递一份给定消息的副本。
	private AtomicInteger federateHandleCounter;
	private Map<Integer,Federate> federates;
	private Set<RtiConnection> federateConnections;
	
	/* 权限设置 */
	public SecretKey federationKey;
	
	/* 消息处理 */
	private MessageSink incomingSink;
	//	private Queue<PorticoMessage> incomingControlQueue;
	private BlockingQueue<PorticoMessage> outgoingQueue;
	private Thread outgoingProcessor;

	/* 发布与订阅设置 */
	private InterestManager interestManager;

	/* 同步点设置 */
	private SyncPointManager syncManager;

	/* 实例仓库 */
	private Repository repository;

	/* 时间管理 */
	private TimeManager timeManager;
	
	/* 所有权设置 */
	private OwnershipManager ownershipManager;
	
	/* MOM 设置 */
	private MomManager momManager;
	private List<FomModule> fomModules;
	
    /* 保存/恢复设置 */
    // private Serializer serializer;
    // private Manifest manifest;
    // private SaveManager saveManager;
    // private RestoreManager restoreManager;

	/* DDM state entities */
	private RegionStore regionStore;
	@SuppressWarnings("unused")
    private int latestRegionToken;
	@SuppressWarnings("unused")
    private int maxRegionToken;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
    /**
     * 在指定的 RTI 中创建一个使用给定名称、FOM 和规范版本的新联邦。<br>
     * 规范版本将用于从 RTI 的 {@link RTIHandlerRegistry} 中加载相应的处理器集合。<br>
     * 如果配置过程中出现问题，将抛出配置异常。<br>
     * 该方法仅在包范围内可用，如需创建 Federation 实例，应使用 {@link FederationManager}。
     *
     * @param rti 当前加载所处的 RTI
     * @param name 联邦的名称
     * @param fom 联邦的对象模型
     * @param hlaVersion 联邦的 HLA 规范版本
     * @throws JConfigurationException 如果在配置消息处理器时出现问题
     */
	protected Federation( RTI rti, String name, ObjectModel fom, HLAVersion hlaVersion )
		throws JConfigurationException
	{
		this.rti               = rti;
		this.federationName    = name;
		this.federationHandle  = FEDERATION_HANDLE_COUNTER.incrementAndGet();
		this.federationVersion = hlaVersion;
		this.fom               = fom;
		
		this.logger = LogManager.getFormatterLogger( rti.getLogger().getName()+".{"+name+"}" );
		
		// Federation Management //
		this.federateHandleCounter = new AtomicInteger(0);
		this.federates = new HashMap<>();
		this.federateConnections = new HashSet<>();
		
		// Auth Settings //
		this.federationKey = null; // must be manually set
		
		// Message Processing //
		this.incomingSink = new MessageSink( name+"-incoming", logger );
		this.outgoingQueue = new LinkedBlockingQueue<>();
		this.outgoingProcessor = new OutgoingMessageProcessor();

		// Sync Point Settings //
		this.syncManager = new SyncPointManager( this );
		
		// Region Store //
		this.regionStore = new RegionStore();
		this.latestRegionToken = 0;
		this.maxRegionToken = 0;
		
		// Pub & Sub Settings //
		this.interestManager = new InterestManager( fom, regionStore );
		
		// Instance Repository //
		this.repository = new Repository( regionStore );
		
		// Time Management //
		this.timeManager = new TimeManager();
		
		// Ownership settings //
		this.ownershipManager = new OwnershipManager();
		
		// MOM settings //
		this.momManager = new MomManager( this );
		this.fomModules = new ArrayList<>();
		// ... TBA ...

        // 填充消息接收端（Message Sinks），此步骤必须最后执行，以确保我们已创建了所有管理组件，这些组件会在处理器配置时被提取使用。否则，这些组件将为空（null）。
		RTIHandlerRegistry.loadHandlers( this );
	}
	
	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	public String getFederationName() { return this.federationName; }
	public int getFederationHandle()  { return this.federationHandle; }
	public HLAVersion getHlaVersion() { return this.federationVersion; }
	public ObjectModel getFOM()       { return this.fom; }
	public Logger getLogger()         { return this.logger; }

	public SecretKey getFederationKey() { return this.federationKey; }
	public void setFedetrationKey( SecretKey key ) { this.federationKey = key; }
	
	public MessageSink getIncomingSink()
	{
		return this.incomingSink;
	}

	public InterestManager getInterestManager()
	{
		return this.interestManager;
	}

	public SyncPointManager getSyncPointManager()
	{
		return this.syncManager;
	}
	
	public Repository getRepository()
	{
		return this.repository;
	}
	
	public RegionStore getRegionStore()
	{
		return this.regionStore;
	}
	
	public TimeManager getTimeManager()
	{
		return this.timeManager;
	}
	
	public OwnershipManager getOwnershipManager()
	{
		return this.ownershipManager;
	}
	
	public MomManager getMomManager()
	{
		return this.momManager;
	}
	
	public void addRawFomModules( List<FomModule> modules )
	{
	    // 根据 1516e 规范，仅记录对 FOM 添加了内容的模块。为简化处理，我们假设只要设计名称（designator）不同，则该模块即添加了新内容
		Set<String> existingDesignators = new HashSet<>();
		for( FomModule existingModule : this.fomModules )
			existingDesignators.add( existingModule.getDesignator() );
		
		for( FomModule newModule : modules )
		{
			String newDesignator = newModule.getDesignator();
			if( !existingDesignators.contains(newDesignator) )
				this.fomModules.add( newModule );
		}
	}
	
	public List<FomModule> getRawFomModules()
	{
		return new ArrayList<>( this.fomModules );
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	///  Federate Management   ////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 该方法在联邦首次创建时被调用（注意方法名使用了过去时态）。它允许联邦执行其所需的任何本地配置或控制操作，类似于一次“启动”调用。
     */
	public void createdFederation()
	{
		// Start the outgoing queue processor
		this.outgoingProcessor.start();
		logger.debug( "Outgoing message processor thread started" );
	}
	
    /**
     * 该方法在联邦被销毁之后调用。它允许联邦清理其可能占用的任何重要资源。
     */
	public void destroyedFederation()
	{
		// Stop the outgoing queue processor
		logger.debug( "Interrupting the outgoing message processor thread" );
		this.outgoingProcessor.interrupt();
		try
		{
			this.outgoingProcessor.join( 5000 );
		}
		catch( InterruptedException ie )
		{}
	}

    /**
     * 将指定的联邦成员（federate）添加到本联邦中。
     *
     * @param federate 要加入本联邦的联邦成员
     * @throws JFederateNameAlreadyInUse 如果该名称已被另一个联邦成员使用
     */
	public int joinFederate( Federate federate ) throws JFederateNameAlreadyInUse
	{
		// 确保联邦成员名称不重复
		// TODO 需要修改，使得联邦成员名称不必唯一
		for( Federate temp : federates.values() )
		{
			if( temp.getFederateName().equalsIgnoreCase(federate.getFederateName()) )
				throw new JFederateNameAlreadyInUse( federate.getFederateName() );
		}
		
		this.addRawFomModules( federate.getRawFomModules() );
		
		// 为该联邦成员分配一个句柄并将其存储
		federate.setFederateHandle( federateHandleCounter.incrementAndGet() );
		this.federates.put( federate.getFederateHandle(), federate );
		
		// 存储该联邦成员所使用的连接
		this.federateConnections.add( federate.getConnection() );
		
		return federate.getFederateHandle();
	}

    /**
     * 移除指定的联邦成员出联邦。这将不仅移除该联邦成员，还会重新评估我们现有的连接集合，以检查分配给该联邦成员的连接是否已不再被使用（如果未被使用，则将其从连接池中移除）。
     *
     * @param federate 要从联邦中移除的联邦成员
     * @throws JFederateNotExecutionMember 如果该联邦成员不在联邦中
     */
	public void resignFederate( Federate federate )
	{
	    // 确保该联邦成员存在于联邦中
		if( federates.containsKey(federate.getFederateHandle()) == false )
		{
			throw new JFederateNotExecutionMember( "federate [%s] not part of federation [%s]",
			                                       federate.getFederateName(),
			                                       federationName );
		}

		// 从联邦成员存储中移除该联邦成员
		this.federates.remove( federate.getFederateHandle() );
		
		// 移除该联邦成员所使用的连接（除非还有其他联邦成员正在使用该连接）
		RtiConnection connection = federate.getConnection();
		boolean stillUsed = federates.values().stream()
		                                      .filter( temp -> connection.equals(temp.getConnection()) )
		                                      .findAny()
		                                      .isPresent();
		if( stillUsed == false )
			federateConnections.remove( connection );
	}

	public Set<Federate> getFederates()
	{
		return new HashSet<>( this.federates.values() );
	}
	
	public Federate getFederate( String name )
	{
		for( Federate federate: federates.values() )
		{
			if( federate.getFederateName().trim().equalsIgnoreCase(name) )
				return federate;
		}
		
		return null;
	}
	
	public Federate getFederate( int federateHandle )
	{
		return federates.get( federateHandle );
	}
	
	public int getFederateHandle( String name )
	{
		int value = PorticoConstants.NULL_HANDLE;
		for( Federate federate : federates.values() )
		{
			if( federate.getFederateName().trim().equalsIgnoreCase("") )
				return federate.getFederateHandle();
		}
		
		return value;		
	}
	
	public Set<Integer> getFederateHandles()
	{
		return federates.keySet();
	}
	
	public boolean containsFederate( int federateHandle )
	{
		return federates.keySet().contains( federateHandle );
	}
	
	public boolean containsFederate( String name )
	{
		for( Federate federate : federates.values() )
		{
			if( federate.getFederateName().trim().equalsIgnoreCase(name) )
				return true;
		}
		
		return false;
	}
	
	public boolean containsFederates()
	{
		return federates.isEmpty() == false;
	}

    /**
     * @return 返回所有已加入该联邦的联邦成员所使用的连接集合。该方法仅返回唯一的连接实例，即使多个联邦成员使用同一个连接，也只返回一次。
     */
	public Set<RtiConnection> getFederateConnections()
	{
		return this.federateConnections;
	}

	///////////////////////////////////////////////////////////////////////////////////////
	///  Message Sending Methods   ////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 将给定的控制消息排队，以便稍后发送到其目标联邦成员。实际发送将在之后由 {@link OutgoingMessageProcessor} 处理完成。
     * 
     * @param message 要排队的消息
     */
	public final void queueControlMessage( PorticoMessage message )
	{
		message.setIsFromRti( true );
		message.setSourceFederateIfNull( PorticoConstants.RTI_HANDLE );
		message.setTargetFederation( federationHandle );
		if( this.outgoingQueue.offer(message) == false )
			logger.warn( "Message could not be added to outgoing queue (overflow): "+message.getType() );
	}

    /**
     * 将给定消息广播到所有与此联邦相连的连接（除了发送该消息的连接）。<br>
     * 注意：即使多个联邦成员使用同一个连接，我们也只维护该连接的一个实例。<br>
     * 因此，如果10个联邦成员分布在3个连接上，调用此方法将触发两次广播请求。<br>
     * <p/>
     * 另外请注意，消息不会回环发送到发送方连接。如果某个连接复用（multiplexing）了多个联邦成员，它必须在内部自行处理向这些成员的广播。<br>
     * 
     * @param message 要广播的消息
     * @param sender 接收到消息的来源连接
     */
	public final void queueDataMessage( PorticoMessage message, RtiConnection sender )
	{
		// Reflect data message into the message sink so that the Mom Handlers can get a go at it
		this.incomingSink.process( new MessageContext(message) );
		
		for( RtiConnection connection : federateConnections )
		{
			if( connection == sender )
				continue;
			else
				connection.sendDataMessage( message );
		}
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------

	///////////////////////////////////////////////////////////////////////////////////////
	///  PRIVATE CLASS: Outgoing Message Processor   //////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
    /**
     * 该类负责处理传出消息队列——将消息从队列中取出，并转发给联邦中各个存在的连接。
     */
	private class OutgoingMessageProcessor extends Thread
	{
		public OutgoingMessageProcessor()
		{
			super( federationName+"-outgoing" );
			super.setDaemon( true );
		}
		
		@Override
		public void run()
		{
			while( Thread.interrupted() == false )
			{
				try
				{
					// 过去下一条消息
					PorticoMessage message = outgoingQueue.take();
					sendMessage( message );
				}
				catch( InterruptedException ie )
				{
					logger.warn( "Outgoing processor was interrupted, time to exit..." );
					return;
				}
			}
		}

		private void sendMessage( PorticoMessage message )
		{
		    // FIXME - 需要更智能地处理，仅将控制消息路由到目标联邦成员所在的连接
			MessageContext ctx = new MessageContext( message );
			for( RtiConnection connection : federateConnections )
			{
				try
				{
					connection.sendControlRequest( ctx );
					if( ctx.isErrorResponse() && ctx.hasResponse() )
						throw ctx.getErrorResponseException();
					
					if( logger.isTraceEnabled() )
						logger.trace( "Passed message [%s] to connection [%s]", message.getType(), connection.getName() );
				}
				catch( Exception e )
				{
					logger.warn( "Error sending message [%s] via connection [%s]",
					             message.getType(), connection.getName(), e );
				}				
			}
		}
	}
}
