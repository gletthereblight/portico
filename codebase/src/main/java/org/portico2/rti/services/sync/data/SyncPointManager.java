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
package org.portico2.rti.services.sync.data;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.portico.lrc.compat.JRTIinternalError;
import org.portico.lrc.compat.JSynchronizationLabelNotAnnounced;
import org.portico.lrc.services.saverestore.data.SaveRestoreTarget;
import org.portico2.rti.federation.Federation;
import org.portico2.rti.services.sync.data.SyncPoint.Status;

/**
 * 该类负责管理与同步点相关的所有记录工作。<br>
 * 它负责处理同步点在各个状态之间的转换，并跟踪记录各个联邦成员已达成的同步点。<br>
 * 此外，该同步点还会维护一个集合，用于记录所有尝试注册该同步点的联邦成员的句柄。<br>
 */
public class SyncPointManager implements SaveRestoreTarget
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private Federation federation;
	private HashMap<String,SyncPoint> syncPoints;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public SyncPointManager( Federation federation )
	{
		this.federation = federation;
		this.syncPoints = new HashMap<String,SyncPoint>();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
    /**
     * @see #registerSyncPoint(String, byte[], Set, int)
     * @param label
     * @param tag
     * @param registrant
     * @return
     * @throws JRTIinternalError
     */
	public synchronized SyncPoint registerSyncPoint( String label, byte[] tag, int registrant )
		throws JRTIinternalError
	{
		return registerSyncPoint( label, tag, null, registrant );
	}
	
	/**
     * 创建并注册一个全联邦范围的同步点。如果该标签已存在，则会抛出异常。
     *
     * @param label 要创建的同步点的标签
     * @param tag 注册该同步点时提供的标签
     * @param registrant 注册该同步点的联邦成员的句柄
     * @return 新创建并注册的同步点
     * @throws JRTIinternalError 如果该同步点已存在
     */
	public synchronized SyncPoint registerSyncPoint( String label,
	                                                 byte[] tag,
	                                                 Set<Integer> federates,
	                                                 int registrant )
		throws JRTIinternalError
	{
		if( syncPoints.containsKey(label) )
			throw new JRTIinternalError( "Synchronziation Point already exists: label="+label );

		SyncPoint point = new SyncPoint( label, tag, federates, registrant );
		syncPoints.put( label, point );
		return point;
	}

	/**
	 * Record that the given federate has achieved the sync point with the provided label.
	 * After each registration we also recalculate to see if the federation is now synchronized.
	 * The point is returned at the conclusion of this (and can be queried for status).
	 * 
	 * @param label The label of the point
	 * @param federateHandle The federate that achieved the point
	 * @return The sync point that was marked as achieved for the given federate
	 * @throws JSynchronizationLabelNotAnnounced If we can't find a point with the given label
	 */
	public synchronized SyncPoint achieveSyncPoint( String label, int federateHandle )
		throws JSynchronizationLabelNotAnnounced
	{
		// Get the point
		SyncPoint point = syncPoints.get( label );
		if( point == null )
			throw new JSynchronizationLabelNotAnnounced( "Synchronization Point not announced: "+label );
		
		// Record that the federate has achieved the point
		point.federateAchieved( federateHandle );
		
		// Update the point status just in case everyone has achieved it
		updatePointStatus( point );
		
		return point;
	}
	
	private void updatePointStatus( SyncPoint point )
	{
		// Get the set of federates that need to be synchronized
		Set<Integer> federateHandles = null;
		if( point.isFederationWide() )
			federateHandles = new HashSet<>( federation.getFederateHandles() );
		else
			federateHandles = point.getFederates();
		
		// Check that every federate in the federation has achieved the point
		// If they haven't, don't change it
		for( int federateHandle : federation.getFederateHandles() )
		{
			if( point.hasFederateAchieved(federateHandle) == false )
				return;
		}
		
		// If we get here we know that everyone in the federation (or everyone in the
		// registration set) has achieved the point
		point.status = Status.SYNCHRONIZED;
	}

	/**
	 * Return <code>true</code> if the point with the given label is marked as "synchronized".
	 * If the label is not known, an exception is thrown
	 * @param label The label to check the sync status for
	 * @return <code>true</code> if the point is achieved, <code>false</code> otherwise
	 * @throws JSynchronizationLabelNotAnnounced If the label is no known
	 */
	public synchronized boolean isSynchronized( String label )
		throws JSynchronizationLabelNotAnnounced
	{
		SyncPoint point = syncPoints.get( label );
		if( point == null )
			throw new JSynchronizationLabelNotAnnounced( "Synchronization Point not announced: "+label );

		return point.isSynchronized();
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public synchronized boolean containsPoint( String label )
	{
		return syncPoints.containsKey( label );
	}
	
	public synchronized SyncPoint getPoint( String label )
	{
		return syncPoints.get( label );
	}
	
	public synchronized SyncPoint removePoint( String label )
	{
		return syncPoints.remove( label );
	}
	
	public synchronized Collection<SyncPoint> getAllPoints()
	{
		return syncPoints.values();
	}

	public synchronized Set<String> getAllUnsynchronizedLabels()
	{
		return syncPoints.values().stream()
		                          .filter( p -> p.isFederationWide() && !p.isSynchronized() )
		                          .map( p -> p.label )
		                          .collect( Collectors.toSet() );
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////// Save/Restore Methods /////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////
	public void saveToStream( ObjectOutput output ) throws Exception
	{
		output.writeObject( this.syncPoints );
	}

	@SuppressWarnings("unchecked")
	public void restoreFromStream( ObjectInput input ) throws Exception
	{
		this.syncPoints = (HashMap<String,SyncPoint>)input.readObject();
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
