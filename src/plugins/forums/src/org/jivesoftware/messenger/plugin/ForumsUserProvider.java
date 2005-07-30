package org.jivesoftware.messenger.plugin;

import com.caucho.hessian.client.HessianProxyFactory;
import com.opensymphony.forums.ws.WSUser;
import com.opensymphony.forums.ws.WebServices;
import org.jivesoftware.messenger.auth.AuthProvider;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.UserProvider;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * User: patrick
 * Date: Jul 22, 2005
 * Time: 3:10:13 PM
 */
public class ForumsUserProvider implements UserProvider, AuthProvider {
    private ForumsSettings settings = new ForumsSettings();

    private WebServices getWebServices() {
        HessianProxyFactory factory = new HessianProxyFactory();
        WebServices ws = null;
        try {
            ws = (WebServices) factory.create(WebServices.class, settings.getUrl());
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return ws;
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return false;
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        try {
            boolean auth = !getWebServices().authenticate(username, password);
            System.out.println("Auth result was: " + auth);
            if (auth) {
                throw new UnauthorizedException();
            }
        } catch (Exception e) {
            e.printStackTrace();

            if (e instanceof UnauthorizedException) {
                throw (UnauthorizedException) e;
            }
        }
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        throw new UnauthorizedException();
    }


    public User loadUser(String username) throws UserNotFoundException {
        WSUser wsUser = getWebServices().getUser(settings.getContext(), username);

        return new User(wsUser.getUsername(), wsUser.getName(), wsUser.getName(),
                wsUser.getCreated(), wsUser.getLastModified());
    }

    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
        return null;
    }

    public void deleteUser(String username) {
    }

    public int getUserCount() {
        return 0;
    }

    public Collection<User> getUsers() {
        return null;
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        return null;
    }

    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
        return null;
    }

    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
    }

    public void setName(String username, String name) throws UserNotFoundException {
    }

    public void setEmail(String username, String email) throws UserNotFoundException {
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
    }

    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
    }

    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return null;
    }

    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        return null;
    }

    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
        return null;
    }

    public boolean isReadOnly() {
        return true;
    }

}
