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

import org.dom4j.io.XMLWriter;
import org.jivesoftware.messenger.PacketDeliverer;
import org.jivesoftware.messenger.PacketException;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.interceptor.InterceptorManager;
import org.jivesoftware.messenger.interceptor.PacketRejectedException;
import org.jivesoftware.messenger.spi.BasicConnection;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;

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

    private Session session;
    private boolean secure;
    private XMLWriter xmlSerializer;
    private boolean flashClient = false;

    /**
     * Create a new session using the supplied socket.
     *
     * @param deliverer The packet deliverer this connection will use
     * @param socket    The socket to represent
     * @param isSecure  True if this is a secure connection
     * @throws NullPointerException If the socket is null
     */
    public SocketConnection(PacketDeliverer deliverer, Socket socket, boolean isSecure)
            throws IOException
    {
        if (socket == null) {
            throw new NullPointerException("Socket channel must be non-null");
        }

        this.secure = isSecure;
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
     * @return true if the session is closed.
     */
    public boolean isClosed() {
        if (session == null) {
            return sock.isClosed();
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
                synchronized (writer) {
                    try {
                        writer.write("</stream:stream>");
                        if (flashClient) {
                            writer.write('\0');
                        }
                        xmlSerializer.flush();
                    }
                    catch (IOException e) {}
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
     * The method essentially calls <tt>packet.send(serializer,version)</tt>.
     *
     * @param packet The packet to deliver.
     */
    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (isClosed()) {
            deliverer.deliver(packet);
        }
        else {
            try {
                // Invoke the interceptors before we send the packet
                InterceptorManager.getInstance().invokeInterceptors(packet, session, false, false);
                synchronized (writer) {
                    try {
                        xmlSerializer.write(packet.getElement());
                        if (flashClient) {
                            writer.write('\0');
                        }
                        xmlSerializer.flush();
                    }
                    catch (IOException e) {
                        Log.error(e);
                        close();
                    }
                }
                // Invoke the interceptors after we have sent the packet
                InterceptorManager.getInstance().invokeInterceptors(packet, session, false, true);
                session.incrementServerPacketCount();
            }
            catch (PacketRejectedException e) {
                // An interceptor rejected the packet so do nothing
            }
        }
    }

    public void setFlashClient(boolean flashClient) {
        this.flashClient = flashClient;
    }

    public boolean isFlashClient() {
        return flashClient;
    }
}