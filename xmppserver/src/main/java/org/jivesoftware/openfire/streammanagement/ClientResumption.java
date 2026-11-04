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
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ClientSessionTask;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.RemoteSessionTask;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.StringTokenizer;

/**
 * Stream resumption for client sessions.
 *
 * A client session may resume when it is authenticated by a user that is not anonymous. A resume request refers to a
 * session of that same user, on this cluster node.
 *
 * Instances are thread-safe: they hold no state other than the session that they are bound to.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
final class ClientResumption implements Resumption<LocalClientSession>
{
    /**
     * The logger to use, which names the session of the stream.
     */
    private final Logger Log;

    private final LocalClientSession session;

    /**
     * Creates an instance bound to a client session, that logs through a logger of its own class.
     *
     * @param session the session (cannot be null).
     */
    ClientResumption(@Nonnull final LocalClientSession session)
    {
        this(LoggerFactory.getLogger(ClientResumption.class), session);
    }

    /**
     * Creates an instance bound to a client session.
     *
     * @param log the logger to use, so that what is logged can be related to the session of the stream.
     * @param session the session (cannot be null).
     */
    ClientResumption(@Nonnull final Logger log, @Nonnull final LocalClientSession session)
    {
        this.Log = log;
        this.session = session;
    }

    @Override
    public boolean isAllowed()
    {
        final AuthToken authToken = session.getAuthToken();
        return authToken != null && !authToken.isAnonymous();
    }

    /**
     * Creates the SM-ID, which is the Base64 encoding of the resource of the session and its stream ID, separated by a
     * NUL character.
     *
     * @return the SM-ID of the stream of the session (never null).
     */
    @Nonnull
    @Override
    public String createId()
    {
        return Base64.getEncoder().encodeToString((session.getAddress().getResource() + "\0" + session.getStreamID().getID()).getBytes(StandardCharsets.UTF_8));
    }

    @Nonnull
    @Override
    public ResumeRequestValidationResult<LocalClientSession> locate(@Nonnull final String previd)
    {
        final AuthToken authToken = session.getAuthToken();
        if (authToken == null) {
            Log.debug("Unable to process session resumption attempt, as session {} does not provide any auth context.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        if (authToken.isAnonymous()) {
            Log.debug("Unable to process session resumption attempt, as session {} is anonymous.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        // Decode previd.
        String resource;
        String streamId;
        try {
            StringTokenizer toks = new StringTokenizer(new String(Base64.getDecoder().decode(previd), StandardCharsets.UTF_8), "\0");
            resource = toks.nextToken();
            streamId = toks.nextToken();
        } catch (Exception e) {
            Log.debug("Exception from previd decode:", e);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        final JID fullJid = new JID(authToken.getUsername(), session.getServerName(), resource, true);
        Log.debug("Resuming session for '{}'. Current session: {}", fullJid, session.getStreamID());

        // Locate existing session.
        final ClientSession route = XMPPServer.getInstance().getRoutingTable().getClientRoute(fullJid);
        if (route == null) {
            Log.debug("Not able for client of '{}' to resume a session on this cluster node. No session was found for this client.", fullJid);
            if (StreamManager.LOCATION_TERMINATE_OTHERS_ENABLED.getValue()) {
                // When the client tries to resume a connection on this host, it is unlikely to try other hosts. Remove any detached sessions living elsewhere in the cluster. (OF-2753)
                CacheFactory.doClusterTask(new ClientSessionTask(fullJid, RemoteSessionTask.Operation.removeDetached));
            }
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }

        if (!(route instanceof LocalClientSession otherSession)) {
            Log.debug("Not allowing a client of '{}' to resume a session on this cluster node. The session can only be resumed on the Openfire cluster node where the original session was connected.", fullJid);
            if (StreamManager.LOCATION_TERMINATE_OTHERS_ENABLED.getValue()) {
                // When the client tries to resume a connection on this host, it is unlikely to try other hosts. Remove any detached sessions living elsewhere in the cluster. (OF-2753)
                CacheFactory.doClusterTask(new ClientSessionTask(fullJid, RemoteSessionTask.Operation.removeDetached));
            }
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        if (!otherSession.getStreamID().getID().equals(streamId)) {
            Log.debug("Not allowing a client of '{}' to resume a session, as the stream ID in the previd ({}) does not match that of the existing session ({}).", fullJid, streamId, otherSession.getStreamID());
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }
        Log.debug("Found existing session for '{}', checking status", fullJid);

        return ResumeRequestValidationResult.success(otherSession);
    }
}
