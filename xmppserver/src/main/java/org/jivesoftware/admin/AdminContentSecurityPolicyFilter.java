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

import org.jivesoftware.openfire.container.AdminConsolePlugin;

/**
 * Adds Content-Security-Policy headers to HTTP responses based on configuration from Admin Console properties.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class AdminContentSecurityPolicyFilter extends ContentSecurityPolicyFilter
{
    public AdminContentSecurityPolicyFilter()
    {
        super(AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_ENABLED, AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE);
    }
}
