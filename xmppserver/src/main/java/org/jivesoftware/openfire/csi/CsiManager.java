/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.csi;

import org.dom4j.Element;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Handles Client State Indication nonzas for one particular client session.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0352.html">XEP-0352: Client State Indication</a>
 */
public class CsiManager
{
    public static final Logger Log = LoggerFactory.getLogger(CsiManager.class);

    /**
     * Controls if Client State Indication functionality is made available to clients.
     */
    public static SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("xmpp.client.csi.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    /**
     * Determines if 'unimportant' stanzas are delayed for a client that is inactive.
     */
    public static SystemProperty<Boolean> DELAY_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("xmpp.client.csi.delay.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    /**
     * Determines the maximum duration of stanzas being delayed for a client that is inactive.
     */
    public static SystemProperty<Duration> DELAY_MAX_DURATION = SystemProperty.Builder.ofType( Duration.class )
        .setKey("xmpp.client.csi.delay.max-duration")
        .setDefaultValue(Duration.ofMinutes(10))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(true)
        .build();

    /**
     * Determines the maximum length of the queue that holds delayed stanzas.
     */
    public static SystemProperty<Integer> DELAY_QUEUE_CAPACITY = SystemProperty.Builder.ofType( Integer.class )
        .setKey("xmpp.client.csi.delay.queue.capacity")
        .setDefaultValue(500)
        .setMinValue(0)
        .setDynamic(true)
        .build();

    public static final String NAMESPACE = "urn:xmpp:csi:0";

    /**
     * The client session for which this instance manages CSI state.
     */
    private final LocalClientSession session;

    /**
     * Client state of {@link #session}, either 'true' for 'active', or 'false' for 'inactive'
     */
    private boolean active;

    /**
     * The timestamp of the last push of data to the client (or the time of instantiation of this instance, if no data
     * has been pushed yet).
     */
    private Instant lastPush = Instant.now();

    /**
     * A queue that can hold 'unimportant' stanzas for the client if it is inactive.
     */
    private final Deque<Packet> queue = new LinkedList<>();

    public CsiManager(@Nonnull final LocalClientSession session)
    {
        this.session = session;
        this.active = true;
    }

    /**
     * Processes a CSI nonza.
     *
     * @param nonza The CSI nonza to be processed.
     */
    public synchronized void process(@Nonnull final Element nonza)
    {
        switch(nonza.getName()) {
            case "active":
                activate();
                break;
            case "inactive":
                deactivate();
                break;
            default:
                Log.warn("Unable to process element that was expected to be a CSI nonza for {}: {}", session, nonza);
        }
    }

    /**
     * Switch to the client state of 'active'.
     */
    public void activate()
    {
        Log.trace("Session for '{}' to CSI 'active'", session.getAddress());
        active = true;

        // If there are delayed stanzas, cause them to be delivered by rescheduling the last one.
        if (!queue.isEmpty()) {
            try {
                session.deliver(queue.pollLast());
            } catch (UnauthorizedException e) {
                Log.error("Unexpected exception while activating CSI.", e);
            }
        }
    }

    /**
     * Switch to the client state of 'inactive'.
     */
    public void deactivate()
    {
        Log.trace("Session for '{}' to CSI 'inactive'", session.getAddress());
        active = false;
    }

    /**
     * Returns the client state for the session that is being tracked by this instance, either 'true' for 'active',
     * or 'false' for 'inactive'
     *
     * @return a client state indication
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * Returns the number of stanzas that are currently in the delay queue.
     *
     * @return the number of delayed stanzas.
     */
    public synchronized int getDelayQueueSize() {
        return queue.size();
    }

    /**
     * Queues an unimportant stanza for later delivery, or returns the entire queue (including the argument) to be
     * sent to the client.
     *
     * @param packet the stanza to process.
     * @return stanzas to be delivered to the client (possibly empty).
     */
    public synchronized List<Packet> queueOrPush(@Nonnull final Packet packet)
    {
        queue.add(packet);

        final boolean mustPush =
               !DELAY_ENABLED.getValue() // The feature is disabled by configuration. Always send stanzas immediately.
            || active // The client is active! Do not delay.
            || queue.size() > DELAY_QUEUE_CAPACITY.getValue() // The delay queue has reached its capacity. Flush the entire thing.
            || Instant.now().isAfter(lastPush.plus(DELAY_MAX_DURATION.getValue())) // Ensure that periodically, delayed data is sent anyway.
            || !canDelay(packet);

        final List<Packet> result = new LinkedList<>();
        if (mustPush) {
            result.addAll(queue);
            queue.clear();
            lastPush = Instant.now();
            Log.trace("Cannot delay delivery of stanza. Push {} {}.", result.size(), result.size() == 1 ? "stanza" : "stanzas");
        } else {
            Log.trace("Delay delivery of stanza. Current queue size: {}", queue.size());
        }
        return result;
    }

    /**
     * Inspects a stanza and evaluates if it is eligible for delayed delivery to inactive clients.
     *
     * @param stanza the stanza to inspect
     * @return 'true' if the stanza delivery can be delayed.
     */
    static boolean canDelay(@Nonnull final Packet stanza)
    {
        if (stanza instanceof IQ) {
            return false;
        }

        if (stanza instanceof Presence) {
            final Presence presence = (Presence) stanza;
            if (presence.getType() == null || presence.getType() == Presence.Type.unavailable) {
                // Presence updates are generally unimportant, unless it is a MUC self-presence stanza, as that suggests
                // that the user joined or left a room.
                final Element muc = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                final boolean isSelfPresence = muc != null && muc.elements("status").stream().anyMatch(status -> "110".equals(status.attributeValue("code")));
                return !isSelfPresence;
            }
        }

        if (stanza instanceof Message)
        {
            final Message message = (Message) stanza;
            if (message.getBody() == null) {
                if (message.getType() == Message.Type.groupchat && !message.getElement().elements("subject").isEmpty()) {
                    // A subject (which can be empty) is sent to indicate that a room join has completed.
                    return false;
                }

                final Element muc = message.getChildElement("x", "http://jabber.org/protocol/muc#user");
                if (muc != null && !muc.elements("invite").isEmpty()) {
                    // Invitations to MUC rooms should be shown immediately.
                    return false;
                }

                if (!message.getElement().elements("encrypted").isEmpty()) {
                    // OMEMO messages never have a body element. We do not know what is being encrypted, but let's assume it's important to err on the side of caution.
                    return false;
                }

                if (message.getElement().elements().stream().anyMatch(element -> element.getNamespaceURI().startsWith("urn:xmpp:jingle-message:"))) {
                    // Typically, things that have to do with setting up an audio/video call. The user wants to see this as soon as possible, so do not delay. (OF-2750)
                    return false;
                }

                // No message body, and none of the exemptions above? It can probably wait.
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if an XML fragment is recognized as a CSI nonza
     *
     * @param fragment the XML to evaluate
     * @return true if the XML is recognized as a CSI nonza, otherwise false.
     */
    public static boolean isStreamManagementNonza(@Nullable final Element fragment) {
        return fragment != null
            && NAMESPACE.equals(fragment.getNamespaceURI())
            && Set.of("active", "inactive").contains(fragment.getName());
    }
}
