package org.jivesoftware.openfire.http;

import org.dom4j.QName;
import org.junit.Test;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link HttpSession.Deliverable}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class HttpSessionDeliverable
{
    /**
     * Verifies that the default namespace is set on empty stanzas.
     *
     * @see <a href="https://igniterealtime.org/issues/browse/OF-1087">OF-1087</a>
     */
    @Test
    public void testNamespaceOnEmptyStanza() throws Exception
    {
        // Setup fixture
        final Message message = new Message();
        message.addChildElement( "unittest", "unit:test:namespace" );
        final List<Packet> packets = new ArrayList<>();
        packets.add( message );

        // Execute system under test
        final HttpSession.Deliverable deliverable = new HttpSession.Deliverable( packets );
        final String result = deliverable.getDeliverable();

        // verify results
        // Note that this assertion depends on the Openfire XML parser-specific ordering of attributes.
        assertEquals( "<message xmlns=\"jabber:client\"><unittest xmlns=\"unit:test:namespace\"/></message>", result );
    }

    /**
     * Verifies that the default namespace is set on empty stanzas (that do not have a child element)
     *
     * @see <a href="https://igniterealtime.org/issues/browse/OF-1087">OF-1087</a>
     */
    @Test
    public void testNamespaceOnEmptyStanzaWithoutChildElement() throws Exception
    {
        // Setup fixture
        final Message message = new Message();
        final List<Packet> packets = new ArrayList<>();
        packets.add( message );

        // Execute system under test
        final HttpSession.Deliverable deliverable = new HttpSession.Deliverable( packets );
        final String result = deliverable.getDeliverable();

        // verify results
        // Note that this assertion depends on the Openfire XML parser-specific ordering of attributes.
        assertEquals( "<message xmlns=\"jabber:client\"/>", result );
    }

    /**
     * Verifies that the default namespace is set on (non-empty) stanzas.
     *
     * @see <a href="https://igniterealtime.org/issues/browse/OF-1087">OF-1087</a>
     */
    @Test
    public void testNamespaceOnStanza() throws Exception
    {
        // Setup fixture
        final Message message = new Message();
        message.setTo( "unittest@example.org/test" );
        message.addChildElement( "unittest", "unit:test:namespace" );
        final List<Packet> packets = new ArrayList<>();
        packets.add( message );

        // Execute system under test
        final HttpSession.Deliverable deliverable = new HttpSession.Deliverable( packets );
        final String result = deliverable.getDeliverable();

        // verify results
        // Note that this assertion depends on the Openfire XML parser-specific ordering of attributes.
        assertEquals( "<message to=\"unittest@example.org/test\" xmlns=\"jabber:client\"><unittest xmlns=\"unit:test:namespace\"/></message>", result );
    }

    /**
     * Verifies that the default namespace is not set on stanzas that already have defined a default namespace.
     *
     * @see <a href="https://igniterealtime.org/issues/browse/OF-1087">OF-1087</a>
     */
    @Test
    public void testNamespaceOnStanzaWithNamespace() throws Exception
    {
        // Setup fixture
        final Message message = new Message();
        message.getElement().setQName( QName.get( "message", "unit:test:preexisting:namespace" ) );
        message.setTo( "unittest@example.org/test" );
        message.addChildElement( "unittest", "unit:test:namespace" );
        final List<Packet> packets = new ArrayList<>();
        packets.add( message );

        // Execute system under test
        final HttpSession.Deliverable deliverable = new HttpSession.Deliverable( packets );
        final String result = deliverable.getDeliverable();

        // verify results
        // Note that this assertion depends on the Openfire XML parser-specific ordering of attributes.
        assertEquals( "<message xmlns=\"unit:test:preexisting:namespace\" to=\"unittest@example.org/test\"><unittest xmlns=\"unit:test:namespace\"/></message>", result );
    }
}
