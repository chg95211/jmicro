package org.jmicro.objfactory.simple.integration.test;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.jmicro.api.JMicro;
import org.jmicro.api.pubsub.IInternalSubRpc;
import org.jmicro.api.pubsub.PSData;
import org.jmicro.api.pubsub.PubSubManager;
import org.jmicro.test.JMicroBaseTestCase;
import org.junit.Test;

public class TestPubSubServer extends JMicroBaseTestCase{

	@Test
	public void testPubSubServerMessage() {
		IInternalSubRpc psm = of.getRemoteServie(IInternalSubRpc.class.getName(), "org.jmicro.pubsub.DefaultPubSubServer", "0.0.1",null);
		PSData psd = new PSData();
		psd.setData(new byte[] {22,33,33});
		psd.setTopic(TOPIC);
		psm.publishData(psd);
	}

	@Test
	public void testPresurePublish() {
		
		final Random ran = new Random();
		
		PubSubManager psm = of.get(PubSubManager.class);
		
		AtomicInteger id = new AtomicInteger(0);
		
		Runnable r = ()->{
			while(true) {
				try {
					//Thread.sleep(2000);
					Thread.sleep(ran.nextInt(100));
					psm.publish(new HashMap<String,Object>(), TOPIC, 
							"test pubsub server id: "+id.getAndIncrement());
				} catch (Throwable e) {
					System.out.println(e.getMessage());;
				}
			}
		};
		
		new Thread(r).start();
		new Thread(r).start();
		/*new Thread(r).start();
		new Thread(r).start();
		new Thread(r).start();*/
		
		JMicro.waitForShutdown();
	}
	
	@Test
	public void testPublishStringMessage() {
		PubSubManager psm = of.get(PubSubManager.class);
		psm.publish(new HashMap<String,Object>(), TOPIC, "test pubsub server");
		JMicro.waitForShutdown();
	}
}
