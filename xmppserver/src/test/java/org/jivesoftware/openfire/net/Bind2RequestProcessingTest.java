package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * Tests for the processFeatureRequests method and featureElement advertisement in Bind2Request.
 */
public class Bind2RequestProcessingTest {

    @Mock
    private Bind2InlineHandler mockHandler1;

    @Mock
    private Bind2InlineHandler mockHandler2;

    @Mock
    private LocalClientSession mockSession;

    private Element successElement;
    private Element featureElement1;
    private Element featureElement2;

    /**
     * Returns a Mockito argument matcher that matches an Element by its local name and namespace URI,
     * rather than by object identity.
     */
    private static Element elementWithNameAndNS(String localName, String namespaceURI) {
        return argThat(new BaseMatcher<Element>() {
            @Override
            public boolean matches(Object item) {
                if (!(item instanceof Element)) return false;
                Element el = (Element) item;
                return localName.equals(el.getName()) && namespaceURI.equals(el.getNamespaceURI());
            }
            @Override
            public void describeTo(Description description) {
                description.appendText("Element with name='" + localName + "' and namespace='" + namespaceURI + "'");
            }
        });
    }

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        successElement = DocumentHelper.createElement("success");

        QName feature1 = new QName("feature1", new Namespace("", "http://test1.namespace"));
        featureElement1 = DocumentHelper.createElement(feature1);

        QName feature2 = new QName("feature2", new Namespace("", "http://test2.namespace"));
        featureElement2 = DocumentHelper.createElement(feature2);

        when(mockHandler1.getNamespace()).thenReturn("http://test1.namespace");
        when(mockHandler2.getNamespace()).thenReturn("http://test2.namespace");
        when(mockHandler1.handleElement(any(), any(), any())).thenReturn(true);
        when(mockHandler2.handleElement(any(), any(), any())).thenReturn(true);
    }

    @AfterEach
    public void tearDown() {
        Bind2Request.unregisterElementHandler("http://test1.namespace");
        Bind2Request.unregisterElementHandler("http://test2.namespace");
        Bind2Request.unregisterElementHandler("http://unhandled.namespace");
    }

    // -------------------------------------------------------------------------
    // processFeatureRequests tests — varying featureRequests content
    // -------------------------------------------------------------------------

    @Test
    public void testProcessFeatureRequestsWithBothFeatures() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Arrays.asList(featureElement1, featureElement2));
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        assertEquals("bound", result.getName());
        assertEquals("urn:xmpp:bind:0", result.getNamespaceURI());
        verify(mockHandler1).handleElement(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1));
        verify(mockHandler2).handleElement(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement2));
    }

    @Test
    public void testProcessFeatureRequestsWithOnlyFirstFeature() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Collections.singletonList(featureElement1));
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        verify(mockHandler1).handleElement(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1));
        verify(mockHandler2, never()).handleElement(any(), any(), any());
    }

    @Test
    public void testProcessFeatureRequestsWithOnlySecondFeature() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Collections.singletonList(featureElement2));
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        verify(mockHandler1, never()).handleElement(any(), any(), any());
        verify(mockHandler2).handleElement(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement2));
    }

    @Test
    public void testProcessFeatureRequestsWithNoFeatures() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Collections.emptyList());
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        verify(mockHandler1, never()).handleElement(any(), any(), any());
        verify(mockHandler2, never()).handleElement(any(), any(), any());
    }

    @Test
    public void testProcessFeatureRequestsWithNoRegisteredHandlers() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Arrays.asList(featureElement1, featureElement2));

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        verify(mockHandler1, never()).handleElement(any(), any(), any());
        verify(mockHandler2, never()).handleElement(any(), any(), any());
    }

    @Test
    public void testProcessFeatureRequestsWithPartialHandlers() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Arrays.asList(featureElement1, featureElement2));
        Bind2Request.registerElementHandler(mockHandler1);

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        verify(mockHandler1).handleElement(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1));
        verify(mockHandler2, never()).handleElement(any(), any(), any());
    }

    @Test
    public void testProcessFeatureRequestsWithHandlerException() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Arrays.asList(featureElement1, featureElement2));
        when(mockHandler1.handleElement(any(), any(), any())).thenThrow(new RuntimeException("Test exception"));
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        Element result = assertDoesNotThrow(() ->
            bind2Request.processFeatureRequests(mockSession, successElement));

        assertNotNull(result);
        verify(mockHandler1).handleElement(any(), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1));
        verify(mockHandler2).handleElement(any(), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement2));
    }

    @Test
    public void testProcessFeatureRequestsWithHandlerReturnsFalse() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Collections.singletonList(featureElement1));
        when(mockHandler1.handleElement(any(), any(), any())).thenReturn(false);
        Bind2Request.registerElementHandler(mockHandler1);

        Element result = assertDoesNotThrow(() ->
            bind2Request.processFeatureRequests(mockSession, successElement));

        assertNotNull(result);
        verify(mockHandler1).handleElement(any(), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1));
    }

    @Test
    public void testProcessFeatureRequestsCreatesBoundElement() {
        Bind2Request bind2Request = new Bind2Request("clientTag", Arrays.asList(featureElement1, featureElement2));

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        assertEquals("bound", result.getName());
        assertEquals("urn:xmpp:bind:0", result.getNamespaceURI());
    }

    @Test
    public void testProcessFeatureRequestsWithNullClientTag() {
        Bind2Request bind2Request = new Bind2Request(null, Collections.singletonList(featureElement1));
        Bind2Request.registerElementHandler(mockHandler1);

        Element result = bind2Request.processFeatureRequests(mockSession, successElement);

        assertNotNull(result);
        verify(mockHandler1).handleElement(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1));
    }

    // -------------------------------------------------------------------------
    // featureElement (stream features advertisement) tests
    // -------------------------------------------------------------------------

    @Test
    public void testFeatureElementWithNoHandlers() {
        Element feature = Bind2Request.featureElement();

        assertNotNull(feature);
        assertEquals("bind", feature.getName());
        assertEquals("urn:xmpp:bind:0", feature.getNamespaceURI());

        Element inline = feature.element("inline");
        assertNotNull(inline);
        assertTrue(inline.elements("feature").isEmpty(), "Expected no advertised features when no handlers are registered");
    }

    @Test
    public void testFeatureElementAdvertisesOneHandler() {
        Bind2Request.registerElementHandler(mockHandler1);

        Element feature = Bind2Request.featureElement();

        assertNotNull(feature);
        assertEquals("bind", feature.getName());
        assertEquals("urn:xmpp:bind:0", feature.getNamespaceURI());

        Element inline = feature.element("inline");
        assertNotNull(inline);
        List<Element> features = inline.elements("feature");
        assertEquals(1, features.size());
        assertEquals("http://test1.namespace", features.get(0).attributeValue("var"));
    }

    @Test
    public void testFeatureElementAdvertisesBothHandlers() {
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        Element feature = Bind2Request.featureElement();

        assertNotNull(feature);
        Element inline = feature.element("inline");
        assertNotNull(inline);
        List<Element> features = inline.elements("feature");
        assertEquals(2, features.size());

        List<String> vars = features.stream()
            .map(e -> e.attributeValue("var"))
            .collect(Collectors.toList());
        assertTrue(vars.contains("http://test1.namespace"), "Expected http://test1.namespace to be advertised");
        assertTrue(vars.contains("http://test2.namespace"), "Expected http://test2.namespace to be advertised");
    }

    @Test
    public void testFeatureElementAfterUnregisteringHandler() {
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);
        Bind2Request.unregisterElementHandler("http://test1.namespace");

        Element feature = Bind2Request.featureElement();

        Element inline = feature.element("inline");
        assertNotNull(inline);
        List<Element> features = inline.elements("feature");
        assertEquals(1, features.size());
        assertEquals("http://test2.namespace", features.get(0).attributeValue("var"));
    }
}
