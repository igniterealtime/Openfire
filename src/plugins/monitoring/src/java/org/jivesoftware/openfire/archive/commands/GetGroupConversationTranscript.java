/**

 * $RCSfile$
 * $Revision$
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.archive.commands;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.*;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Command that allows to retrieve PDF content of group chat transcripts.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class GetGroupConversationTranscript extends AdHocCommand {

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Requesting PDF of conversation transcript");
        form.addInstruction("Fill out this form to request the conversation transcript in PDF format.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("JID of the user that participated in the chat");
        field.setVariable("participant");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("JID of the room");
        field.setVariable("room");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Time when the chat took place");
        field.setVariable("time");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel("Include PDF");
        field.setVariable("includePDF");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Get handle on the Monitoring plugin
        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager()
                .getPlugin("monitoring");
        ConversationManager conversationManager =
                (ConversationManager) plugin.getModule(ConversationManager.class);
        if (!conversationManager.isArchivingEnabled()) {
            note.addAttribute("type", "error");
            note.setText("Message archiving is not enabled.");

            DataForm form = new DataForm(DataForm.Type.result);

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setLabel("Conversation Found?");
            field.setVariable("found");
            field.addValue(false);
            // Add form to reply
            command.add(form.getElement());

            return;
        }

        try {
            JID participant = new JID(data.getData().get("participant").get(0));
            JID room = new JID(data.getData().get("room").get(0));
            Date time = DataForm.parseDate(data.getData().get("time").get(0));
            boolean includePDF = DataForm.parseBoolean(data.getData().get("includePDF").get(0));

            // Get archive searcher module
            ArchiveSearcher archiveSearcher = (ArchiveSearcher) plugin.getModule(ArchiveSearcher.class);

            ArchiveSearch search = new ArchiveSearch();
            search.setParticipants(participant);
            search.setIncludeTimestamp(time);
            search.setRoom(room);

            Collection<Conversation> conversations = archiveSearcher.search(search);

            DataForm form = new DataForm(DataForm.Type.result);

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setLabel("Conversation Found?");
            field.setVariable("found");
            field.addValue(!conversations.isEmpty());

            if (includePDF) {
                ByteArrayOutputStream stream = null;
                if (!conversations.isEmpty()) {
                    stream = new ConversationUtils().getConversationPDF(conversations.iterator().next());
                }

                if (stream != null) {
                    field = form.addField();
                    field.setLabel("PDF");
                    field.setVariable("pdf");
                    field.addValue(StringUtils.encodeBase64(stream.toByteArray()));
                }
            }

            // Add form to reply
            command.add(form.getElement());
        }
        catch (Exception e) {
            Log.error("Error occurred while running the command", e);
            note.addAttribute("type", "error");
            note.setText("Error while processing the command.");
        }
    }

    public String getCode() {
        return "http://jivesoftware.com/protocol/workgroup#get-group-conv-transcript";
    }

    public String getDefaultLabel() {
        return "Get Group Conversation Transcript";
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    /**
     * Returns if the requester can access this command. Admins and components are allowed to
     * execute this command.
     *
     * @param requester the JID of the entity requesting to execute this command.
     * @return true if the requester can access this command.
     */
    public boolean hasPermission(JID requester) {
        InternalComponentManager componentManager =
                (InternalComponentManager) ComponentManagerFactory.getComponentManager();
        return super.hasPermission(requester) || componentManager.hasComponent(requester);
    }
}
