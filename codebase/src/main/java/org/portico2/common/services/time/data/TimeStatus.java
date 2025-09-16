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
package org.portico2.common.services.time.data;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.portico2.rti.services.time.data.TimeManager;

/**
 * 该类包含描述特定联邦成员当前时间相关状态的一系列信息。<br>
 * 在 {@link TimeManager} 内部，每个联邦成员对应一个该类的实例。<br>
 */
public class TimeStatus implements Externalizable
{
	//----------------------------------------------------------
	//                      ENUMERATIONS
	//----------------------------------------------------------
	public enum TriState{ ON, PENDING, OFF };

	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	public TriState constrained   = TriState.OFF; // 该联邦成员是否受约束？
	public TriState regulating    = TriState.OFF; // 该联邦成员是否在进行时间调节？
	public TAR      advancing     = TAR.NONE;     // 当前的时间推进请求状态
	public double   currentTime   = 0.0;          // 当前联邦成员的时间
	public double   requestedTime = 0.0;          // 联邦成员上一次请求的时间
	public double   lookahead     = 0.0;          // 该联邦成员的前瞻值
	public double   lbts          = 0.0;          // 联邦成员的 LBTS（请求时间 + 前瞻值）
	public boolean  asynchronous  = false;        // “HLA 消息”是否应在时间推进请求（TAR）期间递送

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	/**
     * 将我们的时间状态重置为默认值。在首次加入联邦时执行此操作。
     */
	public void reset()
	{
		this.constrained   = TriState.OFF; // is the federate constrained?
		this.regulating    = TriState.OFF; // is the federate regulating?
		this.advancing     = TAR.NONE;     // the current time advancement request status
		this.currentTime   = 0.0;          // the current federate time
		this.requestedTime = 0.0;          // the time the federate last requested
		this.lookahead     = 0.0;          // the lookahead value for the federate
		this.lbts          = 0.0;          // the federate-lbts (requested time+lookahead)
		this.asynchronous  = false;        // should "HLA messages" be delivered with TAR?
	}
	
    /**
     * 该方法将在时间推进被批准后，进行所有必要的内部状态更新。<br>
     * 注意，该方法不会检查推进是否有效，例如，新时间是否高于之前的时间，而是直接假设推进有效，并执行所需的所有状态变更。<br>
     * </p>
     * 
     * 此方法会将该联邦成员的推进状态设置为 {@link TAR#PROVISIONAL}。<br>
     * 当授予回调被处理后，该状态应被切换回 {@link TAR#NONE}。<br>
     */
	public void advanceFederate( double newTime )
	{
		this.currentTime = newTime;
		this.lbts = this.currentTime + this.lookahead;
		this.advancing = TAR.PROVISIONAL;
	}
	
	/**
	 * Same as calling {@link #advanceFederate(double) advanceFederate(getRequestedTime())}
	 */
	public void advanceFederate()
	{
		advanceFederate( requestedTime );
	}
	
    /**
     * 假设联邦 LBTS 为给定值，如果可以进行时间推进授权，则返回 <code>true</code>。<br>
     * 该方法仅进行检查，不会修改任何内部状态。
     * <p/>
     * 如果本地联邦成员的时间推进请求已被授权，但授权回调尚未送达，该方法也将返回 <code>false</code>。<br>
     */
	public boolean canAdvance( double federationLbts )
	{
		// if there is no pending advancement, we're not ready
		if( advancing == TAR.NONE || advancing == TAR.PROVISIONAL )
			return false;
		
		// if we're not constrained we can advance all we want
		if( !isConstrained() )
			return true;
		
		// we're regulating and have an outstanding advance request, can we advance?
		// FIXME if TAR then compare with "<", if TARA the compare with "<="
		return requestedTime < federationLbts;
	}

    /**
     * 适当地修改状态，以反映已向给定时间发出新的时间推进请求。<br>
     * 该方法不会检查变更是否有效，而是直接进行状态修改（设置请求时间、切换推进标志等）。<br>
     */
	public void timeAdvanceRequested( double requestedTime )
	{
		this.requestedTime = requestedTime;
		this.lbts = this.requestedTime + this.lookahead;
		this.advancing = TAR.REQUESTED;
	}

    /**
     * 将推进状态设置为 {@link TAR#NONE}。<br>
     * 对于本地联邦成员，应在其回调被送达后立即调用此方法。<br>
     * 参数给定的时间将被设置为当前时间（同时也作为请求时间）。<br>
     */
	public void advanceGrantCallbackProcessed( double newTime )
	{
		this.advancing = TAR.NONE;
		this.currentTime = newTime;
		this.requestedTime = newTime;
	}

	///////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////// Basic Get and Set Methods ////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////
	public boolean isRegulating()
	{
		return regulating == TriState.ON;
	}

	public boolean isRegulatingPending()
	{
		return regulating == TriState.PENDING;
	}

	public TriState getRegulating()
	{
		return regulating;
	}

	public void setRegulating( TriState regulating )
	{
		this.regulating = regulating;
	}

	public boolean isConstrained()
	{
		return constrained == TriState.ON;
	}

	public boolean isConstrainedPending()
	{
		return constrained == TriState.PENDING;
	}

	public TriState getConstrained()
	{
		return constrained;
	}

	public void setConstrained( TriState constrained )
	{
		this.constrained = constrained;
	}

	public double getLbts()
	{
		return lbts;
	}

	public TAR getAdvancing()
	{
		return advancing;
	}
	
	/**
	 * This method returns <code>true</code> if the federate is currently waiting for a time
	 * advance grant AND once has not yet been received either internally or through a callback.
	 * There is a period of time between when a grant is granted and when the callback is delivered,
	 * this method returns <code>true</code> as soon as the advance is granted, even if the callback
	 * has not been delivered yet.
	 */
	public boolean isInAdvancingState()
	{
		if( advancing == TAR.NONE || advancing == TAR.PROVISIONAL )
			return false;
		else
			return true;
	}

	/**
	 * This method returns <code>true</code> if the federate is currently waiting for a time
	 * advance grant. <b>NOTE:</b> This includes the {@link TAR#PROVISIONAL} status, where an
	 * advance has been granted but the callback has not yet been delivered.
	 * There is a period of time between when a grant is granted and when the callback is delivered,
	 * this method returns <code>true</code> as soon as the advance is granted, even if the callback
	 * has not been delivered yet.
	 * <p/>
	 * Be careful with this, it has caused problems before. Only use it if you want to know whether
	 * the actual federate has an outstanding request that has not been satisifed through a
	 * callback yet.
	 */
	public boolean isAdvanceRequestOutstanding()
	{
		if( advancing == TAR.NONE )
			return false;
		else
			return true;
	}

	public double getCurrentTime()
	{
		return currentTime;
	}

	public void setCurrentTime( double currentTime )
	{
		this.currentTime = currentTime;
	}

	public double getRequestedTime()
	{
		return requestedTime;
	}

	public void setRequestedTime( double requestedTime )
	{
		this.requestedTime = requestedTime;
	}

	public double getLookahead()
	{
		return lookahead;
	}

	public void setLookahead( double lookahead )
	{
		this.lookahead = lookahead;
	}

	public boolean isAsynchronous()
	{
		return this.asynchronous;
	}
	
	public void setAsynchronous( boolean asynchronous )
	{
		this.asynchronous = asynchronous;
	}
	
	/**
	 * Creates and returns a new {@link TimeStatus} instance that is a direct copy of this one.
	 */
	public TimeStatus copy()
	{
		TimeStatus newStatus = new TimeStatus();
		newStatus.constrained = constrained;
		newStatus.regulating = regulating;
		newStatus.advancing = advancing;
		newStatus.currentTime = currentTime;
		newStatus.requestedTime = requestedTime;
		newStatus.lookahead = lookahead;
		newStatus.lbts = lbts;
		return newStatus;
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append( "{\n\tcurrentTime=   " );
		builder.append( currentTime );
		builder.append( "\n\trequestedTime= " );
		builder.append( requestedTime );
		builder.append( "\n\tlookahead=     " );
		builder.append( lookahead );
		builder.append( "\n\tlbts=          " );
		builder.append( lbts );
		builder.append( "\n\tconstained=    " );
		builder.append( constrained );
		builder.append( "\n\tregulating=    " );
		builder.append( regulating );
		builder.append( "\n}" );		

		return builder.toString();
	}

	/////////////////////////////////////////////////////////////
	/////////////////// Serialization Methods ///////////////////
	/////////////////////////////////////////////////////////////
	public void readExternal( ObjectInput input ) throws IOException, ClassNotFoundException
	{
		this.constrained = (TimeStatus.TriState)input.readObject();
		this.regulating = (TimeStatus.TriState)input.readObject();
		this.advancing = (TAR)input.readObject();
		this.currentTime = input.readDouble();
		this.requestedTime = input.readDouble();
		this.lookahead = input.readDouble();
		this.lbts = input.readDouble();
		this.asynchronous = input.readBoolean();
	}
	
	public void writeExternal( ObjectOutput output ) throws IOException
	{
		output.writeObject( this.constrained );
		output.writeObject( this.regulating );
		output.writeObject( this.advancing );
		output.writeDouble( this.currentTime );
		output.writeDouble( this.requestedTime );
		output.writeDouble( this.lookahead );
		output.writeDouble( this.lbts );
		output.writeBoolean( this.asynchronous );
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
