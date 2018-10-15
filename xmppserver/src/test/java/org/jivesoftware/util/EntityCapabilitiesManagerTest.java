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

package org.jivesoftware.util;

import static org.junit.Assert.assertEquals;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmpp.packet.IQ;

/**
 * Test cases for the {@link EntityCapabilitiesManager} class.
 * 
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0115.html">XEP-0115:&nbsp;Entity&nbsp;Capabilities</a>
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class EntityCapabilitiesManagerTest {

    @BeforeClass
    public static void setUp() throws Exception {
        CacheFactory.initialize();
    }

    /**
     * Tests the CAPS verification string generation based on the
     * "Simple Generation Example" provided in section 5.2 of XEP-0115 (version
     * 1.4 and later).
     */
    @Test
    public void testSimpleGenerationExample() throws Exception {
        // formulate the result stanza
        final IQ iq = new IQ(IQ.Type.result);
        iq.setFrom("nurse@capulet.lit/chamber");
        iq.setTo("juliet@capulet.lit");
        iq.setID("simpleexample1");

        final Element query = iq.setChildElement("query",
                "http://jabber.org/protocol/disco#info");

        // Consider an entity whose category is "client", whose service
        // discovery type is "pc", service discovery name is "Exodus 0.9.1"
        // (...)
        final Element identity = query.addElement("identity");
        identity.addAttribute("category", "client");
        identity.addAttribute("type", "pc");
        identity.addAttribute("name", "Exodus 0.9.1");

        // (...) and whose supported features are
        // "http://jabber.org/protocol/disco#info",
        // "http://jabber.org/protocol/disco#items",
        // "http://jabber.org/protocol/muc" and
        // "http://jabber.org/protocol/caps"
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/disco#info");
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/disco#items");
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/muc");
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/caps");

        // Using the SHA-1 algorithm (...)
        final String verification = EntityCapabilitiesManager.generateVerHash(
                iq, "sha-1");

        // the verification string result must be QgayPKawpkPSDYmwT/WM94uAlu0=
        assertEquals("QgayPKawpkPSDYmwT/WM94uAlu0=", verification);
    }

    /**
     * Tests the CAPS verification string generation based on the
     * "Complex Generation Example" provided in section 5.3 of XEP-0115 (version
     * 1.4 and later).
     */
    @Test
    public void testComplexGenerationExample() throws Exception {
        // formulate the result stanza
        final IQ iq = new IQ(IQ.Type.result);
        iq.setFrom("nurse@capulet.lit/chamber");
        iq.setTo("juliet@capulet.lit");
        iq.setID("simpleexample1");

        final Element query = iq.setChildElement("query",
                "http://jabber.org/protocol/disco#info");
        query.addAttribute("node",
                "http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w=");

        // Two identities: "client/pc/Psi" and "client/pc/"
        final Element identityA = query.addElement("identity");
        identityA.addAttribute("category", "client");
        identityA.addAttribute("type", "pc");
        identityA.addAttribute("name", "Psi 0.11");
        identityA.addAttribute("xml:lang", "en");

        final Element identityB = query.addElement("identity");
        identityB.addAttribute("category", "client");
        identityB.addAttribute("type", "pc");
        identityB.addAttribute("name", "\u03a8 0.11");
        identityB.addAttribute("xml:lang", "el");

        // the features: "http://jabber.org/protocol/caps",
        // http://jabber.org/protocol/disco#info",
        // "http://jabber.org/protocol/disco#items",
        // "http://jabber.org/protocol/muc".
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/disco#info");
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/disco#items");
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/muc");
        query.addElement("feature").addAttribute("var",
                "http://jabber.org/protocol/caps");

        // extended service discovery forms
        final Element ext = query.addElement(QName.get("x", "jabber:x:data"));
        ext.addAttribute("type", "result");

        final Element formField = ext.addElement("field");
        formField.addAttribute("var", "FORM_TYPE");
        formField.addAttribute("type", "hidden");
        formField.addElement("value")
                .setText("urn:xmpp:dataforms:softwareinfo");

        final Element ipField = ext.addElement("field");
        ipField.addAttribute("var", "ip_version");
        ipField.addElement("value").setText("ipv4");
        ipField.addElement("value").setText("ipv6");

        final Element osField = ext.addElement("field");
        osField.addAttribute("var", "os");
        osField.addElement("value").setText("Mac");

        final Element osvField = ext.addElement("field");
        osvField.addAttribute("var", "os_version");
        osvField.addElement("value").setText("10.5.1");

        final Element softwareField = ext.addElement("field");
        softwareField.addAttribute("var", "software");
        softwareField.addElement("value").setText("Psi");

        final Element softwarevField = ext.addElement("field");
        softwarevField.addAttribute("var", "software_version");
        softwarevField.addElement("value").setText("0.11");

        // Using the SHA-1 algorithm (...)
        final String verification = EntityCapabilitiesManager.generateVerHash(
                iq, "SHA-1");

        // the verification string result must be q07IKJEyjvHSyhy//CH0CxmKi8w=
        assertEquals("q07IKJEyjvHSyhy//CH0CxmKi8w=", verification);
    }
}
