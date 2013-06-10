package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.Map;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.rules.Rule;
import org.jivesoftware.openfire.plugin.rules.RuleGroupEventListener;
import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.jivesoftware.openfire.plugin.rules.RuleManagerProxy;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

public class PacketFilterPlugin implements Plugin, PacketInterceptor, PropertyEventListener {

	private static final Logger Log = LoggerFactory.getLogger(PacketFilterPlugin.class);

	private static PluginManager pluginManager;

	public PacketFilterPlugin() {
		interceptorManager = InterceptorManager.getInstance();
	}

	// Packet Filter
	private PacketFilter pf;

	// Hook for intercpetorn
	private InterceptorManager interceptorManager;

	private RuleGroupEventListener groupEvListener;

	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		// register with interceptor manager
		Log.info("Packet Filter loaded...");
		interceptorManager.addInterceptor(this);
		pluginManager = manager;
		pf = PacketFilter.getInstance();
		RuleManager ruleManager = new RuleManagerProxy();
		pf.setRuleManager(ruleManager);

		if (JiveGlobals.getBooleanProperty(PacketFilterConstants.Properties.AUTOCREATE_GROUP_RULES, true)) {
			groupEvListener = new RuleGroupEventListener();
			GroupEventDispatcher.addListener(groupEvListener);
		}
	}

	public void destroyPlugin() {
		// unregister with interceptor manager
		interceptorManager.removeInterceptor(this);
		GroupEventDispatcher.removeListener(groupEvListener);
	}

	public String getName() {
		return "packetFilter";

	}

	public static PluginManager getPluginManager() {
		return pluginManager;
	}

	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {

		if (processed) {
			return;
		}

		Rule rule = pf.findMatch(packet);

		if (rule != null) {
			rule.doAction(packet);
		}
	}

	@Override
	public void propertySet(String property, Map<String, Object> params) {
		if (property.equals(PacketFilterConstants.Properties.AUTOCREATE_GROUP_RULES)) {
			if (Boolean.parseBoolean((String) params.get("value"))) {
				GroupEventDispatcher.removeListener(groupEvListener);

				groupEvListener = new RuleGroupEventListener();
				GroupEventDispatcher.addListener(groupEvListener);
			} else {
				GroupEventDispatcher.removeListener(groupEvListener);
				groupEvListener = null;
			}
			
		}
	}

	@Override
	public void propertyDeleted(String property, Map<String, Object> params) {
		GroupEventDispatcher.removeListener(groupEvListener);

		groupEvListener = new RuleGroupEventListener();
		GroupEventDispatcher.addListener(groupEvListener);
	}

	@Override
	public void xmlPropertySet(String property, Map<String, Object> params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void xmlPropertyDeleted(String property, Map<String, Object> params) {
		// TODO Auto-generated method stub

	}

}
