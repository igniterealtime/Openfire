package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * @author dwd
 *
 */
public class LocalServerSession extends LocalSession implements ServerSession {
    protected boolean usingServerDialback = true;
    protected boolean outboundAllowed = false;
    protected boolean inboundAllowed = false;

    public LocalServerSession(String serverName, Connection connection,
            StreamID streamID) {
        super(serverName, connection, streamID, Locale.getDefault());
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.session.LocalSession#canProcess(org.xmpp.packet.Packet)
     */
    @Override
    boolean canProcess(Packet packet) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.session.LocalSession#deliver(org.xmpp.packet.Packet)
     */
    @Override
    void deliver(Packet packet) throws UnauthorizedException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.session.LocalSession#getAvailableStreamFeatures()
     */
    @Override
    public String getAvailableStreamFeatures() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setDetached() {
        // TODO Implement stream management for s2s (OF-2425). Remove this override when it is.
        throw new UnsupportedOperationException("Stream management is not supported for server-to-server connections");
    }

    @Override
    public void reattach(LocalSession connectionProvider, long h) {
        // TODO Implement stream management for s2s (OF-2425). Remove this override when it is.
        throw new UnsupportedOperationException("Stream management is not supported for server-to-server connections");
    }

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    @Nonnull
    @Override
    public Connection getConnection() {
        final Connection connection = super.getConnection();
        // valid only as long as stream management for s2s is not implemented (OF-2425). Remove this override when it is.
        assert connection != null; // Openfire does not implement stream management for s2s (OF-2425). Therefor, the connection cannot be null.
        return connection;
    }

    @Override
    public boolean isUsingServerDialback() {
        return usingServerDialback;
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() +"{" +
            "address=" + getAddress() +
            ", streamID=" + getStreamID() +
            ", status=" + getStatus() +
            (getStatus() == STATUS_AUTHENTICATED ? " (authenticated)" : "" ) +
            (getStatus() == STATUS_CONNECTED ? " (connected)" : "" ) +
            (getStatus() == STATUS_CLOSED ? " (closed)" : "" ) +
            ", isSecure=" + isSecure() +
            ", isDetached=" + isDetached() +
            ", isUsingServerDialback=" + isUsingServerDialback() +
            '}';
    }
}
