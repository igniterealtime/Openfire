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

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A filter that sets HTTP Cache-Control headers for the Openfire Admin Console.
 * <p>
 * This filter distinguishes between static resources (which are cached aggressively for performance)
 * and dynamic administrative content (which is never cached for security reasons).
 * </p>
 *
 * @author Antigravity
 */
public class CacheControlFilter implements Filter {

    /**
     * Set of file extensions considered as static resources.
     */
    private static final Set<String> STATIC_EXTENSIONS;

    static {
        Set<String> extensions = new HashSet<>(Arrays.asList(
            "css", "js", "png", "jpg", "jpeg", "gif", "svg", "woff", "woff2", "ttf", "ico", "webp", "map"
        ));
        STATIC_EXTENSIONS = Collections.unmodifiableSet(extensions);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization required.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String uri = httpRequest.getRequestURI();
            
            // Strip query parameters for extension checking
            int queryIndex = uri.indexOf('?');
            if (queryIndex != -1) {
                uri = uri.substring(0, queryIndex);
            }

            // Only set headers if not already present (preserving specific servlet/filter overrides)
            if (!httpResponse.containsHeader("Cache-Control")) {
                if (isStaticResource(uri)) {
                    // Aggressive caching for static assets to improve performance and reduce server load.
                    // 'immutable' is used as UI assets are typically stable between version upgrades.
                    httpResponse.setHeader("Cache-Control", "public, max-age=31536000, immutable");
                } else {
                    // Disable caching for sensitive administrative and dynamic content to ensure 
                    // that data is never stored in browser history or proxy caches.
                    httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                    httpResponse.setHeader("Pragma", "no-cache");
                    httpResponse.setDateHeader("Expires", 0);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Determines if the given URI refers to a static resource based on its file extension.
     *
     * @param uri the request URI (without query parameters).
     * @return true if the resource is considered static, false otherwise.
     */
    private boolean isStaticResource(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }

        int dotIndex = uri.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < uri.length() - 1) {
            String extension = uri.substring(dotIndex + 1).toLowerCase();
            return STATIC_EXTENSIONS.contains(extension);
        }

        return false;
    }

    @Override
    public void destroy() {
        // No resources to release.
    }
}
