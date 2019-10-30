/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.resultsetmanagement.ResultSet;
import org.xmpp.resultsetmanagement.ResultSetImpl;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * This class adds support for the search functionality for MUC rooms as identified by
 * the 'https://xmlns.zombofant.net/muclumbus/search/1.0' namespace.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://search.jabbercat.org/docs/api>https://search.jabbercat.org/docs/api</a>
 */
public class IQMuclumbusSearchHandler
{
    public static final String VAR_SINNAME = "sinname";
    private static final Logger Log = LoggerFactory.getLogger( IQMuclumbusSearchHandler.class );
    public static final String VAR_Q = "q";
    public static final String VAR_SINDESCRIPTION = "sindescription";
    public static final String VAR_SINDADDR = "sinaddr";
    public static final String VAR_MIN_USERS = "min_users";
    public static final String VAR_KEY = "key";

    public static SystemProperty<Boolean> PROPERTY_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey( "xmpp.muc.muclumbus.v1-0.enabled" )
        .setDynamic( true )
        .setDefaultValue( true )
        .build();

    public static final String REQUEST_ELEMENT_NAME = "search";
    public static final String RESPONSE_ELEMENT_NAME = "result";
    public static final String NAMESPACE = "https://xmlns.zombofant.net/muclumbus/search/1.0";

    public enum Key
    {
        nusers,
        address
    }

    /**
     * The MUC-server to extend with this functionality.
     */
    private final MultiUserChatService mucService;

    /**
     * Creates a new instance of the search provider.
     *
     * @param mucService The server for which to return search results.
     */
    public IQMuclumbusSearchHandler( MultiUserChatService mucService )
    {
        this.mucService = mucService;
    }

    /**
     * Utility method that returns a 'search' child element filled
     * with a blank dataform.
     *
     * @return Element, named 'search', escaped by the 'https://xmlns.zombofant.net/muclumbus/search/1.0' namespace, filled with a blank dataform.
     */
    private static Element getDataElement()
    {
        final DataForm searchForm = new DataForm( DataForm.Type.form );
        searchForm.setTitle( "Chat Rooms Search" );
        searchForm.addInstruction( "Instructions" );

        final FormField typeFF = searchForm.addField();
        typeFF.setVariable( "FORM_TYPE" );
        typeFF.setType( FormField.Type.hidden );
        typeFF.addValue( NAMESPACE + "#params" );

        final FormField qFF = searchForm.addField();
        qFF.setVariable( VAR_Q );
        qFF.setType( FormField.Type.text_single );
        qFF.setLabel( "Search for" );
        qFF.setRequired( false );

        final FormField sinnameFF = searchForm.addField();
        sinnameFF.setVariable( VAR_SINNAME );
        sinnameFF.setType( FormField.Type.boolean_type );
        sinnameFF.setLabel( "Search in name?" );
        sinnameFF.setRequired( false );
        sinnameFF.addValue( true );

        final FormField sindescriptionFF = searchForm.addField();
        sindescriptionFF.setVariable( VAR_SINDESCRIPTION );
        sindescriptionFF.setType( FormField.Type.boolean_type );
        sindescriptionFF.setLabel( "Search in description?" );
        sindescriptionFF.setRequired( false );
        sindescriptionFF.addValue( true );

        final FormField sinaddr = searchForm.addField();
        sinaddr.setVariable( VAR_SINDADDR );
        sinaddr.setType( FormField.Type.boolean_type );
        sinaddr.setLabel( "Search in address?" );
        sinaddr.setRequired( false );
        sinaddr.addValue( true );

        final FormField minUsersFF = searchForm.addField();
        minUsersFF.setVariable( VAR_MIN_USERS );
        minUsersFF.setType( FormField.Type.text_single );
        minUsersFF.setLabel( "Minimum number of users" );
        minUsersFF.setRequired( false );
        minUsersFF.addValue( 1 );

        final FormField keyFF = searchForm.addField();
        keyFF.setVariable( VAR_KEY );
        keyFF.setType( FormField.Type.list_single );
        keyFF.setLabel( "Sort results by" );
        keyFF.setRequired( false );
        keyFF.addOption( "Number of online users", Key.nusers.name() );
        keyFF.addOption( "Address", Key.address.name() );
        keyFF.addValue( Key.address.name() );

        final Element result = DocumentHelper.createElement( QName.get( REQUEST_ELEMENT_NAME, NAMESPACE ) );
        result.add( searchForm.getElement() );
        return result;
    }

    /**
     * Constructs an answer on a IQ stanza that contains a search request. The
     * answer will be an IQ stanza of type 'result' or 'error'.
     *
     * @param iq The IQ stanza that is the search request.
     * @return An answer to the provided request.
     */
    public IQ handleIQ( IQ iq )
    {
        Log.trace( "Received request: {}", iq );

        final IQ reply = IQ.createResultIQ( iq );
        if ( !PROPERTY_ENABLED.getValue() )
        {
            Log.debug( "Unable to process request: service has been disabled by configuration." );
            reply.setError( Condition.service_unavailable );
            return reply;
        }

        final Element formElement = iq.getChildElement().element( QName.get( "x", "jabber:x:data" ) );
        if ( formElement == null )
        {
            Log.debug( "Responding with a fresh search form." );
            reply.setChildElement( getDataElement() );
            return reply;
        }

        // Parse params from request.
        final SearchParameters params;
        try
        {
            params = parseSearchParameters( formElement );
        }
        catch ( IllegalArgumentException | ParseException e )
        {
            Log.debug( "Unable to parse search parameters from request.", e );
            reply.setError( Condition.bad_request );
            return reply;
        }

        // Search for chatrooms matching the request params.
        List<MUCRoom> mucs = searchForChatrooms( params );

        switch ( params.getKey() )
        {
            case nusers:
                mucs = sortByUserAmount( mucs );
                break;

            case address:
                mucs = sortByAddress( mucs );
                break;
        }

        final ResultSet<MUCRoom> searchResults = new ResultSetImpl<>( mucs );

        // See if the requesting entity would like to apply 'result set management'
        final Element set = iq.getChildElement().element( QName.get( "set", ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT ) );
        final List<MUCRoom> mucrsm;

        // apply RSM only if the element exists, and the (total) results set is not empty.
        final boolean applyRSM = set != null && !mucs.isEmpty();

        if ( applyRSM )
        {
            Log.trace( "Applying RSM" );
            if ( !ResultSet.isValidRSMRequest( set ) )
            {
                reply.setError( Condition.bad_request );
                return reply;
            }

            try
            {
                mucrsm = searchResults.applyRSMDirectives( set );
            }
            catch ( NullPointerException e )
            {
                final IQ itemNotFound = IQ.createResultIQ( iq );
                itemNotFound.setError( Condition.item_not_found );
                return itemNotFound;
            }
        }
        else
        {
            // if no rsm, all found rooms are part of the result.
            Log.trace( "Not applying RSM" );
            mucrsm = new ArrayList<>( searchResults );
        }

        final Element res = generateResultElement( mucrsm );

        if ( applyRSM )
        {
            res.add( searchResults.generateSetElementFromResults( mucrsm ) );
        }

        reply.setChildElement( res );

        return reply;
    }

    static SearchParameters parseSearchParameters( final Element formElement ) throws ParseException, IllegalArgumentException
    {
        Log.debug( "Parsing search parameters from request." );

        final SearchParameters result = new SearchParameters();

        final DataForm df = new DataForm( formElement );
        final FormField qFF = df.getField( VAR_Q );
        if ( qFF != null )
        {
            result.setQ( qFF.getFirstValue() );
        }

        final FormField sinnameFF = df.getField( VAR_SINNAME );
        if ( sinnameFF != null )
        {
            final String b = sinnameFF.getFirstValue();
            if ( b != null )
            {
                result.setSinname( DataForm.parseBoolean( b ) );
            }
        }

        final FormField sinddescriptionff = df.getField( VAR_SINDESCRIPTION );
        if ( sinddescriptionff != null )
        {
            final String b = sinddescriptionff.getFirstValue();
            if ( b != null )
            {
                result.setSindescription( DataForm.parseBoolean( b ) );
            }
        }

        final FormField sinaddrFF = df.getField( VAR_SINDADDR );
        if ( sinaddrFF != null )
        {
            final String b = sinaddrFF.getFirstValue();
            if ( b != null )
            {
                result.setSinaddr( DataForm.parseBoolean( b ) );
            }
        }

        final FormField minUsersFF = df.getField( VAR_MIN_USERS );
        if ( minUsersFF != null )
        {
            final String i = minUsersFF.getFirstValue();
            if ( i != null )
            {
                result.setMinUsers( Integer.parseInt( i ) );
            }
        }

        final FormField keyFF = df.getField( VAR_KEY );
        if ( keyFF != null )
        {
            final String k = keyFF.getFirstValue();
            if ( k != null )
            {
                result.setKey( Key.valueOf( k ) );
            }
        }

        Log.debug( "Parsed: " + result );
        return result;
    }

    protected List<MUCRoom> searchForChatrooms( final SearchParameters params )
    {
        Log.debug( "Searching for rooms based on search parameters." );

        List<MUCRoom> mucs = new ArrayList<>();
        for ( MUCRoom room : mucService.getChatRooms() )
        {
            boolean find = false;

            if ( params.getQ() != null && !params.getQ().isEmpty() )
            {
                final List<String> qs = StringUtils.shellSplit( params.getQ() );

                final String naturalLanguageName = room.getNaturalLanguageName();
                if ( params.isSinname() && naturalLanguageName != null )
                {
                    if ( qs.stream().allMatch( naturalLanguageName::contains ) )
                    {
                        find = true;
                    }
                }

                final String description = room.getDescription();
                if ( !find && params.isSindescription() && description != null )
                {
                    if ( qs.stream().allMatch( description::contains ) )
                    {
                        find = true;
                    }
                }

                final String address = room.getJID().toString();
                if ( !find && params.isSinaddr() && address != null )
                {
                    if ( qs.stream().allMatch( address::contains ) )
                    {
                        find = true;
                    }
                }
            }
            else
            {
                // No search query? Every room matches.
                find = true;
            }

            if ( room.getOccupantsCount() < params.getMinUsers() )
            {
                find = false;
            }

            if ( find && canBeIncludedInResult( room ) )
            {
                mucs.add( room );
            }
        }

        Log.debug( "Search resulted in {} rooms.", mucs.size() );
        return mucs;
    }

    static Element generateResultElement( final List<MUCRoom> rooms )
    {
        Log.debug( "Generating result element." );
        final Element res = DocumentHelper.createElement( QName.get( RESPONSE_ELEMENT_NAME, NAMESPACE ) );
        for ( final MUCRoom room : rooms )
        {
            final Element item = res.addElement( "item" );
            item.addAttribute( "address", room.getJID().toString() );
            if ( room.getNaturalLanguageName() != null && !room.getNaturalLanguageName().isEmpty() )
            {
                item.addElement( "name" ).setText( room.getNaturalLanguageName() );
            }
            if ( room.getDescription() != null && !room.getDescription().isEmpty() )
            {
                item.addElement( "description" ).setText( room.getDescription() );
            }
            // Openfire does not support 'language'
            if ( !room.isLocked() && !room.isMembersOnly() && !room.isPasswordProtected() )
            {
                item.addElement( "is-open" );
            }

            final Element anonymityMode = item.addElement( "anonymity-mode" );
            if ( room.canAnyoneDiscoverJID() )
            {
                anonymityMode.setText( "none" );
            }
            else
            {
                anonymityMode.setText( "semi" );
            }

            item.addElement( "nusers" ).setText( Integer.toString( room.getOccupantsCount() ) );
        }
        return res;
    }

    /**
     * Order the provided list by JID of the MUC
     *
     * @param mucs The unordered list that will be sorted.
     * @return The order list of MUC rooms.
     */
    public static List<MUCRoom> sortByAddress( List<MUCRoom> mucs )
    {
        Log.trace( "Sorting by address." );
        mucs.sort( Comparator.comparing( MUCRoom::getJID ) );
        return mucs;
    }

    /**
     * Sorts the provided list in such a way that the MUC with the most users
     * will be the first one in the list.
     *
     * @param mucs The unordered list that will be sorted.
     * @return The sorted list of MUC rooms.
     */
    public static List<MUCRoom> sortByUserAmount( List<MUCRoom> mucs )
    {
        Log.trace( "Sorting by occupant count." );
        mucs.sort( ( o1, o2 ) -> o2.getOccupantsCount() - o1.getOccupantsCount() );
        return mucs;
    }

    /**
     * Checks if the room may be included in search results. This is almost
     * identical to {@link MultiUserChatServiceImpl#canDiscoverRoom(MUCRoom, org.xmpp.packet.JID)},
     * but that method is private and cannot be re-used here.
     *
     * @param room The room to check
     * @return ''true'' if the room may be included in search results, ''false''
     * otherwise.
     */
    private static boolean canBeIncludedInResult( MUCRoom room )
    {
        // Check if locked rooms may be discovered
        final boolean discoverLocked = MUCPersistenceManager.getBooleanProperty( room.getMUCService().getServiceName(), "discover.locked", true );

        if ( !discoverLocked && room.isLocked() )
        {
            return false;
        }
        return room.isPublicRoom();
    }

    static class SearchParameters
    {
        String q = null;
        boolean sinname = true;
        boolean sindescription = true;
        boolean sinaddr = true;
        int minUsers = 1;
        Key key = Key.address;

        public String getQ()
        {
            return q;
        }

        public void setQ( final String q )
        {
            this.q = q;
        }

        public boolean isSinname()
        {
            return sinname;
        }

        public void setSinname( final boolean sinname )
        {
            this.sinname = sinname;
        }

        public boolean isSindescription()
        {
            return sindescription;
        }

        public void setSindescription( final boolean sindescription )
        {
            this.sindescription = sindescription;
        }

        public boolean isSinaddr()
        {
            return sinaddr;
        }

        public void setSinaddr( final boolean sinaddr )
        {
            this.sinaddr = sinaddr;
        }

        public int getMinUsers()
        {
            return minUsers;
        }

        public void setMinUsers( final int minUsers )
        {
            this.minUsers = minUsers;
        }

        public Key getKey()
        {
            return key;
        }

        public void setKey( final Key key )
        {
            this.key = key;
        }

        @Override
        public String toString()
        {
            return "SearchParameters{" +
                "q='" + q + '\'' +
                ", sinname=" + sinname +
                ", sindescription=" + sindescription +
                ", sinaddr=" + sinaddr +
                ", minUsers=" + minUsers +
                ", key=" + key +
                '}';
        }
    }
}
