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
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.xmpp.packet.PacketError;

import javax.annotation.Nonnull;

/**
 * The parts of XEP-0198 stream resumption (§ 5) that depend on the type of session: whether a session may resume, the
 * SM-ID that identifies the stream of a session, how the session that a resume request refers to is found, and how it
 * is taken from the detached sessions before it is resumed.
 *
 * Each instance of an implementation that supports resumption is bound to one session: the session that enables
 * resumption for its own stream, or that requests to resume the stream of another session. An implementation that
 * does not support resumption can be shared by sessions. Implementations must be thread-safe.
 *
 * @param <S> the type of session that can be resumed.
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
interface Resumption<S extends LocalSession>
{
    /**
     * Returns an implementation that matches the type of a session. An implementation that supports resumption is
     * bound to that session.
     *
     * @param session the session (cannot be null).
     * @param log the logger to use, so that what is logged can be related to the session (cannot be null).
     * @return the implementation for the session (never null).
     */
    @Nonnull
    static Resumption<?> forSession(@Nonnull final LocalSession session, @Nonnull final Logger log)
    {
        if (session instanceof LocalClientSession clientSession) {
            return new ClientResumption(log, clientSession);
        }
        if (session instanceof LocalIncomingServerSession serverSession) {
            return new ServerResumption(log, serverSession);
        }
        return Unsupported.INSTANCE;
    }

    /**
     * Checks whether the session is in a state in which it may enable resumption, or resume another session.
     *
     * @return true if the session may enable resumption, or resume another session.
     */
    boolean isAllowed();

    /**
     * Checks whether the stream of the session has gone past the point at which it can resume another session, for
     * example because the session has done what resumption would replace.
     *
     * @return true if the session can no longer resume another session.
     */
    boolean isTooLateToResume();

    /**
     * Creates the SM-ID that identifies the stream of the session, for the 'id' attribute of {@code <enabled/>}.
     *
     * The SM-ID must meet the requirements of XEP-0198 § 5: it must not be reused for simultaneous or later sessions
     * while Openfire runs, it may contain any character that is allowed in an XML attribute, and it should not be
     * longer than 4000 bytes. The peer treats it as opaque. {@link #locate(String)}, invoked on the implementation for
     * another session, must find this session for the SM-ID.
     *
     * @return the SM-ID of the stream of the session (never null).
     */
    @Nonnull
    String createId();

    /**
     * Finds the session that a resume request, received on the session, refers to.
     *
     * The peer supplies the SM-ID, so the SM-ID cannot establish who is resuming: the session that is found must
     * belong to the same authenticated entity as the session on which the request is received. This does not check
     * whether the session that is found can be resumed in other respects.
     *
     * @param previd the SM-ID in the 'previd' attribute of the resume request (cannot be null).
     * @return the session that the request refers to, or the condition to report when it is not found (never null).
     */
    @Nonnull
    ResumeRequestValidationResult<S> locate(@Nonnull String previd);

    /**
     * Takes the session that a resume request refers to from the detached sessions, after it has been detached, and
     * before its connection is replaced. A session that is taken this way is no longer ended because it was detached
     * for too long, and cannot be taken by another resume request.
     *
     * @param target the detached session that is to be resumed (cannot be null).
     * @return false if the session cannot be resumed, as it is no longer detached: it was ended, or taken by another
     *         resume request.
     */
    boolean claim(@Nonnull S target);

    /**
     * Resumption for types of session that do not support it.
     */
    final class Unsupported implements Resumption<LocalSession>
    {
        static final Unsupported INSTANCE = new Unsupported();

        private Unsupported()
        {
        }

        @Override
        public boolean isAllowed()
        {
            return false;
        }

        @Override
        public boolean isTooLateToResume()
        {
            return true;
        }

        @Nonnull
        @Override
        public String createId()
        {
            throw new UnsupportedOperationException("Stream resumption is not supported for this type of session.");
        }

        @Nonnull
        @Override
        public ResumeRequestValidationResult<LocalSession> locate(@Nonnull final String previd)
        {
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        @Override
        public boolean claim(@Nonnull final LocalSession target)
        {
            return false;
        }
    }
}
