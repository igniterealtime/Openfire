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

package org.jivesoftware.openfire.auth;

import java.util.StringTokenizer;
import java.util.Vector;

import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Different clients perform authentication differently, so this policy 
 * will authorize any principal to a requested user that match specific 
 * conditions that are considered secure defaults for most installations. 
 *
 * Keep in mind if a client does not request any username Java copies the
 * authenticated ID to the requested username.
 *
 * <ul>
 * <li>If the authenticated ID is in the form of a plain username, and the 
 *     requested user is in the form of a plain username, then the two must
 *     be exactly the same.  
 * <li>If the authenticated ID contains an '@', then the portion before the 
 *     '@' must match exactly the requested username and the portion after 
 *     the '@' must match at least one of the following:
 *     <ul>
 *     <li>The XMPP domain of the server
 *     <li>The SASL realm of the server
 *     <li>Be in the list of acceptable realms
 *     </ul>
 * <li>If the requested username contains an '@' then the portion before the
 *     '@' will be considered the requested username only if the portion after
 *     the '@' matches the XMPP domain of the server or the portion after the 
 *     '@' in the authenticated ID, if any.
 * </ul>
 *
 * 
 * @see AuthorizationManager
 * @author Jay Kline
 */
public class DefaultAuthorizationPolicy implements AuthorizationPolicy {

    private static final Logger Log = LoggerFactory.getLogger(DefaultAuthorizationPolicy.class);

    private Vector<String> approvedRealms;
    private boolean proxyAuth;

    public DefaultAuthorizationPolicy() {
        approvedRealms = new Vector<>();
        proxyAuth = false;
        
        String realmList = JiveGlobals.getProperty("sasl.approvedRealms");
        if(realmList != null) {
            StringTokenizer st = new StringTokenizer(realmList, " ,\t\n\r\f");
            while(st.hasMoreTokens()) {
                approvedRealms.add(st.nextToken());
            }
        }
        proxyAuth = JiveGlobals.getBooleanProperty("sasl.proxyAuth", false);
    }

    /**
     * Returns true if the principal is explicitly authorized to the JID
     *
     * @param username  The username requested.
     * @param authenID The authenticated ID (principal) requesting the username.
     * @return true if the authenticated ID is authorized to the requested user.
     */
    @Override
    public boolean authorize(String username, String authenID) {
        boolean authorized = false;

        String userUser = username; //I know, I know, dumb variable name...
        String userRealm = null;
 
        String authenUser = authenID;
        String authenRealm = null;

        if(username.contains("@")) {
            userUser = username.substring(0,username.lastIndexOf("@"));
            userRealm = username.substring((username.lastIndexOf("@")+1)); 
        }
        if(authenID.contains("@")){
            authenUser = authenID.substring(0,(authenID.lastIndexOf("@")));
            authenRealm = authenID.substring((authenID.lastIndexOf("@")+1));
        }

        if (!proxyAuth || !AdminManager.getInstance().isUserAdmin(authenUser, true)) {
            if(!userUser.equals(authenUser)) {
                //for this policy the user portion of both must match, so lets short circut here if we can
                if(JiveGlobals.getBooleanProperty("xmpp.auth.ignorecase",true)) {
                    if(!userUser.toLowerCase().equals(authenUser.toLowerCase())){
                        if (Log.isDebugEnabled()) {
                            Log.debug("DefaultAuthorizationPolicy: usernames don't match ("+userUser+" "+authenUser+")");
                        }
                        return false;
                    }
                } else {
                    Log.debug("DefaultAuthorizationPolicy: usernames don't match ("+userUser+" "+authenUser+")");
                    return false;
                }
            }
        }
        Log.debug("DefaultAuthorizationPolicy: Checking authenID realm");
        // Next up, check if the authenID realm is acceptable. 
        if(authenRealm != null) {
            if(authenRealm.equals(XMPPServerInfo.XMPP_DOMAIN.getValue()))  {
                Log.debug("DefaultAuthorizationPolicy: authenRealm = " + XMPPServerInfo.XMPP_DOMAIN.getKey());
                authorized = true;
            } else if(authenRealm.equals(JiveGlobals.getProperty("sasl.realm")))  {
                Log.debug("DefaultAuthorizationPolicy: authenRealm = sasl.realm");
                authorized = true;
            } else { 
                for(String realm : approvedRealms) {
                    if(authenRealm.equals(realm)) {
                        if (Log.isDebugEnabled()) {
                            Log.debug("DefaultAuthorizationPolicy: authenRealm = "+realm+" which is approved");
                        }
                        authorized = true;
                    } else {
                        if (Log.isDebugEnabled()) {
                            Log.debug("DefaultAuthorizationPolicy: authenRealm != "+realm+" which is approved");
                        }
                    }
                }
            }
        } else {
            //no realm in the authenID
            authorized = true;
        }

        if(!authorized) {
            return false;
        }  else {
            //reset for next round of tests
            authorized = false;
        }
        //Next up, check if the username realm is acceptable.
        if(userRealm != null) {
            if(userRealm.equals(XMPPServerInfo.XMPP_DOMAIN.getValue())) {
                Log.debug("DefaultAuthorizationPolicy: userRealm = " + XMPPServerInfo.XMPP_DOMAIN.getKey());
                authorized = true;
            } else {
                if(authenRealm != null && authenRealm.equals(userRealm)) {
                    //authen and username are identical
                    if (Log.isDebugEnabled()) {
                        Log.debug("DefaultAuthorizationPolicy: userRealm = "+authenRealm+" which is approved");
                    }
                    authorized = true;
                }
            }
        } else {
            authorized = true;
        }

        //no more checks
        return authorized;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    @Override
    public String name() {
        return "Default Policy";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    @Override
    public String description() {
        return "Different clients perform authentication differently, so this policy "+ 
               "will authorize any principal to a requested user that match specific "+
               "conditions that are considered secure defaults for most installations.";
    }
}
    
