package cn.jmicro.api.service;

import java.io.File;
import java.util.List;

import cn.jmicro.api.config.Config;
import cn.jmicro.api.raft.IDataOperator;
import cn.jmicro.common.CommonException;
import cn.jmicro.common.Constants;
import cn.jmicro.common.Utils;
import cn.jmicro.common.util.StringUtils;

public class DefaultServiceInstanceNameProvider implements IServiceInstanceNameGenerator {

	private static final String TAG = Constants.INSTANCE_NAME;
	private static final String LOCK_FILE = "lock.tmp";
	
	@Override
	public String getInstanceName(IDataOperator dataOperator,Config config) {
		
		String dataDir = Config.getCommandParam(Constants.LOCAL_DATA_DIR);
		
		if(StringUtils.isEmpty(dataDir)) {
			throw new CommonException(dataDir + " cannot be NULL");
		}
		
		String insName = null;
		
		File ud = null;
		File dir = new File(dataDir);
		
		if(!dir.exists()) {
			dir.mkdirs();
		}
		
		//优先在本地目录中寻找
		for(File f : dir.listFiles()) {
			if(!f.isDirectory()) {
				continue;
			}
			
			/*File lockFile = new File(f.getAbsolutePath(),LOCK_FILE);
			if(lockFile.exists()) {
				//被同一台机上的不同实例使用
				continue;
			}*/
			
			String path = Config.InstanceDir + "/" + f.getName();
			if(!dataOperator.exist(path)) {
				doTag(dataOperator,f,path);
				insName = f.getName();
				break;
			}
		}
		
		if(insName == null) {
			//实例名前缀，默认前缀是instanceName，
			String tag = config.getString(TAG,TAG);
			for(int i = 0; i < Integer.MAX_VALUE ; i++) {
				String name = tag + i;
				ud = new File(dir,name);
				String path = Config.InstanceDir + "/" + name;
				if(!ud.exists() && !dataOperator.exist(path)) {
					doTag(dataOperator,ud.getAbsoluteFile(),path);
					insName = name;
					break;
				}
			}
		}
		
		if(StringUtils.isEmpty(insName)) {
			throw new CommonException("Fail to get instance name");
		}
		
		return insName;
	}
	
	private void doTag(IDataOperator dataOperator,File dir,String path) {
		if(!dir.exists()) {
			dir.mkdirs();
		}
		/*File lf = new File(dir,LOCK_FILE);
		try {
			lf.createNewFile();
		} catch (IOException e) {
			throw new CommonException(lf.getAbsolutePath(),e);
		}
		lf.deleteOnExit();*/
		//本地存在，ZK中不存在,也就是没有虽的机器在使用此目录
		dataOperator.createNodeOrSetData(path, "", true);
	}

}
