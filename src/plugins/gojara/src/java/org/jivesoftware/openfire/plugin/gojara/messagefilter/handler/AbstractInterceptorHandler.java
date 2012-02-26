package org.jivesoftware.openfire.plugin.gojara.messagefilter.handler;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;

/**
 * 
 * A gateway interceptor should extend this class. It supports the child class
 * handling their interceptors.
 * 
 * @author Holger Bergunde
 * 
 */
public abstract class AbstractInterceptorHandler {

	private static Logger Log = Logger.getLogger(AbstractInterceptorHandler.class);
	private String _subdomain;
	private boolean _isRunning = false;
	private Set<PacketInterceptor> _interceptors = new HashSet<PacketInterceptor>();
	private InterceptorManager _iManager;

	public AbstractInterceptorHandler(String subdomain) {
		_subdomain = subdomain;
		_iManager = InterceptorManager.getInstance();
	}

	/**
	 * Add a interceptor to the let it handled by the abstract implementation
	 * The handle must not be started. You have to stop() until you could add
	 * new interceptors.
	 * 
	 * @param interceptor
	 * @return true if it successfully added, otherwise false
	 */
	protected boolean addInterceptor(PacketInterceptor interceptor) {
		if (_isRunning) {
			return false;
		}
		return _interceptors.add(interceptor);
	}

	/**
	 * Remove a interceptor from abstract implementation The handle must not be
	 * started. You have to stop() until you could remove interceptors.
	 * 
	 * @param interceptor
	 * @return true if it successfully added, otherwise false
	 */
	protected boolean removeInterceptor(PacketInterceptor interceptor) {
		if (_isRunning) {
			return false;
		}
		return _interceptors.remove(interceptor);
	}

	/**
	 * Start handling the added interceptors. 
	 * If it is started you could not remove or add interceptors
	 */
	public void start() {
		Log.debug("Start handling message interceptors for gateway " + _subdomain);
		_isRunning = true;
		for (PacketInterceptor interceptor : _interceptors) {
			_iManager.addInterceptor(interceptor);
		}
	}

	/**
	 * Stop handling the added interceptors. 
	 */
	public void stop() {
		Log.debug("Stop handling message interceptors for gateway " + _subdomain);
		if (!_isRunning)
			return;
		_isRunning = false;
		for (PacketInterceptor interceptor : _interceptors) {
			_iManager.removeInterceptor(interceptor);
		}

	}

}
