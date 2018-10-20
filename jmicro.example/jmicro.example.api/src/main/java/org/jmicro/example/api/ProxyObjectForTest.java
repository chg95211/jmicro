package org.jmicro.example.api;

import org.jmicro.api.annotation.Component;
import org.jmicro.api.annotation.Inject;
import org.jmicro.api.registry.IRegistry;

@Component(lazy=false)
public class ProxyObjectForTest {
	
	@Inject
	private IRegistry registry;
	
	private String msg = "ProxyObjectForTest";
	
	public ProxyObjectForTest(String msg){
		this.msg = msg;
	}
	
	public ProxyObjectForTest(){}
	
	public void invokeRpcService(){
		System.out.println("invokeRpcService: "+this.msg);
	}
	
	public void invokeRpcService1(){
		System.out.println("invokeRpcService1: "+this.msg);
	}
	
}
