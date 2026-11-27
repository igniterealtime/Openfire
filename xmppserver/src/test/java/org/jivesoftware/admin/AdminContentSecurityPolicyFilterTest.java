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
import org.jivesoftware.openfire.container.AdminConsolePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdminContentSecurityPolicyFilter}, in particular the frame-ancestors directive of the default
 * admin console policy.
 *
 * @author Matthew Vivian
 */
public class AdminContentSecurityPolicyFilterTest
{
    private static final String DEFAULT_POLICY = AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE.getDefaultValue();

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that the default policy allows only the admin console's own origin to frame its pages.
     */
    @Test
    public void testDefaultPolicyHasFrameAncestorsSelf()
    {
        assertTrue(DEFAULT_POLICY.contains("frame-ancestors 'self';"), "the default policy must limit framing to the console's own origin");
    }

    /**
     * Verifies that, when the policy is not configured, the policy that the filter sends limits framing to the admin
     * console's own origin, and that the rest of the filter chain runs.
     */
    @Test
    public void testFilterSendsFrameAncestorsSelfByDefault() throws Exception
    {
        // Setup test fixture.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);

        // Execute system under test.
        new AdminContentSecurityPolicyFilter().doFilter(request, response, chain);

        // Verify results.
        verify(response, times(1).description("the policy sent by default must limit framing to the console's own origin")).setHeader(eq("Content-Security-Policy"), contains("frame-ancestors 'self';"));
        verify(chain, times(1).description("the rest of the filter chain must run")).doFilter(request, response);
    }

    /**
     * Verifies that a policy an administrator has configured is sent unchanged, so that it can allow a portal to frame
     * the admin console.
     */
    @Test
    public void testFilterSendsConfiguredPolicyUnchanged() throws Exception
    {
        final String configured = "default-src 'self'; frame-ancestors 'self' https://portal.example.org;";
        AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE.setValue(configured);
        final HttpServletResponse response = mock(HttpServletResponse.class);

        new AdminContentSecurityPolicyFilter().doFilter(mock(HttpServletRequest.class), response, mock(FilterChain.class));

        verify(response, times(1).description("a configured policy must be sent unchanged")).setHeader("Content-Security-Policy", configured);
    }
}
