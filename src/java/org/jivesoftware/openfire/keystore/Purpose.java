package org.jivesoftware.openfire.keystore;

/**
 * Potential intended usages for keystores
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public enum Purpose
{
    /**
     * Identification of this Openfire instance used by regular socket-based connections.
     */
    SOCKETBASED_IDENTITYSTORE( false ),

    /**
     * Identification of remote servers that you choose to trust, applies to server-to-server federation via regular socket-based connections.
     */
    SOCKETBASED_S2S_TRUSTSTORE( true ),

    /**
     * Identification of clients that you choose to trust, applies to mutual authentication via regular socket-based connections.
     */
    SOCKETBASED_C2S_TRUSTSTORE( true ),

    /**
     * Identification of this Openfire instance used by regular BOSH (HTTP-bind) connections.
     */
    BOSHBASED_IDENTITYSTORE( false ),

    /**
     * Identification of clients that you choose to trust, applies to mutual authentication via BOSH (HTTP-bind) connections.
     */
    BOSHBASED_C2S_TRUSTSTORE( true ),

    /**
     * Identification of this Openfire instance used by connections to administrative services (eg: user providers).
     */
    ADMINISTRATIVE_IDENTITYSTORE( false ),

    /**
     * Identification of remote applications/servers that provide administrative functionality (eg: user providers).
     */
    ADMINISTRATIVE_TRUSTSTORE( true ),

    /**
     * Openfire web-admin console.
     */
    WEBADMIN_IDENTITYSTORE( false ),

    /**
     * Openfire web-admin console.
     */
    WEBADMIN_TRUSTSTORE( true );

    private final boolean isTrustStore;

    Purpose( boolean isTrustStore )
    {
        this.isTrustStore = isTrustStore;
    }

    public boolean isIdentityStore()
    {
        return !isTrustStore;
    }

    public boolean isTrustStore()
    {
        return isTrustStore;
    }
}
