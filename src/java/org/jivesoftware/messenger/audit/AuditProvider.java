/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.audit;

import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.SessionResultFilter;
import org.jivesoftware.messenger.StreamID;
import java.util.Date;
import java.util.Iterator;

public interface AuditProvider {

    StreamID createSession();

    void updateSession(Session session);

    int getSessionCount();

    Iterator getSessions(SessionResultFilter filter);

    void addEvent(AuditEvent event);

    int getEventCount();

    Iterator getEvents(EventResultFilter filter);

    long createChat(Message packet);

    void updateChat(long chatID, Message packet);

    int getChatCount();

    Iterator getChats(ChatResultFilter filter);

    void archive(Date date);

    void archive(Date startDate, Date stopDate);

    void delete(Date date);
}