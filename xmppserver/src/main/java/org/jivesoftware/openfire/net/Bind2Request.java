/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a SASL2 bind2 request from a client.
 *
 * The bind request contains an optional tag identifying the client software
 * and can include additional feature requests in other namespaces.
 */
public class Bind2Request {
    private static final Logger Log = LoggerFactory.getLogger(Bind2Request.class);


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
     * @return A resource string containing the user agent tag (or "Openfire") followed by a UUID
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
            // where the client supplies no tag is going tobe very expensive, so this
            // prevents an id recovery attack.
            String valueToHmac = resource.toString() + "OpenfireResourceConstant";

            // Compute HMAC
            byte[] hmacResult = ScramUtils.computeHmac(keyBytes, valueToHmac);

            // Convert first 8 bytes of HMAC to hex for resource suffix (16 chars)
            String hmacHex = StringUtils.encodeHex(Arrays.copyOf(hmacResult, 8));

            // Construct final resource string
            return resource.toString() + hmacHex;

        } catch (SaslException e) {
            // Fall back to UUID in case of HMAC computation failure
            Log.error("Failed to compute HMAC for resource string", e);
            return resource.toString() + UUID.randomUUID().toString();
        }
    }
}
