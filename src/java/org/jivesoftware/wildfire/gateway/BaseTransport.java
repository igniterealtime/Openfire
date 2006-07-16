/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.dom4j.Element;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Base class of all transport implementations.
 *
 * Handles all transport non-specific tasks and provides the glue that holds
 * together server interactions and the legacy service.  Does the bulk of
 * the XMPP related work.  Also note that this represents the transport
 * itself, not an individual session with the transport.
 *
 * @author Daniel Henninger
 */
public abstract class BaseTransport implements Component {

    /**
     * Create a new BaseTransport instance.
     *
     * @param jid JID associated with the transport.
     * @param description Description of the transport (for Disco).
     * @param type Type of the transport.
     */
    public BaseTransport(JID jid, String description, TransportType type) {
        this.jid = jid;
        this.description = description;
        this.transportType = type;
    }

    // TODO: Why do I need this?
    public BaseTransport() {
        // I don't understand why I need this.
    }

    /**
     * Manages all active sessions.
     * @see org.jivesoftware.wildfire.gateway.SessionManager
     */
    public final TransportSessionManager sessionManager = new TransportSessionManager();

    /**
     * Manages registration information.
     * @see org.jivesoftware.wildfire.gateway.RegistrationManager
     */
    public final RegistrationManager registrationManager = new RegistrationManager();

    /**
     * JID of the transport in question.
     */
    public JID jid = null;

    /**
     * Description of the transport in question.
     */
    public String description = null;

    /**
     * Component Manager associated with transport.
     */
    public ComponentManager componentManager = null;

    /**
     * Type of the transport in question.
     * @see org.jivesoftware.wildfire.gateway.TransportType
     */
    public TransportType transportType = null;

    private final String DISCO_INFO = "http://jabber.org/protocol/disco#info";
    private final String DISCO_ITEMS = "http://jabber.org/protocol/disco#items";
    private final String IQ_REGISTER = "jabber:iq:register";

    /**
     * Handles all incoming XMPP stanzas, passing them to individual
     * packet type handlers.
     *
     * @param packet The packet to be processed.
     */
    public void processPacket(Packet packet) {
        try {
            List<Packet> reply = new ArrayList<Packet>();
            if (packet instanceof IQ) {
                reply.addAll(processPacket((IQ)packet));
            }
            else if (packet instanceof Presence) {
                reply.addAll(processPacket((Presence)packet));
            }
            else if (packet instanceof Message) {
                reply.addAll(processPacket((Message)packet));
            }
            else {
                Log.info("Received an unhandled packet: " + packet.toString());
            }
        }
        catch (Exception e) {
            Log.error("Error occured while processing packet: " + e.toString());
        }
    }

    /**
     * Handles all incoming message stanzas.
     *
     * @param packet The message packet to be processed.
     */
    private List<Packet> processPacket(Message packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        try {
            TransportSession session = sessionManager.getSession(from);
            session.sendMessage(to, packet.getBody());
        }
        catch (NotFoundException e) {
            // TODO: Should return an error packet here
        }

        return reply;
    }

    /**
     * Handles all incoming presence stanzas.
     *
     * @param packet The presence packet to be processed.
     */
    private List<Packet> processPacket(Presence packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        if (packet.getType() == Presence.Type.error) {
            // We don't want to do anything with this.  Ignore it.
            return reply;
        }

        try {
            TransportSession session = sessionManager.getSession(from);
            if (to.getNode() == null) {
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (!registrations.iterator().hasNext()) {
                    // User is not registered with us.
                    return reply;
                }
                Registration registration = registrations.iterator().next();
    
                // This packet is to the transport itself.
                if (packet.getType() == null) {
                    // User has come online.
                    if (session == null) {
                        session = this.registrationLoggedIn(registration);
                        sessionManager.storeSession(registration.getJID(), session);
                    }
    
                    // TODO: This can also represent a status change.
                }
                else if (packet.getType() == Presence.Type.unavailable) {
                    // User has gone offline.
                    if (session != null && session.isLoggedIn()) {
                        this.registrationLoggedOut(session);
                    }
    
                    sessionManager.removeSession(registration.getJID());
                }
                else {
                    // Anything else we will ignore for now.
                }
            }
            else {
                // This packet is to a user at the transport.
                if (session == null) {
                    // We don't have a session, so stop here.
                    // TODO: maybe return an error?
                }
                else if (packet.getType() == Presence.Type.subscribe) {
                    // User wants to add someone to their legacy roster.
                    session.addContact(packet.getTo());
                }
                else if (packet.getType() == Presence.Type.unsubscribe) {
                    // User wants to remove someone from their legacy roster.
                    session.removeContact(packet.getTo());
                }
                else {
                    // Anything else we will ignore for now.
                }
            }
        }
        catch (NotFoundException e) {
            // We don't care, we account for this later.
        }

        return reply;
    }

    /**
     * Handles all incoming iq stanzas.
     *
     * @param packet The iq packet to be processed.
     */
    private List<Packet> processPacket(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.error) {
            // Lets not start a loop.  Ignore.
            return reply;
        }

        Element child = packet.getChildElement();
        String xmlns = null;
        if (child != null) {
            xmlns = child.getNamespaceURI();
        }

        if (xmlns == null) {
            // No namespace defined.
            // TODO: Should we return an error?
            return reply;
        }

        if (xmlns.equals(DISCO_INFO)) {
            reply.add(handleDiscoInfo(packet));
        }
        else if (xmlns.equals(DISCO_ITEMS)) {
            reply.add(handleDiscoItems(packet));
        }
        else if (xmlns.equals(IQ_REGISTER)) {
            reply.add(handleIQRegister(packet));
        }

        return reply;
    }

    /**
     * Handle service discovery info request.
     *
     * @param packet An IQ packet in the disco info namespace.
     * @return An IQ packet to be returned to the user.
     */
    private IQ handleDiscoInfo(IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        return reply;
    }

    /**
     * Handle service discovery items request.
     *
     * @param packet An IQ packet in the disco items namespace.
     * @return An IQ packet to be returned to the user.
     */
    private IQ handleDiscoItems(IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        return reply;
    }

    /**
     * Handle registration request.
     *
     * @param packet An IQ packet in the iq registration namespace.
     * @return An IQ packet to be returned to the user.
     */
    private IQ handleIQRegister(IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        return reply;
    }

    /**
     * Converts a legacy username to a JID.
     *
     * @param username Username to be converted to a JID.
     * @return The legacy username as a JID.
     */
    public JID convertIDToJID(String username) {
        return new JID(username, this.jid.getDomain(), null);
    }

    /**
     * Handles initialization of the transport.
     */
    public void initialize(JID jid, ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    /**
     * Handles startup of the transport.
     */
    public void start() {
        // Do nothing.
    }

    /**
     * Handles shutdown of the transport.
     *
     * Cleans up all active sessions.
     */
    public void shutdown() {
        // TODO: actually make this function
    }

    /**
     * Returns the name(jid) of the transport.
     */
    public String getName() {
        return transportType.toString();
    }

    /**
     * Returns the description of the transport.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the component manager of the transport.
     */
    public ComponentManager getComponentManager() {
        return componentManager;
    }

    /**
     * Sends a packet through the component manager as the component.
     *
     * @param packet Packet to be sent.
     */
    public void sendPacket(Packet packet) {
        try {
            this.componentManager.sendPacket(this, packet);
        }
        catch (ComponentException e) {
            Log.error("Failed to deliver packet: " + packet.toString());
        }
    }

    /**
     * Will handle logging in to the legacy service.
     *
     * @param registration Registration used for log in.
     * @return A session instance for the new login.
     */
    public abstract TransportSession registrationLoggedIn(Registration registration);

    /**
     * Will handle logging out of the legacy service.
     *
     * @param session TransportSession to be logged out.
     */
    public abstract void registrationLoggedOut(TransportSession session);

}
