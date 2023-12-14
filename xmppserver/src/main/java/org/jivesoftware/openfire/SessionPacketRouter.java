/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire;

import org.dom4j.Element;
import org.jivesoftware.openfire.multiplex.UnknownStanzaException;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.*;

/**
 * Handles the routing of packets to a particular session. It will invoke all of the appropriate
 * interceptors, before and after having the server process the message.
 *
 * @author Alexander Wenckus
 */
public class SessionPacketRouter implements PacketRouter {

    protected LocalClientSession session;
    private PacketRouter router;
    private boolean skipJIDValidation = false;

    public SessionPacketRouter(LocalClientSession session) {
        this.session = session;
        router = XMPPServer.getInstance().getPacketRouter();
    }

    /**
     * Sets if TO addresses of Elements being routed should be validated. Doing stringprep operations
     * is very expensive and sometimes we already validated the TO address so there is no need to
     * validate again the address. For instance, when using Connection Managers the validation
     * is done by the Connection Manager so we can just trust the TO address. On the other hand,
     * the FROM address is set by the server so there is no need to validate it.<p>
     *
     * By default validation is enabled.
     *
     * @param skipJIDValidation true if validation of TO address is enabled.
     */
    public void setSkipJIDValidation(boolean skipJIDValidation) {
        this.skipJIDValidation = skipJIDValidation;
    }

    public void route(Element wrappedElement)
            throws UnknownStanzaException {
        String tag = wrappedElement.getName();
        if ("auth".equals(tag) || "response".equals(tag)) {
            SASLAuthentication.handle(session, wrappedElement);
        }
        else if ("iq".equals(tag)) {
            route(getIQ(wrappedElement));
        }
        else if ("message".equals(tag)) {
            route(new Message(wrappedElement, skipJIDValidation));
        }
        else if ("presence".equals(tag)) {
            route(new Presence(wrappedElement, skipJIDValidation));
        }
        else {
            throw new UnknownStanzaException();
        }
    }

    private IQ getIQ(Element doc) {
        Element query = doc.element("query");
        if (query != null && "jabber:iq:roster".equals(query.getNamespaceURI())) {
            return new Roster(doc);
        }
        else {
            return new IQ(doc, skipJIDValidation);
        }
    }

    @Override
    public void route(Packet packet) {
        // Security: Don't allow users to send packets on behalf of other users
        packet.setFrom(session.getAddress());
        if(packet instanceof IQ) {
            route((IQ)packet);
        }
        else if(packet instanceof Message) {
            route((Message)packet);
        }
        else if(packet instanceof Presence) {
            route((Presence)packet);
        }
    }

    @Override
    public void route(IQ packet) {
        packet.setFrom(session.getAddress());
        router.route(packet);
        session.incrementClientPacketCount();
    }

    @Override
    public void route(Message packet) {
        packet.setFrom(session.getAddress());
        router.route(packet);
        session.incrementClientPacketCount();
    }

    @Override
    public void route(Presence packet) {
        packet.setFrom(session.getAddress());
        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Determines if a peer that is sending a stanza in violation of RFC 6120, section 7.1:
     *
     * <blockquote>If, before completing the resource binding step, the client attempts to send an XML
     * stanza to an entity other than the server itself or the client's account, the server MUST NOT process the
     * stanza and MUST close the stream with a &lt;not-authorized/&gt; stream error.</blockquote>
     *
     * When this method returns 'true', the stream should be closed. This method does not close the stream.
     *
     * @param session The session over which the stanza is sent to Openfire.
     * @param stanza The stanza that is sent to Openfire.
     * @return true if the peer is in violation (and the stream should be closed), otherwise false.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6120#section-7.1">RFC 6120, section 7.1</a>
     * @see <a href="https://igniterealtime.atlassian.net/jira/software/c/projects/OF/issues/OF-2565">issue OF-2565</a>
     */
    public static boolean isInvalidStanzaSentPriorToResourceBinding(final Packet stanza, final ClientSession session)
    {
        // Openfire sets 'authenticated' only after resource binding.
        if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
            return false;
        }

        // Beware, the 'to' address in the stanza will have been overwritten by the
        final JID intendedRecipient = stanza.getTo();
        final JID serverDomain = new JID(XMPPServer.getInstance().getServerInfo().getXMPPDomain());

        // If there's no 'to' address, then the stanza is implicitly addressed at the user itself.
        if (intendedRecipient == null) {
            return false;
        }

        // TODO: after authentication (but prior to resource binding), it should be possible to verify that the
        // intended recipient's bare JID corresponds with the authorized user. Openfire currently does not have an API
        // that can be used to obtain the authorized username, prior to resource binding.

        if (intendedRecipient.equals(serverDomain)) {
            return false;
        }

        return true;
    }
}
