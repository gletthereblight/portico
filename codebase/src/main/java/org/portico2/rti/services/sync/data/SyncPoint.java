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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 该类负责跟踪某个特定同步点的所有相关信息。核心作用是确保联邦中的所有参与者在特定时刻达到同步状态。<br>
 * 每个同步点都有一个关联的状态，用于说明该点在同步过程中的当前所处阶段。<br>
 * 更多细节请见下 {@link Status}。<br>
 * <p/>
 * 
 * 关于谁有权注册同步点的决策采用“先到先得（first-come, first-serve）”的方式处理。
 * <p/>
 * 
 * 该类还会跟踪与该同步点关联的所有联邦成员。 <br>
 * 通常情况下，一个同步点是“全联邦范围”的（即面向所有联邦成员）。然而，在创建时，同步点的成员资格可以被限制。<br>
 * 如果属于这种情况，则只有句柄包含在该同步点中的联邦成员才会获知其存在，并且在判断该同步点是否达成时，也仅考虑这些联邦成员。<br>
 */
public class SyncPoint implements Serializable
{
	//----------------------------------------------------------
	//                      ENUMERATIONS
	//----------------------------------------------------------
    /**
     * 同步点的当前状态可以是以下之一：<br>
     * <ul>
     * <li><b>REQUESTED（已请求）</b>：本地联邦成员已请求注册该同步点</li>
     * <li><b>PENDING（待定）</b>：其他某个联邦成员已对该同步点发出注册请求</li>
     * <li><b>ANNOUNCED（已宣告）</b>：该同步点已被宣告</li>
     * <li><b>ACHIEVED（已达成）</b>：本地联邦成员已达成该同步点</li>
     * <li><b>SYNCHRONIZED（已同步）</b>：所有联邦成员均已达成该同步点</li>
     * </ul>
     */
	public enum Status
	{
		ANNOUNCED,     // the point has been announced
		ACHIEVED,      // the local federate has achieved the point
		SYNCHRONIZED;  // all federates have achieved the point
	}

	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	private static final long serialVersionUID = 98121116105109L;

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	protected String label;
	protected byte[] tag;
	protected Set<Integer> federates;
	protected int registrant;
	protected HashSet<Integer> achieved;
	protected Status status;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	/**
	 * Create a new sync point with the given label, tag and set of federate handles who are part
	 * of the point. If it is a federation-wide sync point, pass <code>null</code> for the handles
	 * parameter (or an empty set).
	 */
	protected SyncPoint( String label, byte[] tag, Set<Integer> federates, int registrant )
	{
		this.label = label;
		this.tag = tag;
		this.achieved = new HashSet<Integer>();
		this.registrant = registrant;
		this.status = Status.ANNOUNCED;
		if( federates == null )
			this.federates = new HashSet<Integer>();
		else
			this.federates = federates;
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	///////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////// Registration Request Handling Methods //////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////
	public int getRegistrant()
	{
		return registrant;
	}
	
	/**
	 * Returns <code>true</code> if this sycn point is a federation-wide (non-restricted) point
	 * that all federates currently in a federation need to have achieved before it becomes
	 * synchronized. This method should always return the opposite of {@link #isRestricted()}.
	 */
	public boolean isFederationWide()
	{
		return this.federates == null || this.federates.isEmpty();
	}
	
	/**
	 * Returns <code>true</code> if this sync point is a restricted one (that is, it isn't
	 * federation-wide). The return value of this method should always be the opposite of
	 * {@link #isFederationWide()}.
	 */
	public boolean isRestricted()
	{
		if( this.federates != null && this.federates.isEmpty() == false )
			return true;
		else
			return false;
	}

	/**
	 * The given federate has achieved this synchronization point. Record it.
	 */
	public void federateAchieved( int federateHandle )
	{
		this.achieved.add( federateHandle );
	}
	
	/**
	 * Returns <code>true</code> if the given federate has said it has achieved this point,
	 * <code>false</code> otherwise.
	 */
	public boolean hasFederateAchieved( int federateHandle )
	{
		return this.achieved.contains( federateHandle );
	}
	
	public boolean isSynchronized()
	{
		return status == Status.SYNCHRONIZED;
	}
	
    /**
     * 检查给定集合中的所有联邦成员是否都已达成此同步点。
     * <p/>
     * 
     * 如果这是一个<b>全联邦范围</b>的同步点，则传入的联邦成员集合应为当前联邦中所有成员的集合。<br>
     * 在这种情况下，如果集合中所有联邦成员的句柄都已被记录为已达成该点，此方法将返回true。<br>
     * <p/>
     * 
     * 如果这是一个<b>受限</b>的同步点，传入的集合应为该同步点所限定的联邦成员集合。<br>
     * <p/>
     * 
     * 检查给定集合中的所有联邦成员是否已达成该同步点。<br>
     * 如果这是私有同步点，应向该方法传入 <code>null</code>（这将导致方法使用内部存储的联邦成员句柄集合）。<br>
     * 如果这是全联邦范围的同步点，则应传入所有成员句柄的集合。<br>
     * 假设向该方法传入了有效的集合，当集合中所有联邦成员都已达成该同步点时，方法将返回<code>true</code>。
     */
	public boolean isSynchronized( Set<Integer> allFederates )
	{
		// if the given set is null, it means we should defer to the set we have locally
		//                                 -OR-
		// if we are a resticted point, ignore the given federates and use our local set
		if( allFederates == null || isRestricted() )
			allFederates = this.federates;

		// check each of the fedeates to see if it has been marked as having achieved the point
		for( Integer federateHandle : allFederates )
		{
			if( achieved.contains(federateHandle) == false )
				return false;
		}
		
		return true;
	}
	
	public String getLabel()
	{
		return this.label;
	}
	
	public byte[] getTag()
	{
		return this.tag;
	}
	
	public Status getStatus()
	{
		return this.status;
	}
	
	/**
	 * Returns the set of all federate handles that are associated with this point. If this is a
	 * federation-wide point, returns an empty set.
	 */
	public Set<Integer> getFederates()
	{
		return this.federates;
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append( "{\n\tlabel=       " );
		builder.append( label );
		builder.append( "\n\tstatus=      " );
		builder.append( status );
		builder.append( "\n\tregistrant=  " );
		builder.append( registrant );
		builder.append( "\n\tfederates=   " );
		builder.append( federates );
		builder.append( "\n\tachieved=    " );
		builder.append( achieved );
		builder.append( "\n}" );		

		return builder.toString();
	}
}
