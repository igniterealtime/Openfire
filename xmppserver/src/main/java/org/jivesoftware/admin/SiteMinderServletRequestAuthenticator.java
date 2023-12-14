/*
 * Copyright (C) 2017-2019 Ignite Realtime Foundation. All rights reserved.
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

import javax.servlet.http.HttpServletRequest;

import org.jivesoftware.util.SystemProperty;

/**
 * <p>
 * Enables CA SiteMinder/Single Sign-On authentication to the admin console - https://www.ca.com/gb/products/ca-single-sign-on.html
 * </p>
 * <p>
 * To enable, set the system property {@code adminConsole.servlet-request-authenticator} =
 * {@code org.jivesoftware.admin.SiteMinderServletRequestAuthenticator} and restart Openfire.
 * </p>
 */
public class SiteMinderServletRequestAuthenticator implements ServletRequestAuthenticator {

    public static final SystemProperty<String> SITE_MINDER_HEADER = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.siteMinderHeader")
        .setDefaultValue("SM_USER")
        .setDynamic(true)
        .build();

    /**
     * Indicates if this ServletRequestAuthenticator is enabled or not
     *
     * @return {@code true} if enabled, otherwise {@code false}
     */
    public static boolean isEnabled() {
        return AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(SiteMinderServletRequestAuthenticator.class);
    }

    @Override
    public String authenticateRequest(final HttpServletRequest request) {
        final String smUser = request.getHeader(SITE_MINDER_HEADER.getValue());
        if (smUser == null || smUser.trim().isEmpty()) {
            // SiteMinder has not authenticated the user
            return null;
        } else {
            return smUser;
        }
    }
}
