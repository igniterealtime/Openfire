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

package org.jivesoftware.util;

import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.roster.RosterManager;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.PrivateStorage;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.XMPPServerInfo;
import org.jivesoftware.messenger.group.GroupManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A utility bean for Messenger admin console pages.
 */
public class WebManager extends WebBean {

    private Map breadcrumbMap = new LinkedHashMap();
    private String title = "";
    private String sidebar = "";

    private int start = 0;
    private int range = 15;

    public WebManager() {
    }

    /**
     * Returns the auth token redirects to the login page if an auth token is not found.
     */
    public AuthToken getAuthToken() {
        return (AuthToken)session.getAttribute("jive.admin.authToken");
    }

    /**
     * Returns <tt>true</tt> if the Messenger container is in setup mode, <tt>false</tt> otherwise.
     */
    public boolean isSetupMode() {
        return getXMPPServer().isSetupMode();
    }

    /**
     * Returns the XMPP server object -- can get many config items from here.
     */
    public XMPPServer getXMPPServer() {
        final XMPPServer xmppServer = XMPPServer.getInstance();
        if (xmppServer == null) {
            // Show that the server is down
            showServerDown();
            return null;
        }
        return xmppServer;
    }

    public UserManager getUserManager() {
        return getXMPPServer().getUserManager();
    }

    public GroupManager getGroupManager() {
        return GroupManager.getInstance();
    }

    public RosterManager getRosterManager() {
        return getXMPPServer().getRosterManager();
    }

    public PrivateStorage getPrivateStore() {
        return getXMPPServer().getPrivateStorage();
    }

    public PresenceManager getPresenceManager() {
        return getXMPPServer().getPresenceManager();
    }

    public SessionManager getSessionManager() {
        return getXMPPServer().getSessionManager();
    }

    public MultiUserChatServer getMultiUserChatServer() {
        return getXMPPServer().getMultiUserChatServer();
    }

    public XMPPServerInfo getServerInfo() {
        return getXMPPServer().getServerInfo();
    }

    /**
     * Returns the page user or <tt>null</tt> if one is not found.
     */
    public User getUser() {
        User pageUser = null;
        try {
            pageUser = getUserManager().getUser(getAuthToken().getUsername());
        }
        catch (Exception ignored) {}
        return pageUser;
    }

    /**
     * Returns <tt>true</tt> if the server is in embedded mode, <tt>false</tt> otherwise.
     */
    public boolean isEmbedded() {
        try {
            ClassUtils.forName("org.jivesoftware.messenger.starter.ServerStarter");
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Restarts the container then sleeps for 3 seconds.
     */
    /*public void restart(XMPPServer server) {
        try {
            server.restart();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }*/

    /**
     * Stops the server then sleeps for 3 seconds.
     */
    /*public void stop(XMPPServer server) {
        try {
            server.stop();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }*/

    public WebManager getManager() {
        return this;
    }

    public void validateService() {
        if (getPresenceManager() == null ||
                getXMPPServer() == null) {
            showServerDown();
        }
    }

    public String getErrorPage() {
        return "error-serverdown.jsp";
    }

    public boolean isServerRunning() {
        if (getPresenceManager() == null ||
                getXMPPServer() == null) {
            return false;
        }
        return true;
    }

    public void addBreadCrumb(String name, String url) {
        breadcrumbMap.put(name, url);
    }

    public Map getBreadCrumbs() {
        return breadcrumbMap;
    }

    public void setSidebar(String sidebar) {
        this.sidebar = sidebar;
    }

    public String getSidebar() {
        return sidebar;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public int getBreadcrumbSize() {
        return getBreadCrumbs().size();
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getStart() {
        return start;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public int getRange() {
        return range;
    }

    public int getCurrentPage() {
        return (start / range) + 1;
    }

    private void sleep() {
        // Sleep for a minute:
        try {
            Thread.sleep(3000L);
        }
        catch (Exception ignored) {
        }
    }

    protected void showServerDown() {
        try {
            response.sendRedirect("error-serverdown.jsp");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}