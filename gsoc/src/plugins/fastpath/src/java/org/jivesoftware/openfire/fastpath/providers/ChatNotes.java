/**
 * $RCSfile$
 * $Revision: 19303 $
 * $Date: 2005-07-14 10:43:49 -0700 (Thu, 14 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.providers;

import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * ChatNotes is responsible for the setting and retrieval off Chat Notes. Each chat note
 * is tied directly to a chat session within Live Assistant.
 */
public class ChatNotes implements WorkgroupProvider {

    private static final String GET_NOTES = "SELECT notes FROM fpSession WHERE sessionID=?";
    private static final String SET_NOTES = "UPDATE fpSession SET notes=? WHERE sessionID=?";

    /**
     * Updates a note within Live Assistant Database (jlaSession table)
     *
     * @param sessionID the chat sessionID associated with this note.
     * @param note the note itself.
     */
    public void appendNote(String sessionID, String note) {
        Connection con;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            try {
                pstmt = con.prepareStatement(SET_NOTES);
                DbConnectionManager.setLargeTextField(pstmt, 1, note);
                pstmt.setString(2, sessionID);
                pstmt.executeUpdate();
            }
            catch (Exception ex) {
                ComponentManagerFactory.getComponentManager().getLog().error(ex);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
    }

    /**
     * Retrieves a note from the Live Assistant Database. (jlaSession table)
     *
     * @param sessionID the sessionID that the note is mapped to.
     * @return the note found. If no note is found, null will be returned.
     */
    public String getNotes(String sessionID) {
        String notes = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_NOTES);
            pstmt.setString(1, sessionID);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                notes = DbConnectionManager.getLargeTextField(rs, 1);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return notes;
    }

    /**
     * Retrieves note from Fastpath.
     *
     * @param packet the associated IQ Packet.
     * @param workgroup the workgroup the request came from.
     * @param sessionID the sessionID the note is mapped to.
     */
    public void sendNotesPacket(IQ packet, Workgroup workgroup, String sessionID) {
        IQ reply = IQ.createResultIQ(packet);

        // Retrieve the web chat setting.
        String notes = getNotes(sessionID);
        if (notes == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        Element note = reply.setChildElement("chat-notes", "http://jivesoftware.com/protocol/workgroup");
        note.addElement("sessionID").setText(sessionID);
        note.addElement("text").setText(notes);
        workgroup.send(reply);
    }

    public boolean handleGet(IQ packet) {
        return hasChatNotesNames(packet);
    }

    public boolean handleSet(IQ packet) {
        return hasChatNotesNames(packet);
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply;
        Element iq = packet.getChildElement();

        // Verify that an agent is requesting this information.
        try {
            AgentSession agentSession = workgroup.getAgentManager().getAgentSession(packet.getFrom());
            if (agentSession != null) {
                String sessionID = iq.element("sessionID").getTextTrim();

                sendNotesPacket(packet, workgroup, sessionID);
            }
            else {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                workgroup.send(reply);
            }
        }
        catch (AgentNotFoundException e) {
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
        }
    }

    public void executeSet(IQ packet, Workgroup workgroup) {
        IQ reply;
        Element iq = packet.getChildElement();

        try {
            // Verify that an agent is requesting this information.
            AgentSession agentSession = workgroup.getAgentManager().getAgentSession(packet.getFrom());
            if (agentSession != null) {
                String sessionID = iq.element("sessionID").getTextTrim();
                Element notes = iq.element("notes");
                String noteText = notes.getTextTrim();
                appendNote(sessionID, noteText);
                reply = IQ.createResultIQ(packet);
            }
            else {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
            }

        }
        catch (AgentNotFoundException e) {
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
        }
        workgroup.send(reply);
    }


    private boolean hasChatNotesNames(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "chat-notes".equals(name);
    }
}