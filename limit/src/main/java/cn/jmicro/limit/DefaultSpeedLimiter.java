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
package cn.jmicro.limit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.jmicro.api.JMicroContext;
import cn.jmicro.api.annotation.Component;
import cn.jmicro.api.annotation.JMethod;
import cn.jmicro.api.limitspeed.ILimiter;
import cn.jmicro.api.monitor.MC;
import cn.jmicro.api.monitor.SF;
import cn.jmicro.api.monitor.ServiceCounter;
import cn.jmicro.api.net.IRequest;
import cn.jmicro.api.registry.ServiceMethod;
import cn.jmicro.common.Constants;

/**
 * 
 * @author Yulei Ye
 * @date 2018年10月4日-下午12:11:58
 */
@Component(lazy=false,value="limiterName")
public class DefaultSpeedLimiter extends AbstractLimiter implements ILimiter{
	
	static final Logger logger = LoggerFactory.getLogger(DefaultSpeedLimiter.class);
	private static final Class<?> TAG = DefaultSpeedLimiter.class;
	
	private static final Short[] TYPES = new Short[] {MC.MT_REQ_START};
	
	private Map<String,AtomicInteger> als = new ConcurrentHashMap<>();
	
	private Map<String,ServiceCounter> limiterData = new ConcurrentHashMap<>();
	
	@JMethod("init")
	public void init(){
	}
	
	@Override
	public boolean enter(IRequest req) {
		
		//not support method override
		ServiceMethod sm = JMicroContext.get().getParam(Constants.SERVICE_METHOD_KEY, null);
		if(sm.getMaxSpeed() <= 0) {
			return true;
		}
		
		ServiceCounter sc =  null;
		AtomicInteger al = null;
		
		String key = sm.getKey().toKey(true, true, true);
		
		if(!limiterData.containsKey(key)){
			key = key.intern();
			synchronized(key) {
				if(!limiterData.containsKey(key)){
					sc =  new ServiceCounter(key, TYPES,120,1,TimeUnit.SECONDS);
					this.limiterData.put(key, sc);
					al = new AtomicInteger(0);
					this.als.put(key, al);
				} else {
					sc = limiterData.get(key);
					al = this.als.get(key);
				}
			}
		} else {
			sc = limiterData.get(key);
			al = this.als.get(key);
		}
		
		double qps = sc.getQps(TimeUnit.SECONDS,MC.MT_REQ_START);
		//logger.info("qps:{},key:{}",qps,sm.getKey().getMethod());
		
		if(qps > sm.getMaxSpeed()){
			
			int cnt = al.get()+1;
			int needWaitTime = (int)((1000.0*cnt)/ sm.getMaxSpeed());
			
			if(needWaitTime >= sm.getTimeout()) {
				String errMsg = "qps:"+qps+",maxQps:"+sm.getMaxSpeed()+",key:"+key;
				SF.eventLog(MC.MT_SERVICE_SPEED_LIMIT, MC.LOG_WARN, TAG, errMsg);
				logger.info(errMsg);
				return false;
			} 
			
			if(needWaitTime > 0) {
				try {
					Thread.sleep(needWaitTime);
				} catch (InterruptedException e) {
					logger.error("Limit wait timeout error",e);
				}
			}
			
			logger.info("wait:{},cnt:{},maxSpeed:{}",needWaitTime,cnt,sm.getMaxSpeed());
			
		}
		
		//logger.info("apply cnt:{}",al.incrementAndGet());
		
		sc.increment(MC.MT_REQ_END);
		
		return true;
	}

	@Override
	public void end(IRequest req) {
		ServiceMethod sm = JMicroContext.get().getParam(Constants.SERVICE_METHOD_KEY, null);
		if(sm.getMaxSpeed() <= 0) {
			return;
		}
		String key = sm.getKey().toKey(true, true, true);
		AtomicInteger al = this.als.get(key);
		if(al != null) {
			int v = al.decrementAndGet();
			//logger.info("END cnt:{}",v);
			if(v <= 0) {
				al.set(0);
			}
		}
	}
	
}
