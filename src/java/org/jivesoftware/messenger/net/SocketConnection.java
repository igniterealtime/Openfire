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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.messenger.PacketDeliverer;
import org.jivesoftware.messenger.PacketException;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.audit.Auditor;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.BasicConnection;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

/**
 * An object to track the state of a Jabber client-server session.
 * Currently this class contains the socket channel connecting the
 * client and server.
 *
 * @author Iain Shigeoka
 */
public class SocketConnection extends BasicConnection {

    /**
     * The socket this session represents
     */
    private Socket sock;

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    private String charset = "UTF-8";

    /**
     * The writer used to send outgoing data.
     */
    private Writer writer;

    /**
     * The packet deliverer for local packets
     */
    private PacketDeliverer deliverer;

    /**
     * Audits packets
     */
    private Auditor auditor;

    private Session session;

    private boolean secure;

    private XMLWriter xmlSerializer;

    /**
     * Create a new session using the supplied socket.
     *
     * @param deliverer The packet deliverer this connection will use
     * @param auditor   Auditor that will audit outgoing packets
     * @param socket    The socket to represent
     * @param isSecure  True if this is a secure connection
     * @throws NullPointerException If the socket is null
     */
    public SocketConnection(PacketDeliverer deliverer, Auditor auditor,
                            Socket socket, boolean isSecure) throws IOException {

        if (socket == null) {
            throw new NullPointerException("Socket channel must be non-null");
        }

        this.secure = isSecure;
        this.auditor = auditor;
        sock = socket;
        writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), charset));
        this.deliverer = deliverer;
        xmlSerializer = new XMLWriter(writer);
    }

    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        try {
            synchronized (writer) {
                writer.write(" ");
                writer.flush();
            }
        }
        catch (Exception e) {
            close();
        }
        return !isClosed();
    }

    public void init(Session owner) {
        session = owner;
    }

    public InetAddress getInetAddress() throws UnauthorizedException {
        return sock.getInetAddress();
    }

    public XMLWriter getSerializer() throws UnauthorizedException {
        return xmlSerializer;
    }

    public Writer getWriter() throws UnauthorizedException {
        return writer;
    }

    /**
     * Retrieve the closed state of the Session.
     *
     * @return True if the session is closed
     */
    public boolean isClosed() {
        if (session == null) {
            try {
                sock.getInputStream();
            }
            catch (IOException e) {
                return false;
            }
            return true;
        }
        return session.getStatus() == Session.STATUS_CLOSED;
    }

    public boolean isSecure() {
        return secure;
    }

    public synchronized void close() {
        if (!isClosed()) {
            try {
                if (session != null) {
                    session.setStatus(Session.STATUS_CLOSED);
                }
            }
            catch (Exception e) {
                // Do nothing
            }
            try {
                sock.close();
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error.close")
                        + "\n" + this.toString(), e);
            }
            notifyCloseListeners();
        }
    }

    /**
     * Delivers the packet to this XMPPAddress without checking the recipient.
     * The method essentially calls
     * <code>packet.send(serializer,version)</code>
     *
     * @param packet The packet to deliver.
     */
    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (isClosed()) {
            deliverer.deliver(packet);
        }
        else {
            synchronized (writer) {
                try {
                    xmlSerializer.write(packet.getElement());
                    xmlSerializer.flush();
                }
                catch (IOException e) {
                    Log.error(e);
                }
            }
            auditor.audit(packet, session);
            session.incrementServerPacketCount();
        }
    }
}
