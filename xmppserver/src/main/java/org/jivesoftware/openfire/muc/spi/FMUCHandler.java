/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.muc.spi;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;

// TODO: monitor health of s2s connection, somehow?
public class FMUCHandler
{
    private static final Logger Log = LoggerFactory.getLogger( FMUCHandler.class );

    public static final SystemProperty<Boolean> FMUC_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.muc.room.fmuc.enabled")
        .setDynamic(true)
        .setDefaultValue(false)
        .addListener( isEnabled -> {
            XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices().forEach(
                service -> service.getChatRooms().forEach(
                    mucRoom -> mucRoom.getFmucHandler().applyConfigurationChanges()
                )
            );
        })
        .build();

    /**
     * Qualified name of the element that denotes FMUC functionality, as specified by XEP-0289.
     */
    public static final QName FMUC = QName.get("fmuc", "http://isode.com/protocol/fmuc");

    /**
     * The room for which this handler operates.
     */
    private final LocalMUCRoom room;

    /**
     * The router used to send packets for the room.
     */
    private final PacketRouter router;

    /**
     * Indicates if FMUC functionality has been started.
     */
    private boolean isStarted;

    /**
     * State that indicates if the local room has been configured to actively start joining a remote room.
     */
    private OutboundJoinConfiguration outboundJoinConfiguration;

    /**
     * Tracks state while the outbound join is being set up.
     */
    private OutboundJoinProgress outboundJoinProgress;

    /**
     * The address of the remote rooms that the local room has connected to (in which the remote room has taken the
     * role of 'joined FMUC node' while the local room has taken the role of the 'joining FMUC node').
     */
    private OutboundJoin outboundJoin;

    /**
     * The addresses of the remote rooms that have connected to the local room (in which the remote rooms have taken the
     * role of 'joining FMUC nodes' while the local room has taken the role of the 'joined FMUC node').
     */
    private Map<JID, InboundJoin> inboundJoins = new HashMap<>();

    public FMUCHandler( @Nonnull LocalMUCRoom chatroom, @Nonnull PacketRouter packetRouter) {
        this.room = chatroom;
        this.router = packetRouter;
    }

    /**
     * Starts federation, which will cause a federation attempt with the outbound ('joined') node, if one is configured,
     * and there currently are occupants in the room.
     */
    public synchronized void startOutbound()
    {
        Log.debug( "(room: '{}'): FMUC outbound federation is being started...", room.getJID() );

        final Collection<MUCRole> occupants = room.getOccupants();
        if ( occupants.isEmpty() ) {
            Log.trace("(room: '{}'): No occupants in the room. No need to initiate an FMUC join.", room.getJID());
        } else {
            Log.trace("(room: '{}'): {} occupant(s) in the room. Initiating an FMUC join for each of them.", room.getJID(), occupants.size());
            for ( final MUCRole occupant : occupants ) {
                try {
                    Log.trace("(room: '{}'): Making occupant '{}' join the FMUC room.", room.getJID(), occupant.getUserAddress());
                    join( occupant, false, true );
                } catch ( Exception e ) {
                    Log.trace("(room: '{}'): An exception occurred while making occupant '{}' join the FMUC room.", room.getJID(), occupant.getUserAddress(), e);
                }
            }
        }
        Log.debug( "(room: '{}'): Finished starting FMUC outbound federation.", room.getJID() );
    }

    /**
     * Stops federation, which will cause any joined and joining nodes to be disconnected.
     */
    public synchronized void stop()
    {
        Log.debug( "(room: '{}'): FMUC federation is being stopped...", room.getJID() );

        // Keep track of all occupants that are becoming unavailable due to them having joined on remote nodes.
        final Set<JID> removedRemoteOccupants = new HashSet<>();

        final Set<JID> removedOccupantsInbound = doStopInbound();
        final Set<JID> removedOccupantsOutbound = doStopOutbound();
        removedRemoteOccupants.addAll( removedOccupantsInbound );
        removedRemoteOccupants.addAll( removedOccupantsOutbound );

        Log.trace( "(room: '{}'): Done disconnecting inbound and outbound nodes from the node set. Now removing all their ({}) occupants from the room.", room.getJID(), removedRemoteOccupants.size() );
        makeRemoteOccupantLeaveRoom( removedRemoteOccupants );

        isStarted = false;
        Log.debug( "(room: '{}'): Finished stopping FMUC federation.", room.getJID() );
    }

    /**
     * Stops inbound federation, which will cause existing federation with all of the inbound ('joining') nodes, if any
     * are established, to be teared down.
     */
    public synchronized void stopInbound() {
        final Set<JID> removedOccupants = doStopInbound();

        Log.trace( "(room: '{}'): Removing all ({}) occupants from the room for remote inbound node(s) that we just disconnected from.", room.getJID(), removedOccupants.size() );
        makeRemoteOccupantLeaveRoom( removedOccupants );

        Log.debug( "(room: '{}'): Finished stopping inbound FMUC federation.", room.getJID() );
    }

    /**
     * Stops inbound federation, which will cause existing federation with one specific inbound ('joining') nodes to be
     * teared down.
     *
     * @param peer the address of the remote node (must be a bare JID).
     */
    public synchronized void stopInbound( @Nonnull JID peer )
    {
        if ( !peer.asBareJID().equals(peer) ) {
            throw new IllegalArgumentException( "Expected argument 'peer' to be a bare JID, but it was not: " + peer );
        }

        final Set<JID> removedOccupants = doStopInbound( peer );

        Log.trace( "(room: '{}'): Removing all ({}) occupants from the room for remote inbound node '{}' that we just disconnected from.", room.getJID(), removedOccupants.size(), peer );
        makeRemoteOccupantLeaveRoom( removedOccupants );

        Log.debug( "(room: '{}'): Finished stopping inbound FMUC federation.", room.getJID() );
    }

    /**
     * The workhorse implementation of stopping inbound federation, that returns a list of JIDs representing the
     * occupants from the remote, joining nodes that are no longer in the room as a result of stopping the federation. For
     * these occupants, a presence stanza should be shared with the remainder of the occupants. It is desirable to send
     * these stanzas only after all any other nodes that are to be disconnected have been disconnected (to prevent
     * sharing updates with other remote nodes that we might also be disconnecting from. This method does
     * therefor not send these stanzas. Instead, it returns the addresses that should be send stanzas. This allows
     * callers to aggregate addresses, and send the the stanzas in one iteration.
     *
     * The implementation in this method will inform the remote, joining node that they left the local, joined node by
     * sending it a 'left' message.
     *
     * @return The JIDs of occupants on the remote, joining node that are no longer in the room.
     */
    private synchronized Set<JID> doStopInbound() {
        return doStopInbound( null );
    }

    /**
     * Identical to {@link #doStopInbound()} but used to disconnect just one node, instead of all of them.
     *
     * When null is passed as an argument, all inbound nodes are disconnected (equivalent to a call to {@link #doStopInbound()}).
     *
     * @param peer the address of the remote node (must be a bare JID), or null to remove all nodes.
     * @return The JIDs of occupants on the remote, joining node that are no longer in the room.
     * @see #doStopInbound()
     */
    private synchronized Set<JID> doStopInbound( @Nullable JID peer ) {
        if ( peer != null && !peer.asBareJID().equals(peer) ) {
            throw new IllegalArgumentException( "Expected argument 'peer' to be null or a bare JID, but it was not: " + peer );
        }

        final Set<JID> result = new HashSet<>();
        if ( inboundJoins.isEmpty() ) {
            Log.trace( "(room: '{}'): No remote MUC joining us. No need to inform joining nodes that they have now left.", room.getJID() );
        } else {
            final Iterator<Map.Entry<JID, InboundJoin>> iterator = inboundJoins.entrySet().iterator();
            while ( iterator.hasNext() )
            {
                final InboundJoin inboundJoin = iterator.next().getValue();
                if ( peer != null && !inboundJoin.getPeer().equals( peer )) {
                    // This is not the peer you're looking for.
                    continue;
                }
                iterator.remove(); // Remove inboundJoin so that it's no longer send stanzas, and incoming stanzas are being treated as if from an unconnected entity.

                result.addAll( inboundJoin.occupants );

                try
                {
                    Log.trace("(room: '{}'): Informing joining node '{}' that it is leaving the FMUC node set.", room.getJID(), inboundJoin.getPeer());
                    final Presence left = new Presence();
                    left.setFrom(room.getJID());
                    left.setTo(inboundJoin.getPeer());
                    left.getElement().addElement(FMUC).addElement("left");
                    router.route(left);
                }
                catch ( Exception e )
                {
                    Log.warn("(room: '{}'): An exception occurred while informing joining node '{}' that it is leaving the FMUC node set.", room.getJID(), inboundJoin.getPeer(), e);
                }
            }
        }
        return result;
    }

    /**
     * Stops outbound federation, which will cause existing federation with the outbound ('joined') node, if one is
     * established, to be teared down.
     */
    public synchronized void stopOutbound()
    {
        final Set<JID> removedOccupants = doStopOutbound();

        Log.trace("(room: '{}'): Removing all ({}) occupants from the room for remote outbound node that we just disconnected from.", room.getJID(), removedOccupants.size());
        makeRemoteOccupantLeaveRoom( removedOccupants );

        Log.debug( "(room: '{}'): Finished stopping outbound FMUC federation.", room.getJID() );
    }

    /**
     * The workhorse implementation of stopping outbound federation, that returns a list of JIDs representing the
     * occupants from the remote, joined node that are no longer in the room as a result of stopping the federation. For
     * these occupants, a presence stanza should be shared with the remainder of the occupants. It is desirable to send
     * these stanzas only after all any other nodes that are to be disconnected have been disconnected (to prevent
     * sharing updates with other remote nodes that we might also be disconnecting from. This method does
     * therefor not send these stanzas. Instead, it returns the addresses that should be send stanzas. This allows
     * callers to aggregate addresses, and send the the stanzas in one iteration.
     *
     * The implementation in this method will inform the remote, joined node that the local, joining node has left, by
     * sending it presence stanzas for all occupants that the local, joining node has contributed to the FMUC set. After
     * the remote, joined, node has received the last stanza, it should conclude that the local, joining node has left
     * (it should respond with a 'left' message, although this implementation does not depend on that).
     *
     * @return The JIDs of occupants on the remote, joined node that are no longer in the room.
     */
    private synchronized Set<JID> doStopOutbound()
    {
        Log.debug("(room: '{}'): Stopping federation with remote node that we joined (if any).", room.getJID());
        final Set<JID> result = new HashSet<>();

        if ( outboundJoinProgress == null ) {
            Log.trace("(room: '{}'): We are not in progress of joining a remote node. No need to abort such an effort.", room.getJID());
        } else {
            Log.trace("(room: '{}'): Aborting the ongoing effort of joining remote node '{}'.", room.getJID(), outboundJoinProgress.getPeer());
            abortOutboundJoinProgress();
        }

        if ( outboundJoin == null) {
            Log.trace("(room: '{}'): We did not join a remote node. No need to inform one that we have left.", room.getJID());
        } else {
            Log.trace("(room: '{}'): Informing joined node '{}' that we are leaving the FMUC node set.", room.getJID(), outboundJoin.getPeer());

            // Remove outboundJoin so that it's no longer send stanzas, and incoming stanzas are being treated as if from an unconnected entity.
            // We'll need to store some state to be able to process things later though.
            final JID peer = outboundJoin.getPeer();
            final Set<PendingCallback> pendingEcho = outboundJoin.pendingEcho;
            final Set<JID> theirOccupants = outboundJoin.occupants;

            outboundJoin = null;

            result.addAll( theirOccupants );

            // If we're waiting for an echo of a stanza that we've sent to this MUC, that now will no longer arrive. Make sure that we unblock all threads waiting for such an echo.
            if ( !pendingEcho.isEmpty() )
            {
                Log.trace("(room: '{}'): Completing {} callbacks that were waiting for an echo from peer '{}' that is being disconnected from.", room.getJID(), pendingEcho.size(), peer );
                for ( final PendingCallback pendingCallback : pendingEcho )
                {
                    try {
                        pendingCallback.complete(); // TODO maybe completeExceptionally?
                    } catch ( Exception e ) {
                        Log.warn("(room: '{}'): An exception occurred while completing callback pending echo from peer '{}' (that we're disconnecting from).", room.getJID(), peer, e);
                    }
                }
            }

            // Find all the occupants that the local node contributed to the FMUC set (those are the occupants that are
            // not joined through the remote, joined node). Note that these can include occupants that are on other nodes!
            final Set<MUCRole> occupantsToLeave = new HashSet<>( room.getOccupants() );
            occupantsToLeave.removeIf( mucRole -> mucRole.getReportedFmucAddress() != null && theirOccupants.contains( mucRole.getReportedFmucAddress() ));
            Log.trace("(room: '{}'): Identified {} occupants that the local node contributed to the FMUC set.", room.getJID(), occupantsToLeave.size());

            // Inform the remote, joined node that these are now all gone.
            for ( final MUCRole occupantToLeave : occupantsToLeave ) {
                try
                {
                    Log.trace("(room: '{}'): Informing joined node '{}' that occupant '{}' left the MUC.", room.getJID(), peer, occupantToLeave.getUserAddress());

                    final Presence leave = occupantToLeave.getPresence().createCopy();
                    leave.setType(Presence.Type.unavailable);
                    leave.setTo(new JID(peer.getNode(), peer.getDomain(), occupantToLeave.getNickname()));
                    leave.setFrom(occupantToLeave.getRoleAddress());

                    // Change (or add) presence information about roles and affiliations
                    Element childElement = leave.getChildElement("x", "http://jabber.org/protocol/muc#user");
                    if ( childElement == null )
                    {
                        childElement = leave.addChildElement("x", "http://jabber.org/protocol/muc#user");
                    }
                    Element item = childElement.element("item");
                    if ( item == null )
                    {
                        item = childElement.addElement("item");
                    }
                    item.addAttribute("role", "none");

                    final Presence enriched = enrichWithFMUCElement(leave, occupantToLeave.getReportedFmucAddress() != null ? occupantToLeave.getReportedFmucAddress() : occupantToLeave.getUserAddress());

                    router.route(enriched);
                } catch ( Exception e ) {
                    Log.warn("(room: '{}'): An exception occurred while informing joined node '{}' that occupant '{}' left the MUC.", room.getJID(), peer, occupantToLeave.getUserAddress(), e);
                }
            }
        }

        Log.trace("(room: '{}'): Finished stopping federation with remote node that we joined (if any).", room.getJID());
        return result;
    }

    /**
     * Reads configuration from the room instance that is being services by this handler, and applies relevant changes.
     */
    public synchronized void applyConfigurationChanges()
    {
        if ( isStarted != (room.isFmucEnabled() && FMUC_ENABLED.getValue()) ) {
            Log.debug( "(room: '{}'): Changing availability of FMUC functionality to {}.", room.getJID(), (room.isFmucEnabled() && FMUC_ENABLED.getValue()) );
            if ((room.isFmucEnabled() && FMUC_ENABLED.getValue())) {
                startOutbound();
            } else {
                stop();
                return;
            }
        }

        final OutboundJoinConfiguration desiredConfig;
        if ( room.getFmucOutboundNode() != null ) {
            desiredConfig = new OutboundJoinConfiguration( room.getFmucOutboundNode(), room.getFmucOutboundMode() );
        } else {
            desiredConfig = null;
        }
        Log.debug("(room: '{}'): Changing outbound join configuration. Existing: {}, New: {}", room.getJID(), this.outboundJoinConfiguration, desiredConfig );

        if ( this.outboundJoinProgress != null ) {
            if (desiredConfig == null) {
                Log.trace( "(room: '{}'): Had, but now no longer has, outbound join configuration. Aborting ongoing federation attempt...", room.getJID() );
                abortOutboundJoinProgress();
            } else if ( this.outboundJoinProgress.getPeer().equals( desiredConfig.getPeer() ) ) {
                Log.trace( "(room: '{}'): New configuration matches peer that ongoing federation attempt is made with. Allowing attempt to continue.", room.getJID() );
            } else {
                Log.trace( "(room: '{}'): New configuration targets a different peer that ongoing federation attempt is made with. Aborting attempt.", room.getJID() );
                abortOutboundJoinProgress();
            }
        }

        if ( this.outboundJoinConfiguration == null && desiredConfig != null ) {
            Log.trace( "(room: '{}'): Did not, but now has, outbound join configuration. Starting federation...", room.getJID() );
            this.outboundJoinConfiguration = desiredConfig;
            startOutbound();
        } else if ( this.outboundJoinConfiguration != null && desiredConfig == null ) {
            Log.trace( "(room: '{}'): Had, but now no longer has, outbound join configuration. Stopping federation...", room.getJID() );
            this.outboundJoinConfiguration = desiredConfig;
            stopOutbound();
        } else if ( this.outboundJoinConfiguration != null && desiredConfig != null ) {
            if ( outboundJoin == null ) {
                if ( this.outboundJoinConfiguration.equals(desiredConfig ) ) {
                    // no change
                } else {
                    Log.trace( "(room: '{}'): Applying new configuration.", room.getJID() );
                    this.outboundJoinConfiguration = desiredConfig;
                    startOutbound();
                }
            } else {
                if ( outboundJoin.getConfiguration().equals( desiredConfig ) ) {
                    Log.trace( "(room: '{}'): New configuration matches configuration of established federation. Not applying any change.", room.getJID() );
                } else {
                    Log.trace( "(room: '{}'): Already had outbound join configuration, now got a different config. Restarting federation...", room.getJID() );
                    stopOutbound();
                    this.outboundJoinConfiguration = desiredConfig;
                    startOutbound();
                }
            }
        }
        isStarted = true;
    }

    /**
     * Propagates a stanza to the FMUC set, if FMUC is active for this room.
     *
     * Note that when a master-slave mode is active, we need to wait for an echo back, before the message can be
     * broadcasted locally. This method will return a CompletableFuture object that is completed as soon as processing
     * can continue. This doesn't necessarily mean that processing/propagating has been completed (eg: when the FMUC
     * is configured to use master-master mode, a completed Future instance will be returned.
     *
     * When FMUC is not active, this method will return a completed Future instance.
     *
     * @param stanza the stanza to be propagated through FMUC.
     * @param sender the role of the sender that is the original author of the stanza.
     * @return A future object that completes when the stanza can be propagated locally.
     */
    public synchronized CompletableFuture<?> propagate( @Nonnull Packet stanza, @Nonnull MUCRole sender )
    {
        if ( !(room.isFmucEnabled() && FMUC_ENABLED.getValue()) ) {
            Log.debug( "(room: '{}'): FMUC disabled, skipping FMUC propagation.", room.getJID() );
            return CompletableFuture.completedFuture(null);
        }

        Log.debug( "(room: '{}'): A stanza (type: {}, from: {}) is to be propagated in the FMUC node set.", room.getJID(), stanza.getClass().getSimpleName(), stanza.getFrom() );

        /* TODO this implementation currently is blocking, and synchronous: inbound propagation only occurs after outbound
                propagation has been started. Is this needed? What should happen if outbound propagation fails? When the
                mode used is master/slave, then it is reasonable to assume that a failed outbound propagation makes the
                message propagation fail completely (and thus should not be propagated to the inbound nodes or locally?
        */
        // propagate to joined FMUC node (conditionally blocks, depending on FMUC mode that's in effect).
        final CompletableFuture<?> propagateToOutbound = propagateOutbound( stanza, sender );

        // propagate to all joining FMUC nodes (need never block).
        final CompletableFuture<?> propagateToInbound = propagateInbound( stanza, sender );

        // Return a Future that completes when all of the Futures constructed above complete.
        return CompletableFuture.allOf( propagateToOutbound, propagateToInbound );
    }

    /**
     * Makes a user in our XMPP domain join the FMUC room.
     *
     * The join event is propagated to all nodes in the FMUC set that the room is part of.
     *
     * When 'outbound' federation is desired, but has not yet been established (when this is the first user to join the
     * room), federation is initiated.
     *
     * Depending on the configuration and state of the FMUC set, the join operation is considered 'blocking'. In case of
     * a FMUC based on master-slave mode, for example, the operation cannot continue until the remote FMUC node has
     * echo'd back the join. To accommodate this behavior, this method will return a Future instance. The Future will
     * immediately be marked as 'done' when the operation need not have 'blocking' behavior. Note that in such cases,
     * the Future being 'done' does explicitly <em>not</em> indicate that the remote FMUC node has received, processed
     * and/or accepted the event.
     *
     * If the local room is not configured to federate with another room, an invocation of this method will do nothing.
     *
     * @param mucRole The role in which the user is joining the room.
     * @return A future object that completes when the stanza can be propagated locally.
     */
    public synchronized Future<?> join( @Nonnull MUCRole mucRole )
    {
        return join( mucRole, true, true );
    }

    protected synchronized Future<?> join( @Nonnull MUCRole mucRole, final boolean includeInbound, final boolean includeOutbound )
    {
        if ( !(room.isFmucEnabled() && FMUC_ENABLED.getValue()) ) {
            Log.debug( "(room: '{}'): FMUC disabled, skipping FMUC join.", room.getJID() );
            return CompletableFuture.completedFuture(null);
        }

        Log.debug( "(room: '{}'): user '{}' (as '{}') attempts to join.", room.getJID(), mucRole.getUserAddress(), mucRole.getRoleAddress() );

        final CompletableFuture<?> propagateToOutbound;
        if ( !includeOutbound ) {
            Log.trace( "(room: '{}'): skip propagating to outbound, as instructed.", room.getJID() );
            propagateToOutbound = CompletableFuture.completedFuture(null);
        } else {
            // Do we need to initiate a new outbound federation (are we configured to have an outbound federation, but has one not been started yet?
            if ( outboundJoinConfiguration != null) {
                if ( outboundJoin == null ) {
                    if ( outboundJoinProgress == null ) {
                        Log.trace("(room: '{}'): FMUC configuration contains configuration for a remote MUC that needs to be joined: {}", room.getJID(), outboundJoinConfiguration.getPeer() );
                        // When a new federation is established, there's no need to explicitly propagate the join too - that's implicitly done as part of the initialization of the new federation.
                        propagateToOutbound = initiateFederationOutbound( mucRole );
                    } else {
                        Log.debug("(room: '{}'): Received a FMUC 'join' request for a remote MUC that we're already in process of joining: {}", room.getJID(), outboundJoinConfiguration.getPeer() );
                        return outboundJoinProgress.addToQueue( generateJoinStanza( mucRole ), mucRole ); // queue a new join stanza to be sent after the ongoing join completes.
                    }
                }
                else
                {
                    // TODO Doesn't this imply some kind of problem - why would we be joining a MUC that we've already joined?
                    Log.warn("(room: '{}'): FMUC configuration contains configuration for a remote MUC: {}. Federation with this MUC has already been established.", room.getJID(), outboundJoin.getPeer() );
                    // propagate to existing the existing joined FMUC node (be blocking if master/slave mode!)
                    propagateToOutbound = propagateOutbound( generateJoinStanza( mucRole ), mucRole );
                }
            } else {
                // Nothing to do!
                Log.trace( "(room: '{}'): FMUC configuration does not contain a remote MUC that needs to be joined.", room.getJID() );
                propagateToOutbound = CompletableFuture.completedFuture(null);
            }
        }

        /* TODO this implementation currently is blocking, and synchronous: inbound propagation only occurs after outbound
                propagation has been started. Is this needed? What should happen if outbound propagation fails? When the
                mode used is master/slave, then it is reasonable to assume that a failed outbound join makes the join fail
                (and thus should not be propagated to the inbound nodes. */

        // propagate to all joining FMUC nodes (need never block).
        final CompletableFuture<?> propagateToInbound;
        if ( !includeInbound ) {
            Log.trace( "(room: '{}'): skip propagating to inbound, as instructed.", room.getJID() );
            propagateToInbound = CompletableFuture.completedFuture(null);
        } else {
            propagateToInbound = propagateInbound( generateJoinStanza( mucRole ), mucRole );
        }

        // Return a Future that completes when all of the Futures constructed above complete.
        return CompletableFuture.allOf( propagateToOutbound, propagateToInbound );
    }

    /**
     * Attempt to establish a federation with a remote MUC room. In this relation 'our' room will take the role of
     * 'joining FMUC node'.
     *
     * @param mucRole Occupant that joined the room, triggering the federation to be initiated.
     * @return A future object that completes when the join can be propagated locally.
     */
    private CompletableFuture<?> initiateFederationOutbound( @Nonnull MUCRole mucRole )
    {
        Log.debug("(room: '{}'): Attempting to establish federation by joining '{}', triggered by user '{}' (as '{}').", room.getJID(), outboundJoinConfiguration.getPeer(), mucRole.getUserAddress(), mucRole.getRoleAddress() );

        final Presence joinStanza = enrichWithFMUCElement( generateJoinStanza( mucRole ), mucRole );
        joinStanza.setFrom( new JID(room.getName(), room.getMUCService().getServiceDomain(), mucRole.getNickname() ) );
        joinStanza.setTo( new JID(outboundJoinConfiguration.getPeer().getNode(), outboundJoinConfiguration.getPeer().getDomain(), mucRole.getNickname() ) );

        Log.trace("(room: '{}'): Registering a callback to be used when the federation request to '{}' has completed.", room.getJID(), outboundJoinConfiguration.getPeer() );
        final CompletableFuture<List<Packet>> result = new CompletableFuture<>();
        final boolean mustBlock = outboundJoinConfiguration.getMode() == FMUCMode.MasterSlave;
        if ( !mustBlock ) {
            Log.trace("(room: '{}'): No need to wait for federation to complete before allowing the local user to join the room.", room.getJID() );
            result.complete( null );
        } else {
            Log.trace("(room: '{}'): Federation needs to have been completed before allowing the local user to join the room.", room.getJID() );
        }

        outboundJoinProgress = new OutboundJoinProgress(outboundJoinConfiguration.getPeer(), result );

        Log.trace( "(room: '{}'): Sending FMUC join request: {}", room.getJID(), joinStanza.toXML() );
        router.route(joinStanza);

        return result;
    }

    /**
     * Sends a stanza to the joined FMUC node, when the local node has established such an outbound join.
     *
     * Note that when a master-slave mode is active, we need to wait for an echo back, before the message can be
     * broadcasted locally. This method will return a CompletableFuture object that is completed as soon as processing
     * can continue. This doesn't necessarily mean that processing/propagating has been completed (eg: when the FMUC
     * is configured to use master-master mode, a completed Future instance will be returned.
     *
     * @param stanza The stanza to be sent.
     * @param sender Representation of the sender of the stanza.
     * @return A future object that completes when the stanza can be propagated locally.
     */
    private CompletableFuture<?> propagateOutbound( @Nonnull Packet stanza, @Nonnull MUCRole sender )
    {
        Log.trace("(room: '{}'): Propagate outbound, stanza: {}, sender: {}", room.getJID(), stanza, sender);

        if ( outboundJoin == null )
        {
            if ( outboundJoinProgress != null ) {
                Log.trace("(room: '{}'): Remote MUC joining in progress. Queuing outbound propagation until after the join has been established.", room.getJID());
                return outboundJoinProgress.addToQueue( stanza, sender );
            }
            else
            {
                Log.trace("(room: '{}'): No remote MUC joined. No need to propagate outbound.", room.getJID());
                return CompletableFuture.completedFuture(null);
            }
        }

        if ( !outboundJoin.wantsStanzasSentBy( sender ) ) {
            Log.trace("(room: '{}'): Skipping outbound propagation to peer '{}', as this peer needs not be sent stanzas sent by '{}' (potentially because it's a master-master mode joined FMUC and the sender originates on that node).", room.getJID(), outboundJoin.getPeer(), sender );
            return CompletableFuture.completedFuture(null);
        }

        final CompletableFuture<?> result = new CompletableFuture<>();
        doPropagateOutbound( stanza, sender, result );

        return result;
    }

    private void doPropagateOutbound( @Nonnull Packet stanza, @Nonnull MUCRole sender, @Nonnull CompletableFuture<?> result )
    {
        Log.debug("(room: '{}'): Propagating a stanza (type '{}') from user '{}' (as '{}') to the joined FMUC node {}.", room.getJID(), stanza.getClass().getSimpleName(), sender.getUserAddress(), sender.getRoleAddress(), outboundJoin.getPeer() );

        final Packet enriched = enrichWithFMUCElement( stanza, sender );
        enriched.setFrom( new JID(room.getName(), room.getMUCService().getServiceDomain(), sender.getNickname() ) );
        enriched.setTo( new JID(outboundJoin.getPeer().getNode(), outboundJoin.getPeer().getDomain(), sender.getNickname() ) );

        // When we're in a master-slave mode with the remote FMUC node that we're joining to, we must wait for it
        // to echo back the presence data, before we can distribute it in the local room.
        final boolean mustBlock = outboundJoin.getMode() == FMUCMode.MasterSlave;
        if ( !mustBlock ) {
            Log.trace("(room: '{}'): No need to wait for an echo back from joined FMUC node {} of the propagation of stanza sent by user '{}' (as '{}').", room.getJID(), outboundJoin.getPeer(), sender.getUserAddress(), sender.getRoleAddress() );
            result.complete( null );
        } else {
            Log.debug("(room: '{}'): An echo back from joined FMUC node {} of the propagation of stanza snet by user '{}' (as '{}') needs to be received before the join event can be propagated locally.", room.getJID(), outboundJoin.getPeer(), sender.getUserAddress(), sender.getRoleAddress() );

            // register callback to complete this future when echo is received back.
            outboundJoin.registerEchoCallback( enriched, result );
        }

        // Send the outbound stanza.
        router.route( enriched );
    }

    /**
     * Sends a stanza to all joined FMUC node, when the local node has accepted such inbound joins from remote peers.
     *
     * Optionally, the address if one particular peer can be provided to avoid propagation of the stanza to that
     * particular node. This is to be used to prevent 'echos back' to the originating FMUC node, when appropriate.
     *
     * @param stanza The stanza to be sent.
     * @param sender Representation of the sender of the stanza.
     * @return A future object that completes when the stanza can be propagated locally.
     */
    private CompletableFuture<?> propagateInbound( @Nonnull Packet stanza, @Nonnull MUCRole sender )
    {
        Log.trace("(room: '{}'): Propagate inbound, stanza: {}, sender: {}", room.getJID(), stanza, sender);
        if ( inboundJoins.isEmpty() )
        {
            Log.trace("(room: '{}'): No remote MUC joining us. No need to propagate inbound.", room.getJID());
            return CompletableFuture.completedFuture(null);
        }

        Log.trace( "(room: '{}'): Propagating a stanza (type '{}') from user '{}' (as '{}') to the all {} joining FMUC nodes.", room.getJID(), stanza.getClass().getSimpleName(), sender.getUserAddress(), sender.getRoleAddress(), inboundJoins.size() );

        for( final InboundJoin inboundJoin : inboundJoins.values() )
        {
            if ( !inboundJoin.wantsStanzasSentBy( sender ) ) {
                Log.trace("(room: '{}'): Skipping inbound propagation to peer '{}', as this peer needs not be sent stanzas sent by '{}' (potentially because it's a master-slave mode joined FMUC and the sender originates on that node).", room.getJID(), inboundJoin.getPeer(), sender );
                continue;
            }

            Log.trace( "(room: '{}'): Propagating a stanza (type '{}') from user '{}' (as '{}') to the joining FMUC node '{}'", room.getJID(), stanza.getClass().getSimpleName(), sender.getUserAddress(), sender.getRoleAddress(), inboundJoin.getPeer() );
            final Packet enriched = enrichWithFMUCElement( stanza, sender );
            enriched.setFrom( sender.getRoleAddress());
            enriched.setTo( inboundJoin.getPeer() );
            router.route( enriched );
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Adds an FMUC child element to the stanza, if such an element does not yet exist.
     *
     * This method provides the functionally opposite implementation of {@link #createCopyWithoutFMUC(Packet)}.
     *
     * @param stanza The stanza to which an FMUC child element is to be added.
     * @param sender Representation of the originator of the stanza.
     * @param <S> Type of stanza
     * @return A copy of the stanza, with an added FMUC child element.
     */
    private static <S extends Packet> S enrichWithFMUCElement( @Nonnull S stanza, @Nonnull MUCRole sender )
    {
        // Defensive copy - ensure that the original stanza (that might be routed locally) is not modified).
        final S result = (S) stanza.createCopy();

        final Element fmuc = result.getElement().element(FMUC);
        if ( fmuc != null ) {
            return result;
        }

        final JID from;
        if ( sender instanceof LocalMUCRoom.RoomRole ) {
            // This role represents the room itself as the sender. Rooms do not have a 'user' address.
            from = sender.getRoleAddress();
        } else {
            from = sender.getUserAddress();
        }
        result.getElement().addElement( FMUC ).addAttribute( "from", from.toString() );

        return result;
    }

    /**
     * Adds an FMUC child element to the stanza, if such an element does not yet exist.
     *
     * This method provides the functionally opposite implementation of {@link #createCopyWithoutFMUC(Packet)}.
     *
     * @param stanza The stanza to which an FMUC child element is to be added.
     * @param sender Representation of the originator of the stanza.
     * @param <S> Type of stanza
     * @return A copy of the stanza, with an added FMUC child element.
     */
    private static <S extends Packet> S enrichWithFMUCElement( @Nonnull S stanza, @Nonnull JID sender )
    {
        // Defensive copy - ensure that the original stanza (that might be routed locally) is not modified).
        final S result = (S) stanza.createCopy();

        final Element fmuc = result.getElement().element(FMUC);
        if ( fmuc != null ) {
            return result;
        }

        result.getElement().addElement( FMUC ).addAttribute( "from", sender.toString() );

        return result;
    }

    /**
     * Removes FMUC child elements from the stanza, if such an element exists.
     *
     * This method provides the functionally opposite implementation of {@link #enrichWithFMUCElement(Packet, MUCRole)}.
     *
     * @param stanza The stanza from which an FMUC child element is to be removed.
     * @param <S> Type of stanza
     * @return A copy of the stanza, without FMUC child element.
     */
    public static <S extends Packet> S createCopyWithoutFMUC( S stanza )
    {
        final S result = (S) stanza.createCopy();
        final Iterator<Element> elementIterator = result.getElement().elementIterator(FMUC);
        while (elementIterator.hasNext()) {
            elementIterator.next();
            elementIterator.remove();
        }
        return result;
    }

    /**
     * Creates a stanza that represents a room 'join' in a MUC room.
     *
     * @param mucRole Representation of the (local) user that caused the join to be initiated.
     */
    // TODO this does not have any FMUC specifics. Must this exist in this class?
    private Presence generateJoinStanza( @Nonnull MUCRole mucRole )
    {
        Log.debug( "(room: '{}'): Generating a stanza that represents the joining of local user '{}' (as '{}').", room.getJID(), mucRole.getUserAddress(), mucRole.getRoleAddress() );
        final Presence joinStanza = new Presence();
        joinStanza.getElement().addElement(QName.get("x", "http://jabber.org/protocol/muc"));
        final Element mucUser = joinStanza.getElement().addElement(QName.get("x", "http://jabber.org/protocol/muc#user"));
        final Element mucUserItem = mucUser.addElement("item");
        mucUserItem.addAttribute("affiliation", mucRole.getAffiliation().toString());
        mucUserItem.addAttribute("role", mucRole.getRole().toString());

        // Don't include the occupant's JID if the room is semi-anon and the new occupant is not a moderator
        if (!room.canAnyoneDiscoverJID()) {
            if (MUCRole.Role.moderator == mucRole.getRole()) {
                mucUserItem.addAttribute("jid", mucRole.getUserAddress().toString());
            }
            else {
                mucUserItem.addAttribute("jid", null);
            }
        }

        return joinStanza;
    }

    public synchronized void process( @Nonnull final Packet stanza )
    {
        if ( !(room.isFmucEnabled() && FMUC_ENABLED.getValue()) ) {
            Log.debug( "(room: '{}'): FMUC disabled, skipping processing of stanza: {}", room.getJID(), stanza.toXML() );
            if ( stanza instanceof IQ && ((IQ) stanza).isRequest() ) {
                final IQ errorResult = IQ.createResultIQ( (IQ) stanza);
                errorResult.setError(PacketError.Condition.service_unavailable);
                router.route( errorResult );
            }
            return;
        }

        Log.trace( "(room: '{}'): Processing stanza from '{}': {}", room.getJID(), stanza.getFrom(), stanza.toXML() );
        final JID remoteMUC = stanza.getFrom().asBareJID();

        if ( stanza.getElement().element(FMUC) == null ) {
            throw new IllegalArgumentException( "Unable to process stanza that does not have FMUC data: " + stanza.toXML() );
        }

        if ( remoteMUC.getNode() == null ) {
            throw new IllegalArgumentException( "Unable to process stanza that did not originate from a MUC room (the 'from' address has no node-part):" + stanza.toXML() );
        }

        if ( outboundJoinProgress != null && outboundJoinProgress.getPeer().equals( remoteMUC ) && !outboundJoinProgress.isJoinComplete() )
        {
            Log.trace("(room: '{}'): Received stanza from '{}' that is identified as outbound FMUC node for which a join is in progress.", room.getJID(), remoteMUC);

            Log.trace("(room: '{}'): Queueing stanza from '{}' as partial FMUC join response.", room.getJID(), remoteMUC);
            outboundJoinProgress.addResponse(stanza);

            if ( outboundJoinProgress.isJoinComplete() )
            {
                Log.debug("(room: '{}'): Received a complete FMUC join response from '{}'.", room.getJID(), remoteMUC);
                finishOutboundJoin();
                outboundJoinProgress = null;
            }
        }
        else if ( outboundJoin != null && outboundJoin.getPeer().equals( remoteMUC ) )
        {
            Log.trace("(room: '{}'): Received stanza from '{}' that is identified as outbound FMUC node.", room.getJID(), remoteMUC);
            if ( stanza instanceof Presence && stanza.getElement().element(FMUC).element("left") != null ) {
                processLeftInstruction( (Presence) stanza );
            } else {
                outboundJoin.evaluateForCallbackCompletion(stanza);
                processRegularMUCStanza( stanza );
            }
        }
        else if ( inboundJoins.get( remoteMUC.asBareJID() ) != null )
        {
            Log.trace("(room: '{}'): Received stanza from '{}' that is identified as inbound FMUC node.", room.getJID(), remoteMUC);
            processRegularMUCStanza( stanza );
        }
        else
        {
            Log.trace("(room: '{}'): Received stanza from '{}' that is not a known FMUC node.", room.getJID(), remoteMUC ); //Treating as inbound FMUC node join request.", room.getJID(), remoteMUC);
            if ( isFMUCJoinRequest( stanza ) ) {
                try
                {
                    checkAcceptingFMUCJoiningNodePreconditions(remoteMUC);
                    acceptJoiningFMUCNode( (Presence) stanza );
                } catch ( final FMUCException e ) {
                    rejectJoiningFMUCNode( (Presence) stanza, e.getMessage() );
                }
            } else {
                Log.debug("(room: '{}'): Unable to process stanza from '{}'. Ignoring: {}", room.getJID(), remoteMUC, stanza.toXML() );
                if ( stanza instanceof IQ && ((IQ) stanza).isRequest() ) {
                    final IQ errorResult = IQ.createResultIQ( (IQ) stanza);
                    errorResult.setError(PacketError.Condition.service_unavailable);
                    router.route( errorResult );
                }
            }
        }
    }

    /**
     * Process a 'left' notification that is sent to us by the remote joined node.
     *
     * All occupants of the room will be notified that the occupants that joined through the node that has disconnected
     * us are no longer available (in a typical scenario, no such local occupants are expected to be in the room, as the
     * 'left' notification should be trigged by the last occupant having left the room).
     *
     * @param stanza The stanza that informed us that the FMUC peer considers us disconnected.
     */
    private void processLeftInstruction( @Nonnull final Presence stanza )
    {
        if ( stanza.getElement().element(FMUC) == null ) {
            throw new IllegalArgumentException( "Unable to process stanza that does not have FMUC data: " + stanza.toXML() );
        }

        Log.trace("(room: '{}'): FMUC peer '{}' informed us that we left the FMUC set.", room.getJID(), outboundJoin.getPeer() );

        // This *should* only occur after all of our local users have left the room. For good measure, send out
        // 'leave' for all occupants from the now disconnected FMUC node anyway.
        makeRemoteOccupantLeaveRoom( outboundJoin.occupants );

        outboundJoin = null;
        outboundJoinProgress = null;
    }

    /**
     * Processes a stanza that is received from another node in the FMUC set, by translating it into 'regular' MUC data.
     *
     * The provided input is expected to be a stanza received from another node in the FMUC set. It is stripped from
     * FMUC data, after which it is distributed to the local users.
     *
     * Additionally, it is sent out to all (other) FMUC nodes that are known.
     *
     * @param stanza The data to be processed.
     */
    private void processRegularMUCStanza( @Nonnull final Packet stanza )
    {
        final Element fmuc = stanza.getElement().element(FMUC);
        if (fmuc == null) {
            throw new IllegalArgumentException( "Provided stanza must have an 'fmuc' child element (but does not).");
        }

        final JID remoteMUC = stanza.getFrom().asBareJID();
        final JID author = new JID( fmuc.attributeValue("from") ); // TODO input validation.
        final MUCRole senderRole = room.getOccupantByFullJID( author );
        Log.trace("(room: '{}'): Processing stanza from remote FMUC peer '{}' as regular room traffic. Sender of stanza: {}", room.getJID(), remoteMUC, author );

        // Distribute. Note that this will distribute both to the local node, as well as to all FMUC nodes in the the FMUC set.
        if ( stanza instanceof Presence ) {
            RemoteFMUCNode remoteFMUCNode = inboundJoins.get(remoteMUC);
            if ( remoteFMUCNode == null && outboundJoin != null && remoteMUC.equals(outboundJoin.getPeer())) {
                remoteFMUCNode = outboundJoin;
            }
            if ( remoteFMUCNode != null )
            {
                final boolean isLeave = ((Presence) stanza).getType() == Presence.Type.unavailable;
                final boolean isJoin = ((Presence) stanza).isAvailable();

                if ( isLeave )
                {
                    Log.trace("(room: '{}'): Occupant '{}' left room on remote FMUC peer '{}'", room.getJID(), author, remoteMUC );
                    makeRemoteOccupantLeaveRoom( (Presence) stanza );
                    remoteFMUCNode.removeOccupant(author); // Remove occupant only after the leave stanzas have been sent, otherwise the author is (no longer) recognized as an occupant of the particular node when the leave is being processed.

                    // The joined room confirms that the joining room has left the set by sending a presence stanza from the bare JID
                    // of the joined room to the bare JID of the joining room with an FMUC payload containing an element 'left'.
                    if ( remoteFMUCNode instanceof InboundJoin && remoteFMUCNode.occupants.isEmpty() ) {
                        Log.trace("(room: '{}'): Last occupant that joined on remote FMUC peer '{}' has now left the room. The peer has left the FMUC node set.", room.getJID(), remoteMUC );
                        final Presence leaveFMUCSet = new Presence();
                        leaveFMUCSet.setTo( remoteMUC );
                        leaveFMUCSet.setFrom( room.getJID() );
                        leaveFMUCSet.getElement().addElement( FMUC ).addElement( "left" );
                        inboundJoins.remove(remoteMUC);

                        router.route( leaveFMUCSet );
                    }
                } else if ( isJoin ) {
                    Log.trace("(room: '{}'): Occupant '{}' joined room on remote FMUC peer '{}'", room.getJID(), author, remoteMUC );
                    remoteFMUCNode.addOccupant(author);
                    makeRemoteOccupantJoinRoom( (Presence) stanza );
                } else {
                    // FIXME implement sharing of presence.
                    Log.error("Processing of presence stanzas received from other FMUC nodes is pending implementation! Ignored stanza: {}", stanza.toXML(), new UnsupportedOperationException());
                }
            }
            else
            {
                Log.warn( "Unable to process stanza: {}", stanza.toXML() );
            }
        } else {
            // Strip all FMUC data.
            final Packet stripped = createCopyWithoutFMUC( stanza );

            // The 'stripped' stanza is going to be distributed locally. Act as if it originates from a local user, instead of the remote FMUC one.
            final JID from;
            from = senderRole.getRoleAddress();
            stripped.setFrom( from );
            stripped.setTo( room.getJID() );

            room.send( stripped, senderRole );
        }
    }

    private void finishOutboundJoin()
    {
        if ( outboundJoinProgress == null ) {
            throw new IllegalStateException( "Cannot finish outbound join from '" + room.getJID() + "' as none is in progress." );
        }
        Log.trace("(room: '{}'): Finish setting up the outbound FMUC join with '{}'.", room.getJID(), outboundJoinProgress.getPeer() );
        if ( !outboundJoinProgress.isJoinComplete() ) {
            throw new IllegalStateException( "Cannot finish outbound join from '" + room.getJID()+"' to '"+ outboundJoinProgress.getPeer()+"', as it is not complete!" );
        }

        List<OutboundJoinProgress.QueuedStanza> queued = outboundJoinProgress.purgeQueue();
        if ( outboundJoinProgress.isRejected() )
        {
            Log.trace("(room: '{}'): Notifying callback waiting for the complete FMUC join response from '{}' with a rejection.", room.getJID(), outboundJoinProgress.getPeer() );
            final FMUCException rejection = new FMUCException(outboundJoinProgress.getRejectionMessage());
            outboundJoinProgress.getCallback().completeExceptionally(rejection);
            queued.forEach( queuedStanza -> queuedStanza.future.completeExceptionally(rejection));
        }
        else
        {
            Log.trace("(room: '{}'): Synchronizing state of local room with joined FMUC node '{}'.", room.getJID(), outboundJoinProgress.getPeer() );
            outboundJoin = new OutboundJoin(outboundJoinConfiguration);

            // Before processing the data in context of the local FMUC room, ensure that the FMUC metadata state is up-to-date.
            for ( final Packet response : outboundJoinProgress.getResponses() ) {
                if ( response instanceof Presence ) {
                    final JID occupantOnJoinedNode = getFMUCFromJID(response);
                    outboundJoin.addOccupant( occupantOnJoinedNode );
                }
            }

            // Use a room role that can be used to identify the remote fmuc node (to prevent data from being echo'd back)
            final LocalMUCRoom.RoomRole roomRole = new LocalMUCRoom.RoomRole( room, outboundJoin.getPeer() );

            // Use received data to augment state of the local room.
            for ( final Packet response : outboundJoinProgress.getResponses() ) {
                try
                {
                    if ( response instanceof Presence ) {
                        makeRemoteOccupantJoinRoom((Presence) response);
                    } else if ( response instanceof Message && response.getElement().element("body") != null) {
                        addRemoteHistoryToRoom((Message) response);
                    } else if ( response instanceof Message && response.getElement().element("subject") != null) {
                        applyRemoteSubjectToRoom((Message) response, roomRole);
                    }
                } catch ( Exception e ) {
                    Log.error( "(room: '{}'): An unexpected exception occurred while processing FMUC join response stanzas.", room.getJID(), e );
                }
            }

            Log.trace("(room: '{}'): Notifying callback waiting for the complete FMUC join response from '{}' with success.", room.getJID(), outboundJoinProgress.getPeer() );
            outboundJoinProgress.getCallback().complete( null );

            Log.trace("(room: '{}'): Sending {} stanza(s) that were queued, waiting for the complete FMUC join", room.getJID(), queued );
            for ( final OutboundJoinProgress.QueuedStanza queuedStanza : queued ) {
                try {
                    doPropagateOutbound(queuedStanza.stanza, queuedStanza.sender, queuedStanza.future);
                } catch ( Exception e ) {
                    Log.warn( "An exception occurred while trying to process a stanza that was queued pending completion of FMUC join in room " + room.getJID() + ": " + queuedStanza.stanza );
                }
            }
        }
    }

    private void applyRemoteSubjectToRoom( @Nonnull final Message message, @Nonnull final MUCRole mucRole )
    {
        try
        {
            Log.trace("(room: '{}'): Received subject from joined FMUC node '{}'. Applying it locally.", room.getJID(), mucRole.getReportedFmucAddress() );
            room.changeSubject(createCopyWithoutFMUC(message), mucRole);
        }
        catch ( ForbiddenException e ) {
            // This should not be possible, as we're using a role above that should bypass the auth checks that throw this exception!
            Log.error( "(room: '{}'): An unexpected exception occurred while processing FMUC join response stanzas.", room.getJID(), e );
        }
    }

    private void addRemoteHistoryToRoom( @Nonnull final Message message )
    {
        final Element fmuc = message.getElement().element(FMUC);
        if ( fmuc == null ) {
            throw new IllegalArgumentException( "Argument 'presence' should be an FMUC presence, but it does not appear to be: it is missing the FMUC child element." );
        }

        Log.trace("(room: '{}'): Received history from joined FMUC node '{}'. Applying it locally.", room.getJID(), outboundJoinProgress.getPeer() );

        final JID userJID = new JID( fmuc.attributeValue("from"));
        final String nickname = message.getFrom().getResource();
        Date sentDate;
        final Element delay = message.getChildElement("delay","urn:xmpp:delay");
        if ( delay != null ) {
            final String stamp = delay.attributeValue("stamp");
            try
            {
                sentDate = new XMPPDateTimeFormat().parseString(stamp);
            }
            catch ( ParseException e )
            {
                Log.warn( "Cannot parse 'stamp' from delay element in message as received in FMUC join: {}", message, e );
                sentDate = null;
            }
        } else {
            sentDate = null;
            Log.warn( "Missing delay element in message received in FMUC join: {}", message );
        }

        final Message cleanedUpMessage = createCopyWithoutFMUC(message);
        room.getRoomHistory().addOldMessage( userJID.toString(), nickname, sentDate, cleanedUpMessage.getSubject(), cleanedUpMessage.getBody(), cleanedUpMessage.toXML() );
    }

    /**
     * Parses the JID from an FMUC stanza.
     *
     * More specifically, this method returns the JID representation of the 'from' attribute value of the 'fmuc' child
     * element in the stanza. A runtime exception is thrown when no such value exists, or when that value is not a
     * valid JID.
     *
     * @param stanza An FMUC stanza
     * @return A JID.
     * @throws RuntimeException when no valid JID value is found in the 'from' attribute of the FMUC child element.
     */
    public static JID getFMUCFromJID( @Nonnull final Packet stanza )
    {
        final Element fmuc = stanza.getElement().element(FMUC);
        if ( fmuc == null ) {
            throw new IllegalArgumentException( "Argument 'stanza' should be an FMUC stanza, but it does not appear to be: it is missing the FMUC child element." );
        }

        final String fromValue = fmuc.attributeValue( "from" );

        if ( fromValue == null ) {
            throw new IllegalArgumentException( "Argument 'stanza' should be a valid FMUC stanza, but it does not appear to be: it is missing a 'from' attribute value in the FMUC child element." );
        }

        final JID userJID = new JID( fromValue );
        return userJID;
    }

    /**
     * Processes a presence stanza that is expected to be an FMUC-flavored 'room join' representation, and adds the
     * remote user to the room.
     *
     * This method will <em>not</em> make modifications to the state of the FMUC node set. It expects those changes to
     * be taken care of by the caller.
     *
     * This method provides the functionally opposite implementation of {@link #makeRemoteOccupantLeaveRoom(Presence)}.
     *
     * @param presence The stanza representing a user on a federated FMUC node joining the room (cannot be null).
     * @see #makeRemoteOccupantLeaveRoom(Presence)
     */
    private void makeRemoteOccupantJoinRoom( @Nonnull final Presence presence )
    {
        // FIXME: better input validation / better problem handling when remote node sends crappy data!
        final Element mucUser = presence.getElement().element(QName.get("x","http://jabber.org/protocol/muc#user"));
        final Element fmuc = presence.getElement().element(FMUC);
        if ( fmuc == null ) {
            throw new IllegalArgumentException( "Argument 'presence' should be an FMUC presence, but it does not appear to be: it is missing the FMUC child element." );
        }

        final JID remoteMUC = presence.getFrom().asBareJID();
        final String nickname = presence.getFrom().getResource();

        Log.debug( "(room: '{}'): Occupant on remote peer '{}' joins the room with nickname '{}'.", room.getJID(), remoteMUC, nickname );

        MUCRole.Role role;
        if ( mucUser != null && mucUser.element("item") != null && mucUser.element("item").attributeValue("role") != null ) {
            try {
                role = MUCRole.Role.valueOf(mucUser.element("item").attributeValue("role"));
            } catch ( IllegalArgumentException e ) {
                Log.info( "Cannot parse role as received in FMUC join, using default role instead: {}", presence, e );
                role = MUCRole.Role.participant;
            }
        } else {
            Log.info( "Cannot parse role as received in FMUC join, using default role instead: {}", presence );
            role = MUCRole.Role.participant;
        }

        MUCRole.Affiliation affiliation;
        if ( mucUser != null && mucUser.element("item") != null && mucUser.element("item").attributeValue("affiliation") != null ) {
            try {
                affiliation = MUCRole.Affiliation.valueOf(mucUser.element("item").attributeValue("affiliation"));
            } catch ( IllegalArgumentException e ) {
                Log.info( "Cannot parse affiliation as received in FMUC join, using default role instead: {}", presence, e );
                affiliation = MUCRole.Affiliation.none;
            }
        } else {
            Log.info( "Cannot parse affiliation as received in FMUC join, using default role instead: {}", presence );
            affiliation = MUCRole.Affiliation.none;
        }

        final JID userJID = getFMUCFromJID( presence );

        final LocalMUCUser user = new LocalMUCUser(room.getMUCService(), router, userJID );

        final LocalMUCRole joinRole = new LocalMUCRole( room.getMUCService(), room, nickname, role, affiliation, user, createCopyWithoutFMUC(presence), router);
        joinRole.setReportedFmucAddress( userJID );

        final boolean clientOnlyJoin = room.alreadyJoinedWithThisNick( user, nickname );
        if (clientOnlyJoin)
        {
            Log.warn( "(room: '{}'): Ignoring join of occupant on remote peer '{}' with nickname '{}' as this user is already in the room.", room.getJID(), remoteMUC, nickname );
        }
        else
        {
            // Update the (local) room state to now include this occupant.
            room.addOccupantRole(joinRole);

            // Send out presence stanzas that signal all other occupants that this occupant has now joined. Unlike a 'regular' join we MUST
            // _not_ sent back presence for all other occupants (that has already been covered by the FMUC protocol implementation).
            room.sendInitialPresenceToExistingOccupants(joinRole);

            // Fire event that occupant joined the room.
            MUCEventDispatcher.occupantJoined(room.getJID(), joinRole.getUserAddress(), joinRole.getNickname());
        }
    }

    /**
     * Removes a remote user from the room.
     *
     * This method is intended to be used when a remote node is being disconnected from the FMUC node set, without having
     * sent 'leave' presence stanzas for its occupants. This method generates such presence stanzas, and delegates
     * further processing to {@link makeRemoteOccupantLeaveRoom}
     *
     * @param removedRemoteOccupants The occupants to be removed from the room.
     */
    private void makeRemoteOccupantLeaveRoom( @Nonnull Set<JID> removedRemoteOccupants ) {
        for ( final JID removedRemoteOccupant : removedRemoteOccupants )
        {
            try
            {
                Log.trace("(room: '{}'): Removing occupant '{}' that was joined through a (now presumably disconnected) remote node.", room.getJID(), removedRemoteOccupant);
                final MUCRole role = room.getOccupantByFullJID( removedRemoteOccupant );
                if ( role == null ) {
                    Log.warn("(room: '{}'): Unable to remove '{}' as it currently is not registered as an occupant of this room.", room.getJID(), removedRemoteOccupant);
                    continue;
                }

                final Presence leave = new Presence();
                leave.setType(Presence.Type.unavailable);
                leave.setTo(role.getRoleAddress());
                leave.setFrom(role.getUserAddress());
                leave.setStatus("FMUC node disconnect");
                final Presence enriched = enrichWithFMUCElement( leave, role.getReportedFmucAddress() );

                makeRemoteOccupantLeaveRoom( enriched );
            }
            catch ( Exception e )
            {
                Log.warn("(room: '{}'): An exception occurred while removing occupant '{}' from a (now presumably disconnected) remote node.", room.getJID(), removedRemoteOccupant, e);
            }
        }
    }

    /**
     * Processes a presence stanza that is expected to be an FMUC-flavored 'leave' representation, and removes the
     * remote user to the room.
     *
     * This method will <em>not</em> make modifications to the state of the FMUC node set. It expects those changes to
     * be taken care of by the caller.
     *
     * This method provides the functionally opposite implementation of {@link #makeRemoteOccupantJoinRoom(Presence)}.
     *
     * @param presence The stanza representing a user on a federated FMUC node leaving the room (cannot be null).
     * @see #makeRemoteOccupantJoinRoom(Presence)
     */
    private void makeRemoteOccupantLeaveRoom( @Nonnull final Presence presence )
    {
        // FIXME: better input validation / better problem handling when remote node sends crappy data!
        final Element fmuc = presence.getElement().element(FMUC);
        if ( fmuc == null ) {
            throw new IllegalArgumentException( "Argument 'presence' should be an FMUC presence, but it does not appear to be: it is missing the FMUC child element." );
        }
        final JID userJID = getFMUCFromJID( presence );

        final MUCRole leaveRole = room.getOccupantByFullJID( userJID );
        leaveRole.setPresence( createCopyWithoutFMUC(presence) ); // update presence to reflect the 'leave' - this is used later to broadcast to other occupants.

        // Send presence to inform all occupants of the room that the user has left.
        room.sendLeavePresenceToExistingOccupants( leaveRole )
            .thenRunAsync( () -> {
                // Update the (local) room state to now include this occupant.
                room.removeOccupantRole( leaveRole );

                // Fire event that occupant left the room.
                MUCEventDispatcher.occupantLeft(leaveRole.getRoleAddress(), leaveRole.getUserAddress(), leaveRole.getNickname());
            });
    }

    /**
     * Checks if the entity that attempts to join, which is assumed to represent a remote, joining FMUC node, is allowed
     * to join the local ('joined') FMUC node.
     *
     * @param joiningPeer the address of the remote room that attempts to join the local FMUC node.
     * @throws FMUCException when the peer cannot join the local FMUC node.
     */
    private void checkAcceptingFMUCJoiningNodePreconditions( @Nonnull final JID joiningPeer ) throws FMUCException
    {
        if ( !joiningPeer.asBareJID().equals(joiningPeer) ) {
            throw new IllegalArgumentException( "Expected argument 'joiningPeer' to be a bare JID, but it was not: " + joiningPeer );
        }

        if ( !(room.isFmucEnabled() && FMUC_ENABLED.getValue()) )
        {
            Log.info( "(room: '{}'): Rejecting join request of remote joining peer '{}': FMUC functionality is not enabled.", room.getJID(), joiningPeer );
            throw new FMUCException( "FMUC functionality is not enabled." );
        }

        if ( this.outboundJoinConfiguration != null && joiningPeer.equals(this.outboundJoinConfiguration.getPeer()) ) {
            Log.info( "(room: '{}'): Rejecting join request of remote joining peer '{}': The local, joined node is set up to federate with the joining node (cannot have circular federation).", room.getJID(), joiningPeer );
            throw new FMUCException( "The joined node is set up to federate with the joining node (cannot have circular federation)." );
        }

        Log.debug( "(room: '{}'): Accepting join request of remote joining peer '{}'.", room.getJID(), joiningPeer );
    }

    /**
     * Sends a stanza back to a remote, joining FMUC node that represents rejection of an FMUC join request.
     *
     * @param joinRequest The request to join that is being rejected
     * @param rejectionMessage An optional, human readable message that describes the reason for the rejection.
     */
    private void rejectJoiningFMUCNode( @Nonnull final Presence joinRequest, @Nullable final String rejectionMessage )
    {
        Log.trace("(room: '{}'): Rejecting FMUC join request from '{}'.", room.getJID(), joinRequest.getFrom().asBareJID() );
        final Presence rejection = new Presence();
        rejection.setTo( joinRequest.getFrom() );
        rejection.setFrom( this.room.getJID() );
        final Element rejectEl = rejection.addChildElement( FMUC.getName(), FMUC.getNamespaceURI() ).addElement("reject");
        if ( rejectionMessage != null && !rejectionMessage.trim().isEmpty() ) {
            rejectEl.setText( rejectionMessage );
        }
        router.route( rejection );
    }

    /**
     * Sends a stanza back to a remote, joining FMUC node that represents acceptance of a FMUC join request.
     *
     * @param joinRequest The request to join that is being accepted.
     */
    private void acceptJoiningFMUCNode( @Nonnull final Presence joinRequest )
    {
        Log.trace("(room: '{}'): Accepting FMUC join request from '{}'.", room.getJID(), joinRequest.getFrom().asBareJID() );
        final JID joiningPeer = joinRequest.getFrom().asBareJID();
        final InboundJoin inboundJoin = new InboundJoin(joiningPeer);
        final JID occupant = getFMUCFromJID(joinRequest);
        inboundJoin.addOccupant( occupant );
        inboundJoins.put(joiningPeer, inboundJoin); // TODO make thread safe.
        afterJoinSendOccupants( joiningPeer );
        afterJoinSendHistory( joiningPeer );
        afterJoinSendSubject( joiningPeer );
        makeRemoteOccupantJoinRoom( joinRequest );
    }

    private void afterJoinSendOccupants( @Nonnull final JID joiningPeer )
    {
        if ( !joiningPeer.asBareJID().equals(joiningPeer) ) {
            throw new IllegalArgumentException( "Expected argument 'joiningPeer' to be a bare JID, but it was not: " + joiningPeer );
        }

        Log.trace("(room: '{}'): Sending current occupants to joining node '{}'.", room.getJID(), joiningPeer );

        for ( final MUCRole occupant : room.getOccupants() ) {
            if ( occupant.getReportedFmucAddress() != null && occupant.getReportedFmucAddress().asBareJID().equals( joiningPeer ) ) {
                Log.trace("(room: '{}'): Skipping occupant '{}' as that originates from the joining node.", room.getJID(), occupant );
                continue;
            }

            // TODO can we use occupant.getPresence() for this?
            // TODO do we need to worry about who we're exposing data to?
            final Presence presence = new Presence();
            presence.setFrom( occupant.getRoleAddress() );
            presence.setTo( joiningPeer );
            final Presence enriched = enrichWithFMUCElement( presence, occupant );
            final Element xitem = enriched.addChildElement( "x", "http://jabber.org/protocol/muc#user" ).addElement( "item" );
            xitem.addAttribute( "affiliation", occupant.getAffiliation().toString() );
            xitem.addAttribute( "role", occupant.getRole().toString() );
            xitem.addAttribute( "jid", occupant.getRoleAddress().toString() );

            router.route( enriched );
        }
    }

    private void afterJoinSendHistory( @Nonnull final JID joiningPeer )
    {
        if ( !joiningPeer.asBareJID().equals(joiningPeer) ) {
            throw new IllegalArgumentException( "Expected argument 'joiningPeer' to be a bare JID, but it was not: " + joiningPeer );
        }

        // TODO. Can org.jivesoftware.openfire.muc.spi.LocalMUCRoom.sendRoomHistoryAfterJoin be reused to reduce duplicate code and responsibilities?
        Log.trace("(room: '{}'): Sending history to joining node '{}'.", room.getJID(), joiningPeer );
        final MUCRoomHistory roomHistory = room.getRoomHistory();
        final Iterator<Message> history = roomHistory.getMessageHistory();
        while (history.hasNext()) {
            // The message stanza in the history is the original stanza (with original addressing), which we can leverage
            // to obtain the 'real' jid of the sender. Note that this sender need not be in the room any more, so we can't
            // depend on having a MUCRole for it.
            final Message oldMessage = history.next();

            final JID originalAuthorUserAddress = oldMessage.getFrom();
            final JID originalAuthorRoleAddress = new JID( room.getJID().getNode(), room.getJID().getDomain(), originalAuthorUserAddress.getResource() );

            final Message enriched = enrichWithFMUCElement( oldMessage, originalAuthorUserAddress );

            // Correct the addressing of the stanza.
            enriched.setFrom( originalAuthorRoleAddress );
            enriched.setTo( joiningPeer );

            router.route( enriched );
        }
    }

    private void afterJoinSendSubject( @Nonnull final JID joiningPeer )
    {
        if ( !joiningPeer.asBareJID().equals(joiningPeer) ) {
            throw new IllegalArgumentException( "Expected argument 'joiningPeer' to be a bare JID, but it was not: " + joiningPeer );
        }

        Log.trace("(room: '{}'): Sending subject to joining node '{}'.", room.getJID(), joiningPeer );

        // TODO can org.jivesoftware.openfire.muc.spi.LocalMUCRoom.sendRoomSubjectAfterJoin be re-used?
        final MUCRoomHistory roomHistory = room.getRoomHistory();
        Message roomSubject = roomHistory.getChangedSubject();
        if ( roomSubject == null ) {
            roomSubject = new Message();
            roomSubject.setFrom(this.room.getJID()); // This might break FMUC, as it does not include the nickname of the author of the subject.
            roomSubject.setTo(joiningPeer);
            roomSubject.setType(Message.Type.groupchat);
            roomSubject.setID(UUID.randomUUID().toString());
            final Element subjectEl = roomSubject.getElement().addElement("subject");
            if ( room.getSubject() != null && !room.getSubject().isEmpty() )
            {
                subjectEl.setText(room.getSubject());
            }
        }

        final JID originalAuthorUserAddress = roomSubject.getFrom();
        final JID originalAuthorRoleAddress = new JID( room.getJID().getNode(), room.getJID().getDomain(), originalAuthorUserAddress.getResource() );

        final Message enriched = enrichWithFMUCElement( roomSubject, originalAuthorUserAddress );

        // Correct the addressing of the stanza.
        enriched.setFrom( originalAuthorRoleAddress );
        enriched.setTo( joiningPeer );

        router.route( enriched );
    }

    public static boolean isSubject( @Nonnull final Packet stanza )
    {
        final Element fmuc = stanza.getElement().element(FMUC);
        if (fmuc == null) {
            throw new IllegalArgumentException( "Provided stanza must have an 'fmuc' child element (but does not).");
        }

        final boolean result = stanza instanceof Message && stanza.getElement().element("subject") != null;
        Log.trace( "Stanza from '{}' was determined to {} a stanza containing a MUC subject.", stanza.getFrom(), result ? "be" : "not be" );
        return result;
    }

    public static boolean isFMUCReject( @Nonnull final Packet stanza )
    {
        final Element fmuc = stanza.getElement().element(FMUC);
        if (fmuc == null) {
            throw new IllegalArgumentException( "Provided stanza must have an 'fmuc' child element (but does not).");
        }

        final boolean result = stanza instanceof Presence && fmuc.element("reject") != null;
        Log.trace( "Stanza from '{}' was determined to {} a stanza containing a FMUC join reject.", stanza.getFrom(), result ? "be" : "not be" );
        return result;
    }

    public static boolean isFMUCJoinRequest( @Nonnull Packet stanza )
    {
        final Element fmuc = stanza.getElement().element(FMUC);
        if (fmuc == null) {
            throw new IllegalArgumentException( "Provided stanza must have an 'fmuc' child element (but does not).");
        }

        final boolean result =
            (stanza instanceof Presence) &&
                stanza.getElement().element( QName.get( "x", "http://jabber.org/protocol/muc") ) != null &&
                stanza.getElement().element( QName.get( "x", "http://jabber.org/protocol/muc#user") ) != null;

        Log.trace( "Stanza from '{}' was determined to {} a stanza containing a FMUC join request.", stanza.getFrom(), result ? "be" : "not be" );
        return result;
    }

    public OutboundJoin getOutboundJoin() {
        return outboundJoin;
    }

    public OutboundJoinProgress getOutboundJoinProgress() {
        return outboundJoinProgress;
    }

    public Collection<InboundJoin> getInboundJoins() {
        return inboundJoins.values();
    }

    public void abortOutboundJoinProgress()
    {
        if ( outboundJoinProgress != null ) {
            outboundJoinProgress.abort();
            outboundJoinProgress = null;
        }
    }

    abstract static class RemoteFMUCNode implements Serializable
    {
        private final Logger Log;

        /**
         * The address of the remote MUC room that is federating with us, in which 'our' MUC room takes the role of
         * 'joined FMUC node' while the room that federates with us (who's address is recorded in this field) takes the
         * role of 'joining FMUC node'.
         */
        private final JID peer;

        /**
         * The addresses of the occupants of the MUC that are joined through this FMUC node.
         */
        protected final Set<JID> occupants = new HashSet<>();

        public JID getPeer() {
            return peer;
        }

        RemoteFMUCNode( @Nonnull final JID peer ) {
            this.peer = peer.asBareJID();
            Log = LoggerFactory.getLogger( this.getClass().getName() + ".[peer: " + peer + "]" );
        }

        public boolean wantsStanzasSentBy( @Nonnull final MUCRole sender ) {
            // Only send data if the sender is not an entity on this remote FMUC node, or the remote FMUC node itself.
            return sender.getReportedFmucAddress() == null || (!occupants.contains( sender.getReportedFmucAddress() ) && !peer.equals( sender.getReportedFmucAddress()) );
        }

        public boolean addOccupant( @Nonnull final JID occupant ) {
            Log.trace( "Adding remote occupant: '{}'", occupant );
            return occupants.add( occupant );
        }

        public boolean removeOccupant( @Nonnull final JID occupant ) {
            Log.trace( "Removing remote occupant: '{}'", occupant );
            return occupants.remove( occupant );
        }

        public Set<JID> getOccupants() {
            return occupants;
        }
    }

    public static class InboundJoin extends RemoteFMUCNode
    {
        public InboundJoin( @Nonnull final JID peer )
        {
            super(peer);
        }
    }

    public static class OutboundJoinConfiguration
    {
        private final FMUCMode mode;
        private final JID peer;

        public OutboundJoinConfiguration( @Nonnull final JID peer, @Nonnull final FMUCMode mode ) {
            this.mode = mode;
            this.peer = peer;
        }

        public FMUCMode getMode()
        {
            return mode;
        }

        public JID getPeer()
        {
            return peer;
        }

        @Override
        public boolean equals( final Object o )
        {
            if ( this == o ) { return true; }
            if ( o == null || getClass() != o.getClass() ) { return false; }
            final OutboundJoinConfiguration that = (OutboundJoinConfiguration) o;
            return mode == that.mode &&
                peer.equals(that.peer);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(mode, peer);
        }

        @Override
        public String toString()
        {
            return "OutboundJoinConfiguration{" +
                "peer=" + peer +
                ", mode=" + mode +
                '}';
        }
    }

    public static class OutboundJoin extends RemoteFMUCNode
    {
        private final FMUCMode mode;

        /**
         * A list of stanzas that need to have been echo'd by a remote FMUC node, before they can be processed locally.
         * This collection _does not_ include stanzas needed to set up the initial join. This collection is only used
         * for subsequent stanzas that are shared in a setting where echo-ing is required (due to the mode of the
         * federation being defined as 'master-slave').
         */
        private final Set<PendingCallback> pendingEcho = new HashSet<>();

        public OutboundJoin( @Nonnull OutboundJoinConfiguration configuration ) {
            super( configuration.getPeer() );
            this.mode = configuration.getMode();
        }

        public OutboundJoin( @Nonnull final JID peer, @Nonnull final FMUCMode mode ) {
            super(peer);
            this.mode = mode;
        }

        public FMUCMode getMode()
        {
            return mode;
        }

        public OutboundJoinConfiguration getConfiguration() {
            return new OutboundJoinConfiguration(getPeer(), getMode() );
        }

        @Override
        public boolean wantsStanzasSentBy( @Nonnull MUCRole sender ) {
            if ( FMUCMode.MasterSlave == mode ) {
                return true; // always wants stanzas - if only because the data needs to be echo'd back.
            }

            return super.wantsStanzasSentBy(sender);
        }

        public synchronized void evaluateForCallbackCompletion( @Nonnull Packet stanza )
        {
            Log.trace( "Evaluating stanza for callback completion..." );
            if ( stanza.getElement().element(FMUC) == null ) {
                throw new IllegalArgumentException( "Argument 'stanza' must have an FMUC child element." );
            }

            // Ignore if we do not have a callback waiting for this stanza.
            final Iterator<PendingCallback> iter = pendingEcho.iterator();
            while (iter.hasNext()) {
                final PendingCallback item = iter.next();
                if ( item.isMatch(stanza) ) {
                    Log.trace( "Invoking callback, as peer '{}' echo'd back stanza: {}", getPeer(), stanza.toXML() );
                    item.complete();
                    iter.remove();
                }
            }
            Log.trace( "Finished evaluating stanza for callback completion." );
        }

        public synchronized void registerEchoCallback( @Nonnull final Packet stanza, @Nonnull final CompletableFuture<?> result )
        {
            if ( stanza.getElement().element(FMUC) == null ) {
                throw new IllegalArgumentException( "Argument 'stanza' must have an FMUC child element." );
            }

            Log.trace( "Registering callback to be invoked when peer '{}' echos back stanza {}", getPeer(), stanza.toXML() );
            pendingEcho.add( new PendingCallback( stanza, result ) );
        }
    }

    public static class OutboundJoinProgress implements Serializable
    {
        private final Logger Log;

        /**
         * The address of the remote MUC room with which we are attempting to federate, in which 'our' MUC room takes the role of
         * 'joining FMUC node' while the room that federates with us (who's address is recorded in this field) takes the
         * role of 'joined FMUC node'.
         */
        private final JID peer;

        /**
         * The future that is awaiting completion of the join operation.
         */
        private final CompletableFuture<List<Packet>> callback;

        /**
         * A list of stanzas that have been sent from the remote room to the local room as part of the 'join' effort.
         *
         * This list is expected to contain (presence of) each participant, a message history, and a subject stanza.
         */
        private final ArrayList<Packet> responses;

        /**
         * A list of stanzas to be sent to the remote room as soon as federation has been established.
         *
         * This list is expected to contain stanzas that were shared with the joined room between the instant that a
         * federation attempt was started, and was completed.
         */
        private final ArrayList<QueuedStanza> queue;

        /**
         * The state of the federation join. Null means that the request is pending completion. True means a successful
         * join was achieved, while false means that the join request failed or was aborted.
         */
        private Boolean joinResult;

        public OutboundJoinProgress( @Nonnull final JID peer, @Nonnull final CompletableFuture<List<Packet>> callback )
        {
            Log = LoggerFactory.getLogger( this.getClass().getName() + ".[peer: " + peer + "]" );
            this.peer = peer.asBareJID();
            this.callback = callback;
            this.responses = new ArrayList<>();
            this.queue = new ArrayList<>();
        }

        public JID getPeer() {
            return peer;
        }

        public synchronized CompletableFuture<List<Packet>> getCallback() {
            return callback;
        }

        public synchronized ArrayList<Packet> getResponses() {
            return responses;
        }

        synchronized void addResponse( @Nonnull final Packet stanza ) {
            this.responses.add( stanza );
            if (joinResult == null) {
                if ( isSubject( stanza ) ) {
                    joinResult = true;
                }
                if ( isFMUCReject( stanza ) ) {
                    joinResult = false;
                }
            }
        }

        /**
         * Adds a stanza to be sent to the remote, joined MUC as soon as federation has been established.
         *
         * This method is intended to be used only when federation is in progress of being established.
         *
         * @param stanza The stanza to share
         * @param sender The author of the stanza
         * @return A future, indicating if local distribution of the stanza needs to wait.
         */
        public synchronized CompletableFuture<?> addToQueue( @Nonnull final Packet stanza, @Nonnull final MUCRole sender ) {
            if( isJoinComplete() ) {
                throw new IllegalStateException( "Queueing a stanza is not expected to occur when federation has already been established." );
            }

            final CompletableFuture<?> result = new CompletableFuture<>();
            if ( callback.isDone() ) {
                result.complete(null);
            }

            Log.trace( "Adding stanza (type {}) from '{}' to queue, to be sent to peer as soon as federation has been established.", stanza.getClass().getSimpleName(), sender.getUserAddress() );
            queue.add( new QueuedStanza( stanza, sender, result ) );

            return result;
        }

        /**
         * Retrieve and clean the list of stanzas that have been queued after federation was initiated, but before it
         * was finished.
         *
         * @return A list of queued stanzas (possibly empty).
         */
        public synchronized List<QueuedStanza> purgeQueue() {
            Log.trace( "Purging queue (size: {}) of stanzas to be sent to peer as soon as federation has been established.", queue.size() );
            final List<QueuedStanza> result = new ArrayList<>( queue );
            queue.clear();
            return result;
        }

        public synchronized boolean isJoinComplete() {
            return joinResult != null;
        }

        public synchronized boolean isRejected() {
            return joinResult != null && !joinResult;
        }

        public synchronized String getRejectionMessage()
        {
            if (!isRejected()) {
                throw new IllegalStateException( "Cannot get rejection message, as rejection did not occur." );
            }

            final Packet stanza = this.responses.get( this.responses.size() -1 );
            final Element fmuc = stanza.getElement().element(FMUC);
            return fmuc.elementText("reject");
        }

        public synchronized boolean isSuccessful() {
            return joinResult != null && joinResult;
        }

        synchronized void abort()
        {
            Log.trace( "Aborting federation attempt." );

            joinResult = false;

            // Messages that are queued to be sent after federation has been established might have threads blocking on that delivery. That now will no longer happen. Make sure that we unblock all threads waiting for such an echo.
            if ( !queue.isEmpty() )
            {
                Log.trace("Completing {} callbacks for queued stanzas might be waiting for federation to be established.", queue.size() );
                for ( final QueuedStanza pendingCallback : queue )
                {
                    try {
                        pendingCallback.future.complete( null ); // TODO maybe completeExceptionally?
                    } catch ( Exception e ) {
                        Log.warn("An exception occurred while completing callback for a queued message.", e);
                    }
                }
            }
            callback.completeExceptionally( new IllegalStateException( "Federation with peer " + peer + " has been aborted.") );
        }

        static class QueuedStanza {
            final Packet stanza;
            final MUCRole sender;
            final CompletableFuture<?> future;

            QueuedStanza( final Packet stanza, final MUCRole sender, final CompletableFuture<?> future ) {
                this.stanza = stanza;
                this.sender = sender;
                this.future = future;
            }
        }
    }

    /**
     * Represents a callback waiting for a stanza to be echo'd back from a remote FMUC node.
     */
    static class PendingCallback {

        final CompletableFuture<?> callback;
        final Class<? extends Packet> type;
        final JID remoteFMUCNode;
        final List<Element> elements;

        public <S extends Packet> PendingCallback( @Nonnull S original, @Nonnull CompletableFuture<?> callback ) {
            if (!hasFMUCElement(original)) {
                throw new IllegalArgumentException( "Provided stanza must be a stanza that is sent to a remote FMUC node, but was not (the FMUC child element is missing): " + original );
            }
            this.type = getType( original );
            this.remoteFMUCNode = original.getTo().asBareJID();
            this.elements = original.getElement().elements();
            this.callback = callback;
        }

        private void complete() {
            callback.complete( null );
        }

        public <S extends Packet> boolean isMatch( @Nonnull S stanza )
        {
            if (!hasFMUCElement(stanza)) {
                throw new IllegalArgumentException( "Provided stanza must be a stanza that was sent by remote FMUC node, but was not (the FMUC child element is missing): " + stanza );
            }

            if (!stanza.getClass().equals(type)) {
                return false;
            }

            if (!stanza.getFrom().asBareJID().equals( remoteFMUCNode )) {
                return false;
            }

            // All child elements of the echo'd stanza must equal the original.
            return elements.equals( stanza.getElement().elements() );
        }

        protected static boolean hasFMUCElement( @Nonnull Packet stanza ) {
            return stanza.getElement().element(FMUC) != null;
        }

        protected static Class<? extends Packet> getType( @Nonnull Packet stanza ) {
            return stanza.getClass();
        }
    }
}
