package org.jmicro.example.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.jmicro.api.objectfactory.IObjectFactory;
import org.jmicro.api.servicemanager.ComponentManager;
import org.jmicro.example.comsumer.TestRpcClient;
import org.junit.Test;

public class TestRpcRequest {

	/*@Test
	public void testDynamicProxy() {
		ITestRpcService src = SimpleObjectFactory.createDynamicServiceProxy(ITestRpcService.class
				,Constants.DEFAULT_NAMESPACE,Constants.DEFAULT_VERSION);
		AbstractServiceProxy asp = (AbstractServiceProxy)src;
		asp.setHandler(new ServiceInvocationHandler());
		System.out.println(src.hello("Hello"));
		System.out.println("testDynamicProxy");
	}*/
	
	@Test
	public void testRpcClient() {
		TestRpcClient src = ComponentManager.getObjectFactory().get(TestRpcClient.class);
		src.invokeRpcService();	
	}
	
	@Test
	public void testInvokePersonService() {
		/*Config cfg = new Config();
		cfg.setBindIp("localhost");
		cfg.setPort(9801);;
		cfg.setBasePackages(new String[]{"org.jmicro","org.jmtest"});
		cfg.setRegistryUrl(new URL("zookeeper","localhost",2180));
		JMicroContext.setCfg(cfg);*/
		
		IObjectFactory of = ComponentManager.getObjectFactory();
		of.start();
		TestRpcClient src = of.get(TestRpcClient.class);
		src.invokePersonService();
	}
	
	
	@SuppressWarnings("static-access")
	@Test
	public void testSockerConn() {

        try {
            //创建Socket对象
            Socket socket=new Socket("localhost",9800);
            
            //根据输入输出流和服务端连接
            OutputStream outputStream=socket.getOutputStream();//获取一个输出流，向服务端发送信息
            PrintWriter printWriter=new PrintWriter(outputStream);//将输出流包装成打印流
            printWriter.print("服务端你好，我是Balla_兔子");
            printWriter.flush();
            socket.shutdownOutput();//关闭输出流
            
            InputStream inputStream=socket.getInputStream();//获取一个输入流，接收服务端的信息
            InputStreamReader inputStreamReader=new InputStreamReader(inputStream);//包装成字符流，提高效率
            BufferedReader bufferedReader=new BufferedReader(inputStreamReader);//缓冲区
            String info="";
            String temp=null;//临时变量
            while((temp=bufferedReader.readLine())!=null){
                info+=temp;
                System.out.println("客户端接收服务端发送信息："+info);
            }
            
            //关闭相对应的资源
            bufferedReader.close();
            inputStream.close();
            printWriter.close();
            outputStream.close();
            socket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

		//Utils.getIns().waitForShutdown();
	}
	
}