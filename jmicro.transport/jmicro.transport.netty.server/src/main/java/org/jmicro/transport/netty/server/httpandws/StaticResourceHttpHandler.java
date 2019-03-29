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
package org.jmicro.transport.netty.server.httpandws;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jmicro.api.annotation.Cfg;
import org.jmicro.api.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * 
 * @author Yulei Ye
 * @date 2018年10月21日-下午9:16:29
 */
@SuppressWarnings("restriction")
@Component(value="nettyStaticResourceHandler",lazy=false)
public class StaticResourceHttpHandler  {

	static final Logger LOG = LoggerFactory.getLogger(StaticResourceHttpHandler.class);
	
	//@Cfg("/StaticResourceHttpHandler/root")
	//private String root="*";
	
	@Cfg("/StaticResourceHttpHandler/debug")
	private boolean debug = false;
	
	@Cfg("/StaticResourceHttpHandler/indexPage")
	private String indexPage="index.html";
	
	@Cfg(value="/StaticResourceHttpHandler/staticResourceRoot_*", changeListener="resourceRootChange")
	private List<String> staticResourceRoots = new ArrayList<>();
	
	private Map<String,byte[]> contents = new HashMap<>();
	
	public boolean canhandle(FullHttpRequest request){
		return request.method().equals(HttpMethod.GET);
	}

	public void handle(ChannelHandlerContext ctx,FullHttpRequest request) throws IOException {
		String path = request.uri();
		
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.headers().set("content-Type",getContentType(path));
		
		byte[] content = this.getContent(path);

		response.headers().set("content-Length",content.length);
		
		ByteBuf responseBuf = Unpooled.copiedBuffer(content);
		response.content().writeBytes(responseBuf);
		responseBuf.release();
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private String getContentType(String path) {
		FileNameMap fileNameMap = URLConnection.getFileNameMap();
		if(path== null || "/".equals(path.trim())){
			return "text/html;charset=UTF-8";
		}else {
			String ct = fileNameMap.getContentTypeFor(path);
			if(ct == null) {
				ct = "text/html;charset=UTF-8";
			}
			return ct;
		}
	}
	   
	private byte[] getContent(String path) {
		String absPath = null;
		
		InputStream bisr = null;
		
		if(path.equals("/")){
			path = path + indexPage;
		}
		
		ClassLoader cl = StaticResourceHttpHandler.class.getClassLoader();
		for(String parent: staticResourceRoots) {
			String ph = parent + path;
			if(!debug && contents.containsKey(ph)) {
				return contents.get(ph);
			}
			File file = new File(ph);
			if(file.exists() && file.isFile()) {
				try {
					absPath = ph;
					bisr = new FileInputStream(absPath);
					break;
				} catch (FileNotFoundException e) {
					LOG.error(absPath,e);
				}
			}
			
			bisr = cl.getResourceAsStream(ph);
			if(bisr != null) {
				absPath = ph;
				break;
			}
		}
		
		if(absPath == null) {
			for(String parent: staticResourceRoots) {
				String ph = parent + "/404.html";
				if(!debug && contents.containsKey(ph)) {
					return contents.get(ph);
				}
				File file = new File(ph);
				if(file.exists()) {
					try {
						bisr = new FileInputStream(file);
						absPath = ph;
						break;
					} catch (FileNotFoundException e) {
						LOG.error(absPath,e);
					}
				}
				
				bisr = cl.getResourceAsStream(ph);
				if(bisr != null) {
					absPath = ph;
					break;
				}
			}
		}
		
		try {
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] line = new byte[1024];
			int len = -1;
			while((len = bisr.read(line)) > 0 ){
				bos.write(line, 0, len);
			}
			contents.put(absPath, bos.toByteArray());
			return contents.get(absPath);
		} catch (IOException e) {
			LOG.error("getContent",e);
		}finally{
			if(bisr != null){
				try {
					bisr.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}
	
	public void resourceRootChange(String root) {
		if(!staticResourceRoots.contains(root)) {
			return;
		}
		
		Set<String> clears = new HashSet<String>();
		for(String p : contents.keySet()) {
			if(p.startsWith(root)) {
				clears.add(p);
			}
		}
		
		for(String p : clears) {
			contents.remove(p);
		}
	}

}
