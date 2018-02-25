/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugins.externalservicediscovery;

import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * A representation of a Service object.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@JiveID( 937 )
public final class Service
{
    private final static Logger Log = LoggerFactory.getLogger( Service.class );

    private final long databaseId;

    /**
     * When sending a push update, the action value indicates if the service is being added or deleted from the set of
     * known services (or simply being modified). The defined values are "add", "remove", and "modify", where "add" is
     * the default.
     */
    enum Action {
        add,
        remove,
        modify
    }

    /**
     * Either a fully qualified domain name (FQDN) or an IP address (IPv4 or IPv6).
     *
     * Required.
     */
    private final String host;

    /**
     * A friendly (human-readable) name or label for the service.
     *
     * Optional.
     */
    private final String name;

    /**
     * The communications port to be used at the host.
     *
     * Recommended.
     */
    private final Integer port;

    /**
     * A boolean value indicating that username and password credentials are required and will need to be requested if
     * not already provided (see Requesting Credentials).
     *
     * Optional.
     */
    private final Boolean restricted;

    /**
     * The underlying transport protocol to be used when communicating with the service (typically either TCP or UDP).
     *
     * Recommended.
     */
    private final String transport;

    /**
     * The service type as registered with the XMPP Registrar.
     *
     * Required.
     */
    private final String type;

    /**
     * The (hard-coded) credentials to use in this service.
     *
     * Optional.
     */
    private final Credentials credentials;

    /**
     * A secret shared with the TURN server, used to generate ephemeral credentials.
     *
     * Optional.
     */
    private final SecretKeySpec secretKey;

    public Service( String name, String host, Integer port, String transport, String type )
    {
        if ( host == null || host.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'host' cannot be null or an empty string." );
        }

        if ( type == null || type.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null or an empty string." );
        }

        this.databaseId = SequenceManager.nextID( this );
        this.host = host;
        this.name = name;
        this.port = port;
        this.restricted = false;
        this.transport = transport;
        this.type = type;
        this.credentials = null;
        this.secretKey = null;
    }

    public Service( String name, String host, Integer port, String transport, String type, String username, String password )
    {
        if ( host == null || host.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'host' cannot be null or an empty string." );
        }

        if ( type == null || type.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null or an empty string." );
        }

        this.databaseId = SequenceManager.nextID( this );
        this.host = host;
        this.name = name;
        this.port = port;
        this.restricted = true; // technically, could be false, but that'll probably be confusing more than a feature.
        this.transport = transport;
        this.type = type;
        this.credentials = new Credentials( username, password, null );
        this.secretKey = null;
    }

    public Service( String name, String host, Integer port, String transport, String type, String sharedSecret )
    {
        if ( host == null || host.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'host' cannot be null or an empty string." );
        }

        if ( type == null || type.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null or an empty string." );
        }

        this.databaseId = SequenceManager.nextID( this );
        this.host = host;
        this.name = name;
        this.port = port;
        this.restricted = true; // technically, could be false, but that'll probably be confusing more than a feature.
        this.transport = transport;
        this.type = type;
        this.credentials = null;
        if ( sharedSecret == null || sharedSecret.isEmpty() )
        {
            this.secretKey = null;
        }
        else
        {
            this.secretKey = new SecretKeySpec( sharedSecret.getBytes( StandardCharsets.UTF_8 ), "HmacSHA1" );
        }
    }

    Service( long databaseId, String name, String host, Integer port, Boolean restricted, String transport, String type, String username, String password, String sharedSecret )
    {
        if ( host == null || host.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'host' cannot be null or an empty string." );
        }

        if ( type == null || type.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null or an empty string." );
        }

        this.databaseId = databaseId;
        this.host = host;
        this.name = name;
        this.port = port;
        this.restricted = restricted;
        this.transport = transport;
        this.type = type;
        if ( username == null && password == null )
        {
            this.credentials = null;
        }
        else
        {
            this.credentials = new Credentials( username, password, null );
        }

        if ( sharedSecret == null )
        {
            this.secretKey = null;
        }
        else
        {
            this.secretKey = new SecretKeySpec( sharedSecret.getBytes( StandardCharsets.UTF_8 ), "HmacSHA1" );
        }
    }

    public long getDatabaseId() { return databaseId; }

    public String getHost()
    {
        return host;
    }

    public String getName()
    {
        return name;
    }

    public Integer getPort()
    {
        return port;
    }

    public Boolean getRestricted()
    {
        return restricted;
    }

    public String getTransport()
    {
        return transport;
    }

    public String getType()
    {
        return type;
    }

    public String getSharedSecret()
    {
        if ( secretKey == null )
        {
            return null;
        }

        return new String( secretKey.getEncoded(), StandardCharsets.UTF_8 );
    }

    public String getRawUsername()
    {
        if ( credentials == null )
        {
            return null;
        }

        return credentials.getUsername();
    }

    String getRawPassword()
    {
        if ( credentials == null )
        {
            return null;
        }

        return credentials.getPassword();
    }

    public Credentials getCredentialsFor( JID user )
    {
        if ( credentials != null )
        {
            return credentials;
        }

        if ( secretKey != null )
        {
            final int ttl = 86400;

            final Date expires = new Date( System.currentTimeMillis() + ( ttl * 1000 ) );

            final String asSecondsSinceEpoch = Long.toString( expires.getTime() / 1000 );

            // temporary-username="timestamp" + ":" + "username"
            String username = asSecondsSinceEpoch; // The TURN server should accept usernames without an identifier-part.

            if ( user != null )
            {
                // Try to set the JID as a username part, if we can.
                try
                {
                    // Although RFC 5389 appears to allow for unescaped JIDs to be used as TURN usernames, problems have
                    // been reported (eg: https://github.com/versatica/JsSIP/issues/184)111
                    username = URLEncoder.encode( user.toBareJID(), "ASCII" ) + ":" + asSecondsSinceEpoch;
                }
                catch ( UnsupportedEncodingException e )
                {
                    Log.debug( "Unable to encode JID ''.", user.toBareJID(), e );
                }
            }

            // temporary-password = base64_encode(hmac-sha1(input = temporary-username, key = shared-secret))
            final String password;
            try
            {
                final Mac mac = Mac.getInstance( "HmacSHA1" );
                mac.init( secretKey );
                final byte[] nonce = mac.doFinal( username.getBytes( StandardCharsets.UTF_8 ) );
                password = StringUtils.encodeBase64( nonce );
            }
            catch ( InvalidKeyException | NoSuchAlgorithmException e )
            {
                Log.warn( "Unable to create ephemeral credentials for '{}' on {}:{}", user, host, port, e );
                return null;
            }

            return new Credentials( username, password, expires );
        }

        return null;
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        final Service service = (Service) o;

        if ( databaseId != service.databaseId )
        {
            return false;
        }
        if ( !host.equals( service.host ) )
        {
            return false;
        }
        if ( name != null ? !name.equals( service.name ) : service.name != null )
        {
            return false;
        }
        if ( port != null ? !port.equals( service.port ) : service.port != null )
        {
            return false;
        }
        if ( restricted != null ? !restricted.equals( service.restricted ) : service.restricted != null )
        {
            return false;
        }
        if ( transport != null ? !transport.equals( service.transport ) : service.transport != null )
        {
            return false;
        }
        if ( !type.equals( service.type ) )
        {
            return false;
        }
        if ( credentials != null ? !credentials.equals( service.credentials ) : service.credentials != null )
        {
            return false;
        }
        return secretKey != null ? secretKey.equals( service.secretKey ) : service.secretKey == null;
    }

    @Override
    public int hashCode()
    {
        int result = (int) ( databaseId ^ ( databaseId >>> 32 ) );
        result = 31 * result + host.hashCode();
        result = 31 * result + ( name != null ? name.hashCode() : 0 );
        result = 31 * result + ( port != null ? port.hashCode() : 0 );
        result = 31 * result + ( restricted != null ? restricted.hashCode() : 0 );
        result = 31 * result + ( transport != null ? transport.hashCode() : 0 );
        result = 31 * result + type.hashCode();
        result = 31 * result + ( credentials != null ? credentials.hashCode() : 0 );
        result = 31 * result + ( secretKey != null ? secretKey.hashCode() : 0 );
        return result;
    }
}
