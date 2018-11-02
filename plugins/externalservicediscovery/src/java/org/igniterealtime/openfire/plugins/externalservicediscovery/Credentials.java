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

import java.util.Date;

/**
 * Representation of service credentials.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class Credentials
{
    /**
     * A timestamp indicating when the provided username and password credentials will expire. The format MUST adhere
     * to the dateTime format specified in XMPP Date and Time Profiles (XEP-0082) [12] and MUST be expressed in UTC.
     *
     * Optional.
     */
    private final Date expires;

    /**
     * A service- or server-generated password for use at the service.
     *
     * Optional.
     */
    private final String password;

    /**
     * A service- or server-generated username for use at the service.
     *
     * Optional.
     */
    private final String username;

    public Credentials( String username, String password, Date expires )
    {
        this.username = username;
        this.password = password;
        this.expires = expires;
    }

    public Date getExpires()
    {
        return expires;
    }

    public String getPassword()
    {
        return password;
    }

    public String getUsername()
    {
        return username;
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

        final Credentials that = (Credentials) o;

        if ( expires != null ? !expires.equals( that.expires ) : that.expires != null )
        {
            return false;
        }
        if ( password != null ? !password.equals( that.password ) : that.password != null )
        {
            return false;
        }
        return username != null ? username.equals( that.username ) : that.username == null;
    }

    @Override
    public int hashCode()
    {
        int result = expires != null ? expires.hashCode() : 0;
        result = 31 * result + ( password != null ? password.hashCode() : 0 );
        result = 31 * result + ( username != null ? username.hashCode() : 0 );
        return result;
    }
}
