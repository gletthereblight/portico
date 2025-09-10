/*
 *   Copyright 2008 The Portico Project
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
package org.portico2.common.services.federation.msg;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.portico.lrc.model.OCInstance;
import org.portico.lrc.model.ObjectModel;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.messaging.MessageType;
import org.portico2.common.services.time.data.TimeStatus;

/**
 * 为了实现正确的分布式运行，每个联邦成员都需要了解联邦中存在的其他所有联邦成员。<br>
 * 在加入联邦时，每个联邦成员会发送一条加入通知，以便其他成员知道它的存在。<br>
 * 然而，这种方法只能让已存在的联邦成员获知有新成员加入。<br>
 * 由于没有其他机制，新加入的联邦成员将无法了解在它之前加入联邦的其他所有成员。<br>
 * 此消息旨在解决这一问题，当一个联邦成员收到加入通知时，它应广播一条包含自身信息的该消息实例，从而向新加入的成员告知自己的存在。<br>
 * 尽管从网络流量的角度来看这种方法并非理想，但这是最简单的信息分发方式，而且由于联邦成员加入模拟的频率相对较低，因此额外流量带来的问题最多只是一个微不足道的顾虑。<br>
 * 
 * @author gaop
 * @date 2025/09/10
 */
public class RoleCall extends PorticoMessage implements Externalizable
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	private static final long serialVersionUID = 98121116105109L;

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private String federateName;
	private String federateType;
	private TimeStatus timeStatus;
	private OCInstance[] controlledObjects;
	private HashMap<String,byte[]> syncPointTags;
	private HashMap<String,Boolean> syncPointStatus; // label/whether federate has acheived it or not

	private List<ObjectModel> additionalModules; // populated in 1516e only

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	/** <b>DO NOT USE</b> This is only provided because the deserialization of Externalizable
	    objects requires that the class have a 0-arg constructor */
	public RoleCall()
	{
		super();
	}

	public RoleCall( int federateHandle,
	                 String federateName, 
	                 String federateType, 
	                 TimeStatus status,
	                 OCInstance[] controlledObjects )
	{
		this();
		this.federateName = federateName;
		this.federateType = federateType;
		this.sourceFederate = federateHandle;
		this.federateName = federateName;
		this.federateType = federateType;
		this.timeStatus = status;
		this.controlledObjects = controlledObjects;
		this.syncPointStatus = new HashMap<String,Boolean>();
		this.syncPointTags = new HashMap<String,byte[]>();
		setImmediateProcessingFlag( true );

		// set in 1516e when the joining federate has provided additional modules
		this.additionalModules = new ArrayList<ObjectModel>();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	@Override
	public MessageType getType()
	{
		throw new RuntimeException( "ROLE CALL IS NO LONGER SUPPORTED" );
	}

	public String getFederateName()
	{
		return this.federateName;
	}
	
	public void setFederateName( String federateName )
	{
		this.federateName = federateName;
	}
	
	public String getFederateType()
	{
		return this.federateType;
	}
	
	public void setFederateType( String federateType )
	{
		this.federateType = federateType;
	}
	
	public TimeStatus getTimeStatus()
	{
		return this.timeStatus;
	}
	
	public OCInstance[] getControlledObjects()
	{
		return this.controlledObjects;
	}
	
	/**
	 * This will NEVER return null. If there are no points, it will return an empty map.
	 */
	public HashMap<String,Boolean> getSyncPointStatus()
	{
		if( syncPointStatus == null )
			return new HashMap<String,Boolean>();
		else
			return syncPointStatus;
	}
	
	/**
	 * This will NEVER return null. If there are no points, it will return an empty map.
	 */
	public HashMap<String,byte[]> getSyncPointTags()
	{
		if( syncPointTags == null )
			return new HashMap<String,byte[]>();
		else
			return syncPointTags;	
	}

	@Override
	public boolean isImmediateProcessingRequired()
	{
		return true;
	}
	
	public void addAdditionalFomModule( ObjectModel model )
	{
		this.additionalModules.add( model );
	}
	
	public void addAdditionalFomModules( List<ObjectModel> modules )
	{
		this.additionalModules.addAll( modules );
	}
	
	public List<ObjectModel> getAdditionalFomModules()
	{
		return this.additionalModules;
	}

	public boolean hasAdditionalFomModules()
	{
		return !this.additionalModules.isEmpty();
	}

	/////////////////////////////////////////////////////////////
	/////////////////// Serialization Methods ///////////////////
	/////////////////////////////////////////////////////////////
	@SuppressWarnings("unchecked")
	public void readExternal( ObjectInput input ) throws IOException, ClassNotFoundException
	{
		super.readExternal( input );
		this.federateName = input.readUTF();
		this.timeStatus = (TimeStatus)input.readObject();
		this.controlledObjects = (OCInstance[])input.readObject();
		
		// read unsynchronized point data
		boolean exists = input.readBoolean();
		if( exists )
			this.syncPointStatus = (HashMap<String,Boolean>)input.readObject();

		exists = input.readBoolean();
		if( exists )
			this.syncPointTags = (HashMap<String,byte[]>)input.readObject();

		exists = input.readBoolean();
		if( exists )
			this.additionalModules = (ArrayList<ObjectModel>)input.readObject();
		else
			this.additionalModules = new ArrayList<ObjectModel>(); // empty list
	}
	
	public void writeExternal( ObjectOutput output ) throws IOException
	{
		super.writeExternal( output );
		output.writeUTF( this.federateName );
		output.writeObject( this.timeStatus );
		output.writeObject( this.controlledObjects );
		
		// write unsynchronized point data, if there is none, write false as the flag
		// to signal this (to avoid sending an empty hashmap)
		if( this.syncPointStatus == null || this.syncPointStatus.isEmpty() )
		{
			output.writeBoolean( false );
		}
		else
		{
			output.writeBoolean( true );
			output.writeObject( this.syncPointStatus );
		}

		if( this.syncPointTags == null || this.syncPointTags.isEmpty() )
		{
			output.writeBoolean( false );
		}
		else
		{
			output.writeBoolean( true );
			output.writeObject( this.syncPointTags );
		}
		
		if( this.additionalModules == null || this.additionalModules.isEmpty() )
		{
			output.writeBoolean( false );
		}
		else
		{
			output.writeBoolean( true );
			output.writeObject( this.additionalModules );
		}
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
