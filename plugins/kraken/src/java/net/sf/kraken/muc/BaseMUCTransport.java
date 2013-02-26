/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.muc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.NameSpace;

import org.apache.log4j.Logger;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.packet.PacketError.Condition;

/**
 * Base class for all MUC transports.
 *
 * This class should be implemented to provide support for MUC in a transport.  It
 * is attached to a BaseTransport implementation and the two work together to handle
 * session management and such.  Generally a good chunk of the work will still be done
 * by the BaseTransport based pieces.
 *
 * @author Daniel Henninger
 */
public abstract class BaseMUCTransport<B extends TransportBuddy> implements Component {

    static Logger Log = Logger.getLogger(BaseMUCTransport.class);

    /**
     * Create a new BaseMUCTransport instance.
     *
     * @param transport Transport to associate with this MUC transport.
     */
    public BaseMUCTransport(BaseTransport<B> transport) {
        this.transport = transport;
        requestWatcher = new RequestWatcher();
        timer.schedule(requestWatcher, requestCheckInterval, requestCheckInterval);
    }

    /* The transport we are associated with. */
    public BaseTransport<B> transport;

    /**
     * Retrieves the transport we are associated with.
     *
     * @return Transport we are associated with.
     */
    public BaseTransport<B> getTransport() {
        return transport;
    }

    /**
     * Retrieves the name of the MUC transport.
     * @see org.xmpp.component.Component#getName()
     */
    public String getName() {
        return getTransport().getName();
    }

    /**
     * Retrieves the description of the MUC transport.
     * @see org.xmpp.component.Component#getDescription()
     */
    public String getDescription() {
        return getTransport().getDescription();
    }

    /**
     * Returns the jid of the MUC transport.
     *
     * @return the jid of the MUC transport.
     */
    public JID getJID() {
        return this.jid;
    }

    /* List of IQ requests that are waiting on a response. */
    ConcurrentHashMap<IQ,Date> pendingIQRequests = new ConcurrentHashMap<IQ,Date>();

    /**
     * Stores a pending request.
     *
     * @param packet IQ packet that we are storing.
     */
    public void storePendingRequest(IQ packet) {
        pendingIQRequests.put(packet, new Date());
    }

    /**
     * Retrieves a pending request or null if no such request is found.
     *
     * @param from JID that the request was originally sent from.
     * @param to JID that the request was originally sent to.
     * @param namespace IQ namespace to identify request.
     * @return Matching pending request or null.
     */
    public IQ getPendingRequest(JID from, JID to, String namespace) {
        for (IQ request : pendingIQRequests.keySet()) {
            if (request.getTo().equals(to) && request.getFrom().toBareJID().equals(from.toBareJID())) {
                Element child = request.getChildElement();
                if (child != null) {
                    String xmlns = child.getNamespaceURI();
                    if (xmlns.equals(namespace)) {
                        pendingIQRequests.remove(request);
                        return request;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Retires a pending request.
     *
     * @param from JID that the request was originally sent from.
     * @param to JID that the request was originally sent to.
     * @param namespace IQ namespace to identify request.
     */
    public void cancelPendingRequest(JID from, JID to, String namespace) {
        for (IQ request : pendingIQRequests.keySet()) {
            if (request.getTo().equals(to) && request.getFrom().toBareJID().equals(from.toBareJID())) {
                Element child = request.getChildElement();
                if (child != null) {
                    String xmlns = child.getNamespaceURI();
                    if (xmlns.equals(namespace)) {
                        IQ result = IQ.createResultIQ(request);
                        result.setError(PacketError.Condition.item_not_found);
                        getTransport().sendPacket(result);
                        pendingIQRequests.remove(request);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Expires a pending request.
     *
     * @param request The request that will be expired
     */
    public void expirePendingRequest(IQ request) {
        IQ result = IQ.createResultIQ(request);
        result.setError(PacketError.Condition.remote_server_timeout);
        getTransport().sendPacket(result);
        pendingIQRequests.remove(request);
    }

    /**
     * Expires all pending requests that have timed out.
     */
    public void checkPendingExpirations() {
        Date now = new Date();
        now.setTime(now.getTime() - requestTimeout);
        for (IQ request : pendingIQRequests.keySet()) {
            Date when = pendingIQRequests.get(request);
            if (now.after(when)) {
                expirePendingRequest(request);
            }
        }
    }

    /**
     * Timer to check for IQ request expirations.
     */
    private Timer timer = new Timer();

    /**
     * Interval at which requests are checked.
     */
    private int requestCheckInterval = 10000; // 10 seconds

    /**
     * How long before requests time out.
     */
    private int requestTimeout = 30000; // 30 seconds

    /**
     * The actual request checker task.
     */
    private RequestWatcher requestWatcher;

    /**
     * Check for expired IQ requests.
     */
    private class RequestWatcher extends TimerTask {
        /**
         * Expire any requests that have timed out.
         */
        @Override
        public void run() {
            checkPendingExpirations();
        }
    }

    /* Cached list of rooms from IRC */
    final public ConcurrentHashMap<String,MUCTransportRoom> roomCache = new ConcurrentHashMap<String,MUCTransportRoom>();

    /* Last room list update */
    public Date roomCacheLastUpdate = null;

    /* How long before the room cache is considered expired. */
    private int roomCacheTimeout = 600000; // 10 minutes

    /**
     * Updates the room cache update timestamp.
     */
    public void updateRoomCacheTimestamp() {
        roomCacheLastUpdate = new Date();
    }

    /**
     * Resets (clears) the room cache.
     */
    public void clearRoomCache() {
        roomCache.clear();
    }

    /**
     * Retrieve cached information about a room.
     *
     * @param room Name of room to retrieve information about.
     * @return MUCTransportRoom instance for room.
     */
    public MUCTransportRoom getCachedRoom(String room) {
        return roomCache.get(room.toLowerCase());
    }

    /**
     * Stores an entry in the room cache.
     *
     * @param room MUCTransportRoom instance that we'll be storing.
     */
    public void cacheRoom(MUCTransportRoom room) {
        roomCache.put(room.getName().toLowerCase(), room);
    }

    /**
     * Retrieves a list of all cached rooms.
     *
     * @return Collection of all cached rooms.
     */
    public Collection<MUCTransportRoom> getCachedRooms() {
        return roomCache.values();
    }

    /**
     * Returns whether we need to trigger a list update.
     *
     * @return True or false if we have passed the timeout period.
     */
    public Boolean isRoomCacheOutOfDate() {
        Date timeout = new Date();
        timeout.setTime(timeout.getTime() - roomCacheTimeout);
        return (roomCacheLastUpdate == null || roomCacheLastUpdate.before(timeout));
    }

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

        try {
            TransportSession<B> session = getTransport().getSessionManager().getSession(from);
            if (!session.isLoggedIn()) {
                Message m = new Message();
                m.setError(Condition.service_unavailable);
                m.setTo(from);
                m.setFrom(getJID());
                m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "kraken", Arrays.asList(getTransport().getType().toString().toUpperCase())));
                reply.add(m);
            }
            else if (to.getNode() == null) {
                // Message to gateway itself.  Throw away for now.
            }
            else {
                MUCTransportSession mucSession = session.getMUCSessionManager().getSession(to.getNode());
                if (packet.getBody() != null) {
                    if (to.getResource() == null) {
                        // Message to room
                        mucSession.sendMessage(packet.getBody());
                    }
                    else {
                        // Private message
                        mucSession.sendPrivateMessage(to.getResource(), packet.getBody());
                    }
                }
                else {
                    if (packet.getSubject() != null) {
                        // Set topic of room
                        mucSession.updateTopic(packet.getSubject());
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
            m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "kraken", Arrays.asList(getTransport().getType().toString().toUpperCase())));
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
            TransportSession<B> session = getTransport().getSessionManager().getSession(from);
            if (!session.isLoggedIn()) {
                Message m = new Message();
                m.setError(Condition.service_unavailable);
                m.setTo(from);
                m.setFrom(getJID());
                m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "kraken", Arrays.asList(getTransport().getType().toString().toUpperCase())));
                reply.add(m);
            }
            else if (to.getNode() == null) {
                // Ignore undirected presence.
            }
            else if (to.getResource() != null) {
                // Presence to a specific resource.
                if (packet.getType() == Presence.Type.unavailable) {
                    // Handle logout.
                    try {
                        MUCTransportSession mucSession = session.getMUCSessionManager().getSession(to.getNode());
                        mucSession.leaveRoom();
                        session.getMUCSessionManager().removeSession(to.getNode());
                    }
                    catch (NotFoundException e) {
                        // Not found?  Well then no problem.
                    }
                }
                else {
                    // Handle login.
                    try {
                        MUCTransportSession mucSession = session.getMUCSessionManager().getSession(to.getNode());
                        // Active session.
                        mucSession.updateStatus(this.getTransport().getPresenceType(packet));
                    }
                    catch (NotFoundException e) {
                        // No current session, lets create one.
                        MUCTransportSession mucSession = createRoom(session, to.getNode(), to.getResource());
                        session.getMUCSessionManager().storeSession(to.getNode(), mucSession);
                        mucSession.enterRoom();
                    }
                }
            }
            else {
                // Presence to the room itself.  Return error as per protocol.
                Presence p = new Presence();
                p.setError(Condition.jid_malformed);
                p.setType(Presence.Type.error);
                p.setTo(from);
                p.setFrom(to);
                reply.add(p);

            }
        }
        catch (NotFoundException e) {
            Log.debug("Unable to find session.");
            Message m = new Message();
            m.setError(Condition.service_unavailable);
            m.setTo(from);
            m.setFrom(getJID());
            m.setBody(LocaleUtils.getLocalizedString("gateway.base.notloggedin", "kraken", Arrays.asList(getTransport().getType().toString().toUpperCase())));
            reply.add(m);
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
            error.setError(PacketError.Condition.bad_request);
            reply.add(error);
            return reply;
        }

        if (xmlns.equals(NameSpace.DISCO_INFO)) {
            reply.addAll(handleDiscoInfo(packet));
        }
        else if (xmlns.equals(NameSpace.DISCO_ITEMS)) {
            reply.addAll(handleDiscoItems(packet));
        }
        else if (xmlns.equals(NameSpace.MUC_ADMIN)) {
            reply.addAll(handleMUCAdmin(packet));
        }
        else if (xmlns.equals(NameSpace.MUC_USER)) {
            reply.addAll(handleMUCUser(packet));
        }
        else {
            Log.debug("Unable to handle iq request: " + xmlns);
            IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.service_unavailable);
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
        JID to = packet.getTo();

        if (packet.getTo().getNode() == null) {
            // Requested info from transport itself.
            IQ result = IQ.createResultIQ(packet);
            if (from.getNode() == null || getTransport().permissionManager.hasAccess(from)) {
                Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_INFO));
                response.addElement("identity")
                        .addAttribute("category", "conference")
                        .addAttribute("type", "text")
                        .addAttribute("name", this.getDescription());
                response.addElement("feature")
                        .addAttribute("var", NameSpace.DISCO_INFO);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.DISCO_ITEMS);
                response.addElement("feature")
                        .addAttribute("var", NameSpace.MUC);
                result.setChildElement(response);
            }
            else {
                result.setError(PacketError.Condition.forbidden);
            }
            reply.add(result);
        }
        else {
            // Ah, a request for information about a room.
            IQ result = IQ.createResultIQ(packet);
            try {
                TransportSession<B> session = getTransport().getSessionManager().getSession(from);
                if (session.isLoggedIn()) {
                    storePendingRequest(packet);
                    session.getRoomInfo(getTransport().convertJIDToID(to));
                }
                else {
                    // Not logged in?  Not logged in then.
                    result.setError(PacketError.Condition.forbidden);
                    reply.add(result);
                }
            }
            catch (NotFoundException e) {
                // Not found?  No active session then.
                result.setError(PacketError.Condition.forbidden);
                reply.add(result);
            }
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
        JID from = packet.getFrom();
        JID to = packet.getTo();

        if (packet.getTo().getNode() == null) {
            // A request for a list of rooms
            IQ result = IQ.createResultIQ(packet);
            if (JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".roomlist", false)) {
                try {
                    TransportSession<B> session = getTransport().getSessionManager().getSession(from);
                    if (session.isLoggedIn()) {
                        storePendingRequest(packet);
                        session.getRooms();
                    }
                }
                catch (NotFoundException e) {
                    // Not found?  No active session then.
                    result.setError(PacketError.Condition.forbidden);
                    reply.add(result);
                }
            }
            else {
                 // Time to lie and tell them we have no rooms
                sendRooms(from, new ArrayList<MUCTransportRoom>());
            }
        }
        else {
            // Ah, a request for members of a room.
            IQ result = IQ.createResultIQ(packet);
            try {
                TransportSession<B> session = getTransport().getSessionManager().getSession(from);
                if (session.isLoggedIn()) {
                    storePendingRequest(packet);
                    session.getRoomMembers(getTransport().convertJIDToID(to));
                }
                else {
                    // Not logged in?  Not logged in then.
                    result.setError(PacketError.Condition.forbidden);
                    reply.add(result);
                }
            }
            catch (NotFoundException e) {
                // Not found?  No active session then.
                result.setError(PacketError.Condition.forbidden);
                reply.add(result);
            }
        }
        return reply;
    }

    /**
     * Handle MUC admin requests.
     *
     * @param packet An IQ packet in the MUC admin namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleMUCAdmin(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        Element query = (packet).getChildElement();
        Element item = query.element("item");
        String nick = item.attribute("nick").getText();
        String role = item.attribute("role").getText();

        try {
            TransportSession<B> session = getTransport().getSessionManager().getSession(from);
            if (session.isLoggedIn()) {
                try {
                    MUCTransportSession mucSession = session.getMUCSessionManager().getSession(to.getNode());
                    if (packet.getTo().getNode() == null) {
                        // Targeted at a room.
                    }
                    else {
                        // Targeted at a specific user.
                        if (nick != null && role != null) {
                            if (role.equals("none")) {
                                // This is a kick
                                String reason = "";
                                Element reasonElem = item.element("reason");
                                if (reasonElem != null) {
                                    reason = reasonElem.getText();
                                }
                                mucSession.kickUser(nick, reason);
                            }
                        }
                    }
                }
                catch (NotFoundException e) {
                    // Not found?  No active session then.
                }
            }
        }
        catch (NotFoundException e) {
            // Not found?  No active session then.
        }
        
        return reply;
    }

    /**
     * Handle MUC user requests.
     *
     * @param packet An IQ packet in the MUC admin namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleMUCUser(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
//        JID from = packet.getFrom();
//        JID to = packet.getTo();

        if (packet.getTo().getNode() == null) {
            // Targeted at a room.
        }
        else {
            // Targeted at a specific user.
        }
        
        return reply;
    }

    /**
     * Sends a list of rooms as a response to a service discovery request.
     *
     * @param to JID we will be sending the response to.
     * @param rooms List of MUCTransportRoom objects to send as a response.
     */
    public void sendRooms(JID to, Collection<MUCTransportRoom> rooms) {
        IQ request = getPendingRequest(to, this.getJID(), NameSpace.DISCO_ITEMS);
        if (request != null) {
            IQ result = IQ.createResultIQ(request);
            Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_ITEMS));
            for (MUCTransportRoom room : rooms) {
                Element item = response.addElement("item");
                item.addAttribute("jid", room.getJid().toBareJID());
                item.addAttribute("name", room.getName());
            }
            result.setChildElement(response);
            this.sendPacket(result);
        }
    }

    /**
     * Sends information about a room as a response to a service discovery request.
     *
     * @param to JID we will be sending the response to.
     * @param roomjid JID of the room info was requested about.
     * @param room A MUCTransportRoom object containing information to return as a response.
     */
    public void sendRoomInfo(JID to, JID roomjid, MUCTransportRoom room) {
        IQ request = getPendingRequest(to, roomjid, NameSpace.DISCO_INFO);
        if (request != null) {
            IQ result = IQ.createResultIQ(request);
            Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_INFO));
            response.addElement("identity")
                    .addAttribute("category", "conference")
                    .addAttribute("type", "text")
                    .addAttribute("name", room.getName());
            response.addElement("feature")
                    .addAttribute("var", NameSpace.MUC);
            response.addElement("feature")
                    .addAttribute("var", NameSpace.DISCO_INFO);
            response.addElement("feature")
                    .addAttribute("var", NameSpace.DISCO_ITEMS);
            if (room.getPassword_protected()) {
                response.addElement("feature")
                        .addAttribute("var", "muc_passwordprotected");
            }
            if (room.getHidden()) {
                response.addElement("feature")
                        .addAttribute("var", "muc_hidden");
            }
            if (room.getTemporary()) {
                response.addElement("feature")
                        .addAttribute("var", "muc_temporary");
            }
            if (room.getOpen()) {
                response.addElement("feature")
                        .addAttribute("var", "muc_open");
            }
            if (!room.getModerated()) {
                response.addElement("feature")
                        .addAttribute("var", "muc_unmoderated");
            }
            if (!room.getAnonymous()) {
                response.addElement("feature")
                        .addAttribute("var", "muc_nonanonymous");
            }
            Element form = DocumentHelper.createElement(QName.get("x", NameSpace.XDATA));
            form.addAttribute("type", "result");
            form.addElement("field")
                .addAttribute("var", "FORM_TYPE")
                .addAttribute("type", "hidden")
                .addElement("value")
                .addCDATA("http://jabber.org/protocol/muc#roominfo");
            if (room.getContact() != null) {
                form.addElement("field")
                    .addAttribute("var", "muc#roominfo_contactjid")
                    .addAttribute("label", "Contact Addresses")
                    .addElement("value")
                    .addCDATA(room.getContact().toString());
            }
            if (room.getName() != null) {
                form.addElement("field")
                    .addAttribute("var", "muc#roominfo_description")
                    .addAttribute("label", "Short Description of Room")
                    .addElement("value")
                    .addCDATA(room.getName());
            }
            if (room.getLanguage() != null) {
                form.addElement("field")
                    .addAttribute("var", "muc#roominfo_lang")
                    .addAttribute("label", "Natural Language for Room Discussions")
                    .addElement("value")
                    .addCDATA(room.getLanguage());
            }
            if (room.getLog_location() != null) {
                form.addElement("field")
                    .addAttribute("var", "muc#roominfo_logs")
                    .addAttribute("label", "URL for Archived Discussion Logs")
                    .addElement("value")
                    .addCDATA(room.getLog_location());
            }
            if (room.getOccupant_count() != null) {
                form.addElement("field")
                    .addAttribute("var", "muc#roominfo_occupants")
                    .addAttribute("label", "Current Number of Occupants in Room")
                    .addElement("value")
                    .addCDATA(room.getOccupant_count().toString());
            }
            if (room.getTopic() != null) {
                form.addElement("field")
                    .addAttribute("var", "muc#roominfo_subject")
                    .addAttribute("label", "Current Subject or Discussion Topic in Room")
                    .addElement("value")
                    .addCDATA(room.getTopic());
            }
            response.add(form);
            result.setChildElement(response);
            this.sendPacket(result);
        }
    }

    /**
     * Sends a list of rooms as a response to a service discovery request.
     *
     * @param to JID we will be sending the response to.
     * @param roomjid JID of the room info was requested about.
     * @param members List of MUCTransportRoomMember objects to send as a response.
     */
    public void sendRoomMembers(JID to, JID roomjid, List<MUCTransportRoomMember> members) {
        IQ request = getPendingRequest(to, roomjid, NameSpace.DISCO_ITEMS);
        if (request != null) {
            IQ result = IQ.createResultIQ(request);
            Element response = DocumentHelper.createElement(QName.get("query", NameSpace.DISCO_ITEMS));
            for (MUCTransportRoomMember member : members) {
                Element item = response.addElement("item");
                item.addAttribute("jid", member.getJid().toBareJID());
            }
            result.setChildElement(response);
            this.sendPacket(result);
        }
    }

    /**
     * Sends a packet through the component manager as the component.
     *
     * @param packet Packet to be sent.
     */
    public void sendPacket(Packet packet) {
        Log.debug(getName()+": Sending packet: "+packet.toXML());
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
        m.setBody(msg);
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
     * Initializes the MUC transport.
     * @see org.xmpp.component.Component#initialize(org.xmpp.packet.JID, org.xmpp.component.ComponentManager)
     */
    public void initialize(JID jid, ComponentManager componentManager) {
        this.jid = jid;
        this.componentManager = componentManager;
    }

    /**
     * JID of the transport in question.
     */
    public JID jid = null;

    /**
     * Component Manager associated with transport.
     */
    public ComponentManager componentManager = null;

    /**
     * Starts the MUC transport.
     * @see org.xmpp.component.Component#start()
     */
    public void start() {
    }

    /**
     * Stops the MUC transport.
     * @see org.xmpp.component.Component#shutdown()
     */
    public void shutdown() {
        requestWatcher.cancel();
        timer.cancel();
    }

    /**
     * Converts a legacy chatroom (and optional username) to a JID.
     *
     * @param roomname Name of room to be converted. 
     * @param username Username to be converted to a JID.
     * @return The legacy username as a JID.
     */
    public JID convertIDToJID(String roomname, String username) {
        if (JiveGlobals.getBooleanProperty("plugin.gateway.tweak.percenthack", false)) {
            return new JID(roomname.replace('@', '%').replace(" ", ""), this.jid.getDomain(), username);
        }
        else {
            return new JID(JID.escapeNode(roomname.replace(" ", "")), this.jid.getDomain(), username);
        }
    }

    /**
     * Handles creation of a connection to a room.
     *
     * This is expected to create the connection to a remote chat room and return the session
     * that will maintain it.
     *
     * @param transportSession Transport session we are attaching to.
     * @param roomname Name of room to connect to.
     * @param nickname Nickname to use in room.
     * @return Session instance that will handle the room interactions.
     */
    public abstract MUCTransportSession createRoom(TransportSession<B> transportSession, String roomname, String nickname);
    
}
