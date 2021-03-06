package cn.jmicro.example.rpc.impl;

import java.util.Random;

import cn.jmicro.api.JMicroContext;
import cn.jmicro.api.annotation.Component;
import cn.jmicro.api.annotation.Reference;
import cn.jmicro.api.annotation.SBreakingRule;
import cn.jmicro.api.annotation.SMethod;
import cn.jmicro.api.annotation.Service;
import cn.jmicro.api.async.IPromise;
import cn.jmicro.api.monitor.MC;
import cn.jmicro.api.monitor.SF;
import cn.jmicro.api.service.IServiceAsyncResponse;
import cn.jmicro.api.test.Person;
import cn.jmicro.common.Constants;
import cn.jmicro.example.api.rpc.ISimpleRpc;
import cn.jmicro.example.api.rpc.genclient.IRpcA$JMAsyncClient;

@Service(namespace="simpleRpc", version="0.0.1", monitorEnable=1, maxSpeed=-1,debugMode=1,
baseTimeUnit=Constants.TIME_SECONDS, clientId=1000,external=true)
@Component
public class SimpleRpcImpl implements ISimpleRpc {

	@Reference(namespace="rpca", version="0.0.1")
	private IRpcA$JMAsyncClient rpca;
	
	private Random r = new Random(100);
	
	@Override
	@SMethod(
			//breakingRule="1S 50% 500MS",
			//1秒钟内异常超50%，熔断服务，熔断后每80毫秒做一次测试
			breakingRule = @SBreakingRule(enable=true,percent=50,checkInterval=5000),
			logLevel=MC.LOG_DEBUG,	
			testingArgs="[\"test args\"]",//测试参数
			monitorEnable=1,
			timeWindow=5*60000,//统计时间窗口5分钟
			slotInterval=100,
			checkInterval=5000,//采样周期2S
			timeout=5000,
			retryInterval=1000,
			debugMode=1,
			maxSpeed=1000,
			baseTimeUnit=Constants.TIME_MILLISECONDS
	)
	public String hello(String name) {
		if(SF.isLoggable(MC.LOG_DEBUG)) {
			SF.eventLog(MC.MT_PLATFORM_LOG,MC.LOG_DEBUG,SimpleRpcImpl.class, name);
		}
		/*int rv = r.nextInt();
		if(rv < 50) {
			throw new CommonException("test breaker exception");
		}*/
		System.out.println("Server hello: " +name);
		return "Server say hello to: "+name;
	}
	
	@Override
	@SMethod(
			//breakingRule="1S 50% 500MS",
			//1秒钟内异常超50%，熔断服务，熔断后每80毫秒做一次测试
			breakingRule = @SBreakingRule(enable=true,percent=50,checkInterval=5000),
			logLevel=MC.LOG_DEBUG,
			testingArgs="[\"test args\"]",//测试参数
			monitorEnable=1,
			timeWindow=5*60000,//统计时间窗口5分钟
			slotInterval=100,
			checkInterval=5000,//采样周期2S
			timeout=5000,
			retryInterval=1000,
			debugMode=0,
			maxSpeed=1000,
			baseTimeUnit=Constants.TIME_MILLISECONDS
	)
	public String hi(Person person) {
		if(SF.isLoggable(MC.LOG_DEBUG)) {
			SF.eventLog(MC.MT_PLATFORM_LOG,MC.LOG_DEBUG,SimpleRpcImpl.class, person.getUsername());
		}
		return "Server say hello to: " + person.toString();
	}

	@Override
	public String linkRpc(String msg) {
		if(SF.isLoggable(MC.LOG_DEBUG)) {
			SF.eventLog(MC.MT_APP_LOG,MC.LOG_DEBUG,SimpleRpcImpl.class, "linkRpc call IRpcA with: " + msg);
		}
		
		System.out.println("linkRpc: " + msg);
		//return this.rpca.invokeRpcA(msg+" linkRpc => invokeRpcA");
		
		//IPromise<String> p = PromiseUtils.callService(this.rpca, "invokeRpcA","linkRpc call IRpcA with: " + msg);
		
		IPromise<String> p = this.rpca.invokeRpcAJMAsync("invokeRpcA");
		JMicroContext cxt = JMicroContext.get();
		if(cxt.isAsync()) {
			IServiceAsyncResponse cb = cxt.getParam(Constants.CONTEXT_SERVICE_RESPONSE,null);
			p.then((rst,fail) -> {
				cb.result(rst);
			});
			return null;
		} else {
			return p.getResult();
		}
		
	}

	
}
