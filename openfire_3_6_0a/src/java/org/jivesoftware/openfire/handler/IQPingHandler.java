package org.jivesoftware.openfire.handler;

import java.util.Collections;
import java.util.Iterator;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.xmpp.packet.IQ;

/**
 * Implements the XMPP Ping as defined by XEP-0199. This protocol offers an
 * alternative to the traditional 'white space ping' approach of determining the
 * availability of an entity. The XMPP Ping protocol allows pings to be
 * performed in a more XML-friendly approach, which can be used over more than
 * one hop in the communication path.
 * 
 * @author Guus der Kinderen
 * @see <a href="http://www.xmpp.org/extensions/xep-0199.html">XEP-0199:XMPP Ping</a>
 */
public class IQPingHandler extends IQHandler implements ServerFeaturesProvider {
	
	public static final String ELEMENT_NAME = "ping";

	public static final String NAMESPACE = "urn:xmpp:ping";
	
	private final IQHandlerInfo info;

	/**
	 * Constructs a new handler that will process XMPP Ping request.
	 */
	public IQPingHandler() {
		super("XMPP Server Ping Handler");
		info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);
	}

	/*
	 * @see
	 * org.jivesoftware.openfire.handler.IQHandler#handleIQ(org.xmpp.packet.IQ)
	 */
	@Override
	public IQ handleIQ(IQ packet) {
		return IQ.createResultIQ(packet);
	}

	/*
	 * @see org.jivesoftware.openfire.handler.IQHandler#getInfo()
	 */
	@Override
	public IQHandlerInfo getInfo() {
		return info;
	}

	/*
	 * @see org.jivesoftware.openfire.disco.ServerFeaturesProvider#getFeatures()
	 */
	public Iterator<String> getFeatures() {
		return Collections.singleton(NAMESPACE).iterator();
	}
}