/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;

/**
 * <p>Allows the simple creation, reading, and updating of message packets.<p>
 * <p>The methods used are mainly convenience interfaces to the various parts
 * of a typical message packet.</p>
 *
 * @author Iain Shigeoka
 */
public interface Message extends XMPPPacket {
    /**
     * Normal message displayed in an email like interface.
     */
    Type NORMAL = new Type("normal");
    /**
     * Chat message displayed in a line-by-line chat interface.
     */
    Type CHAT = new Type("chat");
    /**
     * Groupchat message displayed in chatroom line-by-line interface.
     */
    Type GROUP_CHAT = new Type("groupchat");
    /**
     * Headline message displayed either as an alert, scrolling stock ticker, etc interface.
     */
    Type HEADLINE = new Type("headline");

    String getBody();

    void setBody(String body);

    String getSubject();

    void setSubject(String subject);

    String getThread();

    void setThread(String thread);
}