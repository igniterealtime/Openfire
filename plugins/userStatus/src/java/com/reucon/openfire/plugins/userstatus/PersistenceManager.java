package com.reucon.openfire.plugins.userstatus;

import org.jivesoftware.openfire.session.Session;

import java.util.Date;

/**
 *
 */
public interface PersistenceManager
{
    void setHistoryDays(int historyDays);

    void setAllOffline();

    void setOnline(Session session);

    void setOffline(Session session, Date logoffDate);

    void setPresence(Session session, String presenceText);

    void deleteOldHistoryEntries();
}
