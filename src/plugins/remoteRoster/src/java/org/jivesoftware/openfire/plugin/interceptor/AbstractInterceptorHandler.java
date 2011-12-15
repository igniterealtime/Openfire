package org.jivesoftware.openfire.plugin.interceptor;

import java.util.HashSet;
import java.util.Set;

import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;

public abstract class AbstractInterceptorHandler {

	private String _subdomain;
	private boolean _isRunning = false;
	private Set<PacketInterceptor> _interceptors = new HashSet<PacketInterceptor>();
	private InterceptorManager _iManager;

	public AbstractInterceptorHandler(String subdomain) {
		_subdomain = subdomain;
		_iManager = InterceptorManager.getInstance();
	}

	protected boolean addInterceptor(PacketInterceptor interceptor)
	{
		if (_isRunning) {
			return false;
		}
		return _interceptors.add(interceptor);
	}

	protected boolean removeInterceptor(PacketInterceptor interceptor)
	{
		if (_isRunning) {
			return false;
		}
		return _interceptors.remove(interceptor);
	}

	public void start()
	{
		System.out.println("Start handling message interceptors for gateway " + _subdomain);
		_isRunning = true;
		for (PacketInterceptor interceptor : _interceptors) {
			_iManager.addInterceptor(interceptor);
		}
	}

	public void stop()
	{
		System.out.println("Stop handling message interceptors for gateway " + _subdomain);
		if (!_isRunning)
			return;
		_isRunning = false;
		for (PacketInterceptor interceptor : _interceptors) {
			_iManager.removeInterceptor(interceptor);
		}

	}

}
