/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.StringTokenizer;

/**
 * A utility bean for Openfire admin console pages.
 */
public class WebManager extends WebBean {

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
     * Returns <tt>true</tt> if the Openfire container is in setup mode, <tt>false</tt> otherwise.
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

    public LockOutManager getLockOutManager() {
        return LockOutManager.getInstance();
    }

    public SecurityAuditManager getSecurityAuditManager() {
        return SecurityAuditManager.getInstance();
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

    public MultiUserChatManager getMultiUserChatManager() {
        return getXMPPServer().getMultiUserChatManager();
    }

    public XMPPServerInfo getServerInfo() {
        return getXMPPServer().getServerInfo();
    }

    /**
     * Logs a security event as the currently logged in user.  (convenience routine for SecurityAuditManager)
     *
     * @param summary Summary of event.
     * @param details Details of event, can be null if no details available.
     */
    public void logEvent(String summary, String details) {
        SecurityAuditManager.getInstance().logEvent(getUser().getUsername(), summary, details);
    }

    /**
     * Returns the page user or <tt>null</tt> if one is not found.
     */
    public User getUser() {
        User pageUser = null;
        try {
            pageUser = getUserManager().getUser(getAuthToken().getUsername());
        }
        catch (Exception ignored) {
            // Ignore.
        }
        return pageUser;
    }

    /**
     * Returns <tt>true</tt> if the server is in embedded mode, <tt>false</tt> otherwise.
     */
    public boolean isEmbedded() {
        try {
            ClassUtils.forName("org.jivesoftware.openfire.starter.ServerStarter");
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Restarts the server then sleeps for 3 seconds.
     */
    public void restart() {
        try {
            getXMPPServer().restart();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }

    /**
     * Stops the server then sleeps for 3 seconds.
     */
    public void stop() {
        try {
            getXMPPServer().stop();
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

    public boolean isServerRunning() {
        return !(getPresenceManager() == null || getXMPPServer() == null);
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
            // Ignore.
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

    /**
     * Copies the contents at <CODE>src</CODE> to <CODE>dst</CODE>.
     */
    public static void copy(URL src, File dst) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = src.openStream();
            out = new FileOutputStream(dst);
            dst.mkdirs();
            copy(in, out);
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException e) {
                // Ignore.
            }
            try {
                if (out != null) {
                    out.close();
                }
            }
            catch (IOException e) {
                // Ignore.
            }
        }
    }

    /**
     * Common code for copy routines.  By convention, the streams are
     * closed in the same method in which they were opened.  Thus,
     * this method does not close the streams when the copying is done.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        while (true) {
            int bytesRead = in.read(buffer);
            if (bytesRead < 0) {
                break;
            }
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Returns the number of rows per page for the specified page for the current logged user.
     * The rows per page value is stored as a user property. The same property is being used for
     * different pages. The encoding format is the following "pageName1=value,pageName2=value".
     *
     * @param pageName     the name of the page to look up its stored value.
     * @param defaultValue the default value to return if no user value was found.
     * @return the number of rows per page for the specified page for the current logged user.
     */
    public int getRowsPerPage(String pageName, int defaultValue) {
        return getPageProperty(pageName, "console.rows_per_page", defaultValue);
    }

    /**
     * Sets the new number of rows per page for the specified page for the current logged user.
     * The rows per page value is stored as a user property. The same property is being used for
     * different pages. The encoding format is the following "pageName1=value,pageName2=value".
     *
     * @param pageName the name of the page to stored its new value.
     * @param newValue the new rows per page value.
     */
    public void setRowsPerPage(String pageName, int newValue) {
        setPageProperty(pageName, "console.rows_per_page", newValue);
    }

    /**
     * Returns the number of seconds between each page refresh for the specified page for the
     * current logged user. The value is stored as a user property. The same property is being
     * used for different pages. The encoding format is the following
     * "pageName1=value,pageName2=value".
     *
     * @param pageName     the name of the page to look up its stored value.
     * @param defaultValue the default value to return if no user value was found.
     * @return the number of seconds between each page refresh for the specified page for
     *         the current logged user.
     */
    public int getRefreshValue(String pageName, int defaultValue) {
        return getPageProperty(pageName, "console.refresh", defaultValue);
    }

    /**
     * Sets the number of seconds between each page refresh for the specified page for the
     * current logged user. The value is stored as a user property. The same property is being
     * used for different pages. The encoding format is the following
     * "pageName1=value,pageName2=value".
     *
     * @param pageName the name of the page to stored its new value.
     * @param newValue the new number of seconds between each page refresh.
     */
    public void setRefreshValue(String pageName, int newValue) {
        setPageProperty(pageName, "console.refresh", newValue);
    }

    public int getPageProperty(String pageName, String property, int defaultValue) {
        User user = getUser();
        if (user != null) {
            String values = user.getProperties().get(property);
            if (values != null) {
                StringTokenizer tokens = new StringTokenizer(values, ",=");
                while (tokens.hasMoreTokens()) {
                    String page = tokens.nextToken().trim();
                    String rows = tokens.nextToken().trim();
                    if  (pageName.equals(page)) {
                        try {
                            return Integer.parseInt(rows);
                        }
                        catch (NumberFormatException e) {
                            return defaultValue;
                        }
                    }
                }
            }
        }
        return defaultValue;
    }

    public void setPageProperty(String pageName, String property, int newValue) {
        String toStore = pageName + "=" + newValue;
        User user = getUser();
        if (user != null) {
            String values = user.getProperties().get(property);
            if (values != null) {
                if (values.contains(toStore)) {
                    // The new value for the page was already stored so do nothing
                    return;
                }
                else {
                    if (values.contains(pageName)) {
                        // Replace an old value for the page with the new value
                        int oldValue = getPageProperty(pageName, property, -1);
                        String toRemove = pageName + "=" + oldValue;
                        user.getProperties().put(property, values.replace(toRemove, toStore));
                    }
                    else {
                        // Append the new page-value
                        user.getProperties().put(property, values + "," + toStore);
                    }
                }
            }
            else {
                // Store the new page-value as a new user property
                user.getProperties().put(property, toStore);
            }
        }
    }

    public Cache[] getCaches() {
        Cache[] caches =CacheFactory.getAllCaches();
        Arrays.sort(caches, new Comparator<Cache>() {
            public int compare(Cache cache1, Cache cache2) {
                return cache1.getName().toLowerCase().compareTo(cache2.getName().toLowerCase());
            }
        });
        return caches;
    }
}