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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Stream resumption for sessions with servers that connected to Openfire.
 *
 * A server may resume when Stream Management and resumption are enabled for servers ({@link StreamManager#SERVER_ACTIVE}
 * and {@link StreamManager#SERVER_RESUME_ENABLED}), once at least one of its domains is authenticated on the session.
 * Unlike a client, it resumes after authentication, as nothing else needs to be restored. A resume request refers to an
 * incoming server session on this cluster node, which is identified by its stream ID, and which only the same remote
 * server may resume, with the same or a stronger authentication method.
 *
 * Instances are thread-safe: they hold no state other than the session that they are bound to.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
final class ServerResumption implements Resumption<LocalIncomingServerSession>
{
    /**
     * The logger to use, which names the session of the stream.
     */
    private final Logger Log;

    private final LocalIncomingServerSession session;

    /**
     * Creates an instance bound to an incoming server session, that logs through a logger of its own class.
     *
     * @param session the session (cannot be null).
     */
    ServerResumption(@Nonnull final LocalIncomingServerSession session)
    {
        this(LoggerFactory.getLogger(ServerResumption.class), session);
    }

    /**
     * Creates an instance bound to an incoming server session.
     *
     * @param log the logger to use, so that what is logged can be related to the session of the stream.
     * @param session the session (cannot be null).
     */
    ServerResumption(@Nonnull final Logger log, @Nonnull final LocalIncomingServerSession session)
    {
        this.Log = log;
        this.session = session;
    }

    @Override
    public boolean isAllowed()
    {
        return StreamManager.SERVER_ACTIVE.getValue() && StreamManager.SERVER_RESUME_ENABLED.getValue() && !session.getValidatedDomains().isEmpty();
    }

    /**
     * A server session is never past the point at which it can resume: it resumes after authentication, and nothing
     * that resumption replaces happens after that.
     *
     * @return false
     */
    @Override
    public boolean isTooLateToResume()
    {
        return false;
    }

    /**
     * Creates the SM-ID, which is the Base64 encoding of the stream ID of the session. It does not depend on a domain or
     * resource.
     *
     * @return the SM-ID of the stream of the session (never null).
     */
    @Nonnull
    @Override
    public String createId()
    {
        return Base64.getEncoder().encodeToString(session.getStreamID().getID().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Finds the incoming server session that has the stream ID in the SM-ID. A previd that cannot be decoded, that
     * does not identify another incoming server session on this cluster node, or that identifies a session that the
     * session may not take over ({@link #mayTakeOver(LocalIncomingServerSession)}), is reported as 'item-not-found', as
     * for a previd that Openfire does not recognise (XEP-0198 § 5). The response thereby does not reveal whether a
     * session exists for a previd that the remote server may not resume.
     *
     * @param previd the SM-ID in the 'previd' attribute of the resume request (cannot be null).
     * @return the session that the request refers to, or the condition to report when it is not found (never null).
     */
    @Nonnull
    @Override
    public ResumeRequestValidationResult<LocalIncomingServerSession> locate(@Nonnull final String previd)
    {
        final String streamId;
        try {
            streamId = new String(Base64.getDecoder().decode(previd), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Log.debug("Unable to resume a session for session {}, as its previd cannot be decoded: {}", session.getStreamID(), e.getMessage());
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }

        final LocalIncomingServerSession otherSession = XMPPServer.getInstance().getSessionManager().getIncomingServerSession(BasicStreamIDFactory.createStreamID(streamId));
        if (otherSession == null || otherSession == session) {
            Log.debug("Unable to resume a session for session {}, as no other incoming server session with stream ID '{}' exists on this cluster node.", session.getStreamID(), streamId);
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }
        if (!mayTakeOver(otherSession)) {
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }
        return ResumeRequestValidationResult.success(otherSession);
    }

    /**
     * Checks whether the session may take over another incoming server session, which requires both to belong to the
     * same remote server. The first domain that was authenticated on each session must be the same, and the method with
     * which it was authenticated on the session must be the same as, or stronger than, the one on the other session
     * ({@link #isSameOrStronger}). The domains that the remote server claimed in its stream headers are not used.
     *
     * @param otherSession the session that a resume request refers to (cannot be null).
     * @return true if the session may resume the other session.
     */
    private boolean mayTakeOver(@Nonnull final LocalIncomingServerSession otherSession)
    {
        final LocalIncomingServerSession.DomainAuthentication own = session.getFirstAuthentication();
        final LocalIncomingServerSession.DomainAuthentication other = otherSession.getFirstAuthentication();
        final boolean allowed = own != null && other != null
            && isSameDomain(own.domain(), other.domain())
            && isSameOrStronger(own.method(), other.method());
        if (!allowed) {
            Log.debug("Not allowing session {} to resume session {}: the first authenticated domain and method are '{}' ({}) on the resuming session and '{}' ({}) on the session to resume, while the same domain with the same or a stronger method is required.",
                session.getStreamID(), otherSession.getStreamID(),
                own == null ? null : own.domain(), own == null ? null : own.method(),
                other == null ? null : other.domain(), other == null ? null : other.method());
        }
        return allowed;
    }

    /**
     * Checks whether two domain names are the same after the normalisation that {@link JID} applies to a domain, so that
     * they compare equal regardless of case.
     *
     * @param domain a domain name (cannot be null).
     * @param otherDomain another domain name (cannot be null).
     * @return true if both name the same domain, false if they don't or if either is not a valid domain name.
     */
    static boolean isSameDomain(@Nonnull final String domain, @Nonnull final String otherDomain)
    {
        try {
            return JID.domainprep(domain).equals(JID.domainprep(otherDomain));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks whether an authentication method is the same as, or stronger than, another. A certificate
     * ({@link ServerSession.AuthenticationMethod#SASL_EXTERNAL}) is stronger than
     * {@link ServerSession.AuthenticationMethod#DIALBACK}. Any other method is only the same as itself, and is not
     * compared with others. An unknown (null) method is neither the same as nor stronger than any method.
     *
     * @param method the method that is checked (can be null).
     * @param otherMethod the method that it is compared with (can be null).
     * @return true if method is the same as, or stronger than, otherMethod.
     */
    static boolean isSameOrStronger(@Nullable final ServerSession.AuthenticationMethod method, @Nullable final ServerSession.AuthenticationMethod otherMethod)
    {
        if (method == null || otherMethod == null) {
            return false;
        }
        if (method == otherMethod) {
            return true;
        }
        return method == ServerSession.AuthenticationMethod.SASL_EXTERNAL && otherMethod == ServerSession.AuthenticationMethod.DIALBACK;
    }

    @Override
    public boolean claim(@Nonnull final LocalIncomingServerSession target)
    {
        final boolean claimed = XMPPServer.getInstance().getSessionManager().removeDetached(target);
        if (!claimed) {
            Log.debug("Unable for session {} to resume session {}, as that is no longer detached: it was ended, or resumed on another connection.", session.getStreamID(), target.getStreamID());
        }
        return claimed;
    }
}
