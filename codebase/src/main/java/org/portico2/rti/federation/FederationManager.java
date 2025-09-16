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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.portico.impl.HLAVersion;
import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.model.ObjectModel;
import org.portico2.rti.RTI;

/**
 * {@link FederationManager} 的作用是跟踪和管理 RTI 实例中所包含的各种正在运行的联邦的状态信息。
 */
public class FederationManager
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private Map<String,Federation> federationsByName;
	private Map<Integer,Federation> federationsByHandle;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public FederationManager()
	{
		this.federationsByName = new HashMap<>();
		this.federationsByHandle = new HashMap<>();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	public Collection<Federation> getActiveFederations()
	{
		return federationsByHandle.values();
	}

	public Federation getFederation( String name )
	{
		return federationsByName.get( name );
	}
	
	public Federation getFederation( int federationHandle )
	{
		return federationsByHandle.get( federationHandle );
	}
	
	public boolean containsFederation( String name )
	{
		return federationsByName.containsKey( name );
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	///  Federation Management   ///////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 根据提供的信息创建一个新的联邦，并在返回之前将其存储到管理器中.
     * 
     * @param rti 该联邦所处的 RTI
     * @param name 联邦的名称
     * @param fom 联邦的对象模型
     * @param hlaVersion 联邦应加载消息处理器所对应的 HLA 规范版本
     * @return 一个全新的 {@link Federation} 实例，该实例现已激活并存储在管理器中
     * @throws JConfigurationException 如果在配置处理器时出现问题
     */
	public synchronized Federation createFederation( RTI rti,
	                                                 String name,
	                                                 ObjectModel fom,
	                                                 HLAVersion hlaVersion )
		throws JConfigurationException
	{
		Federation federation = new Federation( rti, name, fom, hlaVersion );
		this.federationsByName.put( federation.getFederationName(), federation );
		this.federationsByHandle.put( federation.getFederationHandle(), federation );
		federation.createdFederation();
		return federation;
	}
	
    /**
     * 销毁联邦.
     * 
     * @param federation
     */
	public synchronized void destroyFederation( Federation federation )
	{
		federation.destroyedFederation();
		this.federationsByName.remove( federation.getFederationName() );
		this.federationsByHandle.remove( federation.getFederationHandle() );
	}
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
