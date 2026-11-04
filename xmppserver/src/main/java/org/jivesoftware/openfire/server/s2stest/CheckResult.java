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
package org.jivesoftware.openfire.server.s2stest;

import java.util.List;
import java.util.Objects;

/**
 * The result of one check: its status and the message that explains it.
 *
 * The message is an i18n key with plain-text parameters, never markup. Parameters can hold text that the remote server
 * controls (a stream error, the names in its certificate), so whatever renders them must escape them.
 *
 * @param check the check this result belongs to.
 * @param status the outcome of the check.
 * @param messageKey the i18n key of the explanation.
 * @param parameters the values for the explanation's placeholders, in order.
 * @author Matthew Vivian
 */
public record CheckResult(S2SCheck check, CheckStatus status, String messageKey, List<String> parameters)
{
    public CheckResult
    {
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(messageKey, "messageKey");
        parameters = List.copyOf(parameters);
    }
}
