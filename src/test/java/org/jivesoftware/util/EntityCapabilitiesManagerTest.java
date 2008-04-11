/**
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Element;
import org.junit.Test;
import org.xmpp.packet.IQ;

/**
 * Test cases for the EntityCapabilitiesManager class.
 *
 * @author Armando Jagucki
 */
public class EntityCapabilitiesManagerTest {

    @Test
    public void testGenerateVerHash() {

        IQ iq = new IQ(IQ.Type.result);
        iq.setFrom("nurse@capulet.lit/chamber");
        iq.setTo("juliet@capulet.lit");
        iq.setID("disco123");

        Element query = iq.setChildElement("query", "http://jabber.org/protocol/disco#info");

        Element identity = query.addElement("identity");
        identity.addAttribute("category", "client");
        identity.addAttribute("type", "pc");

        Element feature = query.addElement("feature");
        feature.addAttribute("var", "http://jabber.org/protocol/disco#info");
        feature = query.addElement("feature");
        feature.addAttribute("var", "http://jabber.org/protocol/disco#items");
        feature = query.addElement("feature");
        feature.addAttribute("var", "http://jabber.org/protocol/muc");

        assertEquals("Generating ver Hash #1", "8RovUdtOmiAjzj+xI7SK5BCw3A8=", generateVerHash(iq));

    }

    @Test
    public void testGenerateVerHash2() {
        String S = "client/pc<http://jabber.org/protocol/disco#info<http://jabber.org/protocol/disco#items<http://jabber.org/protocol/muc<";
        assertEquals("Generating ver Hash #2", "8RovUdtOmiAjzj+xI7SK5BCw3A8=", StringUtils.encodeBase64(StringUtils.decodeHex(StringUtils.hash(S, "SHA-1"))));

    }
    
    @Test
    public void testGenerateVerHash3() {
        String S = "client/pda<http://jabber.org/protocol/geoloc<http://jabber.org/protocol/geoloc+notify<http://jabber.org/protocol/tune<http://jabber.org/protocol/tune+notify<";
        assertEquals("Generating ver Hash #3", "DqGwXvV/QC6X9QrPOFAwJoDwHkk=", StringUtils.encodeBase64(StringUtils.decodeHex(StringUtils.hash(S, "SHA-1"))));

    }
    
    @Test
    public void testGenerateVerHash4() {
        String S = "client/pc<http://jabber.org/protocol/activity<http://jabber.org/protocol/activity+notify<http://jabber.org/protocol/geoloc<http://jabber.org/protocol/geoloc+notify<http://jabber.org/protocol/muc<http://jabber.org/protocol/tune<http://jabber.org/protocol/tune+notify<";
        assertEquals("Generating ver Hash #4", "Hm1UHUVZowSehEBlWo8lO8mPy/M=", StringUtils.encodeBase64(StringUtils.decodeHex(StringUtils.hash(S, "SHA-1"))));

    }

    /**
     * Generates a 'ver' hash attribute.
     * 
     * In order to help prevent poisoning of entity capabilities information,
     * the value of the 'ver' attribute is generated according to the method
     * outlined in XEP-0115.
     * 
     * @param packet
     * @return the generated 'ver' hash
     */
    public String generateVerHash(IQ packet) {
        // Initialize an empty string S.
        String S = "";

        /*
         * Sort the service discovery identities by category and then by type
         * (if it exists), formatted as 'category' '/' 'type'.
         */
        List<String> discoIdentities = new ArrayList<String>();
        Element query = packet.getChildElement();
        Iterator identitiesIterator = query.elementIterator("identity");
        if (identitiesIterator != null) {
            while (identitiesIterator.hasNext()) {
                Element identityElement = (Element) identitiesIterator.next();

                String discoIdentity = identityElement.attributeValue("category");
                discoIdentity += '/';
                discoIdentity += identityElement.attributeValue("type");

                discoIdentities.add(discoIdentity);
            }
            Collections.sort(discoIdentities);
        }

        /*
         * For each identity, append the 'category/type' to S, followed by the
         * '<' character.
         */
        for (String discoIdentity : discoIdentities) {
            S += discoIdentity;
            S += '<';
        }

        // Sort the supported features.
        List<String> discoFeatures = new ArrayList<String>();
        Iterator featuresIterator = query.elementIterator("feature");
        if (featuresIterator != null) {
            while (featuresIterator.hasNext()) {
                Element featureElement = (Element) featuresIterator.next();
                String discoFeature = featureElement.attributeValue("var");
                discoFeatures.add(discoFeature);
            }
            Collections.sort(discoFeatures);
        }

        /*
         * For each feature, append the feature to S, followed by the '<'
         * character.
         */
        for (String discoFeature : discoFeatures) {
            S += discoFeature;
            S += '<';
        }

        /*
         * Compute ver by hashing S using the SHA-1 algorithm as specified in
         * RFC 3174 (with binary output) and encoding the hash using Base64 as
         * specified in Section 4 of RFC 4648 (note: the Base64 output
         * MUST NOT include whitespace and MUST set padding bits to zero).
         */
        S = StringUtils.hash(S, "SHA-1");
        S = StringUtils.encodeBase64(StringUtils.decodeHex(S));

        return S;
    }
}
