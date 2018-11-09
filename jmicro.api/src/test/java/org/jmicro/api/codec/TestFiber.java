package org.jmicro.api.codec;

import java.nio.ByteBuffer;

import org.jmicro.api.net.RpcResponse;
import org.junit.Test;

public class TestFiber {

	@Test
	public void testEndoceArrayResult(){
		RpcResponse resp = new RpcResponse(1,new Integer[]{1,2,3});
		
		ByteBuffer dest = ByteBuffer.allocate(1024);
		Encoder.encodeObject(dest, resp);
		dest.flip();
		
		RpcResponse result = Decoder.decodeObject(dest);
		Object r = result.getResult();
		System.out.println(r);
	}
	
	@Test
	public void testEndoceByteBuffer(){
		byte[] data = new byte[]{'a','b','c','d','e','f','g',};
		ByteBuffer src = ByteBuffer.wrap(data);
		ByteBuffer dest = ByteBuffer.allocate(64);
		Encoder.encodeObject(dest, src);
		dest.flip();
		
		ByteBuffer ebb = Decoder.decodeObject(dest);
		
		System.out.println(new String(ebb.array()));
	}
}
