package org.jmicro.api.monitor;

import org.jmicro.api.net.ISession;
import org.jmicro.common.CommonException;

public abstract class AbstractMonitorDataSubscriber implements IMonitorDataSubscriber {

	public static final Integer[] YTPES = ISession.STATIS_TYPES;
	
	@Override
	public Double getData(String srvKey,Integer type) {
		throw new CommonException("getData not support for type: "+type);
	}

}