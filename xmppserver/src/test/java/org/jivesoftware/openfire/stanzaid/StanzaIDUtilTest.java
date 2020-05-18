package org.jivesoftware.openfire.stanzaid;

import org.dom4j.Element;
import org.dom4j.QName;
import org.junit.Assert;
import org.junit.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests that verify the functionality of {@link StanzaID}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class StanzaIDUtilTest
{
    /**
     * Test if {@link StanzaIDUtil.generateUniqueAndStableStanzaID} generates a UUID value
     * if the provided input does not have a 'origin-id' value
     */
    @Test
    public void testGenerateUUIDWhenNoOriginIDPresent() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();

        // Execute system under test.
        final String result = StanzaIDUtil.generateUniqueAndStableStanzaID( input );

        // Verify results.
        Assert.assertNotNull( result );
        try
        {
            UUID.fromString( result );
        }
        catch ( IllegalArgumentException ex )
        {
            Assert.fail();
        }
    }

    /**
     * Test if {@link StanzaIDUtil.generateUniqueAndStableStanzaID} uses the 'origin-id' provided value,
     * if that's present.
     */
    @Test
    public void testUseOriginIDWhenPresent() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final String expected = "de305d54-75b4-431b-adb2-eb6b9e546013";
        input.getElement().addElement( "origin-id", "urn:xmpp:sid:0" ).addAttribute( "id", expected );

        // Execute system under test.
        final String result = StanzaIDUtil.generateUniqueAndStableStanzaID( input );

        // Verify results.
        assertEquals( expected, result );
    }

    /**
     * Test if {@link StanzaIDUtil.generateUniqueAndStableStanzaID} uses the 'origin-id' provided value,
     * if that's present, even when the value is not a UUID.
     */
    @Test
    public void testUseOriginIDWhenPresentNonUUID() throws Exception
    {
        // Setup fixture.
        final Packet input = new Message();
        final String expected = "not-a-uuid";
        input.getElement().addElement( "origin-id", "urn:xmpp:sid:0" ).addAttribute( "id", expected );

        // Execute system under test.
        final String result = StanzaIDUtil.generateUniqueAndStableStanzaID( input );

        // Verify results.
        assertEquals( expected, result );
    }

    /**
     * Test if {@link StanzaIDUtil.ensureUniqueAndStableStanzaID} adds a stanza-id element
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
        Assert.assertNotNull( result );
        final Element stanzaIDElement = result.getElement().element( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        Assert.assertNotNull( stanzaIDElement );
        try
        {
            UUID.fromString( stanzaIDElement.attributeValue( "id" ) );
        }
        catch ( IllegalArgumentException ex )
        {
            Assert.fail();
        }
        assertEquals( self.toString(), stanzaIDElement.attributeValue( "by" ) );
    }

    /**
     * Test if {@link StanzaIDUtil.ensureUniqueAndStableStanzaID} overwrites a stanza-id
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
        Assert.assertNotNull( result );
        final Element stanzaIDElement = result.getElement().element( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        Assert.assertNotNull( stanzaIDElement );
        try
        {
            UUID.fromString( stanzaIDElement.attributeValue( "id" ) );
        }
        catch ( IllegalArgumentException ex )
        {
            Assert.fail();
        }
        Assert.assertNotEquals( notExpected, stanzaIDElement.attributeValue( "id" ) );
        assertEquals( self.toString(), stanzaIDElement.attributeValue( "by" ) );
    }

    /**
     * Test if {@link StanzaIDUtil.ensureUniqueAndStableStanzaID} does not overwrites
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
        Assert.assertNotNull( result );
        final List<Element> elements = result.getElement().elements( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        assertEquals( 2, elements.size() );
    }

    /**
     * Test if {@link StanzaIDUtil.ensureUniqueAndStableStanzaID} uses the provided
     * origin-id value, if there's one.
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
        Assert.assertNotNull( result );
        final Element stanzaIDElement = result.getElement().element( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        Assert.assertNotNull( stanzaIDElement );
        try
        {
            UUID.fromString( stanzaIDElement.attributeValue( "id" ) );
        }
        catch ( IllegalArgumentException ex )
        {
            Assert.fail();
        }
        Assert.assertEquals( expected, stanzaIDElement.attributeValue( "id" ) );
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
