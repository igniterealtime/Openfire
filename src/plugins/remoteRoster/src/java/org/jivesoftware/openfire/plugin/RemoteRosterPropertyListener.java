package org.jivesoftware.openfire.plugin;

import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.PropertyEventListener;

public abstract class RemoteRosterPropertyListener implements PropertyEventListener {

	@Override
	public void xmlPropertySet(String property, Map<String, Object> params)
	{
	}

	@Override
	public void xmlPropertyDeleted(String property, Map<String, Object> params)
	{
	}

	@Override
	public void propertySet(String property, Map<String, Object> params)
	{
		if (property.contains("plugin.remoteroster.jids.")) {
			changedProperty(property.replace("plugin.remoteroster.jids.", ""));
		}
	}

	@Override
	public void propertyDeleted(String property, Map<String, Object> params)
	{
		String hostname = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
		property += "." + hostname;
		changedProperty(property.replace("plugin.remoteroster.jids.", ""));
	}

	protected abstract void changedProperty(String prop);

}
