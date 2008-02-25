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
 * @author Daniel Henninger
 */
public class ClearspaceUserProvider implements UserProvider {
    protected static final String USER_URL_PREFIX = "userService/";
    protected static final String SEARCH_URL_PREFIX = "profileSearchService/";

    private ClearspaceManager manager;
    private Boolean readOnly;

    public ClearspaceUserProvider() {
        // Gets the manager
        manager = ClearspaceManager.getInstance();

        loadReadOnly();
    }

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

    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        try {

            String path = USER_URL_PREFIX + "users/";

            // Creates the XML with the data
            Document groupDoc =  DocumentHelper.createDocument();
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
            throw new UnsupportedOperationException("Error creatin the user", e);
        }
    }

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
            // it is ok, the user doesn't exist "anymore"
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

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

    public Collection<User> getUsers() {
        Collection<String> usernames = getUsernames();
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

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

    public Collection<User> getUsers(int startIndex, int numResults) {
        String[] usernamesAll = getUsernames().toArray(new String[0]);
        Collection<String> usernames = new ArrayList<String>();

        // Filters the user
        //TODO they aren't in alphabetical order.
        for (int i = startIndex; (i < startIndex + numResults) && (i < usernamesAll.length); i++) {
              usernames.add(usernamesAll[i]);
        }
        
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public void setName(String username, String name) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        try {
            Element user = getUserByUsername(username);
            Element modifiedUser = modifyUser(user.element("return"), "name", name);

            String path = USER_URL_PREFIX + "users";
            manager.executeRequest(PUT, path, modifiedUser.asXML());

        } catch (UserNotFoundException e) {
            throw new UserNotFoundException("User with name " + username + " not found.");
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void setEmail(String username, String email) throws UserNotFoundException {
        try {
            Element user = getUserByUsername(username);
            Element modifiedUser = modifyUser(user.element("return"), "email", email);

            String path = USER_URL_PREFIX + "users";
            manager.executeRequest(PUT, path, modifiedUser.asXML());

        } catch (UserNotFoundException e) {
            throw new UserNotFoundException("User with name " + username + " not found.");
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        try {
            Element user = getUserByUsername(username);
            Element modifiedUser = modifyUser(user.element("return"), "creationDate", WSUtils.formatDate(creationDate));

            String path = USER_URL_PREFIX + "users";
            manager.executeRequest(PUT, path, modifiedUser.asXML());

        } catch (UserNotFoundException e) {
            throw new UserNotFoundException("User with name " + username + " not found.");
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        try {
            Element user = getUserByUsername(username);
            Element modifiedUser = modifyUser(user.element("return"), "modificationDate", WSUtils.formatDate(modificationDate));

            String path = USER_URL_PREFIX + "users";
            manager.executeRequest(PUT, path, modifiedUser.asXML());

        } catch (UserNotFoundException e) {
            throw new UserNotFoundException("User with name " + username + " not found.");
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    private Element modifyUser(Element user, String attributeName, String newValue) {
        Document groupDoc =  DocumentHelper.createDocument();
        Element rootE = groupDoc.addElement("updateUser");
        Element newUser = rootE.addElement("user");
        List<Element> userAttributes = user.elements();
        for (Element userAttribute : userAttributes) {
            if (userAttribute.getName().equals(attributeName)) {
                newUser.addElement(userAttribute.getName()).setText(newValue);
            } else {
                newUser.addElement(userAttribute.getName()).setText(userAttribute.getText());
            }
        }
        return rootE;
    }

    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return new LinkedHashSet<String>(Arrays.asList("Username", "Name", "Email"));
    }

    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        // Creates the XML with the data
        Document groupDoc =  DocumentHelper.createDocument();
        Element rootE = groupDoc.addElement("searchProfile");
        Element queryE = rootE.addElement("WSProfileSearchQuery");
        Element keywords = queryE.addElement("keywords");
        keywords.addText(query);
        Element searchUsername = queryE.addElement("searchUsername");
        searchUsername.addText("true");
        Element searchName = queryE.addElement("searchName");
        searchName.addText("true");
        Element searchEmail = queryE.addElement("searchEmail");
        searchEmail.addText("true");
        Element searchProfile = queryE.addElement("searchProfile");
        searchProfile.addText("false");

        try {
            List<String> usernames = new ArrayList<String>();

            String path = SEARCH_URL_PREFIX + "searchProfile";
            //TODO they aren't in alphabetical order.
            //TODO get only the username field
            Element element = manager.executeRequest(GET, path);

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

    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
        // Creates the XML with the data
        Document groupDoc =  DocumentHelper.createDocument();
        Element rootE = groupDoc.addElement("searchBounded");
        Element queryE = rootE.addElement("WSProfileSearchQuery");
        Element keywords = queryE.addElement("keywords");
        keywords.addText(query);
        Element searchUsername = queryE.addElement("searchUsername");
        searchUsername.addText("true");
        Element searchName = queryE.addElement("searchName");
        searchName.addText("true");
        Element searchEmail = queryE.addElement("searchEmail");
        searchEmail.addText("true");
        Element searchProfile = queryE.addElement("searchProfile");
        searchProfile.addText("false");

        try {
            List<String> usernames = new ArrayList<String>();

            String path = SEARCH_URL_PREFIX + "searchProfile/" + startIndex + "/" + numResults;
            //TODO they aren't in alphabetical order.
            //TODO get only the username field
            Element element = manager.executeRequest(GET, path);

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

    public boolean isReadOnly() {
        if (readOnly == null) {
            loadReadOnly();
        }
        // If it is null returns the most restrictive anwser.
        return (readOnly == null ? false : readOnly);
    }

    public boolean isNameRequired() {
        return false;
    }

    public boolean isEmailRequired() {
        return true;
    }

    private void loadReadOnly() {
        try {
            // See if the is read only
            String path = USER_URL_PREFIX + "isReadOnly";
            Element element = manager.executeRequest(GET, path);
            readOnly = Boolean.valueOf(getReturn(element));
        } catch (Exception e) {
            // if there is a problem, keep it null, maybe in the next call succes.
        }
    }

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
        if (nameVisible) {
            tmpNode = userNode.selectSingleNode("name");
            if (tmpNode != null) {
                name = tmpNode.getText();
            }
        }

        // Gets the email if it is visible
        boolean emailVisible = Boolean.valueOf(userNode.selectSingleNode("emailVisible").getText());
        if (emailVisible) {
            tmpNode = userNode.selectSingleNode("email");
            if (tmpNode != null) {
                email = tmpNode.getText();
            }
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
        //TODO add other attributes, like user properties
        return user;
    }

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
