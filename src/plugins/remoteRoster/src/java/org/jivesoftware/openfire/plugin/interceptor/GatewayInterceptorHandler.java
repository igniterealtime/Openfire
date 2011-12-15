package org.jivesoftware.openfire.plugin.interceptor;

public class GatewayInterceptorHandler extends AbstractInterceptorHandler {
	public GatewayInterceptorHandler(String subdomain) {
		super(subdomain);
		DiscoPackageInterceptor discoInterceptor = new DiscoPackageInterceptor(subdomain);
		RemotePackageInterceptor remoteRosterInterceptor = new RemotePackageInterceptor(subdomain); 
		addInterceptor(remoteRosterInterceptor);
		addInterceptor(discoInterceptor);
	}
}
