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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.jmicro.common.Constants;
/**
 * 
 * @author Yulei Ye
 * @date 2018年10月4日-上午11:58:31
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface Service {

	public String value() default "";
	
	/**
	 * 服务使用的注册表，如将来同时使用实现ZK或Etcd，此属性目前感觉没必要，一个就足够了，
	 * 真想不明白同时用两种类型注册表有什么用
	 */
	public String registry() default Constants.DEFAULT_REGISTRY;
	
	/**
	 * 底层传输层，可以是http或Mina
	 */
	public String server() default Constants.DEFAULT_SERVER;
	
	/**
	 * 服务接口，如果类只实现一个接口，则此值可不填
	 * 一个实现只能存在一个接口作为服务，如果类同时实现了多个接口，则需要在此属性说明那个接口是服务接口
	 */
	public Class<?> infs() default Void.class;
	
	/**
	 * 服务命名空间，服务之间以命名空间作为区别，如出库单服务，入库单服务可以用不同的命名空间相区别，利于服务管理
	 * 客户端使用服务时，可以指定命名空间
	 */
	public String namespace() default Constants.DEFAULT_NAMESPACE;

	/**
	 * 服务版本，每个服务接口可以有多个版本，版本格式为 DD.DD.DD,6个数字用英方步点号隔开
	 * 客户端使用服务时，可以指定版本或版本范围
	 */
	public String version() default Constants.DEFAULT_VERSION;
	
	/**
	 * 服务是否可监控，-1表示未定义，由别的地方定义，如系统环境变量，启动时指定等，0表示不可监控，1表未可以被监控
	 * 可以被监控的意思是：系统启用埋点日志上报，服务请求开始，服务请求得到OK响应，服务超时，服务异常等埋点
	 */
	public int monitorEnable() default -1;
	
	/**
	 * 如果超时了，要间隔多久才重试
	 * @return
	 */
	public int retryInterval() default 500;
	/**
	 * 重试次数
	 */
	//method must can be retry, or 1
	public int retryCnt() default 3;
	
	/**
	 * 请求超时，单位是毫秒
	 */
	public int timeout() default 2000;
	
	/**
	 * 系统检测自动带上的参数 
	 */
	public String testingArgs() default "";
	
	/**
	 * 服务降级前最大失败次数，如降底QPS，提高响应时间等策略
	 * @return
	 */
	public int maxFailBeforeDegrade() default 100;
	
	/**
	 * 可以接受的最大平均响应时间，如果监控检测到超过此时间，系统可能会被降级或熔断
	 */
	public int avgResponseTime() default -1;
	
	/**
	 * 服务熔断前最大失败次数
	 * @return
	 */
	public int maxFailBeforeFusing() default 500;
	
	/**
	 * 支持的最高QPS
	 */
	public String maxSpeed() default "";
}
