package org.jivesoftware.openfire.net;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class Bind2RequestTest {
    QName bindQName = new QName("bind", new Namespace("", "urn:xmpp:bind:0"));

    @Test
    public void testFromNullElement() {
        assertNull(Bind2Request.from(null));
    }

    @Test
    public void testFromElementWithoutBindElement() {
        Element authenticate = DocumentHelper.createElement("authenticate");
        assertNull(Bind2Request.from(authenticate));
    }

    @Test
    public void testFromElementWithWrongNamespace() {
        Element authenticate = DocumentHelper.createElement("authenticate");
        Element bind = authenticate.addElement("bind");
        bind.addNamespace("", "wrong:namespace");

        assertNull(Bind2Request.from(authenticate));
    }

    @Test
    public void testFromElementWithTagOnly() {
        Element authenticate = DocumentHelper.createElement("authenticate");
        Element bind = authenticate.addElement(bindQName);
        Element tag = bind.addElement("tag");
        tag.setText("MyXMPPClient");

        Bind2Request result = Bind2Request.from(authenticate);

        assertNotNull(result);
        assertEquals("MyXMPPClient", result.getClientTag());
        assertTrue(result.getFeatureRequests().isEmpty());
    }

    @Test
    public void testFromElementWithFeatureRequestsOnly() {
        Element authenticate = DocumentHelper.createElement("authenticate");
        Element bind = authenticate.addElement(bindQName);

        bind.addElement(new QName("feature1", new Namespace("", "urn:xmpp:feature1:0")));
        bind.addElement(new QName("feature2", new Namespace("", "urn:xmpp:feature2:0")));

        Bind2Request result = Bind2Request.from(authenticate);

        assertNotNull(result);
        assertNull(result.getClientTag());
        assertEquals(2, result.getFeatureRequests().size());
        assertEquals("feature1", result.getFeatureRequests().get(0).getName());
        assertEquals("feature2", result.getFeatureRequests().get(1).getName());
    }

    @Test
    public void testFromElementWithTagAndFeatures() {
        Element authenticate = DocumentHelper.createElement("authenticate");
        Element bind = authenticate.addElement(bindQName);

        Element tag = bind.addElement("tag");
        tag.setText("MyXMPPClient");

        bind.addElement(new QName("feature", new Namespace("", "urn:xmpp:feature:0")));

        Bind2Request result = Bind2Request.from(authenticate);

        assertNotNull(result);
        assertEquals("MyXMPPClient", result.getClientTag());
        assertEquals(1, result.getFeatureRequests().size());
    }

    @Test
    public void testGenerateResourceStringWithNoTagNoId() {
        Bind2Request request = new Bind2Request(null, List.of());
        UserAgentInfo userAgentInfo = new UserAgentInfo();

        String result = request.generateResourceString(userAgentInfo);

        assertTrue(result.matches("^[0-9a-f]{16}$")); // Should be a 16-character hex string
    }

    @Test
    public void testGenerateResourceStringWithTagNoId() {
        Bind2Request request = new Bind2Request("MyClient", List.of());
        UserAgentInfo userAgentInfo = new UserAgentInfo();

        String result = request.generateResourceString(userAgentInfo);

        assertTrue(result.startsWith("MyClient/"));
        assertTrue(result.substring(9).matches("^[0-9a-f]{16}$")); // HMAC part should be 16 hex chars
    }

    @Test
    public void testGenerateResourceStringWithTagAndId() {
        Bind2Request request = new Bind2Request("MyClient", List.of());
        UserAgentInfo userAgentInfo = new UserAgentInfo();
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        userAgentInfo.setId(uuid);

        String result = request.generateResourceString(userAgentInfo);

        assertTrue(result.startsWith("MyClient/"));
        // Same UUID should generate same HMAC
        assertEquals(result, request.generateResourceString(userAgentInfo));
    }

    @Test
    public void testGenerateResourceStringDifferentTagsSameIdProduceDifferentResults() {
        UserAgentInfo userAgentInfo = new UserAgentInfo();
        String uuid = UUID.randomUUID().toString();
        userAgentInfo.setId(uuid);

        Bind2Request request1 = new Bind2Request("Client1", List.of());
        Bind2Request request2 = new Bind2Request("Client2", List.of());

        String result1 = request1.generateResourceString(userAgentInfo);
        String result2 = request2.generateResourceString(userAgentInfo);

        assertNotEquals(result1, result2);
    }

    @Test
    public void testFeatureRequestsImmutability() {
        List<Element> features = new ArrayList<>();
        Element feature = DocumentHelper.createElement("feature");
        features.add(feature);

        Bind2Request request = new Bind2Request("MyClient", features);

        assertThrows(UnsupportedOperationException.class, () -> {
            request.getFeatureRequests().add(DocumentHelper.createElement("newfeature"));
        });
    }
}
