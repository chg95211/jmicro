package org.jmicro.example.test.pubsub;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.jmicro.api.codec.ICodecFactory;
import org.jmicro.api.codec.JDataInput;
import org.jmicro.api.codec.JDataOutput;
import org.jmicro.api.codec.PrefixTypeEncoderDecoder;
import org.jmicro.api.monitor.MonitorConstant;
import org.jmicro.api.net.Message;
import org.jmicro.api.registry.AsyncConfig;
import org.jmicro.api.registry.ServiceMethod;
import org.jmicro.common.Constants;
import org.jmicro.common.Utils;
import org.jmicro.example.comsumer.TestRpcClient;
import org.jmicro.test.JMicroBaseTestCase;
import org.junit.Test;

public class TestPubsub extends JMicroBaseTestCase{
	
	@Test
	public void testAsyncCallRpc() {
		of.get(TestRpcClient.class).testCallAsyncRpc();
		Utils.getIns().waitForShutdown();
	}
	
	@Test
	public void testEncodePSData() throws IOException {
		org.jmicro.api.pubsub.PSData psd = new org.jmicro.api.pubsub.PSData();
		AsyncConfig as = new AsyncConfig();
		as.setCondition("222");
		as.setEnable(true);
		as.setForMethod("hello");
		as.setMethod("callback");
		as.setNamespace("ts");
		as.setParamStr(null);
		as.setServiceName("testas");
		as.setVersion("222");
		psd.put("asc", as);
		
		psd.put("linkId", 222L);
		psd.put("key", "ssss");
		
		Object obj = psd;
		org.jmicro.api.codec.ISerializeObject so = (org.jmicro.api.codec.ISerializeObject)obj;
		JDataOutput out = new JDataOutput();
		so.encode(out, null);
		
		JDataInput ji = new JDataInput(out.getBuf());
		so.decode(ji);
		
		System.out.println(so);
		
	}
	
	@Test
	public void testEncodePSData1() throws IOException {
		PSData psd = new PSData();
		AsyncConfig as = new AsyncConfig();
		psd.put("asc", as);
		
		psd.put("linkId", 222L);
		//psd.put("key", "ssss");
		
		JDataOutput out = new JDataOutput();
		psd.encode(out, null);
		
		PSData decodePsd = new PSData();
		
		JDataInput ji = new JDataInput(out.getBuf());
		decodePsd.decode(ji);
		
		System.out.println(decodePsd);
	}
	
	@Test
	public void testEncodePSDatas() throws IOException {
		
		ICodecFactory ed = of.get(ICodecFactory.class);
		
		org.jmicro.api.pubsub.PSData psd = new org.jmicro.api.pubsub.PSData();
		psd.setData(new byte[] {22,33,33});
		psd.setId(0);
		psd.setTopic("/test/testtopic");
		
		//psd.getContext().put("key", 222);
		
		RpcRequest req = new RpcRequest();
		req.setMethod("method");
		req.setServiceName("serviceName");
		req.setNamespace("namespace");
		req.setVersion("0.0.1");
		Object[] args = new Object[] {"23fsaf",new Object[] {psd,psd}};
		req.setArgs(args);
		req.setRequestId(22L);
		req.setTransport(Constants.TRANSPORT_NETTY);
		req.setImpl("fsafd");
		
		//Message msg = new Message();
		
		ByteBuffer bb = (ByteBuffer)ed.getEncoder(Message.PROTOCOL_BIN).encode(req);
		
		RpcRequest obj = (RpcRequest)ed.getDecoder(Message.PROTOCOL_BIN).decode(bb, null);
		
		/*msg.setPayload(bb);
		ByteBuffer msgBb = msg.encode();
		
		Message respMsg = Message.readMessage(msgBb);
		
		Object obj = ed.decode((ByteBuffer)respMsg.getPayload());*/
		
		System.out.println(obj);
	}
	
	@Test
	public void testEncodePSDataWithMapAsArgs() throws IOException {
		
		PrefixTypeEncoderDecoder ed = of.get(PrefixTypeEncoderDecoder.class);
		
		Map<Short,Object> data = new HashMap<>();
		data.put(MonitorConstant.CLIENT_CONNECT_FAIL, 222D);
		data.put(MonitorConstant.CLIENT_IOSESSION_CLOSE, new ServiceMethod());
		//data.values().iterator()
		//org.jmicro.api.pubsub.PSData psData = new org.jmicro.api.pubsub.PSData();
		
		PSData psData = new PSData();
		psData.put(Constants.SERVICE_METHOD_KEY, new ServiceMethod());
		psData.put(Constants.SERVICE_NAME_KEY, 2222);
		psData.setData(data);
		
		ByteBuffer buffer = ed.encode(psData);
		
		PSData r = ed.decode(buffer);
		
		System.out.println(r);
	}
	
	
	@Test
	public void testEncodeRequestWithPSData() throws IOException {
		
		org.jmicro.api.pubsub.PSData psd = new org.jmicro.api.pubsub.PSData();
		psd.setData(new byte[] {22,33,33});
		psd.setId(0);
		psd.setTopic("/test/testtopic");
		
		//psd.getContext().put("key", 222);
		
		RpcRequest req = new RpcRequest();
		req.setMethod("method");
		req.setServiceName("serviceName");
		req.setNamespace("namespace");
		req.setVersion("0.0.1");
		Object[] args = new Object[] {"23fsaf",new Object[] {psd,psd}};
		req.setArgs(args);
		req.setRequestId(22L);
		req.setTransport(Constants.TRANSPORT_NETTY);
		req.setImpl("fsafd");
		
		JDataOutput jo = new JDataOutput();
		
		Object obj = req;
		
		//((ISerializeObject)obj).encode(jo, obj);
		req.encode(jo, obj);
		
		
		JDataInput ji = new JDataInput(jo.getBuf());
		RpcRequest r1 = new RpcRequest();
		r1.decode(ji);
		
		System.out.print(r1);
	}
	

	
}
