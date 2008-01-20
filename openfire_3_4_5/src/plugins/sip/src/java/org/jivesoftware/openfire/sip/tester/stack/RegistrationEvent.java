/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.tester.stack;

import java.util.EventObject;

/**
 * Title: SIP Register Tester
 * Description:JAIN-SIP Test application
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class RegistrationEvent extends EventObject {

    public enum Type {
        Normal, WrongPass, NotFound, Forbidden, WrongAuthUser
    };

    private Type type = Type.Normal;

    public RegistrationEvent(String registrationAddress) {
        super(registrationAddress);
    }

    public RegistrationEvent(String registrationAddress, Type type) {
        super(registrationAddress);
        this.type = type;
    }

    public String getReason() {
        return (String) getSource();
    }

    public Type getType() {
        return type;
    }

}
