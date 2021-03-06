package cn.jmicro.api.mng;

import java.util.List;
import java.util.Map;

import cn.jmicro.api.Resp;
import cn.jmicro.api.monitor.MCConfig;
import cn.jmicro.codegenerator.AsyncClientProxy;

@AsyncClientProxy
public interface IMonitorTypeService {

	////////////////////GROBAL CONFIG////////////////////////////////
	Resp<List<MCConfig>> getAllConfigs();
	
	Resp<Void> update(MCConfig mc);
	
	Resp<Void> delete(short type);
	
	Resp<Void> add(MCConfig mc);
	
	//////////////////////FOR MONITOR///////////////////////////////
	Resp<List<Short>> getConfigByMonitorKey(String key);
	
	//Resp<Void> add2Monitor(String key,Short[] type);
	
	//Resp<Void> removeFromMonitor(String key,Short[] type);
	
	Resp<Void> updateMonitorTypes(String key,Short[] adds,Short[] dels);
	
	Resp<Map<String,String>> getMonitorKeyList();
	
	
	//////////////////////FOR SERVICE METHOD MONITOR///////////////////////////////
	Resp<List<Short>> getConfigByServiceMethodKey(String key);
	
	Resp<Void> updateServiceMethodMonitorTypes(String key,Short[] adds,Short[] dels);
	
	Resp<List<MCConfig>> getAllConfigsByGroup(String[] groups);
	
	////////////////////////Named types ///////////////////////////////////
	Resp<Void> addNamedTypes(String name);
	
	Resp<List<Short>> getTypesByNamed(String name);
	
	Resp<Void> updateNamedTypes(String name, Short[] adds, Short[] dels);
	
	Resp<List<String>> getNamedList();
}
