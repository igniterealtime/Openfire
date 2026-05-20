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

package org.jivesoftware.openfire.session;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * The result of a {@link LocalOutgoingServerSession#authenticateDomain(DomainPair)} invocation, carrying the
 * success/failure outcome together with a human-readable diagnostic log that was accumulated during the attempt.
 */
public class DomainAuthResult
{
    private final boolean success;
    private final List<String> diagnosticLog;

    private DomainAuthResult(final boolean success, @Nonnull final List<String> diagnosticLog)
    {
        this.success = success;
        this.diagnosticLog = Collections.unmodifiableList(diagnosticLog);
    }

    /**
     * @return {@code true} if domain authentication succeeded, {@code false} otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns an ordered, human-readable log of the steps taken during the authentication attempt.
     *
     * Each entry corresponds to one log statement emitted during the attempt, prefixed with its
     * level (e.g. {@code "DEBUG: …"}, {@code "WARN: …"}).
     *
     * @return an unmodifiable, ordered list of diagnostic log lines; never {@code null}.
     */
    @Nonnull
    public List<String> getDiagnosticLog() {
        return diagnosticLog;
    }

    /**
     * Creates a result representing a successful domain authentication.
     *
     * @param diagnosticLog log lines accumulated during the attempt.
     * @return a success result.
     */
    @Nonnull
    public static DomainAuthResult success(@Nonnull final List<String> diagnosticLog)
    {
        return new DomainAuthResult(true, diagnosticLog);
    }

    /**
     * Creates a result representing a failed domain authentication.
     *
     * @param diagnosticLog log lines accumulated during the attempt.
     * @return a failure result.
     */
    @Nonnull
    public static DomainAuthResult failure(@Nonnull final List<String> diagnosticLog)
    {
        return new DomainAuthResult(false, diagnosticLog);
    }
}

