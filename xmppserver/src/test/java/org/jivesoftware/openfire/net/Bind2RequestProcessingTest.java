/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
import java.util.Set;
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
        when(mockHandler1.isEnabled()).thenReturn(true);
        when(mockHandler2.isEnabled()).thenReturn(true);
    }

    @AfterEach
    public void tearDown() {
        Bind2Request.unregisterElementHandler(mockHandler1);
        Bind2Request.unregisterElementHandler(mockHandler2);
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
        verify(mockHandler1).handleFailure(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1), any(RuntimeException.class));
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
        verify(mockHandler1).handleFailure(eq(mockSession), elementWithNameAndNS("bound", "urn:xmpp:bind:0"), eq(featureElement1), isNull());
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
        assertTrue(advertisedFeatures(inline).isEmpty(), "Expected no advertised features when no handlers are registered");
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
        Set<String> featureVars = advertisedFeatures(inline);
        assertEquals(2, featureVars.size());
        assertTrue(featureVars.contains("http://test1.namespace"), "Expected http://test1.namespace to be advertised");
        assertTrue(featureVars.contains("http://test2.namespace"), "Expected http://test2.namespace to be advertised");
    }

    @Test
    public void testFeatureElementAfterUnregisteringHandler() {
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);
        Bind2Request.unregisterElementHandler(mockHandler1);

        Element feature = Bind2Request.featureElement();

        Element inline = feature.element("inline");
        assertNotNull(inline);
        Set<String> featureVars = advertisedFeatures(inline);
        assertEquals(1, featureVars.size());
        assertTrue(featureVars.contains("http://test2.namespace"), "Expected http://test2.namespace to be advertised");
    }

    /**
     * Verifies that a handler reports no failure when its request was processed successfully.
     *
     * A failure response that accompanies a successful request would be reported to the peer as though something had
     * gone wrong, and for XEP-0198 would leave a client believing stream management had not been enabled when it had.
     */
    @Test
    public void testNoFailureIsReportedWhenProcessingSucceeds()
    {
        // Setup test fixture.
        Bind2Request.registerElementHandler(mockHandler1);
        final Bind2Request request = new Bind2Request("test-client", List.of(featureElement1));

        // Execute system under test.
        request.processFeatureRequests(mockSession, successElement);

        // Verify result.
        verify(mockHandler1).handleElement(any(), any(), eq(featureElement1));
        verify(mockHandler1, never()).handleFailure(any(), any(), any(), any());
    }

    /**
     * Verifies that a handler whose feature is unavailable is not invoked for a request that names its namespace.
     *
     * A peer can send an inline request for a feature that was advertised earlier in the stream but has since been
     * disabled by configuration, so availability must be checked when the request is processed and not only when the
     * feature list is built.
     */
    @Test
    public void testDisabledHandlerIsNotInvoked()
    {
        // Setup test fixture.
        when(mockHandler1.isEnabled()).thenReturn(false);
        Bind2Request.registerElementHandler(mockHandler1);
        final Bind2Request request = new Bind2Request("test-client", List.of(featureElement1));

        // Execute system under test.
        request.processFeatureRequests(mockSession, successElement);

        // Verify result.
        verify(mockHandler1, never()).handleElement(any(), any(), any());
        verify(mockHandler1, never()).handleFailure(any(), any(), any(), any());
    }

    /**
     * Verifies that one unavailable handler does not prevent the others from processing their requests.
     */
    @Test
    public void testDisabledHandlerDoesNotSuppressOthers()
    {
        // Setup test fixture.
        when(mockHandler1.isEnabled()).thenReturn(false);
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);
        final Bind2Request request = new Bind2Request("test-client", List.of(featureElement1, featureElement2));

        // Execute system under test.
        request.processFeatureRequests(mockSession, successElement);

        // Verify result.
        verify(mockHandler1, never()).handleElement(any(), any(), any());
        verify(mockHandler2).handleElement(any(), any(), eq(featureElement2));
    }

    /**
     * Verifies that a handler whose feature is unavailable is not advertised.
     *
     * Advertising a feature that would then be ignored invites a peer to send a request that receives no response at
     * all, which it cannot distinguish from one the server failed to process.
     */
    @Test
    public void testDisabledHandlerIsNotAdvertised()
    {
        // Setup test fixture.
        when(mockHandler1.isEnabled()).thenReturn(false);
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);

        // Execute system under test.
        final Element inline = Bind2Request.featureElement().element("inline");

        // Verify result.
        assertFalse(advertisedFeatures(inline).contains("http://test1.namespace"),
            "An unavailable feature must not be advertised.");
        assertTrue(advertisedFeatures(inline).contains("http://test2.namespace"),
            "An available feature must still be advertised alongside an unavailable one.");
    }

    /**
     * Verifies that availability is evaluated each time the feature list is built, rather than when the handler was
     * registered.
     *
     * Whether a feature is available is typically governed by a dynamic configuration property, and stream features
     * are regenerated more than once during a stream's lifetime.
     */
    @Test
    public void testAvailabilityIsEvaluatedPerAdvertisement()
    {
        // Setup test fixture.
        Bind2Request.registerElementHandler(mockHandler1);

        // Execute system under test & verify result.
        when(mockHandler1.isEnabled()).thenReturn(true);
        assertTrue(advertisedFeatures(Bind2Request.featureElement().element("inline")).contains("http://test1.namespace"),
            "An available feature must be advertised.");

        when(mockHandler1.isEnabled()).thenReturn(false);
        assertFalse(advertisedFeatures(Bind2Request.featureElement().element("inline")).contains("http://test1.namespace"),
            "A feature that has since become unavailable must no longer be advertised.");

        when(mockHandler1.isEnabled()).thenReturn(true);
        assertTrue(advertisedFeatures(Bind2Request.featureElement().element("inline")).contains("http://test1.namespace"),
            "A feature that has become available again must be advertised again.");
    }

    /**
     * Verifies that a handler which overrides neither of the two new methods behaves exactly as handlers did before
     * they existed: it is available, and it tolerates being asked to report a failure.
     *
     * Handlers are contributed by plugins, which are not necessarily recompiled against a new interface.
     */
    @Test
    public void testHandlerWithoutOverridesRetainsPreviousBehaviour()
    {
        // Setup test fixture: a handler implementing only what the interface has always required.
        final Bind2InlineHandler handler = new Bind2InlineHandler() {
            @Override
            public String getNamespace() {
                return "http://legacy.namespace";
            }

            @Override
            public boolean handleElement(LocalClientSession session, Element bound, Element element) {
                return true;
            }
        };

        // Execute system under test & verify result.
        assertTrue(handler.isEnabled(),
            "A handler that does not override isEnabled must be treated as available, as handlers were before the method existed.");
        assertDoesNotThrow(() -> handler.handleFailure(mockSession, DocumentHelper.createElement(QName.get("bound", "urn:xmpp:bind:0")), featureElement1, null),
            "A handler that does not override handleFailure must tolerate being asked to report one.");
    }

    /**
     * Verifies that whatever a handler adds to the response while reporting a failure survives into the result.
     *
     * This is what makes a protocol-defined failure response expressible at all; XEP-0198 § 9.1.1, for example,
     * requires a &lt;failed/&gt; element inside &lt;bound/&gt; when stream management could not be enabled.
     */
    @Test
    public void testFailureResponseIsRetainedInTheBoundElement()
    {
        // Setup test fixture.
        when(mockHandler1.handleElement(any(), any(), any())).thenReturn(false);
        doAnswer(invocation -> {
            final Element bound = invocation.getArgument(1);
            bound.addElement("failed", "http://test1.namespace");
            return null;
        }).when(mockHandler1).handleFailure(any(), any(), any(), any());
        Bind2Request.registerElementHandler(mockHandler1);
        final Bind2Request request = new Bind2Request("test-client", List.of(featureElement1));

        // Execute system under test.
        final Element bound = request.processFeatureRequests(mockSession, successElement);

        // Verify result.
        assertNotNull(bound.element(QName.get("failed", "http://test1.namespace")),
            "A failure response added by a handler must be retained in the response that is sent to the peer.");
    }

    /**
     * Verifies that a handler which throws while reporting a failure does not prevent the remaining requests from
     * being processed.
     *
     * Handlers are contributed by plugins, so a misbehaving one must not be able to fail the authentication that the
     * bind request is part of.
     */
    @Test
    public void testExceptionWhileReportingFailureDoesNotPropagate()
    {
        // Setup test fixture.
        when(mockHandler1.handleElement(any(), any(), any())).thenReturn(false);
        doThrow(new RuntimeException("handler is broken")).when(mockHandler1).handleFailure(any(), any(), any(), any());
        Bind2Request.registerElementHandler(mockHandler1);
        Bind2Request.registerElementHandler(mockHandler2);
        final Bind2Request request = new Bind2Request("clientTag", Arrays.asList(featureElement1, featureElement2));

        // Execute system under test.
        final Element bound = assertDoesNotThrow(() -> request.processFeatureRequests(mockSession, successElement));

        // Verify result.
        assertNotNull(bound);
        verify(mockHandler1, times(1)).handleFailure(any(), any(), any(), any());
        verify(mockHandler2).handleElement(any(), any(), eq(featureElement2));
    }

    /**
     * Returns the namespaces advertised as inline features.
     */
    private static Set<String> advertisedFeatures(final Element inline)
    {
        return inline.elements("feature").stream()
            .map(feature -> feature.attributeValue("var"))
            .collect(Collectors.toSet());
    }
}
