package org.jivesoftware.openfire.keystore;

import org.jivesoftware.util.JiveGlobals;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Potential intended usages (for TLS connectivity).
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public enum Purpose
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
    BOSH_C2S( "xmpp.bosh.ssl.client.", SOCKET_C2S),

    /**
     * Generic administrative services (eg: user providers).
     */
    ADMIN( "admin.ssl.", SOCKET_S2S),

    /**
     * Openfire web-admin console.
     */
    WEBADMIN( "admin.web.ssl.", ADMIN);

    String prefix;
    Purpose fallback;
    Purpose( String prefix, Purpose fallback) {
        this.prefix = prefix;
        this.fallback = fallback;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public Purpose getFallback()
    {
        return fallback;
    }

    public String getIdentityStoreType()
    {
        final String propertyName = prefix + "storeType";
        final String defaultValue = "jks";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getIdentityStoreType() ).trim();
        }
    }

    public String getTrustStoreType()
    {
        return getIdentityStoreType();
    }

    public String getIdentityStorePassword()
    {
        final String propertyName = prefix + "keypass";
        final String defaultValue = "changeit";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getIdentityStorePassword() ).trim();
        }
    }

    public String getTrustStorePassword()
    {
        final String propertyName = prefix + "trustpass";
        final String defaultValue = "changeit";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getTrustStorePassword() ).trim();
        }
    }

    public boolean acceptSelfSigned()
    {
        // TODO these are new properties! Deprecate (migrate?) all existing 'accept-selfsigned properties' (Eg: org.jivesoftware.openfire.session.ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS )
        final String propertyName = prefix + "certificate.accept-selfsigned";
        final boolean defaultValue = false;

        if ( fallback == null )
        {
            return JiveGlobals.getBooleanProperty( propertyName, defaultValue );
        }
        else
        {
            return JiveGlobals.getBooleanProperty( propertyName, fallback.acceptSelfSigned() );
        }
    }

    public boolean verifyValidity()
    {
        // TODO these are new properties! Deprecate (migrate?) all existing 'verify / verify-validity properties' (Eg: org.jivesoftware.openfire.session.ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY_VALIDITY )
        final String propertyName = prefix + "certificate.verify.validity";
        final boolean defaultValue = true;

        if ( fallback == null )
        {
            return JiveGlobals.getBooleanProperty( propertyName, defaultValue );
        }
        else
        {
            return JiveGlobals.getBooleanProperty( propertyName, fallback.acceptSelfSigned() );
        }
    }

    public String getIdentityStoreLocation() throws IOException
    {
        return canonicalize( getIdentityStoreLocationNonCanonicalized() );
    }

    public String getIdentityStoreLocationNonCanonicalized()
    {
        final String propertyName = prefix + "keystore";
        final String defaultValue = "resources" + File.separator + "security" + File.separator + "keystore";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getIdentityStoreLocationNonCanonicalized() ).trim();
        }
    }

    public String getTrustStoreLocation() throws IOException
    {
        return canonicalize( getTrustStoreLocationNonCanonicalized() );
    }

    public String getTrustStoreLocationNonCanonicalized()
    {
        final String propertyName = prefix + "truststore";
        final String defaultValue = "resources" + File.separator + "security" + File.separator + "truststore";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getTrustStoreLocationNonCanonicalized() ).trim();
        }
    }

    public String getProtocolsEnabled()
    {
        final String propertyName = prefix + "protocols.enabled";
        final String defaultValue = "TLSv1,TLSv1.1,TLSv1.2";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getProtocolsEnabled() ).trim();
        }
    }

    public String getProtocolsDisabled()
    {
        final String propertyName = prefix + "protocols.disabled";
        final String defaultValue = "SSLv1,SSLv2,SSLv2Hello,SSLv3";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getProtocolsDisabled() ).trim();
        }
    }

    public String getCipherSuitesEnabled()
    {
        final String propertyName = prefix + "ciphersuites.enabled";
        final String defaultValue = "";

        final String result;
        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue );
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getCipherSuitesEnabled() );
        }
    }

    public String getCipherSuitesDisabled()
    {
        final String propertyName = prefix + "ciphersuites.disabled";
        final String defaultValue = "";

        if ( fallback == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, fallback.getCipherSuitesDisabled() ).trim();
        }
    }

    public static String canonicalize( String path ) throws IOException
    {
        File file = new File( path );
        if (!file.isAbsolute()) {
            file = new File( JiveGlobals.getHomeDirectory() + File.separator + path );
        }

        return file.getCanonicalPath();
    }

}
