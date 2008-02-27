/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.XMPPServer;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.*;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;
import static org.jivesoftware.openfire.clearspace.WSUtils.parseStringArray;
import org.jivesoftware.openfire.user.*;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * The ClearspaceUserProvider uses the UserService and ProfileSearchService web service inside of Clearspace
 * to retrieve user information and to search for users from Clearspace.
 *
 * @author Gabriel Guardincerri
 */
public class ClearspaceUserProvider implements UserProvider {

    // The UserService webservice url prefix
    protected static final String USER_URL_PREFIX = "userService/";
    // The ProfileSearchService webservice url prefix
    protected static final String SEARCH_URL_PREFIX = "profileSearchService/";

    private ClearspaceManager manager;

    // Used to know it CS is a read only user provider
    private Boolean readOnly;

    public ClearspaceUserProvider() {
        // Gets the manager
        manager = ClearspaceManager.getInstance();
    }

    /**
     * Loads the user using the userService/users GET service. Only loads local users.
     * Throws a UserNotFoundException exception if the user could not be found.
     *
     * @param username the username of the user to load
     * @return a user instance with the user information
     * @throws UserNotFoundException if the user could not be found
     */
    public User loadUser(String username) throws UserNotFoundException {
        // Checks if the user is local
        if (username.contains("@")) {
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0, username.lastIndexOf("@"));
        }

        // Translate the response
        return translate(getUserByUsername(username));
    }

    /**
     * Creates user using the userService/users POST service. If Clearspace is a read only
     * provider throws an UnsupportedOperationException. If there is already a user with
     * the username throws a UserAlreadyExistsException.
     *
     * @param username the username of the user
     * @param password the password of the user
     * @param name     the name of the user (optional)
     * @param email    the email of the user
     * @return an instance of the created user
     * @throws UserAlreadyExistsException    If there is already a user with the username
     * @throws UnsupportedOperationException If Clearspace is a read only provider
     */
    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        try {

            String path = USER_URL_PREFIX + "users/";

            // Creates the XML with the data
            Document groupDoc = DocumentHelper.createDocument();
            Element rootE = groupDoc.addElement("createUserWithUser");
            Element userE = rootE.addElement("user");

            // adds the username
            Element usernameE = userE.addElement("username");
            usernameE.addText(username);

            // adds the name if it is not empty
            if (name != null && !"".equals(name.trim())) {
                Element nameE = userE.addElement("name");
                nameE.addText(name);
            }

            // adds the password
            Element passwordE = userE.addElement("password");
            passwordE.addText(password);

            // adds the the email
            Element emailE = userE.addElement("email");
            emailE.addText(email);

            // new user are always enabled
            Element enabledE = userE.addElement("enabled");
            enabledE.addText("true");


            Element user = manager.executeRequest(POST, path, groupDoc.asXML());

            return translate(user);
        } catch (UserAlreadyExistsException uaee) {
            throw uaee;
        } catch (Exception e) {
            throw new UnsupportedOperationException("Error creating the user", e);
        }
    }

    /**
     * Creates user using the userService/users DELETE service. If the user is not found returns.
     *
     * @param username the username of the user to delete
     */
    public void deleteUser(String username) {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        try {
            long userID = manager.getUserID(username);
            String path = USER_URL_PREFIX + "users/" + userID;
            manager.executeRequest(DELETE, path);

        } catch (UserNotFoundException gnfe) {
            // it is OK, the user doesn't exist "anymore"
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Gets the user count using the userService/users/count GET service.
     *
     * @return the user count
     */
    public int getUserCount() {
        try {
            String path = USER_URL_PREFIX + "users/count";
            Element element = manager.executeRequest(GET, path);
            int count = Integer.valueOf(getReturn(element));
            return count;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Gets all users using the userService/userNames GET service.
     *
     * @return a list of all users
     */
    public Collection<User> getUsers() {
        Collection<String> usernames = getUsernames();
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    /**
     * Gets all  usernames using the userService/userNames GET service.
     *
     * @return a list of all the usernames
     */
    public Collection<String> getUsernames() {
        try {
            String path = USER_URL_PREFIX + "userNames";
            Element element = manager.executeRequest(GET, path);

            return parseStringArray(element);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Gets a bounded list of users using the userService/userNames GET service.
     *
     * @param startIndex the start index
     * @param numResults the number of result
     * @return a bounded list of users
     */
    public Collection<User> getUsers(int startIndex, int numResults) {
        String[] usernamesAll = getUsernames().toArray(new String[0]);
        Collection<String> usernames = new ArrayList<String>();

        // Filters the user
        for (int i = startIndex; (i < startIndex + numResults) && (i < usernamesAll.length); i++) {
            usernames.add(usernamesAll[i]);
        }

        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    /**
     * Updates the name of the user using the userService/update service.
     *
     * @param username the username of the user
     * @param name     the new name of the user
     * @throws UserNotFoundException if there is no user with that username
     */
    public void setName(String username, String name) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        // Creates the params
        Element userUpdateParams = getUserUpdateParams(username);

        // Modifies the attribute of the user
        String[] path = new String[]{"user", "name"};
        WSUtils.modifyElementText(userUpdateParams, path, name);

        // Updates the user
        updateUser(userUpdateParams);
    }

    /**
     * Updates the email of the user using the userService/update service.
     *
     * @param username the username of the user
     * @param email    the new email of the user
     * @throws UserNotFoundException if the user could not be found
     */
    public void setEmail(String username, String email) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        // Creates the params
        Element userUpdateParams = getUserUpdateParams(username);

        // Modifies the attribute of the user
        String[] path = new String[]{"user", "email"};
        WSUtils.modifyElementText(userUpdateParams, path, email);

        // Updates the user
        updateUser(userUpdateParams);
    }


    /**
     * Updates the creationDate of the user using the userService/update service.
     *
     * @param username     the username of the user
     * @param creationDate the new email of the user
     * @throws UserNotFoundException if the user could not be found
     */
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        // Creates the params
        Element userUpdateParams = getUserUpdateParams(username);

        // Modifies the attribute of the user
        String[] path = new String[]{"user", "creationDate"};
        String newValue = WSUtils.formatDate(creationDate);
        WSUtils.modifyElementText(userUpdateParams, path, newValue);

        // Updates the user
        updateUser(userUpdateParams);
    }

    /**
     * Updates the modificationDate of the user using the userService/update service.
     *
     * @param username         the username of the user
     * @param modificationDate the new modificationDate of the user
     * @throws UserNotFoundException if the user could not be found
     */
    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        // Creates the params
        Element userUpdateParams = getUserUpdateParams(username);

        // Modifies the attribute of the user
        String[] path = new String[]{"user", "modificationDate"};
        String newValue = WSUtils.formatDate(modificationDate);
        WSUtils.modifyElementText(userUpdateParams, path, newValue);

        // Updates the user
        updateUser(userUpdateParams);
    }

    /**
     * Creates the parameters to send in a update user request based on the information of <code>username</code>
     *
     * @param username the username of the user
     * @return the parameters to send in a update user request
     * @throws UserNotFoundException if the user could not be found
     */
    protected Element getUserUpdateParams(String username) throws UserNotFoundException {
        // Creates the user update params element
        Element userUpdateParams = DocumentHelper.createDocument().addElement("updateUser");
        Element newUser = userUpdateParams.addElement("user");

        // Gets the current user information
        Element currentUser = getUserByUsername(username).element("return");


        List<Element> userAttributes = currentUser.elements();
        for (Element userAttribute : userAttributes) {
            newUser.addElement(userAttribute.getName()).setText(userAttribute.getText());
        }
        return userUpdateParams;
    }

    /**
     * Updates the user using the userService/users PUT service.
     *
     * @param userUpdateParams the request parameters
     * @throws UserNotFoundException if the user could not be found
     */
    protected void updateUser(Element userUpdateParams) throws UserNotFoundException {
        try {
            String path = USER_URL_PREFIX + "users";
            manager.executeRequest(PUT, path, userUpdateParams.asXML());

        } catch (UserNotFoundException e) {
            throw new UserNotFoundException("User not found.");
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Clearsapce can search using three fields: username, name and email.
     *
     * @return a list of username, name and email
     * @throws UnsupportedOperationException
     */
    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return new LinkedHashSet<String>(Arrays.asList("Username", "Name", "Email"));
    }

    /**
     * Search for the user using the userService/search POST method.
     *
     * @param fields the fields to search on.
     * @param query  the query string.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException if the provider does not
     *                                       support the operation (this is an optional operation).
     */
    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        // Creates the XML with the data
        Element paramsE = DocumentHelper.createDocument().addElement("search");

        Element queryE = paramsE.addElement("query");

        queryE.addElement("keywords").addText(query);

        queryE.addElement("searchUsername").addText("true");
        queryE.addElement("searchName").addText("true");
        queryE.addElement("searchEmail").addText("true");
        queryE.addElement("searchProfile").addText("false");

        try {
            List<String> usernames = new ArrayList<String>();

            //TODO create a service on CS to get only the username field
            String path = SEARCH_URL_PREFIX + "searchProfile";
            Element element = manager.executeRequest(POST, path, paramsE.asXML());

            List<Node> userNodes = (List<Node>) element.selectNodes("return");
            for (Node userNode : userNodes) {
                String username = userNode.selectSingleNode("username").getText();
                usernames.add(username);
            }

            return new UserCollection(usernames.toArray(new String[usernames.size()]));
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Search for the user using the userService/searchBounded POST method.
     *
     * @param fields     the fields to search on.
     * @param query      the query string.
     * @param startIndex the starting index in the search result to return.
     * @param numResults the number of users to return in the search result.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException if the provider does not
     *                                       support the operation (this is an optional operation).
     */
    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
        // Creates the XML with the data
        Element paramsE = DocumentHelper.createDocument().addElement("searchBounded");

        Element queryE = paramsE.addElement("query");

        queryE.addElement("keywords").addText(query);

        queryE.addElement("searchUsername").addText("true");
        queryE.addElement("searchName").addText("true");
        queryE.addElement("searchEmail").addText("true");
        queryE.addElement("searchProfile").addText("false");

        paramsE.addElement("startIndex").addText(String.valueOf(startIndex));
        paramsE.addElement("numResults").addText(String.valueOf(numResults));

        try {
            List<String> usernames = new ArrayList<String>();

            //TODO create a service on CS to get only the username field
            String path = SEARCH_URL_PREFIX + "searchProfile";
            Element element = manager.executeRequest(POST, path, paramsE.asXML());

            List<Node> userNodes = (List<Node>) element.selectNodes("return");
            for (Node userNode : userNodes) {
                String username = userNode.selectSingleNode("username").getText();
                usernames.add(username);
            }

            return new UserCollection(usernames.toArray(new String[usernames.size()]));
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Returns true if Clearspace is a read only user provider.
     *
     * @return true if Clearspace is a read only user provider
     */
    public boolean isReadOnly() {
        if (readOnly == null) {
            synchronized (this) {
                if (readOnly == null) {
                    loadReadOnly();
                }
            }
        }
        // If it is null returns the most restrictive answer.
        return (readOnly == null ? false : readOnly);
    }

    /**
     * In Clearspace name is optional.
     *
     * @return false
     */
    public boolean isNameRequired() {
        return false;
    }

    /**
     * In Clearspace email is required
     *
     * @return true
     */
    public boolean isEmailRequired() {
        return true;
    }

    /**
     * Tries to load the read only attribute using the userService/isReadOnly service.
     */
    private void loadReadOnly() {
        try {
            // See if the is read only
            String path = USER_URL_PREFIX + "isReadOnly";
            Element element = manager.executeRequest(GET, path);
            readOnly = Boolean.valueOf(getReturn(element));
        } catch (Exception e) {
            // if there is a problem, keep it null, maybe in the next call success.
        }
    }

    /**
     * Translates a Clearspace xml user response into a Openfire User
     *
     * @param responseNode the Clearspace response
     * @return a User instance with its information
     */
    private User translate(Node responseNode) {
        String username = null;
        String name = null;
        String email = null;
        Date creationDate = null;
        Date modificationDate = null;

        Node userNode = responseNode.selectSingleNode("return");
        Node tmpNode;

        // Gets the username
        username = userNode.selectSingleNode("username").getText();

        // Gets the name if it is visible
        boolean nameVisible = Boolean.valueOf(userNode.selectSingleNode("nameVisible").getText());

        // Gets the name
        tmpNode = userNode.selectSingleNode("name");
        if (tmpNode != null) {
            name = tmpNode.getText();
        }

        // Gets the email if it is visible
        boolean emailVisible = Boolean.valueOf(userNode.selectSingleNode("emailVisible").getText());

        // Gets the email
        tmpNode = userNode.selectSingleNode("email");
        if (tmpNode != null) {
            email = tmpNode.getText();
        }

        // Gets the creation date
        tmpNode = userNode.selectSingleNode("creationDate");
        if (tmpNode != null) {
            creationDate = WSUtils.parseDate(tmpNode.getText());
        }

        // Gets the modification date
        tmpNode = userNode.selectSingleNode("modificationDate");
        if (tmpNode != null) {
            modificationDate = WSUtils.parseDate(tmpNode.getText());
        }

        // Creates the user
        User user = new User(username, name, email, creationDate, modificationDate);
        user.setNameVisible(nameVisible);
        user.setEmailVisible(emailVisible);
        return user;
    }

    /**
     * Gets a user using the userService/users GET service.
     *
     * @param username the username of the user
     * @return the user xml response
     * @throws UserNotFoundException if the user could not be found
     */
    private Element getUserByUsername(String username) throws UserNotFoundException {
        try {

            // Requests the user
            String path = USER_URL_PREFIX + "users/" + username;
            Element response = manager.executeRequest(GET, path);

            // return the response
            return response;

        } catch (UserNotFoundException unfe) {
            throw unfe;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UserNotFoundException
            throw new UserNotFoundException("Error loading the user", e);
        }
    }
}
