/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.*;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.*;
import org.xmpp.packet.PacketError.Condition;

import java.util.*;

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
public abstract class BaseTransport implements Component, RosterEventListener {

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
     */
    public void setup(TransportType type, String description) {
        this.description = description;
        this.transportType = type;
        permissionManager = new PermissionManager(transportType);
    }

    /**
     * Handles initialization of the transport.
     */
    public void initialize(JID jid, ComponentManager componentManager) {
        this.jid = jid;
        this.componentManager = componentManager;
        rosterManager = XMPPServer.getInstance().getRosterManager();
        sessionManager.startThreadManager(jid);
    }

    /**
     * Manages all active sessions.
     * @see org.jivesoftware.openfire.gateway.TransportSessionManager
     */
    public final TransportSessionManager sessionManager = new TransportSessionManager(this);

    /**
     * Manages registration information.
     * @see org.jivesoftware.openfire.gateway.RegistrationManager
     */
    public final RegistrationManager registrationManager = new RegistrationManager();

    /**
     * Manages permission information.
     * @see org.jivesoftware.openfire.gateway.PermissionManager
     */
    public PermissionManager permissionManager = null;

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
     * Type of the transport in question.
     * @see org.jivesoftware.openfire.gateway.TransportType
     */
    public TransportType transportType = null;

    private final String DISCO_INFO = "http://jabber.org/protocol/disco#info";
    private final String DISCO_ITEMS = "http://jabber.org/protocol/disco#items";
    private final String IQ_GATEWAY = "jabber:iq:gateway";
    private final String IQ_GATEWAY_REGISTER = "jabber:iq:gateway:register";
    private final String IQ_REGISTER = "jabber:iq:register";
    private final String IQ_REGISTERED = "jabber:iq:registered";
    private final String IQ_VERSION = "jabber:iq:version";
    private final String CHATSTATES = "http://jabber.org/protocol/chatstates";
    private final String XEVENT = "jabber:x:event";

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

            if (reply.size() > 0) {
                for (Packet p : reply) {
                    this.sendPacket(p);
                }
            }
        }
        catch (Exception e) {
            Log.error("Error occured while processing packet:", e);
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

        try {
            TransportSession session = sessionManager.getSession(from);
            if (!session.isLoggedIn()) {
                Message m = new Message();
                m.setError(Condition.service_unavailable);
                m.setTo(from);
                m.setFrom(getJID());
                m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "gateway", Arrays.asList(transportType.toString().toUpperCase())));
                reply.add(m);
            }
            else if (to.getNode() == null) {
                // Message to gateway itself.  Throw away for now.
                if (packet.getBody() != null) {
                    session.sendServerMessage(packet.getBody());
                }
            }
            else {
                if (packet.getBody() != null) {
                    session.sendMessage(to, packet.getBody());
                }
                else {
                    // Checking for XEP-0022 message events
                    Element eEvent = packet.getChildElement("x", XEVENT);
                    if (eEvent != null) {
                        if (eEvent.element("composing") != null) {
                            session.sendChatState(to, ChatStateType.composing);
                        }
                        else {
                            session.sendChatState(to, ChatStateType.paused);
                        }
                    }
                    else {
                        // Ok then, lets check for XEP-0085 chat states
                        if (packet.getChildElement("composing", CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.composing);
                        }
                        else if (packet.getChildElement("active", CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.active);
                        }
                        else if (packet.getChildElement("inactive", CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.inactive);
                        }
                        else if (packet.getChildElement("paused", CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.paused);
                        }
                        else if (packet.getChildElement("gone", CHATSTATES) != null) {
                            session.sendChatState(to, ChatStateType.gone);
                        }
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
            m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "gateway", Arrays.asList(transportType.toString().toUpperCase())));
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

        if (packet.getType() == Presence.Type.error) {
            // We don't want to do anything with this.  Ignore it.
            return reply;
        }

        try {
            if (to.getNode() == null) {
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (registrations.isEmpty()) {
                    // User is not registered with us.
                    Log.debug("Unable to find registration.");
                    return reply;
                }
                Registration registration = registrations.iterator().next();

                // This packet is to the transport itself.
                if (packet.getType() == null) {
                    // A user's resource has come online.
                    TransportSession session;
                    try {
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
                            session.resendContactStatuses(from);
                            // If this priority is the highest, treat it's status as golden
                            if (session.isHighestPriority(from.getResource())) {
                                session.updateStatus(getPresenceType(packet), packet.getStatus());
                            }
                        }
                    }
                    catch (NotFoundException e) {
                        Log.debug("A new session has come online: " + from);

                        session = this.registrationLoggedIn(registration, from, getPresenceType(packet), packet.getStatus(), packet.getPriority());
                        sessionManager.storeSession(from, session);
                    }
                }
                else if (packet.getType() == Presence.Type.unavailable) {
                    // A user's resource has gone offline.
                    TransportSession session;
                    try {
                        session = sessionManager.getSession(from);
                        if (session.getResourceCount() > 1) {
                            String resource = from.getResource();

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
                            if (session.isLoggedIn()) {
                                this.registrationLoggedOut(session);
                            }

                            sessionManager.removeSession(from);
                        }
                    }
                    catch (NotFoundException e) {
                        Log.debug("Ignoring unavailable presence for inactive seession.");
                    }
                }
                else if (packet.getType() == Presence.Type.probe) {
                    // Client is asking for presence status.
                    TransportSession session;
                    try {
                        session = sessionManager.getSession(from);
                        if (session.isLoggedIn()) {
                            Presence p = new Presence();
                            p.setTo(from);
                            p.setFrom(to);
                            this.sendPacket(p);
                        }
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
                    TransportSession session = sessionManager.getSession(from);

                    if (packet.getType() == Presence.Type.probe) {
                        // Presence probe, lets try to tell them.
                        session.retrieveContactStatus(to);
                    }
                    else {
                        // Anything else we will ignore for now.
                    }
                }
                catch (NotFoundException e) {
                    // Well we just don't care then.
                }
            }
        }
        catch (Exception e) {
            Log.error("Exception while processing packet: ", e);
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

        if (xmlns.equals(DISCO_INFO)) {
            reply.addAll(handleDiscoInfo(packet));
        }
        else if (xmlns.equals(DISCO_ITEMS)) {
            reply.addAll(handleDiscoItems(packet));
        }
        else if (xmlns.equals(IQ_GATEWAY)) {
            reply.addAll(handleIQGateway(packet));
        }
        else if (xmlns.equals(IQ_REGISTER)) {
            reply.addAll(handleIQRegister(packet));
        }
        else if (xmlns.equals(IQ_VERSION)) {
            reply.addAll(handleIQVersion(packet));
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
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();

        if (packet.getTo().getNode() == null) {
            // Requested info from transport itself.
            IQ result = IQ.createResultIQ(packet);
            Element response = DocumentHelper.createElement(QName.get("query", DISCO_INFO));
            response.addElement("identity")
                    .addAttribute("category", "gateway")
                    .addAttribute("type", this.transportType.toString())
                    .addAttribute("name", this.description);
            response.addElement("feature")
                    .addAttribute("var", IQ_GATEWAY);
            response.addElement("feature")
                    .addAttribute("var", IQ_REGISTER);
            response.addElement("feature")
                    .addAttribute("var", IQ_VERSION);
            Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
            if (!registrations.isEmpty()) {
                response.addElement("feature")
                        .addAttribute("var", IQ_REGISTERED);
            }
            result.setChildElement(response);
            reply.add(result);
        }

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
            Element query = DocumentHelper.createElement(QName.get("query", IQ_GATEWAY));
            query.addElement("desc").addText(LocaleUtils.getLocalizedString("gateway.base.enterusername", "gateway", Arrays.asList(transportType.toString().toUpperCase())));
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
                Element query = DocumentHelper.createElement(QName.get("query", IQ_GATEWAY));
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
     * Handle registration request.
     *
     * @param packet An IQ packet in the iq registration namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQRegister(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        Element remove = packet.getChildElement().element("remove");
        if (remove != null) {
            // User wants to unregister.  =(
            // this.convinceNotToLeave() ... kidding.
            IQ result = IQ.createResultIQ(packet);

            if (packet.getChildElement().elements().size() != 1) {
                result.setError(Condition.bad_request);
                reply.add(result);
                return reply;
            }

            // Tell the end user the transport went byebye.
            Presence unavailable = new Presence(Presence.Type.unavailable);
            unavailable.setTo(from);
            unavailable.setFrom(to);
            reply.add(unavailable);

            try {
                this.deleteRegistration(from);
            }
            catch (UserNotFoundException e) {
                Log.error("Error cleaning up contact list of: " + from);
                result.setError(Condition.registration_required);
            }

            reply.add(result);
        }
        else {
            // User wants to register with the transport.
            String username = null;
            String password = null;
            String nickname = null;

            try {
                DataForm form = new DataForm(packet.getChildElement().element("x"));
                List<FormField> fields = form.getFields();
                for (FormField field : fields) {
                    String var = field.getVariable();
                    if (var.equals("username")) {
                        username = field.getValues().get(0);
                    }
                    else if (var.equals("password")) {
                        password = field.getValues().get(0);
                    }
                    else if (var.equals("nick")) {
                        nickname = field.getValues().get(0);
                    }

                }
            }
            catch (Exception e) {
                // No with data form apparantly
            }

            if (packet.getType() == IQ.Type.set) {
                Boolean registered = false;
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (registrations.iterator().hasNext()) {
                    registered = true;
                }

                if (!registered && !permissionManager.hasAccess(from)) {
                    // User does not have permission to register with transport.
                    // We want to allow them to change settings if they are already registered.
                    IQ result = IQ.createResultIQ(packet);
                    result.setError(Condition.forbidden);
                    reply.add(result);
                    Message em = new Message();
                    em.setType(Message.Type.error);
                    em.setTo(packet.getFrom());
                    em.setFrom(packet.getTo());
                    em.setBody(LocaleUtils.getLocalizedString("gateway.base.registrationdeniedbyacls", "gateway"));
                    reply.add(em);
                    return reply;
                }

                Element userEl = packet.getChildElement().element("username");
                Element passEl = packet.getChildElement().element("password");
                Element nickEl = packet.getChildElement().element("nick");

                if (userEl != null) {
                    username = userEl.getTextTrim();
                }

                if (passEl != null) {
                    password = passEl.getTextTrim();
                }

                if (nickEl != null) {
                    nickname = nickEl.getTextTrim();
                }

                username = (username == null || username.equals("")) ? null : username;
                password = (password == null || password.equals("")) ? null : password;
                nickname = (nickname == null || nickname.equals("")) ? null : nickname;

                if (    username == null
                        || (isPasswordRequired() && password == null)
                        || (isNicknameRequired() && nickname == null)) {
                    // Invalid information from stanza, lets yell.
                    IQ result = IQ.createResultIQ(packet);
                    result.setError(Condition.bad_request);
                    reply.add(result);
                }
                else {
                    boolean rosterlessMode = false;
                    Element x = packet.getChildElement().element("x");
                    if (x != null && x.getNamespaceURI() != null && x.getNamespaceURI().equals(IQ_GATEWAY_REGISTER)) {
                        rosterlessMode = true;
                        Log.info("Registered " + packet.getFrom() + " as " + username + " in rosterless mode.");
                    }
                    else {
                        Log.info("Registered " + packet.getFrom() + " as " + username);
                    }

                    IQ result = IQ.createResultIQ(packet);
                    Element response = DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
                    result.setChildElement(response);
                    reply.add(result);

                    try {
                        this.addNewRegistration(from, username, password, nickname, rosterlessMode);
                    }
                    catch (UserNotFoundException e) {
                        Log.error("Someone attempted to register with the gateway who is not registered with the server: " + from);
                        IQ eresult = IQ.createResultIQ(packet);
                        eresult.setError(Condition.forbidden);
                        reply.add(eresult);
                        Message em = new Message();
                        em.setType(Message.Type.error);
                        em.setTo(packet.getFrom());
                        em.setFrom(packet.getTo());
                        em.setBody(LocaleUtils.getLocalizedString("gateway.base.registrationdeniednoacct", "gateway"));
                        reply.add(em);
                    }
                    catch (IllegalAccessException e) {
                        Log.error("Someone who is not a user of this server tried to register with the transport: "+from);
                        IQ eresult = IQ.createResultIQ(packet);
                        eresult.setError(Condition.forbidden);
                        reply.add(eresult);
                        Message em = new Message();
                        em.setType(Message.Type.error);
                        em.setTo(packet.getFrom());
                        em.setFrom(packet.getTo());
                        em.setBody(LocaleUtils.getLocalizedString("gateway.base.registrationdeniedbyhost", "gateway"));
                        reply.add(em);
                    }
                    catch (IllegalArgumentException e) {
                        Log.error("Someone attempted to register with the gateway with an invalid username: " + from);
                        IQ eresult = IQ.createResultIQ(packet);
                        eresult.setError(Condition.bad_request);
                        reply.add(eresult);
                        Message em = new Message();
                        em.setType(Message.Type.error);
                        em.setTo(packet.getFrom());
                        em.setFrom(packet.getTo());
                        em.setBody(LocaleUtils.getLocalizedString("gateway.base.registrationdeniedbadusername", "gateway"));
                        reply.add(em);
                    }
                }
            }
            else if (packet.getType() == IQ.Type.get) {
                Element response = DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
                IQ result = IQ.createResultIQ(packet);

                String curUsername = null;
                String curPassword = null;
                String curNickname = null;
                Boolean registered = false;
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (registrations.iterator().hasNext()) {
                    Registration registration = registrations.iterator().next();
                    curUsername = registration.getUsername();
                    curPassword = registration.getPassword();
                    curNickname = registration.getNickname();
                    registered = true;
                }

                if (!registered && !permissionManager.hasAccess(from)) {
                    // User does not have permission to register with transport.
                    // We want to allow them to change settings if they are already registered.
                    result.setError(Condition.forbidden);
                    reply.add(result);
                    Message em = new Message();
                    em.setType(Message.Type.error);
                    em.setTo(packet.getFrom());
                    em.setFrom(packet.getTo());
                    em.setBody(LocaleUtils.getLocalizedString("gateway.base.registrationdeniedbyacls", "gateway"));
                    reply.add(em);
                    return reply;
                }

                DataForm form = new DataForm(DataForm.Type.form);
                form.addInstruction(getTerminologyRegistration());

                FormField usernameField = form.addField();
                usernameField.setLabel(getTerminologyUsername());
                usernameField.setVariable("username");
                usernameField.setType(FormField.Type.text_single);
                if (curUsername != null) {
                    usernameField.addValue(curUsername);
                }

                FormField passwordField = form.addField();
                passwordField.setLabel(getTerminologyPassword());
                passwordField.setVariable("password");
                passwordField.setType(FormField.Type.text_private);
                if (curPassword != null) {
                    passwordField.addValue(curPassword);
                }

                String nicknameTerm = getTerminologyNickname();
                if (nicknameTerm != null) {
                    FormField nicknameField = form.addField();
                    nicknameField.setLabel(nicknameTerm);
                    nicknameField.setVariable("nick");
                    nicknameField.setType(FormField.Type.text_single);
                    if (curNickname != null) {
                        nicknameField.addValue(curNickname);
                    }
                }

                response.add(form.getElement());

                response.addElement("instructions").addText(getTerminologyRegistration());
                if (registered) {
                    response.addElement("registered");
                    response.addElement("username").addText(curUsername);
                    if (curPassword == null) {
                        response.addElement("password");
                    }
                    else {
                        response.addElement("password").addText(curPassword);
                    }
                    if (nicknameTerm != null) {
                        if (curNickname == null) {
                            response.addElement("nick");
                        }
                        else {
                            response.addElement("nick").addText(curNickname);
                        }
                    }
                }
                else {
                    response.addElement("username");
                    response.addElement("password");
                    if (nicknameTerm != null) {
                        response.addElement("nick");
                    }
                }

                // Add special indicator for rosterless gateway handling.
                response.addElement("x").addNamespace("", IQ_GATEWAY_REGISTER);

                result.setChildElement(response);

                reply.add(result);
            }
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
            Element query = DocumentHelper.createElement(QName.get("query", IQ_VERSION));
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
//        PresenceEventDispatcher.addListener(this);
        // Probe all registered users [if they are logged in] to auto-log them in
        for (Registration registration : registrationManager.getRegistrations()) {
            if (SessionManager.getInstance().getSessionCount(registration.getJID().getNode()) > 0) {
                Presence p = new Presence(Presence.Type.probe);
                p.setFrom(this.getJID());
                p.setTo(registration.getJID());
                sendPacket(p);
            }
        }
    }

    /**
     * Handles shutdown of the transport.
     *
     * Cleans up all active sessions.
     */
    public void shutdown() {
        RosterEventDispatcher.removeListener(this);
//        PresenceEventDispatcher.removeListener(this);
        // Disconnect everyone's session
        for (TransportSession session : sessionManager.getSessions()) {
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
     * Returns the name (type) of the transport.
     */
    public String getName() {
        return transportType.toString();
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
     * @return the registration manager of the transport.
     */
    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    /**
     * @return the session manager of the transport.
     */
    public TransportSessionManager getSessionManager() {
        return sessionManager;
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
            versionString = pluginManager.getVersion(pluginManager.getPlugin("gateway"));
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
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, List<String> groups) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());
            try {
                RosterItem gwitem = roster.getRosterItem(contactjid);
                boolean changed = false;
                if (gwitem.getSubStatus() != RosterItem.SUB_BOTH) {
                    gwitem.setSubStatus(RosterItem.SUB_BOTH);
                    changed = true;
                }
                if (gwitem.getAskStatus() != RosterItem.ASK_NONE) {
                    gwitem.setAskStatus(RosterItem.ASK_NONE);
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
                if (curgroups != groups) {
                    try {
                        gwitem.setGroups(groups);
                        changed = true;
                    }
                    catch (Exception ee) {
                        // Oooookay, ignore then.
                    }
                }
                if (changed) {
                    roster.updateRosterItem(gwitem);
                }
            }
            catch (UserNotFoundException e) {
                try {
                    // Create new roster item for the gateway service or legacy contact. Only
                    // roster items related to the gateway service will be persistent. Roster
                    // items of legacy users are never persisted in the DB.
                    RosterItem gwitem =
                            roster.createRosterItem(contactjid, true, contactjid.getNode() == null);
                    gwitem.setSubStatus(RosterItem.SUB_BOTH);
                    gwitem.setAskStatus(RosterItem.ASK_NONE);
                    gwitem.setNickname(nickname);
                    try {
                        gwitem.setGroups(groups);
                    }
                    catch (Exception ee) {
                        // Oooookay, ignore then.
                    }
                    roster.updateRosterItem(gwitem);
                }
                catch (UserAlreadyExistsException ee) {
                    Log.error("getRosterItem claims user exists, but couldn't find via getRosterItem?", ee);
                }
                catch (Exception ee) {
                    Log.error("Exception while creating roster item:", ee);
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
     * Either updates or adds a contact to a user's roster.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactid String contact name, will be translated to JID.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, String contactid, String nickname, String group) throws UserNotFoundException {
        try {
            addOrUpdateRosterItem(userjid, convertIDToJID(contactid), nickname, group);
        }
        catch (UserNotFoundException e) {
            // Pass it on down.
            throw e;
        }
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
                        Log.error("Error removing roster item: " + ri.toString(), e);
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
        try {
            removeFromRoster(userjid, convertIDToJID(contactid));
        }
        catch (UserNotFoundException e) {
            // Pass it on through.
            throw e;
        }
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
    public void syncLegacyRoster(JID userjid, List<TransportBuddy> legacyitems) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());

            // Lets lock down the roster from update notifications if there's an active session.
            try {
                TransportSession session = sessionManager.getSession(userjid.getNode());
                session.lockRoster();
            }
            catch (NotFoundException e) {
                // No active session?  Then no problem.
            }

            // First thing first, we want to build ourselves an easy mapping.
            Map<JID,TransportBuddy> legacymap = new HashMap<JID,TransportBuddy>();
            for (TransportBuddy buddy : legacyitems) {
                //Log.debug("ROSTERSYNC: Mapping "+buddy.getName());
                legacymap.put(convertIDToJID(buddy.getName()), buddy);
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
                    //Log.debug("ROSTERSYNC: We found, updating " + jid.toString());
                    // Ok, matched a legacy to jabber roster item
                    // Lets update if there are differences
                    TransportBuddy buddy = legacymap.get(jid);
                    try {
                        this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroup());
                    }
                    catch (UserNotFoundException e) {
                        Log.error("Failed updating roster item", e);
                    }
                    legacymap.remove(jid);
                }
                else {
                    //Log.debug("ROSTERSYNC: We did not find, removing " + jid.toString());
                    // This person is apparantly no longer in the legacy roster.
                    try {
                        this.removeFromRoster(userjid, jid);
                    }
                    catch (UserNotFoundException e) {
                        Log.error("Failed removing roster item", e);
                    }
                }
            }

            // Ok, we should now have only new items from the legacy roster
            for (TransportBuddy buddy : legacymap.values()) {
                //Log.debug("ROSTERSYNC: We have new, adding " + buddy.getName());
                try {
                    this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroup());
                }
                catch (UserNotFoundException e) {
                    Log.error("Failed adding new roster item", e);
                }
            }

            // All done, lets unlock the roster.
            try {
                TransportSession session = sessionManager.getSession(userjid.getNode());
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
     * Adds a registration with this transport.
     *
     * @param jid JID of user to add registration to.
     * @param username Legacy username of registration.
     * @param password Legacy password of registration.
     * @param nickname Legacy nickname of registration.
     * @param noRosterItem True if the transport is not to show up in the user's roster.
     * @throws UserNotFoundException if registration or roster not found.
     * @throws IllegalAccessException if jid is not from this server.
     * @throws IllegalArgumentException if username is not valid for this transport type.
     */
    public void addNewRegistration(JID jid, String username, String password, String nickname, Boolean noRosterItem) throws UserNotFoundException, IllegalAccessException {
        if (!XMPPServer.getInstance().getServerInfo().getName().equals(jid.getDomain())) {
            throw new IllegalAccessException("Domain of jid registering does not match domain of server.");
        }

        if (!isUsernameValid(username)) {
            throw new IllegalArgumentException("Username specified is not valid for this transport type.");
        }

        Collection<Registration> registrations = registrationManager.getRegistrations(jid, this.transportType);
        Boolean foundReg = false;
        for (Registration registration : registrations) {
            if (!registration.getUsername().equals(username)) {
                registrationManager.deleteRegistration(registration);
            }
            else {
                registration.setPassword(password);
                foundReg = true;
            }
            try {
                TransportSession relatedSession = sessionManager.getSession(registration.getJID().getNode());
                this.registrationLoggedOut(relatedSession);
            }
            catch (NotFoundException e) {
                // No worries, move on.
            }
        }

        if (!foundReg) {
            registrationManager.createRegistration(jid, this.transportType, username, password, nickname);
        }

        // Clean up any leftover roster items from other transports.
        try {
            cleanUpRoster(jid, !noRosterItem);
        }
        catch (UserNotFoundException ee) {
            throw new UserNotFoundException("Unable to find roster.");
        }

        if (!noRosterItem) {
            try {
                addOrUpdateRosterItem(jid, this.getJID(), this.getDescription(), "Transports");
            }
            catch (UserNotFoundException e) {
                throw new UserNotFoundException("User not registered with server.");
            }
        }

        // Lets ask them what their presence is, maybe log them in immediately.
        Presence p = new Presence(Presence.Type.probe);
        p.setTo(jid);
        p.setFrom(getJID());
        sendPacket(p);

    }

    /**
     * Removes a registration from this transport.
     *
     * @param jid JID of user to add registration to.
     * @throws UserNotFoundException if registration or roster not found.
     */
    public void deleteRegistration(JID jid) throws UserNotFoundException {
        Collection<Registration> registrations = registrationManager.getRegistrations(jid, this.transportType);
        if (registrations.isEmpty()) {
            throw new UserNotFoundException("User was not registered.");
        }


        // Log out any active sessions.
        try {
            TransportSession session = sessionManager.getSession(jid);
            if (session.isLoggedIn()) {
                this.registrationLoggedOut(session);
            }
            sessionManager.removeSession(jid);
        }
        catch (NotFoundException e) {
            // Ok then.
        }
        
        // For now, we're going to have to just nuke all of these.  Sorry.
        for (Registration reg : registrations) {
            registrationManager.deleteRegistration(reg);
        }

        // Clean up the user's contact list.
        try {
            cleanUpRoster(jid, false, true);
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
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
                TransportSession session = sessionManager.getSession(jid.getNode());
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
                        Log.error("Error removing roster item: " + ri.toString(), e);
                    }
                }
            }

            // All done, lets unlock the roster.
            try {
                TransportSession session = sessionManager.getSession(jid.getNode());
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
     * off so that all of the associated roster items appear offline.  This also sends
     * the unavailable presence for the transport itself.
     *
     * @param jid JID of user whose roster we want to clean up.
     * @throws UserNotFoundException if the user is not found.
     */
    public void notifyRosterOffline(JID jid) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(jid.getNode());
            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().getDomain().equals(this.jid.getDomain())) {
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
        Log.debug(getType().toString()+": Sending packet: "+packet.toXML());
        try {
            this.componentManager.sendPacket(this, packet);
        }
        catch (Exception e) {
            Log.error("Failed to deliver packet: " + packet.toString());
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
        m.setBody(msg);
        if (type.equals(Message.Type.chat) || type.equals(Message.Type.normal)) {
            Element xEvent = m.addChildElement("x", "jabber:x:event");
//            xEvent.addElement("id");
            xEvent.addElement("composing");
            m.addChildElement("active", CHATSTATES);
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

    /**
     * Sends a typing notification the component manager.
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
        Element xEvent = m.addChildElement("x", "jabber:x:event");
        xEvent.addElement("id");
        xEvent.addElement("composing");
        m.addChildElement("composing", CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends a typing paused notification the component manager.
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
        m.addChildElement("paused", CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends an inactive chat session notification the component manager.
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
        m.addChildElement("inactive", CHATSTATES);
        sendPacket(m);
    }

    /**
     * Sends a gone chat session notification the component manager.
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
        m.addChildElement("gone", CHATSTATES);
        sendPacket(m);
    }

    /**
     * Intercepts roster additions related to the gateway and flags them as non-persistent.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#addingContact(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem, boolean)
     */
    public boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
        if (item.getJid().getDomain().equals(this.getJID().toString()) && item.getJid().getNode() != null) {
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
        if (!item.getJid().getDomain().equals(this.getJID().getDomain())) {
            // Not ours, not our problem.
            return;
        }
        if (item.getJid().getNode() == null) {
            // Gateway itself, don't care.
            return;
        }
        try {
            TransportSession session = sessionManager.getSession(roster.getUsername());
            if (!session.isRosterLocked(item.getJid().toString())) {
                Log.debug(getType().toString()+": contactUpdated "+roster.getUsername()+":"+item.getJid());
                session.updateContact(item);
            }
        }
        catch (NotFoundException e) {
            // Well we just don't care then.
        }
    }

    /**
     * Handles additions to a roster.  We don't really care because we hear about these via subscribes.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#contactAdded(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem)
     */
    public void contactAdded(Roster roster, RosterItem item) {
        if (!item.getJid().getDomain().equals(this.getJID().getDomain())) {
            // Not ours, not our problem.
            return;
        }
        if (item.getJid().getNode() == null) {
            // Gateway itself, don't care.
            return;
        }
        try {
            TransportSession session = sessionManager.getSession(roster.getUsername());
            if (!session.isRosterLocked(item.getJid().toString())) {
                Log.debug(getType().toString()+": contactAdded "+roster.getUsername()+":"+item.getJid());
                session.addContact(item);
            }
        }
        catch (NotFoundException e) {
            // Well we just don't care then.
        }
    }

    /**
     * Handles deletions from a roster.  We don't really care because we hear about these via unsubscribes.
     *
     * @see org.jivesoftware.openfire.roster.RosterEventListener#contactDeleted(org.jivesoftware.openfire.roster.Roster, org.jivesoftware.openfire.roster.RosterItem)
     */
    public void contactDeleted(Roster roster, RosterItem item) {
        if (!item.getJid().getDomain().equals(this.getJID().getDomain())) {
            // Not ours, not our problem.
            return;
        }
        if (item.getJid().getNode() == null) {
            // Gateway itself, don't care.
            return;
        }
        try {
            TransportSession session = sessionManager.getSession(roster.getUsername());
            if (!session.isRosterLocked(item.getJid().toString())) {
                Log.debug(getType().toString()+": contactDeleted "+roster.getUsername()+":"+item.getJid());
                session.removeContact(item);
            }
        }
        catch (NotFoundException e) {
            // Well we just don't care then.
        }
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
     * Will handle logging in to the legacy service.
     *
     * @param registration Registration used for log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     * @param priority Priority of the session (from presence packet).
     * @return A session instance for the new login.
     */
    public abstract TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority);

    /**
     * Will handle logging out of the legacy service.
     *
     * @param session TransportSession to be logged out.
     */
    public abstract void registrationLoggedOut(TransportSession session);

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
