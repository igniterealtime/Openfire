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
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Bind2InlineHandler registration and processing functionality.
 */
public class Bind2InlineHandlerTest {

    @Test
    public void testRegisterElementHandler() {
        Bind2InlineHandler handler = new TestBind2InlineHandler("urn:xmpp:test:0");

        assertDoesNotThrow(
            () -> Bind2Request.registerElementHandler(handler),
            "A handler should be registered when its namespace is not already registered"
        );

        assertTrue(
            Bind2Request.unregisterElementHandler(handler),
            "The registered handler should be removable using the same handler instance"
        );
    }

    @Test
    public void testRegisterElementHandlerRejectsNullNamespace() {
        Bind2InlineHandler handler = new TestBind2InlineHandler(null);

        assertThrows(
            IllegalArgumentException.class,
            () -> Bind2Request.registerElementHandler(handler),
            "Registering a handler without a namespace should fail"
        );
    }

    @Test
    public void testRegisterElementHandlerRejectsEmptyNamespace() {
        Bind2InlineHandler handler = new TestBind2InlineHandler("");

        assertThrows(
            IllegalArgumentException.class,
            () -> Bind2Request.registerElementHandler(handler),
            "Registering a handler with an empty namespace should fail"
        );
    }

    @Test
    public void testUnregisterElementHandlerRejectsNullNamespace() {
        Bind2InlineHandler handler = new TestBind2InlineHandler(null);

        assertThrows(
            IllegalArgumentException.class,
            () -> Bind2Request.unregisterElementHandler(handler),
            "Unregistering a handler without a namespace should fail"
        );
    }

    @Test
    public void testUnregisterElementHandlerRejectsEmptyNamespace() {
        Bind2InlineHandler handler = new TestBind2InlineHandler("");

        assertThrows(
            IllegalArgumentException.class,
            () -> Bind2Request.unregisterElementHandler(handler),
            "Unregistering a handler with an empty namespace should fail"
        );
    }

    @Test
    public void testRegisterElementHandlerRejectsDuplicateNamespace() {
        String namespace = "urn:xmpp:test:0";
        Bind2InlineHandler firstHandler = new TestBind2InlineHandler(namespace);
        Bind2InlineHandler secondHandler = new TestBind2InlineHandler(namespace);

        Bind2Request.registerElementHandler(firstHandler);

        try {
            assertThrows(
                IllegalStateException.class,
                () -> Bind2Request.registerElementHandler(secondHandler),
                "Registering a second handler for an existing namespace should fail"
            );

            assertTrue(
                Bind2Request.unregisterElementHandler(firstHandler),
                "The original handler should remain registered after duplicate registration fails"
            );

            assertFalse(
                Bind2Request.unregisterElementHandler(secondHandler),
                "The rejected handler should never be registered"
            );
        } finally {
            Bind2Request.unregisterElementHandler(firstHandler);
            Bind2Request.unregisterElementHandler(secondHandler);
        }
    }

    @Test
    public void testUnregisterElementHandlerRequiresRegisteredHandler() {
        String namespace = "urn:xmpp:test:0";
        Bind2InlineHandler registeredHandler = new TestBind2InlineHandler(namespace);
        Bind2InlineHandler otherHandler = new TestBind2InlineHandler(namespace);

        Bind2Request.registerElementHandler(registeredHandler);

        try {
            assertFalse(
                Bind2Request.unregisterElementHandler(otherHandler),
                "A handler that is not registered should not remove the registered handler"
            );

            assertTrue(
                Bind2Request.unregisterElementHandler(registeredHandler),
                "The actually registered handler should still be removable"
            );
        } finally {
            Bind2Request.unregisterElementHandler(registeredHandler);
            Bind2Request.unregisterElementHandler(otherHandler);
        }
    }

    @Test
    public void testUnregisterElementHandlerReturnsFalseForUnregisteredHandler() {
        Bind2InlineHandler handler = new TestBind2InlineHandler("urn:xmpp:test:0");

        assertFalse(
            Bind2Request.unregisterElementHandler(handler),
            "Unregistering a handler that was never registered should return false"
        );
    }

    private static class TestBind2InlineHandler implements Bind2InlineHandler
    {
        private final String namespace;

        private TestBind2InlineHandler(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public boolean handleElement(LocalClientSession clientSession, Element bound, Element element) {
            return true;
        }
    }
}
