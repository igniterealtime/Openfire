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

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.audit.Auditor;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.io.EOFException;
import java.io.InputStreamReader;
import java.net.Socket;
import javax.xml.stream.*;

/**
 * @author Iain Shigeoka
 */
public class SocketReadThread extends Thread {

    private Socket sock;
    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    private String charset = "UTF-8";

    private XMLInputFactory xppFactory;
    private XMLStreamReader xpp;

    private Session session;
    private Connection connection;

    private static final String ETHERX_NAMESPACE =
            "http://etherx.jabber.org/streams";

    private String serverName;
    /**
     * Router used to route incoming packets to the correct channels.
     */
    private PacketRouter router;

    /**
     * Audits incoming data
     */
    private Auditor auditor;

    private PacketFactory packetFactory;

    private boolean clearSignout = false;

    /**
     * Create dedicated read thread for this socket.
     *
     * @param router     The router for sending packets that were read
     * @param serverName The name of the server this socket is working for
     * @param auditor    The audit manager that will audit incoming packets
     * @param sock       The socket to read from
     * @param session    The session being read
     */
    public SocketReadThread(PacketRouter router,
                            PacketFactory packetFactory,
                            String serverName,
                            Auditor auditor,
                            Socket sock,
                            Session session) {
        super("SRT reader");
        this.serverName = serverName;
        this.router = router;
        this.packetFactory = packetFactory;
        this.auditor = auditor;
        this.session = session;
        connection = session.getConnection();
        this.sock = sock;
        xppFactory = XMLInputFactory.newInstance();
        xppFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
    }

    /**
     * A dedicated thread loop for reading the stream and sending incoming
     * packets to the appropriate router.
     */
    public void run() {
        try {
            xpp =
                    xppFactory.createXMLStreamReader(new InputStreamReader(sock.getInputStream(),
                            charset));

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
        catch (XMLStreamException ie) {
            // Check if the user abruptly cut the connection without sending previously an 
            // unavailable presence
            if (clearSignout == false) {
                if (session != null && session.getStatus() == Session.STATUS_AUTHENTICATED) {
                    Presence presence = session.getPresence();
                    if (presence != null) {
                        // Simulate an unavailable presence sent by the user.
                        Presence packet = (Presence) presence.createDeepCopy();
                        packet.setType(Presence.UNAVAILABLE);
                        try {
                            packet.setAvailable(false);
                            packet.setVisible(false);
                        }
                        catch (UnauthorizedException e) {}
                        packet.setOriginatingSession(session);
                        packet.setSender(session.getAddress());
                        packet.setSending(false);
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
     *
     * @throws XMLStreamException if there is trouble reading from the socket
     */
    private void readStream() throws UnauthorizedException, XMLStreamException {

        while (true) {
            for (int eventType = xpp.next();
                 eventType != XMLStreamConstants.START_ELEMENT;
                 eventType = xpp.next()) {
                if (eventType == XMLStreamConstants.CHARACTERS) {
                    if (!xpp.isWhiteSpace()) {
                        throw new XMLStreamException(LocaleUtils.getLocalizedString("admin.error.packet.text"));
                    }
                }
                else if (eventType == XMLStreamConstants.END_DOCUMENT) {
                    return;
                }
            }

            String tag = xpp.getLocalName();

            if ("message".equals(tag)) {
                Message packet = packetFactory.getMessage(xpp);
                packet.setOriginatingSession(session);
                packet.setSender(session.getAddress());
                packet.setSending(false);
                auditor.audit(packet);
                router.route(packet);
                session.incrementClientPacketCount();
            }
            else if ("presence".equals(tag)) {
                Presence packet = packetFactory.getPresence(xpp);
                packet.setOriginatingSession(session);
                packet.setSender(session.getAddress());
                packet.setSending(false);
                auditor.audit(packet);
                router.route(packet);
                session.incrementClientPacketCount();
                // Update the flag that indicates if the user made a clean sign out
                clearSignout = (Presence.UNAVAILABLE == packet.getType() ? true : false);
            }
            else if ("iq".equals(tag)) {
                IQ packet = packetFactory.getIQ(xpp);
                packet.setOriginatingSession(session);
                packet.setSender(session.getAddress());
                packet.setSending(false);
                auditor.audit(packet);
                router.route(packet);
                session.incrementClientPacketCount();
            }
            else {
                throw new XMLStreamException(LocaleUtils.getLocalizedString("admin.error.packet.tag") + tag);
            }
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
     * @throws XMLStreamException    If the stream is not valid XML
     */
    private void createSession() throws UnauthorizedException, XMLStreamException {

        for (int eventType = xpp.getEventType();
             eventType != XMLStreamConstants.START_ELEMENT;
             eventType = xpp.next()) {
        }

        // Conduct error checking, the opening tag should be 'stream'
        // in the 'etherx' namespace
        if (!xpp.getLocalName().equals("stream")) {
            throw new XMLStreamException(LocaleUtils.getLocalizedString("admin.error.bad-stream"));
        }
        if (!xpp.getNamespaceURI(xpp.getPrefix()).equals(ETHERX_NAMESPACE)) {
            throw new XMLStreamException(LocaleUtils.getLocalizedString("admin.error.bad-namespace"));
        }

        XMLStreamWriter xser = connection.getSerializer();
        xser.writeStartDocument();
        xser.writeStartElement("stream", "stream", "http://etherx.jabber.org/streams");
        xser.writeNamespace("stream", "http://etherx.jabber.org/streams");
        xser.writeDefaultNamespace("jabber:client");
        xser.writeAttribute("from", serverName);
        xser.writeAttribute("id", session.getStreamID().toString());
        xser.writeCharacters(" ");
        xser.flush();

        // TODO: check for SASL support in opening stream tag
    }
}
