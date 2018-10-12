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

package org.jivesoftware.openfire.handler;

import org.jivesoftware.openfire.auth.UnauthorizedException;

/**
 * Handle the various user registration settings that are
 * valid under XMPP. Although user registration is a fairly
 * flexible beast in XMPP, much of registration is redundant to
 * vCard and no mainstream clients support registration fields
 * with much fidelity anyhow. So registration is kept simple,
 * and we defer to vCard for user information gathering.
 *
 * @author Iain Shigeoka
 */
public interface IQRegisterInfo {

    /**
     * An unknown type
     */
    int UNKNOWN = -1;
    /**
     * The user's email
     */
    int EMAIL = 0;
    /**
     * The user's full name
     */
    int NAME = 1;
    /**
     * The user's first name
     */
    int FIRST_NAME = 2;
    /**
     * The user's last name
     */
    int LAST_NAME = 3;
    /**
     * The user's address
     */
    int ADDRESS = 4;
    /**
     * The user's city
     */
    int CITY = 5;
    /**
     * The user's state
     */
    int STATE = 6;
    /**
     * The user's zip code
     */
    int ZIP = 7;
    /**
     * The user's phone
     */
    int PHONE = 8;
    /**
     * The user's url
     */
    int URL = 9;
    /**
     * The date of registration
     */
    int DATE = 10;
    /**
     * Misc data to associate with the account
     */
    int MISC = 11;
    /**
     * Misc text to associate with the account
     */
    int TEXT = 12;

    /**
     * Element names of the fields, array index matches type
     */
    String[] FIELD_NAMES = {
        "email",
        "name",
        "first",
        "last",
        "address",
        "city",
        "state",
        "zip",
        "phone",
        "url",
        "date",
        "misc",
        "text"
    };

    /**
     * Fields should be stored in user properties
     */
    int FIELD_IN_USER_PROPS = 0;
    /**
     * Fields should be stored in vCard
     */
    int FIELD_IN_VCARD = 0;

    /**
     * Determines where field information is stored.
     *
     * @return the location type.
     */
    int getFieldStoreLocation();

    /**
     * Sets the location for storing field information.
     *
     * @param location The location type
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          If you don't have permission to adjust this setting
     */
    void setFieldStoreLocation( int location ) throws UnauthorizedException;

    /**
     * Determines if users can automatically register user accounts
     * without system administrator intervention.
     *
     * @return True if open registration is supported
     */
    boolean isOpenRegistrationSupported();

    /**
     * Tells the server whether to support open registration or not.
     *
     * @param isSupported True if open registration is supported
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          If you don't have permission to change this setting
     */
    void setOpenRegistrationSupported( boolean isSupported ) throws UnauthorizedException;

    /**
     * Determines if a given field is required for registration.
     *
     * @param fieldType The field to check
     * @return True if the field is required
     */
    boolean isFieldRequired( int fieldType );

    /**
     * Tells the server whether to require a registration field or not.
     *
     * @param fieldType  The field to require.
     * @param isRequired True if the field should be required
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          If you don't have permission to change this setting
     */
    void setFieldRequired( int fieldType, boolean isRequired ) throws UnauthorizedException;

    /**
     * Get the setting type from a field's element name. This is a convenience
     * for looking up the correct field type from an element name.
     *
     * @param fieldElementName The known element name
     * @return The field type, one of the static int types defined in this class
     */
    int getFieldType( String fieldElementName );

    /**
     * Obtain the element name from a field type. This is a convience for
     * looking up the correct field element name from it's field type.
     *
     * @param fieldType The known field type
     * @return The field element name
     */
    String getFieldElementName( int fieldType );
}
