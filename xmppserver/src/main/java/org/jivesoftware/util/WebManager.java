/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.PrivateStorage;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.pubsub.PubSubInfo;
import org.jivesoftware.openfire.pubsub.PubSubServiceInfo;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;

/**
 * A utility bean for Openfire admin console pages.
 */
public class WebManager extends WebBean {

    private static final Logger Log = LoggerFactory.getLogger(WebManager.class);

    private int start = 0;
    private int range = 15;

    public WebManager() {
    }

    /**
     * Invalidates and recreates session (do this on login/logout).
     * @return the new HTTP session
     */
    public HttpSession invalidateSession() {
        session.invalidate();
        session = request.getSession(true);
        return session;
    }

    /**
     * @return the auth token; redirect to the login page if an auth token is not found.
     */
    public AuthToken getAuthToken() {
        return (AuthToken)session.getAttribute("jive.admin.authToken");
    }

    /**
     * @return {@code true} if the Openfire container is in setup mode, {@code false} otherwise.
     */
    public boolean isSetupMode() {
        return getXMPPServer().isSetupMode();
    }

    /**
     * @return the XMPP server object -- can get many config items from here.
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

    public PubSubServiceInfo getPubSubInfo() {
        return new PubSubInfo();
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
     * @return the page user or {@code null} if one is not found.
     */
    public User getUser() {
        User pageUser = null;
        try {
            final AuthToken authToken = getAuthToken();
            if (authToken == null )
            {
                Log.debug( "Unable to get user: no auth token on session." );
                return null;
            }
            if (authToken instanceof AuthToken.OneTimeAuthToken) {
                return new User(authToken.getUsername(), "Recovery via One Time Auth Token", null, new Date(), new Date());
            }
            final String username = authToken.getUsername();
            if (username == null || username.isEmpty())
            {
                Log.debug( "Unable to get user: no username in auth token on session." );
                return null;
            }
            pageUser = getUserManager().getUser(username);
        }
        catch (Exception ex) {
            Log.debug("Unexpected exception (which is ignored) while trying to obtain user.", ex);
        }
        return pageUser;
    }

    /**
     * @return {@code true} if the server is in embedded mode, {@code false} otherwise.
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
            Log.error(e.getMessage(), e);
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
            Log.error(e.getMessage(), e);
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
            Log.trace("Sleep got interrupted.");
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
     * @param src the source location
     * @param dst the target location
     * @throws IOException if the copy failed
     */
    public static void copy(URL src, File dst) throws IOException {

        try (InputStream in = src.openStream()) {
            try (OutputStream out = new FileOutputStream(dst)) {
                dst.mkdirs();
                copy(in, out);
            }
        }
    }

    /**
     * Common code for copy routines.  By convention, the streams are
     * closed in the same method in which they were opened.  Thus,
     * this method does not close the streams when the copying is done.
     * @param in the input stream
     * @param out the output stream
     * @throws IOException if the copy failed
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
            @Override
            public int compare(Cache cache1, Cache cache2) {
                return cache1.getName().toLowerCase().compareTo(cache2.getName().toLowerCase());
            }
        });
        return caches;
    }
}
