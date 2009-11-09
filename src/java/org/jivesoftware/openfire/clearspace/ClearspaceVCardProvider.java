/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.clearspace;

import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.POST;
import static org.jivesoftware.openfire.clearspace.ClearspaceVCardTranslator.Action.DELETE;
import static org.jivesoftware.openfire.clearspace.ClearspaceVCardTranslator.Action.NO_ACTION;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardProvider;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ClearspaceLockOutProvider uses the UserService web service inside of Clearspace
 * to retrieve, edit and delete user information from Clearspace.  With this information the provider
 * builds user's VCard.
 *
 * @author Gabriel Guardincerri
 */
public class ClearspaceVCardProvider implements VCardProvider {

	private static final Logger Log = LoggerFactory.getLogger(ClearspaceVCardProvider.class);

    protected static final String PROFILE_URL_PREFIX = "profileService/";
    protected static final String PROFILE_FIELDS_URL_PREFIX = "profileFieldService/";
    protected static final String AVATAR_URL_PREFIX = "avatarService/";

    private Boolean avatarReadOnly;
    private boolean fieldsIDLoaded;

    public ClearspaceVCardProvider() {
    }

    /**
     * Loads the VCard with information from CS. It uses information from the user, the user profile and the avatar.
     * With this 3 sources of informations it builds the VCard.
     *
     * @param username username of user to load VCard of
     * @return the user's VCard
     */
    public Element loadVCard(String username) {
        // if the fields id are not loaded
        if (!fieldsIDLoaded) {
            synchronized (this) {
                if (!fieldsIDLoaded) {
                    // try to load them
                    loadDefaultProfileFields();
                    // if still not loaded then the operation could no be perform
                    if (!fieldsIDLoaded) {
                        // It is not supported exception, wrap it into an UnsupportedOperationException
                        throw new UnsupportedOperationException("Error loading the profiles IDs");
                    }
                }
            }
        }

        try {

            // Gets the user
            User user = UserManager.getInstance().getUser(username);

            long userID = ClearspaceManager.getInstance().getUserID(username);

            // Gets the profiles information
            Element profiles = getProfiles(userID);

            // Gets the avatar information
            Element avatar = getAvatar(userID);

            // Translate the response
            return ClearspaceVCardTranslator.getInstance().translateClearspaceInfo(profiles, user, avatar);

        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Error loading the vCard", e);
        }
    }

    /**
     * Creates the user's VCard. CS always has some information of users. So creating it is actually updating.
     * Throws an UnsupportedOperationException if Clearspace can't save some changes. Returns the VCard after the change.
     *
     * @param username     the username
     * @param vCardElement the vCard to save.
     * @return vCard as it is after the provider has a chance to adjust it.
     * @throws AlreadyExistsException        it's never throw by this implementation
     * @throws UnsupportedOperationException if the provider does not support the
     *                                       operation.
     */
    public Element createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        return saveVCard(username, vCardElement);
    }

    /**
     * Updates the user vcard in Clearspace. Throws an UnsupportedOperationException if Clearspace can't
     * save some changes. Returns the VCard after the change.
     *
     * @param username     the username.
     * @param vCardElement the vCard to save.
     * @return vCard as it is after the provider has a chance to adjust it.
     * @throws NotFoundException             if the vCard to update does not exist.
     * @throws UnsupportedOperationException if the provider does not support the
     *                                       operation.
     */
    public Element updateVCard(String username, Element vCardElement) throws NotFoundException {
        return saveVCard(username, vCardElement);
    }

    /**
     * Always return false since Clearspace always support some changes.
     *
     * @return true
     */
    public boolean isReadOnly() {
        // Return always false, since some changes are always allowed
        return false;
    }

    /**
     * Returns true the user can modify the Avatar of Clearspace.
     *
     * @return if the Avatar of Clearspace can be modified.
     */
    private boolean isAvatarReadOnly() {
        if (avatarReadOnly == null) {
            synchronized (this) {
                if (avatarReadOnly == null) {
                    loadAvatarReadOnly();
                }
            }
        }
        return avatarReadOnly == null ? false : avatarReadOnly;
    }

    /**
     * Saves the vCard of the user. First check if the change can be made,
     * if not throws an UnsupportedOperationException.
     * The VCard information is divided into 3 parts. First the preferred
     * email and the user full name are stored into Clearspace user information.
     * Second the avatar is stored into Clearspace avatar information. If the avatar was
     * new or it was modified, a new avatar is created in Clearspace. If the avatar was
     * deleted, in Clearspace the user won't have an active avatar.
     *
     * @param username     the username of the user to update the avatar info to
     * @param vCardElement the vCard with the new information
     * @return the VCard with the updated information
     * @throws UnsupportedOperationException if the provider does not support some changes.
     */
    private Element saveVCard(String username, Element vCardElement) {
        if (Log.isDebugEnabled()) {
            Log.debug("Saving VCARD: " + vCardElement.asXML());
        }

        if (!fieldsIDLoaded) {
            synchronized (this) {
                if (!fieldsIDLoaded) {
                    // try to load them
                    loadDefaultProfileFields();
                    // if still not loaded then the operation could no be perform
                    if (!fieldsIDLoaded) {
                        // It is not supported exception, wrap it into an UnsupportedOperationException
                        throw new UnsupportedOperationException("Error loading the profiles IDs");
                    }
                }
            }
        }

        try {

            long userID = ClearspaceManager.getInstance().getUserID(username);
            ClearspaceUserProvider userProvider = (ClearspaceUserProvider) UserManager.getUserProvider();

            // Gets the user params that can be used to update it
            Element userUpdateParams = userProvider.getUserUpdateParams(username);
            // Gets the element that contains the user information
            Element userElement = userUpdateParams.element("user");

            // Gets the profiles params that can be used to update them
            Element profilesUpdateParams = getProfilesUpdateParams(userID);
            //Element profilesElement = profilesUpdateParams.element("profiles");

            // Get the avatar params that can be used to create it. It doesn't have an avatar sub element.
            Element avatarCreateParams = getAvatarCreateParams(userID);

            // Modifies the profile, user and avatar elements according to the VCard information.
            ClearspaceVCardTranslator.Action[] actions;
            actions = ClearspaceVCardTranslator.getInstance().translateVCard(vCardElement, profilesUpdateParams, userElement, avatarCreateParams);

            // Throws an exception if the changes implies to modify something that is read only
            if ((actions[1] != NO_ACTION && userProvider.isReadOnly()) || (actions[2] != NO_ACTION && isAvatarReadOnly())) {
                throw new UnsupportedOperationException("ClearspaceVCardProvider: Invalid vcard changes.");
            }

            // Updates the profiles
            if (actions[0] != NO_ACTION) {
                updateProfiles(profilesUpdateParams);
            }

            // Updates the user
            if (actions[1] != NO_ACTION) {
                userProvider.updateUser(userUpdateParams);
            }

            // Updates the avatar
            if (actions[2] != NO_ACTION) {
                // Set no active avatar to delete
                if (actions[2] == DELETE) {
                    setActiveAvatar(userID, -1);
                } else {
                    // else it was created or updated, on both cases it needs to be created and assigned as the active avatar.
                    long avatarID = createAvatar(avatarCreateParams);
                    setActiveAvatar(userID, avatarID);
                }
            }
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedOperationException("Error saving the VCard", e);
        }

        return loadVCard(username);
    }

    /**
     * Deletes the profiles and avatar information of the user.
     *
     * @param username the username.
     */
    public void deleteVCard(String username) {
        ClearspaceUserProvider userProvider = (ClearspaceUserProvider) UserManager.getUserProvider();
        if (userProvider.isReadOnly() || isAvatarReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        long userID;
        try {
            userID = ClearspaceManager.getInstance().getUserID(username);
        } catch (UserNotFoundException gnfe) {
            // it is OK, the user doesn't exist "anymore"
            return;
        }

        deleteAvatar(userID);

        deleteProfiles(userID);
    }

    /**
     * Deletes the profiles of the user.
     *
     * @param userID the user id.
     */
    private void deleteProfiles(long userID) {
        try {
            String path = PROFILE_URL_PREFIX + "profiles/" + userID;
            ClearspaceManager.getInstance().executeRequest(ClearspaceManager.HttpType.DELETE, path);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Deletes the avatar of the user.
     *
     * @param userID the user id.
     */
    private void deleteAvatar(long userID) {
        try {
            String path = AVATAR_URL_PREFIX + "avatar/" + userID;
            ClearspaceManager.getInstance().executeRequest(ClearspaceManager.HttpType.DELETE, path);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Makes the request to the webservice of Clearspace to update the profiles information.
     *
     * @param profilesUpdateParams the profiles params to use with the request.
     */
    private void updateProfiles(Element profilesUpdateParams) {
        // Try to save the profile changes
        try {
            String path = PROFILE_URL_PREFIX + "profiles";
            ClearspaceManager.getInstance().executeRequest(POST, path, profilesUpdateParams.asXML());
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * Set the active avatar of the user.
     *
     * @param userID   the userID
     * @param avatarID the avatarID
     */
    private void setActiveAvatar(long userID, long avatarID) {
        try {
            Document profilesDoc = DocumentHelper.createDocument();
            Element rootE = profilesDoc.addElement("setActiveAvatar");
            rootE.addElement("userID").setText(String.valueOf(userID));
            rootE.addElement("avatarID").setText(String.valueOf(avatarID));

            // Requests the user active avatar
            String path = AVATAR_URL_PREFIX + "activeAvatar/" + userID;

            ClearspaceManager.getInstance().executeRequest(POST, path, rootE.asXML());
        } catch (Exception e) {
            throw new UnsupportedOperationException("Error setting the user's " + userID + " active avatar " + avatarID, e);
        }
    }

    /**
     * Creates the avatar.
     *
     * @param avatarCreateParams the avatar information
     * @return the new avatarID
     */
    private long createAvatar(Element avatarCreateParams) {
        try {

            // Requests the user active avatar
            String path = AVATAR_URL_PREFIX + "avatars";
            Element avatar = ClearspaceManager.getInstance().executeRequest(POST, path, avatarCreateParams.asXML());

            return Long.valueOf(avatar.element("return").element("WSAvatar").elementTextTrim("id"));
        } catch (Exception e) {
            throw new UnsupportedOperationException("Error creating the avatar", e);
        }
    }

    /**
     * Returns the profiles of the user.
     *
     * @param userID the user id.
     * @return the profiles.
     */
    private Element getProfiles(long userID) {
        try {
            // Requests the user profile
            String path = PROFILE_URL_PREFIX + "profiles/" + userID;
            return ClearspaceManager.getInstance().executeRequest(GET, path);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Error getting the profiles of user: " + userID, e);
        }
    }

    /**
     * Return the avatar of the user.
     *
     * @param userID the user id.
     * @return the avatar.
     */
    private Element getAvatar(long userID) {
        try {
            // Requests the user active avatar
            String path = AVATAR_URL_PREFIX + "activeAvatar/" + userID;
            return ClearspaceManager.getInstance().executeRequest(GET, path);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Error getting the avatar of user: " + userID, e);
        }
    }

    /**
     * Tries to load the avatar read only info.
     */
    private void loadAvatarReadOnly() {
        try {
            // See if the is read only
            String path = AVATAR_URL_PREFIX + "userAvatarsEnabled";
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);
            avatarReadOnly = !Boolean.valueOf(getReturn(element));
        } catch (Exception e) {
            // if there is a problem, keep it null, maybe next call success.
            Log.warn("Error loading the avatar read only information", e);
        }
    }

    /**
     * Tries to load the default profiles fields info.
     */
    private void loadDefaultProfileFields() {
        try {
            String path = PROFILE_FIELDS_URL_PREFIX + "fields";
            Element defaultFields = ClearspaceManager.getInstance().executeRequest(GET, path);

            ClearspaceVCardTranslator.getInstance().initClearspaceFieldsId(defaultFields);
            fieldsIDLoaded = true;
        } catch (Exception e) {
            // if there is a problem, keep it null, maybe next call success.
            Log.warn("Error loading the default profiles fields", e);
        }

    }

    /**
     * Returns an element that can be used as a parameter to create an avatar.
     * This element has the user's avatar information.
     *
     * @param userID the id of user.
     * @return the element with that can be used to create an Avatar.
     * @throws UserNotFoundException if the userID is invalid.
     * @throws Exception             if there is problem doing the request.
     */
    private Element getAvatarCreateParams(long userID) throws Exception {

        // Creates response element
        Element avatarCreateParams = DocumentHelper.createDocument().addElement("createAvatar");

        // Gets current avatar
        Element avatarResponse = getAvatar(userID);

        // Translates from the response to create params
        Element avatar = avatarResponse.element("return");
        if (avatar != null) {
            // Sets the owner
            avatarCreateParams.addElement("ownerID").setText(avatar.elementText("owner"));

            // Sets the attachment values
            Element attachment = avatar.element("attachment");
            if (attachment != null) {
                avatarCreateParams.addElement("name").setText(attachment.elementText("name"));
                avatarCreateParams.addElement("contentType").setText(attachment.elementText("contentType"));
                avatarCreateParams.addElement("data").setText(attachment.elementText("data"));
            }
        }

        return avatarCreateParams;
    }

    /**
     * Returns an element that can be used as a parameter to modify the user profiles.
     * This element has the user's avatar information.
     *
     * @param userID the id of user.
     * @return the element with that can be used to create an Avatar.
     * @throws UserNotFoundException if the userID is invalid.
     * @throws Exception             if there is problem doing the request.
     */
    private Element getProfilesUpdateParams(long userID) throws Exception {
        Element params = DocumentHelper.createDocument().addElement("setProfile");

        // Add the userID param
        params.addElement("userID").setText(String.valueOf(userID));

        // Gets current profiles to merge the information
        Element currentProfile = getProfiles(userID);

        // Adds the current profiles to the new profile
        addProfiles(currentProfile, params);

        return params;
    }

    /**
     * Adds the profiles elements from one profile to the other one.
     *
     * @param currentProfile the profile with the information.
     * @param newProfiles    the profile to copy the information to.
     */
    private void addProfiles(Element currentProfile, Element newProfiles) {

        // Gets current fields
        List<Element> fields = (List<Element>) currentProfile.elements("return");

        // Iterate over current fields
        for (Element field : fields) {

            // Get the fieldID and values
            String fieldID = field.elementText("fieldID");
            // The value name of the value field could be value or values
            Element value = field.element("value");
            boolean multiValues = false;
            if (value == null) {
                value = field.element("values");
                if (value != null) {
                    multiValues = true;
                }
            }

            // Don't add empty field. Field id 0 means no field.
            if ("0".equals(fieldID)) {
                continue;
            }

            // Adds the profile to the new profiles element
            Element newProfile = newProfiles.addElement("profiles");
            newProfile.addElement("fieldID").setText(fieldID);
            // adds the value if it is not empty
            if (value != null) {
                if (multiValues) {
                    newProfile.addElement("values").setText(value.getText());
                } else {
                    newProfile.addElement("value").setText(value.getText());
                }
            }
        }
    }
}
