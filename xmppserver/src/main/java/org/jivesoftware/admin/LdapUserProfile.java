/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.admin;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.jivesoftware.openfire.ldap.LdapManager;
import org.jivesoftware.openfire.ldap.LdapVCardProvider;
import org.jivesoftware.openfire.vcard.VCardBean;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Bean that stores the vcard mapping. It is also responsible for saving the mapping
 * as a system property and retrieving it.
 *
 * @author Gaston Dombiak
 */
public class LdapUserProfile extends VCardBean
{
    private static final Logger Log = LoggerFactory.getLogger(LdapUserProfile.class);

    private Boolean avatarStoredInDB = false;

    public Boolean getAvatarStoredInDB() {
        return avatarStoredInDB;
    }

    public void setAvatarStoredInDB(Boolean avatarStoredInDB) {
        if (avatarStoredInDB == null) {
            this.avatarStoredInDB = false;
        }
        else {
            this.avatarStoredInDB = avatarStoredInDB;
        }
    }

    /**
     * Sets default mapping values when using an Active Directory server.
     */
    public void initForActiveDirectory() {
        name = "{cn}";
        email = "{mail}";
        fullName = "{displayName}";
        nickname = "";
        birthday = "";
        photo = "{jpegPhoto}";
        homeStreet = "{homePostalAddress}";
        homeCity = "";
        homeState = "";
        homeZip = "{homeZip}";
        homeCountry = "{co}";
        homePhone = "{homePhone}";
        homeMobile = "{mobile}";
        homeFax = "";
        homePager = "";
        businessStreet = "{streetAddress}";
        businessCity = "{l}";
        businessState = "{st}";
        businessZip = "{postalCode}";
        businessCountry = "{co}";
        businessJobTitle = "{title}";
        businessDepartment = "{department}";
        businessPhone = "{telephoneNumber}";
        businessMobile = "{mobile}";
        businessFax = "{facsimileTelephoneNumber}";
        businessPager = "{pager}";
        avatarStoredInDB = false;
    }

    /**
     * Sets default mapping values when using an Active Directory server.
     */
    public void initForOpenLDAP() {
        name = "{cn}";
        email = "{mail}";
        fullName = "{displayName}";
        nickname = "{uid}";
        birthday = "";
        photo = "{jpegPhoto}";
        homeStreet = "{homePostalAddress}";
        homeCity = "";
        homeState = "";
        homeZip = "";
        homeCountry = "";
        homePhone = "{homePhone}";
        homeMobile = "";
        homeFax = "";
        homePager = "";
        businessStreet = "{postalAddress}";
        businessCity = "{l}";
        businessState = "{st}";
        businessZip = "{postalCode}";
        businessCountry = "";
        businessJobTitle = "{title}";
        businessDepartment = "{departmentNumber}";
        businessPhone = "{telephoneNumber}";
        businessMobile = "{mobile}";
        businessFax = "";
        businessPager = "{pager}";
        avatarStoredInDB = false;
    }

    /**
     * Saves current configuration as XML/DB properties.
     */
    public void saveProperties() {
        final Element vCard = asElement();

        // Generate content to store in property
        String vcardXML;
        StringWriter writer = new StringWriter();
        OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
        XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
        try {
            xmlWriter.write(vCard);
            vcardXML = writer.toString();
        }
        catch (IOException e) {
            Log.error("Error pretty formating XML", e);
            vcardXML = vCard.asXML();
        }

        StringBuilder sb = new StringBuilder(vcardXML.length());
        sb.append("<![CDATA[").append(vcardXML).append("]]>");
        // Save mapping as an XML property
        JiveGlobals.setProperty("ldap.vcard-mapping", sb.toString());

        // Set that the vcard provider is LdapVCardProvider
        VCardManager.VCARD_PROVIDER.setValue(LdapVCardProvider.class);

        // Save duplicated fields in LdapManager (should be removed in the future)
        LdapManager.getInstance().setNameField(new LdapUserTester.PropertyMapping(name));
        LdapManager.getInstance().setEmailField(email.replaceAll("(\\{)([\\d\\D&&[^}]]+)(})", "$2"));

        // Store the DB storage variable in the actual database.
        LdapVCardProvider.STORE_AVATAR_IN_DB.setValue(avatarStoredInDB);
    }

    /**
     * Returns true if the vCard mappings where successfully loaded from the XML/DB
     * properties.
     *
     * @return true if mappings where loaded from saved property.
     */
    public boolean loadFromProperties() {
        String xmlProperty = JiveGlobals.getProperty("ldap.vcard-mapping");
        if (xmlProperty == null || xmlProperty.trim().isEmpty()) {
            return false;
        }

        try {
            // Remove CDATA wrapping element
            if (xmlProperty.startsWith("<![CDATA[")) {
                xmlProperty = xmlProperty.substring(9, xmlProperty.length()-3);
            }
            // Parse XML
            Document document = DocumentHelper.parseText(xmlProperty);
            Element vCard = document.getRootElement();
            loadFromElement(vCard);
            avatarStoredInDB = LdapVCardProvider.STORE_AVATAR_IN_DB.getValue();
        }
        catch (DocumentException e) {
            Log.error("Error loading vcard mappings from property", e);
            return false;
        }

        return true;
    }
}
