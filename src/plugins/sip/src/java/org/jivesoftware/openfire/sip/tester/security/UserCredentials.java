/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.tester.security;

/**
 * Title: SIP Register Tester
 * Description:JAIN-SIP Test application
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class UserCredentials {
    /**
     */
    private static String userName = null;

    /**
     */
    private static char[] password = null;

    /**
     */
    private static String authUserName = null;

    /**
     */
    private static String displayName = null;

    /**
     * Sets the user name.
     *
     * @param userName The user name to set.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Sets the user name.
     *
     * @param userName The user name to set in Authentication challenge.
     */
    public void setAuthUserName(String userName) {
        this.authUserName = userName;
    }

    public static void clean() {
        UserCredentials.userName = null;
        UserCredentials.password = null;
        UserCredentials.authUserName = null;
    }

    public static String getUserDisplay() {
        return UserCredentials.displayName == null ? UserCredentials.userName : UserCredentials.displayName;
    }

    /**
     * Returns the user name.
     *
     * @return the user name.
     */
    public String getUserName() {
        return this.userName;
    }

    /**
     * Returns the user name.
     *
     * @return the user name.
     */
    public String getAuthUserName() {
        return this.authUserName != null && !this.authUserName.equals("") ? this.authUserName
                : this.userName;
    }

    /**
     * Sets the user password.
     *
     * @param passwd The password associated with username
     */
    public void setPassword(char[] passwd) {
        this.password = passwd;
    }

    /**
     * Returns these credentials' password
     *
     * @return these credentials' password
     */
    public char[] getPassword() {
        return UserCredentials.password;
	}

}
