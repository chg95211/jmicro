package cn.jmicro.server;

public interface IServerListener {

	void serverStared(String ip,int port,String transport);
	
	//void serverStop(String ip,int port,String transport);
}
