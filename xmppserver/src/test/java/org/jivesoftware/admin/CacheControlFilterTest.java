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

import org.jivesoftware.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CacheControlFilter}.
 *
 * @author Milan Tyagi
 */
@ExtendWith(MockitoExtension.class)
public class CacheControlFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private CacheControlFilter filter;

    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() {
        filter = new CacheControlFilter();
        // By default, responses are not committed and return 200 OK.
        lenient().doReturn(false).when(response).isCommitted();
        lenient().doReturn(HttpServletResponse.SC_OK).when(response).getStatus();
        lenient().doReturn(false).when(response).containsHeader("Cache-Control");
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    // ------------------------------------------------------------------
    // Static resource tests
    // ------------------------------------------------------------------

    @Test
    public void staticPngResourceReceivesCacheHeaders() throws Exception {
        // Setup test fixture.
        doReturn("/images/logo.png").when(request).getRequestURI();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("Cache-Control", "public, max-age=3600");
        verify(response, never()).setHeader(eq("Pragma"), anyString());
    }

    @Test
    public void staticCssResourceReceivesCacheHeaders() throws Exception {
        // Setup test fixture.
        doReturn("/css/styles.css").when(request).getRequestURI();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("Cache-Control", "public, max-age=3600");
    }

    @Test
    public void staticJsResourceReceivesCacheHeaders() throws Exception {
        // Setup test fixture.
        doReturn("/js/app.js").when(request).getRequestURI();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("Cache-Control", "public, max-age=3600");
    }

    // ------------------------------------------------------------------
    // Dynamic resource tests
    // ------------------------------------------------------------------

    @Test
    public void dynamicJspPageReceivesNoCacheHeaders() throws Exception {
        // Setup test fixture.
        doReturn("/index.jsp").when(request).getRequestURI();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader("Expires", 0);
    }

    @Test
    public void dynamicPageWithNoExtensionReceivesNoCacheHeaders() throws Exception {
        // Setup test fixture.
        doReturn("/admin/users").when(request).getRequestURI();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader("Expires", 0);
    }

    // ------------------------------------------------------------------
    // Existing Cache-Control header tests
    // ------------------------------------------------------------------

    @Test
    public void existingCacheControlHeaderIsNotOverwritten() throws Exception {
        // Setup test fixture.
        lenient().doReturn("/images/logo.png").when(request).getRequestURI();
        doReturn(true).when(response).containsHeader("Cache-Control");

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result: filter chain must still be invoked, but no headers set.
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
    }

    // ------------------------------------------------------------------
    // Error response tests
    // ------------------------------------------------------------------

    @Test
    public void notFoundResponseDoesNotReceiveCacheHeaders() throws Exception {
        // Setup test fixture.
        lenient().doReturn("/images/logo.png").when(request).getRequestURI();
        doReturn(HttpServletResponse.SC_NOT_FOUND).when(response).getStatus();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
    }

    @Test
    public void internalServerErrorDoesNotReceiveCacheHeaders() throws Exception {
        // Setup test fixture.
        lenient().doReturn("/index.jsp").when(request).getRequestURI();
        doReturn(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).when(response).getStatus();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
    }

    // ------------------------------------------------------------------
    // Committed response test
    // ------------------------------------------------------------------

    @Test
    public void committedResponseDoesNotReceiveCacheHeaders() throws Exception {
        // Setup test fixture.
        lenient().doReturn("/images/logo.png").when(request).getRequestURI();
        doReturn(true).when(response).isCommitted();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
    }

    // ------------------------------------------------------------------
    // Case-insensitive extension test
    // ------------------------------------------------------------------

    @Test
    public void extensionMatchingIsCaseInsensitive() throws Exception {
        // Setup test fixture.
        doReturn("/images/logo.PNG").when(request).getRequestURI();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(response).setHeader("Cache-Control", "public, max-age=3600");
    }

    // ------------------------------------------------------------------
    // 304 Not Modified test
    // ------------------------------------------------------------------

    @Test
    public void notModifiedResponseReceivesCacheHeaders() throws Exception {
        // Setup test fixture.
        doReturn("/images/logo.png").when(request).getRequestURI();
        doReturn(HttpServletResponse.SC_NOT_MODIFIED).when(response).getStatus();

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(response).setHeader("Cache-Control", "public, max-age=3600");
    }

    // ------------------------------------------------------------------
    // Configurable max-age via SystemProperty test
    // ------------------------------------------------------------------

    @Test
    public void customMaxAgeIsAppliedFromSystemProperty() throws Exception {
        // Setup test fixture.
        doReturn("/css/style.css").when(request).getRequestURI();
        CacheControlFilter.STATIC_RESOURCE_MAX_AGE.setValue(Duration.ofMinutes(30));

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result.
        verify(response).setHeader("Cache-Control", "public, max-age=1800");
    }

    // ------------------------------------------------------------------
    // Filter chain invocation order test
    // ------------------------------------------------------------------

    @Test
    public void filterChainIsInvokedBeforeHeadersAreSet() throws Exception {
        // Setup test fixture.
        doReturn("/images/logo.png").when(request).getRequestURI();
        // Execute system under test.
        filter.doFilter(request, response, filterChain);
        // Verify that chain.doFilter was called (it must execute before headers are applied).
        InOrder inOrder = inOrder(filterChain, response);
        inOrder.verify(filterChain).doFilter(request, response);
        inOrder.verify(response).setHeader("Cache-Control", "public, max-age=3600");
    }
}
