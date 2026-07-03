/*
 * Copyright (C) 2024-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.jivesoftware.openfire.net.Bind2InlineHandler;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Bind2InlineHandler} that processes XEP-0198 Stream Management {@code <enable/>} elements
 * sent inline within a SASL2 Bind2 request (XEP-0388 / XEP-0386).
 *
 * <p>When a client includes an {@code <enable/>} element in the {@code urn:xmpp:sm:3} namespace
 * inside its Bind2 {@code <bind/>} element, this handler delegates to the session's
 * {@link StreamManager} to enable stream management (and optionally resumption) immediately
 * after resource binding, without requiring a separate round-trip.</p>
 *
 * <p>The {@code <enabled/>} response from the server is added as a child of the {@code <bound/>}
 * element in the SASL2 {@code <success/>} stanza.</p>
 *
 * @see <a href="https://xmpp.org/extensions/xep-0198.html">XEP-0198: Stream Management</a>
 * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
 */
public class Bind2StreamManagementHandler implements Bind2InlineHandler {

    private static final Logger Log = LoggerFactory.getLogger(Bind2StreamManagementHandler.class);

    @Override
    public String getNamespace() {
        return StreamManager.NAMESPACE_V3;
    }

    /**
     * Handles an {@code <enable/>} element from a Bind2 inline feature request by enabling
     * XEP-0198 stream management on the session. The {@code <enabled/>} response element
     * produced by the stream manager is added as a child of the provided {@code bound} element.
     *
     * <p>Only {@code <enable/>} elements are processed; any other element name is ignored.</p>
     *
     * @param session the client session on which stream management should be enabled
     * @param bound   the {@code <bound/>} element to which the {@code <enabled/>} response is added
     * @param element the inline element from the Bind2 request (expected to be {@code <enable/>})
     * @return {@code true} if the element was an {@code <enable/>} and was processed;
     *         {@code false} if the element was not an {@code <enable/>} or processing failed
     */
    @Override
    public boolean handleElement(LocalClientSession session, Element bound, Element element) {
        if (!"enable".equals(element.getName())) {
            Log.debug("Bind2StreamManagementHandler received unexpected element '{}'; ignoring.", element.getName());
            return false;
        }
        Log.debug("Processing inline SM <enable/> for session {}", session.getAddress());
        final String namespace = element.getNamespaceURI();
        final String resumeAttr = element.attributeValue("resume");
        final boolean resume = "true".equalsIgnoreCase(resumeAttr) || "1".equals(resumeAttr) || "yes".equalsIgnoreCase(resumeAttr);
        final Element enabled = session.getStreamManager().enableAndBuildElement(namespace, resume);
        if (enabled != null) {
            bound.add(enabled);
            return true;
        }
        return false;
    }
}
