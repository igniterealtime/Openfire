/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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