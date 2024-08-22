/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.container;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that verify the functionality of {@link PluginServlet}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginServletTest
{
    @Test
    public void testWildcard() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/*", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/foo.jsp");

        // Verify results.
        assertNotNull(result);
    }

    @Test
    public void testWildcardExtension() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/*.jsp", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/foo.jsp");

        // Verify results.
        assertNotNull(result);
    }

    @Test
    public void testWildcardExtensionFalse() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/*.jsp", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/foo.gif");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testWildcardExtensionConcat() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/*.jsp", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/foo.jsp99");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testExact() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/foo", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/foo");

        // Verify results.
        assertNotNull(result);
    }

    @Test
    public void testExactFalse() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/foo", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/bar");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testExactConcat() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/foo", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/foobar");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testSubdirWildcard() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/*", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/foo.jsp");

        // Verify results.
        assertNotNull(result);
    }

    @Test
    public void testSubdirWildcardExtension() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/*.jsp", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/foo.jsp");

        // Verify results.
        assertNotNull(result);
    }
    @Test
    public void testSubdirWildcardExtensionFalse() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/*.jsp", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/foo.gif");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testSubdirWildcardExtensionConcat() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/*.jsp", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/foo.jsp99");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testSubdirExact() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/foo", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/foo");

        // Verify results.
        assertNotNull(result);
    }

    @Test
    public void testSubdirExactFalse() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/foo", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/bar");

        // Verify results.
        assertNull(result);
    }

    @Test
    public void testSubdirExactConcat() throws Exception
    {
        // Setup test fixture.
        final Map<String, Object> haystack = Map.of("myplugin/baz/foo", new Object());

        // Execute system under test.
        final Object result = PluginServlet.getWildcardMappedObject(haystack, "myplugin/baz/foobar");

        // Verify results.
        assertNull(result);
    }
}
