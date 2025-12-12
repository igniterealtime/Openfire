/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.muc.spi;

import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link MUCPersistenceManager}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @author Matthew Vivian
 */
public class MUCPersistenceManagerTest
{
    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() {
        Fixtures.clearExistingProperties();
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    // =====================================
    // Tests for constructRoomSubjectMessage
    // =====================================

    /**
     * Verifies that the provided stanza is a correct representation of a 'subject change' as specified by XEP-0045.
     *
     * @param stanza The stanza that is to be tested
     * @param message A (prefix for) an error message, used when the assertion fails.
     */
    public void assertValidSubjectChangeStanza(final Packet stanza, final String message)
    {
        assertNotNull(stanza, message + ": stanza was null.");
        assertNotNull(stanza.getFrom(), message + ": stanza did not have a 'from' attribute (which is expected to be the room or an authorNickname's JID");
        assertInstanceOf(Message.class, stanza, message + ": stanza is not a Message.");
        assertNotNull(stanza.getElement().element("subject"), message + ": stanza must (but did not) have a 'subject' element (even if there's no subject, in which case the element is expected to be empty).");
        assertNull(((Message) stanza).getBody(), message + ": stanza must not (but did) have a 'body'.");
    }

    /**
     * Verifies that {@link MUCPersistenceManager#constructRoomSubjectMessage(JID, String, Instant, String)}
     * successfully generates a 'subject change' stanza, when no subject is provided.
     */
    @Test
    public void testConstructRoomSubjectMessageEmptySubject()
    {
        // Setup test fixture.
        final JID roomJID = new JID("testroom@conference.example.org");
        final String subject = null;
        final Instant date = null;
        final String authorNickname = null;

        // Execute system under test.
        final Message result = MUCPersistenceManager.constructRoomSubjectMessage(roomJID, subject, date, authorNickname);

        // Verify results.
        assertValidSubjectChangeStanza(result, "Expected the generated stanza to be a valid 'subject change' stanza, but it was not");
        assertTrue(result.getSubject() == null || result.getSubject().isEmpty(), "Didn't expect the generated stanza to have a subject (but it had).");
    }

    /**
     * Verifies that {@link MUCPersistenceManager#constructRoomSubjectMessage(JID, String, Instant, String)}
     * successfully generates a 'subject change' stanza, when a subject is provided.
     */
    @Test
    public void testConstructRoomSubjectMessageNonEmptySubject()
    {
        // Setup test fixture.
        final JID roomJID = new JID("testroom@conference.example.org");
        final String subject = "A test subject";
        final Instant date = null;
        final String authorNickname = null;

        // Execute system under test.
        final Message result = MUCPersistenceManager.constructRoomSubjectMessage(roomJID, subject, date, authorNickname);

        // Verify results.
        assertValidSubjectChangeStanza(result, "Expected the generated stanza to be a valid 'subject change' stanza, but it was not");
        assertEquals(subject, result.getSubject(), "Expected the generated stanza to reflect the subject that was used in the input (but it did not).");
    }

    /**
     * Verifies that {@link MUCPersistenceManager#constructRoomSubjectMessage(JID, String, Instant, String)}
     * successfully generates a 'subject change' stanza, when a subject and an author is provided.
     */
    @Test
    public void testConstructRoomSubjectMessageAuthor()
    {
        // Setup test fixture.
        final JID roomJID = new JID("testroom@conference.example.org");
        final String subject = "A test subject";
        final Instant date = null;
        final String authorNickname = "My Nickname";

        // Execute system under test.
        final Message result = MUCPersistenceManager.constructRoomSubjectMessage(roomJID, subject, date, authorNickname);

        // Verify results.
        assertValidSubjectChangeStanza(result, "Expected the generated stanza to be a valid 'subject change' stanza, but it was not");
        assertEquals(authorNickname, result.getFrom().getResource(), "Expected the generated stanza's 'from' address to represent the nickname of the author of the subject (but it did not).");
    }

    /**
     * Verifies that {@link MUCPersistenceManager#constructRoomSubjectMessage(JID, String, Instant, String)}
     * successfully generates a 'subject change' stanza, when a subject and a subject change date is provided.
     */
    @Test
    public void testConstructRoomSubjectDate()
    {
        // Setup test fixture.
        final JID roomJID = new JID("testroom@conference.example.org");
        final String subject = "A test subject";
        final Instant date = ZonedDateTime.of(1969, 7, 21, 2, 56, 15, 123000000, ZoneOffset.UTC).toInstant();
        final String authorNickname = null;

        // Execute system under test.
        final Message result = MUCPersistenceManager.constructRoomSubjectMessage(roomJID, subject, date, authorNickname);

        // Verify results.
        assertValidSubjectChangeStanza(result, "Expected the generated stanza to be a valid 'subject change' stanza, but it was not");
        final Element delayElement = result.getChildElement("delay", "urn:xmpp:delay");

        assertEquals("1969-07-21T02:56:15.123Z", delayElement.attributeValue("stamp"), "Expected the generated stanza's 'from' address to represent the timestamp when the subject change was applied (but it did not).");
    }

    // =====================================
    // Tests for ROOM_LOADING_WORKERS property
    // =====================================

    @Test
    public void roomLoadingWorkersPropertyDefaultsToTwo() {
        // When no property is set, the default should be 2 (parallel loading with 2 workers)
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(2));
    }

    @Test
    public void roomLoadingWorkersPropertyAcceptsValidValues() {
        // Test that valid values within range are accepted
        JiveGlobals.setProperty("xmpp.muc.loading.workers", "3");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(3));

        JiveGlobals.setProperty("xmpp.muc.loading.workers", "5");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(5));
    }

    @Test
    public void roomLoadingWorkersPropertyRejectsValueBelowMinimum() {
        // Values below 1 should be rejected and default should be used
        JiveGlobals.setProperty("xmpp.muc.loading.workers", "0");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(2));

        JiveGlobals.setProperty("xmpp.muc.loading.workers", "-1");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(2));
    }

    @Test
    public void roomLoadingWorkersPropertyRejectsValueAboveMaximum() {
        // Values above 5 should be rejected and default should be used
        JiveGlobals.setProperty("xmpp.muc.loading.workers", "6");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(2));

        JiveGlobals.setProperty("xmpp.muc.loading.workers", "10");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(2));
    }

    @Test
    public void roomLoadingWorkersPropertyRejectsNonNumericValues() {
        // Non-numeric values should be rejected and default should be used
        JiveGlobals.setProperty("xmpp.muc.loading.workers", "abc");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(2));
    }

    @Test
    public void roomLoadingWorkersPropertyAcceptsBoundaryValues() {
        // Test boundary values: minimum (1) and maximum (5)
        JiveGlobals.setProperty("xmpp.muc.loading.workers", "1");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(1));

        JiveGlobals.setProperty("xmpp.muc.loading.workers", "5");
        assertThat(MUCPersistenceManager.ROOM_LOADING_WORKERS.getValue(), is(5));
    }
}