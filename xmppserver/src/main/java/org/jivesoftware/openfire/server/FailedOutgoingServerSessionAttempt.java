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

package org.jivesoftware.openfire.server;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Basic diagnostics for a failed outgoing server-to-server connection attempt.
 *
 * Instances of this type are intended to be stored in a clustered cache, which requires that
 * keys and values are serializable.
 */
public class FailedOutgoingServerSessionAttempt implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final String localDomain;
    private final String remoteDomain;
    private final Instant when;
    private final String errorType;
    private final String errorMessage;

    /**
     * Constructs diagnostics for one failed outgoing S2S connection establishment attempt.
     *
     * @param localDomain the local domain from which the outgoing attempt was initiated.
     * @param remoteDomain the remote peer domain that connection establishment targeted.
     * @param when timestamp that indicates when the failure was observed.
     * @param errorType fully qualified class name of the throwable that caused the failure, or null.
     * @param errorMessage throwable message that describes the failure, or null.
     */
    public FailedOutgoingServerSessionAttempt(final String localDomain, final String remoteDomain, final Instant when, final String errorType, final String errorMessage)
    {
        this.localDomain = localDomain;
        this.remoteDomain = remoteDomain;
        this.when = when;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    /**
     * @return the local domain from which the outgoing attempt was initiated.
     */
    public String getLocalDomain() {
        return localDomain;
    }

    /**
     * @return the remote domain for which connection establishment failed.
     */
    public String getRemoteDomain() {
        return remoteDomain;
    }

    /**
     * @return timestamp of when this failure was recorded.
     */
    public Instant getWhen() {
        return when;
    }

    /**
     * @return fully qualified class name of the exception that caused the failure, or null.
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * @return message associated with the exception that caused the failure, or null.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Creates diagnostics payload for a failed outgoing S2S connection attempt.
     *
     * @param localDomain the local domain from which the outgoing attempt was initiated.
     * @param remoteDomain the remote domain that connection establishment targeted.
     * @param cause the failure cause.
     * @return a diagnostics payload representing the provided failure.
     */
    public static FailedOutgoingServerSessionAttempt from(final String localDomain, final String remoteDomain, final Throwable cause)
    {
        final String type = cause == null ? null : cause.getClass().getName();
        final String message = cause == null ? null : Objects.toString(cause.getMessage(), null);
        return new FailedOutgoingServerSessionAttempt(localDomain, remoteDomain, Instant.now(), type, message);
    }
}


