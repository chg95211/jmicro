package org.jmicro.mng.impl;

import java.util.HashMap;
import java.util.Map;

import org.jmicro.api.annotation.Component;
import org.jmicro.api.i18n.II18N;
import org.jmicro.api.monitor.v1.MonitorConstant;

@Component
public class TypeII18NImpl implements II18N {
	
	@Override
	public String key() {
		return "en";
	}

	@Override
	public Map<String, String> values() {
		Map<String,String> data = new HashMap<>();
		for(Short t : MonitorConstant.MONITOR_VAL_2_KEY.keySet()) {
			data.put("statis.index."+t, MonitorConstant.MONITOR_VAL_2_KEY.get(t));
		}
		return data;
	}

	
}
