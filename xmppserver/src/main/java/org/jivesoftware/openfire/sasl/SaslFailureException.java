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

import javax.security.sasl.SaslException;

/**
 * A SaslException with XMPP 'failure' context.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SaslFailureException extends SaslException
{
    private final Failure failure;

    public SaslFailureException( Failure failure, String message )
    {
        super( message );
        this.failure = failure;
    }

    public SaslFailureException( Failure failure )
    {
        this.failure = failure;
    }

    public SaslFailureException( String detail, Failure failure )
    {
        super( detail );
        this.failure = failure;
    }

    public SaslFailureException( String detail, Throwable ex, Failure failure )
    {
        super( detail, ex );
        this.failure = failure;
    }

    public Failure getFailure()
    {
        return failure;
    }
}
