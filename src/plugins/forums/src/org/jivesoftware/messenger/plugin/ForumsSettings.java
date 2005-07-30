package org.jivesoftware.messenger.plugin;

import com.opensymphony.forums.ws.AuthContext;
import org.jivesoftware.util.JiveGlobals;

/**
 * User: patrick
 * Date: Jul 22, 2005
 * Time: 4:09:01 PM
 */
public class ForumsSettings {

    String url;
    String username;
    String password;

    public ForumsSettings() {
        url = JiveGlobals.getProperty("forums.url", "http://localhost:8080/forums/webservices");
        username = JiveGlobals.getProperty("forums.username", "admin");
        password = JiveGlobals.getProperty("forums.password", "admin");
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AuthContext getContext() {
        AuthContext ac = new AuthContext();
        ac.setUsername(username);
        ac.setPassword(password);
        return ac;
    }
}
