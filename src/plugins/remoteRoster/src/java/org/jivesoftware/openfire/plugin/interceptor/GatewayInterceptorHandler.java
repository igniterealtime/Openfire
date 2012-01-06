package org.jivesoftware.openfire.plugin.interceptor;

public class GatewayInterceptorHandler extends AbstractInterceptorHandler {
	public GatewayInterceptorHandler(String subdomain) {
		super(subdomain);
		DiscoPackageInterceptorHandler discoInterceptor = new DiscoPackageInterceptorHandler(subdomain);
		RemotePackageInterceptor remoteRosterInterceptor = new RemotePackageInterceptor(subdomain); 
		StatisticPackageInterceptor statisticInterceptor = new StatisticPackageInterceptor(subdomain);
		addInterceptor(remoteRosterInterceptor);
		addInterceptor(discoInterceptor);
		addInterceptor(statisticInterceptor);
	}
}
