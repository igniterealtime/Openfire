/*
 * Copyright (C) 2023-2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.admin.servlet;

import org.json.JSONArray;
import org.junit.Test;

import java.io.InputStream;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link BlogPostServlet}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class BlogPostServletTest
{
    /**
     * Verifies that the code that parses RSS data for the blog posts generates expect results.
     */
    @Test
    public void testRssParsing() throws Exception
    {
        // Setup test fixture.
        try (final InputStream rssStream = BlogPostServlet.class.getResourceAsStream("/rss/ignite-blog.rss")) {

            // Execute system under test.
            final JSONArray result = BlogPostServlet.parseFirstEntries(rssStream, 2);

            // Verify results.
            assertEquals(2, result.length());
            assertEquals("https://discourse.igniterealtime.org/t/cve-2023-32315-openfire-vulnerability-update/93166", result.getJSONObject(0).getString("link"));
            assertEquals("CVE-2023-32315: Openfire vulnerability (update)", result.getJSONObject(0).getString("title"));

            assertEquals("https://discourse.igniterealtime.org/t/sparkweb-lives-again/93130", result.getJSONObject(1).getString("link"));
            assertEquals("SparkWeb lives again", result.getJSONObject(1).getString("title"));
        }
    }

    /**
     * Verifies that the code that parses RSS data for the blog posts generates expect results, even when the default
     * Locale does not match the locale of the RSS content (assumed to be English).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2775">OF-2775: RSS News Feed appears empty</a>
     */
    @Test
    public void testLocale() throws Exception
    {
        // Setup test fixture.
        final Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);

            // Execute system under test.
            testRssParsing();
        } finally {
            // Teardown test fixture.
            Locale.setDefault(defaultLocale);
        }
    }
}
