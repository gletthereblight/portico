/*
 *   Copyright 2009 The Portico Project
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
package org.portico.lrc.services.saverestore.data;

import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * 任何需要参与保存/恢复过程的组件都必须实现此接口。<br>
 * 这两个方法允许每个组件控制其状态的哪一部分被保存，并执行保存或恢复前后的准备工作或清理工作。<br>
 * <p/>
 * 此外，任何希望被包含在保存/恢复集合中的组件都必须向 {@link Serializer} 注册，否则它将不会被包含。<br>
 */
public interface SaveRestoreTarget
{
	/**
	 * The target should save any state it needs to be persisted to the given output stream.
	 */
	public void saveToStream( ObjectOutput output ) throws Exception;

	/**
	 * The target should restore any state it needs from the given output stream. It might also
	 * want to use this opportunity to do any re-initialization of non-persisted data items.
	 */
	public void restoreFromStream( ObjectInput input ) throws Exception;
}
