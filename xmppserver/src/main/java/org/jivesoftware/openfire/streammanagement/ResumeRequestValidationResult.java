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
package org.jivesoftware.openfire.streammanagement;

import org.jivesoftware.openfire.session.LocalClientSession;
import org.xmpp.packet.PacketError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Outcome of StreamManager#validateResumeRequest(ResumeRequest): either the pre-existing session that
 * is to be resumed, or the condition to report as to why the resumption request cannot be honored.
 */
final class ResumeRequestValidationResult
{
    @Nullable private final LocalClientSession target;
    @Nullable private final PacketError.Condition failureCondition;

    private ResumeRequestValidationResult(@Nullable final LocalClientSession target, @Nullable final PacketError.Condition failureCondition)
    {
        this.target = target;
        this.failureCondition = failureCondition;
    }

    /**
     * Creates a result representing a successfully validated resume request.
     *
     * @param target the pre-existing session that is to be resumed (cannot be null).
     * @return a success result.
     */
    static ResumeRequestValidationResult success(@Nonnull final LocalClientSession target)
    {
        return new ResumeRequestValidationResult(target, null);
    }

    /**
     * Creates a result representing a resume request that failed validation.
     *
     * @param condition the condition to report as to why the resumption request cannot be honored (cannot be null).
     * @return a failure result.
     */
    static ResumeRequestValidationResult failure(@Nonnull final PacketError.Condition condition)
    {
        return new ResumeRequestValidationResult(null, condition);
    }

    /**
     * Returns whether the resume request was successfully validated.
     *
     * @return {@code true} if the request is valid and a target session was found.
     */
    boolean isSuccess()
    {
        return target != null;
    }

    /**
     * Returns the pre-existing session that is to be resumed. Only set when {@link #isSuccess()} returns {@code true}.
     * Guaranteed to be non-null when {@link #isSuccess()} returns {@code true}.
     *
     * @return the target session, or {@code null} on failure.
     */
    @Nullable
    LocalClientSession getTarget()
    {
        return target;
    }

    /**
     * Returns the condition to report as to why the resumption request cannot be honored. Only set when
     * {@link #isSuccess()} returns {@code false}. Guaranteed to be non-null when {@link #isSuccess()}
     * returns {@code false}.
     *
     * @return the failure condition, or {@code null} on success.
     */
    @Nullable
    PacketError.Condition getFailureCondition()
    {
        return failureCondition;
    }
}
