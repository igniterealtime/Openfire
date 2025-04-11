/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.resultsetmanagement.ResultSet;
import org.xmpp.resultsetmanagement.ResultSetImpl;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class adds support for the search functionality for MUC rooms as defined in XEP-0433: Extended Channel Search.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://xmpp.org/extensions/xep-0433.html">XEP-0433: Extended Channel Search</a>
 */
public class IQExtendedChannelSearchHandler
{
    private static final Logger Log = LoggerFactory.getLogger( IQExtendedChannelSearchHandler.class );

    public static final String VAR_ALL = "all";
    public static final String VAR_SINNAME = "sinname";
    public static final String VAR_Q = "q";
    public static final String VAR_SINDESCRIPTION = "sindescription";
    public static final String VAR_SINADDRESS = "sinaddress";
    public static final String VAR_MIN_USERS = "min_users";
    public static final String VAR_KEY = "key";
    public static final String ERRORMESSAGE_UNSUPPORTED_KEY_VALUE = "Unsupported 'key' value";

    public static SystemProperty<Boolean> PROPERTY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.muc.extendedchannelsearch.enabled")
        .setDynamic( true )
        .setDefaultValue( true )
        .build();

    public static final String REQUEST_ELEMENT_NAME = "search";
    public static final String RESPONSE_ELEMENT_NAME = "result";
    public static final String NAMESPACE = "urn:xmpp:channel-search:0:search";
    public static final String SEARCH_FORM_TYPE = "urn:xmpp:channel-search:0:search-params";

    public enum Key
    {
        nusers("{urn:xmpp:channel-search:0:order}nusers"),
        address("{urn:xmpp:channel-search:0:order}address");

        private final String xmlRepresentation;

        Key(String xmlRepresentation)
        {
            this.xmlRepresentation = xmlRepresentation;
        }

        public String getXmlRepresentation()
        {
            return xmlRepresentation;
        }

        public static Key byXmlRepresentation(final String xmlRepresentation) {
            for (final Key key : values()) {
                if (key.xmlRepresentation.equals(xmlRepresentation)) {
                    return key;
                }
            }
            throw new IllegalArgumentException("Not a valid value: " + xmlRepresentation);
        }
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
    public IQExtendedChannelSearchHandler(MultiUserChatService mucService )
    {
        this.mucService = mucService;
    }

    /**
     * Utility method that returns a 'search' child element filled
     * with a blank dataform.
     *
     * @return Element, named 'search', escaped by the 'urn:xmpp:channel-search:0:search' namespace, filled with a blank dataform.
     */
    private static Element getDataElement()
    {
        final DataForm searchForm = new DataForm( DataForm.Type.form );
        searchForm.setTitle( "Chat Rooms Search" );
        searchForm.addInstruction( "Use this form the query for chat rooms on the server. Note that the 'return all' and 'search for' options are mutually exclusive." );

        final FormField typeFF = searchForm.addField();
        typeFF.setVariable( "FORM_TYPE" );
        typeFF.setType( FormField.Type.hidden );
        typeFF.addValue( SEARCH_FORM_TYPE );

        final FormField qAll = searchForm.addField();
        qAll.setVariable( VAR_ALL );
        qAll.setType( FormField.Type.boolean_type );
        qAll.setLabel( "Return all entries" );
        qAll.setRequired( false );

        final FormField qFF = searchForm.addField();
        qFF.setVariable( VAR_Q );
        qFF.setType( FormField.Type.text_single );
        qFF.setLabel( "Search for" );
        qFF.setRequired( false );

        final FormField sinnameFF = searchForm.addField();
        sinnameFF.setVariable( VAR_SINNAME );
        sinnameFF.setType( FormField.Type.boolean_type );
        sinnameFF.setLabel( "Search in name" );
        sinnameFF.setRequired( false );

        final FormField sindescriptionFF = searchForm.addField();
        sindescriptionFF.setVariable( VAR_SINDESCRIPTION );
        sindescriptionFF.setType( FormField.Type.boolean_type );
        sindescriptionFF.setLabel( "Search in description" );
        sindescriptionFF.setRequired( false );

        final FormField sinaddr = searchForm.addField();
        sinaddr.setVariable(VAR_SINADDRESS);
        sinaddr.setType( FormField.Type.boolean_type );
        sinaddr.setLabel( "Search in address" );
        sinaddr.setRequired( false );

        final FormField minUsersFF = searchForm.addField();
        minUsersFF.setVariable( VAR_MIN_USERS );
        minUsersFF.setType( FormField.Type.text_single );
        minUsersFF.setLabel( "Minimum number of users" );
        minUsersFF.setRequired( false );

        final FormField keyFF = searchForm.addField();
        keyFF.setVariable( VAR_KEY );
        keyFF.setType( FormField.Type.list_single );
        keyFF.setLabel( "Sort results by" );
        keyFF.setRequired( false );
        keyFF.addOption( "Number of online users", Key.nusers.getXmlRepresentation() );
        keyFF.addOption( "Address", Key.address.getXmlRepresentation() );
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
            if (e.getMessage().startsWith(ERRORMESSAGE_UNSUPPORTED_KEY_VALUE)) {
                reply.setError(new PacketError(Condition.feature_not_implemented, PacketError.Type.modify, "The sort key that was provided is not recognized."));
                reply.getError().setApplicationCondition("invalid-sort-key", "urn:xmpp:channel-search:0:error");
            } else {
                reply.setError(new PacketError(Condition.bad_request, PacketError.Type.modify, "Unable to successfully parse the search parameters from the provided form."));
            }
            return reply;
        }

        final Set<String> conflictingFields = getConflictingFields(params);
        if (!conflictingFields.isEmpty()) {
            Log.debug("Request from {} contained conflicting fields: {}", iq.getFrom(), String.join(", ", conflictingFields) );
            final PacketError error = new PacketError(Condition.bad_request, PacketError.Type.modify, "These fields cannot be used in the same request: '" + String.join("', '", conflictingFields) + "'.", "en");
            final Element fields = error.getElement().addElement("conflicting-fields", "urn:xmpp:channel-search:0:error");
            for (final String conflictingField : conflictingFields) {
                fields.addElement("var").setText(conflictingField);
            }
            reply.setError(error);
            return reply;
        }

        final Map<String, Set<String>> missingFields = getMissingFields(params);
        if (!missingFields.isEmpty()) {
            Log.debug("Request from {} contained missing fields: {}", iq.getFrom(), String.join(", ", missingFields.keySet()) );
            final PacketError error = new PacketError(Condition.bad_request, PacketError.Type.modify, "Some fields cannot be used without specifying other fields. Please add: '" + String.join("', '", missingFields.keySet())+"' or remove: '" + missingFields.values().stream().flatMap(Collection::stream).collect(Collectors.joining("', '")) + "'.", "en");
            final Element fields = error.getElement().addElement("conflicting-fields", "urn:xmpp:channel-search:0:error");
            for (final Collection<String> dependants : missingFields.values()) {
                for (String dependant : dependants) {
                    fields.addElement("var").setText(dependant);
                }
            }
            reply.setError(error);
            return reply;
        }

        if ((params.getAll() == null || !params.getAll()) && (params.getQ() == null || params.getQ().isEmpty())) {
            Log.debug("Request from {} didn't contain any search conditions.", iq.getFrom());
            final PacketError error = new PacketError(Condition.bad_request, PacketError.Type.cancel, "A search request needs to contain a search condition. Please add either 'q' or 'all'.", "en");
            error.setApplicationCondition("no-search-conditions", "urn:xmpp:channel-search:0:error");
            reply.setError(error);
            return reply;
        }

        // Search for chatrooms matching the request params.
        List<MUCRoomSearchInfo> mucs = searchForChatrooms( params );

        final Key key;
        if (params.getKey() != null ) {
            // Use the value provided by the client.
            key = params.getKey();
        } else {
            // Use the default key defined by the server.
            key = Key.valueOf(new DataForm(getDataElement().element(QName.get(DataForm.ELEMENT_NAME, DataForm.NAMESPACE))).getField(VAR_KEY).getFirstValue());
        }

        switch (key) {
            case nusers:
                mucs = sortByUserAmount(mucs);
                break;

            case address:
                mucs = sortByAddress(mucs);
                break;
        }

        final ResultSet<MUCRoomSearchInfo> searchResults = new ResultSetImpl<>( mucs );

        // See if the requesting entity would like to apply 'result set management'
        final Element set = iq.getChildElement().element( QName.get( "set", ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT ) );
        final List<MUCRoomSearchInfo> mucrsm;

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

        res.add( searchResults.generateSetElementFromResults( mucrsm ) );

        reply.setChildElement( res );

        return reply;
    }

    static SearchParameters parseSearchParameters( final Element formElement ) throws ParseException, IllegalArgumentException
    {
        Log.debug( "Parsing search parameters from request." );

        final SearchParameters result = new SearchParameters();

        final DataForm df = new DataForm( formElement );
        final FormField allFF = df.getField( VAR_ALL );
        if ( allFF != null )
        {
            final String b = allFF.getFirstValue();
            if ( b != null )
            {
                result.setAll( DataForm.parseBoolean( b ) );
            }
        }

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

        final FormField sinaddrFF = df.getField(VAR_SINADDRESS);
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
                try {
                    result.setKey(Key.byXmlRepresentation(k));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(ERRORMESSAGE_UNSUPPORTED_KEY_VALUE + ": " + k, e);
                }
            }
        }

        Log.debug( "Parsed: " + result );
        return result;
    }

    protected List<MUCRoomSearchInfo> searchForChatrooms( final SearchParameters params )
    {
        Log.debug( "Searching for rooms based on search parameters." );

        List<MUCRoomSearchInfo> mucs = new ArrayList<>();
        for ( MUCRoomSearchInfo room : mucService.getAllRoomSearchInfo() )
        {
            boolean find = false;

            if ( params.getAll() != null && params.getAll() ) {
                find = true;
            }
            else if ( params.getQ() != null && !params.getQ().isEmpty() )
            {
                final List<String> qs = StringUtils.shellSplit( params.getQ() );

                final String naturalLanguageName = room.getNaturalLanguageName();
                if ( params.isSinname() != null && params.isSinname() && naturalLanguageName != null )
                {
                    if ( qs.stream().allMatch( naturalLanguageName::contains ) )
                    {
                        find = true;
                    }
                }

                final String description = room.getDescription();
                if ( !find && params.isSindescription() != null && params.isSindescription() && description != null )
                {
                    if ( qs.stream().allMatch( description::contains ) )
                    {
                        find = true;
                    }
                }

                final String address = room.getJID().toString();
                if ( !find && params.isSinaddr() != null && params.isSinaddr() && address != null )
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

            if ( params.getMinUsers() != null && room.getOccupantsCount() < params.getMinUsers() )
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

    static Element generateResultElement( final List<MUCRoomSearchInfo> rooms )
    {
        Log.debug( "Generating result element." );
        final Element res = DocumentHelper.createElement( QName.get( RESPONSE_ELEMENT_NAME, NAMESPACE ) );
        for ( final MUCRoomSearchInfo room : rooms )
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
    public static List<MUCRoomSearchInfo> sortByAddress( List<MUCRoomSearchInfo> mucs )
    {
        Log.trace( "Sorting by address." );
        mucs.sort( Comparator.comparing( MUCRoomSearchInfo::getJID) );
        return mucs;
    }

    /**
     * Sorts the provided list in such a way that the MUC with the most users
     * will be the first one in the list. Rooms with equal amounts of users
     * are ordered by JID.
     *
     * @param mucs The unordered list that will be sorted.
     * @return The sorted list of MUC rooms.
     */
    public static List<MUCRoomSearchInfo> sortByUserAmount( List<MUCRoomSearchInfo> mucs )
    {
        Log.trace( "Sorting by occupant count." );
        mucs.sort( Comparator.comparing(MUCRoomSearchInfo::getOccupantsCount).reversed().thenComparing(MUCRoomSearchInfo::getJID) );
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
    private static boolean canBeIncludedInResult( MUCRoomSearchInfo room )
    {
        // Check if locked rooms may be discovered
        final boolean discoverLocked = MUCPersistenceManager.getBooleanProperty( room.getServiceName(), "discover.locked", true );

        if ( !discoverLocked && room.isLocked() )
        {
            return false;
        }
        return room.isPublicRoom();
    }

    static Set<String> getConflictingFields(final SearchParameters form) {
        final Set<String> result = new HashSet<>();
        if (form.getAll() != null && form.getAll()) {
            if (form.getQ() != null && !form.getQ().isEmpty()) {
                // Mark 'all' and 'q' as conflict (but don't report any fields associated with 'q', to avoid confusion).
                result.add(VAR_ALL);
                result.add(VAR_Q);
            } else {
                // 'all' cannot be combined with fields that require 'q' (even if 'q' itself is not present).
                if (form.isSindescription() != null) {
                    result.add(VAR_ALL);
                    result.add(VAR_SINDESCRIPTION);
                }
                if (form.isSinname() != null) {
                    result.add(VAR_ALL);
                    result.add(VAR_SINNAME);
                }
                if (form.isSinaddr() != null) {
                    result.add(VAR_ALL);
                    result.add(VAR_SINADDRESS);
                }
            }
        }

        return result;
    }

    static Map<String, Set<String>> getMissingFields(final SearchParameters parameters) {
        final Map<String, Set<String>> result = new HashMap<>();

        if (parameters.getQ() == null || parameters.getQ().isEmpty()) {
            final Set<String> dependants = new HashSet<>();
            if (parameters.isSinaddr() != null) {
                dependants.add(VAR_SINADDRESS);
            }
            if (parameters.isSinname() != null) {
                dependants.add(VAR_SINNAME);
            }
            if (parameters.isSindescription() != null) {
                dependants.add(VAR_SINDESCRIPTION);
            }
            if (!dependants.isEmpty()) {
                result.put(VAR_Q, dependants);
            }
        }
        return result;
    }

    static class SearchParameters
    {
        Boolean all = null;
        String q = null;
        Boolean sinname = null;
        Boolean sindescription = null;
        Boolean sinaddr = null;
        Integer minUsers = null;
        Key key = null;

        public Boolean getAll() {
            return all;
        }

        public void setAll( final Boolean all ) {
            this.all = all;
        }

        public String getQ()
        {
            return q;
        }

        public void setQ( final String q )
        {
            this.q = q;
        }

        public Boolean isSinname()
        {
            return sinname;
        }

        public void setSinname( final Boolean sinname )
        {
            this.sinname = sinname;
        }

        public Boolean isSindescription()
        {
            return sindescription;
        }

        public void setSindescription( final Boolean sindescription )
        {
            this.sindescription = sindescription;
        }

        public Boolean isSinaddr()
        {
            return sinaddr;
        }

        public void setSinaddr( final Boolean sinaddr )
        {
            this.sinaddr = sinaddr;
        }

        public Integer getMinUsers()
        {
            return minUsers;
        }

        public void setMinUsers( final Integer minUsers )
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
                "all='" + all + '\'' +
                ", q='" + q + '\'' +
                ", sinname=" + sinname +
                ", sindescription=" + sindescription +
                ", sinaddr=" + sinaddr +
                ", minUsers=" + minUsers +
                ", key=" + key +
                '}';
        }
    }
}
