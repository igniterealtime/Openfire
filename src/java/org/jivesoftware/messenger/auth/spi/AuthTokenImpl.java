/**
 * DbAuthToken.java
 * November 17, 2000
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.auth.spi;

import org.jivesoftware.messenger.auth.AuthToken;
import java.io.Serializable;

/**
 * Database implementation of the AuthToken interface.
 *
 * @author Iain Shigeoka
 */
public final class AuthTokenImpl implements AuthToken, Serializable {

    private static final long serialVersionUID = 01L;

    private long userID;

    /**
     * Constucts a new DbAuthToken with the specified userID.
     *
     * @param userID the userID to create an authToken token with.
     */
    public AuthTokenImpl(long userID) {
        this.userID = userID;
    }

    // AuthToken Interface

    public long getUserID() {
        return userID;
    }

    public boolean isAnonymous() {
        return userID == -1;
    }
}