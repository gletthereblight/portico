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

/**
 * Time Advance Request，“时间推进请求”，枚举类型，表示联邦成员可能具有的各种时间推进请求状态。
 */
public enum TAR
{
    /**
     * 当前没有待处理的时间推进请求
     */
    NONE,
    /**
     * 当前处于时间推进请求中
     */
    REQUESTED,
    /**
     * 当前处于“可用时推进”请求中
     */
    AVAILABLE,
    /**
     * 已发出推进回调，但尚未被处理
     */
    PROVISIONAL;
}
