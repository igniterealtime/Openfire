/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.sip.log;

import org.dom4j.Element;
import org.jivesoftware.util.Log;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 *
 * Component that process CallLogExtension packets
 *
 * @author Thiago Rocha Camargo
 */
public class LogComponent implements Component{

	ComponentManager componentManager = null;
	private LogListener logListener = null;

	/**
	 * Namespace of the packet extension.
	 */
	public static final String NAMESPACE = "http://www.jivesoftware.com/protocol/log";

	public static final String PROPNAME = "plugin.logger.serviceName";

	public static final String NAME = "logger";

	public LogComponent(LogListener logListener){
		this.componentManager = logListener.getComponentManager();
		this.logListener = logListener;
	}

	public void initialize(JID jid, ComponentManager componentManager) {
	}

	public void start() {
	}

	public void shutdown() {
	}

	// Component Interface

	public void processPacket(Packet packet) {
		if (packet instanceof IQ) {
			// Handle disco packets
			IQ iq = (IQ) packet;
			// Ignore IQs of type ERROR or RESULT
			if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
				return;
			}
			processIQ(iq);
		}
	}

	private void processIQ(IQ iq) {
		IQ reply = IQ.createResultIQ(iq);
		Element childElement = iq.getChildElement();
		String namespace = childElement.getNamespaceURI();
		Element childElementCopy = iq.getChildElement().createCopy();
		reply.setChildElement(childElementCopy);

		if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
			if (iq.getTo().getNode() == null) {
				// Return service identity and features
				Element identity = childElementCopy.addElement("identity");
				identity.addAttribute("category", "component");
				identity.addAttribute("type", "generic");
				identity.addAttribute("name", "Remote Logger");
				childElementCopy.addElement("feature").addAttribute("var",
						"http://jabber.org/protocol/disco#info");
				childElementCopy.addElement("feature").addAttribute("var",
						NAMESPACE);
			}
		} else if (NAMESPACE.equals(namespace)) {
			if (iq.getTo().getNode() == null && iq.getFrom() != null) {

				reply = logListener.logReceived(reply);
				//reply.setTo(reply.getFrom());

			}else{
				reply.getChildElement().addAttribute("type","unregistered");

			}
		}
		try {
			componentManager.sendPacket(this, reply);
		} catch (Exception e) {
			Log.error(e);
		}

	} // Other Methods

	public String getDescription() {
		// Get the description from the plugin.xml file.
		return "Remote Logger";
	}


	public String getName() {
		// Get the name from the plugin.xml file.
		return NAME;
	}

}

