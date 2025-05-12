/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for the {@link EntityCapabilitiesManager} class.
 * 
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0115.html">XEP-0115:&nbsp;Entity&nbsp;Capabilities</a>
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class EntityCapabilitiesManagerTest {

    @BeforeAll
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

    @Test
    public void testEncodingOfResult() throws Exception
    {
        // Setup test fixture.
        final String input = "pubsub/pep//<server/im//Openfire Server<http://jabber.org/protocol/address<http://jabber.org/protocol/caps<http://jabber.org/protocol/commands<http://jabber.org/protocol/disco#info<http://jabber.org/protocol/disco#items<http://jabber.org/protocol/offline<http://jabber.org/protocol/pubsub<http://jabber.org/protocol/pubsub#access-open<http://jabber.org/protocol/pubsub#auto-create<http://jabber.org/protocol/pubsub#auto-subscribe<http://jabber.org/protocol/pubsub#collections<http://jabber.org/protocol/pubsub#config-node<http://jabber.org/protocol/pubsub#create-and-configure<http://jabber.org/protocol/pubsub#create-nodes<http://jabber.org/protocol/pubsub#delete-items<http://jabber.org/protocol/pubsub#delete-nodes<http://jabber.org/protocol/pubsub#filtered-notifications<http://jabber.org/protocol/pubsub#get-pending<http://jabber.org/protocol/pubsub#instant-nodes<http://jabber.org/protocol/pubsub#item-ids<http://jabber.org/protocol/pubsub#manage-subscriptions<http://jabber.org/protocol/pubsub#meta-data<http://jabber.org/protocol/pubsub#modify-affiliations<http://jabber.org/protocol/pubsub#multi-items<http://jabber.org/protocol/pubsub#multi-subscribe<http://jabber.org/protocol/pubsub#outcast-affiliation<http://jabber.org/protocol/pubsub#persistent-items<http://jabber.org/protocol/pubsub#presence-notifications<http://jabber.org/protocol/pubsub#publish<http://jabber.org/protocol/pubsub#publish-options<http://jabber.org/protocol/pubsub#publisher-affiliation<http://jabber.org/protocol/pubsub#purge-nodes<http://jabber.org/protocol/pubsub#retract-items<http://jabber.org/protocol/pubsub#retrieve-affiliations<http://jabber.org/protocol/pubsub#retrieve-default<http://jabber.org/protocol/pubsub#retrieve-items<http://jabber.org/protocol/pubsub#retrieve-subscriptions<http://jabber.org/protocol/pubsub#subscribe<http://jabber.org/protocol/pubsub#subscription-options<http://jabber.org/protocol/rsm<jabber:iq:last<jabber:iq:privacy<jabber:iq:private<jabber:iq:register<jabber:iq:roster<jabber:iq:version<msgoffline<urn:xmpp:archive:auto<urn:xmpp:archive:manage<urn:xmpp:blocking<urn:xmpp:bookmarks-conversion:0<urn:xmpp:carbons:2<urn:xmpp:extdisco:1<urn:xmpp:extdisco:2<urn:xmpp:fulltext:0<urn:xmpp:mam:0<urn:xmpp:mam:1<urn:xmpp:mam:2<urn:xmpp:ping<urn:xmpp:push:0<urn:xmpp:raa:0<urn:xmpp:raa:0#embed-message <urn:xmpp:raa:0#embed-presence-directed<urn:xmpp:raa:0#embed-presence-sub<urn:xmpp:serverinfo:0<urn:xmpp:time<vcard-temp<http://jabber.org/network/serverinfo<admin-addresses<mailto:benjamin@holyarmy.org<mailto:dan.caseley@surevine.com<mailto:greg.d.thomas@gmail.com<mailto:guus.der.kinderen@gmail.com<mailto:robincollier@hotmail.com<xmpp:akrherz@igniterealtime.org<xmpp:benjamin@igniterealtime.org<xmpp:csh@igniterealtime.org<xmpp:dan.caseley@igniterealtime.org<xmpp:dwd@dave.cridland.net<xmpp:flow@igniterealtime.org<xmpp:gdt@igniterealtime.org<xmpp:guus.der.kinderen@igniterealtime.org<xmpp:lg@igniterealtime.org<xmpp:rcollier@igniterealtime.org<urn:xmpp:dataforms:softwareinfo<os<Linux<os_version<4.14.355-276.618.amzn2.x86_64 amd64 - Java 17.0.14<software<Openfire<software_version<5.0.0 Alpha<";

        // Execute system under test.
        final String hash = EntityCapabilitiesManager.generateHash(input, "SHA-1");
        final String result = EntityCapabilitiesManager.encodeHash(hash);

        // Verify results.
        assertEquals("89D/mEGBT1K0RtY28gEkGRbV2rc=", result);
    }

    @Test
    public void test() throws Exception
    {
        // Setup test fixture.
        final String raw = """
            <iq type="result" xmlns="jabber:client" to="inputmice3@igniterealtime.org/Conversations.cI4W" from="igniterealtime.org" id="L3xl8X8_kzvx">
              <query node="https://www.igniterealtime.org/projects/openfire/#Cd91QBSG4JGOCEvRsSz64xeJPMk=" xmlns="http://jabber.org/protocol/disco#info">
                <identity name="Openfire Server" type="im" category="server" xmlns="http://jabber.org/protocol/disco#info"/>
                <identity type="pep" category="pubsub" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:raa:0#embed-presence-directed" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/caps" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#retrieve-default" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#purge-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#subscription-options" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:raa:0#embed-message " xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#outcast-affiliation" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="msgoffline" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#delete-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="jabber:iq:register" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#config-node" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#retrieve-items" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#auto-create" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/disco#items" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#delete-items" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:mam:0" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:mam:1" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:mam:2" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:fulltext:0" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#persistent-items" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#create-and-configure" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#retrieve-affiliations" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:time" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#manage-subscriptions" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:bookmarks-conversion:0" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#auto-subscribe" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/offline" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#publish-options" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:carbons:2" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/address" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#collections" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#retrieve-subscriptions" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="vcard-temp" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#subscribe" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#create-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#get-pending" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:blocking" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#multi-subscribe" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#presence-notifications" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:ping" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:archive:manage" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#filtered-notifications" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:push:0" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#meta-data" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#multi-items" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#item-ids" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="jabber:iq:roster" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#instant-nodes" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#modify-affiliations" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:raa:0#embed-presence-sub" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#publisher-affiliation" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#access-open" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="jabber:iq:version" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#retract-items" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:extdisco:1" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="jabber:iq:privacy" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:extdisco:2" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/commands" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="jabber:iq:last" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:raa:0" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/pubsub#publish" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:serverinfo:0" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="urn:xmpp:archive:auto" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/disco#info" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="jabber:iq:private" xmlns="http://jabber.org/protocol/disco#info"/>
                <feature var="http://jabber.org/protocol/rsm" xmlns="http://jabber.org/protocol/disco#info"/>
                <x type="result" xmlns="jabber:x:data">
                  <field var="FORM_TYPE" type="hidden" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">urn:xmpp:dataforms:softwareinfo</value>
                  </field>
                  <field var="os" type="text-single" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">Linux</value>
                  </field>
                  <field var="os_version" type="text-single" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">4.14.355-276.618.amzn2.x86_64 amd64 - Java 17.0.14</value>
                  </field>
                  <field var="software" type="text-single" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">Openfire</value>
                  </field>
                  <field var="software_version" type="text-single" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">5.0.0 Alpha</value>
                  </field>
                </x>
                <x type="result" xmlns="jabber:x:data">
                  <field var="FORM_TYPE" type="hidden" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">http://jabber.org/network/serverinfo</value>
                  </field>
                  <field var="admin-addresses" type="list-multi" xmlns="jabber:x:data">
                    <value xmlns="jabber:x:data">xmpp:dwd@dave.cridland.net</value>
                    <value xmlns="jabber:x:data">xmpp:akrherz@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">xmpp:benjamin@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">mailto:benjamin@holyarmy.org</value>
                    <value xmlns="jabber:x:data">xmpp:csh@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">xmpp:dan.caseley@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">mailto:dan.caseley@surevine.com</value>
                    <value xmlns="jabber:x:data">xmpp:flow@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">xmpp:gdt@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">mailto:greg.d.thomas@gmail.com</value>
                    <value xmlns="jabber:x:data">xmpp:guus.der.kinderen@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">mailto:guus.der.kinderen@gmail.com</value>
                    <value xmlns="jabber:x:data">xmpp:lg@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">xmpp:rcollier@igniterealtime.org</value>
                    <value xmlns="jabber:x:data">mailto:robincollier@hotmail.com</value>
                  </field>
                </x>
              </query>
            </iq>
            """;
        final Document doc = DocumentHelper.parseText(raw);
        final IQ input = new IQ(doc.getRootElement());

        // Execute system under test.
        final String result = EntityCapabilitiesManager.generateVerHash(input, "SHA-1");

        // Verify results.
        assertEquals("89D/mEGBT1K0RtY28gEkGRbV2rc=", result);
    }
}
