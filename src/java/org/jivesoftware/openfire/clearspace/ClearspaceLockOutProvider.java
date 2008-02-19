/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.NotLockedOutException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.PUT;
import org.jivesoftware.util.Log;

import org.dom4j.Node;
import org.dom4j.Element;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;

/**
 * The ClearspaceLockOutProvider uses the UserService web service inside of Clearspace
 * to retrieve user properties from Clearspace.  One of these properties refers to whether
 * the user is disabled or not.  In the future we may implement this in a different manner
 * that will require less overall communication with Clearspace.
 *
 * @author Daniel Henninger
 */
public class ClearspaceLockOutProvider implements LockOutProvider {

    protected static final String USER_URL_PREFIX = "userService/";

    private ClearspaceManager manager;

    /**
     * Generate a ClearspaceLockOutProvider instance.
     */
    public ClearspaceLockOutProvider() {
        // Gets the manager
        manager = ClearspaceManager.getInstance();
    }

    /**
     * The ClearspaceLockOutProvider will retrieve lockout information from Clearspace's user properties.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#getDisabledStatus(String)
     */
    public LockOutFlag getDisabledStatus(String username) throws NotLockedOutException {
        try {
            // Retrieve the disabled status, translate it into a LockOutFlag, and return it.
            return checkUserDisabled(getUserByUsername(username));
        }
        catch (UserNotFoundException e) {
            // Not a valid user?  We will leave it up to the user provider to handle rejecting this user.
            return null;
        }
    }

    /**
     * The ClearspaceLockOutProvider will set lockouts in Clearspace itself.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#setDisabledStatus(org.jivesoftware.openfire.lockout.LockOutFlag)
     */
    public void setDisabledStatus(LockOutFlag flag) {
        try {
            setUserData(setEnabledStatus(getUserByUsername(flag.getUsername()), false));
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to set disabled status for Clearspace user: "+flag.getUsername());
        }
    }

    /**
     * The ClearspaceLockOutProvider will set lockouts in Clearspace itself.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#unsetDisabledStatus(String)
     */
    public void unsetDisabledStatus(String username) {
        try {
            setUserData(setEnabledStatus(getUserByUsername(username), true));
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to set enabled status for Clearspace user: "+username);
        }
    }

    /**
     * The ClearspaceLockOutProvider will set lockouts in Clearspace itself.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isReadOnly()
     */
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Clearspace only supports a strict "are you disabled or not".
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isDelayedStartSupported()
     */
    public boolean isDelayedStartSupported() {
        return false;
    }

    /**
     * Clearspace only supports a strict "are you disabled or not".
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isTimeoutSupported()
     */
    public boolean isTimeoutSupported() {
        return false;
    }

    /**
     * Modifies the XML returned about a user to indicate whether they are enabled or disabled.
     * It is important for this to incorporate the existing user data and only tweak the field
     * that we want to change.
     *
     * @param responseNode The node returned from user data request.
     * @param enabled Whether the account should be enabled or disabled.
     * @return A modified user data node with appropriate settings for whether they are disabled or enabled.
     */
    private Node setEnabledStatus(Node responseNode, Boolean enabled) {
        Node userNode = responseNode.selectSingleNode("return");

        // Sets the enabled status
        userNode.selectSingleNode("enabled").setText(enabled ? "true" : "false");

        // Returns the modified node.
        return userNode;
    }

    /**
     * Examines the XML returned about a user to find out if they are enabled or disabled, throwing
     * a NotLockedOutException if they are.
     *
     * @param responseNode Element returned from REST service. (@see #getUserByUsername)
     * @return Either a LockOutFlag indicating that the user is disabled, or an exception is thrown.
     * @throws NotLockedOutException if the user is not currently locked out.
     */
    private LockOutFlag checkUserDisabled(Node responseNode) throws NotLockedOutException {
        Node userNode = responseNode.selectSingleNode("return");

        // Gets the username
        String username = userNode.selectSingleNode("username").getText();

        // Gets the enabled field
        boolean isEnabled = Boolean.valueOf(userNode.selectSingleNode("enabled").getText());
        if (isEnabled) {
            // We're good, indicate that they're not locked out.
            throw new NotLockedOutException();
        }
        else {
            // Creates the lock out flag
            return new LockOutFlag(username, null, null);
        }
    }

    /**
     * Retrieves user properties for a Clearspace user in XML format.
     *
     * @param username Username to look up.
     * @return XML Element including information about the user.
     * @throws UserNotFoundException The user was not found in the Clearspace database.
     */
    private Element getUserByUsername(String username) throws UserNotFoundException {
        try {
            // Requests the user
            String path = USER_URL_PREFIX + "users/" + username;
            // return the response
            return manager.executeRequest(GET, path);
        }
        catch (Exception e) {
            // It is not supported exception, wrap it into an UserNotFoundException
            throw new UserNotFoundException("Error loading the user", e);
        }
    }

    /**
     * Sets user properties data for a Clearspace user.
     *
     * @param node XML data to send to Clearspace REST service.
     */
    private void setUserData(Node node) {
        try {
            // Requests the user
            String path = USER_URL_PREFIX + "users";

            // Creates the XML with the data
            Document userDoc =  DocumentHelper.createDocument();
            Element rootE = userDoc.addElement("updateUser");
            rootE.add(node);

            manager.executeRequest(PUT, path, userDoc.asXML());
        }
        catch (Exception e) {
            // Error while setting properties?
            Log.error("Unable to set user data via REST service in Clearspace:", e);
        }
    }
    
}
