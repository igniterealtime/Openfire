/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a SASL2 bind2 request from a client.
 *
 * The bind request contains an optional tag identifying the client software
 * and can include additional feature requests in other namespaces.
 */
public class Bind2Request {
    private static final Logger Log = LoggerFactory.getLogger(Bind2Request.class);
    
    // Add a map to store registered handlers by namespace
    private static final Map<String, Bind2InlineHandler> elementHandlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler for processing inline elements with a specific namespace.
     *
     * Only one handler can be registered for each namespace. Attempting to register a handler for an already registered
     * namespace will result in an IllegalStateException.
     *
     * @param handler The handler to register
     */
    public static void registerElementHandler(@Nonnull final Bind2InlineHandler handler)
    {
        final String namespace = handler.getNamespace();
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("Handler namespace cannot be null or empty");
        }

        if (elementHandlers.putIfAbsent(namespace, handler) != null) {
            throw new IllegalStateException("An inline element handler is already registered for namespace: " + namespace);
        }

        Log.debug("Registered inline element handler for namespace: {}", namespace);
    }

    /**
     * Unregisters an inline element handler associated with a specific namespace.
     *
     * This method removes the handler previously registered for processing inline elements
     * with the specified namespace. If no handler is registered for the given namespace or
     * if the provided handler does not match the currently registered handler, no action
     * will be performed.
     *
     * @param handler The inline element handler to unregister. Must not be null and must provide a valid namespace.
     * @return {@code true} if the handler was successfully unregistered, {@code false} otherwise.
     * @throws IllegalArgumentException if the handler's namespace is {@code null} or empty.
     */
    public static boolean unregisterElementHandler(@Nonnull final Bind2InlineHandler handler)
    {
        final String namespace = handler.getNamespace();

        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("Handler namespace cannot be null or empty");
        }

        boolean removed = elementHandlers.remove(namespace, handler);

        if (removed) {
            Log.debug("Unregistered inline element handler for namespace: {}", namespace);
        }

        return removed;
    }

    /**
     * Process feature request elements using registered handlers.
     *
     * @return Element for <bound/>
     */
    public Element processFeatureRequests(LocalClientSession clientSession, Element successElement) {
        Element bound = successElement.addElement(new QName("bound", new Namespace("", NAMESPACE)));

        for (Element element : featureRequests) {
            String namespace = element.getNamespaceURI();
            Bind2InlineHandler handler = elementHandlers.get(namespace);

            if (handler != null && handler.isEnabled()) {
                try {
                    if (!handler.handleElement(clientSession, bound, element)) {
                        Log.info("Handler for namespace {} failed to process element", namespace);
                        invokeFailureHandler(clientSession, bound, element, null, handler, namespace);
                    }
                } catch (Exception e) {
                    Log.warn("Error processing element with namespace: {}", namespace, e);
                    invokeFailureHandler(clientSession, bound, element, e, handler, namespace);
                }
            } else {
                Log.debug("No handler registered/enabled for namespace: {}", namespace);
                // We don't fail here because there's no obvious way we could fail.
            }
        }

        return bound;
    }

    /**
     * Invokes the failure-handler of a Bind2-handler, logging but otherwise suppressing any exception thrown by the
     * failure-handler.
     *
     * @param clientSession the client session.
     * @param bound the bound element.
     * @param element the element that failed to be processed.
     * @param cause the processing exception, or {@code null} when the handler returned {@code false}.
     * @param handler the Bind2-handler that failed to process the element.
     * @param namespace the namespace of the element.
     */
    private static void invokeFailureHandler(final LocalClientSession clientSession, final Element bound, final Element element, @Nullable final Exception cause, @Nonnull final Bind2InlineHandler handler, final String namespace)
    {
        try {
            handler.handleFailure(clientSession, bound, element, cause);
        } catch (Exception ex) {
            Log.warn("Error invoking failure handler after failing to process element with namespace: {}", namespace, ex);
        }
    }

    public static Element featureElement() {
        Element bind2 = DocumentHelper.createElement(new QName("bind", new Namespace("", "urn:xmpp:bind:0")));
        Element bind2inline = bind2.addElement("inline");
        for (Bind2InlineHandler handler : elementHandlers.values()) {
            if (!handler.isEnabled()) {
                continue;
            }
            Element var = bind2inline.addElement("feature");
            var.addAttribute("var", handler.getNamespace());
        }
        return bind2;
    }


    private static final String NAMESPACE = "urn:xmpp:bind:0";
    private static final String ELEMENT_NAME = "bind";
    private static final String TAG_ELEMENT = "tag";

    private final String clientTag;
    private final List<Element> featureRequests;

    /**
     * Creates a new Bind2Request instance.
     *
     * @param clientTag Optional string identifying the client software, can be null.
     * @param featureRequests List of feature request elements, can be empty but not null.
     */
    public Bind2Request(String clientTag, List<Element> featureRequests) {
        this.clientTag = clientTag;
        this.featureRequests = Collections.unmodifiableList(new ArrayList<>(featureRequests));
    }

    /**
     * Extracts bind information from a SASL2 authenticate element.
     *
     * @param authenticateElement The authenticate element from which to extract bind data.
     * @return A Bind2Request instance containing the extracted data, or null if no bind element was found.
     */
    public static Bind2Request from(Element authenticateElement) {
        if (authenticateElement == null) {
            return null;
        }

        Element bindElement = authenticateElement.element(ELEMENT_NAME);
        if (bindElement == null || !NAMESPACE.equals(bindElement.getNamespaceURI())) {
            return null;
        }

        // Extract the optional client tag
        Element tagElement = bindElement.element(TAG_ELEMENT);
        String clientTag = tagElement != null ? tagElement.getTextTrim() : null;

        // Collect feature requests (elements from other namespaces)
        List<Element> featureRequests = new ArrayList<>();
        for (Element element : bindElement.elements()) {
            if (!NAMESPACE.equals(element.getNamespaceURI()) && !element.getName().equals(TAG_ELEMENT)) {
                featureRequests.add(element.createCopy());
            }
        }

        return new Bind2Request(clientTag, featureRequests);
    }

    /**
     * Gets the client software identifier tag.
     *
     * @return The client tag or null if none was provided.
     */
    public String getClientTag() {
        return clientTag;
    }

    /**
     * Gets the list of feature request elements.
     *
     * @return An unmodifiable list of feature request elements.
     */
    public List<Element> getFeatureRequests() {
        return featureRequests;
    }


    /**
     * Generates a resource string using the user agent information or defaults.
     *
     * @param userAgentInfo The user agent information, can be null
     * @return A resource string containing the client tag (if provided) followed by random string (hex or UUID).
     */
    public String generateResourceString(UserAgentInfo userAgentInfo) {
        StringBuilder resource = new StringBuilder();

        // Add the client tag if available
        if (clientTag != null && !clientTag.isEmpty()) {
            resource.append(clientTag);
            resource.append('/');
        }

        String hmacKey;

        // Get the UUID to use as HMAC key
        if (userAgentInfo != null && userAgentInfo.getId() != null) {
            hmacKey = userAgentInfo.getId();
        } else {
            hmacKey = UUID.randomUUID().toString();
        }

        try {
            // Convert UUID string to bytes for use as HMAC key
            byte[] keyBytes = hmacKey.getBytes(StandardCharsets.UTF_8);

            // Using a fixed constant here - building a rainbow table here for the case
            // where the client supplies no tag is going to be very expensive, so this
            // prevents an id recovery attack.
            String valueToHmac = resource + "OpenfireResourceConstant";

            // Compute HMAC
            byte[] hmacResult = ScramUtils.computeHmac(keyBytes, valueToHmac, "HmacSHA1");

            // Convert first 8 bytes of HMAC to hex for resource suffix (16 chars)
            String hmacHex = StringUtils.encodeHex(Arrays.copyOf(hmacResult, 8));

            // Construct final resource string
            return resource + hmacHex;

        } catch (SaslException e) {
            // Fall back to UUID in case of HMAC computation failure
            Log.error("Failed to compute HMAC for resource string", e);
            return resource.toString() + UUID.randomUUID();
        }
    }
}
