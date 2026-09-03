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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a XEP-0198 stream resumption request: the data carried by a {@code <resume/>} element.
 *
 * This is used by both variants of stream resumption defined by XEP-0198:
 * <ul>
 *     <li>the traditional flow, in which the client sends a top-level {@code <resume/>} element after having
 *         established a stream (see § 5, "Resumption"); and</li>
 *     <li>the inline flow, in which the {@code <resume/>} element is instead nested inside a SASL2 (XEP-0388)
 *         {@code <authenticate/>} element (see § 9.2, "Inline Stream Resumption").</li>
 * </ul>
 *
 * Either way, the element takes the same shape:
 * <pre>{@code
 * <resume xmlns='urn:xmpp:sm:3' h='some-sequence-number' previd='some-long-sm-id'/>
 * }</pre>
 */
public final class ResumeRequest
{
    private static final String ELEMENT_NAME = "resume";

    private final String previd;
    private final long h;
    private final String namespace;

    /**
     * Constructs a request.
     *
     * @param previd the SM-ID of the former stream that the client wishes to resume (cannot be null).
     * @param h the sequence number of the last stanza that the client received from the server over the former stream.
     * @param namespace the Stream Management namespace that the client used for this request (cannot be null).
     */
    private ResumeRequest(@Nonnull final String previd, final long h, @Nonnull final String namespace)
    {
        this.previd = previd;
        this.h = h;
        this.namespace = namespace;
    }

    /**
     * Parses a (traditional, top-level) XEP-0198 {@code <resume/>} element.
     *
     * @param resumeElement the {@code <resume/>} element (cannot be null).
     * @return the parsed request (never null).
     * @throws MalformedResumeRequestException if the element is malformed.
     */
    @Nonnull
    public static ResumeRequest from(@Nonnull final Element resumeElement) throws MalformedResumeRequestException
    {
        return parse(resumeElement, resumeElement.getNamespaceURI());
    }

    /**
     * Parses the inline XEP-0198 {@code <resume/>} content of a SASL2 {@code <authenticate/>} element.
     *
     * Returns {@code null} when the element carries no {@code <resume/>} child (in a namespace recognized as a
     * Stream Management namespace), in which case there is nothing for the caller to act on.
     *
     * @param authenticateElement the {@code <authenticate/>} element (cannot be null).
     * @return the parsed request, or {@code null} if the element carries no inline resume request.
     * @throws MalformedResumeRequestException if a {@code <resume/>} element is present but is malformed.
     */
    @Nullable
    public static ResumeRequest fromSasl2Authenticate(@Nonnull final Element authenticateElement) throws MalformedResumeRequestException
    {
        final Element resumeElement = authenticateElement.element(ELEMENT_NAME);
        if (resumeElement == null) {
            return null;
        }

        final String namespace = resumeElement.getNamespaceURI();
        if (!StreamManager.NAMESPACE_V3.equals(namespace) && !StreamManager.NAMESPACE_V2.equals(namespace)) {
            // Not a Stream Management resume request (could be some other, unrelated, element named 'resume').
            return null;
        }

        return parse(resumeElement, namespace);
    }

    @Nonnull
    private static ResumeRequest parse(@Nonnull final Element resumeElement, @Nonnull final String namespace) throws MalformedResumeRequestException
    {
        final String previd = resumeElement.attributeValue("previd");
        if (previd == null || previd.isEmpty()) {
            throw new MalformedResumeRequestException("Stream resumption requires a 'previd' attribute.");
        }

        final String hValue = resumeElement.attributeValue("h");
        if (hValue == null || hValue.isEmpty()) {
            throw new MalformedResumeRequestException("Stream resumption requires an 'h' attribute.");
        }

        final long h;
        try {
            h = Long.parseLong(hValue);
        } catch (final NumberFormatException e) {
            throw new MalformedResumeRequestException("Stream resumption 'h' attribute must be a number, but was: " + hValue);
        }
        if (h < 0 || h > StreamManager.MASK) {
            // XEP-0198 § 4: 'h' is an unsigned 32-bit integer. Out-of-range values are rejected here rather than
            // later by StreamManager#validateClientAcknowledgement(long), which throws for them.
            throw new MalformedResumeRequestException("Stream resumption 'h' attribute must be an unsigned 32-bit integer, but was: " + h);
        }

        return new ResumeRequest(previd, h, namespace);
    }

    /**
     * Returns the SM-ID of the former stream that the client wishes to resume.
     *
     * @return the (still Base64-encoded) SM-ID.
     */
    @Nonnull
    public String getPrevId()
    {
        return previd;
    }

    /**
     * Returns the sequence number of the last stanza that the client received from the server over the former
     * stream.
     *
     * @return a non-negative sequence number.
     */
    public long getH()
    {
        return h;
    }

    /**
     * Returns the Stream Management namespace that the client used for this request.
     *
     * @return a Stream Management namespace.
     */
    @Nonnull
    public String getNamespace()
    {
        return namespace;
    }

    @Override
    public String toString()
    {
        return "ResumeRequest{previd='" + previd + "', h=" + h + ", namespace='" + namespace + "'}";
    }
}
