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
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.*;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardProvider;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.Log;

import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Daniel Henninger
 */
public class ClearspaceVCardProvider implements VCardProvider {

    private static final int CS_FIELD_ID_TITLE = 1;
    private static final int CS_FIELD_ID_DEPARTMENT = 2;
    private static final int CS_FIELD_ID_TIME_ZONE = 9;
    private static final int CS_FIELD_ID_WORK_ADDRESS = 11;
    private static final int CS_FIELD_ID_HOME_ADDRESS = 3;
    private static final int CS_FIELD_ID_ALTERNATE_EMAIL = 10;
    private static final int CS_FIELD_ID_URL = 5;

    protected static final String PROFILE_URL_PREFIX = "profileService/";
    protected static final String AVATAR_URL_PREFIX = "avatarService/";

    private ClearspaceManager manager;
    private Boolean avatarReadOnly;
    private Boolean readOnly;

    public ClearspaceVCardProvider() {
        this.manager = ClearspaceManager.getInstance();
        loadReadOnly();
    }

    public Element loadVCard(String username) {
        try {

            // Gets the user
            User user = UserManager.getInstance().getUser(username);

            // TODO should use the the user insted of requesting again
            long userID = manager.getUserID(username);

            // Requests the user profile
            String path = PROFILE_URL_PREFIX + "profiles/" + userID;
            Element profile = manager.executeRequest(GET, path);

            Element avatar = getAvatar(userID);

            // Translate the response
            return translate(profile, user, avatar);

        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Error loading the user", e);
        }
    }

    private Element getAvatar(long userID) throws Exception {
        // Requests the user active avatar
        String path = AVATAR_URL_PREFIX + "activeAvatar/" + userID;
        Element avatar = manager.executeRequest(GET, path);
        return avatar;
    }

    public Element createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        return saveVCard(username, vCardElement);
    }

    public Element updateVCard(String username, Element vCardElement) throws NotFoundException {
        return saveVCard(username, vCardElement);
    }

    private Element saveVCard(String username, Element vCardElement) {
        Log.debug("Saving VCARD: "+vCardElement.asXML());

        Document profilesDoc =  DocumentHelper.createDocument();
        Element rootE = profilesDoc.addElement("setProfile");

        try {
            long userID = manager.getUserID(username);

            // Add the userID param
            rootE.addElement("userID").setText(String.valueOf(userID));

            // Add all the profiles elements
            Element profiles = rootE.addElement("profiles");

            // Add the Title
            addNotEmptyProfile(vCardElement, "TITLE", CS_FIELD_ID_TITLE, profiles);

            // Add the Department
            Element tmpElement = vCardElement.element("ORG");
            if (tmpElement != null) {
                addNotEmptyProfile(tmpElement, "ORGUNIT", CS_FIELD_ID_DEPARTMENT, profiles);
            }

            // Add the home and work address
            List<Element> addressElements = (List<Element>) vCardElement.elements("ADR");
            if (addressElements != null) {
                for (Element address : addressElements) {
                    if (address.element("WORK") != null) {
                        addProfile(CS_FIELD_ID_WORK_ADDRESS, marshallAddress(address), profiles);
                    } else if (address.element("HOME") != null) {
                        addProfile(CS_FIELD_ID_HOME_ADDRESS, marshallAddress(address), profiles);
                    }
                }
            }

            // Add the URL
            addNotEmptyProfile(vCardElement, "URL", CS_FIELD_ID_URL, profiles);

            // Add the prefered and alternative email address
            List<Element> emailsElement = (List<Element>) vCardElement.elements("EMAIL");
            if (emailsElement != null) {
                for (Element email : emailsElement) {
                    if (email.element("PREF") == null) {
                        addNotEmptyProfile(email, "USERID", CS_FIELD_ID_ALTERNATE_EMAIL, profiles);
                    } else {
                        String emailAddress = email.elementTextTrim("USERID");
                        if (emailAddress != null && !"".equals(emailAddress)) {
                            // The prefered email is stored in the user
                            UserManager.getUserProvider().setEmail(username, emailAddress);
                        }
                    }
                }
            }

            // Add the Full name to the user
            String fullName = vCardElement.elementTextTrim("FN");
            if (fullName != null && !"".equals(fullName)) {
                UserManager.getUserProvider().setName(username, fullName);
            }


            try {

                String path = PROFILE_URL_PREFIX + "profiles";

                Element group = manager.executeRequest(POST, path, rootE.asXML());
            } catch (Exception e) {
                // It is not supported exception, wrap it into an UnsupportedOperationException
                throw new UnsupportedOperationException("Unexpected error", e);
            }


            try {

                // TODO save some avatars in a cache to avoid getting them all the time
                // Gets the user's current avatar
                Element currAvatar = getAvatar(userID);
                String[] currAvatarData = getAvatarContentTypeAndImage(currAvatar);

                Element photoElement = vCardElement.element("PHOTO");
                if (photoElement != null) {
                    String contentType = photoElement.elementTextTrim("TYPE");
                    String data = photoElement.elementTextTrim("BINVAL");

                    if (contentType == null && currAvatarData[0] != null) {
                        // new avatar
                        long avatarID = createAvatar(contentType, data, userID, username);
                        setActiveAvatar(userID, avatarID);
                    } else if (contentType != null && currAvatarData[0] == null) {
                        // delete
                        setActiveAvatar(userID, -1);
                    } else if ((contentType != null && !contentType.equals(currAvatarData[0])) ||
                            (data != null && !data.equals(currAvatarData[1]))) {
                        // modify
                        long avatarID = createAvatar(contentType, data, userID, username);
                        setActiveAvatar(userID, avatarID);
                    }
                }

            } catch (Exception e) {
                // It is not supported exception, wrap it into an UnsupportedOperationException
                throw new UnsupportedOperationException("Error loading the user", e);
            }


        } catch (UserNotFoundException e) {
            throw new UnsupportedOperationException("User not found", e);
        }

        return loadVCard(username);
    }

    private void setActiveAvatar(long userID, long avatarID) throws UserNotFoundException {
        try {
            Document profilesDoc =  DocumentHelper.createDocument();
            Element rootE = profilesDoc.addElement("setActiveAvatar");
            rootE.addElement("userID").setText(String.valueOf(userID));
            rootE.addElement("avatarID").setText(String.valueOf(avatarID));

            // Requests the user active avatar
            String path = AVATAR_URL_PREFIX + "activeAvatar/" + userID;
            
            manager.executeRequest(POST, path, rootE.asXML());

        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Error creating the avatar", e);
        }
    }

    private long createAvatar(String contentType, String data, long userID, String username) throws UserNotFoundException {
        try {
            Document profilesDoc =  DocumentHelper.createDocument();
            Element rootE = profilesDoc.addElement("createAvatar");
            rootE.addElement("ownerID").setText(String.valueOf(userID));
            rootE.addElement("name").setText(String.valueOf(username));
            rootE.addElement("contentType").setText(String.valueOf(contentType));
            rootE.addElement("data").setText(String.valueOf(data));

            // Requests the user active avatar
            String path = AVATAR_URL_PREFIX + "createAvatar/" + userID;

            Element avatar = manager.executeRequest(POST, path, rootE.asXML());

            long id = Long.valueOf(avatar.element("return").element("WSAvatar").elementTextTrim("id"));
            return id;
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Error creating the avatar", e);
        }
    }

    private void addNotEmptyProfile(Element elements, String elementName, int fieldID, Element profiles) {
        String value = elements.elementTextTrim(elementName);
        if (value != null && !"".equals(value)) {
            addProfile(fieldID, value, profiles);
        }
    }
                         
    private void addProfile(int fieldID, String value, Element profiles) {
        Element profile = profiles.addElement("WSUserProfile");
        profile.addElement("fieldID").setText(String.valueOf(fieldID));
        profile.addElement("value").setText(value);
    }
    
    private String marshallAddress(Node address) {

        StringBuilder sb = new StringBuilder();

        addField(address, "STREET", "street1", sb);

        addField(address, "EXTADD", "street2", sb);

        addField(address, "LOCALITY", "city", sb);

        addField(address, "REGION", "state", sb);

        addField(address, "CTRY", "country", sb);

        addField(address, "PCODE", "zip", sb);

        Node tmpNode = address.selectSingleNode("HOME");
        if (tmpNode != null) {
            sb.append("type:HOME");
        }

        tmpNode = address.selectSingleNode("WORK");
        if (tmpNode != null) {
            sb.append("type:WORK");
        }

        return sb.toString();
    }

    private void addField(Node node, String nodeName, String fieldName, StringBuilder sb) {
        Node tmpNode = node.selectSingleNode(nodeName);
        if (tmpNode != null) {
            sb.append(fieldName).append(":").append(tmpNode.getText());
        }
    }


    public void deleteVCard(String username) {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        long userID = -1;
        try {
            userID = manager.getUserID(username);
        } catch (UserNotFoundException gnfe) {
            // it is ok, the user doesn't exist "anymore"
            return;
        }

        if (!isAvatarReadOnly()) {
            try {
                String path = AVATAR_URL_PREFIX + "avatar/" + userID;
                manager.executeRequest(DELETE, path);
            } catch (Exception e) {
                // It is not supported exception, wrap it into an UnsupportedOperationException
                throw new UnsupportedOperationException("Unexpected error", e);
            }
        }

        try {
            String path = PROFILE_URL_PREFIX + "profiles/" + userID;
            manager.executeRequest(DELETE, path);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public boolean isReadOnly() {/*
        if (readOnly == null) {
            loadReadOnly();
        }
        return readOnly == null ? false : readOnly;*/
        return true;
    }

    private void loadReadOnly() {
        boolean userReadOnly = UserManager.getUserProvider().isReadOnly();

        try {
            // See if the is read only
            String path = AVATAR_URL_PREFIX + "userAvatarsEnabled";
            Element element = manager.executeRequest(GET, path);
            avatarReadOnly = !Boolean.valueOf(getReturn(element));
        } catch (Exception e) {
            // if there is a problem, keep it null, maybe in the next call succes.
            return;
        }

        readOnly = userReadOnly || avatarReadOnly; 
    }

    private Element translate(Element profile, User user, Element avatar) {

        Document vCardDoc =  DocumentHelper.createDocument();
        Element vCard = vCardDoc.addElement("vCard", "vcard-temp");

        addUserInformation(user, vCard);
        addProfileInformation(profile, vCard);
        addAvatarInformation(avatar, vCard);

        return vCard;
    }

    private void addProfileInformation(Element profile, Element vCard) {
        // Translate the profile XML

        List<Node> fields = (List<Node>) profile.selectNodes("return");

        /* Profile response sample
        <ns1:getProfileResponse xmlns:ns1="http://jivesoftware.com/clearspace/webservices">
            <return>
                <fieldID>2</fieldID>
                <value>RTC</value>
            </return>
            <return>
                <fieldID>9</fieldID>
                <value>-300</value>
            </return>
            <return>
                <fieldID>11</fieldID>
                <value>street1:San Martin,street2:1650,city:Cap Fed,state:Buenos Aires,country:Argentina,zip:1602,type:HOME</value>
            </return>
            <return>
                <fieldID>1</fieldID>
                <value>Mr.</value>
            </return>
            <return>
                <fieldID>3</fieldID>
                <value>street1:Alder 2345,city:Portland,state:Oregon,country:USA,zip:32423,type:WORK</value>
            </return>
            <return>
                <fieldID>10</fieldID>
                <values>gguardin@gmail.com|work</values>
            </return>
            <return>
                <fieldID>5</fieldID>
                <values>http://www.gguardin.com.ar</values>
            </return>
        </ns1:getProfileResponse>
        */

        for (Node field : fields) {
            int fieldID = Integer.valueOf(field.selectSingleNode("fieldID").getText());
            // The value name of the value field could be value or values
            Node fieldNode = field.selectSingleNode("value");
            if (fieldNode == null) {
                fieldNode = field.selectSingleNode("values");
            }
            // if it is an empty field, continue with the next field
            if (fieldNode == null) {
                continue;
            }

            // get the field value
            String fieldValue = fieldNode.getText();

            switch (fieldID) {
                case CS_FIELD_ID_TITLE:
                    vCard.addElement("TITLE").setText(fieldValue);
                    break;
                case CS_FIELD_ID_DEPARTMENT:
                    vCard.addElement("ORG").addElement("ORGUNIT").setText(fieldValue);
                    break;
                case CS_FIELD_ID_TIME_ZONE:
                    //TODO check if the time zone is ISO
                    vCard.addElement("TZ").setText(fieldValue);
                    break;
                case CS_FIELD_ID_WORK_ADDRESS:
                    Element workAdr = vCard.addElement("ADR");
                    workAdr.addElement("WORK");
                    addAddress(fieldValue, workAdr);
                    break;
                case CS_FIELD_ID_HOME_ADDRESS:
                    Element homeAdr = vCard.addElement("ADR");
                    homeAdr.addElement("HOME");
                    addAddress(fieldValue, homeAdr);
                    break;
                case CS_FIELD_ID_URL:
                    vCard.addElement("URL").setText(fieldValue);
                    break;
                case CS_FIELD_ID_ALTERNATE_EMAIL:
                    vCard.addElement("EMAIL").addElement("USERID").setText(fieldValue);
                    break;
            }
        }
    }

    private void addUserInformation(User user, Element vCard) {
        // The name could be null (if in Clearspace the name is not visible in Openfire it is null)
        if (user.getName() != null && !"".equals(user.getName().trim())) {
            vCard.addElement("FN").setText(user.getName());
        }

        // Email is mandatory, but may be invisible
        if (user.getEmail() != null && !"".equals(user.getName().trim())) {
            Element email = vCard.addElement("EMAIL");
            email.addElement("PREF");
            email.addElement("USERID").setText(user.getEmail());
            // TODO emails == jabber id? jabber id is ok?
            vCard.addElement("JABBERID").setText(user.getEmail());
        }

    }

    private void addAvatarInformation(Element avatarResponse, Element vCard) {
        String[] avatarData = getAvatarContentTypeAndImage(avatarResponse);

        if (avatarData[0] != null && avatarData[1] != null) {
            // Add the avatar to the vCard
            Element photo = vCard.addElement("PHOTO");
            photo.addElement("TYPE").setText(avatarData[0]);
            photo.addElement("BINVAL").setText(avatarData[1]);
        }
    }

    private String[] getAvatarContentTypeAndImage(Element avatarResponse) {
        String[] result = new String[2];

        Element avatar = avatarResponse.element("return");
        if (avatar != null) {
            Element attachment = avatar.element("attachment");
            if (attachment != null) {
                result[0] = attachment.element("contentType").getText();
                result[1] = attachment.element("data").getText();
            }
        }
        return result;
    }

    private void addAddress(String address, Element addressE) {
        StringTokenizer strTokenize = new StringTokenizer(address, ",");
        while(strTokenize.hasMoreTokens()) {
            String token = strTokenize.nextToken();
            int index = token.indexOf(":");
            String field = token.substring(0, index);
            String value = token.substring(index + 1);

            if ("street1".equals(field)) {
                addressE.addElement("STREET").setText(value);

            } else if ("street2".equals(field)) {
                addressE.addElement("EXTADD").setText(value);

            } else if ("city".equals(field)) {
                addressE.addElement("LOCALITY").setText(value);

            } else if ("state".equals(field)) {
                addressE.addElement("REGION").setText(value);

            } else if ("country".equals(field)) {
                addressE.addElement("CTRY").setText(value);

            } else if ("zip".equals(field)) {
                addressE.addElement("PCODE").setText(value);

            } else if ("type".equals(field)) {
                if ("HOME".equals(value)) {
                    addressE.addElement("HOME");
                } else if ("WORK".equals(value)) {
                    addressE.addElement("WORK");
                }
            }
        }
        
    }

    public boolean isAvatarReadOnly() {
        return avatarReadOnly;
    }
}
