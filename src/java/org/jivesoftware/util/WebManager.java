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

import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ServiceLookup;
import org.jivesoftware.messenger.container.ServiceLookupFactory;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.RosterManager;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.PrivateStore;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.XMPPServerInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URLEncoder;

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
        AuthToken authToken = (AuthToken)session.getAttribute("jive.admin.authToken");
        if (authToken == null) {
            showLogin();
        }
        return authToken;
    }

    /**
     * Returns <tt>true</tt> if the Messenger container is in setup mode, <tt>false</tt> otherwise.
     */
    public boolean isSetupMode() {
        return getContainer().isSetupMode();
    }

    /**
     * Returns an instnace of the ServiceLookup.
     */
    public ServiceLookup getServiceLookup() {
        try {
            return ServiceLookupFactory.getLookup();
        }
        catch (UnauthorizedException ex) {
            return null;
        }
    }

    /**
     * Returns the server's container.
     */
    public Container getContainer() {
        return (Container)getServiceLookup().lookup(Container.class);
    }

    /**
     * Returns the XMPP server object -- can get many config items from here.
     */
    public XMPPServer getXMPPServer() {
        final XMPPServer xmppServer = (XMPPServer)getServiceLookup().lookup(XMPPServer.class);
        if (xmppServer == null) {
            // Show that the server is down
            showServerDown();
            return null;
        }
        return xmppServer;
    }

    public UserManager getUserManager() {
        return (UserManager)getServiceLookup().lookup(UserManager.class);
    }

    public RosterManager getRosterManager() {
        return (RosterManager)getServiceLookup().lookup(RosterManager.class);
    }

    public PrivateStore getPrivateStore() {
        return (PrivateStore)getServiceLookup().lookup(PrivateStore.class);
    }

    public PresenceManager getPresenceManager() {
        return (PresenceManager)getServiceLookup().lookup(PresenceManager.class);
    }

    public SessionManager getSessionManager() {
        return (SessionManager)getServiceLookup().lookup(SessionManager.class);
    }

    public MultiUserChatServer getMultiUserChatServer() {
        return (MultiUserChatServer)getServiceLookup().lookup(MultiUserChatServer.class);
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
        catch (UserNotFoundException ex) {
            Log.error(ex);
        }
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
    public void restart(Container container) {
        try {
            container.restart();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }

    /**
     * Stops the container then sleeps for 3 seconds.
     */
    public void stop(Container container) {
        try {
            container.stop();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }

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

    private void showLogin() {
        try {
            response.sendRedirect(getRedirectURL(null));
        }
        catch (Exception ex) {
            ex.printStackTrace();
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

    private String getRedirectURL(String optionalParams) {
        StringBuffer buf = new StringBuffer();
        try {
            StringBuffer rURL = request.getRequestURL();
            int pos = rURL.lastIndexOf("/");
            if ((pos+1) <= rURL.length()) {
                buf.append(rURL.substring(pos+1, rURL.length()));
            }
            String qs = request.getQueryString();
            if (qs != null) {
                buf.append("?").append(qs);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        try {
            String url= "login.jsp?url=" + URLEncoder.encode(buf.toString(), "ISO-8859-1")
                    + (optionalParams != null ? "&"+optionalParams : "");
            return url;
        }
        catch (Exception e) {
            Log.error(e);
            return null;
        }
    }
}