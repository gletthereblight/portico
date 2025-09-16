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
package org.portico2.common.services.pubsub.data;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.portico.lrc.compat.JAttributeNotDefined;
import org.portico.lrc.compat.JInteractionClassNotDefined;
import org.portico.lrc.compat.JInteractionClassNotPublished;
import org.portico.lrc.compat.JInteractionClassNotSubscribed;
import org.portico.lrc.compat.JInvalidRegionContext;
import org.portico.lrc.compat.JObjectClassNotDefined;
import org.portico.lrc.compat.JObjectClassNotPublished;
import org.portico.lrc.compat.JObjectClassNotSubscribed;
import org.portico.lrc.compat.JRTIinternalError;
import org.portico.lrc.compat.JRegionNotKnown;
import org.portico.lrc.model.ACMetadata;
import org.portico.lrc.model.ICMetadata;
import org.portico.lrc.model.OCMetadata;
import org.portico.lrc.model.ObjectModel;
import org.portico.lrc.model.RegionInstance;
import org.portico.lrc.services.saverestore.data.SaveRestoreTarget;
import org.portico2.common.PorticoConstants;
import org.portico2.common.services.ddm.data.RegionStore;

/**
 * 兴趣管理器（Interest Manager）用于跟踪联邦中各联邦成员的发布与订阅信息。<br>
 * 该管理器通过为每个对象类（Object Class）和交互类（Interaction Class）存储一组 {@link OCInterest} 和 {@link ICInterest} 实例来工作。<br>
 * 这些类保存了哪些联邦成员对它们感兴趣以及兴趣类型的信息，也就是说，我们并非为每个联邦成员单独存储一套信息，而是按特定的对象类/交互类进行分组存储（每个分组内部包含对该类感兴趣的联邦成员相关信息）。<br>
 */
public class InterestManager implements SaveRestoreTarget
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private ObjectModel fom;
	private RegionStore regionStore;
	private Map<OCMetadata,OCInterest> pObjects;       // 对象类发布兴趣
	private Map<OCMetadata,OCInterest> sObjects;       // 对象类订阅兴趣
	private Map<ICMetadata,ICInterest> pInteractions;  // 交互类发布兴趣
	private Map<ICMetadata,ICInterest> sInteractions;  // 交互类订阅兴趣

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public InterestManager( ObjectModel fom, RegionStore regionStore )
	{
		this.fom = fom;
		this.regionStore = regionStore;
		this.pObjects = new HashMap<OCMetadata,OCInterest>();
		this.sObjects = new HashMap<OCMetadata,OCInterest>();
		this.pInteractions = new HashMap<ICMetadata,ICInterest>();
		this.sInteractions = new HashMap<ICMetadata,ICInterest>();
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
    /**
     * <b>注意！！！！</b>
     * <p/>
     * 此调用对兴趣管理器的正常运行至关重要。管理器初始化时，FOM（联邦对象模型）未必可用。<br>
     * 例如，在 LRC 中，我们在加入任何联邦并接收到 FOM 之前就已初始化。如果在通过此方法设置 FOM 之前就使用兴趣管理器，将导致空指针异常（NPE）。<br>
     * 
     * @param fom 我们正在使用的FOM
     */
	public void setFOM( ObjectModel fom )
	{
		this.fom = fom;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////
	///////////////////// Generic Object Publication/Subscription Methods ////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 与 {@link #register(Map, String, int, int, Set, int)} 方法相同，但该方法为区域令牌（region token） 传入
     * {@link PorticoConstants#NULL_HANDLE}，并捕获/忽略与区域相关的异常。<br>
     * 请在非 DDM（动态数据分发）注册时使用此方法。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federate 当前注册所关联的联邦成员的句柄
     * @param clazz 当前注册所针对的类的句柄
     * @param attributes 正在注册兴趣的一组属性
     */
	private void register( Map<OCMetadata,OCInterest> map,
	                       String action,
	                       int federate,
	                       int clazz,
	                       Set<Integer> attributes )
		throws JObjectClassNotDefined,
		       JAttributeNotDefined,
		       JRTIinternalError
	{
		try
		{
			register( map, action, federate, clazz, attributes, PorticoConstants.NULL_HANDLE );
		}
		catch( JRegionNotKnown rnk )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
		catch( JInvalidRegionContext irc )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
	}
	                       	
    /**
     * 该方法将为指定联邦成员句柄所标识的联邦成员，注册其对某个对象类的兴趣（发布或订阅）。<br>
     * 它会对提供的信息执行所有适当的检查，以验证句柄在模型中存在且有效（例如确保属性属于正确的对象类等）。<br>
     * 如果提供的信息存在问题，将抛出异常。<br>
     * 本方法的此版本接受一个区域令牌（region token）与注册关联，供与 DDM（动态数据分发）相关的调用使用。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注册所关联的联邦成员的句柄
     * @param classHandle 当前注册所针对的类的句柄
     * @param attributes 正在注册兴趣的一组属性
     * @param regionToken 与注册关联的区域令牌。如果不想处理 DDM 相关内容，可传入 {@link PorticoConstants#NULL_HANDLE}
     */
	private synchronized void register( Map<OCMetadata,OCInterest> map,
	                                    String action,
	                                    int federateHandle,
	                                    int classHandle,
	                                    Set<Integer> attributes,
	                                    int regionToken )
		throws JObjectClassNotDefined,
		       JAttributeNotDefined,
		       JRegionNotKnown,
		       JInvalidRegionContext,
		       JRTIinternalError
	{
		// find the object class
		OCMetadata objectClass = fom.getObjectClass( classHandle );
		if( objectClass == null )
			throw new JObjectClassNotDefined( action + ": class not defined: " + classHandle );
		
		// find each attribute handle in the class
		for( int attributeHandle : attributes )
		{
			if( objectClass.hasAttribute(attributeHandle) == false )
			{
				throw new JAttributeNotDefined( action +": attribute ["+ attributeHandle +
				                                "] not defined in object class [" +
				                                objectClass.getQualifiedName() + "]" );
			}
		}
		
		////////////////////////////////////////////////////
		// do all the data distribution management checks //
		////////////////////////////////////////////////////
		RegionInstance region = null;
		if( regionToken != PorticoConstants.NULL_HANDLE )
		{
			region = regionStore.getRegion( regionToken );
			if( region == null )
				throw new JRegionNotKnown( "token: " + regionToken );
			
			for( int attributeHandle : attributes )
			{
				ACMetadata attribute = objectClass.getAttribute(attributeHandle);
				if( attribute.getSpace() == null ||
					attribute.getSpace().getHandle() != region.getSpaceHandle() )
				{
					throw new JInvalidRegionContext( "attribute [" + attributeHandle +
					    "] can't be associated with region [token:" + region.getToken() +
					    "]: Routing space not associated with attribute in FOM" );
				}
			}
		}
		
		///////////////////////////////////////////////////
		// record the registration, augment if it exists //
		///////////////////////////////////////////////////
		OCInterest interest = map.get( objectClass );
		if( interest == null )
		{
			interest = new OCInterest( objectClass );
			map.put( objectClass, interest );
		}

		interest.registerInterest( federateHandle, attributes, region /*null ok*/ );
	}
	
    /**
     * 该方法将注销指定联邦成员对某个特定对象类的所有兴趣。<br>
     * 如果该类在 FOM 中不存在，或该联邦成员对该类原本就没有注册兴趣，则会抛出异常。<br>
     * 如果没有注册记录，此方法将抛出一个 {@link NoRegistration} 异常。<br>
     * 包装方法（Wrapper methods）可以提取该异常的消息，并抛出相应类型的合适异常。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注销所关联的联邦成员的句柄
     * @param classHandle 当前注销所针对的类的句柄
     */
	private void unregister( Map<OCMetadata,OCInterest> map,
	                         String action,
	                         int federateHandle,
	                         int classHandle )
		throws JObjectClassNotDefined,
		       NoRegistration
	{
		try
		{
			unregister( map, action, federateHandle, classHandle, PorticoConstants.NULL_HANDLE );
		}
		catch( JRegionNotKnown rnk )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
	}

    /**
     * 该方法将注销指定联邦成员对某个特定对象类的所有兴趣。<br>
     * 如果该类在 FOM 中不存在，或该联邦成员对该类原本就没有注册兴趣，则会抛出异常。<br>
     * 如果没有注册记录，此方法将抛出一个 {@link NoRegistration} 异常。<br>
     * 包装方法（Wrapper methods）可以提取该异常的消息，并抛出相应类型的合适异常。<br>
     * 本方法的此版本接受一个区域令牌（region token）与注销操作关联。请在与 DDM（动态数据分发）相关的调用中使用。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注销所关联的联邦成员的句柄
     * @param classHandle 当前注销所针对的类的句柄
     * @param regionToken 与注销操作关联的区域令牌。如果不想处理 DDM 相关内容，可传入 {@link PorticoConstants#NULL_HANDLE}
     */
	private synchronized void unregister( Map<OCMetadata,OCInterest> map,
	                                      String action,
	                                      int federateHandle,
	                                      int classHandle,
	                                      int regionToken )
		throws JObjectClassNotDefined,
		       JRegionNotKnown,
		       NoRegistration
	{
		// validate the region if applicable
		RegionInstance region = null;
		if( regionToken != PorticoConstants.NULL_HANDLE )
		{
			region = regionStore.getRegion( regionToken );
			if( region == null )
				throw new JRegionNotKnown( "token: " + regionToken );
		}

		// find the metadata for the object class so that we can locate the appropriate
		// OCInterest and remove the federate as a recorded interest
		OCMetadata objectClass = fom.getObjectClass( classHandle );
		if( objectClass == null )
			throw new JObjectClassNotDefined( action + ": class not defined: " + classHandle );
		
		OCInterest interest = map.get( objectClass );
		if( interest == null )
			throw new NoRegistration( action+": federate has no pub/sub interest in "+classHandle );
		
		interest.removeInterest( federateHandle, region );
	}

    /**
     * 该方法将注销关联的联邦成员对指定类中某些特定属性的兴趣。<br>
     * 如果该类中的其他属性之前也注册了兴趣，则这些注册在调用此方法后仍然保留。<br>
     * 如果给定的属性句柄集合为 <code>null</code> 或为空，则将移除对该类的全部兴趣（所有属性）。<br>
     * 如果找不到对应的对象类，或相关记录不存在，则会抛出异常。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federate 当前注销所关联的联邦成员的句柄
     * @param classHandle 当前注销所针对的类的句柄
     * @param attributes 正在注销其兴趣的一组属性
     */
	private void unregister( Map<OCMetadata,OCInterest> map,
	                         String action,
	                         int federate,
	                         int classHandle,
	                         Set<Integer> attributes )
		throws JObjectClassNotDefined,
		       NoRegistration
	{
		try
		{
			unregister( map, action, federate, classHandle, attributes, PorticoConstants.NULL_HANDLE );
		}
		catch( JRegionNotKnown rnk )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
	}
	
    /**
     * 该方法将注销关联的联邦成员对指定类中某些特定属性的兴趣。<br>
     * 如果该类中的其他属性之前也注册了兴趣，则这些注册在调用此方法后仍然保留。<br>
     * 如果给定的属性句柄集合为 <code>null</code> 或为空，则将移除对该类的全部兴趣（所有属性）。<br>
     * 如果找不到对应的对象类，或相关记录不存在，则会抛出异常。<br>
     * 本方法的此版本接受一个区域令牌（region token）与注销操作关联。请在与 DDM（动态数据分发）相关的调用中使用。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注销所关联的联邦成员的句柄
     * @param classHandle 当前注销所针对的类的句柄
     * @param attributes 正在注销其兴趣的一组属性
     * @param regionToken 与注册关联的区域令牌。如果不想处理 DDM 相关内容，可传入 {@link PorticoConstants#NULL_HANDLE}
     */
	private synchronized void unregister( Map<OCMetadata,OCInterest> map,
	                                      String action,
	                                      int federateHandle,
	                                      int classHandle,
	                                      Set<Integer> attributes,
	                                      int regionToken )
		throws JObjectClassNotDefined,
		       JRegionNotKnown,
		       NoRegistration
	{
		// validate the region if applicable
		RegionInstance region = null;
		if( regionToken != PorticoConstants.NULL_HANDLE )
		{
			region = regionStore.getRegion( regionToken );
			if( region == null )
				throw new JRegionNotKnown( "token: " + regionToken );
			
			// FIXME Add support for region handling and attribute sets
			throw new RuntimeException( "not yet supported" );
		}

		// find the metadata for the object class so that we can locate the appropriate
		// OCInterest and remove the federate as a recorded interest
		OCMetadata objectClass = fom.getObjectClass( classHandle );
		if( objectClass == null )
			throw new JObjectClassNotDefined( action+": class not defined: "+classHandle );
		
		OCInterest interest = map.get( objectClass );
		if( interest == null )
			throw new NoRegistration( action+": federate has no pub/sub interest in "+classHandle );
		
		interest.removeInterest( federateHandle, attributes );
	}
	
    /**
     * 获取指定联邦成员句柄当前已注册的所有属性。如果该联邦成员未注册该类，则抛出 {@link NoRegistration} 异常。
     */
	private Set<Integer> getRegisteredAttributes( Map<OCMetadata,OCInterest> map,
	                                              String action,
	                                              int federateHandle,
	                                              int classHandle )
		throws JObjectClassNotDefined, NoRegistration
	{
	    // 查找对象类的元数据，以便定位相应的 OCInterest 并获取所有已注册的属性
		OCMetadata objectClass = fom.getObjectClass( classHandle );
		if( objectClass == null )
			throw new JObjectClassNotDefined( action+": object class not defined: "+classHandle );
		
		OCInterest interest = map.get( objectClass );
		if( interest == null )
			throw new NoRegistration( action+": federate has no pub/sub interest in "+classHandle );
		
		Set<Integer> registered = interest.getInterest( federateHandle );
		if( registered == null )
			throw new NoRegistration( action+": federate has no pub/sub interest in "+classHandle );
		else
			return interest.getInterest( federateHandle );
	}

    /**
     * 该方法用于检查指定的联邦成员是否已对给定的对象类注册了兴趣。
     */
	private boolean isObjectClassRegistered( Map<OCMetadata,OCInterest> map,
	                                         int federateHandle,
	                                         int classHandle )
	{
		// get the metadata for the object class so we can locate the interest
		OCInterest interest = map.get( fom.getObjectClass(classHandle) );
		if( interest == null )
			return false;
		else
			return interest.hasInterest( federateHandle );
	}

	/**
     * 如果联邦成员对指定的属性类已注册兴趣，则返回 <code>true</code>。<br>
     * 如果找不到对象类或属性类，或该属性未注册，则返回 <code>false</code>。<br>
     */
	private boolean isAttributeClassRegistered( Map<OCMetadata,OCInterest> map,
	                                            int federateHandle,
	                                            int classHandle,
	                                            int attributeHandle )
	{
		// get the metadata for the object class so we can locate the interest
		OCInterest interest = map.get( fom.getObjectClass(classHandle) );
		if( interest == null )
			return false;
		else
			return interest.hasAttributeInterest( federateHandle, attributeHandle );
	}

	//////////////////////////////////////////////////////////////////////////////////////////
	////////////////// Generic Interaction Publication/Subscription Methods //////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 与 {@link #register(Map, String, int, int, int)} 方法相同，但该方法为区域令牌（region token）。<br>
     * 传入 {@link PorticoConstants#NULL_HANDLE}，并捕获/忽略与区域相关的异常。<br>
     * 请在非 DDM（动态数据分发）注册时使用此方法。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注册所关联的联邦成员的句柄
     * @param classHandle 当前注册所针对的类的句柄
     */
	private void register( Map<ICMetadata,ICInterest> map,
	                       String action,
	                       int federateHandle,
	                       int classHandle )
		throws JInteractionClassNotDefined,
		       JRTIinternalError
	{
		try
		{
			register( map, action, federateHandle, classHandle, PorticoConstants.NULL_HANDLE );
		}
		catch( JRegionNotKnown rnk )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
		catch( JInvalidRegionContext irc )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
	}
	
    /**
     * 为指定的联邦成员注册对某个特定交互类的发布或订阅兴趣。<br>
     * 兴趣的类型（发布或订阅）取决于传递给该方法的映射（map）参数<br>
     * （例如，对于发布，传入交互发布实例变量）。<br>
     * 如果该交互类在 FOM 中未定义，将抛出异常，且给定的字符串将用于错误消息中，因此可以指明尝试执行的操作。<br>
     * 本方法在注册时会考虑区域（region）数据。<br>
     * 如果不需要 DDM（动态数据分发）相关功能，可为 regionToken 参数传入 {@link PorticoConstants#NULL_HANDLE}。<br>
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注册所关联的联邦成员的句柄
     * @param classHandle 当前注册所针对的类的句柄
     * @param regionToken 与注册关联的区域令牌。如果不想处理 DDM 相关内容，可传入 {@link PorticoConstants#NULL_HANDLE}
     */
	private synchronized void register( Map<ICMetadata,ICInterest> map,
	                                    String action,
	                                    int federateHandle,
	                                    int classHandle,
	                                    int regionToken )
		throws JInteractionClassNotDefined,
		       JRegionNotKnown,
		       JInvalidRegionContext,
		       JRTIinternalError
	{
		// find the interaction class
		ICMetadata interactionClass = fom.getInteractionClass( classHandle );
		if( interactionClass == null )
			throw new JInteractionClassNotDefined( action + ": class not defined: " + classHandle );

		// do all the data distribution management checks if applicable
		RegionInstance region = null;
		if( regionToken != PorticoConstants.NULL_HANDLE )
		{
			region = regionStore.getRegionCreatedBy( regionToken, federateHandle );
			if( region == null )
				throw new JRegionNotKnown( "token: " + regionToken );
			
			// check that the FOM allows regions of this space to link with this interaction class
			if( interactionClass.getSpace() == null ||
				interactionClass.getSpace().getHandle() != region.getSpaceHandle() )
			{
				throw new JInvalidRegionContext( "The routing space for the region is different" +
				    " from the routing space associated with the interaction class in the FOM" );
			}
		}

		// record the registration. if the interest already exists, augment it, if not, create it
		ICInterest interest = map.get( interactionClass );
		if( interest == null )
		{
			interest = new ICInterest( interactionClass );
			map.put( interactionClass, interest );
		}
		
		interest.registerInterest( federateHandle, region );
	}
	
    /**
     * 与 {@link #register(Map, String, int, int)} 方法相反。<br>
     * 用于移除指定联邦成员对某个类的发布或订阅兴趣。<br>
     * 如果该类在 FOM 中不存在，或该联邦成员未注册此兴趣，则会抛出异常。<br>
     */
	private void unregisterInteraction( Map<ICMetadata,ICInterest> map,
	                                    String action,
	                                    int federateHandle,
	                                    int classHandle )
		throws JInteractionClassNotDefined,
		       NoRegistration
	{
		try
		{
			unregisterInteraction( map,
			                       action,
			                       federateHandle,
			                       classHandle,
			                       PorticoConstants.NULL_HANDLE );
		}
		catch( JRegionNotKnown rnk )
		{
			// won't happen because we're passing PorticoConstants.NULL_HANDLE
		}
	}

    /**
     * 与 {@link #register(Map, String, int, int)} 方法相反。用于移除指定联邦成员对某个类的发布或订阅兴趣。<br>
     * 如果该类在 FOM 中不存在，或该联邦成员未注册此兴趣，则会抛出异常。<br>
     * 本方法在注销时会考虑区域（region）数据。如果不需要 DDM（动态数据分发）相关功能，可为 regionToken 参数传入 {@link PorticoConstants#NULL_HANDLE}。
     * 
     * @param map 要更新的兴趣数据映射（发布或订阅）
     * @param action 在异常中使用的标签，用于描述当前操作的目的
     * @param federateHandle 当前注销所关联的联邦成员的句柄
     * @param classHandle 当前注销所针对的类的句柄
     * @param regionToken 与注册关联的区域令牌。如果不想处理 DDM 相关内容，可传入 {@link PorticoConstants#NULL_HANDLE}。
     */
	private synchronized void unregisterInteraction( Map<ICMetadata,ICInterest> map,
	                                                 String action,
	                                                 int federateHandle,
	                                                 int classHandle,
	                                                 int regionToken )
		throws JInteractionClassNotDefined,
		       JRegionNotKnown,
		       NoRegistration
	{
		// validate region information if applicable
		RegionInstance region = null;
		if( regionToken != PorticoConstants.NULL_HANDLE )
		{
			region = regionStore.getRegionCreatedBy( regionToken, federateHandle );
			if( region == null )
				throw new JRegionNotKnown( "token: " + regionToken );
		}
		
		// 查找交互类的元数据，以便定位相应的 ICInterest 并移除该联邦成员的记录兴趣
		ICMetadata interactionClass = fom.getInteractionClass( classHandle );
		if( interactionClass == null )
			throw new JInteractionClassNotDefined( action + ": class not defined: " + classHandle );
		
		ICInterest interest = map.get( interactionClass );
		if( interest == null )
			throw new NoRegistration( action+": federate has no pub/sub interest in "+classHandle );
		
		// 如果未提供区域数据，则会传入 null，这等同于使用默认区域，从而使区域相关的考虑被忽略
		interest.removeInterest( federateHandle, region );
	}
	
	/**
     * 该方法用于检查指定的联邦成员是否已对给定的交互类注册了兴趣。
     */
	private boolean isInteractionClassRegistered( Map<ICMetadata,ICInterest> map,
	                                              int federateHandle,
	                                              int classHandle )
	{
		// get the metadata for the object class so we can locate the interest
		ICInterest interest = map.get( fom.getInteractionClass(classHandle) );
		if( interest == null )
			return false;
		else
			return interest.hasInterest( federateHandle );
	}

	//////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////// Object Publication Methods ///////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This method will record the publication of an object class for the federate identified
	 * by the given federate handle. It will run all the appriorate checks on the given information
	 * to validate that the handles exist in the model and are appropriate (make sure the attributes
	 * are of the right object class etc...)
	 */
	public void publishObjectClass( int federateHandle, int classHandle, Set<Integer> attributes )
		throws JObjectClassNotDefined,
		       JAttributeNotDefined,
		       JRTIinternalError
	{
		register( pObjects, "PUBLISH-OBJECT", federateHandle, classHandle, attributes );
	}

	/**
	 * This method will remove any object publication record the specified federate had in the
	 * object class identified by the given handle. If the class doesn't exist in the FOM, or
	 * the particular federate didn't have a publication interest in it, an exception will be
	 * thrown.
	 */
	public void unpublishObjectClass( int federateHandle, int classHandle )
		throws JObjectClassNotDefined,
		       JObjectClassNotPublished
	{
		try
		{
			unregister( pObjects, "PUBLISH-OBJECT", federateHandle, classHandle );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotPublished( nr.getMessage() );
		}
	}
	
	/**
	 * This method will remove the publication record associated with the identified federate in
	 * the given attributes of the given class. If other attributes in the class were also
	 * published, their record will remain after this call. If the given set is either empty or
	 * <code>null</code>, then the entire publication (for *ALL* attributes of the class) will be
	 * removed. If the object class cannot be found, or a publication record didn't exist, an
	 * exception will be thrown.
	 */
	public void unpublishObjectClass( int federateHandle, int classHandle, Set<Integer> attributes )
		throws JObjectClassNotDefined,
		       JObjectClassNotPublished
	{
		try
		{
			unregister( pObjects, "PUBLISH-OBJECT", federateHandle, classHandle, attributes );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotPublished( nr.getMessage() );
		}
	}
	
	/**
	 * Get all the attributes that are currently published by the identified federate handle. If
	 * the class isn't published by the federate, an exception is thrown.
	 */
	public Set<Integer> getPublishedAttributes( int federateHandle, int classHandle )
		throws JObjectClassNotDefined, JObjectClassNotPublished
	{
		try
		{
			return getRegisteredAttributes( pObjects, "PUBLISH-OBJECT", federateHandle, classHandle );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotPublished( nr.getMessage() );
		}
	}

	/**
	 * This method will check to see if the identified federate publishes the given object class.
	 */
	public boolean isObjectClassPublished( int federateHandle, int classHandle )
	{
		return isObjectClassRegistered( pObjects, federateHandle, classHandle );
	}

	/**
	 * Returns <code>true</code> if the federate publishes the identified attribute class. If the
	 * object or attribute classes can't be found, or the attribute isn't published,
	 * <code>false</code> is returned.
	 */
	public boolean isAttributeClassPublished( int federateHandle,
	                                          int classHandle,
	                                          int attributeHandle )
	{
		return isAttributeClassRegistered( pObjects, federateHandle, classHandle, attributeHandle );
	}

	//////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////// Object Subscription Methods ///////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This method will record the subscription to a particular set of attribute for an identified
	 * federate. Checks to validate that each of the attribute exists in the object class exist
	 * will be run, with exceptions being thrown for transgressions.
	 */
	public void subscribeObjectClass( int federateHandle, int classHandle, Set<Integer> attributes )
		throws JObjectClassNotDefined, JAttributeNotDefined, JRTIinternalError
	{
		register( sObjects, "SUBSCRIBE-OBJECT", federateHandle, classHandle, attributes );
	}

	/**
	 * This method will record the subscription to a particular set of attributes for an identified
	 * federate. It will also associate the subscription of each attribute with the given region.
	 * Checks to validate that each of the attributes exists in the object class, with exceptions
	 * being thrown for transgressions. Checks will also be made to ensure that the specified region
	 * exists and is of a routing space that the FOM defines as valid for each attribute.
	 */
	public void subscribeObjectClass( int federateHandle,
	                                  int classHandle,
	                                  Set<Integer> attributes,
	                                  int regionToken )
		throws JObjectClassNotDefined,
		       JAttributeNotDefined,
		       JRegionNotKnown,
		       JInvalidRegionContext,
		       JRTIinternalError
	{
		register( sObjects,
		          "SUBSCRIBE-OBJECT-DDM",
		          federateHandle,
		          classHandle,
		          attributes,
		          regionToken );
		return;
	}
	
	/**
	 * This method will remove any object subscription record the specified federate had in the
	 * object class identified by the given handle. If the class doesn't exist in the FOM, or
	 * the particular federate didn't have a publication interest in it, an exception will be
	 * thrown.
	 */
	public void unsubscribeObjectClass( int federateHandle, int classHandle )
		throws JObjectClassNotDefined,
		       JObjectClassNotSubscribed
	{
		try
		{
			unregister( sObjects, "UNSUBSCRIBE-OBJECT", federateHandle, classHandle );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotSubscribed( nr.getMessage() );
		}
	}

	/**
	 * This method will remove any object subscription record the specified federate had in the
	 * object class identified by the given handle. If the class doesn't exist in the FOM, or
	 * the particular federate didn't have a publication interest in it, an exception will be
	 * thrown.  Checks will also be made to ensure that the specified region exists and is of a
	 * routing space that the FOM defines as valid for each attribute.
	 */
	public void unsubscribeObjectClass( int federateHandle, int classHandle, int regionToken )
		throws JObjectClassNotDefined,
		       JObjectClassNotSubscribed,
		       JRegionNotKnown,
		       JInvalidRegionContext
	{
		try
		{
			unregister( sObjects, "UNSUBSCRIBE-OBJECT", federateHandle, classHandle, regionToken );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotSubscribed( nr.getMessage() );
		}
	}
	
	/**
	 * This method will remove the subscription record associated with the identified federate in
	 * the given attributes of the given class. If other attributes in the class were also
	 * subscribed, their record will remain after this call. If the given set is either empty or
	 * <code>null</code>, then the entire subscription (for *ALL* attributes of the class) will be
	 * removed. If the object class cannot be found, or a subscription record didn't exist, an
	 * exception will be thrown.
	 */
	public void unsubscribeObjectClass( int federateHandle,
	                                    int classHandle,
	                                    Set<Integer> attributes )
		throws JObjectClassNotDefined,
		       JObjectClassNotSubscribed
	{
		try
		{
			unregister( sObjects, "UNSUBSCRIBE-OBJECT", federateHandle, classHandle, attributes );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotSubscribed( nr.getMessage() );
		}
	}

	/**
	 * Get all the attributes that are currently subscribe by the identified federate handle for
	 * the given class. If the class isn't published by the federate, an exception is thrown.
	 */
	public Set<Integer> getSubscribedAttributes( int federateHandle, int classHandle )
		throws JObjectClassNotDefined,
		       JObjectClassNotSubscribed
	{
		try
		{
			return getRegisteredAttributes( sObjects, "SUBSCRIBE-OBJECT", federateHandle, classHandle );
		}
		catch( NoRegistration nr )
		{
			throw new JObjectClassNotSubscribed( nr.getMessage() );
		}
	}
	
	/**
	 * Get the {@link OCInterest} the given federate has in the given object class. If the federate
	 * isn't subscribed, an exception will be thrown, otherwise the interest object will be returned
	 */
	public OCInterest getSubscribedInterest( int federateHandle, OCMetadata objectClass )
		throws JObjectClassNotSubscribed
	{
		OCInterest interest = sObjects.get( objectClass );
		if( interest == null )
			throw new JObjectClassNotSubscribed( "federate has no interest in "+objectClass );
		
		return interest;
	}

    /**
     * 该方法通常用于确定联邦成员在发现新注册对象时应将其视为哪种类型的类。<br>
     * 联邦成员即使没有直接订阅该新对象的具体类型，也可能需要接收发现通知。<br>
     * 例如，它可能订阅了该类型的父类，因此应以父类类型发现该对象。<br>
     * <p/>
     * 本方法将查找并返回代表联邦成员所订阅的、最具体的 {@link OCMetadata} 类型。<br>
     * 它从给定句柄标识的类开始检查：如果联邦成员订阅了该类，则返回其元数据；如果没有，则继续检查其父类（若订阅了父类则返回父类元数据），依此类推，直至没有更多父类。<br>
     * 若最终未找到任何订阅的父类，则返回 null。如果初始类无法找到，也返回 null。<br>
     * 
     * @param federateHandle 要为其查找订阅信息的联邦成员句柄
     * @param initialClass 开始查找订阅信息的类句柄
     * @return 联邦成员所订阅的最具体对象类的 OCMetadata，若当前订阅兴趣无法发现该对象则返回 null
     */
	public OCMetadata getDiscoveryType( int federateHandle, int initialClass )
	{
		// get the metadata for the original class so we can return it if necessary
		OCMetadata clazz = fom.getObjectClass( initialClass );
		if( clazz == null )
			return null; // don't know what the initial class is :S
		
		// check to see if the class is subscribed, if it isn't, move on to its parent and check.
		// keep doing this until we find the first class that is subscribed (and return it) or
		// until we run out of parents to check
		while( true )
		{
			if( isObjectClassSubscribedDirectly(federateHandle,clazz.getHandle()) )
			{
				return clazz;
			}
			else
			{
				clazz = clazz.getParent();
				if( clazz == null )
					return null; // no more parents
			}
		}
	}
	
	/**
	 * This method will check to see if the identified federate subscribes to the object class. If
	 * the exact class isn't subscribed, the method will check up the inheritence hierarchy to see
	 * if a parent class is subscribed to.
	 * 
	 * @return <code>true</code> if the identified federate subscribes to the class of the given
	 *         handle, OR, to any of the parent classes
	 */
	public boolean isObjectClassSubscribed( int federateHandle, int initialClass )
	{
		OCMetadata clazz = fom.getObjectClass( initialClass );
		if( clazz == null )
			return false;
		
		while( clazz != null )
		{
			if( isObjectClassSubscribedDirectly( federateHandle, clazz.getHandle()) )
				return true;
			else
				clazz = clazz.getParent();
		}
		
		return false;
	}

	/**
	 * The same as {@link #isObjectClassSubscribed(int, int)} except that it <b>DOES NOT</b> take
	 * inheritance into account when making its decision.
	 * 
	 * @return <code>true</code> if the identified federate published the given class handle, 
	 *         <code>false</code> otherwise.
	 */
	public boolean isObjectClassSubscribedDirectly( int federateHandle, int classHandle )
	{
		return isObjectClassRegistered( sObjects, federateHandle, classHandle );
	}

	/**
	 * Returns <code>true</code> if the federate subscribes to the identified attribute class. If
	 * the object or attribute classes can't be found, or the attribute isn't subscribed,
	 * <code>false</code> is returned.
	 */
	public boolean isAttributeClassSubscribed( int federateHandle,
	                                           int classHandle,
	                                           int attributeHandle )
	{
		return isAttributeClassRegistered( sObjects, federateHandle, classHandle, attributeHandle );
	}

	/**
	 * Return a set of all the federate handles that have a subscription interest in the given
	 * object class <b>OR</b> any of its parents.
	 * 
	 * @param objectClass The class that we want to find all the subscribers for
	 * @return A set of all federate handles that declare an interest in the given object class
	 *         of any of its parents
	 */
	public Set<Integer> getAllSubscribers( OCMetadata objectClass )
	{
		HashSet<Integer> set = new HashSet<>();

		OCMetadata currentClass = objectClass;
		while( currentClass != null )
		{
			OCInterest interest = sObjects.get( currentClass );
			if( interest != null )
				set.addAll( interest.getFederates() );

			currentClass = currentClass.getParent();
		}
		
		return set;
	}
	
	/**
	 * Return the set of all federates that have some subscription interest in the given class,
	 * and the exact type that they are interested in. It might not be the explicit type passed,
	 * but rather one of its parents, so we need to be clear about what specifically the interest
	 * is and check all the way up the hierarchy.
	 * <p/>
	 * The returned map will contain the handle of the federate with the interest and the
	 * <i>most specific</i> class they are interested in.
	 * 
	 * @param initialClass The class to start checking for federates with a subscription interest
	 * @return A map of all federates with an interest in this class, or a parent class, and the
	 *         specific class they are interested in.
	 */
	public Map<Integer,OCMetadata> getAllSubscribersWithTypes( OCMetadata initialClass )
	{
		HashMap<Integer,OCMetadata> map = new HashMap<>();
		
		// get the interest information for thi
		OCMetadata currentClass = initialClass;
		while( currentClass != null )
		{
			// find if there is any interest in this class directly
			OCInterest currentInterest = sObjects.get( currentClass );
			if( currentInterest != null )
			{
				for( int federateHandle : currentInterest.getFederates() )
				{
					// only register a federate's interest if it isn't already stored.
					// if it is stored, it means they're interested in a more specific class
					if( map.containsKey(federateHandle) == false )
						map.put( federateHandle, currentClass );
				}
			}

			// skip to the parent class
			currentClass = currentClass.getParent();
		}
		
		return map;
	}

	//////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////// Interaction Publication Methods ////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This method will record the publication of the identified interaction class for the given
	 * federate. It will also validate that the interaction class exists in the FOM and will throw
	 * an exception if it isn't.
	 */
	public void publishInteractionClass( int federateHandle, int classHandle )
		throws JInteractionClassNotDefined,
		       JRTIinternalError
	{
		register( pInteractions, "PUBLISH-INTERACTION", federateHandle, classHandle );
	}
	
	/**
	 * This method will remove any interaction publication record the specified federate had in the
	 * interaction class identified by the given handle. If the class doesn't exist in the FOM, or
	 * the particular federate didn't have a publication interest in it, an exception will be
	 * thrown.
	 */
	public void unpublishInteractionClass( int federateHandle, int classHandle )
		throws JInteractionClassNotDefined,
		       JInteractionClassNotPublished
	{
		try
		{
			unregisterInteraction( pInteractions,
			                       "UNPUBLISH-INTERACTION",
			                       federateHandle,
			                       classHandle );
		}
		catch( NoRegistration nr )
		{
			throw new JInteractionClassNotPublished( nr.getMessage() );
		}
	}
	
	/**
	 * This method will check to see if the identified federate publishes the interaction class.
	 */
	public boolean isInteractionClassPublished( int federateHandle, int classHandle )
	{
		return isInteractionClassRegistered( pInteractions, federateHandle, classHandle );
	}

	//////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////// Interaction Subscription Methods ////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This method will record the subscription to the identified interaction class for the given
	 * federate. It will also validate that the interaction class exists in the FOM and will throw
	 * an exception if it isn't.
	 */
	public void subscribeInteractionClass( int federateHandle, int classHandle )
		throws JInteractionClassNotDefined,
		       JRTIinternalError
	{
		register( sInteractions, "SUBSCRIBE-INTERACTION", federateHandle, classHandle );
	}
	
	/**
	 * This method will record the subscription to the identified interaction class for the given
	 * federate. It will also validate that the interaction class exists in the FOM and will throw
	 * an exception if it isn't. If the given <code>regionToken</code> doesn't identify a known
	 * region, or the region's routing space isn't declared in the FOM for the interaction class,
	 * exceptions are thrown. 
	 */
	public void subscribeInteractionClass( int federateHandle, int classHandle, int regionToken )
		throws JInteractionClassNotDefined,
		       JRegionNotKnown,
		       JInvalidRegionContext,
		       JRTIinternalError
	{
		register( sInteractions,
		          "SUBSCRIBE-INTERACTION-DDM",
		          federateHandle,
		          classHandle,
		          regionToken );
	}

	/**
	 * This method will remove any interaction subscription record the specified federate had in the
	 * interaction class identified by the given handle. If the class doesn't exist in the FOM, or
	 * the particular federate didn't have a subscription interest in it, an exception will be
	 * thrown.
	 */
	public void unsubscribeInteractionClass( int federateHandle, int classHandle )
		throws JInteractionClassNotDefined,
		       JInteractionClassNotSubscribed
	{
		try
		{
			unregisterInteraction( sInteractions,
			                       "UNSUBSCRIBE-INTERACTION",
			                       federateHandle,
			                       classHandle );
		}
		catch( NoRegistration nr )
		{
			throw new JInteractionClassNotSubscribed( nr.getMessage() );
		}
	}
	
	/**
	 * This method will remove any interaction subscription record the specified federate had in the
	 * interaction class identified by the given handle. If the class doesn't exist in the FOM, or
	 * the particular federate didn't have a subscription interest in it, an exception will be
	 * thrown. If the given <code>regionToken</code> doesn't identify a known region, or the
	 * region's routing space isn't declared in the FOM for the interaction class, exceptions are
	 * thrown.
	 */
	public void unsubscribeInteractionClass( int federateHandle, int classHandle, int regionToken )
		throws JInteractionClassNotDefined,
		       JInteractionClassNotSubscribed,
		       JRegionNotKnown
	{
		try
		{
			unregisterInteraction( sInteractions,
			                       "UNSUBSCRIBE-INTERACTION",
			                       federateHandle,
			                       classHandle,
			                       regionToken );
		}
		catch( NoRegistration nr )
		{
			throw new JInteractionClassNotSubscribed( nr.getMessage() );
		}
	}

	/**
	 * This method will check to see if the identified federate subscribes to the interaction class.
	 */
	public boolean isInteractionClassSubscribed( int federateHandle, int initialClass )
	{
		ICMetadata clazz = fom.getInteractionClass( initialClass );
		if( clazz == null )
			return false;
		
		while( clazz != null )
		{
			if( isInteractionClassSubscribedDirectly(federateHandle, clazz.getHandle()) )
				return true;
			else
				clazz = clazz.getParent();
		}
		
		return false;
	}
	
	/**
	 * Returns <code>true</code> if the identified federate is subscribed directly to the given
	 * interaction class. This method will *NOT* check the inheritance hierarchy, it will only
	 * check for direct subscriptions.
	 */
	public boolean isInteractionClassSubscribedDirectly( int federateHandle, int classHandle )
	{
		return isInteractionClassRegistered( sInteractions, federateHandle, classHandle );
	}
	
	/**
	 * Just because a federate isn't subscribed directly to a particular interaction class doesn't
	 * mean that it isn't interested in incoming interactions of that type. The federate could be
	 * subscribed to a parent class. This method will find the interaction class the given federate
	 * is subscribed to that is closest to the interaction class represented by the given class
	 * handle. Starting with the initial class, it will climb the inheritance hierarchy until it
	 * either finds an interaction class that the federate is subscribed to, or runs out of parents
	 * to check for. If it finds a class, the {@link ICMetadata} for that type is returned. If it
	 * doesn't find one, <code>null</code> is returned.
	 * 
	 * @param federateHandle The handle of the federate who has the potential subscription
	 * @param initialClass The handle of the object class to start looking for subscriptions at
	 */
	public ICMetadata getSubscribedInteractionType( int federateHandle, int initialClass )
	{
		ICInterest interest = getSubscribedInteractionInterest( federateHandle, initialClass );
		if( interest == null )
			return null;
		else
			return interest.getInteractionClass();
	}
	
	/**
	 * This method is the same as {@link #getSubscribedInteractionType(int, int)} except that it
	 * will return the relevant {@link ICInterest} rather than the metadata of the type (you can
	 * get the metadata from the interest).
	 * 
	 * @param federateHandle The handle of the federate who has the potential subscription
	 * @param initialClass The handle of the object class to start looking for subscriptions at
	 */
	public ICInterest getSubscribedInteractionInterest( int federateHandle, int initialClass )
	{
		// get the metadata for the original class so we can return it if necessary
		ICMetadata clazz = fom.getInteractionClass( initialClass );
		if( clazz == null )
			return null; // don't know what the initial class is :S
		
		// check to see if the class is subscribed, if it isn't, move on to its parent and check.
		// keep doing this until we find the first class that is subscribed (and return it) or
		// until we run out of parents to check
		while( true )
		{
			ICInterest interest = sInteractions.get( clazz );
			if( interest != null && interest.hasInterest(federateHandle) )
				return interest;
			
			// there is no interest registered for this type of the federate itself hasn't
			// got an interest in it, move on up to the parent if there is one
			clazz = clazz.getParent();
			if( clazz == null )
				return null; // no more parents
		}
	}

	/**
	 * Return a set of all the federate handles that have a subscription interest in the given
	 * interaction class <b>OR</b> any of its parents.
	 * 
	 * @param interactionClass The class that we want to find all the subscribers for
	 * @return A set of all federate handles that declare an interest in the given object class
	 *         of any of its parents
	 */
	public Set<Integer> getAllSubscribers( ICMetadata interactionClass )
	{
		HashSet<Integer> set = new HashSet<>();

		ICMetadata currentClass = interactionClass;
		while( currentClass != null )
		{
			ICInterest interest = sInteractions.get( currentClass );
			if( interest != null )
				set.addAll( interest.getFederates() );

			currentClass = currentClass.getParent();
		}
		
		return set;
	}
	
	/**
	 * Returns a set of object class handles that the provided federate has a publication interest in.
	 * 
	 * @param federateHandle the handle of the federate to find all object class publications for
	 * @return a set of object class handles that the provided federate has a publication interest in
	 */
	public Set<Integer> getAllPublishedObjectClasses( int federateHandle )
	{
		Set<Integer> objectClasses = new HashSet<>();
		for( OCInterest interest : this.pObjects.values() )
		{
			if( interest.hasInterest(federateHandle) )
				objectClasses.add( interest.getObjectClass().getHandle() );
		}
		
		return objectClasses;
	}
	
	/**
	 * Returns a set of object class handles that the provided federate has a subscription interest in.
	 * 
	 * @param federateHandle the handle of the federate to find all object class subscriptions for
	 * @return a set of object class handles that the provided federate has a subscription interest in
	 */
	public Set<Integer> getAllSubscribedObjectClasses( int federateHandle )
	{
		Set<Integer> objectClasses = new HashSet<>();
		for( OCInterest interest : this.sObjects.values() )
		{
			if( interest.hasInterest(federateHandle) )
				objectClasses.add( interest.getObjectClass().getHandle() );
		}
		
		return objectClasses;
	}
	
	/**
	 * Returns a set of interaction class handles that the provided federate has a publication interest 
	 * in.
	 * 
	 * @param federateHandle the handle of the federate to find all interaction class publications for
	 * @return a set of interaction class handles that the provided federate has a publication interest in
	 */
	public Set<Integer> getAllPublishedInteractionClasses( int federateHandle )
	{
		Set<Integer> interactionClasses = new HashSet<>();
		for( ICInterest interest : this.pInteractions.values() )
		{
			if( interest.hasInterest(federateHandle) )
				interactionClasses.add( interest.getInteractionClass().getHandle() );
		}
		
		return interactionClasses;
	}
	
	/**
	 * Returns a set of interaction class handles that the provided federate has a subscription interest 
	 * in.
	 * 
	 * @param federateHandle the handle of the federate to find all interaction class subscriptions for
	 * @return a set of interaction class handles that the provided federate has a subscriptions interest in
	 */
	public Set<Integer> getAllSubscribedInteractionClasses( int federateHandle )
	{
		Set<Integer> interactionClasses = new HashSet<>();
		for( ICInterest interest : this.sInteractions.values() )
		{
			if( interest.hasInterest(federateHandle) )
				interactionClasses.add( interest.getInteractionClass().getHandle() );
		}
		
		return interactionClasses;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////// Private Helper Methods /////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////

	/////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////// Save/Restore Methods /////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////
	public void saveToStream( ObjectOutput output ) throws Exception
	{
		output.writeObject( pObjects );
		output.writeObject( sObjects );
		output.writeObject( pInteractions );
		output.writeObject( sInteractions );
	}

	@SuppressWarnings("unchecked")
	public void restoreFromStream( ObjectInput input ) throws Exception
	{
		this.pObjects      = (Map<OCMetadata,OCInterest>)input.readObject();
		this.sObjects      = (Map<OCMetadata,OCInterest>)input.readObject();
		this.pInteractions = (Map<ICMetadata,ICInterest>)input.readObject();
		this.sInteractions = (Map<ICMetadata,ICInterest>)input.readObject();
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
	
	private class NoRegistration extends Exception
	{
		private static final long serialVersionUID = 98121116105109L;
		public NoRegistration( String message )
		{
			super( message );
		}
	}
}
