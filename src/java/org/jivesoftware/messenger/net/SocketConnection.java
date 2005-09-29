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

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.interceptor.InterceptorManager;
import org.jivesoftware.messenger.interceptor.PacketRejectedException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * An object to track the state of a XMPP client-server session.
 * Currently this class contains the socket channel connecting the
 * client and server.
 *
 * @author Iain Shigeoka
 */
public class SocketConnection implements Connection {

    /**
     * The utf-8 charset for decoding and encoding XMPP packet streams.
     */
    public static final String CHARSET = "UTF-8";

    private Map<ConnectionCloseListener, Object> listeners = new HashMap<ConnectionCloseListener, Object>();

    private Socket socket;

    private Writer writer;

    private PacketDeliverer deliverer;

    private Session session;
    private boolean secure;
    private org.jivesoftware.util.XMLWriter xmlSerializer;
    private boolean flashClient = false;
    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;
	private TLSStreamHandler tlsStreamHandler;

    /**
     * Create a new session using the supplied socket.
     *
     * @param deliverer the packet deliverer this connection will use.
     * @param socket the socket to represent.
     * @param isSecure true if this is a secure connection.
     * @throws NullPointerException if the socket is null.
     */
    public SocketConnection(PacketDeliverer deliverer, Socket socket, boolean isSecure)
            throws IOException
    {
        if (socket == null) {
            throw new NullPointerException("Socket channel must be non-null");
        }

        this.secure = isSecure;
        this.socket = socket;
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), CHARSET));
        this.deliverer = deliverer;
        xmlSerializer = new XMLSocketWriter(writer, this);
    }

    /**
     * Returns the stream handler responsible for securing the plain connection and providing
     * the corresponding input and output streams.
     *
     * @return the stream handler responsible for securing the plain connection and providing
     *         the corresponding input and output streams.
     */
    public TLSStreamHandler getTLSStreamHandler() {
		return tlsStreamHandler;
	}

    /**
     * Secures the plain connection by negotiating TLS with the client.
     *
     * @param clientMode boolean indicating if this entity is a client or a server.
     * @throws IOException if an error occured while securing the connection.
     */
    public void startTLS(boolean clientMode) throws IOException {
		if (!secure) {
			secure = true;
			tlsStreamHandler = new TLSStreamHandler(socket, clientMode);
			writer = new BufferedWriter(new OutputStreamWriter(tlsStreamHandler.getOutputStream(), CHARSET));
			xmlSerializer = new XMLSocketWriter(writer, this);
		}
	}
	
    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        try {
            synchronized (writer) {
                // Register that we started sending data on the connection
                SocketSendingTracker.getInstance().socketStartedSending(this);
                writer.write(" ");
                writer.flush();
            }
        }
        catch (Exception e) {
            Log.warn("Closing no longer valid connection" + "\n" + this.toString(), e);
            close();
        }
        finally {
            // Register that we finished sending data on the connection
            SocketSendingTracker.getInstance().socketFinishedSending(this);
        }
        return !isClosed();
    }

    public void init(Session owner) {
        session = owner;
    }

    public Object registerCloseListener(ConnectionCloseListener listener, Object handbackMessage) {
        Object status = null;
        if (isClosed()) {
            listener.onConnectionClose(handbackMessage);
        }
        else {
            status = listeners.put(listener, handbackMessage);
        }
        return status;
    }

    public Object removeCloseListener(ConnectionCloseListener listener) {
        return listeners.remove(listener);
    }

    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    public Writer getWriter() {
        return writer;
    }

    public boolean isClosed() {
        if (session == null) {
            return socket.isClosed();
        }
        return session.getStatus() == Session.STATUS_CLOSED;
    }

    public boolean isSecure() {
        return secure;
    }

    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    /**
     * Sets the XMPP version information. In most cases, the version should be "1.0".
     * However, older clients using the "Jabber" protocol do not set a version. In that
     * case, the version is "0.0".
     *
     * @param majorVersion the major version.
     * @param minorVersion the minor version.
     */
    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * Sets the language code that should be used for this connection (e.g. "en").
     *
     * @param language the language code.
     */
    public void setLanaguage(String language) {
        this.language = language;
    }

    public boolean isFlashClient() {
        return flashClient;
    }

    /**
     * Sets whether the connected client is a flash client. Flash clients need to
     * receive a special character (i.e. \0) at the end of each xml packet. Flash
     * clients may send the character \0 in incoming packets and may start a
     * connection using another openning tag such as: "flash:client".
     *
     * @param flashClient true if the if the connection is a flash client.
     */
    public void setFlashClient(boolean flashClient) {
        this.flashClient = flashClient;
    }

    public void close() {
        boolean wasClosed = false;
        synchronized (this) {
            if (!isClosed()) {
                try {
                    if (session != null) {
                        session.setStatus(Session.STATUS_CLOSED);
                    }
                    synchronized (writer) {
                        try {
                            // Register that we started sending data on the connection
                            SocketSendingTracker.getInstance().socketStartedSending(this);
                            writer.write("</stream:stream>");
                            if (flashClient) {
                                writer.write('\0');
                            }
                            writer.flush();
                        }
                        catch (IOException e) {}
                        finally {
                            // Register that we finished sending data on the connection
                            SocketSendingTracker.getInstance().socketFinishedSending(this);
                        }
                    }
                }
                catch (Exception e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error.close")
                            + "\n" + this.toString(), e);
                }
                closeConnection();
                wasClosed = true;
            }
        }
        if (wasClosed) {
            notifyCloseListeners();
        }
    }

    /**
     * Forces the connection to be closed immediately no matter if closing the socket takes
     * a long time. This method should only be called from {@link SocketSendingTracker} when
     * sending data over the socket has taken a long time and we need to close the socket, discard
     * the connection and its session.
     */
    void forceClose() {
        if (session != null) {
            // Set that the session is closed. This will prevent threads from trying to
            // deliver packets to this session thus preventing future locks.
            session.setStatus(Session.STATUS_CLOSED);
        }
        closeConnection();
        // Notify the close listeners so that the SessionManager can send unavailable
        // presences if required.
        notifyCloseListeners();
    }

    private void closeConnection() {
        try {
            if (tlsStreamHandler == null) {
                socket.close();
            }
            else {
                // Close the channels since we are using TLS (i.e. NIO). If the channels implement
                // the InterruptibleChannel interface then any other thread that was blocked in
                // an I/O operation will be interrupted and an exception thrown
                tlsStreamHandler.close();
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.close")
                    + "\n" + this.toString(), e);
        }
    }

    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (isClosed()) {
            deliverer.deliver(packet);
        }
        else {
            try {
                // Invoke the interceptors before we send the packet
                InterceptorManager.getInstance().invokeInterceptors(packet, session, false, false);
                boolean errorDelivering = false;
                synchronized (writer) {
                    try {
                        xmlSerializer.write(packet.getElement());
                        if (flashClient) {
                            writer.write('\0');
                        }
                        xmlSerializer.flush();
                    }
                    catch (IOException e) {
                        Log.debug("Error delivering packet" + "\n" + this.toString(), e);
                        errorDelivering = true;
                    }
                }
                if (errorDelivering) {
                    close();
                    // Retry sending the packet again. Most probably if the packet is a
                    // Message it will be stored offline
                    deliverer.deliver(packet);
                }
                else {
                    // Invoke the interceptors after we have sent the packet
                    InterceptorManager.getInstance().invokeInterceptors(packet, session, false, true);
                    session.incrementServerPacketCount();
                }
            }
            catch (PacketRejectedException e) {
                // An interceptor rejected the packet so do nothing
            }
        }
    }

    public void deliverRawText(String text) {
        if (!isClosed()) {
            boolean errorDelivering = false;
            synchronized (writer) {
                try {
                    // Register that we started sending data on the connection
                    SocketSendingTracker.getInstance().socketStartedSending(this);
                    writer.write(text);
                    if (flashClient) {
                        writer.write('\0');
                    }
                    writer.flush();
                }
                catch (IOException e) {
                    Log.debug("Error delivering raw text" + "\n" + this.toString(), e);
                    errorDelivering = true;
                }
                finally {
                    // Register that we finished sending data on the connection
                    SocketSendingTracker.getInstance().socketFinishedSending(this);
                }
            }
            if (errorDelivering) {
                close();
            }
        }
    }

    /**
     * Notifies all close listeners that the connection has been closed.
     * Used by subclasses to properly finish closing the connection.
     */
    private void notifyCloseListeners() {
        synchronized (listeners) {
            for (ConnectionCloseListener listener : listeners.keySet()) {
                listener.onConnectionClose(listeners.get(listener));
            }
        }
    }

    public String toString() {
        return super.toString() + " socket: " + socket + " session: " + session;
    }
}