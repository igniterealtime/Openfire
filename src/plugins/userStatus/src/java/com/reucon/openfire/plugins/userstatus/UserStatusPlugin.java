package com.reucon.openfire.plugins.userstatus;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.PresenceEventDispatcher;
import org.jivesoftware.openfire.user.PresenceEventListener;
import org.jivesoftware.util.*;
import org.xmpp.packet.Presence;
import org.xmpp.packet.JID;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;

/**
 * UserStatus plugin for Openfire.
 */
public class UserStatusPlugin implements Plugin, PropertyEventListener, SessionEventListener, PresenceEventListener, PersistenceManager
{
    public static final String HISTORY_DAYS_PROPERTY = "user-status.historyDays";
    public static final int DEFAULT_HISTORY_DAYS = -1;

    private Collection<PersistenceManager> persistenceManagers;

    public void initializePlugin(PluginManager manager, File pluginDirectory)
    {
        int historyDays = JiveGlobals.getIntProperty(HISTORY_DAYS_PROPERTY, DEFAULT_HISTORY_DAYS);
        PropertyEventDispatcher.addListener(this);

        persistenceManagers = new ArrayList<PersistenceManager>();
        persistenceManagers.add(new DefaultPersistenceManager());

        try
        {
            Class.forName("com.reucon.openfire.phpbb3.PhpBB3AuthProvider");
            persistenceManagers.add(new PhpBB3PersistenceManager());
        }
        catch (ClassNotFoundException e)
        {
            // ignore
        }

        setAllOffline();
        setHistoryDays(historyDays);

        for (ClientSession session : SessionManager.getInstance().getSessions())
        {
            sessionCreated(session);
        }

        SessionEventDispatcher.addListener(this);
        PresenceEventDispatcher.addListener(this);
    }

    public void destroyPlugin()
    {
        PresenceEventDispatcher.removeListener(this);
        SessionEventDispatcher.removeListener(this);
    }

    public void sessionCreated(Session session)
    {
        if (!XMPPServer.getInstance().getUserManager().isRegisteredUser(session.getAddress()))
        {
            return;
        }

        setOnline(session);
    }

    public void sessionDestroyed(Session session)
    {
        if (!XMPPServer.getInstance().getUserManager().isRegisteredUser(session.getAddress()))
        {
            return;
        }

        setOffline(session, new Date());
    }

    public void anonymousSessionCreated(Session session)
    {
        // we are not interested in anonymous sessions
    }

    public void anonymousSessionDestroyed(Session session)
    {
        // we are not interested in anonymous sessions
    }

    public void resourceBound(Session session)
    {
        // not interested
    }

    public void availableSession(ClientSession session, Presence presence)
    {
        updatePresence(session, presence);
    }

    public void unavailableSession(ClientSession session, Presence presence)
    {
        updatePresence(session, presence);
    }

    public void presencePriorityChanged(ClientSession session, Presence presence)
    {
        // we are not interested in priority changes
    }

    public void presenceChanged(ClientSession session, Presence presence)
    {
        updatePresence(session, presence);
    }

    public void subscribedToPresence(JID subscriberJID, JID authorizerJID)
    {
        // we are not interested in subscription updates
    }

    public void unsubscribedToPresence(JID unsubscriberJID, JID recipientJID)
    {
        // we are not interested in subscription updates
    }

    public void propertySet(String property, Map<String, Object> params)
    {
        if (HISTORY_DAYS_PROPERTY.equals(property))
        {
            final Object value = params.get("value");
            if (value != null)
            {
                try
                {
                    setHistoryDays(Integer.valueOf(value.toString()));
                }
                catch (NumberFormatException e)
                {
                    setHistoryDays(DEFAULT_HISTORY_DAYS);
                }
                deleteOldHistoryEntries();
            }
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {
        if (HISTORY_DAYS_PROPERTY.equals(property))
        {
            setHistoryDays(DEFAULT_HISTORY_DAYS);
            deleteOldHistoryEntries();
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params)
    {
        // we don't use xml properties
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params)
    {
        // we don't use xml properties
    }

    private void updatePresence(ClientSession session, Presence presence)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        final String presenceText;

        if (!XMPPServer.getInstance().getUserManager().isRegisteredUser(session.getAddress()))
        {
            return;
        }

        if (Presence.Type.unavailable.equals(presence.getType()))
        {
            presenceText = presence.getType().toString();
        }
        else if (presence.getShow() != null)
        {
            presenceText = presence.getShow().toString();
        }
        else if (presence.isAvailable())
        {
            presenceText = "available";
        }
        else
        {
            return;
        }

        setPresence(session, presenceText);
    }

    // implementation of PersistenceManager

    public void setHistoryDays(int historyDays)
    {
        for (PersistenceManager pm : persistenceManagers)
        {
            pm.setHistoryDays(historyDays);
        }
    }

    public void setAllOffline()
    {
        for (PersistenceManager pm : persistenceManagers)
        {
            pm.setAllOffline();
        }
    }

    public void setOnline(Session session)
    {
        for (PersistenceManager pm : persistenceManagers)
        {
            pm.setOnline(session);
        }
    }

    public void setOffline(Session session, Date logoffDate)
    {
        for (PersistenceManager pm : persistenceManagers)
        {
            pm.setOffline(session, logoffDate);
        }
    }

    public void setPresence(Session session, String presenceText)
    {
        for (PersistenceManager pm : persistenceManagers)
        {
            pm.setPresence(session, presenceText);
        }
    }

    public void deleteOldHistoryEntries()
    {
        for (PersistenceManager pm : persistenceManagers)
        {
            pm.deleteOldHistoryEntries();
        }
    }
}
