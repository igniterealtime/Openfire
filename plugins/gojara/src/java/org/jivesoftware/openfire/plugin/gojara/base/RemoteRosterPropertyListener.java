package org.jivesoftware.openfire.plugin.gojara.base;

import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.PropertyEventListener;

/**
 * @author Holger Bergunde
 * 
 *         This class implements the @see PropertyEventListener It monitors the
 *         JiveGlobals to check if there are made changes.
 * 
 */
public abstract class RemoteRosterPropertyListener implements PropertyEventListener {

	public void xmlPropertySet(String property, Map<String, Object> params) {
	}

	public void xmlPropertyDeleted(String property, Map<String, Object> params) {
	}

	public void propertySet(String property, Map<String, Object> params) {
		if (property.contains("plugin.remoteroster.jids.")) {
			changedProperty(property.replace("plugin.remoteroster.jids.", ""));
		}
	}

	public void propertyDeleted(String property, Map<String, Object> params) {
		String hostname = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
		property += "." + hostname;
		changedProperty(property.replace("plugin.remoteroster.jids.", ""));
	}

	/**
	 * 
	 * If there were changes we are interested in, this template method get
	 * called. The property string is truncated. Method gets triggered, if the
	 * property contains substring "plugin.remoteroster.jids."
	 * 
	 * @param prop
	 *            substring of changes property
	 */
	protected abstract void changedProperty(String prop);

}
