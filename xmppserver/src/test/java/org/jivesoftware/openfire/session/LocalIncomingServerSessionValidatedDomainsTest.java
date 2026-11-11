/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that the domains that were validated for a {@link LocalIncomingServerSession} can be read while a domain is
 * validated (OF-3420). Domains are validated by the thread that processes data from the remote server, while other
 * threads, such as the one that closes the session, read them.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class LocalIncomingServerSessionValidatedDomainsTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private LocalIncomingServerSession session;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        final Connection connection = mock(Connection.class, withSettings().lenient());
        session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), REMOTE_DOMAIN);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that the validated domains can be iterated while a domain is validated. The domain is validated between
     * two steps of the iteration, which stands in for another thread that validates it at that moment.
     */
    @Test
    public void validatedDomainsCanBeIteratedWhileDomainIsValidated()
    {
        // Setup test fixture.
        session.addValidatedDomain(REMOTE_DOMAIN);
        session.addValidatedDomain("other.example.org");
        final Iterator<String> iterator = session.getValidatedDomains().iterator();
        final List<String> iterated = new ArrayList<>();
        iterated.add(iterator.next());

        // Execute system under test.
        session.addValidatedDomain("third.example.org");
        assertDoesNotThrow(() -> iterator.forEachRemaining(iterated::add), "Iterating the validated domains must not fail when a domain is validated at the same time.");

        // Verify result.
        assertTrue(iterated.containsAll(List.of(REMOTE_DOMAIN, "other.example.org")), "The domains that were validated before the iteration started must be iterated, but were: " + iterated);
        assertTrue(session.isValidDomain("third.example.org"), "The domain that was validated during the iteration must be valid.");
    }
}
