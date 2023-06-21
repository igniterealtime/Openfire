/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package dom.io;

import org.dom4j.Document;
import org.dom4j.Namespace;
import org.dom4j.io.XMPPPacketReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the functionality of {@link XMPPPacketReader}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class XMPPPacketReaderTest
{
    private XMPPPacketReader packetReader;

    @BeforeEach
    public void setup()
    {
        packetReader = new XMPPPacketReader();
    }

    /**
     * Check if the 'jabber:client' default namespace declaration is stripped from a stream tag.
     *
     * Openfire strips this namespace (among others) to make the resulting XML 're-usable', in context of the
     * implementation note in RFC 6120, section 4.8.3.
     *
     * @see <a href="https://xmpp.org/rfcs/rfc6120.html#streams-ns-xmpp">RFC 6120, 4.8.3. XMPP Content Namespaces</a>
     */
    @Test
    public void testStripContentNamespace() throws Exception
    {
        // Setup fixture.
        final String input = "<stream:stream to='example.com' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'><message from='juliet@example.com' to='romeo@example.net' xml:lang='en'><body>Art thou not Romeo, and a Montague?</body></message></stream:stream>";

        // Execute system under test.
        final Document result = packetReader.read( new StringReader( input ) );

        // Verify result.
        assertFalse( result.asXML().contains( "jabber:client" ) );
    }

    /**
     * Check if the 'jabber:client' default namespace declaration is stripped from a stanza.
     *
     * Openfire strips this namespace (among others) to make the resulting XML 're-usable', in context of the
     * implementation note in RFC 6120, section 4.8.3.
     *
     * @see <a href="https://xmpp.org/rfcs/rfc6120.html#streams-ns-xmpp">RFC 6120, 4.8.3. XMPP Content Namespaces</a>
     */
    @Test
    public void testStripPrefixFreeCanonicalization() throws Exception
    {
        // Setup fixture.
        final String input = "<stream:stream to='example.com' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'><message xmlns='jabber:client' from='juliet@example.com' to='romeo@example.net' xml:lang='en'><body>Art thou not Romeo, and a Montague?</body></message></stream:stream>";

        // Execute system under test.
        final Document result = packetReader.read( new StringReader( input ) );

        // Verify result.
        assertFalse( result.asXML().contains( "jabber:client" ) );
    }

    /**
     * Check that the 'jabber:client' default namespace declaration is explicitly _not_ stripped from a child element
     * of a stanza.
     *
     * Openfire strips this namespace (among others) to make the resulting XML 're-usable', in context of the
     * implementation note in RFC 6120, section 4.8.3.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1335">Issue OF-1335: Forwarded messages rewritten to default namespace over S2S</a>
     * @see <a href="https://xmpp.org/rfcs/rfc6120.html#streams-ns-xmpp">RFC 6120, 4.8.3. XMPP Content Namespaces</a>
     */
    @Test
    public void testAvoidStrippingInternalContentNamespace() throws Exception
    {
        // Setup fixture
        final String input =
            "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' to='example.com' version='1.0'>" +
            "  <message xmlns='jabber:client'>" +
            "    <other xmlns='something:else'>" +
            "      <message xmlns='jabber:client'/>" +
            "    </other>" +
            "  </message>" +
            "</stream:stream>";

        final Document result = packetReader.read( new StringReader( input ) );

        // Verify result.
        assertFalse( result.asXML().substring( 0, result.asXML().indexOf("something:else") ).contains( "jabber:client" ), "'jabber:client' should not occur before 'something:else'" );
        assertTrue( result.asXML().substring( result.asXML().indexOf("something:else") ).contains( "jabber:client" ), "'jabber:client' should occur after 'something:else'" );
    }

    /**
     * Check that the 'jabber:client' default namespace declaration is explicitly _not_ stripped from a child element
     * of a stanza.
     *
     * Openfire strips this namespace (among others) to make the resulting XML 're-usable', in context of the
     * implementation note in RFC 6120, section 4.8.3.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1335">Issue OF-1335: Forwarded messages rewritten to default namespace over S2S</a>
     * @see <a href="https://xmpp.org/rfcs/rfc6120.html#streams-ns-xmpp">RFC 6120, 4.8.3. XMPP Content Namespaces</a>
     */
    @Test
    public void testAvoidStrippingPrefixFreeCanonicalization() throws Exception
    {
        // Setup fixture
        final String input =
            "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' to='example.com' version='1.0'>" +
                "  <message>" +
                "    <other xmlns='something:else'>" +
                "      <message xmlns='jabber:client'/>" +
                "    </other>" +
                "  </message>" +
                "</stream:stream>";

        final Document result = packetReader.read( new StringReader( input ) );

        // Verify result.
        assertFalse( result.asXML().substring( 0, result.asXML().indexOf("something:else") ).contains( "jabber:client" ), "'jabber:client' should not occur before 'something:else'" );
        assertTrue( result.asXML().substring( result.asXML().indexOf("something:else") ).contains( "jabber:client" ), "'jabber:client' should occur after 'something:else'" );
    }

    /**
     * Check that a websocket connection would also work.
     */
    @Test
    public void testStripNamespacesForWebsocket() throws Exception
    {
        final String input_header = "<open xmlns='urn:ietf:params:xml:ns:xmpp-framing' to='example.com' version='1.0' />";
        final Document doc_header = packetReader.read( new StringReader( input_header ) );
        final String input = "  <message xmlns='jabber:client'>" +
            "    <other xmlns='something:else'>" +
            "      <message xmlns='jabber:client'/>" +
            "    </other>" +
            "  </message>";
        final Document result = packetReader.read( new StringReader( input ) );

        // Verify result.
        assertFalse( result.asXML().substring( 0, result.asXML().indexOf("something:else") ).contains( "jabber:client" ), "'jabber:client' should not occur before 'something:else'" );
        assertTrue( result.asXML().substring( result.asXML().indexOf("something:else") ).contains( "jabber:client" ), "'jabber:client' should occur after 'something:else'" );
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} returns a prefix and
     * namespace declaration on the element that the parser is currently on.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_CustomNamespace() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<message xmlns:unittest='custom:namespace'/>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"message".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(1, namespacesOnCurrentElement.size());
        assertEquals(Namespace.get("unittest", "custom:namespace"), namespacesOnCurrentElement.iterator().next());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} returns a prefix and
     * namespace declaration on the element that the parser is currently on.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_XmlLang() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<message xml:lang='en'/>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"message".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(0, namespacesOnCurrentElement.size());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} does not return a
     * default namespace declaration on the element that the parser is currently on.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_DefaultNamespace() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<message xmlns='default:namespace'/>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"message".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(0, namespacesOnCurrentElement.size());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} does not return a
     * prefix and namespace declaration on the element that the parser is currently on, if the namespace matches one of
     * the namespaces in {@link XMPPPacketReader#IGNORED_NAMESPACE_ON_STANZA}.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_IgnoredNamespace() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<message xmlns:unittest='jabber:client'/>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"message".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(0, namespacesOnCurrentElement.size());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} does not return a
     * prefix and namespace declaration on the element that the parser is currently on, if the namespace matches one of
     * the 'http://etherx.jabber.org/streams' namespace.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_IgnoredStreamNamespace() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<message xmlns:stream='http://etherx.jabber.org/streams'/>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"message".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(0, namespacesOnCurrentElement.size());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} only returns a prefix
     * and namespace declaration on the element that the parser is currently on, when that element also declares data
     * that is to be ignored.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_MixedNamespace() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<stream xmlns='custom:default:namespace' xmlns:xmpp='jabber:client' xmlns:unittest='custom:namespace'/>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"stream".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(1, namespacesOnCurrentElement.size());
        assertEquals(Namespace.get("unittest", "custom:namespace"), namespacesOnCurrentElement.iterator().next());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} does not return data
     * that is declared on a child element of the element that the parser is currently on.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_MixedNamespace_WithChildElement() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<stream xmlns='custom:default:namespace' xmlns:xmpp='jabber:client' xmlns:unittest='custom:namespace'>" +
            "  <child xmlns:foo='bar'/>" +
            "</stream>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to the root element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"stream".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(1, namespacesOnCurrentElement.size());
        assertEquals(Namespace.get("unittest", "custom:namespace"), namespacesOnCurrentElement.iterator().next());
    }

    /**
     * Verify that {@link XMPPPacketReader#getPrefixedNamespacesOnCurrentElement(XmlPullParser)} does not return data
     * that is declared on a parent element of the element that the parser is currently on.
     */
    @Test
    public void test_GetNamespacesOnCurrentElement_MixedNamespace_OnChildElement() throws Exception
    {
        // Setup test fixture.
        packetReader.getXPPParser().resetInput();
        final String input = "<stream xmlns:foo='bar'>" +
            "  <child xmlns='custom:default:namespace' xmlns:xmpp='jabber:client' xmlns:unittest='custom:namespace'/>" +
            "</stream>";

        packetReader.getXPPParser().setInput(new StringReader(input));
        int type;
        do { // Progress parsing to CHILD element.
            type = packetReader.getXPPParser().next();
        } while (type != XmlPullParser.START_TAG || !"child".equals(packetReader.getXPPParser().getName()));

        // Execute system under test.
        final Set<Namespace> namespacesOnCurrentElement = XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(packetReader.getXPPParser());

        // Verify results.
        assertEquals(1, namespacesOnCurrentElement.size());
        assertEquals(Namespace.get("unittest", "custom:namespace"), namespacesOnCurrentElement.iterator().next());
    }
}
