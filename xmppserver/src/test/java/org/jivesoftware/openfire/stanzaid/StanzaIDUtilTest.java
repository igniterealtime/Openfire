/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.stanzaid;

import org.dom4j.Element;
import org.dom4j.QName;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the functionality of {@link StanzaIDUtil}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class StanzaIDUtilTest
{
    /**
     * Test if {@link StanzaIDUtil#ensureUniqueAndStableStanzaID(Packet, JID)} adds a stanza-id element
     * with proper 'by' and UUID value if the provided input does not have a 'origin-id'
     * element.
     */
    @Test
    public void testGeneratesStanzaIDElement() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final JID self = new JID( "foobar" );

        // Execute system under test.
        final Packet result = StanzaIDUtil.ensureUniqueAndStableStanzaID( input, self );

        // Verify results.
        assertNotNull( result );
        final Element stanzaIDElement = result.getElement().element( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        assertNotNull( stanzaIDElement );
        assertDoesNotThrow(() -> UUID.fromString( stanzaIDElement.attributeValue( "id" ) ));
        assertEquals( self.toString(), stanzaIDElement.attributeValue( "by" ) );
    }

    /**
     * Test if {@link StanzaIDUtil#ensureUniqueAndStableStanzaID(Packet, JID)} overwrites a stanza-id
     * element when another is present with the same 'by' value.
     */
    @Test
    public void testOverwriteStanzaIDElement() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final JID self = new JID( "foobar" );
        final String notExpected = "de305d54-75b4-431b-adb2-eb6b9e546013";
        final Element toOverwrite = input.getElement().addElement( "stanza-id", "urn:xmpp:sid:0" );
        toOverwrite.addAttribute( "by", self.toString() );
        toOverwrite.addAttribute( "id", notExpected );

        // Execute system under test.
        final Packet result = StanzaIDUtil.ensureUniqueAndStableStanzaID( input, self );

        // Verify results.
        assertNotNull( result );
        final Element stanzaIDElement = result.getElement().element( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        assertNotNull( stanzaIDElement );
        assertDoesNotThrow(() -> UUID.fromString( stanzaIDElement.attributeValue( "id" ) ));
        assertNotEquals( notExpected, stanzaIDElement.attributeValue( "id" ) );
        assertEquals( self.toString(), stanzaIDElement.attributeValue( "by" ) );
    }

    /**
     * Test if {@link StanzaIDUtil#ensureUniqueAndStableStanzaID(Packet, JID)} does not overwrites
     * a stanza-id element when another is present with a different 'by' value.
     */
    @Test
    public void testDontOverwriteStanzaIDElement() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final JID self = new JID( "foobar" );
        final String notExpected = "de305d54-75b4-431b-adb2-eb6b9e546013";
        final Element toOverwrite = input.getElement().addElement( "stanza-id", "urn:xmpp:sid:0" );
        toOverwrite.addAttribute( "by", new JID( "someoneelse" ).toString() );
        toOverwrite.addAttribute( "id", notExpected );

        // Execute system under test.
        final Packet result = StanzaIDUtil.ensureUniqueAndStableStanzaID( input, self );

        // Verify results.
        assertNotNull( result );
        final List<Element> elements = result.getElement().elements( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        assertEquals( 2, elements.size() );
    }

    /**
     * Test if {@link StanzaIDUtil#ensureUniqueAndStableStanzaID(Packet, JID)} uses a different value, if the provided
     * data has an origin-id value.
     */
    @Test
    public void testUseOriginIdElement() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final JID self = new JID( "foobar" );
        final String expected = "de305d54-75b4-431b-adb2-eb6b9e546013";
        final Element toOverwrite = input.getElement().addElement( "origin-id", "urn:xmpp:sid:0" );
        toOverwrite.addAttribute( "id", expected );

        // Execute system under test.
        final Packet result = StanzaIDUtil.ensureUniqueAndStableStanzaID( input, self );

        // Verify results.
        assertNotNull( result );
        final Element stanzaIDElement = result.getElement().element( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        assertNotNull( stanzaIDElement );
        assertDoesNotThrow(() -> UUID.fromString( stanzaIDElement.attributeValue( "id" ) ));
        assertNotEquals( expected, stanzaIDElement.attributeValue( "id" ) );
        assertEquals( self.toString(), stanzaIDElement.attributeValue( "by" ) );
    }

    /**
     * Test if {@link StanzaIDUtil#findFirstUniqueAndStableStanzaID(Packet, String)} can parse a stanza that contains a
     * stanza ID.
     */
    @Test
    public void testParseUUIDValue() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final JID self = new JID( "foobar" );
        final String expected = "de305d54-75b4-431b-adb2-eb6b9e546013";
        final Element toOverwrite = input.getElement().addElement( "stanza-id", "urn:xmpp:sid:0" );
        toOverwrite.addAttribute( "id", expected );
        toOverwrite.addAttribute( "by", self.toString() );

        // Execute system under test.
        final String result = StanzaIDUtil.findFirstUniqueAndStableStanzaID( input, self.toString() );

        // Verify results.
        assertEquals( expected, result );
    }

    /**
     * Test if {@link StanzaIDUtil#findFirstUniqueAndStableStanzaID(Packet, String)} can parse a stanza that contains a
     * stanza ID that is not a UUID value. OF-2026
     */
    @Test
    public void testParseNonUUIDValue() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final JID self = new JID( "foobar" );
        final String expected = "not-a-uuid";
        final Element toOverwrite = input.getElement().addElement( "stanza-id", "urn:xmpp:sid:0" );
        toOverwrite.addAttribute( "id", expected );
        toOverwrite.addAttribute( "by", self.toString() );

        // Execute system under test.
        final String result = StanzaIDUtil.findFirstUniqueAndStableStanzaID( input, self.toString() );

        // Verify results.
        assertEquals( expected, result );
    }
}
