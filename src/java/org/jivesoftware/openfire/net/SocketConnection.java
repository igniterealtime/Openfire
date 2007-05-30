/**
 * $RCSfile$
 * $Revision: 3187 $
 * $Date: 2005-12-11 13:34:34 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZOutputStream;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

import javax.net.ssl.SSLSession;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static Map<SocketConnection, String> instances =
            new ConcurrentHashMap<SocketConnection, String>();

    /**
     * Milliseconds a connection has to be idle to be closed. Timeout is disabled by default. It's
     * up to the connection's owner to configure the timeout value. Sending stanzas to the client
     * is not considered as activity. We are only considering the connection active when the
     * client sends some data or hearbeats (i.e. whitespaces) to the server.
     * The reason for this is that sending data will fail if the connection is closed. And if
     * the thread is blocked while sending data (because the socket is closed) then the clean up
     * thread will close the socket anyway.
     */
    private long idleTimeout = -1;

    final private Map<ConnectionCloseListener, Object> listeners =
            new HashMap<ConnectionCloseListener, Object>();

    private Socket socket;
    private SocketReader socketReader;

    private Writer writer;
    private AtomicBoolean writing = new AtomicBoolean(false);

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private PacketDeliverer backupDeliverer;

    private LocalSession session;
    private boolean secure;
    private boolean compressed;
    private org.jivesoftware.util.XMLWriter xmlSerializer;
    private boolean flashClient = false;
    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;
    private TLSStreamHandler tlsStreamHandler;

    private long writeStarted = -1;

    /**
     * TLS policy currently in use for this connection.
     */
    private TLSPolicy tlsPolicy = TLSPolicy.optional;

    /**
     * Compression policy currently in use for this connection.
     */
    private CompressionPolicy compressionPolicy = CompressionPolicy.disabled;

    public static Collection<SocketConnection> getInstances() {
        return instances.keySet();
    }

    /**
     * Create a new session using the supplied socket.
     *
     * @param backupDeliverer the packet deliverer this connection will use when socket is closed.
     * @param socket the socket to represent.
     * @param isSecure true if this is a secure connection.
     * @throws java.io.IOException if there was a socket error while sending the packet.
     */
    public SocketConnection(PacketDeliverer backupDeliverer, Socket socket, boolean isSecure)
            throws IOException {
        if (socket == null) {
            throw new NullPointerException("Socket channel must be non-null");
        }

        this.secure = isSecure;
        this.socket = socket;
        // DANIELE: Modify socket to use channel
        if (socket.getChannel() != null) {
            writer = Channels.newWriter(
                    ServerTrafficCounter.wrapWritableChannel(socket.getChannel()), CHARSET);
        }
        else {
            writer = new BufferedWriter(new OutputStreamWriter(
                    ServerTrafficCounter.wrapOutputStream(socket.getOutputStream()), CHARSET));
        }
        this.backupDeliverer = backupDeliverer;
        xmlSerializer = new XMLSocketWriter(writer, this);

        instances.put(this, "");
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

    public void startTLS(boolean clientMode, String remoteServer) throws IOException {
        if (!secure) {
            secure = true;
            // Prepare for TLS
            tlsStreamHandler = new TLSStreamHandler(socket, clientMode, remoteServer, session instanceof IncomingServerSession);
            if (!clientMode) {
                // Indicate the client that the server is ready to negotiate TLS
                deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
            }
            // Start handshake
            tlsStreamHandler.start();
            // Use new wrapped writers
            writer = new BufferedWriter(new OutputStreamWriter(tlsStreamHandler.getOutputStream(), CHARSET));
            xmlSerializer = new XMLSocketWriter(writer, this);
        }
    }

    public void addCompression() {
        // WARNING: We do not support adding compression for incoming traffic but not for outgoing traffic.
    }

    public void startCompression() {
        compressed = true;

        try {
            if (tlsStreamHandler == null) {
                ZOutputStream out = new ZOutputStream(
                        ServerTrafficCounter.wrapOutputStream(socket.getOutputStream()),
                        JZlib.Z_BEST_COMPRESSION);
                out.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                writer = new BufferedWriter(new OutputStreamWriter(out, CHARSET));
                xmlSerializer = new XMLSocketWriter(writer, this);
            }
            else {
                ZOutputStream out = new ZOutputStream(tlsStreamHandler.getOutputStream(), JZlib.Z_BEST_COMPRESSION);
                out.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                writer = new BufferedWriter(new OutputStreamWriter(out, CHARSET));
                xmlSerializer = new XMLSocketWriter(writer, this);
            }
        } catch (IOException e) {
            // TODO Would be nice to still be able to throw the exception and not catch it here
            Log.error("Error while starting compression", e);
            compressed = false;
        }
    }

    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        boolean allowedToWrite = false;
        try {
            requestWriting();
            allowedToWrite = true;
            // Register that we started sending data on the connection
            writeStarted();
            writer.write(" ");
            writer.flush();
        }
        catch (Exception e) {
            Log.warn("Closing no longer valid connection" + "\n" + this.toString(), e);
            close();
        }
        finally {
            // Register that we finished sending data on the connection
            writeFinished();
            if (allowedToWrite) {
                releaseWriting();
            }
        }
        return !isClosed();
    }

    public void init(LocalSession owner) {
        session = owner;
    }

    public void registerCloseListener(ConnectionCloseListener listener, Object handbackMessage) {
        if (isClosed()) {
            listener.onConnectionClose(handbackMessage);
        }
        else {
            listeners.put(listener, handbackMessage);
        }
    }

    public void removeCloseListener(ConnectionCloseListener listener) {
        listeners.remove(listener);
    }

    public byte[] getAddress() throws UnknownHostException {
        return socket.getInetAddress().getAddress();
    }

    public String getHostAddress() throws UnknownHostException {
        return socket.getInetAddress().getHostAddress();
    }

    public String getHostName() throws UnknownHostException {
        return socket.getInetAddress().getHostName();
    }

    /**
     * Returns the port that the connection uses.
     *
     * @return the port that the connection uses.
     */
    public int getPort() {
        return socket.getPort();
    }

    /**
     * Returns the Writer used to send data to the connection. The writer should be
     * used with caution. In the majority of cases, the {@link #deliver(Packet)}
     * method should be used to send data instead of using the writer directly.
     * You must synchronize on the writer before writing data to it to ensure
     * data consistency:
     *
     * <pre>
     *  Writer writer = connection.getWriter();
     * synchronized(writer) {
     *     // write data....
     * }</pre>
     *
     * @return the Writer for this connection.
     */
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

    public boolean isCompressed() {
        return compressed;
    }

    public TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    /**
     * Sets whether TLS is mandatory, optional or is disabled. When TLS is mandatory clients
     * are required to secure their connections or otherwise their connections will be closed.
     * On the other hand, when TLS is disabled clients are not allowed to secure their connections
     * using TLS. Their connections will be closed if they try to secure the connection. in this
     * last case.
     *
     * @param tlsPolicy whether TLS is mandatory, optional or is disabled.
     */
    public void setTlsPolicy(TLSPolicy tlsPolicy) {
        this.tlsPolicy = tlsPolicy;
    }

    public CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    /**
     * Sets whether compression is enabled or is disabled.
     *
     * @param compressionPolicy whether Compression is enabled or is disabled.
     */
    public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Sets the number of milliseconds a connection has to be idle to be closed. Sending
     * stanzas to the client is not considered as activity. We are only considering the
     * connection active when the client sends some data or hearbeats (i.e. whitespaces)
     * to the server.
     *
     * @param timeout the number of milliseconds a connection has to be idle to be closed.
     */
    public void setIdleTimeout(long timeout) {
        this.idleTimeout = timeout;
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

    public SSLSession getSSLSession() {
        if (tlsStreamHandler != null) {
            return tlsStreamHandler.getSSLSession();
        }
        return null;
    }

    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    public void close() {
        boolean wasClosed = false;
        synchronized (this) {
            if (!isClosed()) {
                try {
                    if (session != null) {
                        session.setStatus(Session.STATUS_CLOSED);
                    }
                    boolean allowedToWrite = false;
                    try {
                        requestWriting();
                        allowedToWrite = true;
                        // Register that we started sending data on the connection
                        writeStarted();
                        writer.write("</stream:stream>");
                        if (flashClient) {
                            writer.write('\0');
                        }
                        writer.flush();
                    }
                    catch (IOException e) {
                        // Do nothing
                    }
                    finally {
                        // Register that we finished sending data on the connection
                        writeFinished();
                        if (allowedToWrite) {
                            releaseWriting();
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

    public void systemShutdown() {
        deliverRawText("<stream:error><system-shutdown " +
                "xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>");
        close();
    }

    void writeStarted() {
        writeStarted = System.currentTimeMillis();
    }

    void writeFinished() {
        writeStarted = -1;
    }

    /**
     * Returns true if the socket was closed due to a bad health. The socket is considered to
     * be in a bad state if a thread has been writing for a while and the write operation has
     * not finished in a long time or when the client has not sent a heartbeat for a long time.
     * In any of both cases the socket will be closed.
     *
     * @return true if the socket was closed due to a bad health.s
     */
    boolean checkHealth() {
        // Check that the sending operation is still active
        long writeTimestamp = writeStarted;
        if (writeTimestamp > -1 && System.currentTimeMillis() - writeTimestamp >
                JiveGlobals.getIntProperty("xmpp.session.sending-limit", 60000)) {
            // Close the socket
            if (Log.isDebugEnabled()) {
                Log.debug("Closing connection: " + this + " that started sending data at: " +
                        new Date(writeTimestamp));
            }
            forceClose();
            return true;
        }
        else {
            // Check if the connection has been idle. A connection is considered idle if the client
            // has not been receiving data for a period. Sending data to the client is not
            // considered as activity.
            if (idleTimeout > -1 && socketReader != null &&
                    System.currentTimeMillis() - socketReader.getLastActive() > idleTimeout) {
                // Close the socket
                if (Log.isDebugEnabled()) {
                    Log.debug("Closing connection that has been idle: " + this);
                }
                forceClose();
                return true;
            }
        }
        return false;
    }

    private void release() {
        writeStarted = -1;
        instances.remove(this);
    }

    /**
     * Forces the connection to be closed immediately no matter if closing the socket takes
     * a long time. This method should only be called from {@link SocketSendingTracker} when
     * sending data over the socket has taken a long time and we need to close the socket, discard
     * the connection and its session.
     */
    private void forceClose() {
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
        release();
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
            backupDeliverer.deliver(packet);
        }
        else {
            boolean errorDelivering = false;
            boolean allowedToWrite = false;
            try {
                requestWriting();
                allowedToWrite = true;
                xmlSerializer.write(packet.getElement());
                if (flashClient) {
                    writer.write('\0');
                }
                xmlSerializer.flush();
            }
            catch (Exception e) {
                Log.debug("Error delivering packet" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            finally {
                if (allowedToWrite) {
                    releaseWriting();
                }
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                backupDeliverer.deliver(packet);
            }
            else {
                session.incrementServerPacketCount();
            }
        }
    }

    public void deliverRawText(String text) {
        if (!isClosed()) {
            boolean errorDelivering = false;
            boolean allowedToWrite = false;
            try {
                requestWriting();
                allowedToWrite = true;
                // Register that we started sending data on the connection
                writeStarted();
                writer.write(text);
                if (flashClient) {
                    writer.write('\0');
                }
                writer.flush();
            }
            catch (Exception e) {
                Log.debug("Error delivering raw text" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            finally {
                // Register that we finished sending data on the connection
                writeFinished();
                if (allowedToWrite) {
                    releaseWriting();
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
                try {
                    listener.onConnectionClose(listeners.get(listener));
                }
                catch (Exception e) {
                    Log.error("Error notifying listener: " + listener, e);
                }
            }
        }
    }

    private void requestWriting() throws Exception {
        for (;;) {
            if (writing.compareAndSet(false, true)) {
                // We are now in writing mode and only we can write to the socket
                return;
            }
            else {
                // Check health of the socket
                if (checkHealth()) {
                    // Connection was closed then stop
                    throw new Exception("Probable dead connection was closed");
                }
                else {
                    Thread.sleep(1);
                }
            }
        }
    }

    private void releaseWriting() {
        writing.compareAndSet(true, false);
    }

    public String toString() {
        return super.toString() + " socket: " + socket + " session: " + session;
    }

    public void setSocketReader(SocketReader socketReader) {
        this.socketReader = socketReader;
    }
}