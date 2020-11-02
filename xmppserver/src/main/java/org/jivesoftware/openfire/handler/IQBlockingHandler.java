/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentFactory;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.privacy.PrivacyListProvider;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.util.*;

/**
 * Implementation of XEP-0191 "Blocking Command".
 *
 * This implementation uses the default privacy list of a user to store its blocklist.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class IQBlockingHandler extends IQHandler implements ServerFeaturesProvider
{
    private static final Logger Log = LoggerFactory.getLogger( IQBlockingHandler.class );

    public static final String NAMESPACE = "urn:xmpp:blocking";

    public IQBlockingHandler()
    {
        super( "XEP-0191 Blocking Command handler" );
    }

    @Override
    public IQHandlerInfo getInfo()
    {
        return new IQHandlerInfo( "blocklist", NAMESPACE );
    }


    @Override
    public Iterator<String> getFeatures()
    {
        return Collections.singletonList( NAMESPACE ).iterator();
    }

    @Override
    public IQ handleIQ( IQ iq ) throws UnauthorizedException
    {
        if ( iq.isResponse() )
        {
            return null;
        }

        final JID requester = iq.getFrom();

        if ( !XMPPServer.getInstance().getUserManager().isRegisteredUser( requester, false ) )
        {
            final IQ error = IQ.createResultIQ( iq );
            error.setError( PacketError.Condition.not_authorized );
            return error;
        }

        final User user;
        try
        {
            user = UserManager.getInstance().getUser( requester.getNode() );
        }
        catch ( UserNotFoundException e )
        {
            Log.error( "Unable to retrieve user '{}' that was verified to be an existing user!", requester.getNode(), e );
            final IQ error = IQ.createResultIQ( iq );
            error.setError( PacketError.Condition.internal_server_error );
            return error;
        }

        try
        {
            if ( iq.getType().equals( IQ.Type.get ) && "blocklist".equals( iq.getChildElement().getName() ) )
            {
                final Set<JID> blocklist = getBlocklist( user );
                final IQ response = IQ.createResultIQ( iq );

                final Element blocklistElement = DocumentHelper.createElement( QName.get( getInfo().getName(), getInfo().getNamespace() ) );
                for ( final JID blocked : blocklist )
                {
                    blocklistElement.addElement( "item" ).addAttribute( "jid", blocked.toString() );
                }
                response.setChildElement( blocklistElement );

                sessionManager.getSession( iq.getFrom() ).setHasRequestedBlocklist( true );
                return response;
            }
            else if ( iq.getType().equals( IQ.Type.set ) && "block".equals( iq.getChildElement().getName() ) )
            {
                final List<Element> items = iq.getChildElement().elements( "item" );
                if ( items == null || items.isEmpty() )
                {
                    final IQ error = IQ.createResultIQ( iq );
                    error.setError( PacketError.Condition.bad_request );
                    return error;
                }

                final List<JID> toBlocks = new ArrayList<>();
                for ( final Element item : items )
                {
                    toBlocks.add( new JID( item.attributeValue( "jid" ) ) );
                }

                addToBlockList( user, toBlocks );
                pushBlocklistUpdates( user, toBlocks );

                return IQ.createResultIQ( iq );
            }
            else if ( iq.getType().equals( IQ.Type.set ) && "unblock".equals( iq.getChildElement().getName() ) )
            {
                final Set<JID> unblocked;
                final List<Element> items = iq.getChildElement().elements( "item" );
                if ( items == null || items.isEmpty() )
                {
                    unblocked = removeAllFromBlocklist( user );
                }
                else
                {
                    final Collection<JID> toUnblocks = new ArrayList<>();
                    for ( final Element item : items )
                    {
                        toUnblocks.add( new JID( item.attributeValue( "jid" ) ) );
                    }
                    unblocked = removeFromBlockList( user, toUnblocks );
                }

                pushBlocklistUpdates( user, unblocked );
                sendPresence( user, unblocked );

                return IQ.createResultIQ( iq );
            }
            else
            {
                final IQ error = IQ.createResultIQ( iq );
                error.setError( PacketError.Condition.feature_not_implemented );
                return error;
            }
        }
        catch ( Exception e )
        {
            Log.warn( "An exception occurred while trying to process an IQ block request from '{}':", iq.getFrom(), e );
            final IQ error = IQ.createResultIQ( iq );
            error.setError( PacketError.Condition.internal_server_error );
            return error;
        }
    }

    /**
     * Retrieves all of the JIDs that are on the blocklist of the provided user.
     *
     * @param user The use for which to retrieve the blocklist (cannot be null).
     * @return The JIDs that are on the blocklist (possibly empty, but never null).
     */
    protected Set<JID> getBlocklist( User user )
    {
        Log.debug( "Retrieving all JIDs that are on the blocklist of user '{}'.", user.getUsername() );
        final PrivacyList defaultPrivacyList = PrivacyListManager.getInstance().getDefaultPrivacyList( user.getUsername() );
        if ( defaultPrivacyList == null )
        {
            return Collections.emptySet();
        }

        return defaultPrivacyList.getBlockedJIDs();
    }

    /**
     * Sends current presence information of the local user to the a collection of JIDs, if appropriate.
     *
     * @param user       The use for which to send presence (cannot be null).
     * @param recipients The entities to which information is send (cannot be null, can be empty)
     */
    private void sendPresence( User user, Set<JID> recipients )
    {
        if ( recipients.isEmpty() )
        {
            return;
        }

        final PresenceManager presenceManager = XMPPServer.getInstance().getPresenceManager();
        final Presence presence = presenceManager.getPresence( user );
        if ( presence == null )
        {
            return;
        }

        for ( final JID recipient : recipients )
        {
            try
            {
                if ( presenceManager.canProbePresence( recipient, user.getUsername() ) )
                {
                    presenceManager.probePresence( recipient.asBareJID(), XMPPServer.getInstance().createJID( user.getUsername(), null ) );
                }
            }
            catch ( UserNotFoundException e )
            {
                Log.error( "Unable to send presence information of user '{}' to unblocked entity '{}' as local user is not found.", user.getUsername(), recipient );
            }
        }
    }

    /**
     * Adds a collection of JIDs to the blocklist of the provided user.
     *
     * This method adds the JIDs to the default privacy list, creating a new privacy list (and setting it as default)
     * if the user does not have a default privacy list.
     *
     * The newly added JIDs are push on the front end of the list. The order of pre-existing list items is modified.
     *
     * @param user     The owner of the blocklist to which JIDs are to be added (cannot be null).
     * @param toBlocks The JIDs to be added (can be null, which results in a noop).
     */
    protected void addToBlockList( User user, List<JID> toBlocks )
    {
        if ( toBlocks == null || toBlocks.isEmpty() )
        {
            return;
        }

        Log.debug( "Obtain or create a the default privacy list for '{}'", user.getUsername() );
        PrivacyList defaultPrivacyList = PrivacyListManager.getInstance().getDefaultPrivacyList( user.getUsername() );
        if ( defaultPrivacyList == null )
        {
            final Element listElement = DocumentFactory.getInstance().createDocument().addElement( "list", "jabber:iq:privacy" );
            listElement.addAttribute( "name", "blocklist" );

            defaultPrivacyList = PrivacyListManager.getInstance().createPrivacyList( user.getUsername(), "blocklist", listElement );
            PrivacyListManager.getInstance().changeDefaultList( user.getUsername(), defaultPrivacyList, null );
        }

        Log.debug( "Adding {} JIDs as blocked items to the beginning of list '{}' (belonging to '{}')", toBlocks.size(), defaultPrivacyList.getName(), user.getUsername() );
        final Element listElement = defaultPrivacyList.asElement();
        for ( int i = 0; i < toBlocks.size(); i++ )
        {
            final Element element = DocumentHelper.createElement( "item" )
                .addAttribute( "type", "jid" )
                .addAttribute( "value", toBlocks.get( i ).toString() )
                .addAttribute( "action", "deny" )
                .addAttribute( "order", Integer.toString( i ) );
            listElement.elements().add( i, element );
        }

        Log.debug( "Iterating over all items of list '{}' (belonging to '{}'), to ensure that their 'order' value is unique.", defaultPrivacyList.getName(), user.getUsername() );
        final List<Element> elements = listElement.elements();
        for ( int i = 0; i < elements.size(); i++ )
        {
            elements.get( i ).attribute( "order" ).setValue( Integer.toString( i ) );
        }

        defaultPrivacyList.updateList( listElement );
        PrivacyListProvider.getInstance().updatePrivacyList( user.getUsername(), defaultPrivacyList );
    }

    /**
     * Sends an IQ-set with the newly blocked JIDs to all resources of the user that have requested the blocklist.
     *
     * @param user      The for which updates are to be broadcasted (cannot be null).
     * @param newBlocks The JIDs for which an update needs to be sent (cannot be null, can be empty).
     */
    protected void pushBlocklistUpdates( User user, List<JID> newBlocks )
    {
        if ( newBlocks.isEmpty() )
        {
            return;
        }

        Log.debug( "Pushing blocklist updates to all resources of user '{}' that have previously requested the blocklist.", user.getUsername() );

        final Collection<ClientSession> sessions = sessionManager.getSessions( user.getUsername() );
        for ( final ClientSession session : sessions )
        {
            if ( session.hasRequestedBlocklist() )
            {
                final IQ iq = new IQ( IQ.Type.set );
                iq.setTo( session.getAddress() );
                final Element block = iq.setChildElement( "block", NAMESPACE );
                for ( final JID newBlock : newBlocks )
                {
                    block.addElement( "item" ).addAttribute( "jid", newBlock.toString() );
                }

                XMPPServer.getInstance().getPacketRouter().route( iq );
            }
        }
    }

    /**
     * Removes all JIDs from the blocklist of the provided user.
     *
     * This method removes the JIDs to the default privacy list. When no default privacy list exists for this user, this
     * method does nothing.
     *
     * @param user The owner of the blocklist to which JIDs are to be added (cannot be null).
     * @return The JIDs that are removed (never null, possibly empty).
     */
    private Set<JID> removeAllFromBlocklist( User user )
    {
        Log.debug( "Obtain the default privacy list for '{}'", user.getUsername() );
        final Set<JID> result = new HashSet<>();
        PrivacyList defaultPrivacyList = PrivacyListManager.getInstance().getDefaultPrivacyList( user.getUsername() );
        if ( defaultPrivacyList == null )
        {
            return result;
        }

        Log.debug( "Removing all JIDs from blocklist '{}' (belonging to '{}')", defaultPrivacyList.getName(), user.getUsername() );
        final Element listElement = defaultPrivacyList.asElement();
        final Set<Element> toRemove = new HashSet<>();
        for ( final Element element : listElement.elements( "item" ) )
        {
            if ( "jid".equals( element.attributeValue( "type" ) )
                && "deny".equals( element.attributeValue( "action" ) ) )
            {
                toRemove.add( element );
                result.add( new JID( element.attributeValue( "value" ) ) );
            }
        }

        if ( !toRemove.isEmpty() )
        {
            for ( final Element remove : toRemove )
            {
                listElement.remove( remove );
            }

            defaultPrivacyList.updateList( listElement );
            PrivacyListProvider.getInstance().updatePrivacyList( user.getUsername(), defaultPrivacyList );
        }
        return result;
    }

    /**
     * Removes a collection of JIDs from the blocklist of the provided user.
     *
     * This method removes the JIDs to the default privacy list. When no default privacy list exists for this user, this
     * method does nothing.
     *
     * @param user       The owner of the blocklist to which JIDs are to be added (cannot be null).
     * @param toUnblocks The JIDs to be removed (can be null, which results in a noop).
     * @return The JIDs that are removed (never null, possibly empty).
     */
    protected Set<JID> removeFromBlockList( User user, Collection<JID> toUnblocks )
    {
        final Set<JID> result = new HashSet<>();
        if ( toUnblocks == null || toUnblocks.isEmpty() )
        {
            return result;
        }

        Log.debug( "Obtain the default privacy list for '{}'", user.getUsername() );
        PrivacyList defaultPrivacyList = PrivacyListManager.getInstance().getDefaultPrivacyList( user.getUsername() );
        if ( defaultPrivacyList == null )
        {
            return result;
        }

        Log.debug( "Removing {} JIDs as blocked items from list '{}' (belonging to '{}')", toUnblocks.size(), defaultPrivacyList.getName(), user.getUsername() );
        final Element listElement = defaultPrivacyList.asElement();
        final Set<Element> toRemove = new HashSet<>();
        for ( final Element element : listElement.elements( "item" ) )
        {
            final JID jid = new JID( element.attributeValue( "value" ) );
            if ( "jid".equals( element.attributeValue( "type" ) )
                && "deny".equals( element.attributeValue( "action" ) )
                && toUnblocks.contains( jid ) )
            {
                toRemove.add( element );
                result.add( jid );
            }
        }

        if ( !toRemove.isEmpty() )
        {
            for ( final Element remove : toRemove )
            {
                listElement.remove( remove );
            }

            defaultPrivacyList.updateList( listElement );
            PrivacyListProvider.getInstance().updatePrivacyList( user.getUsername(), defaultPrivacyList );
        }

        return result;
    }

    /**
     * Sends an IQ-set with the newly blocked JIDs to all resources of the user that have requested the blocklist.
     *
     * @param user      The for which updates are to be broadcasted (cannot be null).
     * @param newBlocks The JIDs for which an update needs to be sent (cannot be null, can be empty).
     */
    protected void pushBlocklistUpdates( User user, Collection<JID> newBlocks )
    {
        if ( newBlocks.isEmpty() )
        {
            return;
        }

        Log.debug( "Pushing blocklist updates to all resources of user '{}' that have previously requested the blocklist.", user.getUsername() );

        final Collection<ClientSession> sessions = sessionManager.getSessions( user.getUsername() );
        for ( final ClientSession session : sessions )
        {
            if ( session.hasRequestedBlocklist() )
            {
                final IQ iq = new IQ( IQ.Type.set );
                iq.setTo( session.getAddress() );
                final Element block = iq.setChildElement( "unblock", NAMESPACE );
                for ( final JID newBlock : newBlocks )
                {
                    block.addElement( "item" ).addAttribute( "jid", newBlock.toString() );
                }

                XMPPServer.getInstance().getPacketRouter().route( iq );
            }
        }
    }
}
