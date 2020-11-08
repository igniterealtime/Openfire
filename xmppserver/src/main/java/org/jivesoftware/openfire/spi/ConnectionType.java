package org.jivesoftware.openfire.spi;

/**
 * Types of (socket-based, including HTTP) connections.
 *
 * This is an enumeration of the connections that are expected to be terminated by an instance of the Openfire instance,
 * and is used to define type-specific characteristics, including but not limited to:
 * <ul>
 *     <li>Property-name definition</li>
 *     <li>Applicable encryption policies</li>
 *     <li>Identity &amp; trust store configuration</li>
 * </ul>
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public enum ConnectionType
{
    /**
     * Socket-based server-to-server (XMPP federation) connectivity.
     */
    SOCKET_S2S( "xmpp.socket.ssl.", null ),

    /**
     * Socket-based client connectivity.
     */
    SOCKET_C2S( "xmpp.socket.ssl.client.", null ),

    /**
     * BOSH (HTTP-bind) based client connectivity.
     */
    BOSH_C2S( "xmpp.bosh.ssl.client.", SOCKET_C2S ),

    /**
     * Openfire web-admin console.
     */
    WEBADMIN( "admin.web.ssl.", SOCKET_C2S ),

    /**
     * Openfire External Component connectivity.
     */
    COMPONENT( "xmpp.component.", SOCKET_S2S ),

    /**
     * Openfire Connection Manager (multiplexer) connectivity.
     */
    CONNECTION_MANAGER( "xmpp.multiplex.", SOCKET_S2S );

    String prefix;
    ConnectionType fallback;
    ConnectionType( String prefix, ConnectionType fallback )
    {
        this.prefix = prefix;
        this.fallback = fallback;
    }

    /**
     * Flag that indicates whether the connections of this type are client-oriented.
     * @return true if it is SOCKET_C2S or its fallback is SOCKET_C2S.
     */
    public boolean isClientOriented()
    {
        ConnectionType pointer = this;
        while (pointer != null) {
            if (SOCKET_C2S == pointer) {
                return true;
            }

            pointer = pointer.getFallback();
        }
        return false;
    }

    /**
     * Returns the prefix used for the name of properties that are used to configure connections of this type.
     * @return A property name prefix (never null or an empty string).
     */
    public String getPrefix()
    {
        return prefix;
    }

    /**
     * Returns a type from which configuration can be used, when configuration specific for this type is missing.
     * @return A configuration fallback, or null if no such fallback exists.
     */
    public ConnectionType getFallback()
    {
        return fallback;
    }
}
