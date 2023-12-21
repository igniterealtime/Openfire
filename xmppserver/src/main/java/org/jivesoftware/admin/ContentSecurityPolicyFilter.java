/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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

import javax.annotation.Nonnull;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds Content-Security-Policy headers to HTTP responses.
 *
 * This filter uses the values from to-be-supplied {@link SystemProperty} instances to configure if HTTP response
 * headers are to be added, and if so, what value to add.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ContentSecurityPolicyFilter implements Filter
{
    private final SystemProperty<Boolean> statusProperty;
    private final SystemProperty<String> valueProperty;

    public ContentSecurityPolicyFilter(@Nonnull final SystemProperty<Boolean> statusProperty, @Nonnull final SystemProperty<String> valueProperty)
    {
        this.statusProperty = statusProperty;
        this.valueProperty = valueProperty;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        if (response instanceof HttpServletResponse && statusProperty.getValue()) {
            final String value = valueProperty.getValue();
            if (value != null && !value.trim().isEmpty()) {
                ((HttpServletResponse) response).setHeader("Content-Security-Policy", value);
            }
        }
        chain.doFilter(request, response);
    }
}
