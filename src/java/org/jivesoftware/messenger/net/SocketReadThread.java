/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketException;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.XPPPacketReader;
import org.jivesoftware.messenger.Connection;
import org.jivesoftware.messenger.PacketRouter;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.audit.Auditor;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Roster;

/**
 * Reads XMPP XML from a socket.
 *
 * @author Derek DeMoro
 */
public class SocketReadThread extends Thread {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    private static String CHARSET = "UTF-8";

    private static final String ETHERX_NAMESPACE = "http://etherx.jabber.org/streams";

    private Socket sock;
    private Session session;
    private Connection connection;
    private String serverName;
    /**
     * Router used to route incoming packets to the correct channels.
     */
    private PacketRouter router;
    /**
     * Audits incoming data
     */
    private Auditor auditor;
    private boolean clearSignout = false;
    XmlPullParserFactory factory = null;
    XPPPacketReader reader = null;

    /**
     * Create dedicated read thread for this socket.
     *
     * @param router     The router for sending packets that were read
     * @param serverName The name of the server this socket is working for
     * @param auditor    The audit manager that will audit incoming packets
     * @param sock       The socket to read from
     * @param session    The session being read
     */
    public SocketReadThread(PacketRouter router, String serverName, Auditor auditor, Socket sock,
                            Session session) {
        super("SRT reader");
        this.serverName = serverName;
        this.router = router;
        this.auditor = auditor;
        this.session = session;
        connection = session.getConnection();
        this.sock = sock;
    }

    /**
     * A dedicated thread loop for reading the stream and sending incoming
     * packets to the appropriate router.
     */
    public void run() {
        try {
            factory = XmlPullParserFactory.newInstance();
            // factory.setNamespaceAware(true);

            reader = new XPPPacketReader();
            reader.setXPPFactory(factory);

            reader.getXPPParser().setInput(new InputStreamReader(sock.getInputStream(),
                    CHARSET));

            // Read in the opening tag and prepare for packet stream
            createSession();

            // Read the packet stream until it ends
            if (session != null) {
                readStream();
            }

        }
        catch (EOFException eof) {
            // Normal disconnect
        }
        catch (SocketException se) {
            // The socket was closed. The server may close the connection for several reasons (e.g.
            // user requested to remove his account). Do nothing here. 
        }
        catch (XmlPullParserException ie) {
            // Check if the user abruptly cut the connection without sending previously an
            // unavailable presence
            if (clearSignout == false) {
                if (session != null && session.getStatus() == Session.STATUS_AUTHENTICATED) {
                    Presence presence = session.getPresence();
                    if (presence != null) {
                        // Simulate an unavailable presence sent by the user.
                        Presence packet = presence.createCopy();
                        packet.setType(Presence.Type.unavailable);
                        packet.setFrom(session.getAddress());
                        router.route(packet);
                        clearSignout = true;
                    }
                }
            }
            // It is normal for clients to abruptly cut a connection
            // rather than closing the stream document
            // Since this is normal behavior, we won't log it as an error
//            Log.error(LocaleUtils.getLocalizedString("admin.disconnect"),ie);
        }
        catch (Exception e) {
            if (session != null) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error.stream"), e);
            }
        }
        finally {
            if (session != null) {
                Log.debug("Logging off " + session.getAddress() + " on " + connection);
                try {
                    // Allow everything to settle down after a disconnect
                    // e.g. presence updates to avoid sending double
                    // presence unavailable's
                    sleep(3000);
                    session.getConnection().close();
                }
                catch (Exception e) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.connection")
                            + "\n" + sock.toString());
                }
            }
            else {
                Log.error(LocaleUtils.getLocalizedString("admin.error.connection")
                        + "\n" + sock.toString());
            }
        }
    }

    /**
     * Read the incoming stream until it ends. Much of the reading
     * will actually be done in the channel handlers as they run the
     * XPP through the data. This method mostly handles the idle waiting
     * for incoming data. To prevent clients from stalling channel handlers,
     * a watch dog timer is used. Packets that take longer than the watch
     * dog limit to read will cause the session to be closed.
     */
    private void readStream() throws Exception {
        while (true) {
            Document document = reader.parseDocument();

            if (document != null) {
                Element doc = document.getRootElement();

                String tag = doc.getName();
                if ("message".equals(tag)) {
                    Message packet = new Message(doc);
                    packet.setFrom(session.getAddress());
                    auditor.audit(packet);
                    router.route(packet);
                    session.incrementClientPacketCount();
                }
                else if ("presence".equals(tag)) {
                    Presence packet = new Presence(doc);
                    packet.setFrom(session.getAddress());
                    auditor.audit(packet);
                    router.route(packet);
                    session.incrementClientPacketCount();
                    // Update the flag that indicates if the user made a clean sign out
                    clearSignout = (Presence.Type.unavailable == packet.getType() ? true : false);
                }
                else if ("iq".equals(tag)) {
                    IQ packet = getIQ(doc);
                    packet.setFrom(session.getAddress());
                    auditor.audit(packet);
                    router.route(packet);
                    session.incrementClientPacketCount();
                }
                else {
                    throw new XmlPullParserException(LocaleUtils.getLocalizedString("admin.error.packet.tag") + tag);
                }
            }
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

    /**
     * Uses the XPP to grab the opening stream tag and create
     * an active session object. In all cases, the method obtains the
     * opening stream tag, checks for errors, and either creates a session
     * or returns an error and kills the connection. If the connection
     * remains open, the XPP will be set to be ready for the first packet.
     * A call to next() should result in an START_TAG state with the first
     * packet in the stream.
     *
     * @throws UnauthorizedException If the caller did not have permission
     *                               to use this method.
     */
    private void createSession() throws UnauthorizedException, XmlPullParserException, IOException, Exception {
        XmlPullParser xpp = reader.getXPPParser();
        for (int eventType = xpp.getEventType();
             eventType != XmlPullParser.START_TAG;
             eventType = xpp.next()) {
        }

        // Conduct error checking, the opening tag should be 'stream'
        // in the 'etherx' namespace
        if (!xpp.getName().equals("stream")) {
            throw new XmlPullParserException(LocaleUtils.getLocalizedString("admin.error.bad-stream"));
        }
        if (!xpp.getNamespace(xpp.getPrefix()).equals(ETHERX_NAMESPACE)) {
            throw new XmlPullParserException(LocaleUtils.getLocalizedString("admin.error.bad-namespace"));
        }

        Writer writer = connection.getWriter();
        String startPacket = "<?xml version='1.0' encoding='"+CHARSET+"'?><stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"jabber:client\" from=\""+serverName+"\" id=\""+session.getStreamID().toString()+"\">";
        writer.write(startPacket);
        writer.flush();
        // TODO: check for SASL support in opening stream tag
    }


}
