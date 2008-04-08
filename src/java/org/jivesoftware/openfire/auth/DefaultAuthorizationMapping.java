/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-07 09:28:54 -0500 (Fri, 07 Apr 2006) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.auth;

import java.util.Vector;
import java.util.StringTokenizer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

/**
 * This is the interface the used to provide default default authorization
 * ID's when none was selected by the client.
 * This class simply removes the realm (if any) from the principal if and only if
 * the realm matches the server's realm, the server's xmpp domain name, or 
 * 
 * @author Jay Kline
 */
public class DefaultAuthorizationMapping implements AuthorizationMapping {

    private Vector<String> approvedRealms;

    public DefaultAuthorizationMapping() {
        approvedRealms = new Vector<String>();
        
        String realmList = JiveGlobals.getProperty("sasl.approvedRealms");
        if(realmList != null) {
            StringTokenizer st = new StringTokenizer(realmList, " ,\t\n\r\f");
            while(st.hasMoreTokens()) {
                approvedRealms.add(st.nextToken());
            }
        }
    }

    /**
     * Returns true if the principal is explicity authorized to the JID
     *
     * @param principal The autheticated principal requesting authorization.
     * @return The name of the default username to use.
     */
    public String map(String principal) {
        if(principal.contains("@")) {
            String realm = principal.substring(principal.lastIndexOf('@')+1);
            String username = principal.substring(0,principal.lastIndexOf('@'));

            if(realm.length() > 0) {
                if(realm.equals(JiveGlobals.getProperty("xmpp.domain"))) {
                    Log.debug("DefaultAuthorizationMapping: realm = xmpp.domain");
                    return username;
                } else if(realm.equals(JiveGlobals.getProperty("sasl.realm"))) {
                    Log.debug("DefaultAuthorizationMapping: ream = sasl.realm");
                    return username;
                } else {
                    for(String approvedRealm : approvedRealms) {
                        if(realm.equals(approvedRealm)) {
                            Log.debug("DefaultAuthorizationMapping: realm ("+realm+") = "+approvedRealm+" which is approved");
                            return username;
                        } else {
                            Log.debug("DefaultAuthorizationPolicy: realm ("+realm+") != "+approvedRealm+" which is approved");
                        }
                    }
                }
                Log.debug("DefaultAuthorizationMapping: No approved mappings found.");
                return principal;
            } else {
                Log.debug("DefaultAuthorizationMapping: Realm has no length");
            }
        } else {
            Log.debug("DefaultAuthorizationMapping: No realm found");
        }
        return principal;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public String name() {
        return "Default Mapping";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    public String description() {
        return "Simply remove's the realm of the requesting principal if and only if "+
               "the realm matches the server's realm or the server's xmpp domain name. "+
               "Otherwise the principal is used as the username.";
    }
}