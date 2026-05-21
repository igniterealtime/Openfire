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
package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.OccupantManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implements MUC2-PAM (urn:xmpp:muc:pam:0): server-side handling of bare-JID occupancy sessions.
 *
 * Clients send an IQ-set containing {@code <join xmlns='urn:xmpp:muc:0' room='...'>} or
 * {@code <part xmlns='urn:xmpp:muc:0' room='...'>} to their own bare JID. This handler
 * forwards the request to the MUC room, tracks the pending IQ, and on receiving the room's
 * IQ-result records (or removes) the bare-JID session in the OccupantManager.
 *
 * Groupchat messages from the room addressed to the bare JID are silently dropped by
 * {@code MultiUserChatServiceImpl.processNonOccupantMessage()}.
 *
 * @see <a href="https://xmpp.org/extensions/inbox/muc2-addressing-occupancy.html">muc2-addressing-occupancy ProtoXEP</a>
 */
public class IQMuc2PamHandler extends IQHandler implements ServerFeaturesProvider {

    private static final Logger Log = LoggerFactory.getLogger(IQMuc2PamHandler.class);

    public static final String NAMESPACE = "urn:xmpp:muc:0";
    public static final String PAM_NAMESPACE = "urn:xmpp:muc:pam:0";
    public static final String ELEMENT_JOIN = "join";
    public static final String ELEMENT_PART = "part";

    /** Timeout for pending IQ responses from the MUC room (seconds). */
    private static final long PENDING_IQ_TIMEOUT_SECONDS = 30;

    private final IQHandlerInfo info;

    /**
     * Pending IQ entries: maps the forwarded IQ id to the original client IQ and metadata.
     */
    private final Map<String, PendingIQ> pendingIQs = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "muc2-pam-timeout");
        t.setDaemon(true);
        return t;
    });

    private static final class PendingIQ {
        final IQ originalIQ;
        final JID senderBareJID;
        final JID roomJID;
        final String nickname;
        final boolean isJoin;

        PendingIQ(IQ originalIQ, JID senderBareJID, JID roomJID, String nickname, boolean isJoin) {
            this.originalIQ = originalIQ;
            this.senderBareJID = senderBareJID;
            this.roomJID = roomJID;
            this.nickname = nickname;
            this.isJoin = isJoin;
        }
    }

    public IQMuc2PamHandler() {
        super("MUC2 PAM Handler");
        info = new IQHandlerInfo(ELEMENT_JOIN, NAMESPACE);
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(PAM_NAMESPACE).iterator();
    }

    /**
     * Do not perform the "no such user" check: the IQ is addressed to the sender's own bare JID,
     * which is a valid local user.
     */
    @Override
    public boolean performNoSuchUserCheck() {
        return false;
    }

    @Override
    public IQ handleIQ(final IQ packet) throws UnauthorizedException {
        if (packet.isResponse()) {
            // This is a result/error from the MUC room — dispatch it back to the original client.
            handleRoomResponse(packet);
            return null;
        }

        if (!IQ.Type.set.equals(packet.getType())) {
            final IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.bad_request);
            return error;
        }

        final JID senderFullJID = packet.getFrom();
        final JID senderBareJID = senderFullJID.asBareJID();
        final JID toJID = packet.getTo();

        // The IQ must be addressed to the sender's own bare JID.
        if (toJID == null || !toJID.asBareJID().equals(senderBareJID)) {
            final IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.bad_request);
            return error;
        }

        final Element child = packet.getChildElement();
        if (child == null) {
            final IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.bad_request);
            return error;
        }

        final String elementName = child.getName();
        final boolean isJoin = ELEMENT_JOIN.equals(elementName);
        final boolean isPart = ELEMENT_PART.equals(elementName);

        if (!isJoin && !isPart) {
            final IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.bad_request);
            return error;
        }

        final String roomAttr = child.attributeValue("room");
        if (roomAttr == null || roomAttr.isEmpty()) {
            final IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.bad_request);
            return error;
        }

        final JID roomJID;
        try {
            roomJID = new JID(roomAttr);
        } catch (IllegalArgumentException e) {
            final IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.jid_malformed);
            return error;
        }

        String nickname = null;
        if (isJoin) {
            final Element nickEl = child.element("nickname");
            if (nickEl != null) {
                nickname = nickEl.getTextTrim();
            }
            if (nickname == null || nickname.isEmpty()) {
                final IQ error = IQ.createResultIQ(packet);
                error.setError(PacketError.Condition.bad_request);
                return error;
            }
        }

        // Build the forwarded IQ to send to the room.
        final IQ forwardedIQ = new IQ(IQ.Type.set);
        forwardedIQ.setFrom(senderBareJID);
        forwardedIQ.setTo(roomJID.asBareJID());
        final Element forwardedChild = forwardedIQ.setChildElement(elementName, NAMESPACE);
        forwardedChild.addAttribute("room", roomAttr);
        if (isJoin && nickname != null) {
            forwardedChild.addElement("nickname").addText(nickname);
        }

        // Store pending IQ entry.
        final String pendingKey = forwardedIQ.getID();
        final PendingIQ pending = new PendingIQ(packet, senderBareJID, roomJID.asBareJID(), nickname, isJoin);
        pendingIQs.put(pendingKey, pending);

        // Schedule timeout cleanup.
        timeoutExecutor.schedule(() -> {
            final PendingIQ timedOut = pendingIQs.remove(pendingKey);
            if (timedOut != null) {
                Log.warn("MUC2-PAM: timed out waiting for room IQ result for pending key {}", pendingKey);
                final IQ errorResponse = IQ.createResultIQ(timedOut.originalIQ);
                errorResponse.setError(PacketError.Condition.remote_server_timeout);
                XMPPServer.getInstance().getPacketRouter().route(errorResponse);
            }
        }, PENDING_IQ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Route the forwarded IQ to the room.
        Log.debug("MUC2-PAM: forwarding {} IQ from {} to room {}", elementName, senderBareJID, roomJID);
        XMPPServer.getInstance().getPacketRouter().route(forwardedIQ);

        // Return null — response will be sent asynchronously when the room replies.
        return null;
    }

    /**
     * Handles an IQ result or error returned by the MUC room for a previously forwarded PAM IQ.
     */
    private void handleRoomResponse(final IQ response) {
        final String pendingKey = response.getID();
        final PendingIQ pending = pendingIQs.remove(pendingKey);
        if (pending == null) {
            Log.debug("MUC2-PAM: received room IQ response with unknown id '{}', ignoring.", pendingKey);
            return;
        }

        if (IQ.Type.result.equals(response.getType())) {
            // Success: update bare-JID session tracking.
            final MultiUserChatService mucService = XMPPServer.getInstance()
                .getMultiUserChatManager().getMultiUserChatService(pending.roomJID);
            if (mucService != null) {
                final OccupantManager occupantManager = ((org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl) mucService).getOccupantManager();
                final JID serviceJID = new JID(mucService.getServiceDomain());
                if (pending.isJoin) {
                    occupantManager.addBareJidSession(serviceJID, pending.roomJID, pending.senderBareJID, pending.nickname);
                    Log.debug("MUC2-PAM: recorded bare-JID session for {} in room {}", pending.senderBareJID, pending.roomJID);
                } else {
                    occupantManager.removeBareJidSession(pending.senderBareJID, pending.roomJID);
                    Log.debug("MUC2-PAM: removed bare-JID session for {} from room {}", pending.senderBareJID, pending.roomJID);
                }
            } else {
                Log.warn("MUC2-PAM: could not find MUC service for room {} to update session tracking.", pending.roomJID);
            }
        } else {
            Log.debug("MUC2-PAM: room returned error for {} IQ from {}: {}", pending.isJoin ? "join" : "part", pending.senderBareJID, response.getError());
        }

        // Forward the result/error back to the original client.
        final IQ clientResponse = IQ.createResultIQ(pending.originalIQ);
        if (IQ.Type.error.equals(response.getType()) && response.getError() != null) {
            clientResponse.setError(response.getError());
        }
        XMPPServer.getInstance().getPacketRouter().route(clientResponse);
    }

    @Override
    public void destroy() {
        timeoutExecutor.shutdownNow();
        super.destroy();
    }
}
