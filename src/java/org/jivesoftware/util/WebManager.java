package org.jivesoftware.util;

import org.jivesoftware.messenger.chat.ChatServer;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ServiceLookup;
import org.jivesoftware.messenger.container.ServiceLookupFactory;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.WebBean;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.PrivateStore;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.XMPPServerInfo;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebManager extends WebBean {
    private LinkedHashMap breadcrumbMap = new LinkedHashMap();
    private String title = "";
    private String sidebar = "";

    private int start = 0;
    private int range = 15;

    public WebManager() {
    }

    public AuthToken getAuthToken() {
        AuthToken authToken = (AuthToken)session.getAttribute("jive.admin.authToken");
        if (authToken == null) {
            showLogin();
        }
        return authToken;
    }

    public boolean isSetupMode() {
        return getContainer().isSetupMode();
    }

    public ServiceLookup getServiceLookup() {
        try {
            return ServiceLookupFactory.getLookup();
        }
        catch (UnauthorizedException ex) {
            return null;
        }
    }

    public Container getContainer() {
        return (Container)getServiceLookup().lookup(Container.class);
    }

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
        // The user object of the logged-in user
        UserManager userManager = (UserManager)getServiceLookup().lookup(UserManager.class);
        return userManager;
    }


    public PrivateStore getPrivateStore() {
        final PrivateStore privateStore = (PrivateStore)getServiceLookup().lookup(PrivateStore.class);
        return privateStore;
    }

    public PresenceManager getPresenceManager() {
        return (PresenceManager)getServiceLookup().lookup(PresenceManager.class);
    }

    public SessionManager getSessionManager() {
        return (SessionManager)getServiceLookup().lookup(SessionManager.class);
    }

    public ChatServer getChatServer() {
        return (ChatServer)getServiceLookup().lookup(ChatServer.class);
    }

    public MultiUserChatServer getMultiUserChatServer() {
        return (MultiUserChatServer)getServiceLookup().lookup(MultiUserChatServer.class);
    }

    public XMPPServerInfo getServerInfo() {
        return getXMPPServer().getServerInfo();
    }

    public User getUser() {
        User pageUser = null;
        try {
            pageUser = getUserManager().getUser(getAuthToken().getUserID());
        }
        catch (UserNotFoundException ex) {
            ex.printStackTrace();
        }
        return pageUser;
    }


    public boolean isEmbedded() {
        try {
            ClassUtils.forName("org.jivesoftware.messenger.container.starter.ServerStarter");
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }


    public void restart(Container container) {
        try {
            container.restart();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }

    public void stop(Container container) {
        try {
            container.stop();
        }
        catch (Exception e) {
            Log.error(e);
        }
        sleep();
    }

    public void sleep() {
        // Sleep for a minute:
        try {
            Thread.sleep(3000L);
        }
        catch (Exception ignored) {
        }
    }

    public WebManager getManager() {
        return this;
    }

    public void showLogin() {
        try {
            response.sendRedirect("login.jsp");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void showServerDown() {
        try {
            response.sendRedirect("error-serverdown.jsp");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
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

}