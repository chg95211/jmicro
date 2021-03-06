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
package cn.jmicro.api.codec;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import cn.jmicro.api.monitor.ServiceCounter;

/**
 * 
 * @author Yulei Ye
 * @date 2018年11月28日 下午11:00:41
 */
public class TestServiceCounter {

	@Test
	public void test01() {
		ServiceCounter sc = new ServiceCounter("test",null,10,1,TimeUnit.SECONDS);
		short t = 1;
		sc.addCounter(t);
		Random rand = new Random(1000);
		
		while(true) {
			long v = rand.nextLong()%1000;
			v = v<0? -v:v;
			sc.add(t,v);
			System.out.println(sc.get(t));
			try {
				Thread.sleep(v);
			} catch (InterruptedException e) {
			}
		}
	}
}
