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

package org.jivesoftware.admin;

import org.jivesoftware.util.SystemProperty;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A servlet filter that sets HTTP Cache-Control headers for the Openfire Admin Console.
 * <p>
 * This filter distinguishes between static resources and dynamic administrative content:
 * <ul>
 *     <li>Static assets (images, scripts, stylesheets, fonts) receive configurable short-term
 *         caching headers to reduce redundant requests and improve responsiveness.</li>
 *     <li>Dynamic and administrative pages receive no-store/no-cache headers to prevent
 *         sensitive content from being cached by browsers or proxies.</li>
 * </ul>
 * <p>
 * Headers are applied <em>after</em> the filter chain has executed, and only when the response
 * status indicates success (200 or 304). This prevents error responses from accidentally
 * receiving cache headers. If a {@code Cache-Control} header has already been set by another
 * servlet or filter, this filter will not overwrite it.
 * <p>
 * Both the set of static resource file extensions and the cache duration are configurable
 * via Openfire system properties.
 *
 * @author Milan Tyagi
 * @see #STATIC_RESOURCE_EXTENSIONS
 * @see #STATIC_RESOURCE_MAX_AGE
 */
public class CacheControlFilter implements Filter {

    /**
     * A comma-separated list of file extensions that are considered static resources.
     * Requests for resources matching these extensions will receive short-term caching headers.
     */
    public static final SystemProperty<String> STATIC_RESOURCE_EXTENSIONS = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.static-resource-extensions")
        .setDefaultValue("js,css,png,jpg,jpeg,gif,svg,woff,woff2,ttf,ico,webp,map")
        .setDynamic(true)
        .build();

    /**
     * The maximum duration that static resources may be cached by browsers and proxies.
     * The default value is one hour ({@code PT1H}).
     */
    public static final SystemProperty<Duration> STATIC_RESOURCE_MAX_AGE = SystemProperty.Builder.ofType(Duration.class)
        .setKey("adminConsole.static-resource-max-age")
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDefaultValue(Duration.ofHours(1))
        .setDynamic(true)
        .build();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization required.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // Execute the rest of the filter chain first, so that headers are only applied
        // after the response status code has been determined.
        chain.doFilter(request, response);

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // Do not modify headers on a committed response or if Cache-Control is already set.
            if (httpResponse.isCommitted() || httpResponse.containsHeader("Cache-Control")) {
                return;
            }

            // Only apply cache headers to successful responses (200 OK, 304 Not Modified).
            int status = httpResponse.getStatus();
            if (status != HttpServletResponse.SC_OK && status != HttpServletResponse.SC_NOT_MODIFIED) {
                return;
            }

            String uri = httpRequest.getRequestURI();

            if (isStaticResource(uri)) {
                long maxAgeSeconds = Math.max(0, STATIC_RESOURCE_MAX_AGE.getValue().getSeconds());
                httpResponse.setHeader("Cache-Control", "public, max-age=" + maxAgeSeconds);
            } else {
                // Disable caching for sensitive administrative and dynamic content to ensure
                // that data is never stored in browser history or proxy caches.
                httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                httpResponse.setHeader("Pragma", "no-cache");
                httpResponse.setDateHeader("Expires", 0);
            }
        }
    }

    /**
     * Determines if the given URI refers to a static resource based on its file extension.
     * The set of recognized extensions is read dynamically from the
     * {@link #STATIC_RESOURCE_EXTENSIONS} system property. Matching is case-insensitive.
     *
     * @param uri the request URI.
     * @return {@code true} if the resource is considered static, {@code false} otherwise.
     */
    private boolean isStaticResource(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }

        int dotIndex = uri.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < uri.length() - 1) {
            String extension = uri.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
            return getStaticExtensions().contains(extension);
        }

        return false;
    }

    /**
     * Parses the current value of {@link #STATIC_RESOURCE_EXTENSIONS} into a {@link Set} of
     * lowercase extension strings.
     *
     * @return an unmodifiable set of recognized static resource extensions.
     */
    private Set<String> getStaticExtensions() {
        String value = STATIC_RESOURCE_EXTENSIONS.getValue();
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> extensions = new HashSet<>();
        for (String ext : value.split("[\\s,]+")) {
            String trimmed = ext.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                extensions.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(extensions);
    }

    @Override
    public void destroy() {
        // No resources to release.
    }
}
