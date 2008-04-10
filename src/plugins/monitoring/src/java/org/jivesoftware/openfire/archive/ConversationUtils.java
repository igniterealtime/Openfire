/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.archive;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Utility class for asynchronous web calls for archiving tasks.
 *
 * @author Derek DeMoro
 */
public class ConversationUtils {

    /**
     * Returns the status of the rebuilding of the messaging/metadata archives. This is done
     * asynchronously.
     *
     * @return the status the rebuilding (0 - 100) where 100 is complete.
     */
    public int getBuildProgress() {
        // Get handle on the Monitoring plugin
        MonitoringPlugin plugin =
            (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
                "monitoring");

        ArchiveIndexer archiveIndexer = (ArchiveIndexer)plugin.getModule(ArchiveIndexer.class);

        Future<Integer> future = archiveIndexer.getIndexRebuildProgress();
        if (future != null) {
            try {
                return future.get();
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        return -1;
    }

    public ConversationInfo getConversationInfo(long conversationID, boolean formatParticipants) {
        // Create ConversationInfo bean
        ConversationInfo info = new ConversationInfo();

        // Get handle on the Monitoring plugin
        MonitoringPlugin plugin =
            (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
                "monitoring");

        ConversationManager conversationmanager =
            (ConversationManager)plugin.getModule(ConversationManager.class);

        try {
            Conversation conversation = conversationmanager.getConversation(conversationID);
            info = toConversationInfo(conversation, formatParticipants);
        }
        catch (NotFoundException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }

        return info;
    }


    /**
     * Retrieves all the existing conversations from the system.
     *
     * @return a Map of ConversationInfo objects.
     */
    public Map<String, ConversationInfo> getConversations(boolean formatParticipants) {
        Map<String, ConversationInfo> cons = new HashMap<String, ConversationInfo>();
        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager()
            .getPlugin("monitoring");
        ConversationManager conversationManager =
            (ConversationManager)plugin.getModule(ConversationManager.class);
        Collection<Conversation> conversations = conversationManager.getConversations();
        List<Conversation> lConversations =
            Arrays.asList(conversations.toArray(new Conversation[conversations.size()]));
        for (Iterator<Conversation> i = lConversations.iterator(); i.hasNext();) {
            Conversation con = i.next();
            ConversationInfo info = toConversationInfo(con, formatParticipants);
            cons.put(Long.toString(con.getConversationID()), info);
        }
        return cons;
    }

    public ByteArrayOutputStream getConversationPDF(Conversation conversation) {
        Font red = FontFactory
            .getFont(FontFactory.HELVETICA, 12f, Font.BOLD, new Color(0xFF, 0x00, 0x00));
        Font blue = FontFactory
            .getFont(FontFactory.HELVETICA, 12f, Font.ITALIC, new Color(0x00, 0x00, 0xFF));
        Font black = FontFactory.getFont(FontFactory.HELVETICA, 12f, Font.BOLD, Color.BLACK);

        Map<String, Font> colorMap = new HashMap<String, Font>();
        if (conversation != null) {
            Collection<JID> set = conversation.getParticipants();
            int count = 0;
            for (JID jid : set) {
                if (conversation.getRoom() == null) {
                    if (count == 0) {
                        colorMap.put(jid.toString(), blue);
                    }
                    else {
                        colorMap.put(jid.toString(), red);
                    }
                    count++;
                }
                else {
                    colorMap.put(jid.toString(), black);
                }
            }
        }


        return buildPDFContent(conversation, colorMap);
    }

    private ByteArrayOutputStream buildPDFContent(Conversation conversation,
                                                  Map<String, Font> colorMap) {
        Font roomEvent = FontFactory
            .getFont(FontFactory.HELVETICA, 12f, Font.ITALIC, new Color(0xFF, 0x00, 0xFF));

        try {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PDFEventListener());
            document.open();


            Paragraph p = new Paragraph(
                LocaleUtils.getLocalizedString("archive.search.pdf.title", "monitoring"),
                FontFactory.getFont(FontFactory.HELVETICA,
                    18, Font.BOLD));
            document.add(p);
            document.add(Chunk.NEWLINE);

            ConversationInfo coninfo = new ConversationUtils()
                .getConversationInfo(conversation.getConversationID(), false);

            String participantsDetail;
            if (coninfo.getAllParticipants() == null) {
                participantsDetail = coninfo.getParticipant1() + ", " + coninfo.getParticipant2();
            }
            else {
                participantsDetail = String.valueOf(coninfo.getAllParticipants().length);
            }

            Paragraph chapterTitle = new Paragraph(
                LocaleUtils
                    .getLocalizedString("archive.search.pdf.participants", "monitoring") +
                    " " + participantsDetail,
                FontFactory.getFont(FontFactory.HELVETICA, 12,
                    Font.BOLD));

            document.add(chapterTitle);


            Paragraph startDate = new Paragraph(
                LocaleUtils.getLocalizedString("archive.search.pdf.startdate", "monitoring") +
                    " " +
                    coninfo.getDate(),
                FontFactory.getFont(FontFactory.HELVETICA, 12,
                    Font.BOLD));
            document.add(startDate);


            Paragraph duration = new Paragraph(
                LocaleUtils.getLocalizedString("archive.search.pdf.duration", "monitoring") +
                    " " +
                    coninfo.getDuration(),
                FontFactory.getFont(FontFactory.HELVETICA, 12,
                    Font.BOLD));
            document.add(duration);


            Paragraph messageCount = new Paragraph(
                LocaleUtils
                    .getLocalizedString("archive.search.pdf.messagecount", "monitoring") +
                    " " +
                    conversation.getMessageCount(),
                FontFactory.getFont(FontFactory.HELVETICA, 12,
                    Font.BOLD));
            document.add(messageCount);
            document.add(Chunk.NEWLINE);


            Paragraph messageParagraph;

            for (ArchivedMessage message : conversation.getMessages()) {
                String time = JiveGlobals.formatTime(message.getSentDate());
                String from = message.getFromJID().getNode();
                if (conversation.getRoom() != null) {
                    from = message.getToJID().getResource();
                }
                String body = message.getBody();
                String prefix;
                if (!message.isRoomEvent()) {
                    prefix = "[" + time + "] " + from + ":  ";
                    Font font = colorMap.get(message.getFromJID().toString());
                    if (font == null) {
                        font = colorMap.get(message.getFromJID().toBareJID());
                    }
                    if (font == null) {
                        font = FontFactory.getFont(FontFactory.HELVETICA, 12f, Font.BOLD, Color.BLACK);
                    }
                    messageParagraph = new Paragraph(new Chunk(prefix, font));
                }
                else {
                    prefix = "[" + time + "] ";
                    messageParagraph = new Paragraph(new Chunk(prefix, roomEvent));
                }
                messageParagraph.add(body);
                messageParagraph.add(" ");
                document.add(messageParagraph);
            }

            document.close();
            return baos;
        }
        catch (DocumentException e) {
            Log.error("error creating PDF document: " + e.getMessage(), e);
            return null;
        }
    }

    private ConversationInfo toConversationInfo(Conversation conversation,
                                                boolean formatParticipants) {
        final ConversationInfo info = new ConversationInfo();
        // Set participants
        Collection<JID> col = conversation.getParticipants();

        if (conversation.getRoom() == null) {
            JID user1 = (JID)col.toArray()[0];
            info.setParticipant1(formatJID(formatParticipants, user1));
            JID user2 = (JID)col.toArray()[1];
            info.setParticipant2(formatJID(formatParticipants, user2));
        }
        else {
            info.setConversationID(conversation.getConversationID());
            JID[] occupants = col.toArray(new JID[col.size()]);
            String[] jids = new String[col.size()];
            for (int i = 0; i < occupants.length; i++) {
                jids[i] = formatJID(formatParticipants, occupants[i]);
            }
            info.setAllParticipants(jids);
        }

        Map<String, String> cssLabels = new HashMap<String, String>();
        int count = 0;
        for (JID jid : col) {
            if (!cssLabels.containsKey(jid.toString())) {
                if (conversation.getRoom() == null) {
                    if (count % 2 == 0) {
                        cssLabels.put(jid.toBareJID(), "conversation-label2");
                    }
                    else {
                        cssLabels.put(jid.toBareJID(), "conversation-label1");
                    }
                    count++;
                }
                else {
                    cssLabels.put(jid.toString(), "conversation-label4");
                }
            }
        }

        // Set date
        info.setDate(JiveGlobals.formatDateTime(conversation.getStartDate()));
        info.setLastActivity(JiveGlobals.formatTime(conversation.getLastActivity()));
        // Create body.
        final StringBuilder builder = new StringBuilder();
        builder.append("<table width=100%>");
        for (ArchivedMessage message : conversation.getMessages()) {
            String time = JiveGlobals.formatTime(message.getSentDate());
            String from = message.getFromJID().getNode();
            if (conversation.getRoom() != null) {
                from = message.getToJID().getResource();
            }
            String cssLabel = cssLabels.get(message.getFromJID().toBareJID());
            String body = message.getBody();
            builder.append("<tr valign=top>");
            if (!message.isRoomEvent()) {
                builder.append("<td width=1% nowrap class=" + cssLabel + ">").append("[")
                    .append(time).append("]").append("</td>");
                builder.append("<td width=1% class=" + cssLabel + ">").append(from).append(": ")
                    .append("</td>");
                builder.append("<td class=conversation-body>").append(body).append("</td");
            }
            else {
                builder.append("<td width=1% nowrap class=conversation-label3>").append("[")
                    .append(time).append("]").append("</td>");
                builder.append("<td colspan=2 class=conversation-label3><i>").append(body)
                    .append("</i></td");
            }
            builder.append("</tr>");
        }

        if (conversation.getMessages().size() == 0) {
            builder.append("<span class=small-description>" +
                LocaleUtils.getLocalizedString("archive.search.results.archive_disabled",
                    "monitoring") +
                "</a>");
        }

        info.setBody(builder.toString());

        // Set message count
        info.setMessageCount(conversation.getMessageCount());

        long duration =
            (conversation.getLastActivity().getTime() - conversation.getStartDate().getTime());
        info.setDuration(duration);

        return info;
    }

    private String formatJID(boolean html, JID jid) {
        String formattedJID;
        if (html) {
            UserManager userManager = UserManager.getInstance();
            if (XMPPServer.getInstance().isLocal(jid) &&
                userManager.isRegisteredUser(jid.getNode())) {
                formattedJID = "<a href='/user-properties.jsp?username=" +
                    jid.getNode() + "'>" + jid.toBareJID() + "</a>";
            }
            else {
                formattedJID = jid.toBareJID();
            }
        }
        else {
            formattedJID = jid.toBareJID();
        }
        return formattedJID;
    }

    class PDFEventListener extends PdfPageEventHelper {

        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            try {
                cb.setColorStroke(new Color(156, 156, 156));
                cb.setLineWidth(2);
                cb.moveTo(document.leftMargin(), document.bottomMargin() - 5);
                cb.lineTo(document.getPageSize().width() - document.rightMargin(),
                    document.bottomMargin() - 5);
                cb.stroke();

                ClassLoader classLoader = ConversationUtils.class.getClassLoader();
                Enumeration<URL> providerEnum = classLoader.getResources("images/pdf_generatedbyof.gif");
                while (providerEnum.hasMoreElements()) {
                    Image gif = Image.getInstance(providerEnum.nextElement());
                    cb.addImage(gif, 221, 0, 0, 28, (int)document.leftMargin(),
                        (int)document.bottomMargin() - 35);
                }

            }
            catch (Exception e) {
                Log.error("error drawing PDF footer: " + e.getMessage());
            }
            cb.saveState();

        }
    }
}
