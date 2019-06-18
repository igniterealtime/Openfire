/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.resultsetmanagement.ResultSet;
import org.xmpp.resultsetmanagement.ResultSetImpl;

import java.text.ParseException;
import java.util.*;

/**
 * This class adds jabber:iq:search combined with 'result set management'
 * functionality to the MUC service of Openfire.
 * 
 * @author Guus der Kinderen - Nimbuzz B.V. &lt;guus@nimbuzz.com&gt;
 * @author Giancarlo Frison - Nimbuzz B.V. &lt;giancarlo@nimbuzz.com&gt;
 */
public class IQMUCSearchHandler
{
    public static final String JABBER_IQ_SEARCH = "jabber:iq:search";
    public static final String SUBJECT = "subject";
    public static final String NUM_USERS = "num_users";
    public static final String NUM_MAX_USERS = "num_max_users";
    /**
     * The MUC-server to extend with jabber:iq:search functionality.
     */
    private final MultiUserChatService mucService;

    /**
     * Creates a new instance of the search provider.
     * 
     * @param mucService
     *            The server for which to return search results.
     */
    public IQMUCSearchHandler(MultiUserChatService mucService)
    {
        this.mucService = mucService;
    }

    /**
     * Utility method that returns a 'jabber:iq:search' child element filled
     * with a blank dataform.
     * 
     * @return Element, named 'query', escaped by the 'jabber:iq:search'
     *         namespace, filled with a blank dataform.
     */
    private static Element getDataElement()
    {
        final DataForm searchForm = new DataForm(DataForm.Type.form);
        searchForm.setTitle("Chat Rooms Search");
        searchForm.addInstruction("Instructions");

        final FormField typeFF = searchForm.addField();
        typeFF.setVariable("FORM_TYPE");
        typeFF.setType(FormField.Type.hidden);
        typeFF.addValue(JABBER_IQ_SEARCH);

        final FormField nameFF = searchForm.addField();
        nameFF.setVariable("name");
        nameFF.setType(FormField.Type.text_single);
        nameFF.setLabel("Name");
        nameFF.setRequired(false);

        final FormField matchFF = searchForm.addField();
        matchFF.setVariable("name_is_exact_match");
        matchFF.setType(FormField.Type.boolean_type);
        matchFF.setLabel("Name must match exactly");
        matchFF.setRequired(false);

        final FormField subjectFF = searchForm.addField();
        subjectFF.setVariable(SUBJECT);
        subjectFF.setType(FormField.Type.text_single);
        subjectFF.setLabel("Subject");
        subjectFF.setRequired(false);

        final FormField userAmountFF = searchForm.addField();
        userAmountFF.setVariable(NUM_USERS);
        userAmountFF.setType(FormField.Type.text_single);
        userAmountFF.setLabel("Number of users");
        userAmountFF.setRequired(false);

        final FormField maxUsersFF = searchForm.addField();
        maxUsersFF.setVariable(NUM_MAX_USERS);
        maxUsersFF.setType(FormField.Type.text_single);
        maxUsersFF.setLabel("Max number allowed of users");
        maxUsersFF.setRequired(false);

        final FormField includePasswordProtectedFF = searchForm.addField();
        includePasswordProtectedFF.setVariable("include_password_protected");
        includePasswordProtectedFF.setType(FormField.Type.boolean_type);
        includePasswordProtectedFF.setLabel("Include password protected rooms");
        includePasswordProtectedFF.setRequired(false);

        final Element probeResult = DocumentHelper.createElement(QName.get(
            "query", JABBER_IQ_SEARCH));
        probeResult.add(searchForm.getElement());
        return probeResult;
    }

    /**
     * Constructs an answer on a IQ stanza that contains a search request. The
     * answer will be an IQ stanza of type 'result' or 'error'.
     * 
     * @param iq
     *            The IQ stanza that is the search request.
     * @return An answer to the provided request.
     */
    public IQ handleIQ(IQ iq)
    {
        final IQ reply = IQ.createResultIQ(iq);
        final Element formElement = iq.getChildElement().element(
            QName.get("x", "jabber:x:data"));
        if (formElement == null)
        {
            reply.setChildElement(getDataElement());
            return reply;
        }

        // parse params from request.
        final DataForm df = new DataForm(formElement);
        boolean nameIsExactMatch = false;
        String subject = null;
        int numUsers = -1;
        int numMaxUsers = -1;
        boolean includePasswordProtectedRooms = true;

        final Set<String> names = new HashSet<>();
        for (final FormField field : df.getFields()) 
        {
            if (field.getVariable().equals("name"))
            {
                names.add(field.getFirstValue());
            }
        }

        final FormField matchFF = df.getField("nameIsExactMatch");
        if (matchFF != null)
        {
            final String b = matchFF.getFirstValue();
            if (b != null)
            {
                nameIsExactMatch = b.equals("1")
                        || b.equalsIgnoreCase("true")
                        || b.equalsIgnoreCase("yes");
            }
        }

        final FormField subjectFF = df.getField(SUBJECT);
        if (subjectFF != null)
        {
            subject = subjectFF.getFirstValue();
        }

        try
        {
            final FormField userAmountFF = df.getField(NUM_USERS);
            if (userAmountFF != null)
            {
                String value = userAmountFF.getFirstValue();
                if (value != null && !"".equals(value)) {
                    numUsers = Integer.parseInt(value);
                }
            }

            final FormField maxUsersFF = df.getField(NUM_MAX_USERS);
            if (maxUsersFF != null)
            {
                String value = maxUsersFF.getFirstValue();
                if (value != null && !"".equals(value)) {
                    numMaxUsers = Integer.parseInt(value);
                }
            }
        }
        catch (NumberFormatException e)
        {
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }

        final FormField includePasswordProtectedRoomsFF = df.getField("include_password_protected");
        if (includePasswordProtectedRoomsFF != null)
        {
            final String b = includePasswordProtectedRoomsFF.getFirstValue();
            if (b != null)
            {
                try {
                    includePasswordProtectedRooms = DataForm.parseBoolean( b );
                } catch ( ParseException e ) {
                    reply.setError(PacketError.Condition.bad_request);
                    return reply;
                }
            }
        }

        // search for chatrooms matching the request params.
        final List<MUCRoom> mucs = new ArrayList<>();
        for (MUCRoom room : mucService.getChatRooms())
        {
            boolean find = false;

            if (!names.isEmpty())
            {
                for (final String name : names)
                {
                    if (nameIsExactMatch)
                    {
                        if (name.equalsIgnoreCase(room.getNaturalLanguageName()))
                        {
                            find = true;
                            break;
                        }
                    }
                    else
                    {
                        if (room.getNaturalLanguageName().toLowerCase().indexOf(
                            name.toLowerCase()) != -1)
                        {
                            find = true;
                            break;
                        }
                    }
                }
            }

            if (subject != null
                    && room.getSubject().toLowerCase().indexOf(
                        subject.toLowerCase()) != -1)
            {
                find = true;
            }

            if (numUsers > -1 && room.getParticipants().size() < numUsers)
            {
                find = false;
            }

            if (numMaxUsers > -1 && room.getMaxUsers() < numMaxUsers)
            {
                find = false;
            }

            if (!includePasswordProtectedRooms && room.isPasswordProtected())
            {
                find = false;
            }

            if (find && canBeIncludedInResult(room))
            {
                mucs.add(room);
            }
        }

        final ResultSet<MUCRoom> searchResults = new ResultSetImpl<>(
            sortByUserAmount(mucs));

        // See if the requesting entity would like to apply 'result set
        // management'
        final Element set = iq.getChildElement().element(
            QName.get("set", ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT));
        final List<MUCRoom> mucrsm;

        // apply RSM only if the element exists, and the (total) results
        // set is not empty.
        final boolean applyRSM = set != null && !mucs.isEmpty();

        if (applyRSM)
        {
            if (!ResultSet.isValidRSMRequest(set))
            {
                reply.setError(Condition.bad_request);
                return reply;
            }

            try
            {
                mucrsm = searchResults.applyRSMDirectives(set);
            }
            catch (NullPointerException e)
            {
                final IQ itemNotFound = IQ.createResultIQ(iq);
                itemNotFound.setError(Condition.item_not_found);
                return itemNotFound;
            }
        }
        else
        {
            // if no rsm, all found rooms are part of the result.
            mucrsm = new ArrayList<>(searchResults);
        }

        final Element res = DocumentHelper.createElement(QName.get("query",
            JABBER_IQ_SEARCH));

        final DataForm resultform = new DataForm(DataForm.Type.result);
        boolean atLeastoneResult = false;
        for (MUCRoom room : mucrsm)
        {
            final Map<String, Object> fields = new HashMap<>();
            fields.put("name", room.getNaturalLanguageName());
            fields.put(SUBJECT, room.getSubject());
            fields.put(NUM_USERS, room.getOccupantsCount());
            fields.put(NUM_MAX_USERS, determineMaxUsersDisplay(room.getMaxUsers()));
            fields.put("is_password_protected", room.isPasswordProtected());
            fields.put("is_member_only", room.isMembersOnly());
            fields.put("jid", room.getRole().getRoleAddress().toString());
            resultform.addItemFields(fields);
            atLeastoneResult = true;
        }
        if (atLeastoneResult)
        {
            resultform.addReportedField("name", "Name", FormField.Type.text_single);
            resultform.addReportedField(SUBJECT, "Subject", FormField.Type.text_single);
            resultform.addReportedField(NUM_USERS, "Number of users", FormField.Type.text_single);
            resultform.addReportedField(NUM_MAX_USERS, "Max number allowed of users", FormField.Type.text_single);
            resultform.addReportedField("is_password_protected", "Is a password protected room.", FormField.Type.boolean_type);
            resultform.addReportedField("is_member_only", "Is a member only room.", FormField.Type.boolean_type);
            resultform.addReportedField("jid", "JID", FormField.Type.jid_single);
        }
                res.add(resultform.getElement());
        if (applyRSM)
        {
            res.add(searchResults.generateSetElementFromResults(mucrsm));
        }

        reply.setChildElement(res);

        return reply;
    }

    private String determineMaxUsersDisplay(int maxUsers) {
        return (maxUsers == 0) ? "unlimited" : String.valueOf(maxUsers);
     }


    /**
     * Sorts the provided list in such a way that the MUC with the most users
     * will be the first one in the list.
     * 
     * @param mucs
     *            The unordered list that will be sorted.
     * @return The sorted list of MUC rooms.
     */
    private static List<MUCRoom> sortByUserAmount(List<MUCRoom> mucs)
    {
        Collections.sort(mucs, new Comparator<MUCRoom>()
        {
            @Override
            public int compare(MUCRoom o1, MUCRoom o2)
            {
                return o2.getOccupantsCount() - o1.getOccupantsCount();
            }
        });

        return mucs;
    }

    /**
     * Checks if the room may be included in search results. This is almost
     * identical to {@link MultiUserChatServiceImpl#canDiscoverRoom(org.jivesoftware.openfire.muc.MUCRoom, org.xmpp.packet.JID)},
     * but that method is private and cannot be re-used here.
     * 
     * @param room
     *            The room to check
     * @return ''true'' if the room may be included in search results, ''false''
     *         otherwise.
     */
    private static boolean canBeIncludedInResult(MUCRoom room)
    {
        // Check if locked rooms may be discovered
        final boolean discoverLocked = MUCPersistenceManager.getBooleanProperty(room.getMUCService().getServiceName(), "discover.locked", true);

        if (!discoverLocked && room.isLocked())
        {
            return false;
        }
        return room.isPublicRoom();
    }
}
