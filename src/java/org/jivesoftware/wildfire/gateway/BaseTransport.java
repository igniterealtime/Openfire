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
import java.util.Map;
import java.util.HashMap;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.roster.Roster;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.wildfire.roster.RosterManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.user.UserAlreadyExistsException;
import org.jivesoftware.wildfire.XMPPServer;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError.Condition;
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
    }

    /**
     * Handles initialization of the transport.
     */
    public void initialize(JID jid, ComponentManager componentManager) {
        this.jid = jid;
        this.componentManager = componentManager;
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
     * Manager component for user rosters.
     */
    public final RosterManager rosterManager = new RosterManager();

    /**
     * Type of the transport in question.
     * @see org.jivesoftware.wildfire.gateway.TransportType
     */
    public TransportType transportType = null;

    private final String DISCO_INFO = "http://jabber.org/protocol/disco#info";
    private final String DISCO_ITEMS = "http://jabber.org/protocol/disco#items";
    private final String IQ_GATEWAY = "jabber:iq:gateway";
    private final String IQ_REGISTER = "jabber:iq:register";
    private final String IQ_VERSION = "jabber:iq:version";

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
     */
    private List<Packet> processPacket(Message packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        Log.debug("Got Message packet: " + packet.toString());

        try {
            TransportSession session = sessionManager.getSession(from);
            session.sendMessage(to, packet.getBody());
        }
        catch (NotFoundException e) {
            // TODO: Should return an error packet here
            Log.debug("Unable to find session.");
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

        Log.debug("Got Presence packet: " + packet.toString());

        if (packet.getType() == Presence.Type.error) {
            // We don't want to do anything with this.  Ignore it.
            return reply;
        }

        try {
            if (to.getNode() == null) {
                Log.debug("Presence to gateway");
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (!registrations.iterator().hasNext()) {
                    // User is not registered with us.
                    Log.debug("Unable to find registration.");
                    return reply;
                }
                Registration registration = registrations.iterator().next();
    
                // This packet is to the transport itself.
                if (packet.getType() == null) {
                    // User has come online.
                    Log.debug("Got available.");
                    TransportSession session = null;
                    try {
                        session = sessionManager.getSession(from);

                        // Well, this could represent a status change.
                        session.updateStatus(getPresenceType(packet), packet.getStatus());
                    }
                    catch (NotFoundException e) {
                        session = this.registrationLoggedIn(registration, from, getPresenceType(packet), packet.getStatus());
                        //sessionManager.storeSession(registration.getJID(), session);
                        sessionManager.storeSession(from, session);
                    }
                }
                else if (packet.getType() == Presence.Type.unavailable) {
                    // User has gone offline.
                    Log.debug("Got unavailable.");
                    TransportSession session = null;
                    try {
                        session = sessionManager.getSession(from);
                        if (session.isLoggedIn()) {
                            this.registrationLoggedOut(session);
                        }

                        //sessionManager.removeSession(registration.getJID());
                        sessionManager.removeSession(from);
                    }
                    catch (NotFoundException e) {
                        Log.debug("Ignoring unavailable presence for inactive seession.");
                    }
                }
                else if (packet.getType() == Presence.Type.probe) {
                    // Client is asking for presence status.
                    Log.debug("Got probe.");
                    TransportSession session = null;
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
                    Log.debug("Ignoring this packet.");
                    // Anything else we will ignore for now.
                }
            }
            else {
                Log.debug("Presence to user at gateway");
                // This packet is to a user at the transport.
                try {
                    TransportSession session = sessionManager.getSession(from);
                
                    if (packet.getType() == Presence.Type.probe) {
                        // Presence probe, lets try to tell them.
                        session.retrieveContactStatus(packet.getTo());
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
     */
    private List<Packet> processPacket(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        Log.debug("Got IQ packet: " + packet.toString());

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
            // TODO: Should we return an error?
            Log.debug("No XMLNS");
            return reply;
        }

        if (xmlns.equals(DISCO_INFO)) {
            Log.debug("Matched Disco Info");
            reply.addAll(handleDiscoInfo(packet));
        }
        else if (xmlns.equals(DISCO_ITEMS)) {
            Log.debug("Matched Disco Items");
            reply.addAll(handleDiscoItems(packet));
        }
        else if (xmlns.equals(IQ_GATEWAY)) {
            Log.debug("Matched IQ Gateway");
            reply.addAll(handleIQGateway(packet));
        }
        else if (xmlns.equals(IQ_REGISTER)) {
            Log.debug("Matched IQ Register");
            reply.addAll(handleIQRegister(packet));
        }
        else if (xmlns.equals(IQ_VERSION)) {
            Log.debug("Matched IQ Version");
            reply.addAll(handleIQVersion(packet));
        }
        else {
            Log.debug("Matched nothing");
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
            query.addElement("desc").addText("Please enter the person's "+this.getName()+" username.");
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

        Element remove = packet.getChildElement().element("remove");
        if (remove != null) {
            // User wants to unregister.  =(
            // this.convinceNotToLeave() ... kidding.
            IQ result = IQ.createResultIQ(packet);

            Collection<Registration> registrations = registrationManager.getRegistrations(packet.getFrom(), this.transportType);
            // For now, we're going to have to just nuke all of these.  Sorry.
            for (Registration reg : registrations) {
                registrationManager.deleteRegistration(reg);
            }

            // Tell the end user the transport went byebye.
            Presence unavailable = new Presence(Presence.Type.unavailable);
            unavailable.setTo(packet.getFrom());
            unavailable.setFrom(packet.getTo());
            reply.add(unavailable);

            // Clean up the user's contact list.
            try {
                Roster roster = rosterManager.getRoster(packet.getFrom().getNode());
                for (RosterItem ri : roster.getRosterItems()) {
                    if (ri.getJid().getDomain() == this.jid.getDomain()) {
                        try {
                            roster.deleteRosterItem(ri.getJid(), false);
                        }
                        catch (Exception e) {
                            Log.error("Error removing roster item: " + ri.toString());
                        }
                    }
                }
            }
            catch (UserNotFoundException e) {
                Log.error("Error cleaning up contact list of: " + packet.getFrom());
                result.setError(Condition.bad_request);
            }

            reply.add(result);
        }
        else {
            // User wants to register with the transport.
            String username = null;
            String password = null;

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
                }
            }
            catch (Exception e) {
                // No with data form apparantly
            }

            if (packet.getType() == IQ.Type.set) {
                Element userEl = packet.getChildElement().element("username");
                Element passEl = packet.getChildElement().element("password");

                if (userEl != null) {
                    username = userEl.getTextTrim();
                }

                if (passEl != null) {
                    password = passEl.getTextTrim();
                }

                if (username == null || password == null) {
                    // Found nothing from stanza, lets yell.
                    IQ result = IQ.createResultIQ(packet);
                    result.setError(Condition.bad_request);
                    reply.add(result);
                }
                else {
                    Log.info("Registered " + packet.getFrom() + " as " + username);
                    IQ result = IQ.createResultIQ(packet);
                    Element response = DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
                    result.setChildElement(response);
                    reply.add(result);

                    Collection<Registration> registrations = registrationManager.getRegistrations(packet.getFrom(), this.transportType);
                    Boolean foundReg = false;
                    for (Registration registration : registrations) {
                        if (!registration.getUsername().equals(username)) {
                            registrationManager.deleteRegistration(registration);
                        }
                        else {
                            registration.setPassword(password);
                            foundReg = true;
                        }
                    }

                    if (!foundReg) {
                        registrationManager.createRegistration(packet.getFrom(), this.transportType, username, password);
                    }

                    try {
                        addOrUpdateRosterItem(packet.getFrom(), packet.getTo(), this.getDescription(), "Transports");
                    }
                    catch (UserNotFoundException e) {
                        Log.error("Someone attempted to register with the gateway who is not registered with the server: " + packet.getFrom());
                        IQ eresult = IQ.createResultIQ(packet);
                        eresult.setError(Condition.bad_request);
                        reply.add(eresult);
                    }

                    // Lets ask them what their presence is, maybe log
                    // them in immediately.
                    Presence p = new Presence(Presence.Type.probe);
                    p.setTo(packet.getFrom());
                    p.setFrom(packet.getTo());
                    reply.add(p);
                }
            }
            else if (packet.getType() == IQ.Type.get) {
                Element response = DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
                IQ result = IQ.createResultIQ(packet);

                DataForm form = new DataForm(DataForm.Type.form);
                form.addInstruction("Please enter your " + this.getName() + " username and password.");

                FormField usernameField = form.addField();
                usernameField.setLabel("Username");
                usernameField.setVariable("username");
                usernameField.setType(FormField.Type.text_single);

                FormField passwordField = form.addField();
                passwordField.setLabel("Password");
                passwordField.setVariable("password");
                passwordField.setType(FormField.Type.text_private);

                response.addElement("instructions").addText("Please enter your " + this.getName() + " username and password.");
                response.addElement("username");
                response.addElement("password");

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
            query.addElement("name").addText("Wildfire " + this.getDescription());
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
        return new JID(username.replace('@', '%'), this.jid.getDomain(), null);
    }

    /**
     * Converts a JID to a legacy username.
     *
     * @param jid JID to be converted to a legacy username.
     * @return THe legacy username as a String.
     */
    public String convertJIDToID(JID jid) {
        return jid.getNode().replace('%', '@');
    }

    /**
     * Gets an easy to use presence type from a presence packet.
     *
     * @param packet A presence packet from which the type will be pulled.
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
     * Returns the jid of the transport.
     */
    public JID getJID() {
        return this.jid;
    }

    /**
     * Returns the name (type) of the transport.
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
     * Retains the version string for later requests.
     */
    private String versionString = null;

    /**
     * Returns the version string of the gateway.
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
     * @param group Group item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, String group) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());
            try {
                RosterItem gwitem = roster.getRosterItem(contactjid);
                Boolean changed = false;
                if (gwitem.getSubStatus() != RosterItem.SUB_BOTH) {
                    gwitem.setSubStatus(RosterItem.SUB_BOTH);
                    changed = true;
                }
                if (gwitem.getAskStatus() != RosterItem.ASK_NONE) {
                    gwitem.setAskStatus(RosterItem.ASK_NONE);
                    changed = true;
                }
                if (!gwitem.getNickname().equals(nickname)) {
                    gwitem.setNickname(nickname);
                    changed = true;
                }
                List<String> curgroups = gwitem.getGroups();
                List<String> groups = new ArrayList<String>();
                groups.add(group);
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
                    RosterItem gwitem = roster.createRosterItem(contactjid, true);
                    gwitem.setSubStatus(RosterItem.SUB_BOTH);
                    gwitem.setAskStatus(RosterItem.ASK_NONE);
                    gwitem.setNickname(nickname);
                    List<String> groups = new ArrayList<String>();
                    groups.add(group);
                    try {
                        gwitem.setGroups(groups);
                    }
                    catch (Exception ee) {
                        // Oooookay, ignore then.
                    }
                    roster.updateRosterItem(gwitem);
                }
                catch (UserAlreadyExistsException ee) {
                    Log.error("getRosterItem claims user exists, but couldn't find via getRosterItem?");
                    // TODO: Should we throw exception or something?
                }
                catch (Exception ee) {
                    Log.error("createRosterItem caused exception: " + ee.getMessage());
                    // TODO: Should we throw exception or something?
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
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
            addOrUpdateRosterItem(userjid, new JID(contactid, this.jid.toBareJID(), null), nickname, group);
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
    void removeFromRoster(JID userjid, JID contactjid) throws UserNotFoundException {
        // Clean up the user's contact list.
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());
            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().toBareJID().equals(contactjid.toBareJID())) {
                    try {
                        roster.deleteRosterItem(ri.getJid(), false);
                    }
                    catch (Exception e) {
                        Log.error("Error removing roster item: " + ri.toString());
                        // TODO: Should we say something?
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
            removeFromRoster(userjid, new JID(contactid, this.jid.toBareJID(), null));
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

            // First thing first, we want to build ourselves an easy mapping.
            Map<JID,TransportBuddy> legacymap = new HashMap<JID,TransportBuddy>();
            for (TransportBuddy buddy : legacyitems) {
                Log.debug("ROSTERSYNC: Mapping "+buddy.getName());
                legacymap.put(new JID(buddy.getName(), this.jid.getDomain(), null), buddy);
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
                        this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroup());
                    }
                    catch (UserNotFoundException e) {
                        // TODO: Something is quite wrong if we see this.
                        Log.error("Failed updating roster item");
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
                        // TODO: Something is quite wrong if we see this.
                        Log.error("Failed removing roster item");
                    }
                }
            }

            // Ok, we should now have only new items from the legacy roster
            for (TransportBuddy buddy : legacymap.values()) {
                Log.debug("ROSTERSYNC: We have new, adding " + buddy.getName());
                try {
                    this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroup());
                }
                catch (UserNotFoundException e) {
                    // TODO: Something is quite wrong if we see this.
                    Log.error("Failed adding new roster item");
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
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
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     * @return A session instance for the new login.
     */
    public abstract TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus);

    /**
     * Will handle logging out of the legacy service.
     *
     * @param session TransportSession to be logged out.
     */
    public abstract void registrationLoggedOut(TransportSession session);

}
