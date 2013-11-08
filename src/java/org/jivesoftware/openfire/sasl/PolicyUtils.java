/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

package org.jivesoftware.openfire.sasl;

import javax.security.sasl.Sasl;
import java.util.Map;

/**
 * Static class that contains utilities for dealing with Java SASL
 * security policy-related properties.
 *
 * @author Rosanna Lee
 */
final public class PolicyUtils {
    // Can't create one of these
    private PolicyUtils() {
    }

    public final static int NOPLAINTEXT = 0x0001;
    public final static int NOACTIVE = 0x0002;
    public final static int NODICTIONARY = 0x0004;
    public final static int FORWARD_SECRECY = 0x0008;
    public final static int NOANONYMOUS = 0x0010;
    public final static int PASS_CREDENTIALS = 0x0200;

    /**
     * Determines whether a mechanism's characteristics, as defined in flags,
     * fits the security policy properties found in props.
     * @param flags The mechanism's security characteristics
     * @param props The security policy properties to check
     * @return true if passes; false if fails
     */
    public static boolean checkPolicy(int flags, Map props) {
        if (props == null) {
            return true;
        }

        if ("true".equalsIgnoreCase((String)props.get(Sasl.POLICY_NOPLAINTEXT))
            && (flags&NOPLAINTEXT) == 0) {
            return false;
        }
        if ("true".equalsIgnoreCase((String)props.get(Sasl.POLICY_NOACTIVE))
            && (flags&NOACTIVE) == 0) {
            return false;
        }
        if ("true".equalsIgnoreCase((String)props.get(Sasl.POLICY_NODICTIONARY))
            && (flags&NODICTIONARY) == 0) {
            return false;
        }
        if ("true".equalsIgnoreCase((String)props.get(Sasl.POLICY_NOANONYMOUS))
            && (flags&NOANONYMOUS) == 0) {
            return false;
        }
        if ("true".equalsIgnoreCase((String)props.get(Sasl.POLICY_FORWARD_SECRECY))
            && (flags&FORWARD_SECRECY) == 0) {
            return false;
        }
        if ("true".equalsIgnoreCase((String)props.get(Sasl.POLICY_PASS_CREDENTIALS))
            && (flags&PASS_CREDENTIALS) == 0) {
            return false;
        }

        return true;
    }

    /**
     * Given a list of mechanisms and their characteristics, select the
     * subset that conforms to the policies defined in props.
     * Useful for SaslXXXFactory.getMechanismNames(props) implementations.
     *
     */
    public static String[] filterMechs(String[] mechs, int[] policies,
        Map props) {
        if (props == null) {
            return mechs.clone();
        }

        boolean[] passed = new boolean[mechs.length];
        int count = 0;
        for (int i = 0; i< mechs.length; i++) {
            if (passed[i] = checkPolicy(policies[i], props)) {
                ++count;
            }
        }
        String[] answer = new String[count];
        for (int i = 0, j=0; i< mechs.length; i++) {
            if (passed[i]) {
                answer[j++] = mechs[i];
            }
        }

        return answer;
    }
}
