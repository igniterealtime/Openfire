/**

 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * This class is responsible for handling all the packets sent to the workgroup service whose
 * element name is transcript-search. If no data form is present inside the child element then
 * the a search data form will be returned. Otherwise, the result of the search will be returned
 * to the sender of the search request.
 *
 * @author Gaston Dombiak
 */
public class IQChatSearchHandler {

	private static final Logger Log = LoggerFactory.getLogger(IQChatSearchHandler.class);
	
    private static final String LOAD_META_DATA =
            "SELECT metadataName, metadataValue FROM fpSessionMetadata WHERE sessionID=?";

    private WorkgroupManager workgroupManager;
    private DataForm searchForm;
    private DataForm resultForm;

    public IQChatSearchHandler(WorkgroupManager workgroupManager) {
        this.workgroupManager = workgroupManager;
        init();
    }

    public void handleIQ(IQ packet) {
        try {
            // Check that the sender of this IQ is an agent
            workgroupManager.getAgentManager().getAgent(packet.getFrom());

            Element iq = packet.getChildElement();

            IQ reply = IQ.createResultIQ(packet);

            if (iq.elements().isEmpty()) {
                reply.setChildElement(iq.createCopy());
                // Send the search form to the agent
                reply.addExtension(searchForm.createCopy());
                workgroupManager.send(reply);
            }
            else {
                // Send the result of the search to the agent
                Date startDate = null;
                Date endDate = null;
                Collection<Workgroup> workgroups = WorkgroupManager.getInstance().getWorkgroups();
                JID agentJID = null;
                String queryString = null;

                // Get the search parameters from the completed form
                DataForm submitedForm = (DataForm)packet.getExtension(DataForm.ELEMENT_NAME,
                    DataForm.NAMESPACE);
                for (FormField field : submitedForm.getFields()) {
                    if ("date/start".equals(field.getVariable())) {
                        try {
                            startDate = DataForm.parseDate(field.getValues().get(0));
                        }
                        catch (ParseException e) {
                            Log.debug("Invalid startDate " +
                                field.getValues().get(0), e);
                        }
                    }
                    else if ("date/end".equals(field.getVariable())) {
                        try {
                            endDate = DataForm.parseDate(field.getValues().get(0));
                        }
                        catch (ParseException e) {
                            Log.debug("Invalid endDate " +
                                field.getValues().get(0), e);
                        }
                    }
                    else if ("workgroups".equals(field.getVariable())) {
                        if (!field.getValues().isEmpty()) {
                            workgroups = new ArrayList<Workgroup>();
                            for (String value : field.getValues()) {
                                try {
                                    workgroups.add(
                                        WorkgroupManager.getInstance().getWorkgroup(
                                            new JID(value)));
                                }
                                catch (UserNotFoundException e) {
                                    Log.debug("Invalid workgroup JID " +
                                        value, e);
                                }
                            }
                        }
                        else {
                            // Search in all the workgroups since no one was specified
                            workgroups = WorkgroupManager.getInstance().getWorkgroups();
                        }
                    }
                    else if ("agent".equals(field.getVariable())) {
                        agentJID = new JID(field.getValues().get(0));
                    }
                    else if ("queryString".equals(field.getVariable())) {
                        queryString = field.getValues().get(0);
                    }
                }

                // Build the response
                DataForm searchResults = resultForm.createCopy();
                // Perform the search
                for (Workgroup workgroup : workgroups) {
                    ChatSearch search = new ChatSearch(workgroup, startDate, endDate, agentJID,
                        queryString);
                    for (QueryResult result : search.getResults()) {
                        Map<String, Object> fields = new LinkedHashMap<String, Object>();
                        fields.put("workgroup", result.getWorkgroup().getJID().toBareJID());
                        fields.put("sessionID", result.getSessionID());
                        fields.put("startDate", result.getStartDate());
                        fields.put("agentJIDs", result.getAgentJIDs());
                        fields.put("relevance", result.getRelevance());

                        // Add Metadata
                        Map<String, String> metadata = getMetadataMap(result.getSessionID());
                        if (metadata.containsKey("question")) {
                            fields.put("question", metadata.get("question"));
                        }

                        if (metadata.containsKey("email")) {
                            fields.put("email", metadata.get("email"));
                        }

                        if (metadata.containsKey("username")) {
                            fields.put("username", metadata.get("username"));
                        }

                        searchResults.addItemFields(fields);
                    }
                }
                reply.setChildElement(iq.getName(), iq.getNamespaceURI());
                reply.addExtension(searchResults);
                workgroupManager.send(reply);
            }
        }
        catch (AgentNotFoundException e) {
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.not_authorized));
            workgroupManager.send(reply);
        }
    }

    private void init() {
        // Configure the search form that will be sent to the agents
        this.searchForm = new DataForm(DataForm.Type.form);
        this.searchForm.setTitle("Chat search");
        this.searchForm.addInstruction("Fill out this form to search for chats");
        // Add starting date
        FormField field = this.searchForm.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Starting Date");
        field.setVariable("date/start");
        // Add ending date
        field = this.searchForm.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Ending Date");
        field.setVariable("date/end");
        // Add workgroup JID
        field = this.searchForm.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("Workgroup");
        field.setVariable("workgroups");
        // Add agent JID
        field = this.searchForm.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("Agent");
        field.setVariable("agent");
        // Add query string
        field = this.searchForm.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Search Terms");
        field.setVariable("queryString");
        field.setRequired(true);

        // Configure the form that will hold the search results
        this.resultForm = new DataForm(DataForm.Type.result);
        this.resultForm.addReportedField("workgroup", null, FormField.Type.jid_single);
        this.resultForm.addReportedField("sessionID", null, FormField.Type.text_single);
        this.resultForm.addReportedField("startDate", null, FormField.Type.text_single);
        this.resultForm.addReportedField("agentJIDs", null, FormField.Type.jid_multi);
        this.resultForm.addReportedField("relevance", null, FormField.Type.text_single);
    }

    private Map<String, String> getMetadataMap(String sessionID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Map<String, String> map = new HashMap<String, String>();
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_META_DATA);
            pstmt.setString(1, sessionID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                map.put(name, value);
            }
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return map;
    }
}