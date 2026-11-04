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
 * Counts of warnings and errors logged while the test ran.
 *
 * Only the warnings and errors recorded for this connection attempt decide the log check. Others logged by the server
 * during the test may be unrelated, so they are reported as information only.
 *
 * @param attemptWarnings warnings recorded for this attempt.
 * @param attemptErrors errors recorded for this attempt.
 * @param otherWarningsAndErrors warnings and errors logged during the test that were not recorded for this attempt.
 * @author Matthew Vivian
 */
public record LogSummary(int attemptWarnings, int attemptErrors, int otherWarningsAndErrors)
{
    public LogSummary
    {
        if (attemptWarnings < 0 || attemptErrors < 0 || otherWarningsAndErrors < 0) {
            throw new IllegalArgumentException("Log counts cannot be negative.");
        }
    }
}
