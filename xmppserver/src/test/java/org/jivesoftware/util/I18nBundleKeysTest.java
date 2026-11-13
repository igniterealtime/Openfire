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
package org.jivesoftware.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the admin console's message bundles (openfire_i18n*.properties). A key defined twice in a bundle is a
 * mistake that nothing else reports: the later definition silently replaces the earlier one, so a page shows another
 * message than the one it asks for.
 *
 * @author Matthew Vivian
 */
public class I18nBundleKeysTest
{
    private static final String BASE_BUNDLE = "openfire_i18n.properties";

    /**
     * Verifies that no message bundle defines a key more than once.
     */
    @Test
    public void testNoBundleDefinesAKeyTwice() throws Exception
    {
        // Setup test fixture.
        final URL base = I18nBundleKeysTest.class.getClassLoader().getResource(BASE_BUNDLE);
        assertNotNull(base, "the base message bundle must be on the classpath");

        // Execute system under test.
        final Map<String, List<String>> duplicatesByBundle = new LinkedHashMap<>();
        final int bundles = forEachBundle(base.toURI(), (name, content) -> {
            final List<String> duplicates = duplicateKeys(content);
            if (!duplicates.isEmpty()) {
                duplicatesByBundle.put(name, duplicates);
            }
        });

        // Verify result.
        assertTrue(bundles > 1, "the base bundle and its translations must be found, but " + bundles + " bundle(s) were");
        assertEquals(Map.of(), duplicatesByBundle, "no bundle may define a key twice");
    }

    /**
     * Verifies that the check reads a bundle as {@link Properties#load(java.io.Reader)} does, so that it finds every key
     * defined twice and nothing else: a plain duplicate; one in a file with CRLF line endings; one after a comment that
     * ends in a backslash (a comment does not continue onto the next line); one written once plainly and once with an
     * escaped character; and none where a continuation line merely looks like a definition.
     */
    @Test
    public void testDuplicateKeysAreFoundAsPropertiesReadsThem() throws Exception
    {
        // Execute system under test & verify result.
        assertEquals(List.of("a.b"), duplicateKeys("a.b=first\na.c=other\na.b = again\n"), "a plain duplicate must be reported");
        assertEquals(List.of("a"), duplicateKeys("a=first\r\nb=other\r\na=second\r\n"), "a duplicate must be reported in a file with CRLF line endings");
        assertEquals(List.of("a"), duplicateKeys("# a comment ending in a backslash \\\na=first\na=second\n"),
            "a comment ending in a backslash must not hide the definition after it");
        assertEquals(List.of("a.b"), duplicateKeys("a.b=x\na\\.b=y\n"), "a key written with an escaped character must be compared as it is read");
        assertEquals(List.of("a"), duplicateKeys("a=x\n\\u0061=y\n"), "a key written with a Unicode escape must be compared as it is read");
        assertEquals(List.of(), duplicateKeys("a=first \\\n    a=part of the value of a\nb=other\n"), "a continuation line must not be read as a definition");
    }

    private interface BundleVisitor
    {
        void visit(String name, String content) throws IOException;
    }

    /**
     * Visits every openfire_i18n*.properties beside the base bundle, whether that is in a directory or a jar.
     *
     * @return the number of bundles visited.
     */
    private static int forEachBundle(final URI base, final BundleVisitor visitor) throws IOException
    {
        if ("jar".equals(base.getScheme())) {
            try (final FileSystem jar = FileSystems.newFileSystem(base, Map.of())) {
                return visitBundles(jar.getPath("/"), visitor);
            }
        }
        return visitBundles(Path.of(base).getParent(), visitor);
    }

    private static int visitBundles(final Path directory, final BundleVisitor visitor) throws IOException
    {
        int count = 0;
        try (final Stream<Path> files = Files.list(directory)) {
            for (final Path file : files.filter(f -> {
                final String name = f.getFileName().toString();
                return name.startsWith("openfire_i18n") && name.endsWith(".properties");
            }).toList()) {
                visitor.visit(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the keys that a properties file defines more than once, as {@link Properties#load(java.io.Reader)} reads
     * them: that method stores each definition with {@link Properties#put(Object, Object)}, so a key it already holds is
     * one defined again.
     */
    static List<String> duplicateKeys(final String content) throws IOException
    {
        final List<String> duplicates = new ArrayList<>();
        final Properties properties = new Properties()
        {
            @Override
            public synchronized Object put(final Object key, final Object value)
            {
                if (containsKey(key) && !duplicates.contains(key.toString())) {
                    duplicates.add(key.toString());
                }
                return super.put(key, value);
            }
        };
        properties.load(new StringReader(content));
        return duplicates;
    }
}
