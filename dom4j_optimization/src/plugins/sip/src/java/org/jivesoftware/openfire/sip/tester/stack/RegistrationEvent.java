/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
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
