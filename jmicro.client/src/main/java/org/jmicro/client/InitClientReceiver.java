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
package org.jmicro.client;

import java.util.List;

import org.jmicro.api.annotation.Component;
import org.jmicro.api.net.IMessageHandler;
import org.jmicro.api.objectfactory.IObjectFactory;
import org.jmicro.api.objectfactory.IPostFactoryReady;
import org.jmicro.api.objectfactory.ProxyObject;
import org.jmicro.common.Constants;
/**
 * 
 * @author Yulei Ye
 * @date 2018年10月9日-下午5:51:55
 */
@Component(active=true,value="InitClientReceiver",side=Constants.SIDE_COMSUMER)
public class InitClientReceiver implements IPostFactoryReady{
	@Override
	public void ready(IObjectFactory of) {
		List<IMessageHandler> list = of.getByParent(IMessageHandler.class);
		ClientMessageReceiver sr = of.get(ClientMessageReceiver.class);
		for(IMessageHandler h: list){
			Class<?> tcls = ProxyObject.getTargetCls(h.getClass());
			if(tcls.isAnnotationPresent(Component.class)){
				Component anno = tcls.getAnnotation(Component.class);
				if(anno.active() && Constants.SIDE_COMSUMER.equals(anno.side())){
					sr.registHandler(h);
				}
			}
		}
	}
	
	@Override
	public int runLevel() {
		return 0;
	}
}
