package com.reucon.openfire.plugin.archive.xep0136;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

/**
 * Encapsulates support for <a
 * href="http://www.xmpp.org/extensions/xep-0136.html">XEP-0136</a>.
 */
public class Xep0136Support {
	private static final String NAMESPACE_AUTO = "urn:xmpp:archive:auto";

	final XMPPServer server;
	final Map<String, IQHandler> element2Handlers;
	final IQHandler iqDispatcher;
	final Collection<IQHandler> iqHandlers;

	public Xep0136Support(XMPPServer server) {
		this.server = server;
		this.element2Handlers = Collections
				.synchronizedMap(new HashMap<String, IQHandler>());
		this.iqDispatcher = new AbstractIQHandler("XEP-0136 IQ Dispatcher",
				null) {
			public IQ handleIQ(IQ packet) throws UnauthorizedException {
				if (!MonitoringPlugin.getInstance().isEnabled()) {
					return error(packet,
							PacketError.Condition.feature_not_implemented);
				}

				final IQHandler iqHandler = element2Handlers.get(packet
						.getChildElement().getName());
				if (iqHandler != null) {
					return iqHandler.handleIQ(packet);
				} else {
					return error(packet,
							PacketError.Condition.feature_not_implemented);
				}
			}
		};

		iqHandlers = new ArrayList<IQHandler>();

		// support for #ns-pref
		// iqHandlers.add(new IQPrefHandler());

		// support for #ns-manage
		iqHandlers.add(new IQListHandler());
		iqHandlers.add(new IQRetrieveHandler());
		// iqHandlers.add(new IQRemoveHandler());
	}

	public void start() {
		for (IQHandler iqHandler : iqHandlers) {
			try {
				iqHandler.initialize(server);
				iqHandler.start();
			} catch (Exception e) {
				Log.error("Unable to initialize and start "
						+ iqHandler.getClass());
				continue;
			}

			element2Handlers.put(iqHandler.getInfo().getName(), iqHandler);
			if (iqHandler instanceof ServerFeaturesProvider) {
				for (Iterator<String> i = ((ServerFeaturesProvider) iqHandler)
						.getFeatures(); i.hasNext();) {
					server.getIQDiscoInfoHandler().addServerFeature(i.next());
				}
			}
		}
		server.getIQDiscoInfoHandler().addServerFeature(NAMESPACE_AUTO);
		server.getIQRouter().addHandler(iqDispatcher);
	}

	public void stop() {
		IQRouter iqRouter = server.getIQRouter();
		IQDiscoInfoHandler iqDiscoInfoHandler = server.getIQDiscoInfoHandler();

		for (IQHandler iqHandler : iqHandlers) {
			element2Handlers.remove(iqHandler.getInfo().getName());
			try {
				iqHandler.stop();
				iqHandler.destroy();
			} catch (Exception e) {
				Log.warn("Unable to stop and destroy " + iqHandler.getClass());
			}

			if (iqHandler instanceof ServerFeaturesProvider) {
				for (Iterator<String> i = ((ServerFeaturesProvider) iqHandler)
						.getFeatures(); i.hasNext();) {
					if (iqDiscoInfoHandler != null) {
						iqDiscoInfoHandler.removeServerFeature(i.next());
					}
				}
			}
		}
		if (iqRouter != null) {
			iqRouter.removeHandler(iqDispatcher);
		}
	}
}
