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
package org.jivesoftware.openfire.container;

import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.FilterMapping;
import org.eclipse.jetty.ee8.servlet.ServletHandler;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.jivesoftware.admin.AdminContentSecurityPolicyFilter;
import org.jivesoftware.admin.ContentTypeOptionsFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the filters that {@link AdminConsolePlugin} registers on the web application context of the admin console.
 *
 * @author Matthew Vivian
 */
public class AdminConsoleWebAppContextTest
{
    private static ServletHandler servletHandler;

    @BeforeAll
    public static void setUpClass(@TempDir final File pluginDir) throws Exception
    {
        // Build the context as AdminConsolePlugin.startup does, without starting a server.
        final AdminConsolePlugin plugin = new AdminConsolePlugin();
        plugin.createWebAppContext(pluginDir);

        // Jetty wraps the (nested) web application context in a core context handler.
        final WebAppContext context = plugin.getContexts().getHandlers().stream()
            .filter(handler -> handler instanceof ContextHandler.CoreContextHandler)
            .map(handler -> ((ContextHandler.CoreContextHandler) handler).getContextHandler())
            .filter(handler -> handler instanceof WebAppContext)
            .map(handler -> (WebAppContext) handler)
            .findFirst()
            .orElse(null);
        assertNotNull(context, "the admin console web application context must be created");
        servletHandler = context.getServletHandler();
    }

    /**
     * Verifies that the filter that forbids content type sniffing applies to every path of the admin console.
     */
    @Test
    public void testContentTypeOptionsFilterIsMappedToAllPaths()
    {
        assertTrue(pathSpecsOf(ContentTypeOptionsFilter.class).contains("/*"), "the X-Content-Type-Options filter must be mapped to /*");
    }

    /**
     * Verifies that the Content Security Policy filter applies to every path of the admin console.
     */
    @Test
    public void testContentSecurityPolicyFilterIsMappedToAllPaths()
    {
        assertTrue(pathSpecsOf(AdminContentSecurityPolicyFilter.class).contains("/*"), "the Content Security Policy filter must be mapped to /*");
    }

    private static Set<String> pathSpecsOf(final Class<?> filterClass)
    {
        final Map<String, Class<?>> classByName = Arrays.stream(servletHandler.getFilters())
            .collect(Collectors.toMap(FilterHolder::getName, FilterHolder::getHeldClass));
        return Arrays.stream(servletHandler.getFilterMappings())
            .filter(mapping -> filterClass.equals(classByName.get(mapping.getFilterName())))
            .map(FilterMapping::getPathSpecs)
            .filter(specs -> specs != null)
            .flatMap(Arrays::stream)
            .collect(Collectors.toSet());
    }
}
