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

import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ContentTypeOptionsFilter}.
 *
 * @author Matthew Vivian
 */
public class ContentTypeOptionsFilterTest
{
    /**
     * Verifies that the header is set before the rest of the chain runs, so that it is present however the rest of
     * the chain produces the response (including an error page), and that the chain is always invoked.
     */
    @Test
    public void testSetsNosniffBeforeInvokingChain() throws Exception
    {
        // Setup test fixture.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            verify(response, description("the response must forbid content type sniffing before the rest of the chain runs")).setHeader("X-Content-Type-Options", "nosniff");
            return null;
        }).when(chain).doFilter(request, response);

        // Execute system under test.
        new ContentTypeOptionsFilter().doFilter(request, response, chain);

        // Verify results.
        verify(chain, description("the rest of the chain must run")).doFilter(request, response);
    }

    /**
     * Verifies that a response that is not an HTTP response is passed down the chain untouched.
     */
    @Test
    public void testPassesNonHttpResponseThrough() throws Exception
    {
        // Setup test fixture.
        final ServletRequest request = mock(ServletRequest.class);
        final ServletResponse response = mock(ServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);

        // Execute system under test.
        new ContentTypeOptionsFilter().doFilter(request, response, chain);

        // Verify results.
        verify(chain, description("the chain must run for a non-HTTP response")).doFilter(request, response);
        verifyNoInteractions(response);
    }
}
