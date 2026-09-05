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

import org.dom4j.Element;
import org.jivesoftware.openfire.session.LocalClientSession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Outcome of {@link StreamManager#processSasl2Resume(ResumeRequest)}: either the {@code <resumed/>} element
 * (and the now-resumed session) to embed in a SASL2 {@code <success/>} response, or the {@code <failed/>} element to
 * embed instead.
 */
public final class Sasl2ResumeResult
{
    private final boolean success;
    private final Element resultElement;
    private final LocalClientSession resumedSession;

    /**
     * Constructs a result.
     *
     * @param success whether the resume request was honored.
     * @param resultElement the element to embed in the SASL2 {@code <success/>} response (cannot be null).
     * @param resumedSession the resumed session, or {@code null} on failure.
     */
    private Sasl2ResumeResult(final boolean success, @Nonnull final Element resultElement, @Nullable final LocalClientSession resumedSession)
    {
        this.success = success;
        this.resultElement = resultElement;
        this.resumedSession = resumedSession;
    }

    /**
     * Creates a result representing a successfully resumed session.
     *
     * @param resumedElement the {@code <resumed/>} element to embed in the SASL2 {@code <success/>} response (cannot be null).
     * @param resumedSession the session that was resumed (cannot be null).
     * @return a success result.
     */
    static Sasl2ResumeResult success(@Nonnull final Element resumedElement, @Nonnull final LocalClientSession resumedSession)
    {
        return new Sasl2ResumeResult(true, resumedElement, resumedSession);
    }

    /**
     * Creates a result representing a resume request that could not be honored.
     *
     * @param failedElement the {@code <failed/>} element to embed instead (cannot be null).
     * @return a failure result.
     */
    static Sasl2ResumeResult failure(@Nonnull final Element failedElement)
    {
        return new Sasl2ResumeResult(false, failedElement, null);
    }

    /**
     * Returns whether the resume request was honored.
     *
     * @return {@code true} if the session was successfully resumed.
     */
    public boolean isSuccess()
    {
        return success;
    }

    /**
     * Returns the element ({@code <resumed/>} on success, {@code <failed/>} otherwise) to embed in the SASL2
     * {@code <success/>} response.
     *
     * @return the result element (never null).
     */
    @Nonnull
    public Element getResultElement()
    {
        return resultElement;
    }

    /**
     * Returns the session that was resumed. Only set when {@link #isSuccess()} returns {@code true}.
     *
     * Guaranteed to be non-null when {@link #isSuccess()} returns {@code true}.
     *
     * @return the resumed session, or {@code null} on failure.
     */
    @Nullable
    public LocalClientSession getResumedSession()
    {
        return resumedSession;
    }
}
