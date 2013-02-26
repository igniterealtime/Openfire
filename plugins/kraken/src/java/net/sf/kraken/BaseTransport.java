/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken;

import net.sf.kraken.avatars.Avatar;
import net.sf.kraken.muc.BaseMUCTransport;
import net.sf.kraken.permissions.PermissionManager;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.registration.RegistrationHandler;
import net.sf.kraken.registration.RegistrationManager;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.session.TransportSessionManager;
import net.sf.kraken.session.cluster.TransportSessionRouter;
import net.sf.kraken.type.*;
import net.sf.kraken.util.chatstate.ChatStateChangeEvent;
import net.sf.kraken.util.chatstate.ChatStateEventListener;
import net.sf.kraken.util.chatstate.ChatStateEventSource;

import org.apache.log4j.Logger;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.RemotePacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.*;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardListener;
import org.jivesoftware.openfire.vcard.VCardEventDispatcher;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.*;
import org.xmpp.packet.PacketError.Condition;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.Lock;

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
public abstract class BaseTransport<B extends TransportBuddy> implements Component, RosterEventListener, UserEventListener, PacketInterceptor, SessionEventListener, VCardListener, ChatStateEventListener {

    static Logger Log = Logger.getLogger(BaseTransport.class);

    private final ChatStateEventSource chatStateEventSource = new ChatStateEventSource(this);

    /**
     * Create a new BaseTransport instance.
     */
    public BaseTransport() {
        // We've got nothing to do here.
    }

    /**
     * Set up the transport instance.
     *
     * @param type Type of the transport.
     * @param description Description of the transport (for Disco).
     * @param sessionRouter The session router.
     */
    public void setup(TransportType type, String description, TransportSessionRouter sessionRouter) {
        // Allow XMPP setting to be overridden.
        // TODO: Make this more generic.
        if (type.equals(TransportType.xmpp) && JiveGlobals.getProperty("plugin.gateway.xmpp.overridename") != null) {
            description = JiveGlobals.getProperty("plugin.gateway.xmpp.overridename");
        }
        this.description = description;
        this.transportType = type;
        this.sessionRouter = sessionRouter;
        permissionManager = new PermissionManager(transportType);
    }

    /**
     * Handles initialization of the transport.
     */
    public void initialize(JID jid, ComponentManager componentManager) {
        this.jid = jid;
        this.componentManager = componentManager;
        rosterManager = XMPPServer.getInstance().getRosterManager();
    }

    /**
     * Manages all active sessions.
     * @see net.sf.kraken.session.TransportSessionManager
     */
    public final TransportSessionManager<B> sessionManager = new TransportSessionManager<B>(this);

    /**
     * Manages permission information.
     * @see net.sf.kraken.permissions.PermissionManager
     */
    public PermissionManager permissionManager = null;

    /**
     * Manages session routing.
     * @see net.sf.kraken.session.cluster.TransportSessionRouter
     */
    public TransportSessionRouter sessionRouter = null;

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
     * Manager component for user rosters.
     */
    public RosterManager rosterManager;

    /**
     * MUC transport handler we are associated with.  null means no handler.
     */
    public BaseMUCTransport<B> mucTransport;

    /**
     * Type of the transport in question.
     * @see net.sf.kraken.type.TransportType
     */
    public TransportType transportType = null;

    /**
     * Handles all incoming XMPP stanzas, passing them to individual
     * packet type handlers.
     *
     * @param packet The packet to be processed.
     */
    public void processPacket(Packet packet) {
        JID from = packet.getFrom();
        JID to = packet.getTo();
        if (to == null) {
            // Well clearly it's for us or it wouldn't have gotten here...
            packet.setTo(getJID());
            to = getJID();
        }
        
        if (from != null && to != null) {
            Lock l = CacheFactory.getLock(from.toBareJID()+"@"+transportType.toString()+"pp", sessionRouter.sessionLocations);
            try {
                l.lock();
                byte[] targetNodeID = sessionRouter.getSession(transportType.toString(), from.toBareJID());
                if (targetNodeID != null && !Arrays.equals(targetNodeID, XMPPServer.getInstance().getNodeID().toByteArray())) {
                    RemotePacketRouter router = XMPPServer.getInstance().getRoutingTable().getRemotePacketRouter();
                    if (router != null) {
                        // Not for our node, send it elsewhere.
                        router.routePacket(targetNodeID, to, packet);
                        return;
                    }
                }
            }
            finally {
                l.unlock();
            }
        }
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
                Log.debug("Received an unhandled packet: " + packet.toString());
            }

            if (reply.size() > 0) {
                for (Packet p : reply) {
                    this.sendPacket(p);
                }
            }
        }
        catch (Exception e) {
            Log.warn("Error occured while processing packet:", e);
        }
    }

    /**
     * Handles all incoming message stanzas.
     *
     * @param packet The message packet to be processed.
     * @return list of packets that will be sent back to the message sender.
     */
    private List<Packet> processPacket(Message packet) {
        Log.debug("Received message packet: "+packet.toXML());
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();
        if (to == null) {
            // Well clearly it's for us or it wouldn't have gotten here...
            packet.setTo(getJID());
            to = getJID();
        }

        try {
            TransportSession<B> session = sessionManager.getSession(from);
            if (!session.isLoggedIn()) {
                Message m = new Message();
                m.setError(Condition.service_unavailable);
                m.setTo(from);
                m.setFrom(getJID());
                m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "kraken", Arrays.asList(transportType.toString().toUpperCase())));
                reply.add(m);
            }
            else if (to.getNode() == null) {
                // Message to gateway itself.
                if (packet.getBody() != null) {
                    Message m = new Message();
                    m.setTo(from);
                    m.setFrom(getJID());
                    m.setBody(LocaleUtils.getLocalizedString("gateway.base.msgtotransport", "kraken"));
                    reply.add(m);

                }
            }
            else {
                if (packet.getBody() != null) {
                    if (packet.getChildElement("buzz", NameSpace.SPARKNS) != null) {
                        session.sendBuzzNotification(to, packet.getBody());
                    }
                    else if (packet.getChildElement("attention", NameSpace.ATTENTIONNS) != null) {
                        session.sendBuzzNotification(to, packet.getBody());
                    }
                    else {
                        session.sendMessage(to, packet.getBody());
                    }
                }
                else {
                    // Checking for XEP-0022 message events
                    Element eEvent = packet.getChildElement("x", NameSpace.XEVENT);
                    if (eEvent != null && JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
                        if (eEvent.element("composing") != null) {
                            session.sendChatState(to, ChatStateType.composing);
                        }
                        else {
                            session.sendChatState(to, ChatStateType.paused);
                        }
                    }
                    else {
                        // Ok then, lets check for XEP-0085 chat states
                        if (packet.getChildElement("composing", NameSpace.CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.composing);
                        }
                        else if (packet.getChildElement("active", NameSpace.CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.active);
                        }
                        else if (packet.getChildElement("inactive", NameSpace.CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.inactive);
                        }
                        else if (packet.getChildElement("paused", NameSpace.CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.paused);
                        }
                        else if (packet.getChildElement("gone", NameSpace.CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.gone);
                        }
                    }
                    if (packet.getChildElement("buzz", NameSpace.SPARKNS) != null) {
                        session.sendBuzzNotification(to, null);
                    }
                }
            }
        }
        catch (NotFoundException e) {
            Log.debug("Unable to find session.");
            Message m = new Message();
            m.setError(Condition.service_unavailable);
            m.setTo(from);
            m.setFrom(getJID());
            m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "kraken", Arrays.asList(transportType.toString().toUpperCase())));
            reply.add(m);
        }

        return reply;
    }

    /**
     * Handles all incoming presence stanzas.
     *
     * @param packet The presence packet to be processed.
     * @return list of packets that will be sent back to the presence requester.
     */
    private List<Packet> processPacket(Presence packet) {
        Log.debug("Received presence packet: "+packet.toXML());
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();
        if (to == null) {
            // Well clearly it's for us or it wouldn't have gotten here...
            packet.setTo(getJID());
            to = getJID();
        }

        if (packet.getType() == Presence.Type.error) {
            // We don't want to do anything with this.  Ignore it.
            return reply;
        }

        try {
            if (to.getNode() == null) {
                Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations(from, this.transportType);
                if (registrations.isEmpty()) {
                    // User is not registered with us.
                    Log.debug("Unable to find registration.");
                    Presence p = new Presence();
                    p.setTo(from);
                    p.setFrom(to);
                    p.setError(PacketError.Condition.forbidden);
                    p.setType(Presence.Type.unavailable);
                    reply.add(p);
                    return reply;
                }
                if (JiveGlobals.getBooleanProperty("plugin.gateway."+getType()+".registrationstrict", false) && !permissionManager.hasAccess(from)) {
                    Log.debug("Attempt to log in by restricted account: "+from);
                    Presence p = new Presence();
                    p.setTo(from);
                    p.setFrom(to);
                    p.setError(PacketError.Condition.forbidden);
                    p.setType(Presence.Type.unavailable);
                    reply.add(p);
                    return reply;
                }
                Registration registration = registrations.iterator().next();

                // This packet is to the transport itself.
                if (packet.getType() == null) {
                    // A user's resource has come online.
                    TransportSession<B> session;
                    Lock l = CacheFactory.getLock(registration.getJID()+"@"+transportType.toString()+"ns", sessionRouter.sessionLocations);
                    try {
                        l.lock();
                        session = sessionManager.getSession(from);

                        if (session.hasResource(from.getResource())) {
                            Log.debug("An existing resource has changed status: " + from);

                            if (session.getPriority(from.getResource()) != packet.getPriority()) {
                                session.updatePriority(from.getResource(), packet.getPriority());
                            }
                            if (session.isHighestPriority(from.getResource())) {
                                // Well, this could represent a status change.
                                session.updateStatus(getPresenceType(packet), packet.getStatus());
                            }
                        }
                        else {
                            Log.debug("A new resource has come online: " + from);

                            // This is a new resource, lets send them what we know.
                            session.addResource(from.getResource(), packet.getPriority());
                            // Tell the new resource what the state of their buddy list is.
                            session.getBuddyManager().sendAllAvailablePresences(from);
                            // If this priority is the highest, treat it's status as golden
                            if (session.isHighestPriority(from.getResource())) {
                                session.updateStatus(getPresenceType(packet), packet.getStatus());
                            }
                        }
                        // Attach the session
                        session.attachSession();
                    }
                    catch (NotFoundException e) {
                        Log.debug("A new session has come online: " + from);

                        session = this.registrationLoggedIn(registration, from, getPresenceType(packet), packet.getStatus(), packet.getPriority());
                        sessionManager.storeSession(from, session);
                    }
                    finally {
                        l.unlock();
                    }
                }
                else if (packet.getType() == Presence.Type.unavailable) {
                    // A user's resource has gone offline.
                    TransportSession<B> session;
                    try {
                        session = sessionManager.getSession(from);
                        String resource = from.getResource();
                        if (session.hasResource(resource)) {
                            if (session.getResourceCount() > 1) {

                                // Just one of the resources, lets adjust accordingly.
                                if (session.isHighestPriority(resource)) {
                                    Log.debug("A high priority resource (of multiple) has gone offline: " + from);

                                    // Ooh, the highest resource went offline, drop to next highest.
                                    session.removeResource(resource);

                                    // Lets ask the next highest resource what it's presence is.
                                    Presence p = new Presence(Presence.Type.probe);
                                    p.setTo(session.getJIDWithHighestPriority());
                                    p.setFrom(this.getJID());
                                    sendPacket(p);
                                }
                                else {
                                    Log.debug("A low priority resource (of multiple) has gone offline: " + from);

                                    // Meh, lower priority, big whoop.
                                    session.removeResource(resource);
                                }
                            }
                            else {
                                Log.debug("A final resource has gone offline: " + from);

                                // No more resources, byebye.
                                this.registrationLoggedOut(session);

                                sessionManager.removeSession(from);
                            }
                        }
                    }
                    catch (NotFoundException e) {
                        Log.debug("Ignoring unavailable presence for inactive seession.");
                    }
                }
                else if (packet.getType() == Presence.Type.probe) {
                    // Client is asking for presence status.
                    TransportSession<B> session;
                    try {
                        session = sessionManager.getSession(from);
                        session.sendPresence(from);
                    }
                    catch (NotFoundException e) {
                        Log.debug("Ignoring probe presence for inactive session.");
                    }
                }
                else {
                    Log.debug("Ignoring this packet:" + packet.toString());
                    // Anything else we will ignore for now.
                }
            }
            else {
                // This packet is to a user at the transport.
                try {
                    TransportSession<B> session = sessionManager.getSession(from);

                    if (packet.getType() == Presence.Type.probe) {
                        // Presence probe, lets try to answer appropriately.
                        if (session.isLoggedIn()) {
                            try {
                                TransportBuddy buddy = session.getBuddyManager().getBuddy(to);
                                buddy.sendPresence(from);
                            }
                            catch (NotFoundException e) {
                                // User was not found so send an error presence
                                Presence p = new Presence();
                                p.setTo(from);
                                p.setFrom(to);
                                // TODO: this causes some ugliness in some clients
//                                p.setError(PacketError.Condition.forbidden);
                                // If the user tries to check on a buddy before we are totally logged in
                                // and have the full list, this gets thrown for legit contacts.
                                // We'll send unavailable for now.
                                p.setType(Presence.Type.unavailable);
                                sendPacket(p);
                            }
                        }
                    }
                    else if (packet.getType() == Presence.Type.subscribe) {
                        // For the time being, we are going to lie to the end user that the subscription has worked.
                        Presence p = new Presence();
                        p.setType(Presence.Type.subscribed);
                        p.setTo(from);
                        p.setFrom(to);
                        sendPacket(p);
                    }
                    else if (packet.getType() == Presence.Type.unsubscribe) {
                        // For the time being, we are going to lie to the end user that the unsubscription has worked.
                        Presence p = new Presence();
                        p.setType(Presence.Type.unsubscribed);
                        p.setTo(from);
                        p.setFrom(to);
                        sendPacket(p);
                    }
                    else if (packet.getType() == Presence.Type.subscribed) {
                        // let the legacy domain know that the contact was accepted.
                        session.acceptAddContact(packet.getTo());
                    }
                    else {
                        // Anything else we will ignore for now.
                    }
                } catch (NotFoundException e) {
                    // Well we just don't care then.
                    Log.debug("User not found while processing "
                            + "presence stanza: " + packet.toXML(), e);
                }
            }
        }
        catch (Exception e) {
            Log.debug("Exception while processing packet: ", e);
        }

        return reply;
    }

    /**
     * Handles all incoming iq stanzas.
     *
     * @param packet The iq packet to be processed.
     * @return list of packets that will be sent back to the IQ requester.
     */
    private List<Packet> processPacket(IQ packet) {
        Log.debug("Received iq packet: "+packet.toXML());                
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.error) {
            // Lets not start a loop.  Ignore.
            return reply;
        }

        String xmlns = null;
        Element child = (packet).getChildElement();
        if (child != null) {
            xmlns = child.getNamespaceURI();
        }

        if (xmlns == null) {
            // No namespace defined.
            Log.debug("No XMLNS:" + packet.toString());
            IQ error = IQ.createResultIQ(packet);
            error.setError(Condition.bad_request);
            reply.add(error);
            return reply;
        }

        if (xmlns.equals(NameSpace.DISCO_INFO)) {
            reply.addAll(handleDiscoInfo(packet));
        }
        else if (xmlns.equals(NameSpace.DISCO_ITEMS)) {
            reply.addAll(handleDiscoItems(packet));
        }
        else if (xmlns.equals(NameSpace.IQ_GATEWAY)) {
            reply.addAll(handleIQGateway(packet));
        }
        else if (xmlns.equals(NameSpace.IQ_REGISTER)) {
            // TODO if all handling is going to be offloaded to
            // ChannelHandler-like constructs, the exception handling
            // could/should be made more generic.
            try {
                // note that this handler does not make use of the reply-queue.
                // Instead, it sends packets directly.
                new RegistrationHandler(this).process(packet);
            } catch (UnauthorizedException ex) {
                final IQ result = IQ.createResultIQ(packet);
                result.setError(Condition.forbidden);
                reply.add(result);
                final Message em = new Message();
                em.setType(Message.Type.error);
                em.setTo(packet.getFrom());
                em.setFrom(packet.getTo());
                em.setBody(ex.getMessage());
                reply.add(em);
            }
        }
        else if (xmlns.equals(NameSpace.IQ_VERSION)) {
            reply.addAll(handleIQVersion(packet));
        }
        else if (xmlns.equals(NameSpace.VCARD_TEMP) && child.getName().equals("vCard")) {
            reply.addAll(handleVCardTemp(packet));
        }
        else if (xmlns.equals(NameSpace.IQ_ROSTER)) {
            // No reason to 'argue' about this one.  Return success.
            reply.add(IQ.createResultIQ(packet));
        }
        else if (xmlns.equals(NameSpace.IQ_LAST)) { 
            reply.addAll(handleIQLast(packet));
        }
        else {
            Log.debug("Unable to handle iq request: " + xmlns);
            IQ error = IQ.createResultIQ(packet);
            error.setError(Condition.service_unavailable);
            reply.add(error);
        }

        return reply;
    }

    /**
     * Handle service discovery info request.
     *
     * @param packet An IQ packet in the disco info namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleDiscoInfo(IQ packet) {
        // TODO: why return a list? we're sure to return always exactly one result.
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        IQ result = IQ.createResultIQ(packet);
        
        if (packet.getTo().getNode() == null) {
            // Requested info from transport itself.
            if (from.getNode() == null || RegistrationManager.getInstance().isRegistered(from, this.transportType) || permissionManager.hasAccess(from)) {
                Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_INFO));
                response.addElement("identity")
                        .addAttribute("category", "gateway")
                        .addAttribute("type", this.transportType.discoIdentity())
                        .addAttribute("name", this.description);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.DISCO_INFO);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.DISCO_ITEMS);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.IQ_GATEWAY);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.IQ_REGISTER);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.IQ_VERSION);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.IQ_LAST);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.VCARD_TEMP);
                if (RegistrationManager.getInstance().isRegistered(from, this.transportType)) {
                    response.addElement("feature")
                            .addAttribute("var", NameSpace.IQ_REGISTERED);
                }
                result.setChildElement(response);
            }
            else {
                result.setError(Condition.forbidden);
            }
        } else {
            // Requested info from a gateway user.
            
            final TransportSession<B> session;
            try {
                session = sessionManager.getSession(packet.getFrom());
                if ((from.getNode() == null || permissionManager.hasAccess(from)) && session != null) {
                    final Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_INFO));
                    response.addElement("identity")
                        .addAttribute("category", "client")
                        .addAttribute("type", "pc");
                    response.addElement("feature")
                        .addAttribute("var", NameSpace.DISCO_INFO);
                    
                    for(final SupportedFeature feature : session.supportedFeatures) {
                        response.addElement("feature")
                            .addAttribute("var", feature.getVar());
                    }
                    result.setChildElement(response);
                } else {
                    result.setError(Condition.forbidden);
                }
            } catch (NotFoundException ex) {
                result.setError(Condition.item_not_found);
            }
        }
        reply.add(result);
        return reply;
    }

    /**
     * Handle service discovery items request.
     *
     * @param packet An IQ packet in the disco items namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleDiscoItems(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        IQ result = IQ.createResultIQ(packet);
        Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_ITEMS));
        result.setChildElement(response);
        reply.add(result);
        return reply;
    }

    /**
     * Handle gateway translation service request.
     *
     * @param packet An IQ packet in the iq gateway namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQGateway(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.get) {
            IQ result = IQ.createResultIQ(packet);
            Element query = DocumentHelper.createElement(QName.get("query", NameSpace.IQ_GATEWAY));
            query.addElement("desc").addText(LocaleUtils.getLocalizedString("gateway.base.enterusername", "kraken", Arrays.asList(transportType.toString().toUpperCase())));
            query.addElement("prompt");
            result.setChildElement(query);
            reply.add(result);
        }
        else if (packet.getType() == IQ.Type.set) {
            IQ result = IQ.createResultIQ(packet);
            String prompt = null;
            Element promptEl = packet.getChildElement().element("prompt");
            if (promptEl != null) {
                prompt = promptEl.getTextTrim();
            }
            if (prompt == null) {
                result.setError(Condition.bad_request);
            }
            else {
                JID jid = this.convertIDToJID(prompt);
                Element query = DocumentHelper.createElement(QName.get("query", NameSpace.IQ_GATEWAY));
                // This is what Psi expects
                query.addElement("prompt").addText(jid.toString());
                // This is JEP complient
                query.addElement("jid").addText(jid.toString());
                result.setChildElement(query);
            }
            reply.add(result);
        }

        return reply;
    }

    /**
     * Handle vcard request.
     *
     * @param packet An IQ packet in the vcard-temp namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleVCardTemp(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        if (packet.getType() == IQ.Type.get) {
            IQ result = IQ.createResultIQ(packet);
            if (from.getNode() != null) {
                try {
                    TransportSession<B> session = sessionManager.getSession(from);
                    Element vcard = session.getBuddyManager().getBuddy(to).getVCard();
                    result.setChildElement(vcard);
                }
                catch (NotFoundException e) {
                    Log.debug("Contact not found while retrieving vcard for: "+from);
                    result.setError(Condition.item_not_found);
                }
            }
            else {
                result.setError(Condition.feature_not_implemented);
            }
            reply.add(result);
        }
        else if (packet.getType() == IQ.Type.set) {
            IQ result = IQ.createResultIQ(packet);
            result.setError(Condition.forbidden);
            reply.add(result);
        }

        return reply;
    }

    /**
     * Handle last request.
     *
     * @param packet An IQ packet in the jabber:iq:last namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQLast(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        if (packet.getType() == IQ.Type.get) {
            IQ result = IQ.createResultIQ(packet);
            if (from.getNode() != null) {
                try {
                    TransportSession<B> session = sessionManager.getSession(from);
                    Element response = DocumentHelper.createElement(QName.get("query", NameSpace.IQ_LAST)); 
                    Long timestamp = session.getBuddyManager().getBuddy(to).getLastActivityTimestamp();
                    String lastevent = session.getBuddyManager().getBuddy(to).getLastActivityEvent();
                    response.addAttribute("seconds", new Long(new Date().getTime() - timestamp).toString());
                    if (lastevent != null) {
                        response.addCDATA(lastevent);
                    }
                    result.setChildElement(response);
                }
                catch (NotFoundException e) {
                    Log.debug("Contact not found while retrieving last activity for: "+from);
                    result.setError(Condition.item_not_found);
                }
            }
            else {
                result.setError(Condition.feature_not_implemented);
            }
            reply.add(result);
        }
        else if (packet.getType() == IQ.Type.set) {
            IQ result = IQ.createResultIQ(packet);
            result.setError(Condition.forbidden);
            reply.add(result);
        }

        return reply;
    }


    /**
     * Handle version request.
     *
     * @param packet An IQ packet in the iq version namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQVersion(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.get) {
            IQ result = IQ.createResultIQ(packet);
            Element query = DocumentHelper.createElement(QName.get("query", NameSpace.IQ_VERSION));
            query.addElement("name").addText("Openfire " + this.getDescription());
            query.addElement("version").addText(XMPPServer.getInstance().getServerInfo().getVersion().getVersionString() + " - " + this.getVersionString());
            query.addElement("os").addText(System.getProperty("os.name"));
            result.setChildElement(query);
            reply.add(result);
        }

        return reply;
    }

    /**
     * Converts a legacy username to a JID.
     *
     * @param username Username to be converted to a JID.
     * @return The legacy username as a JID.
     */
    public JID convertIDToJID(String username) {
        if (username.indexOf("/") > -1) {
            username = username.substring(0, username.indexOf("/"));
        }
        if (JiveGlobals.getBooleanProperty("plugin.gateway.tweak.percenthack", false)) {
            return new JID(username.replace('@', '%').replace(" ", ""), this.jid.getDomain(), null);
        }
        else {
            return new JID(JID.escapeNode(username.replace(" ", "")), this.jid.getDomain(), null);
        }
    }

    /**
     * Converts a JID to a legacy username.
     *
     * @param jid JID to be converted to a legacy username.
     * @return THe legacy username as a String.
     */
    public String convertJIDToID(JID jid) {
        if (JiveGlobals.getBooleanProperty("plugin.gateway.tweak.percenthack", false)) {
            return jid.getNode().replace('%', '@');
        }
        else {
            return JID.unescapeNode(jid.getNode());
        }
    }

    /**
     * Sets up a presence packet according to a presenceType setting.
     *
     * @param packet packet to set up.
     * @param presenceType presence type to set up.
     */
    public void setUpPresencePacket(Presence packet, PresenceType presenceType) {
        if (presenceType.equals(PresenceType.away)) {
            packet.setShow(Presence.Show.away);
        }
        else if (presenceType.equals(PresenceType.xa)) {
            packet.setShow(Presence.Show.xa);
        }
        else if (presenceType.equals(PresenceType.dnd)) {
            packet.setShow(Presence.Show.dnd);
        }
        else if (presenceType.equals(PresenceType.chat)) {
            packet.setShow(Presence.Show.chat);
        }
        else if (presenceType.equals(PresenceType.unavailable)) {
            packet.setType(Presence.Type.unavailable);
        }
        else if (presenceType.equals(PresenceType.unknown)) {
            // We consider this unavailable since we don't know what it is.
            packet.setType(Presence.Type.unavailable);
        }
    }

    /**
     * Gets an easy to use presence type from a presence packet.
     *
     * @param packet A presence packet from which the type will be pulled.
     * @return the presence type of the specified packet.
     */
    public PresenceType getPresenceType(Presence packet) {
        Presence.Type ptype = packet.getType();
        Presence.Show stype = packet.getShow();

        if (stype == Presence.Show.chat) {
            return PresenceType.chat;
        }
        else if (stype == Presence.Show.away) {
            return PresenceType.away;
        }
        else if (stype == Presence.Show.xa) {
            return PresenceType.xa;
        }
        else if (stype == Presence.Show.dnd) {
            return PresenceType.dnd;
        }
        else if (ptype == Presence.Type.unavailable) {
            return PresenceType.unavailable;
        }
        else if (packet.isAvailable()) {
            return PresenceType.available;
        }
        else {
            return PresenceType.unknown;
        }
    }

    /**
     * Handles startup of the transport.
     */
    public void start() {
        RosterEventDispatcher.addListener(this);
        UserEventDispatcher.addListener(this);
        SessionEventDispatcher.addListener(this);
        VCardEventDispatcher.addListener(this);
        InterceptorManager.getInstance().addInterceptor(this);
        if (!JiveGlobals.getBooleanProperty("plugin.gateway.tweak.noprobeonstart", false)) {
            // Probe all registered users [if they are logged in] to auto-log them in
            // TODO: Do we need to account for local vs other node sessions?
            for (ClientSession session : SessionManager.getInstance().getSessions()) {
                try {
                    JID jid = XMPPServer.getInstance().createJID(session.getUsername(), null);
                    if (RegistrationManager.getInstance().isRegistered(jid, getType())) {
                        Presence p = new Presence(Presence.Type.probe);
                        p.setFrom(this.getJID());
                        p.setTo(jid);
                        sendPacket(p);
                    }
                }
                catch (UserNotFoundException e) {
                    // Not a valid user for the gateway then
                }
            }
        }
    }

    /**
     * Handles shutdown of the transport.
     *
     * Cleans up all active sessions.
     */
    public void shutdown() {
        InterceptorManager.getInstance().removeInterceptor(this);
        VCardEventDispatcher.removeListener(this);
        SessionEventDispatcher.removeListener(this);
        RosterEventDispatcher.removeListener(this);
        UserEventDispatcher.removeListener(this);
        // Disconnect everyone's session
        for (TransportSession<B> session : sessionManager.getSessions()) {
            registrationLoggedOut(session);
            session.removeAllResources();
        }
        sessionManager.shutdown();
    }

    /**
     * Returns the jid of the transport.
     *
     * @return the jid of the transport.
     */
    public JID getJID() {
        return this.jid;
    }

    /**
     * Returns the roster manager for the transport.
     *
     * @return the roster manager for the transport.
     */
    public RosterManager getRosterManager() {
        return this.rosterManager;
    }

    /**
     * @return the name (type) of the transport.
     */
    public String getName() {
        return transportType.toString();
    }

    /**
     * @return the MUC transport we are associated with.
     */
    public BaseMUCTransport<B> getMUCTransport() {
        return mucTransport;
    }

    /**
     * @return the type of the transport.
     */
    public TransportType getType() {
        return transportType;
    }

    /**
     * @return the description of the transport.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the component manager of the transport.
     */
    public ComponentManager getComponentManager() {
        return componentManager;
    }

    /**
     * @return the session manager of the transport.
     */
    public TransportSessionManager<B> getSessionManager() {
        return sessionManager;
    }

    /**
     * @return the session router of the transport.
     */
    public TransportSessionRouter getSessionRouter() {
        return sessionRouter;
    }

    /**
     * @return the chat state managing object of the transport
     */
    public ChatStateEventSource getChatStateEventSource() {
        return chatStateEventSource;
    }

    /**
     * Retains the version string for later requests.
     */
    private String versionString = null;

    /**
     * @return the version string of the gateway.
     */
    public String getVersionString() {
        if (versionString == null) {
            PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
            versionString = pluginManager.getVersion(pluginManager.getPlugin("kraken"));
        }
        return versionString;
    }

    /**
     * Either updates or adds a JID to a user's roster.
     *
     * Tries to only edit the roster if it has to.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactjid JID to add to roster.
     * @param nickname Nickname of item. (can be null)
     * @param groups List of group the item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, Collection<String> groups) throws UserNotFoundException {
        // SubTypes "FROM" and "BOTH" should be avoided for legacy user contacts. Instead, use "TO". The gateway
        // component itself does have a "BOTH" subscription. This cuts down on needless traffic: the gateway component
        // will broadcast presence changes to every legacy contact - there's no need for the local domain to 
        // address each and every legacy contact independently.
        RosterItem.SubType subType = (contactjid.getNode() == null ? RosterItem.SUB_BOTH : RosterItem.SUB_TO);
        RosterItem.AskType askType = RosterItem.ASK_NONE;
        addOrUpdateRosterItem(userjid, contactjid, nickname, groups, subType, askType);
    }

    /**
     * Either updates or adds a JID to a user's roster.
     *
     * Tries to only edit the roster if it has to.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactjid JID to add to roster.
     * @param nickname Nickname of item. (can be null)
     * @param groups List of group the item is to be placed in. (can be null)
     * @param subtype Specific subscription setting.
     * @param asktype Specific ask setting.
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, Collection<String> groups, RosterItem.SubType subtype, RosterItem.AskType asktype) throws UserNotFoundException {
        Log.debug("add or update roster item " + contactjid + " for: "
                + userjid);
        try {
            final Roster roster = rosterManager.getRoster(userjid.getNode());
            try {
                RosterItem gwitem = roster.getRosterItem(contactjid);
                Log.debug("Found existing roster item " + contactjid + " for: "
                        + userjid + ". We will update if required.");

                boolean changed = false;
                if (gwitem.getSubStatus() != subtype) {
                    gwitem.setSubStatus(subtype);
                    changed = true;
                }
                if (gwitem.getAskStatus() != asktype) {
                    gwitem.setAskStatus(asktype);
                    changed = true;
                }
                // This could probably be simplified, for now I'm going with brute force logic.
                // gnickname is null, nickname is null, leave
                // gnickname is not null, nickname is null, set gnickname to null
                // gnickname is null, nickname is not null, set gnickname to nickname
                // gnickname is not null, nickname is not null, if different, set gnickname to nickname
                if (    (gwitem.getNickname() != null && nickname == null) ||
                        (gwitem.getNickname() == null && nickname != null) ||
                        (gwitem.getNickname() != null && nickname != null && !gwitem.getNickname().equals(nickname))) {
                    gwitem.setNickname(nickname);
                    changed = true;
                }
                List<String> curgroups = gwitem.getGroups();
                // This could probably be simplified, for now I'm going with brute force logic.
                // curgroups is null, groups is null, leave
                // curgroups is not null and has entries, groups is null or empty, set curgroups to empty
                // curgroups is null or empty, groups is not null and has entries, set curgroups to groups
                // curgroups is not null, groups is not null, if their sizes are different or curgroups does not contain all of groups, set curgroups to groups
                if (    ((curgroups != null && curgroups.size() > 0) && (groups == null || groups.size() == 0)) ||
                        ((curgroups == null || curgroups.size() == 0) && (groups != null && groups.size() > 0)) ||
                        (curgroups != null && groups != null && ((curgroups.size() != groups.size()) || !curgroups.containsAll(groups)))) {
                    try {
                        gwitem.setGroups((List<String>)(groups != null ? groups : new ArrayList<String>()));
                        changed = true;
                    }
                    catch (Exception ee) {
                        Log.debug("Exception while setting groups for roster item:", ee);
                    }
                }
                if (changed) {
                    Log.debug("Updating existing roster item " + contactjid + " for: "
                            + userjid);
                    roster.updateRosterItem(gwitem);
                } else {
                    Log.debug("Update of existing roster item " + contactjid + " for: "
                            + userjid + " can be skipped - nothing changed.");
                }
            }
            catch (UserNotFoundException e) {
                try {
                    // Create new roster item for the gateway service or legacy contact. Only
                    // roster items related to the gateway service will be persistent. Roster
                    // items of legacy users are never persisted in the DB.  (unless tweak enabled)
                    Log.debug("Creating new roster item " + contactjid + " for: "
                            + userjid + ". No existing item was found.");
                    final RosterItem gwitem =
                            roster.createRosterItem(contactjid, false, contactjid.getNode() == null || JiveGlobals.getBooleanProperty("plugin.gateway.tweak.persistentroster", false));
                    gwitem.setSubStatus(subtype);
                    gwitem.setAskStatus(asktype);
                    gwitem.setNickname(nickname);
                    try {
                        gwitem.setGroups((List<String>)groups);
                    }
                    catch (Exception ee) {
                        Log.debug("Exception while setting groups for gateway item:", ee);
                    }
                    roster.updateRosterItem(gwitem);
                }
                catch (UserAlreadyExistsException ee) {
                    Log.debug("getRosterItem claims user exists, but couldn't find via getRosterItem?", ee);
                }
                catch (Exception ee) {
                    Log.debug("Exception while creating roster item:", ee);
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
    }

    /**
     * Either updates or adds a JID to a user's roster.
     *
     * Tries to only edit the roster if it has to.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactjid JID to add to roster.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, String group) throws UserNotFoundException {
        ArrayList<String> groups = null;
        if (group != null) {
            groups = new ArrayList<String>();
            groups.add(group);
        }
        addOrUpdateRosterItem(userjid, contactjid, nickname, groups);
    }

    /**
     * Either updates or adds a JID to a user's roster.
     *
     * Tries to only edit the roster if it has to.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactjid JID to add to roster.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @param subtype Specific subscription setting.
     * @param asktype Specific ask setting.
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, String group, RosterItem.SubType subtype, RosterItem.AskType asktype) throws UserNotFoundException {
        ArrayList<String> groups = null;
        if (group != null) {
            groups = new ArrayList<String>();
            groups.add(group);
        }
        addOrUpdateRosterItem(userjid, contactjid, nickname, groups, subtype, asktype);
    }

    /**
     * Either updates or adds a contact to a user's roster.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactid String contact name, will be translated to JID.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, String contactid, String nickname, String group) throws UserNotFoundException {
        addOrUpdateRosterItem(userjid, convertIDToJID(contactid), nickname, group);
    }

    /**
     * Either updates or adds a contact to a user's roster.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactid String contact name, will be translated to JID.
     * @param nickname Nickname of item. (can be null)
     * @param groups Group items is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, String contactid, String nickname, Collection<String> groups) throws UserNotFoundException {
        addOrUpdateRosterItem(userjid, convertIDToJID(contactid), nickname, groups);
    }

    /**
     * Either updates or adds a contact to a user's roster.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactid String contact name, will be translated to JID.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @param subtype Specific subscription setting.
     * @param asktype Specific ask setting.
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, String contactid, String nickname, String group, RosterItem.SubType subtype, RosterItem.AskType asktype) throws UserNotFoundException {
        addOrUpdateRosterItem(userjid, convertIDToJID(contactid), nickname, group, subtype, asktype);
    }

    /**
     * Either updates or adds a contact to a user's roster.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactid String contact name, will be translated to JID.
     * @param nickname Nickname of item. (can be null)
     * @param groups Group items is to be placed in. (can be null)
     * @param subtype Specific subscription setting.
     * @param asktype Specific ask setting.
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, String contactid, String nickname, Collection<String> groups, RosterItem.SubType subtype, RosterItem.AskType asktype) throws UserNotFoundException {
        addOrUpdateRosterItem(userjid, convertIDToJID(contactid), nickname, groups, subtype, asktype);
    }

    /**
     * Removes a roster item from a user's roster.
     *
     * @param userjid JID of user whose roster we will interact with.
     * @param contactjid JID to be removed from roster.
     * @throws UserNotFoundException if userjid not found.
     */
    public void removeFromRoster(JID userjid, JID contactjid) throws UserNotFoundException {
        // Clean up the user's contact list.
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());
            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().toBareJID().equals(contactjid.toBareJID())) {
                    try {
                        roster.deleteRosterItem(ri.getJid(), false);
                    }
                    catch (Exception e) {
                        Log.debug("Error removing roster item: " + ri.toString(), e);
                    }
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
    }

    /**
     * Removes a roster item from a user's roster based on a legacy contact.
     *
     * @param userjid JID of user whose roster we will interact with.
     * @param contactid Contact to be removed, will be translated to JID.
     * @throws UserNotFoundException if userjid not found.
     */
    void removeFromRoster(JID userjid, String contactid) throws UserNotFoundException {
        // Clean up the user's contact list.
        removeFromRoster(userjid, convertIDToJID(contactid));
    }

    /**
     * Sync a user's roster with their legacy contact list.
     *
     * Given a collection of transport buddies, syncs up the user's
     * roster by fixing any nicknames, group assignments, adding and removing
     * roster items, and generally trying to make the jabber roster list
     * assigned to the transport's JID look at much like the legacy buddy
     * list as possible.  This is a very extensive operation.  You do not
     * want to do this very often.  Typically once right after the person
     * has logged into the legacy service.
     *
     * @param userjid JID of user who's roster we are syncing with.
     * @param legacyitems List of TransportBuddy's to be synced.
     * @throws UserNotFoundException if userjid not found.
     */
    public void syncLegacyRoster(JID userjid, Collection<B> legacyitems) throws UserNotFoundException {
        Log.debug("Syncing Legacy Roster: "+legacyitems);
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());

            // Lets lock down the roster from update notifications if there's an active session.
            try {
                TransportSession<B> session = sessionManager.getSession(userjid.getNode());
                session.lockRoster();
            }
            catch (NotFoundException e) {
                // No active session?  Then no problem.
            }

            // First thing first, we want to build ourselves an easy mapping.
            Map<JID,TransportBuddy> legacymap = new HashMap<JID, TransportBuddy>();
            for (TransportBuddy buddy : legacyitems) {
//                Log.debug("ROSTERSYNC: Mapping "+buddy.getName());
                legacymap.put(buddy.getJID(), buddy);
            }

            // Now, lets go through the roster and see what matches up.
            for (RosterItem ri : roster.getRosterItems()) {
                if (!ri.getJid().getDomain().equals(this.jid.getDomain())) {
                    // Not our contact to care about.
                    continue;
                }
                if (ri.getJid().getNode() == null) {
                    // This is a transport instance, lets leave it alone.
                    continue;
                }
                JID jid = new JID(ri.getJid().toBareJID());
                if (legacymap.containsKey(jid)) {
                    Log.debug("ROSTERSYNC: We found, updating " + jid.toString());
                    // Ok, matched a legacy to jabber roster item
                    // Lets update if there are differences
                    TransportBuddy buddy = legacymap.get(jid);
                    try {
                        if (buddy.getAskType() != null && buddy.getSubType() != null) {
                            this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroups(), buddy.getSubType(), buddy.getAskType());
                        }
                        else {
                            this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroups());
                        }
                    }
                    catch (UserNotFoundException e) {
                        Log.debug("Failed updating roster item", e);
                    }
                    legacymap.remove(jid);
                }
                else {
                    Log.debug("ROSTERSYNC: We did not find, removing " + jid.toString());
                    // This person is apparantly no longer in the legacy roster.
                    try {
                        this.removeFromRoster(userjid, jid);
                    }
                    catch (UserNotFoundException e) {
                        Log.debug("Failed removing roster item", e);
                    }
                }
            }

            // Ok, we should now have only new items from the legacy roster
            for (TransportBuddy buddy : legacymap.values()) {
                Log.debug("ROSTERSYNC: We have new, adding " + buddy.getName());
                try {
                    this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroups());
                }
                catch (UserNotFoundException e) {
                    Log.debug("Failed adding new roster item", e);
                }
            }

            // All done, lets unlock the roster.
            try {
                TransportSession<B> session = sessionManager.getSession(userjid.getNode());
                session.unlockRoster();
            }
            catch (NotFoundException e) {
                // No active session?  Then no problem.
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
    }

    /**
     * Cleans a roster of entries related to this transport.
     *
     * This function will run through the roster of the specified user and clean up any
     * entries that share the domain of this transport.  Depending on the removeNonPersistent
     * option, it will either leave or keep the non-persistent 'contact' entries.
     *
     * @param jid JID of the user whose roster we want to clean up.
     * @param leaveDomain If set, we do not touch the roster item associated with the domain itself.
     * @param removeNonPersistent If set, we will also remove non-persistent items.
     * @throws UserNotFoundException if the user is not found.
     */
    public void cleanUpRoster(JID jid, Boolean leaveDomain, Boolean removeNonPersistent) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(jid.getNode());

            // Lets lock down the roster from update notifications if there's an active session.
            try {
                TransportSession<B> session = sessionManager.getSession(jid.getNode());
                session.lockRoster();
            }
            catch (NotFoundException e) {
                // No active session?  Then no problem.
            }

            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().getDomain().equals(this.jid.getDomain())) {
                    if (ri.isShared()) { // Is a shared item we can't really touch.
                        continue;
                    }
                    if (!removeNonPersistent && ri.getID() == 0) { // Is a non-persistent roster item.
                        continue;
                    }
                    if (leaveDomain && ri.getJid().getNode() == null) { // The actual transport domain item.
                        continue;
                    }
                    try {
                        Log.debug("Cleaning up roster entry " + ri.getJid().toString());
                        roster.deleteRosterItem(ri.getJid(), false);
                    }
                    catch (Exception e) {
                        Log.debug("Error removing roster item: " + ri.toString(), e);
                    }
                }
            }

            // All done, lets unlock the roster.
            try {
                TransportSession<B> session = sessionManager.getSession(jid.getNode());
                session.unlockRoster();
            }
            catch (NotFoundException e) {
                // No active session?  Then no problem.
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
        }
    }

    /**
     * Cleans a roster of entries related to this transport that are not shared.
     *
     * This function will run through the roster of the specified user and clean up any
     * entries that share the domain of this transport.  This is primarily used during registration
     * to clean up leftovers from other transports.
     *
     * @param jid JID of user whose roster we want to clean up.
     * @param leaveDomain If set, we do not touch the roster item associated with the domain itself.
     * @throws UserNotFoundException if the user is not found.
     */
    public void cleanUpRoster(JID jid, Boolean leaveDomain) throws UserNotFoundException {
        try {
            cleanUpRoster(jid, leaveDomain, false);
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
        }
    }

    /**
     * Sends offline packets for an entire roster to the target user.
     *
     * This function will run through the roster of the specified user and send offline
     * presence packets for each roster item.   This is typically used when a user logs
     * off so that all of the associated roster items appear offline.  This does not send
     * the unavailable presence for the transport itself.
     *
     * @param jid JID of user whose roster we want to clean up.
     * @throws UserNotFoundException if the user is not found.
     * @deprecated Use net.sf.kraken.roster.TransportBuddyManager#sendOfflineForAllAvailablePresences(JID)
     */
    @Deprecated
    public void notifyRosterOffline(JID jid) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(jid.getNode());
            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().getNode() != null && ri.getJid().getDomain().equals(this.jid.getDomain())) {
                    Presence p = new Presence(Presence.Type.unavailable);
                    p.setTo(jid);
                    p.setFrom(ri.getJid());
                    sendPacket(p);
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
        }
    }

    /**
     * Sends a packet through the component manager as the component.
     *
     * @param packet Packet to be sent.
     */
    public void sendPacket(Packet packet) {
        // Prevent future chat state notifications from being send out for
        // contacts that are offline.
        if (packet instanceof Presence) {
            final Presence presence = (Presence) packet;
            if (presence.getType() == Presence.Type.unavailable && presence.getFrom().getNode() != null) {
                this.chatStateEventSource.isGone(presence.getFrom(), presence.getTo());
            }
        }
        
        Log.debug(getType().toString()+": Sending packet: "+packet.toXML());
        try {
            this.componentManager.sendPacket(this, packet);
        }
        catch (Exception e) {
            Log.warn("Failed to deliver packet: " + packet.toString());
        }
    }

    /**
     * Sends a simple message through he component manager.
     *
     * @param to Who the message is for.
     * @param from Who the message is from.
     * @param msg Message to be send.
     * @param type Type of message to be sent.
     */
    public void sendMessage(JID to, JID from, String msg, Message.Type type) {
        Message m = new Message();
        m.setType(type);
        m.setFrom(from);
        m.setTo(to);
        m.setBody(net.sf.kraken.util.StringUtils.removeInvalidXMLCharacters(msg));
        if (msg.length() == 0) {
            Log.debug("Dropping empty message packet.");
            return;
        }
        if (type.equals(Message.Type.chat) || type.equals(Message.Type.normal)) {
            chatStateEventSource.isActive(from, to);
            m.addChildElement("active", NameSpace.CHATSTATES);
            if (JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
                Element xEvent = m.addChildElement("x", "jabber:x:event");
    //            xEvent.addElement("id");
                xEvent.addElement("composing");
            }
        }
        else if (type.equals(Message.Type.error)) {
            // Error responses require error elements, even if we aren't going to do it "right" yet
            // TODO: All -real- error handling
            m.setError(Condition.undefined_condition);
        }
        try {
            TransportSession session = sessionManager.getSession(to);
            if (session.getDetachTimestamp() != 0) {
                // This is a detached session then, so lets store the packet instead of delivering.
                session.storePendingPacket(m);
                return;
            }
        }
        catch (NotFoundException e) {
            // No session?  That's "fine", allow it through, it's probably something from the transport itself.
        }
        sendPacket(m);
    }

    /**
     * Sends a simple message through the component manager.
     *
     * @param to Who the message is for.
     * @param from Who the message is from.
     * @param msg Message to be send.
     */
    public void sendMessage(JID to, JID from, String msg) {
        sendMessage(to, from, msg, Message.Type.chat);
    }

    public static SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");

    /**
     * Sends an offline message through the component manager.
     *
     * @param to Who the message is for.
     * @param from Who the message is from.
     * @param msg Message to be send.
     * @param type Type of message to be sent.
     * @param time Time when the message was originally sent.
     * @param reason Reason for offline message (can be null)
     */
    public void sendOfflineMessage(JID to, JID from, String msg, Message.Type type, Date time, String reason) {
        Message m = new Message();
        m.setType(type);
        m.setFrom(from);
        m.setTo(to);
        m.setBody(net.sf.kraken.util.StringUtils.removeInvalidXMLCharacters(msg));
        if (msg.length() == 0) {
            Log.debug("Dropping empty message packet.");
            return;
        }
        Element delay = m.addChildElement("delay", NameSpace.DELAY);
//        delay.addAttribute("from", from.toBareJID());
        delay.addAttribute("stamp", UTC_FORMAT.format(time));
        if (reason != null) {
            delay.addCDATA(reason);
        }
        Element offline = m.addChildElement("offline", NameSpace.OFFLINE);
        offline.addElement("item").addAttribute("node", UTC_FORMAT.format(time));
        Element x = m.addChildElement("x", NameSpace.X_DELAY);
//        x.addAttribute("from", from.toBareJID());
        x.addAttribute("stamp", UTC_FORMAT.format(time));
        if (reason != null) {
            x.addCDATA(reason);
        }
//        m.addChildElement("time","");
//        m.getChildElement("time","").setText(time);

        try {
            TransportSession session = sessionManager.getSession(to);
            if (session.getDetachTimestamp() != 0) {
                // This is a detached session then, so lets store the packet instead of delivering.
                session.storePendingPacket(m);
                return;
            }
        }
        catch (NotFoundException e) {
            // No session?  That's "fine", allow it through, it's probably something from the transport itself.
        }
        sendPacket(m);
    }

    /**
     * Sends an offline message through the component manager.
     *
     * @param to Who the message is for.
     * @param from Who the message is from.
     * @param msg Message to be send.
     * @param time Time when the message was originally sent.
     * @param reason Reason for offline message (can be null)
     */
    public void sendOfflineMessage(JID to, JID from, String msg, Date time, String reason) {
        sendOfflineMessage(to, from, msg, Message.Type.chat, time, reason);
    }

    /**
     * Sends a buzz notiifcation through the component manager.
     *
     * @param to Who the notification is for.
     * @param from Who the notification is from.
     * @param message Message attached to buzz notification.
     */
    private void sendBuzzNotification(JID to, JID from, String message) {
        Message m = new Message();
//        m.setType(Message.Type.headline);
        m.setTo(to);
        m.setFrom(from);
//        if (message != null && message.length() > 0) {
//            m.setBody(message);
//        }
        m.addChildElement("buzz", NameSpace.SPARKNS);
//        m.addChildElement("attention", "http://www.xmpp.org/extensions/xep-0224.html#ns");
        sendPacket(m);
    }

    /**
     * Sends an Attention notification, as specified in XEP-0224.
     * 
     * @param to The entity that of which the attention is being tried to capture.
     * @param from The entity that is trying to capturing another ones attention.
     * @param message An optional (headline) message (can be <tt>null</tt>).
     */
    public void sendAttentionNotification(JID to, JID from, String message) {
        // TODO: query receiving entity for support.
        
        // TODO: only buzz clients that support the Spark namespace!
        sendBuzzNotification(to, from, message);
        
        final Message stanza = new Message();
        if (message != null && message.trim().length() > 0) {
            stanza.setBody(message);
        }
        
        stanza.addChildElement("attention", NameSpace.ATTENTIONNS);
        
        stanza.setType(Message.Type.headline);
        stanza.setTo(to);
        stanza.setFrom(from);
        
        sendPacket(stanza);
    }

    /* (non-Javadoc)
     * @see net.sf.kraken.util.chatstate.ChatStateEventListener#chatStateChange(net.sf.kraken.util.chatstate.ChatStateEvent)
     */
    public void chatStateChange(ChatStateChangeEvent event) {
        if (this.getJID().getDomain().equals(event.sender.getDomain())) {
            // a local user is receiving a chat state event
            switch (event.type) {
                case active:
                    // No need to do anything here. "Active" chat states are
                    // included in each outgoing message. Sending them again
                    // here would only lead to duplicate messages.
                    //sendChatActiveNotification(event.receiver, event.sender);
                    break;

                case composing:
                    sendComposingNotification(event.receiver, event.sender);
                    break;

                case gone:
                    sendChatGoneNotification(event.receiver, event.sender);
                    break;

                case inactive:
                    sendChatInactiveNotification(event.receiver, event.sender);
                    break;

                case paused:
                    sendComposingPausedNotification(event.receiver, event.sender);
                    break;

                default:
                    // The code should include cases for every possible state.
                    throw new AssertionError(event.type);
            }
        }
        else if (this.getJID().getDomain().equals(event.receiver.getDomain())) {
            // a local user is sending an event
            try {
                getSessionManager().getSession(event.sender).sendChatState(event.receiver, event.type);
            }
            catch (NotFoundException e) {
                Log.debug("A local user that does not have a session with us is "
                        + "sending chat state messages to alegacy user. Event:" + event);
            }
        }
        else {
            Log.warn("Cannot send chat state event, as nor the sender or recipient "
                    + "appears to be a locally registered (and online) user. Event: " + event);
        }
    }

    /**
     * Sends a typing notification through the component manager.
     *
     * This will check whether the person supports typing notifications before sending.
     * TODO: actually check for typing notification support
     *
     * @param to Who the notification is for.
     * @param from Who the notification is from.
     */
    public void sendComposingNotification(JID to, JID from) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(to);
        m.setFrom(from);
        if (JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
            Element xEvent = m.addChildElement("x", "jabber:x:event");
            xEvent.addElement("id");
            xEvent.addElement("composing");
        }
        m.addChildElement("composing", NameSpace.CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends a typing paused notification through the component manager.
     *
     * This will check whether the person supports typing notifications before sending.
     * TODO: actually check for typing notification support
     *
     * @param to Who the notification is for.
     * @param from Who the notification is from.
     */
    public void sendComposingPausedNotification(JID to, JID from) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(to);
        m.setFrom(from);
        if (JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
            Element xEvent = m.addChildElement("x", "jabber:x:event");
            xEvent.addElement("id");
        }
        m.addChildElement("paused", NameSpace.CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends an inactive chat session notification through the component manager.
     *
     * This will check whether the person supports typing notifications before sending.
     * TODO: actually check for typing notification support
     *
     * @param to Who the notification is for.
     * @param from Who the notification is from.
     */
    public void sendChatInactiveNotification(JID to, JID from) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(to);
        m.setFrom(from);
        if (JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
            Element xEvent = m.addChildElement("x", "jabber:x:event");
            xEvent.addElement("id");
        }
        m.addChildElement("inactive", NameSpace.CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends a gone chat session notification through the component manager.
     *
     * This will check whether the person supports typing notifications before sending.
     * TODO: actually check for typing notification support
     *
     * @param to Who the notification is for.
     * @param from Who the notification is from.
     */
    public void sendChatGoneNotification(JID to, JID from) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(to);
        m.setFrom(from);
        if (JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
            Element xEvent = m.addChildElement("x", "jabber:x:event");
            xEvent.addElement("id");
        }
        m.addChildElement("gone", NameSpace.CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends a active chat session notification through the component manager.
     * Note that this type of state is also included in chat messages with a
     * body. Take care to avoid sending duplicate 'active' state messages. This
     * method is intended to be used only for those (rare) occurrances, where
     * the legacy domain wishes to express that a contact is activly taking part
     * in a conversation, without sending an actual chat message.
     * 
     * This will check whether the person supports typing notifications before
     * sending. TODO: actually check for typing notification support
     * 
     * @param to
     *            Who the notification is for.
     * @param from
     *            Who the notification is from.
     */
    public void sendChatActiveNotification(JID to, JID from) {
        final Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(to);
        m.setFrom(from);
        if (JiveGlobals.getBooleanProperty("plugin.gateway.globsl.messageeventing", true)) {
            final Element xEvent = m.addChildElement("x", "jabber:x:event");
            xEvent.addElement("id");
        }
        m.addChildElement("active", NameSpace.CHATSTATES);
        sendPacket(m);
    }
    
    /**
     * Intercepts roster additions related to the gateway and flags them as non-persistent.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#addingContact(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem, boolean)
     */
    public boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
        if (item.getJid().getDomain().equals(this.getJID().toString()) && item.getJid().getNode() != null && !JiveGlobals.getBooleanProperty("plugin.gateway.tweak.persistentroster", false)) {
            return false;
        }
        return persistent;
    }

    /**
     * Handles updates to a roster item that are not normally forwarded to the transport.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#contactUpdated(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem)
     */
    public void contactUpdated(Roster roster, RosterItem item) {
        //TODO: Disabling this for now
//        if (!item.getJid().getDomain().equals(this.getJID().getDomain())) {
//            // Not ours, not our problem.
//            return;
//        }
//        if (item.getJid().getNode() == null) {
//            // Gateway itself, don't care.
//            return;
//        }
//        if (item.getGroups() == null) {
//            // We got a null grouplist?  That's not reasonable.
//            return;
//        }
//        try {
//            TransportSession<B> session = sessionManager.getSession(roster.getUsername());
//            if (!session.isRosterLocked(item.getJid().toString())) {
//                Log.debug(getType().toString()+": contactUpdated "+roster.getUsername()+":"+item.getJid());
//                TransportBuddy buddy = session.getBuddyManager().getBuddy(item.getJid());
//                if (buddy != null) {
//                    buddy.setNicknameAndGroups(item.getNickname(), item.getGroups());
//                }
//                else {
//                    Log.debug("Updating contact "+item.getJid()+" for "+roster.getUsername()+" failed, unable to locate.");
//                }
//            }
//        }
//        catch (NotFoundException e) {
//            // Well we just don't care then.
//        }
    }

    /**
     * Handles additions to a roster.  We don't really care because we hear about these via subscribes.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#contactAdded(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem)
     */
    public void contactAdded(Roster roster, RosterItem item) {
        //TODO: Disabling this for now
//        if (!item.getJid().getDomain().equals(this.getJID().getDomain())) {
//            // Not ours, not our problem.
//            return;
//        }
//        if (item.getJid().getNode() == null) {
//            // Gateway itself, don't care.
//            return;
//        }
//        try {
//            TransportSession<B> session = sessionManager.getSession(roster.getUsername());
//            if (!session.isRosterLocked(item.getJid().toString())) {
//                Log.debug(getType().toString()+": contactAdded "+roster.getUsername()+":"+item.getJid());
//                session.addContact(item);
//            }
//        }
//        catch (NotFoundException e) {
//            // Well we just don't care then.
//        }
    }

    /**
     * Handles deletions from a roster.  We don't really care because we hear about these via unsubscribes.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#contactDeleted(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem)
     */
    public void contactDeleted(Roster roster, RosterItem item) {
        //TODO: Disabling this for now
//        if (!item.getJid().getDomain().equals(this.getJID().getDomain())) {
//            // Not ours, not our problem.
//            return;
//        }
//        if (item.getJid().getNode() == null) {
//            // Gateway itself, don't care.
//            return;
//        }
//        try {
//            TransportSession<B> session = sessionManager.getSession(roster.getUsername());
//            if (!session.isRosterLocked(item.getJid().toString())) {
//                Log.debug(getType().toString()+": contactDeleted "+roster.getUsername()+":"+item.getJid());
//                session.getBuddyManager().removeBuddy(convertJIDToID(item.getJid()));
//            }
//        }
//        catch (NotFoundException e) {
//            // Well we just don't care then.
//        }
    }

    /**
     * Handles notifications of a roster being loaded.  Not sure we care.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#rosterLoaded(org.jivesoftware.openfire.roster.Roster)
     */
    public void rosterLoaded(Roster roster) {
        Log.debug(getType().toString()+": rosterLoaded "+roster.getUsername());
        // Don't care
    }

    /**
     * Handles a user being deleted from the server.
     *
     * @see org.jivesoftware.openfire.event.UserEventListener#userDeleting(org.jivesoftware.openfire.user.User, java.util.Map)
     */
    public void userDeleting(User user, Map<String,Object> params) {
        try {
            new RegistrationHandler(this).deleteRegistration(XMPPServer
                    .getInstance().createJID(user.getUsername(), null));
        } catch (UserNotFoundException e) {
            // Not our problem then.
        }
    }

    /**
     * Handles a user being added to the server.  We do not care.
     *
     * @see org.jivesoftware.openfire.event.UserEventListener#userCreated(org.jivesoftware.openfire.user.User, java.util.Map)
     */
    public void userCreated(User user, Map<String,Object> params) {
        // Don't care.
    }

    /**
     * Handles a user being modified on the server.  We do not care.
     *
     * @see org.jivesoftware.openfire.event.UserEventListener#userModified(org.jivesoftware.openfire.user.User, java.util.Map)
     */
    public void userModified(User user, Map<String,Object> params) {
        // Don't care
    }

    /**
     * Handles a session going offline.  Apparently presence isn't entirely reliable...
     *
     * @see org.jivesoftware.openfire.event.SessionEventListener#sessionDestroyed(org.jivesoftware.openfire.session.Session)
     */
    public void sessionDestroyed(Session destroyedSession) {
        JID from = destroyedSession.getAddress();
        // A user's resource has gone offline.
        TransportSession<B> session;
        try {
            session = sessionManager.getSession(from);
            if (session.getLoginStatus().equals(TransportLoginStatus.LOGGING_OUT) || session.getLoginStatus().equals(TransportLoginStatus.LOGGED_OUT)) {
                // This is being taken care of by something else then.
                return;
            }
            String resource = from.getResource();
            if (session.hasResource(resource)) {
                if (session.getResourceCount() > 1) {

                    // Just one of the resources, lets adjust accordingly.
                    if (session.isHighestPriority(resource)) {
                        Log.debug("A high priority resource (of multiple) has gone offline (NoPres): " + from);

                        // Ooh, the highest resource went offline, drop to next highest.
                        session.removeResource(resource);

                        // Lets ask the next highest resource what it's presence is.
                        Presence p = new Presence(Presence.Type.probe);
                        p.setTo(session.getJIDWithHighestPriority());
                        p.setFrom(this.getJID());
                        sendPacket(p);
                    }
                    else {
                        Log.debug("A low priority resource (of multiple) has gone offline (NoPres): " + from);

                        // Meh, lower priority, big whoop.
                        session.removeResource(resource);
                    }
                }
                else {
                    Log.debug("A final resource has gone offline (NoPres): " + from);

                    // No more resources, byebye.
                    this.registrationLoggedOut(session);

                    sessionManager.removeSession(from);
                }
            }
        }
        catch (NotFoundException e) {
            // Ignore
        }
    }

    /**
     * Handles a resource getting bound.  We do not care.
     *
     * @see org.jivesoftware.openfire.event.SessionEventListener#resourceBound(org.jivesoftware.openfire.session.Session)
     */
    public void resourceBound(Session session) {
        // Do nothing.
    }

    /**
     * Handles a session coming online.  We do not care.
     *
     * @see org.jivesoftware.openfire.event.SessionEventListener#sessionCreated(org.jivesoftware.openfire.session.Session)
     */
    public void sessionCreated(Session session) {
        // Don't care
    }

    /**
     * Handles an anonymous session going offline.  We do not care.
     *
     * @see org.jivesoftware.openfire.event.SessionEventListener#anonymousSessionDestroyed(org.jivesoftware.openfire.session.Session)
     */
    public void anonymousSessionDestroyed(Session session) {
        try {
            new RegistrationHandler(this).deleteRegistration(XMPPServer
                    .getInstance().createJID(session.getAddress().getNode(), null));
        } catch (UserNotFoundException e) {
            // Not our problem then.
        }
    }

    /**
     * Handles an anonymous session coming online.  We do not care.
     *
     * @see org.jivesoftware.openfire.event.SessionEventListener#anonymousSessionCreated(org.jivesoftware.openfire.session.Session)
     */
    public void anonymousSessionCreated(Session session) {
        // Don't care
    }

    /**
     * VCard was just created.
     *
     * @see org.jivesoftware.openfire.vcard.VCardListener#vCardCreated(String, Element)
     */
    public void vCardCreated(String username, Element vcardElem) {
        if (vcardElem != null) {
            if (JiveGlobals.getBooleanProperty("plugin.gateway."+getType()+".avatars", true)) {
                Element photoElem = vcardElem.element("PHOTO");
                if (photoElem != null) {
                    Element typeElem = photoElem.element("TYPE");
                    Element binElem = photoElem.element("BINVAL");
                    if (typeElem != null && binElem != null) {
                        try {
                            MessageDigest md = MessageDigest.getInstance("SHA-1");
                            byte[] imageData = Base64.decode(binElem.getText());
                            md.update(imageData);
                            String xmppHash = StringUtils.encodeHex(md.digest());
                            try {
                                TransportSession<B> trSession = sessionManager.getSession(username);
                                if (trSession.getAvatar() == null || !trSession.getAvatar().getXmppHash().equals(xmppHash)) {
                                    // Store a cache of the avatar
                                    trSession.setAvatar(new Avatar(trSession.getJID(), imageData));
                                    trSession.updateLegacyAvatar(typeElem.getText(), imageData);
                                }
                            }
                            catch (NotFoundException e) {
                                // Not an active session, ignore.
                            }
                        }
                        catch (NoSuchAlgorithmException e) {
                            Log.error("Gateway: Unable to find support for SHA algorith?");
                        }
                    }
                }
            }
        }
    }

    /**
     * VCard was just updated.
     *
     * @see org.jivesoftware.openfire.vcard.VCardListener#vCardUpdated(String, Element)
     */
    public void vCardUpdated(String username, Element vcardElem) {
        if (vcardElem != null) {
            if (JiveGlobals.getBooleanProperty("plugin.gateway."+getType()+".avatars", true)) {
                Element photoElem = vcardElem.element("PHOTO");
                if (photoElem != null) {
                    Element typeElem = photoElem.element("TYPE");
                    Element binElem = photoElem.element("BINVAL");
                    if (typeElem != null && binElem != null) {
                        try {
                            MessageDigest md = MessageDigest.getInstance("SHA-1");
                            byte[] imageData = Base64.decode(binElem.getText());
                            md.update(imageData);
                            String xmppHash = StringUtils.encodeHex(md.digest());
                            try {
                                TransportSession<B> trSession = sessionManager.getSession(username);
                                if (trSession.getAvatar() == null || !trSession.getAvatar().getXmppHash().equals(xmppHash)) {
                                    // Store a cache of the avatar
                                    trSession.setAvatar(new Avatar(trSession.getJID(), imageData));
                                    trSession.updateLegacyAvatar(typeElem.getText(), imageData);
                                }
                            }
                            catch (NotFoundException e) {
                                // Not an active session, ignore.
                            }
                        }
                        catch (NoSuchAlgorithmException e) {
                            Log.error("Gateway: Unable to find support for SHA algorith?");
                        }
                    }
                }
            }
        }
    }

    /**
     * VCard was just deleted.
     *
     * @see org.jivesoftware.openfire.vcard.VCardListener#vCardDeleted(String, Element)
     */
    public void vCardDeleted(String username, Element vcardElem) {
        Log.debug("vCardDeleted: "+username);
        // TODO: Handle this for potentially an avatar undo   
    }

    /**
     * Intercepts disco items packets to filter out users who aren't allowed to register.
     *
     * @see org.jivesoftware.openfire.interceptor.PacketInterceptor#interceptPacket(org.xmpp.packet.Packet, org.jivesoftware.openfire.session.Session, boolean, boolean)
     */
    @SuppressWarnings("unchecked")
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) {
        // If not IQ, return immediately.
        if (!(packet instanceof IQ)) {
            return;
        }

        // If it's a result IQ, process for possible filtering.
        if (((IQ)packet).getType().equals(IQ.Type.result)) {

            // If the packet is not outgoing back to the user or not processed yet, we don't care.
            if (processed || incoming) {
                return;
            }

            // If not query, return immediately.
            Element child = packet.getElement().element("query");
            if (child == null) {
                return;
            }

            // If no namespace uri, return immediately.
            if (child.getNamespaceURI() == null) {
                return;
            }

            // If not disco#items, return immediately.
            if (!child.getNamespaceURI().equals(NameSpace.DISCO_ITEMS)) {
                return;
            }

            // If the node is null, we don't care, not directly related to a user.
            JID to = packet.getTo();
            if (to.getNode() == null) {
                return;
            }

            JID from = packet.getFrom();
            // If not from server itself, return immediately.
            if (!XMPPServer.getInstance().isLocal(from)) {
                return;
            }

            // If user registered, return immediately.
            if (RegistrationManager.getInstance().isRegistered(to, transportType)) {
                return;
            }

            // Check if allowed, if so return immediately.
            if (permissionManager.hasAccess(to)) {
                return;
            }

            // Filter out item associated with transport.
            Iterator iter = child.elementIterator();
            while (iter.hasNext()) {
                Element elem = (Element)iter.next();
                try {
                    if (elem.attribute("jid").getText().equals(this.jid.toString())) {
                        child.remove(elem);
                    }
                }
                catch (Exception e) {
                    // No worries.  Wasn't what we were looking for.
                }
            }

            //TODO: should filter conference as well at some point.

            return;
        }

        // If it's a set IQ, process for possible roster activity.
        if (((IQ)packet).getType().equals(IQ.Type.set)) {

            // If the packet is not coming from the user, we don't care.
            if (!incoming) {
                return;
            }

            // If not query, return immediately.
            Element child = packet.getElement().element("query");
            if (child == null) {
                return;
            }

            // If not jabber:iq:roster, return immediately.
            if (!child.getNamespaceURI().equals(NameSpace.IQ_ROSTER)) {
                return;
            }

            // Example items in roster modification.
            Iterator iter = child.elementIterator();
            while (iter.hasNext()) {
                Element elem = (Element)iter.next();
                if (!elem.getName().equals("item")) { continue; }
                String jidStr;
                String nickname = null;
                String sub = null;
                ArrayList<String> groups = new ArrayList<String>();
                try {
                    jidStr = elem.attributeValue("jid");
                }
                catch (Exception e) {
                    // No JID found, we don't want this then.
                    continue;
                }
                JID jid = new JID(jidStr);
                if (!jid.getDomain().equals(this.getJID().toString())) {
                    // Not for our domain, moving on.
                    continue;
                }
                if (jid.getNode() == null) {
                    // Gateway itself, don't care.
                    return;
                }
                try {
                    nickname = elem.attributeValue("name");
                }
                catch (Exception e) {
                    // No nickname, ok then.
                }
                try {
                    sub = elem.attributeValue("subscription");
                }
                catch (Exception e) {
                    // No subscription, no worries.
                }
                Iterator groupIter = elem.elementIterator();
                while (groupIter.hasNext()) {
                    Element groupElem = (Element)groupIter.next();
                    if (!groupElem.getName().equals("group")) { continue; }
                    groups.add(groupElem.getText());
                }
                if (sub != null && sub.equals("remove")) {
                    try {
                        TransportSession trSession = sessionManager.getSession(session.getAddress().getNode());
                        if (!trSession.isRosterLocked(jid.toString())) {
                            Log.debug(getType().toString()+": contact delete "+session.getAddress().getNode()+":"+jid);
                            trSession.getBuddyManager().removeBuddy(convertJIDToID(jid));
                        }
                    }
                    catch (NotFoundException e) {
                        // Well we just don't care then.
                    }
                }
                else {
                    try {
                        TransportSession trSession = sessionManager.getSession(session.getAddress().getNode());
                        if (!trSession.isRosterLocked(jid.toString())) {
                            try {
                                TransportBuddy buddy = trSession.getBuddyManager().getBuddy(jid);
                                Log.debug(getType().toString()+": contact update "+session.getAddress().getNode()+":"+jid+":"+nickname+":"+groups);
                                buddy.setNicknameAndGroups(nickname, groups);
                            }
                            catch (NotFoundException e) {
                                Log.debug(getType().toString()+": contact add "+session.getAddress().getNode()+":"+jid+":"+nickname+":"+groups);
                                trSession.addContact(jid, nickname, groups);
                            }
                        }
                    }
                    catch (NotFoundException e) {
                        // Well we just don't care then.
                    }
                }
            }
        }
    }

    /**
     * Will handle logging in to the legacy service.
     *
     * @param registration Registration used for log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     * @param priority Priority of the session (from presence packet).
     * @return A session instance for the new login.
     */
    public abstract TransportSession<B> registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority);

    /**
     * Will handle logging out of the legacy service.
     *
     * @param session TransportSession to be logged out.
     */
    public abstract void registrationLoggedOut(TransportSession<B> session);

    /**
     * Returns the terminology used for a username on the legacy service.
     *
     * @return String term for username.
     */
    public abstract String getTerminologyUsername();

    /**
     * Returns the terminology used for a password on the legacy service.
     *
     * @return String term for password.
     */
    public abstract String getTerminologyPassword();

    /**
     * Returns the terminology used for a nickname on the legacy service.
     *
     * You can return null to indicate that this is not supported by the legacy service.
     *
     * @return String term for nickname.
     */
    public abstract String getTerminologyNickname();

    /**
     * Returns instructions for registration in legacy complient terminology.
     *
     * You would write this out as if the entry textfields for the username and password
     * are after it/on the same page.  So something along these lines would be good:
     * Please enter your legacy username and password.
     *
     * @return String phrase for registration.
     */
    public abstract String getTerminologyRegistration();

    /**
     * Is a password required for this service?
     *
     * @return True or false whether the password is required.
     */
    public abstract Boolean isPasswordRequired();

    /**
     * Is a nickname required for this service?
     *
     * @return True or false whether the nickname is required.
     */
    public abstract Boolean isNicknameRequired();

    /**
     * Is the specified username valid?
     *
     * @param username Username to validate.
     * @return True or false whether the passed username is valud for the service.
     */
    public abstract Boolean isUsernameValid(String username);

}
