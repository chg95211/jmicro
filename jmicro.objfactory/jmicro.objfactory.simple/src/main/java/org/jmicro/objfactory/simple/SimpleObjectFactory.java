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
import org.jmicro.api.config.Config;
import org.jmicro.api.config.IConfigLoader;
import org.jmicro.api.objectfactory.IObjectFactory;
import org.jmicro.api.objectfactory.IPostFactoryReady;
import org.jmicro.api.objectfactory.IPostInitListener;
import org.jmicro.api.objectfactory.ProxyObject;
import org.jmicro.api.service.IServerServiceProxy;
import org.jmicro.common.CommonException;
import org.jmicro.common.Constants;
import org.jmicro.common.util.ClassGenerator;
import org.jmicro.common.util.ReflectUtils;
import org.jmicro.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 
 * @author Yulei Ye
 * @date 2018年10月4日-下午12:12:24
 */
@ObjFactory
@Component(Constants.DEFAULT_OBJ_FACTORY)
public class SimpleObjectFactory implements IObjectFactory {

	static AtomicInteger idgenerator = new AtomicInteger();
	
	private final static Logger logger = LoggerFactory.getLogger(SimpleObjectFactory.class);
	
	private static AtomicInteger isInit = new AtomicInteger(0);
	
	private boolean fromLocal = true;
	
	private List<IPostFactoryReady> postReadyListeners = new ArrayList<>();
	
	private List<IPostInitListener> postListeners = new ArrayList<>();
	
	private Map<Class<?>,Object> objs = new ConcurrentHashMap<Class<?>,Object>();
	
	private Map<String,Object> nameToObjs = new ConcurrentHashMap<String,Object>();
	
	private Map<String,Object> clsNameToObjs = new ConcurrentHashMap<String,Object>();
	
	private ClientServiceProxyManager clientServiceProxyManager = null;
	
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
			
			if(obj == null){
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
		
		Object o = this.clientServiceProxyManager.getService(clsName);
		if(o != null){
			return (T)o;
		}
		
		Class<?> cls = ClassScannerUtils.getIns().getClassByAnnoName(clsName);
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
					&& !Modifier.isInterface(c.getModifiers()) && Modifier.isPublic(c.getModifiers())) {
				Object obj = this.get(c);
				if(obj != null) {
					list.add((T)obj);
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
			doAfterCreate(obj);
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
	
	private <T> T createObject(Class<T> cls,boolean doAfterCreate) {
		Object obj = null;
		try {
			if(!isLazy(cls)) {
				obj = cls.newInstance();
				if(doAfterCreate){
					 doAfterCreate(obj);
				}
			} else {
				obj = createLazyProxyObject(cls);
			}
		} catch (InstantiationException | IllegalAccessException e) {
			throw new CommonException("Fail to create obj for ["+cls.getName()+"]",e);
		}
		return  (T)obj;
	}
	
     private void doAfterCreate(Object obj) {
    	 injectDepependencies(obj);
    	 notifyPrePostListener(obj);
    	 doInit(obj);
		 notifyAfterPostListener(obj);
	}
     
     private void notifyAfterPostListener(Object obj) {
 		if(this.postListeners.isEmpty()) {
 			return;
 		}
 		for(IPostInitListener l : this.postListeners){
 			l.afterInit(obj);
 		}	
 	}
     
	private void notifyPrePostListener(Object obj) {
		if(this.postListeners.isEmpty()) {
			return;
		}
		for(IPostInitListener l : this.postListeners){
			l.preInit(obj);
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
						|| Modifier.isInterface(mod) || !Modifier.isPublic(mod)){
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
		
		/*Set<Class<?>> postFactoryListners = ClassScannerUtils.getIns().loadClassByClass(IPostFactoryReady.class);
		if(postFactoryListners != null && !postFactoryListners.isEmpty()) {
			for(Class<?> c : postFactoryListners){
				PostListener comAnno = c.getAnnotation(PostListener.class);
				int mod = c.getModifiers();
				if((comAnno != null && !comAnno.value()) || Modifier.isAbstract(mod) 
						|| Modifier.isInterface(mod) || !Modifier.isPublic(mod)){
					continue;
				}
				
				try {
					IPostFactoryReady l = (IPostFactoryReady)c.newInstance();
					this.addPostReadyListener(l);
				} catch (InstantiationException | IllegalAccessException e) {
					logger.error("Create IPostInitListener Error",e);
				}
			}
		}*/
		
		Set<Class<?>> clses = ClassScannerUtils.getIns().getComponentClass();
		if(clses != null && !clses.isEmpty()) {
			for(Class<?> c : clses){
				if(IObjectFactory.class.isAssignableFrom(c) || !c.isAnnotationPresent(Component.class)){
					continue;
				}
				if(c.isAnnotationPresent(Component.class)) {
					Component cann = c.getAnnotation(Component.class);
					if(cann.active()){
						Object obj = null;
						if(c.isAnnotationPresent(Service.class)) {
							 obj = createServiceObject(c,false);
						} else {
							obj = createObject(c,false);
						}
						this.cacheObj(c, obj, true);
					}
				}
				
			}
		}
		
		clientServiceProxyManager = new ClientServiceProxyManager(this);
		clientServiceProxyManager.init();
		List<Object> l = new ArrayList<>();
		l.addAll(this.objs.values());
		l.sort(new Comparator<Object>(){
			@SuppressWarnings("unused")
			@Override
			public int compare(Object o1, Object o2) {
				Component c1 = ProxyObject.getTargetCls(o1.getClass()).getAnnotation(Component.class);
				Component c2 = ProxyObject.getTargetCls(o2.getClass()).getAnnotation(Component.class);
				return c1.level() > c2.level()?1:c1.level() == c2.level()?0:-1;
			}
		});
		
		Config cfg = (Config)objs.get(Config.class);
		
		//Config cfg = this.get(Config.class);
		
		List<IConfigLoader> configLoaders = this.getByParent(IConfigLoader.class);
		
		for(IConfigLoader cl : configLoaders){
			cl.load(cfg.getParams());
		}
		
		cfg.init();
		
		if(!l.isEmpty()){
			for(int i =0; i < l.size(); i++){
				Object o = l.get(i);
				//System.out.println(o);
				doAfterCreate(o);
			}
		}
		
		List<IPostFactoryReady> postL = this.getByParent(IPostFactoryReady.class);
		
		for(IPostFactoryReady lis : postL){
			lis.ready(this);
		}
		
		for(IPostFactoryReady lis : this.postReadyListeners){
			lis.ready(this);
		}
		
		fromLocal = false;
		
		isInit.set(2);
		synchronized(isInit){
			isInit.notifyAll();
		}
	}

	private Object createServiceObject(Class<?> cls, boolean doAfterCreate) {
		Object obj = createDynamicService(cls);
		if(doAfterCreate){
			 doAfterCreate(obj);
		}
		return  obj;
	}

	private void createZK() {
		String zKClazzName="org.jmicro.zk.ZKDataOperator";
		Class<?> zkConnClazz = ClassScannerUtils.getIns().getClassByName(zKClazzName);
		Object zkConn = null;
		try {
			zkConn = zkConnClazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e1) {
			logger.error("", e1);
		}
		this.clsNameToObjs.put(zKClazzName, zkConn);
		this.nameToObjs.put("zkCoon", zkConn);
		this.objs.put(zkConnClazz, zkConn);
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
	
	@Override
	public void addPostReadyListener(IPostFactoryReady listener) {
		for(IPostFactoryReady l : postReadyListeners){
			if(l.getClass() == listener.getClass()) return;
		}
		postReadyListeners.add(listener);
	}
	
	private boolean isProviderSide(Object o){
		Class<?> cls = ProxyObject.getTargetCls(o.getClass());
		Component comAnno = cls.getAnnotation(Component.class);
		if(comAnno == null){
			return true;
		}
		return Constants.SIDE_PROVIDER.equals(comAnno.side());
	}
	
	private boolean isComsumerSide(Object o){
		Class<?> cls = ProxyObject.getTargetCls(o.getClass());
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
			if(isProviderSide(ite.next())){
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
			if(isComsumerSide(ite.next())){
				ite.remove();
			}
		}
		return list;
	}	

	private void injectDepependencies(Object obj) {
		Class<?> cls = ProxyObject.getTargetCls(obj.getClass());
		Field[] fs = cls.getDeclaredFields();
		
		Component comAnno = cls.getAnnotation(Component.class);
		
		boolean isProvider = isProviderSide(obj);
		boolean isComsumer =  isComsumerSide(obj);
		
		for(Field f : fs) {
			Object srv = null;
			boolean isRequired = false;
			if(f.isAnnotationPresent(Reference.class)){
				//Inject the remote service obj
				Reference ref = f.getAnnotation(Reference.class);
				/*
				String name = ref.value();
				if(StringUtils.isEmpty(name)) {
					name = f.getClass().getName();
				}
				*/
				isRequired = ref.required();
				
				srv = clientServiceProxyManager.createOrGetService(obj,f);
				//getServiceProxy(cls,f,ref,obj);
				
			}else if(f.isAnnotationPresent(Inject.class)){
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
					
					
				}else if(type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
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
				}else {
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
						if(isProvider && this.isComsumerSide(srv)){
							throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] side should provider");
						}else if(isComsumer && this.isProviderSide(srv)){
							throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] side should comsumer");
						}
					}
				}
			}
			
			if(srv != null) {
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
					throw new CommonException("Class ["+cls.getName()+"] field ["+ f.getName()+"] dependency ["+f.getType().getName()+"] error",e1);
				}
				
			}else if(isRequired) {
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
		 
		 //只为代理接口生成代理方法，别的方法继承自原始类
		 Method[] ms1 = srvInterface.getMethods();
		 
		 Method[] ms2 = new Method[ms1.length];
		 
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
        		   
           if (!Void.TYPE.equals(rt)) {
        	   code.append("Object ret = ");
           }
           code.append("super.").append(m.getName()).append("(");
           for (int j = 0; j < pts.length; j++){
          	 code.append("("+pts[j].getName()+")$").append(j + 1);
          	 if(j < pts.length-1){
          		code.append(",");
          	 }
           }
           code.append(");");
           
           if (!Void.TYPE.equals(rt)) {
        	   code.append(" return ").append(SimpleObjectFactory.asArgument(rt, "ret")).append(";");
           }

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
		for(Method m : cls.getMethods()){
			if(Modifier.isPrivate(m.getModifiers()) || m.getDeclaringClass() == Object.class){
				continue;
			}
			StringBuffer sb = new StringBuffer();
			//sb.append("if (!this.init) { System.out.println( \"lazy init class:"+cls.getName()+"\"); this.init=true; this.target = ("+cls.getName()+")((java.lang.reflect.Constructor)constructors.get(this.conKey)).newInstance(this.conArgs);}");
			sb.append(" _init0();");
			sb.append(" Object v = methods[").append(index).append("].invoke(this.target,$args); ");	
			Class<?> rt = m.getReturnType();
			if (!Void.TYPE.equals(rt)) {
				sb.append(" return ").append(asArgument(rt, "v")).append(";");
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
		Method initMethod1 = null;
		Method initMethod2 = null;
		for(Method m : obj.getClass().getDeclaredMethods()) {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
