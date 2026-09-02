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

import org.dom4j.Element;
import org.jivesoftware.openfire.session.LocalClientSession;

/**
 * Interface for plugins that handle inline elements in SASL2 bind2 requests.
 */
public interface Bind2InlineHandler {

    /**
     * Gets the namespace this handler processes.
     *
     * @return The XML namespace URI this handler supports
     */
    String getNamespace();

    /**
     * Process an inline element from a bind2 request.
     *
     * @param bound The "bound" element to add any output to
     * @param element The DOM element to process
     * @return true if the element was handled successfully, false otherwise
     */
    boolean handleElement(LocalClientSession session, Element bound, Element element);

    /**
     * Indicates whether this handler is currently available for advertisement and request processing.
     *
     * A handler whose feature is disabled by configuration is neither advertised in the Bind2 inline feature list nor
     * invoked for a request that names its namespace, so that a peer is never offered something it cannot use.
     *
     * @return {@code true} when the inline feature is available
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Gives a handler an opportunity to add the protocol-defined failure response after request processing failed.
     *
     * Not every inline extension defines one, so the default does nothing.
     *
     * @param session the client session
     * @param bound the Bind2 response element
     * @param element the request that could not be processed
     * @param cause the processing exception, or {@code null} when the handler returned {@code false}
     */
    default void handleFailure(LocalClientSession session, Element bound, Element element, Throwable cause) {
        // Most inline extensions do not define a failure response.
    }
}
