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

/**
 * The outcome of one check in the server-to-server connection test.
 *
 * @author Matthew Vivian
 */
public enum CheckStatus
{
    /**
     * The step happened and succeeded.
     */
    PASS("pass"),

    /**
     * The step succeeded, but in a way the remote administrator may want to change.
     */
    WARNING("warning"),

    /**
     * The step happened and failed.
     */
    FAIL("fail"),

    /**
     * The step did not happen, because the attempt stopped at an earlier check.
     */
    NOT_REACHED("not_reached"),

    /**
     * Nothing was recorded for the step in this test, for example because an existing connection was reused. Never a
     * pass: the check has no evidence either way.
     */
    NOT_OBSERVED("not_observed");

    private final String keySegment;

    CheckStatus(final String keySegment)
    {
        this.keySegment = keySegment;
    }

    /**
     * The i18n key of the status label shown in the admin console.
     *
     * @return the resource bundle key, never null.
     */
    public String labelKey()
    {
        return S2SCheck.KEY_PREFIX + "status." + keySegment;
    }
}
