package org.jmicro.example.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Set;

import org.jmicro.api.JMicro;
import org.jmicro.api.codec.ICodecFactory;
import org.jmicro.api.codec.IDecoder;
import org.jmicro.api.codec.IEncoder;
import org.jmicro.api.codec.JDataInput;
import org.jmicro.api.codec.JDataOutput;
import org.jmicro.api.config.Config;
import org.jmicro.api.net.Message;
import org.jmicro.api.net.RpcRequest;
import org.jmicro.api.objectfactory.IObjectFactory;
import org.jmicro.api.registry.IRegistry;
import org.jmicro.api.registry.ServiceItem;
import org.jmicro.common.Constants;
import org.jmicro.common.Utils;
import org.jmicro.example.test.vo.ResponseVo;
import org.junit.Test;

public class TestRpcRequest {

	@Test
	public void testRpcRequest() throws SQLException {
		 
		 RpcRequest req = new RpcRequest();
		 //req.setSession();
		 
		 //req.setImpl("org.jmicro.objfactory.simple.TestRpcService");
		 req.setServiceName("org.jmicro.objfactory.simple.ITestRpcService");
		 req.setMethod("hello");
		 req.setArgs(new Object[]{"Yyl"});
		 req.setNamespace("test");
		 req.setVersion("1.0.0");
		 req.setRequestId(1000L);
		 
		// JmicroManager.getIns().addRequest(req);
		 Utils.getIns().waitForShutdown();
	}
	
	@Test
	public void testRpcRequestVo() throws SQLException, IOException {
		 
		 RpcRequestVo req = new RpcRequestVo();
		 
		 req.setArgs(new Object[] {"222"});
		 req.setMsg(new Message());
		 req.setFinish(true);
		 req.setReqId(222L);
		 req.setImpl("test");
		 req.setNamespace("ns");
		 //req.setRequestId(355L);
		 req.setServiceName("3333");
		 
		 JDataOutput out = new JDataOutput();
		 req.encode(out, req);
		 
		 JDataInput ji = new JDataInput(out.getBuf());
			
		 RpcRequestVo reqvo = new RpcRequestVo();
		 reqvo.decode(ji);
		 System.out.println(reqvo);
		
	}
	
	@Test
	public void testRpcResponseVo() throws IOException {
		 
		ResponseVo req = new ResponseVo();
		 
		 req.setId(111L);
		 req.setMonitorEnable(true);
		 req.setMsg(new Message());
		 req.setReqId(222L);
		 req.setResult("Hello world");
		 req.setSuccess(true);
		 
		 JDataOutput out = new JDataOutput();
		 req.encode(out, req);
		 
		 JDataInput ji = new JDataInput(out.getBuf());
			
		 ResponseVo reqvo = new ResponseVo();
		 reqvo.decode(ji);
		 System.out.println(reqvo);
		
	}
	
	@Test
	public void testEncodeDecodeMessage() {
		IObjectFactory of = JMicro.getObjectFactoryAndStart(new String[] { "-DinstanceName=TestCodec" });
		ICodecFactory codeFactory = of.get(ICodecFactory.class);

		Message msg = new Message();
		msg.setType(Constants.MSG_TYPE_REQ_JRPC);
		msg.setProtocol(Message.PROTOCOL_BIN);
		msg.setReqId(2222L);
		msg.setVersion(Message.MSG_VERSION);
		msg.setLevel(Message.PRIORITY_NORMAL);
		
		//msg.setStream(false);
		msg.setDumpDownStream(false);
		msg.setDumpUpStream(false);
		msg.setNeedResponse(true);
		msg.setLoggable(true);
		msg.setMonitorable(true);
		msg.setDebugMode(true);
		
		msg.setInstanceName(Config.getInstanceName());
		msg.setTime(System.currentTimeMillis());
		msg.setLinkId(333L);
		msg.setMethod("testmethod");
		
		IEncoder encoder = codeFactory.getEncoder(Message.PROTOCOL_BIN);

		RpcRequest req = new RpcRequest();
		req.setArgs(new Object[] { 1, "222", new Long(22L) });
		req.setMsg(new Message());
		req.setFinish(true);
		req.setRequestId(222L);
		req.setImpl("test");
		req.setNamespace("ns");
		req.setRequestId(355L);
		req.setServiceName("3333");

		ByteBuffer bb = (ByteBuffer) encoder.encode(req);
		
		msg.setPayload(bb);
		
		ByteBuffer msgbb = msg.encode();
		
		Message reqMsg = Message.readMessage(msgbb);

		IDecoder decoder = codeFactory.getDecoder(Message.PROTOCOL_BIN);
		RpcRequest req1 = (RpcRequest) decoder.decode(reqMsg.getPayload(), RpcRequest.class);
		System.out.println(req1);
	}
	
/*	@Test
	public void testDynamicProxy() {
		ITestRpcService src = SimpleObjectFactory.createDynamicServiceProxy(
				ITestRpcService.class,Constants.DEFAULT_NAMESPACE,Constants.DEFAULT_VERSION);
		AbstractServiceProxy asp = (AbstractServiceProxy)src;
		asp.setHandler(new ServiceInvocationHandler());
		System.out.println(src.hello("Hello"));
		System.out.println("testDynamicProxy");
	}*/
	
	@Test
	public void testStartServer() {
		/*Config cfg = new Config();
		cfg.setBindIp("localhost");
		cfg.setPort(9800);;
		cfg.setBasePackages(new String[]{"org.jmicro","org.jmtest"});
		cfg.setRegistryUrl(new URL("zookeeper","localhost",2180));
		JMicroContext.setCfg(cfg);*/
		
		IObjectFactory of = JMicro.getObjectFactoryAndStart(new String[0]);
		Utils.getIns().waitForShutdown();
	}

/*	@SuppressWarnings("serial")
	@Test
	public void helloFiber() {
		new Fiber<String>() {
			@Override
			protected String run() throws SuspendExecution, InterruptedException {
				System.out.println("Hello Fiber");
				return "Hello Fiber";
			}
			
		}.start();
		
		Utils.getIns().waitForShutdown();
	}*/
	
	@Test
	public void testGetService() {
		IObjectFactory of = JMicro.getObjectFactoryAndStart(new String[]{"-DinstanceName=testGetService"});
		IRegistry registry = of.get(IRegistry.class);
		//registry.get
		//org.jmicro.example.api.ITestRpcService&testrpc&0.0.1
		
		/*Set<ServiceItem> sis = registry.getServices("org.jmicro.example.api.ITestRpcService", 
				"testrpc", "0.0.0<=x");*/
		
		/*Set<ServiceItem> sis = registry.getServices("org.jmicro.example.api.ITestRpcService", 
				"testrpc", "x<=2.0.0");*/
		
		/*Set<ServiceItem> sis = registry.getServices("org.jmicro.example.api.ITestRpcService", 
				"testrpc", "0.0.0<=x<=2.0.0");*/
		
		/*Set<ServiceItem> sis = registry.getServices("org.jmicro.example.api.ITestRpcService", 
				"testrpc", "*");*/
		
		Set<ServiceItem> sis = registry.getServices("org.jmicro.example.api.ITestRpcService", 
				"testrpc", "0.0.*");
		
		System.out.println(sis);
		Utils.getIns().waitForShutdown();
	}
}
