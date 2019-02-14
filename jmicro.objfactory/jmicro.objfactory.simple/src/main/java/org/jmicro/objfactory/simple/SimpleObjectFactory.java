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
package org.jmicro.objfactory.simple;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jmicro.api.ClassScannerUtils;
import org.jmicro.api.JMicro;
import org.jmicro.api.annotation.Component;
import org.jmicro.api.annotation.Inject;
import org.jmicro.api.annotation.JMethod;
import org.jmicro.api.annotation.ObjFactory;
import org.jmicro.api.annotation.PostListener;
import org.jmicro.api.annotation.Reference;
import org.jmicro.api.annotation.Service;
import org.jmicro.api.classloader.RpcClassLoader;
import org.jmicro.api.config.Config;
import org.jmicro.api.config.IConfigLoader;
import org.jmicro.api.http.annotation.HttpHandler;
import org.jmicro.api.objectfactory.IFactoryListener;
import org.jmicro.api.objectfactory.IObjectFactory;
import org.jmicro.api.objectfactory.IPostInitListener;
import org.jmicro.api.objectfactory.ProxyObject;
import org.jmicro.api.raft.IDataOperator;
import org.jmicro.api.registry.IRegistry;
import org.jmicro.api.registry.ServiceItem;
import org.jmicro.api.service.IServerServiceProxy;
import org.jmicro.api.service.ServiceManager;
import org.jmicro.common.CommonException;
import org.jmicro.common.Constants;
import org.jmicro.common.Utils;
import org.jmicro.common.util.ClassGenerator;
import org.jmicro.common.util.ReflectUtils;
import org.jmicro.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yulei Ye
 * @date 2018年10月4日-下午12:12:24
 */
@ObjFactory(Constants.DEFAULT_OBJ_FACTORY)
@Component(Constants.DEFAULT_OBJ_FACTORY)
public class SimpleObjectFactory implements IObjectFactory {

	static AtomicInteger idgenerator = new AtomicInteger();
	
	private final static Logger logger = LoggerFactory.getLogger(SimpleObjectFactory.class);
	
	private static AtomicInteger isInit = new AtomicInteger(0);
	
	private boolean fromLocal = true;
	
	private List<IFactoryListener> postReadyListeners = new ArrayList<>();
	
	private List<IPostInitListener> postListeners = new ArrayList<>();
	
	private Map<Class<?>,Object> objs = new ConcurrentHashMap<Class<?>,Object>();
	
	private Map<String,Object> nameToObjs = new ConcurrentHashMap<String,Object>();
	
	private Map<String,Object> clsNameToObjs = new ConcurrentHashMap<String,Object>();
	
	private ClientServiceProxyManager clientServiceProxyManager = null;
	
	private HttpHandlerManager httpHandlerManager = new HttpHandlerManager(this);
	
	@Override
	public <T> T getRemoteServie(String srvName, String namespace, String version,ClassLoader cl) {
		Object obj = null;
		if(obj == null){
			obj = this.clientServiceProxyManager.getService(srvName,namespace,version,cl);
		}
		return (T)obj;
	}

	@Override
	public <T> T getRemoteServie(ServiceItem item,ClassLoader cl) {
		Object obj = null;
		if(obj == null){
			obj = this.clientServiceProxyManager.getService(item,cl);
		}
		return (T)obj;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Class<T> cls) {
		checkStatu();
		Object obj = null;
		if(cls.isInterface() || Modifier.isAbstract(cls.getModifiers())) {
			List<T> l = this.getByParent(cls);
			if(l.size() == 1) {
				obj =  l.get(0);
			}else if(l.size() > 1) {
				throw new CommonException("More than one instance of class ["+cls.getName()+"].");
			}
			
			if(obj == null && cls.isAnnotationPresent(Service.class)){
				obj = this.clientServiceProxyManager.getService(cls);
			}
		} else {
			obj = objs.get(cls);
			if(obj != null){
				return  (T)obj;
			}
			
			obj = this.createObject(cls,true);
			if(obj != null) {
				cacheObj(cls,obj,true);
			}
		}
		return (T)obj;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getByName(String clsName) {
		checkStatu();
		if(this.clsNameToObjs.containsKey(clsName)){
			return (T) this.clsNameToObjs.get(clsName);
		}
		if(this.nameToObjs.containsKey(clsName)){
			return (T) this.nameToObjs.get(clsName);
		}
		
		Class<?> cls = this.loadCls(clsName);
		if(cls != null && cls.isAnnotationPresent(Service.class)) {
			Object o = this.clientServiceProxyManager.getService(clsName);
			if(o != null){
				return (T)o;
			}
		}
		
		if(cls != null){
			return (T)get(cls);
		}
		return null;
	}

	@Override
	public <T> List<T> getByParent(Class<T> parrentCls) {
		List<T> list = new ArrayList<>();
		Set<Class<?>> clazzes = ClassScannerUtils.getIns().loadClassByClass(parrentCls);
		for(Class<?> c: clazzes) {
			if(parrentCls.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())
					&& !Modifier.isInterface(c.getModifiers()) && Modifier.isPublic(c.getModifiers())
					&& c.isAnnotationPresent(Component.class)) {
				Component anno = c.getAnnotation(Component.class);
				if(anno != null && anno.active()) {
					Object obj = this.get(c);
					if(obj != null) {
						list.add((T)obj);
					}
				}
			}
		}
		Object obj = this.objs.get(parrentCls);
		if(obj != null){
			list.add((T)obj);
		}
		return list;
	}
	
	private void checkStatu(){
		if(isInit.get() == 1 && fromLocal) {
			return;
		}
		if(isInit.get() < 2){
			throw new CommonException("Object Factory not init finish");
		}
	}

	public Object createNoProxy(Class<?> cls) {
		checkStatu();
		Object obj = objs.get(cls);
		if(obj != null && !(obj instanceof ProxyObject)){
			return  obj;
		}
		try {
			obj = cls.newInstance();
			doAfterCreate(obj,null);
			//will replace the proxy object if exist, this is no impact to client
			cacheObj(cls,obj,false);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new CommonException("Fail to create obj for ["+cls.getName()+"]",e);
		}
		return obj;
	}
	
	private void cacheObj(Class<?> cls,Object obj,boolean check){
		cls = ProxyObject.getTargetCls(cls);
		if(check && objs.containsKey(cls)){
			throw new CommonException("class["+cls.getName()+"] instance exist");
		}
		objs.put(cls, obj);
		String comName = this.getComName(cls);
		if(!StringUtils.isEmpty(comName)){
			this.nameToObjs.put(comName, obj);
		}
		this.clsNameToObjs.put(cls.getName(), obj);
	}
	
	private boolean canCreate(Class<?> cls) {
		return !IObjectFactory.class.isAssignableFrom(cls) && cls.isAnnotationPresent(Component.class);
	}
	
	private <T> T createObject(Class<T> cls,boolean doAfterCreate) {
		if(!canCreate(cls)) {
			return null;
		}
		Object obj = null;
		try {
			if(!isLazy(cls)) {
				obj = cls.newInstance();
				if(doAfterCreate){
					 doAfterCreate(obj,null);
				}
			} else {
				obj = createLazyProxyObject(cls);
			}
		} catch (InstantiationException | IllegalAccessException e) {
			throw new CommonException("Fail to create obj for ["+cls.getName()+"]",e);
		}
		return  (T)obj;
	}
	
	private void doAfterCreate(Object obj,Config cfg) {
    	 if(cfg == null){
    		  cfg = (Config)objs.get(Config.class);
    	 }
    	 if(cfg == null){
    		 throw new CommonException("Config not load!");
    	 }
    	 
    	 /*if(obj instanceof IMessageReceiver) {
    		 logger.debug("");
    	 }*/
    	 
    	 if(!(obj instanceof ProxyObject)){
    		 injectDepependencies(obj);
    		 notifyPreInitPostListener(obj,cfg);
        	 doInit(obj);
    		 notifyAfterInitPostListener(obj,cfg);
    	 }
	}
     
     private void notifyAfterInitPostListener(Object obj,Config cfg) {
 		if(this.postListeners.isEmpty()) {
 			return;
 		}
 		for(IPostInitListener l : this.postListeners){
 			l.afterInit(obj,cfg);
 		}	
 	}
     
	private void notifyPreInitPostListener(Object obj,Config cfg) {
		if(this.postListeners.isEmpty()) {
			return;
		}
		for(IPostInitListener l : this.postListeners){
			l.preInit(obj,cfg);
		}	
	}

	private boolean isLazy(Class<?> cls) {
		if(cls.isAnnotationPresent(Component.class)){
			Component lazy = cls.getAnnotation(Component.class);
			return lazy.lazy();
		}
		return true;
	}

	public synchronized void start(){
		if(!isInit.compareAndSet(0, 1)){
			if(isInit.get() == 1) {
				synchronized(isInit) {
					try {
						isInit.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			return;
		}
		
		Set<Class<?>> listeners = ClassScannerUtils.getIns().loadClassByClass(IPostInitListener.class);
		if(listeners != null && !listeners.isEmpty()) {
			for(Class<?> c : listeners){
				PostListener comAnno = c.getAnnotation(PostListener.class);
				int mod = c.getModifiers();
				if((comAnno != null && !comAnno.value())|| Modifier.isAbstract(mod) 
						|| Modifier.isInterface(mod) || !Modifier.isPublic(mod)
						){
					continue;
				}
				
				try {
					IPostInitListener l = (IPostInitListener)c.newInstance();
					this.addPostListener(l);
				} catch (InstantiationException | IllegalAccessException e) {
					logger.error("Create IPostInitListener Error",e);
				}
			}
		}
		
		boolean serverOnly = Config.isServerOnly();
		
		boolean clientOnly = Config.isClientOnly();
		
		/**
		 * 意味着此两个参数只能在命令行或环境变量中做配置，不能在ZK中配置，因为此时ZK还没启动，此配置正是ZK的启动配置
		 */
		String dataOperatorName = Config.getCommandParam(Constants.DATA_OPERATOR, String.class, Constants.DEFAULT_DATA_OPERATOR);
		String registryName = Config.getCommandParam(Constants.REGISTRY_KEY, String.class, Constants.DEFAULT_REGISTRY);
	
		IRegistry registry = null;
		IDataOperator dop = null;
		ServiceManager srvManager = null;
		
		/*Set<IServer> servers = new HashSet<>();
		ServiceLoader sl = null;*/
		
		Set<Object> systemObjs = new HashSet<>();
		
		Set<Class<?>> clses = ClassScannerUtils.getIns().getComponentClass();
		if(clses != null && !clses.isEmpty()) {
			for(Class<?> c : clses){
				if(!this.canCreate(c)){
					continue;
				}
				
				Component cann = c.getAnnotation(Component.class);
				if(!cann.active()){
					logger.debug("disable com: "+c.getName());
					continue;
				}
				
				if(serverOnly && isComsumerSide(ProxyObject.getTargetCls(c))) {
					//指定了服务端或客户端，不需要另一方所特定的组件
					logger.debug("serverOnly server disable: "+c.getName());
					continue;
				}
				
				if(clientOnly && isProviderSide(ProxyObject.getTargetCls(c))) {
					logger.debug("clientOnly client disable: "+c.getName());
						continue;
					}
				
				//logger.debug("enable com: "+c.getName());
				Object obj = null;
				if(c.isAnnotationPresent(Service.class)) {
					 obj = createServiceObject(c,false);
				} else if(c.isAnnotationPresent(HttpHandler.class)) {
					obj = createHttpHanderObject(c);
				} else {
					obj = this.createObject(c, false);
				}
				this.cacheObj(c, obj, true);
				
				if(IDataOperator.class.isAssignableFrom(c) && dataOperatorName.equals(cann.value())){
					if(dop == null) {
						dop = (IDataOperator)obj;
					}else {
						throw new CommonException("More than one [" +dataOperatorName+"] to be found ["+c.getName()+", "+dop.getClass().getName()+"]" );
					}
					systemObjs.add(dop);
				}
				
				if(ServiceManager.class == c) {
					if(srvManager == null) {
						srvManager = (ServiceManager)obj;
					}else {
						throw new CommonException("More than one [" +ServiceManager.class.getName()+"] to be found ["+c.getName()+", "+srvManager.getClass().getName()+"]" );
					}
					systemObjs.add(srvManager);
				}
				
				/*if(ServiceLoader.class == c) {
					if(sl == null) {
						sl = (ServiceLoader)obj;
					}else {
						throw new CommonException("More than one [" +ServiceLoader.class.getName()+"] to be found ["+c.getName()+", "+sl.getClass().getName()+"]" );
					}
					systemObjs.add(sl);
				}
				
				if(IServer.class.isAssignableFrom(c) ) {
					IServer server = (IServer)obj;
					servers.add(server);
					systemObjs.add(servers);
				}*/
				
				if(IRegistry.class.isAssignableFrom(c) && registryName.equals(cann.value())){
					if(registry == null) {
						registry = (IRegistry)obj;
					}else {
						throw new CommonException("More than one [" +registryName+"] to be found ["+c.getName()+", "+registry.getClass().getName()+"]" );
					}
					systemObjs.add(srvManager);
				}
			}
		}
		
		if(dop == null){
			throw new CommonException("IDataOperator with name :"+dataOperatorName +" not found!");
		}
		
		//this.cacheObj(IObjectFactory.class, this, true);
		
		dop.init();
		
		Config cfg = (Config)objs.get(Config.class);
		//初始化配置目录
		cfg.setDataOperator(dop);
		//IConfigLoader具体的配置加载类
		List<IConfigLoader> configLoaders = this.getByParent(IConfigLoader.class);
		//加载配置，并调用init0方法做初始化
		cfg.loadConfig(configLoaders);
		
		if(registry == null){
			throw new CommonException("IRegistry with name :"+registryName +" not found!");
		}
		
		srvManager.setDataOperator(dop);
		srvManager.init();
		
		registry.setDataOperator(dop);
		registry.setSrvManager(srvManager);
		registry.init();
		
		/*sl.setRegistry(registry);
		sl.init();*/
		
		clientServiceProxyManager = new ClientServiceProxyManager(this);
		clientServiceProxyManager.init();
		
		//取得全部工厂监听器
		List<IFactoryListener> postL = this.getByParent(IFactoryListener.class);
		postReadyListeners.addAll(postL);
		postReadyListeners.sort(new Comparator<IFactoryListener>(){
			@Override
			public int compare(IFactoryListener o1, IFactoryListener o2) {
				return o1.runLevel() > o2.runLevel()?1:o1.runLevel() == o2.runLevel()?0:-1;
			}
		});
		
		//初始化前监听器
		for(IFactoryListener lis : this.postReadyListeners){
			//在这里可以注册实例
			lis.preInit(this);;
		}
		
		List<Object> l = new ArrayList<>();
		l.addAll(this.objs.values());
		l.sort(new Comparator<Object>(){
			@Override
			public int compare(Object o1, Object o2) {
				Component c1 = ProxyObject.getTargetCls(o1.getClass()).getAnnotation(Component.class);
				Component c2 = ProxyObject.getTargetCls(o2.getClass()).getAnnotation(Component.class);
				if(c1 == null && c2 == null) {
					return 0;
				}else if(c1 == null && c2 != null) {
					return -1;
				}else if(c1 != null && c2 == null) {
					return 1;
				}else {
					return c1.level() > c2.level()?1:c1.level() == c2.level()?0:-1;
				}
			}
		});
		
		this.cacheObj(this.getClass(), this, true);
		
		notifyPreInitPostListener(registry,cfg);
		
		Set<Object> haveInits = new HashSet<>();
		
		if(!l.isEmpty()){
			
			for(int i =0; i < l.size(); i++){
				Object o = l.get(i);
				
				 if(o instanceof ProxyObject){
		    		continue;
		    	 }
				 
				/*if(IIdServer.class.isInstance(o)) {
					 //此代码仅用于开发测试，正式代码中应该注掉
					 //用于测试具体某个组件的配置初始化
					 logger.debug(o.toString());
				 }*/
				
				if(systemObjs.contains(o) ||  haveInits.contains(o)) {
					continue;
				}
				haveInits.add(o);
				//只要在初始化前注入配置信息
				notifyPreInitPostListener(o,cfg);
			}
			
			haveInits.clear();
			
			//依赖注入
			for(int i =0; i < l.size(); i++){
				Object o = l.get(i);
				
				 if(o instanceof ProxyObject){
		    		continue;
		    	 }
				 
				/*if(IIdServer.class.isInstance(o)) {
					 //此代码仅用于开发测试，正式代码中应该注掉
					 //用于测试具体某个组件的配置初始化
					 logger.debug(o.toString());
				 }*/
				
				if(systemObjs.contains(o) || haveInits.contains(o) || !isEnable(o)) {
					continue;
				}
				
				haveInits.add(o);
				injectDepependencies(o);
				//doAfterCreate(o,cfg);
			}
			
			haveInits.clear();
			
			for(int i =0; i < l.size(); i++){
				Object o = l.get(i);
				
				 if(o instanceof ProxyObject){
		    		continue;
		    	 }
				 
				/*if(IIdServer.class.isInstance(o)) {
					 //此代码仅用于开发测试，正式代码中应该注掉
					 //用于测试具体某个组件的配置初始化
					 logger.debug(o.toString());
				 }*/
				
				if(systemObjs.contains(o) || haveInits.contains(o) || !isEnable(o)) {
					continue;
				}
				haveInits.add(o);
				//调用各组件的init方法
				doInit(o);
			}
			
			haveInits.clear();
			for(int i =0; i < l.size(); i++){
				Object o = l.get(i);
				
				 if(o instanceof ProxyObject){
		    		continue;
		    	 }
				 
				/*if(IIdServer.class.isInstance(o)) {
					 //此代码仅用于开发测试，正式代码中应该注掉
					 //用于测试具体某个组件的配置初始化
					 logger.debug(o.toString());
				 }*/
				
				if(systemObjs.contains(o) || haveInits.contains(o) || !isEnable(o)) {
					continue;
				}
				haveInits.add(o);
				//通知初始化完成
				processReference(o);
			}
			
			haveInits.clear();
			for(int i =0; i < l.size(); i++){
				Object o = l.get(i);
				
				 if(o instanceof ProxyObject){
		    		continue;
		    	 }
				 
				/*if(IIdServer.class.isInstance(o)) {
					 //此代码仅用于开发测试，正式代码中应该注掉
					 //用于测试具体某个组件的配置初始化
					 logger.debug(o.toString());
				 }*/
				
				if(systemObjs.contains(o) || haveInits.contains(o) || !isEnable(o)) {
					continue;
				}
				haveInits.add(o);
				//通知初始化完成
				notifyAfterInitPostListener(o,cfg);
			}
			haveInits.clear();
			
		}
		
		haveInits = null;
		
		//RpcClassloaderClient在preinit时注入
		RpcClassLoader cl = this.get(RpcClassLoader.class);
		if(cl != null) {
			Thread.currentThread().setContextClassLoader(cl);
		}
		
		for(IFactoryListener lis : this.postReadyListeners){
			lis.afterInit(this);
		}
		
		fromLocal = false;
		
		isInit.set(2);
		synchronized(isInit){
			isInit.notifyAll();
		}
	}
	
	private boolean isEnable(Object o) {
		if(o == null) {
			return false;
		}
		
		try {
			Method m = o.getClass().getMethod( "isEnable", new Class<?>[0]);
			return (Boolean)m.invoke(o, new Object[0]);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			try {
				Method m = o.getClass().getMethod( "isEnable", new Class<?>[0]);
				return (Boolean)m.invoke(o, new Object[0]);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
				try {
					Field f = o.getClass().getField("enable");
					boolean acc = f.isAccessible();
					if(!acc) {
						f.setAccessible(true);
					}
					Boolean v = f.getBoolean(o);
					f.setAccessible(acc);
					return v;
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e2) {
				}
			}
		}
		
		return true;
	}

	private Class<?> loadCls(String clsName) {
		
		Class<?> cls = ClassScannerUtils.getIns().getClassByName(clsName);
		if(cls == null) {
			try {
				cls = Thread.currentThread().getContextClassLoader().loadClass(clsName);
			} catch (ClassNotFoundException e) {
			}
		}
		
		if(cls == null) {
			try {
				cls = this.getClass().getClassLoader().loadClass(clsName);
			} catch (ClassNotFoundException e) {
			}
		}
		
		if(cls == null) {
			RpcClassLoader cl = this.get(RpcClassLoader.class);
			if(cl != null) {
				try {
					cls = cl.loadClass(clsName);
				} catch (ClassNotFoundException e) {
				}
			}
		}
		
		return cls;
		
	}

	private Object createHttpHanderObject(Class<?> c) {
		Object o = this.httpHandlerManager.createHandler(c);	
		return o;
	}

	private Object createServiceObject(Class<?> cls, boolean doAfterCreate) {
		Object obj = createDynamicService(cls);
		if(doAfterCreate){
			 doAfterCreate(obj,null);
		}
		return  obj;
	}

	@Override
	public boolean exist(Class<?> clazz) {
		checkStatu();
		return this.objs.containsKey(clazz);
	}

	@Override
	public void regist(Object obj) {		
		this.cacheObj(obj.getClass(), obj,true);
	}

	@Override
	public void regist(Class<?> clazz, Object obj) {
		this.cacheObj(clazz, obj,true);
	}

	@Override
	public void addPostListener(IPostInitListener listener) {
		for(IPostInitListener l : postListeners){
			if(l.getClass() == listener.getClass()) return;
		}
		postListeners.add(listener);
	}
	
	/*@Override
	public void addPostReadyListener(IFactoryListener listener) {
		for(IFactoryListener l : postReadyListeners){
			if(l.getClass() == listener.getClass()) return;
		}
		postReadyListeners.add(listener);
	}*/
	
	private boolean isProviderSide(Class<?> cls){
		//Class<?> cls = ProxyObject.getTargetCls(o.getClass());
		Component comAnno = cls.getAnnotation(Component.class);
		if(comAnno == null){
			return true;
		}
		return Constants.SIDE_PROVIDER.equals(comAnno.side());
	}
	
	private boolean isComsumerSide(Class<?> cls){
		//Class<?> cls = ProxyObject.getTargetCls(o.getClass());
		Component comAnno = cls.getAnnotation(Component.class);
		if(comAnno == null){
			return true;
		}
		return Constants.SIDE_COMSUMER.equals(comAnno.side());
	}
	
	private List<?> filterProviderSide(List<?> list){
		if(list == null || list.isEmpty()){
			return null;
		}
		Iterator<?> ite = list.iterator();
		while(ite.hasNext()){
			if(isProviderSide(ProxyObject.getTargetCls(ite.next().getClass()))){
				ite.remove();
			}
		}
		return list;
	}
	
	private  List<?> filterComsumerSide(List<?> list){
		if(list == null || list.isEmpty()){
			return null;
		}
		Iterator<?> ite = list.iterator();
		while(ite.hasNext()){
			if(isComsumerSide(ProxyObject.getTargetCls(ite.next().getClass()))){
				ite.remove();
			}
		}
		return list;
	}
	
	private void processReference(Object obj) {
		
		Class<?> cls = ProxyObject.getTargetCls(obj.getClass());
		List<Field> fields = new ArrayList<>();
		Utils.getIns().getFields(fields, cls);
		
		for(Field f : fields) {
			
			Object srv = null;
			boolean isRequired = false;
			Class<?> refCls = f.getType();
			
			//系统启动时可以为某些类指定特定的实现
			srv = this.getCommandSpecifyConponent(f);
			
			if(srv == null && f.isAnnotationPresent(Reference.class)){
				//Inject the remote service obj
				Reference ref = f.getAnnotation(Reference.class);
				/*
				String name = ref.value();
				if(StringUtils.isEmpty(name)) {
					name = f.getClass().getName();
				}
				*/
				isRequired = ref.required();
				
				try {
					srv = clientServiceProxyManager.createOrGetService(obj,f);
				} catch (CommonException e) {
					if(!isRequired) {
						logger.error("optional dependence cls ["+ f.getType().getName()
								+"] not found for class ["+obj.getClass().getName()+"]",e.getMessage());
					}else {
						throw e;
					}
				}
				
			}
			
			if(srv != null) {
				setObjectVal(obj, f, srv);
			} else if(isRequired) {
				throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] not found");
			} else {
				if(f.isAnnotationPresent(Reference.class))
					logger.warn("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] not found");
			}
			
		}
			
	}
	
	private void setObjectVal(Object obj,Field f,Object srv) {

		String setMethodName = "set"+f.getName().substring(0, 1).toUpperCase()+f.getName().substring(1);
		Method m = null;
		try {
			 m = obj.getClass().getMethod(setMethodName, f.getType());
			 m.invoke(obj, srv);
		} catch (InvocationTargetException | NoSuchMethodException e1) {
		    boolean bf = f.isAccessible();
			if(!bf) {
				f.setAccessible(true);
			}
			try {
				f.set(obj, srv);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new CommonException("",e);
			}
			if(!bf) {
				f.setAccessible(bf);
			} 
		}catch(SecurityException | IllegalAccessException | IllegalArgumentException e1){
			throw new CommonException("Class ["+obj.getClass().getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] error",e1);
		}
	
	}
	
	private Object getCommandSpecifyConponent(Field f) {
		//系统启动时可以为某些类指定特定的实现
		Object srv = null;
		String commandComName = Config.getCommandParam(f.getType().getName(), String.class, null);
		if(!StringUtils.isEmpty(commandComName) && ( f.isAnnotationPresent(Inject.class) || f.isAnnotationPresent(Reference.class) )) {
			//对于注入或引用的服务,命令行指定实现组件名称
			if(this.nameToObjs.containsKey(commandComName)) {
				srv = this.nameToObjs.get(commandComName);
			} else {
				//指定的组件不存在
				throw new CommonException("Component Name["+commandComName+"] for service ["+f.getType().getName()+"] not found");
			}
			
			if(!f.getType().isInstance(srv)) {
				//指定的组件不是该类的实例
				throw new CommonException("Component with Name["+commandComName+"] not a instance of class ["+f.getType().getName()+"]");
			}
		}
		
		return srv;
	}

	private void injectDepependencies(Object obj) {
		Class<?> cls = ProxyObject.getTargetCls(obj.getClass());
		List<Field> fields = new ArrayList<>();
		Utils.getIns().getFields(fields, cls);
		
		//Component comAnno = cls.getAnnotation(Component.class);
		
		boolean isProvider = isProviderSide(ProxyObject.getTargetCls(obj.getClass()));
		boolean isComsumer =  isComsumerSide(ProxyObject.getTargetCls(obj.getClass()));
		
		for(Field f : fields) {
			Object srv = null;
			boolean isRequired = false;
			Class<?> refCls = f.getType();
			
			srv = this.getCommandSpecifyConponent(f);
			
			if(srv == null && f.isAnnotationPresent(Inject.class)){
				//Inject the local component
				Inject inje = f.getAnnotation(Inject.class);
				String name = inje.value();
				isRequired = inje.required();
				Class<?> type = f.getType();
				
				if(type.isArray()) {
					Class<?> ctype = type.getComponentType();
					List<?> l = this.getByParent(ctype);
					
					if(isProvider){
						l = this.filterComsumerSide(l);
					}else if(isComsumer){
						l = this.filterProviderSide(l);
					}
					
					if(l != null && l.size() > 0){
						Object[] arr = new Object[l.size()];
						l.toArray(arr);
						srv = arr;
					}
				}else if(List.class.isAssignableFrom(type)){
					ParameterizedType genericType = (ParameterizedType) f.getGenericType();
					if(genericType == null){
						throw new CommonException("must be ParameterizedType for cls:"+ cls.getName()+",field: "+f.getName());
					}
					Class<?> ctype = (Class<?>)genericType.getActualTypeArguments()[0];
					
					List<?> l = this.getByParent(ctype);
					if(isProvider){
						l = this.filterComsumerSide(l);
					}else if(isComsumer){
						l = this.filterProviderSide(l);
					}
					
					if(l != null && l.size() > 0){
						boolean bf = f.isAccessible();
						Object o = null;
						if(!bf) {
							f.setAccessible(true);
						}
						try {
							o = f.get(obj);
						} catch (IllegalArgumentException | IllegalAccessException e) {
							throw new CommonException("",e);
						}
						if(!bf) {
							f.setAccessible(bf);
						}
						
						if(o == null){
							if(type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
								o = new ArrayList<Object>();
							} else {
								try {
									o = type.newInstance();
								} catch (InstantiationException | IllegalAccessException e) {
									throw new CommonException("",e);
								}
							}
						}
						List<Object> el = (List<Object>)o;
						el.addAll(l);
						srv = el;
					}
					
				}else if(Set.class.isAssignableFrom(type)){
					
					ParameterizedType genericType = (ParameterizedType) f.getGenericType();
					if(genericType == null){
						throw new CommonException("must be ParameterizedType for cls:"+ cls.getName()+",field: "+f.getName());
					}
					Class<?> ctype = (Class<?>)genericType.getActualTypeArguments()[0];
					List<?> l = this.getByParent(ctype);
					
					if(isProvider){
						l = this.filterComsumerSide(l);
					}else if(isComsumer){
						l = this.filterProviderSide(l);
					}
					
					if(l != null && l.size() > 0){
						boolean bf = f.isAccessible();
						Object o = null;
						if(!bf) {
							f.setAccessible(true);
						}
						try {
							o = f.get(obj);
						} catch (IllegalArgumentException | IllegalAccessException e) {
							throw new CommonException("",e);
						}
						if(!bf) {
							f.setAccessible(bf);
						}
						
						if(o == null){
							if(type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
								o = new HashSet<Object>();
							} else {
								try {
									o = type.newInstance();
								} catch (InstantiationException | IllegalAccessException e) {
									throw new CommonException("",e);
								}
							}
						}
						Set<Object> el = (Set<Object>)o;
						el.addAll(l);
						srv = el;
					}
					
				}else if(Map.class.isAssignableFrom(type)){
					ParameterizedType genericType = (ParameterizedType) f.getGenericType();
					if(genericType == null){
						throw new CommonException("must be ParameterizedType for cls:"+ cls.getName()+",field: "+f.getName());
					}
					Class<?> keyType = (Class<?>)genericType.getActualTypeArguments()[0];
					if(keyType != String.class) {
						throw new CommonException("Map inject only support String as key");
					}
					
					Class<?> valueType = (Class<?>)genericType.getActualTypeArguments()[1];
					if(valueType == Object.class) {
						logger.warn("{} as map key will cause all components to stop in class {} field {}",
								Object.class.getName(), cls.getName(),f.getName());
					}
					
					List<?> l = this.getByParent(valueType);
					if(isProvider){
						l = this.filterComsumerSide(l);
					}else if(isComsumer){
						l = this.filterProviderSide(l);
					}
					
					if(l != null && !l.isEmpty()) {
						boolean bf = f.isAccessible();
						Map map = null;
						if(!bf) {
							f.setAccessible(true);
						}
						try {
							map = (Map)f.get(obj);
						} catch (IllegalArgumentException | IllegalAccessException e) {
							throw new CommonException("",e);
						}
						if(!bf) {
							f.setAccessible(bf);
						}
						
						if(map == null){
							map = new HashMap();
						}
						
						for(Object com : l) {
							String comName = this.getComName(com.getClass());
							map.put(comName, com);
						}
						srv = map;
					}
				}else if(type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
					/*if(type == IObjectFactory.class) {
						logger.debug(type.getName());
					}*/
					List<?> l = this.getByParent(type);
					
					if(isProvider){
						l = this.filterComsumerSide(l);
					}else if(isComsumer){
						l = this.filterProviderSide(l);
					}
					
					if(l != null && !l.isEmpty() && StringUtils.isEmpty(name)) {
						if(l.size() == 1) {
							srv =  l.get(0);
						}else if(l.size() > 1) {
							StringBuffer sb = new StringBuffer("More implement for type [").append(cls.getName()).append("] ");
							for(Object s : l) {
								sb.append(s.getClass()).append(",");
							}
							throw new CommonException(sb.toString());
						}
					} else if(l != null && !l.isEmpty()){
						for(Object s : l) {
							String n = JMicro.getClassAnnoName(s.getClass());
							if(name.equals(n)){
								srv = s;
							}
						}
					}
				} else {
					String annName = name;
					if(annName != null && !"".equals(annName.trim())){
						srv = this.getByName(name);
						if(srv == null){
							this.getByParent(type);
						}
						srv = this.getByName(name);
					}
					if(srv == null) {
						srv = this.get(type);
					}
					if(srv != null){
						Class<?> clazz = ProxyObject.getTargetCls(srv.getClass());
						//如果没有Component注解，默认服务提供者及消费者都可用flag=true，只有定义了一方可用的组件，才需要做检测
						boolean flag = this.isComsumerSide(clazz) && this.isProviderSide(clazz);
						if(!flag) {
							if(isProvider && this.isComsumerSide(clazz)){
								throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] side should provider");
							}else if(isComsumer && this.isProviderSide(ProxyObject.getTargetCls(srv.getClass()))){
								throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] side should comsumer");
							}
						}
						
					
					}
				}
			}
			
			if(srv != null) {
				setObjectVal(obj, f, srv);
			} else if(isRequired) {
				throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] not found");
			}
		}
		
	}
	
	private Object createDynamicService(Class<?> cls) {
		 ClassGenerator classGenerator = ClassGenerator.newInstance(Thread.currentThread().getContextClassLoader());
		 classGenerator.setClassName(cls.getName()+"$JmicroSrv"+SimpleObjectFactory.idgenerator.getAndIncrement());
		 classGenerator.setSuperClass(cls);
		 classGenerator.addInterface(IServerServiceProxy.class);
		 classGenerator.addDefaultConstructor();
		 
		 Service srvAnno = cls.getAnnotation(Service.class);
		 Class<?> srvInterface = srvAnno.infs();
		 if(srvInterface == null || srvInterface == Void.class){
			 if(cls.getInterfaces() == null || cls.getInterfaces().length != 1) {
				 throw new CommonException("Class ["+cls.getName()+"] must implements one and only one service interface");
			 }
			 srvInterface = cls.getInterfaces()[0];
		 }
		 classGenerator.addInterface(srvInterface);
		 
		 
		 //classGenerator.addField("public static java.lang.reflect.Method[] methods;");
		 //classGenerator.addField("private " + InvocationHandler.class.getName() + " handler = new org.jmicro.api.client.ServiceInvocationHandler(this);");
      
/*		 classGenerator.addField("private boolean enable=true;");
		 classGenerator.addMethod("public java.lang.String getNamespace(){ return \"" + ServiceItem.namespace(srvAnno.namespace()) + "\";}");
		 classGenerator.addMethod("public java.lang.String getVersion(){ return \"" + ServiceItem.version(srvAnno.version()) + "\";}");
		 classGenerator.addMethod("public java.lang.String getServiceName(){ return \"" + srvInterface.getName() + "\";}");
		 classGenerator.addMethod("public boolean enable(){  return this.enable;}");
		 classGenerator.addMethod("public void enable(boolean en){ this.enable=en;}");*/
		 classGenerator.addMethod("public java.lang.String wayd(java.lang.String msg){ return msg;}");
		 
		 //只为代理接口生成代理方法,别的方法继承自原始类
		 Method[] ms1 = srvInterface.getMethods();
		 
		 //Method[] ms2 = new Method[ms1.length];
		/* if(cls.getName().equals("org.jmicro.example.provider.TestRpcServiceImpl")) {
			 System.out.println("");
		 }*/
		 logger.debug("Create Service: {}",cls.getName());
		 for(int i =0; i < ms1.length; i++) {
		     //Method m1 = ms1[i];
		     Method m = null;
			try {
				m = cls.getMethod(ms1[i].getName(), ms1[i].getParameterTypes());
			} catch (NoSuchMethodException | SecurityException e) {
				throw new CommonException("Method not found: " + ms1[i].getName());
			}
			 if (m.getDeclaringClass() == Object.class || !Modifier.isPublic(m.getModifiers())){
				 continue;
		     }
			 
		   //ms2[i] = m;
		   
		   Class<?> rt = m.getReturnType();
           Class<?>[] pts = m.getParameterTypes();

           StringBuilder code = new StringBuilder();

           if(!Void.TYPE.equals(rt)) {
        	   code.append(ReflectUtils.getName(rt)).append(" ret = ");
           }
           code.append(" super.").append(m.getName()).append("(");
           for(int j = 0; j < pts.length; j++){
          	 code.append("$").append(j + 1);
          	 if(j < pts.length-1){
          		code.append(",");
          	 }
           }
           code.append(");");
           
           if (!Void.TYPE.equals(rt)) {
        	   code.append(" return ret;");
           }
           logger.debug(code.toString());
           classGenerator.addMethod(m.getName(), m.getModifiers(), rt, pts, m.getExceptionTypes(), code.toString());      
		 }
		 
		   Class<?> clazz = classGenerator.toClass();
       try {
      	    //clazz.getField("methods").set(null, ms2);
			Object proxy = clazz.newInstance();
			return proxy; 
		} catch (InstantiationException | IllegalArgumentException | IllegalAccessException | SecurityException e1) {
			throw new CommonException("Fail to create proxy ["+ cls.getName()+"]");
		}
	}

	@SuppressWarnings("unchecked")
	private <T>  T createLazyProxyObject(Class<T> cls) {
		logger.debug("createLazyProxyObject: " + cls.getName());
		ClassGenerator cg = ClassGenerator.newInstance(Thread.currentThread().getContextClassLoader());
		cg.setClassName(cls.getName()+"$Jmicro"+idgenerator.getAndIncrement());
		cg.setSuperClass(cls.getName());
		cg.addInterface(ProxyObject.class);
		//cg.addDefaultConstructor();
		Constructor<?>[] cons = cls.getConstructors();
		Map<String,java.lang.reflect.Constructor<?>> consMap = new HashMap<>();
		String conbody = "this.conArgs=$args; for(int i = 0; i < $args.length; i++) { Object arg = $args[i]; this.conKey = this.conKey + arg.getClass().getName();}";
		for(Constructor<?> c : cons){
			String key = null;
			Class<?>[] ps = c.getParameterTypes();
			for(Class<?> p: ps){
				key = key + p.getName();
			}
			consMap.put(key, c);
			cg.addConstructor(c.getModifiers(),c.getParameterTypes(),c.getExceptionTypes(),conbody);
		}
		
		cg.addMethod("private void _init0() { if (this.init) return; this.init=true; this.target = ("+cls.getName()+")(factory.createNoProxy("+cls.getName()+".class));}");
		cg.addMethod("public Object getTarget(){ _init0(); return this.target;}");
		
		int index = 0;
		List<Method> methods = new ArrayList<>();
		Utils.getIns().getMethods(methods, cls);
		for(Method m : methods){
			if(Modifier.isPrivate(m.getModifiers()) || m.getDeclaringClass() == Object.class){
				continue;
			}
			StringBuffer sb = new StringBuffer();
			//sb.append("if (!this.init) { System.out.println( \"lazy init class:"+cls.getName()+"\"); this.init=true; this.target = ("+cls.getName()+")((java.lang.reflect.Constructor)constructors.get(this.conKey)).newInstance(this.conArgs);}");
			sb.append(" _init0();");
			Class<?> rt = m.getReturnType();
			
			if (!Void.TYPE.equals(rt)) {
				sb.append(ReflectUtils.getName(rt)).append(" v = ");
			}
			
			sb.append(" methods[").append(index).append("].invoke(this.target,$args); ");	
			
			if (!Void.TYPE.equals(rt)) {
				sb.append(" return v ;");
			}
			cg.addMethod(m.getName(), m.getModifiers(), m.getReturnType(), m.getParameterTypes(),
					m.getExceptionTypes(), sb.toString());
			index++;
		} 
		cg.addField("private boolean init=false;");
		cg.addField("private "+cls.getName()+" target=null; ");
		cg.addField("private java.lang.String conKey;");
		cg.addField("private java.lang.Object[] conArgs;");
		
		cg.addField("public static java.lang.reflect.Method[] methods;");
		cg.addField("public static org.jmicro.objfactory.simple.SimpleObjectFactory factory;");
		
		Class<?> cl = cg.toClass();
		
		try {
			cl.getField("methods").set(null, cls.getMethods());
			cl.getField("factory").set(null, this);
			Object o = cl.newInstance();
			//doAfterCreate(o);
			return (T)o;
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException | InstantiationException e) {
			logger.error("Create lazy proxy error for: "+ cls.getName(), e);
		}
		return null;
	}

	

	 public static String asArgument(Class<?> cl, String name) {
	        if (cl.isPrimitive()) {
	            if (Boolean.TYPE == cl)
	                return name + "==null?false:((Boolean)" + name + ").booleanValue()";
	            if (Byte.TYPE == cl)
	                return name + "==null?(byte)0:((Byte)" + name + ").byteValue()";
	            if (Character.TYPE == cl)
	                return name + "==null?(char)0:((Character)" + name + ").charValue()";
	            if (Double.TYPE == cl)
	                return name + "==null?(double)0:((Double)" + name + ").doubleValue()";
	            if (Float.TYPE == cl)
	                return name + "==null?(float)0:((Float)" + name + ").floatValue()";
	            if (Integer.TYPE == cl)
	                return name + "==null?(int)0:((Integer)" + name + ").intValue()";
	            if (Long.TYPE == cl)
	                return name + "==null?(long)0:((Long)" + name + ").longValue()";
	            if (Short.TYPE == cl)
	                return name + "==null?(short)0:((Short)" + name + ").shortValue()";
	            throw new RuntimeException(name + " is unknown primitive type.");
	        }
	        return "(" + ReflectUtils.getName(cl) + ")" + name;
	    }
	 
	 private String getComName(Class<?> cls) {
		 return JMicro.getClassAnnoName(cls);
	 }
	 
	private void doInit(Object obj) {
		Class<?> tc = ProxyObject.getTargetCls(obj.getClass());
		Method initMethod1 = null;
		Method initMethod2 = null;
		List<Method> methods = new ArrayList<>();
		Utils.getIns().getMethods(methods, tc);
		for(Method m : methods ) {
			if(m.isAnnotationPresent(JMethod.class)) {
				JMethod jm = m.getAnnotation(JMethod.class);
				if("init".equals(jm.value())) {
					initMethod1 = m;
					break;
				}
			}else if(m.getName().equals("init")) {
				initMethod2 = m;
			}
		}
		try {
			if(initMethod1 != null) {
				initMethod1.invoke(obj, new Object[]{});
			}else if(initMethod2 != null){
				initMethod2.invoke(obj, new Object[]{});
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			logger.error("Component init error:"+obj.getClass().getName(),e);
		}
	}

}
