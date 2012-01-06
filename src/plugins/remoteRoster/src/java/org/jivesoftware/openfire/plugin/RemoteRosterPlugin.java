package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.component.ComponentEventListener;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.plugin.interceptor.AbstractInterceptorHandler;
import org.jivesoftware.openfire.plugin.interceptor.GatewayInterceptorHandler;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

public class RemoteRosterPlugin implements Plugin {

	private static final Logger Log = LoggerFactory.getLogger(RemoteRosterPlugin.class);
	final SessionManager _sessionManager = SessionManager.getInstance();
	private Map<String, AbstractInterceptorHandler> _interceptors = new HashMap<String, AbstractInterceptorHandler>();
	private static PluginManager pluginManager;
	private Set<String> _waitingForIQResponse = new HashSet<String>();

	private PropertyEventListener _settingsObserver;

	public RemoteRosterPlugin() {

	}

	public void initializePlugin(PluginManager manager, File pluginDirectory)
	{
		Log.debug("Starting RemoteRoster Plugin");
		pluginManager = manager;
		manageExternalComponents();
		listenToSettings();
		
		
	}

	private void manageExternalComponents()
	{
		InternalComponentManager compManager = InternalComponentManager.getInstance();
		compManager.addListener(new ComponentEventListener() {
			/**
			 * Check if the unregistered component contains to one of our
			 * package interceptors
			 */
			@Override
			public void componentUnregistered(JID componentJID)
			{	
				ComponentSession session = _sessionManager.getComponentSession(componentJID.getDomain());
				if (session != null && _interceptors.containsKey(session.getExternalComponent().getInitialSubdomain())) {
					String initialSubdomain = session.getExternalComponent().getInitialSubdomain();
					// Remove it from Map & ComponentManager
					removeInterceptor(initialSubdomain);
				}
			}

			/**
			 * If there is a new external Component, check if it is a gateway
			 * and add create a package interceptor if it is enabled
			 */
			@Override
			public void componentRegistered(JID componentJID)
			{
				_waitingForIQResponse.add(componentJID.getDomain());
			}

			@Override
			public void componentInfoReceived(IQ iq)
			{
				String from = iq.getFrom().getDomain();
				// Waiting for this external component sending an IQ response to
				// us?
				if (_waitingForIQResponse.contains(from)) {
					Element packet = iq.getChildElement();
					Document doc = packet.getDocument();
					List<Node> nodes = Utils.findNodesInDocument(doc, "//disco:identity[@category='gateway']");
					// Is this external component a gateway and there is no
					// package interceptor for it?
					if (nodes.size() > 0 && !_interceptors.containsKey(from)) {
						updateInterceptors(from);
					}

					// We got the IQ, we can now remove it from the set, because
					// we are not waiting any more
					_waitingForIQResponse.remove(from);
				}
			}
		});
	}

	private void listenToSettings()
	{
		_settingsObserver = new RemoteRosterPropertyListener() {
			@Override
			protected void changedProperty(String prop)
			{
				updateInterceptors(prop);
			}
		};
		PropertyEventDispatcher.addListener(_settingsObserver);
	}

	public void destroyPlugin()
	{
		for (String key : _interceptors.keySet()) {
			_interceptors.get(key).stop();
		}
		PropertyEventDispatcher.removeListener(_settingsObserver);
		pluginManager = null;
	}

	private void updateInterceptors(String componentJID)
	{
		boolean allowed = JiveGlobals.getBooleanProperty("plugin.remoteroster.jids." + componentJID, false);
		if (allowed) {
			if (!_interceptors.containsKey(componentJID)) {
				createNewPackageIntercetor(componentJID);
			}
		} else {
			if (_interceptors.containsKey(componentJID)) {
				removeInterceptor(componentJID);
			}
		}
	}

	public String getName()
	{
		return "remoteRoster";

	}

	public static PluginManager getPluginManager()
	{
		return pluginManager;
	}

	private void removeInterceptor(String initialSubdomain)
	{
		AbstractInterceptorHandler interceptor = _interceptors.get(initialSubdomain);
		if (interceptor != null) {
			_interceptors.remove(initialSubdomain);
			interceptor.stop();
		}
	}

	private void createNewPackageIntercetor(String initialSubdomain)
	{
		AbstractInterceptorHandler interceptor = new GatewayInterceptorHandler(initialSubdomain);
		_interceptors.put(initialSubdomain, interceptor);
		interceptor.start();
	}

}
