/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.ldap;

import org.jivesoftware.openfire.auth.AuthorizationMapping;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.directory.*;

/**
 * Provider for authorization mapping using LDAP. If the authenticated identity ('principal', whose password is used)
 * did not request an authorization identity ('username', to act as), provide one via LDAP. Specify the lookup field in
 * the system properties. An entry in that file would look like the following:
 *
 * <ul>
 * <li>{@code ldap.princField = k5login}</li>
 * <li>{@code ldap.princSearchFilter = princField={0}}</li>
 * </ul>
 * <p>
 * Each ldap object that represents a user is expected to have exactly one of ldap.usernameField and ldap.princField,
 * and they are both expected to be unique over the search base.
 *
 * A search will be performed over all objects where princField = principal, and the usernameField will be returned.
 *
 * Note that it is expected this search return exactly one object. (There can only be one default) If more than one is
 * returned, the first entry encountered will be used, and no sorting is performed or requested.
 *
 * If more control over the search is needed, you can specify the mapSearchFilter used to perform the LDAP query.

 * This implementation requires that LDAP be configured, obviously.
 *
 * @author Jay Kline
 */
public class LdapAuthorizationMapping implements AuthorizationMapping {

    private static final Logger Log = LoggerFactory.getLogger(LdapAuthorizationMapping.class);

    private LdapManager manager;
    private String princField;
    private String princSearchFilter;

    public LdapAuthorizationMapping() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("ldap.princField");
        JiveGlobals.migrateProperty("ldap.princSearchFilter");

        manager = LdapManager.getInstance();
        princField = JiveGlobals.getProperty("ldap.princField", "k5login");
        princSearchFilter = JiveGlobals.getProperty("ldap.princSearchFilter");
        StringBuilder filter = new StringBuilder();
        if(princSearchFilter == null) {
            filter.append('(').append(princField).append("={0})");
        } else {
            filter.append("(&(").append(princField).append("={0})(");
            filter.append(princSearchFilter).append("))");
        }
        princSearchFilter = filter.toString();
    }

    @Override
    public String map(String authcid) {
        String authzid = authcid;
        DirContext ctx = null;
        try {
            Log.debug("LdapAuthorizationMapping: Starting LDAP search...");
            String usernameField = manager.getUsernameField();
            //String baseDN = manager.getBaseDN();
            boolean subTreeSearch = manager.isSubTreeSearch();
            ctx = manager.getContext();
            SearchControls constraints = new SearchControls();
            if (subTreeSearch) {
                constraints.setSearchScope
            (SearchControls.SUBTREE_SCOPE);
            }
            // Otherwise, only search a single level.
            else {
                constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            constraints.setReturningAttributes(new String[] { usernameField });

            NamingEnumeration answer = ctx.search("", princSearchFilter, 
                    new String[] {LdapManager.sanitizeSearchFilter(authcid)},
                    constraints);
            Log.debug("LdapAuthorizationMapping: ... search finished");
            if (answer == null || !answer.hasMoreElements()) {
                Log.debug("LdapAuthorizationMapping: Username based on principal '" + authcid + "' not found.");
                return authcid;
            }
            Attributes atrs = ((SearchResult)answer.next()).getAttributes();
            Attribute usernameAttribute = atrs.get(usernameField);
            authzid = (String) usernameAttribute.get();
        }
        catch (Exception e) {
            // Ignore.
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to map authorization for principal {}.", authcid, ex);
            }
        }
        return authzid;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    @Override
    public String name() {
        return "LDAP Authorization Mapping";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    @Override
    public String description() {
        return "Provider for authorization using LDAP. Returns the authentication identity's (principal, whose password " +
            "is used) default authorization identity (username to act as) using the attribute specified in ldap.princField.";
    }
}
