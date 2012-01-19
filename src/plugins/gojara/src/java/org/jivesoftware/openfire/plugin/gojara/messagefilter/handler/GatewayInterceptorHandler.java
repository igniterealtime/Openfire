package org.jivesoftware.openfire.plugin.gojara.messagefilter.handler;

import org.jivesoftware.openfire.plugin.gojara.messagefilter.interceptors.DiscoPackageInterceptorHandler;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.interceptors.StatisticPackageInterceptor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;

/**
 * 
 * This is the main handler for our gateways. It initializes all needed
 * interceptors with the component subdomain this handler is bind to You have to
 * start and stop this handler manually.
 * 
 * @author Holger Bergunde
 * 
 */
public class GatewayInterceptorHandler extends AbstractInterceptorHandler {
	public GatewayInterceptorHandler(String subdomain) {
		super(subdomain);
		DiscoPackageInterceptorHandler discoInterceptor = new DiscoPackageInterceptorHandler(subdomain);
		RemoteRosterInterceptor remoteRosterInterceptor = new RemoteRosterInterceptor(subdomain);
		StatisticPackageInterceptor statisticInterceptor = new StatisticPackageInterceptor(subdomain);
		addInterceptor(remoteRosterInterceptor);
		addInterceptor(discoInterceptor);
		addInterceptor(statisticInterceptor);
	}
}
