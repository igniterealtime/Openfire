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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds the 'X-Content-Type-Options: nosniff' header to HTTP responses, which tells browsers to use the Content-Type
 * that the server declares instead of guessing one from the content. Browsers then also refuse to run a script or
 * apply a stylesheet that is not served with a script or stylesheet type.
 *
 * The header is set before the rest of the filter chain runs, so that it is present on every response that passes
 * through the chain, including the error pages of the web application. A response that the servlet container
 * produces before it reaches the filter chain (for a request to a protected path such as /WEB-INF, or for a malformed
 * request) does not pass through this filter and does not get the header.
 *
 * @author Matthew Vivian
 */
public class ContentTypeOptionsFilter implements Filter
{
    private static final String HEADER_NAME = "X-Content-Type-Options";
    private static final String HEADER_VALUE = "nosniff";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader(HEADER_NAME, HEADER_VALUE);
        }
        chain.doFilter(request, response);
    }
}
