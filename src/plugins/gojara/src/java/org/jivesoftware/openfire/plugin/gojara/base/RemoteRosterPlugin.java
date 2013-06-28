package org.jivesoftware.openfire.plugin.gojara.base;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.component.ComponentEventListener;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.MainInterceptor;
import org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager;
import org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * @author Holger Bergunde
 * @author axel.frederik.brand
 * 
 *         This class is the basic reprasentation for the GoJara plugin. It is
 *         the entry point for openfire to start or stop this plugin.
 * 
 *         GoJara has been developed to support XEP-xxx Remote Roster
 *         Management. Further information: <a
 *         href="http://jkaluza.fedorapeople.org/remote-roster.html">Here</a>
 * 
 *         RemoteRoster enables Spectrum IM support for Openfire. Currently only
 *         2.3, 2.4 and 2.5 implemented. 2.1 and 2.2 of the protocol standard is
 *         not supported by Spectrum IM
 */
public class RemoteRosterPlugin implements Plugin {

	private static final Logger Log = LoggerFactory.getLogger(RemoteRosterPlugin.class);
	private static PluginManager pluginManager;
	private Set<String> _waitingForIQResponse = new HashSet<String>();
	private PropertyEventListener _settingsObserver;
	private ComponentEventListener _componentObserver;
	private MainInterceptor mainInterceptor = new MainInterceptor();
	private InterceptorManager iManager = InterceptorManager.getInstance();
	private InternalComponentManager compManager = InternalComponentManager.getInstance();
	private TransportSessionManager transportSessionManager = TransportSessionManager.getInstance();
	private GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();
	
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		Log.info("Starting RemoteRoster Plugin");
		pluginManager = manager;
		iManager.addInterceptor(mainInterceptor);
		manageExternalComponents();
		listenToSettings();
		transportSessionManager.initializeSessions();
		gojaraAdminManager.gatherGatewayStatistics();
		Log.info("Started Gojara successfully. Currently running interceptors: "+iManager.getInterceptors().size());
	}

	/**
	 * Handles external components that connect to openfire. We check if the
	 * external component is maybe a gateway and interesting for us
	 */
	private void manageExternalComponents() {
		_componentObserver = new ComponentEventListener() {
			/*
			 * Check if the unregistered component contains to one of our
			 * package interceptors
			 */
			
			public void componentUnregistered(JID componentJID) {
					mainInterceptor.removeTransport(componentJID.toString());
			}

			/*
			 * If there is a new external Component, check if it is a gateway
			 * and add create a package interceptor if it is enabled
			 */
			
			public void componentRegistered(JID componentJID) {
				_waitingForIQResponse.add(componentJID.getDomain());
			}

			
			public void componentInfoReceived(IQ iq) {
				String from = iq.getFrom().getDomain();
				// Waiting for this external component sending an IQ response to
				// us?
				if (_waitingForIQResponse.contains(from)) {
					Element packet = iq.getChildElement();
					Document doc = packet.getDocument();
					List<Node> nodes = XpathHelper.findNodesInDocument(doc, "//disco:identity[@category='gateway']");
					if (nodes.size() > 0) {
						updateInterceptors(from);
					}

					// We got the IQ, we can now remove it from the set, because
					// we are not waiting any more
					_waitingForIQResponse.remove(from);
				}
			}
		};
		compManager.addListener(_componentObserver);
	}

	/**
	 * Registers a listener for JiveGlobals. We might restart our service, if
	 * there were some changes for our gateways
	 */
	private void listenToSettings() {
		_settingsObserver = new RemoteRosterPropertyListener() {
			@Override
			protected void changedProperty(String prop) {
				updateInterceptors(prop);
			}
		};
		PropertyEventDispatcher.addListener(_settingsObserver);
	}

	public void destroyPlugin() {
		Log.info("Destroying GoJara");
		mainInterceptor.freeze();
		iManager.removeInterceptor(mainInterceptor);
		PropertyEventDispatcher.removeListener(_settingsObserver);
		compManager.removeListener(_componentObserver);
		gojaraAdminManager.removeAdminUser();
		pluginManager = null;
		mainInterceptor = null;
		compManager = null;
	}

	private void updateInterceptors(String componentJID) {
		boolean allowed = JiveGlobals.getBooleanProperty("plugin.remoteroster.jids." + componentJID, false);
		if (allowed) {
			mainInterceptor.addTransport(componentJID);
		} else {
			mainInterceptor.removeTransport(componentJID);
		}
	}

	public String getName() {
		return "gojara";

	}

	public static PluginManager getPluginManager() {
		return pluginManager;
	}

}
