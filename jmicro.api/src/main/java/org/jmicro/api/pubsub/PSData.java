/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jmicro.api.pubsub;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.jmicro.api.annotation.SO;
import org.jmicro.api.registry.UniqueServiceMethodKey;

/**
 * 
 * @author Yulei Ye
 * @date 2018年12月22日 下午11:10:43
 */
@SO
public final class PSData implements Serializable{
	
	// 1: 队列消息, 只要一个消费者成功消费消息即为成功，并且只能一个消费者成功消费消息
	// 0:订阅消息，可以有多个消费者消费消息
	public static final byte FLAG_QUEUE = 1<<0;
	
	public static final byte FLAG_PUBSUB = 0<<0;
	
	//1右移1位，异步方法，决定回调方法的参数类型为消息通知的返回值
	public static final byte FLAG_ASYNC_METHOD = 1<<1;
	//1右移两位，消息回调通知，决定回调方法的参数类型为消息通知的返回值分别为 消息发送状态码，消息ID，消息上下文透传
	public static final byte FLAG_MESSAGE_CALLBACK = 1<<2;
	
	//1:  如果是订阅类消息,必须全部成功消费才算成功，否则对于失败
	//0:只需要确保其中一个消费者成功消费消息即可认为消息发送成功，即使别的消费者消费失败，也不会重发消息
	//public static final byte FLAG_SUCCESS_ALL = 1<<1;
	
	private static final long serialVersionUID = 389875668374730999L;

	private Map<String,Object> context = new HashMap<>();
	
	//消息ID,唯一标识一个消息
	private long id = 0;
	
	private byte flag = 0;
	
	private String topic;
	
	private Object data;
	
	//消息发送结果回调的RPC方法，用于消息服务器给发送者回调
	private UniqueServiceMethodKey callback;
	
	//客户端发送失败次数，用于重发计数，如果消息失败次数到达一定量，将消息丢弃，并调用localCallback（如果存在）通知调用者，
	private transient int failCnt = 0;
	
	public static byte flag(byte ...fs) {
		byte fl = 0;
		for(byte f : fs) {
			fl |= f;
		}
		return fl; 
	}

	public void mergeContext(Map<String,Object> cxt) {
		this.context.putAll(cxt);
	}

	public byte getFlag() {
		return flag;
	}

	public void setFlag(byte flag) {
		this.flag = flag;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}
	
	public void put(String key,Object v) {
		this.context.put(key, v);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T get(String key) {
		return (T) this.context.get(key);
	}


	public UniqueServiceMethodKey getCallback() {
		return callback;
	}


	public void setCallback(UniqueServiceMethodKey callback) {
		this.callback = callback;
	}

	public int getFailCnt() {
		return failCnt;
	}


	public void setFailCnt(int failCnt) {
		this.failCnt = failCnt;
	}
	
	
}
