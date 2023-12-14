/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class SiteMinderServletRequestAuthenticatorTest {

    private SiteMinderServletRequestAuthenticator authenticator;

    @Mock private HttpServletRequest request;

    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws Exception {
        authenticator = new SiteMinderServletRequestAuthenticator();
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    @Test
    public void willAuthenticateAUser() {

        final String userId = "a-test-user";
        doReturn(userId).when(request).getHeader("SM_USER");

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(userId));
    }

    @Test
    public void willNotAuthenticateAMissingUser() {

        doReturn(null).when(request).getHeader("SM_USER");

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(nullValue()));
    }

    @Test
    public void willNotAuthenticateABlankUser() {

        doReturn("").when(request).getHeader("SM_USER");

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(nullValue()));
    }

    @Test
    public void defaultHeaderIsCorrect() {

        assertThat(SiteMinderServletRequestAuthenticator.SITE_MINDER_HEADER.getValue(), is("SM_USER"));

    }

    @Test
    public void willAuthenticateAUserWithDifferentHeader() {

        final String userId = "a-test-user";
        final String header = "a-custom-header";
        SiteMinderServletRequestAuthenticator.SITE_MINDER_HEADER.setValue(header);
        doReturn(userId).when(request).getHeader(header);

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(userId));
    }
}
