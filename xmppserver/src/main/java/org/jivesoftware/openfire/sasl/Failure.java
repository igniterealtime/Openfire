/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.sasl;

/**
 * XMPP specified SASL errors.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://tools.ietf.org/html/rfc6120#section-6.5">RFC 6120 section 6.5</a>
 */
public enum Failure
{
    ABORTED( "aborted" ),
    ACCOUNT_DISABLED( "account-disabled" ),
    CREDENTIALS_EXPIRED( "credentials-expired" ),
    ENCRYPTION_REQUIRED( "encryption-required" ),
    INCORRECT_ENCODING( "incorrect-encoding" ),
    INVALID_AUTHZID( "invalid-authzid" ),
    INVALID_MECHANISM( "invalid-mechanism" ),
    MALFORMED_REQUEST( "malformed-request" ),
    MECHANISM_TOO_WEAK( "mechanism-too-weak" ),
    NOT_AUTHORIZED( "not-authorized" ),
    TEMPORARY_AUTH_FAILURE( "temporary-auth-failure" );

    private String name = null;

    Failure( String name )
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
