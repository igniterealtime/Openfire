/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire;

import org.jivesoftware.wildfire.multiplex.UnknownStanzaException;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.wildfire.net.SASLAuthentication;
import org.xmpp.packet.*;
import org.dom4j.Element;

import java.io.UnsupportedEncodingException;

/**
 * Handles the routing of packets to a particular session. It will invoke all of the appropriate
 * interceptors, before and after having the server process the message.
 *
 * @author Alexander Wenckus
 */
public class SessionPacketRouter implements PacketRouter {

    private ClientSession session;
    private PacketRouter router;
    private SessionManager sessionManager;

    public SessionPacketRouter() {
        this(null);
    }

    public SessionPacketRouter(ClientSession session) {
        this.session = session;
        router = XMPPServer.getInstance().getPacketRouter();
        sessionManager = SessionManager.getInstance();
    }

    public void route(Element wrappedElement)
            throws UnsupportedEncodingException, UnknownStanzaException {
        String tag = wrappedElement.getName();
        if ("auth".equals(tag) || "response".equals(tag)) {
            SASLAuthentication.handle(session, wrappedElement);
        }
        else if ("iq".equals(tag)) {
            route(getIQ(wrappedElement));
        }
        else if ("message".equals(tag)) {
            route(new Message(wrappedElement));
        }
        else if ("presence".equals(tag)) {
            route(new Presence(wrappedElement));
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
            return new IQ(doc);
        }
    }

    public void route(Packet packet) {
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

    public void route(IQ packet) {
        if(session == null) {
            session = sessionManager.getSession(packet.getFrom());
            if(session == null) {
                router.route(packet);
            }
        }
        packet.setFrom(session.getAddress());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet so answer a not_allowed error
            IQ reply = new IQ();
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setID(packet.getID());
            reply.setTo(session.getAddress());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_allowed);
            session.process(reply);
            // Check if a message notifying the rejection should be sent
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message notification = new Message();
                notification.setTo(session.getAddress());
                notification.setFrom(packet.getTo());
                notification.setBody(e.getRejectionMessage());
                session.process(notification);
            }
        }
    }

    public void route(Message packet) {
        if(session == null) {
            session = sessionManager.getSession(packet.getFrom());
            if(session == null) {
                router.route(packet);
            }
        }
        packet.setFrom(session.getAddress());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message reply = new Message();
                reply.setID(packet.getID());
                reply.setTo(session.getAddress());
                reply.setFrom(packet.getTo());
                reply.setType(packet.getType());
                reply.setThread(packet.getThread());
                reply.setBody(e.getRejectionMessage());
                session.process(reply);
            }
        }
    }

    public void route(Presence packet) {
        if(session == null) {
            session = sessionManager.getSession(packet.getFrom());
            if(session == null) {
                router.route(packet);
            }
        }
        packet.setFrom(session.getAddress());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet so answer a not_allowed error
            Presence reply = new Presence();
            reply.setID(packet.getID());
            reply.setTo(session.getAddress());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_allowed);
            session.process(reply);
            // Check if a message notifying the rejection should be sent
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message notification = new Message();
                notification.setTo(session.getAddress());
                notification.setFrom(packet.getTo());
                notification.setBody(e.getRejectionMessage());
                session.process(notification);
            }
        }
    }
}
