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
package org.jmicro.api.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.jmicro.common.Constants;

/**
 *
 * 当注册到服务方法上时，则该方法注册为指定主题的监听器,方法参数必须唯一且是 ＠Link PSItem
 * 
 * @author Yulei Ye
 * @date 2018年12月22日 下午10:50:50
 */
@Target({METHOD})
@Retention(RUNTIME)
public @interface Subscribe {

	public String topic();
	
	//public String pubsubServer() default Constants.DEFAULT_PUBSUB;
	
}
