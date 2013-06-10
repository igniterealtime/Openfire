package org.jivesoftware.openfire.plugin;

import org.jivesoftware.util.LocaleUtils;

/*
 Static util methods.
 */
public class PacketFilterUtil {

	/*
	 * Method to get the component part from a jid. The packet could be from the component itself so just return.
	 */
	public static String getDomain(String jid) {
		if (jid.contains("@")) {
			int atIndex = jid.indexOf("@");
			return (jid.substring(atIndex + 1, jid.length()));
		} else {
			return jid;
		}
	}

	public static String formatRuleSourceDest(String sourceDest) {
		String result = sourceDest;
		if (sourceDest != null && PacketFilterConstants.ANY_GROUP.equals(sourceDest)) {
			try {
				result = LocaleUtils.getPluginResourceBundle("packetFilter").getString("pf.anygroup");
			} catch (Exception e) {
				result = "Any Group";
			}
		}
		return result;
	}
	
}
