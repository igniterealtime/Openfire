/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.security.Provider;

/**
 * This is the Provider object providing the SaslServerFactory written by Jive Software. 
 *
 * @see SaslServerFactoryImpl
 */

public class SaslProvider extends Provider {

    /**
     * Constructs a the JiveSoftware SASL provider.
     */
    public SaslProvider()
    {
        super("JiveSoftware", 1.1, "JiveSoftware Openfire SASL provider v1.1" );

        final SaslServerFactoryImpl serverFactory = new SaslServerFactoryImpl();
        for ( final String name : serverFactory.getMechanismNames( null ) )
        {
            put( "SaslServerFactory." + name, serverFactory.getClass().getCanonicalName() );
        }
    }
}
