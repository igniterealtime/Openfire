/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.ldap;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LongList;

import javax.naming.*;
import javax.naming.directory.*;

/**
 * LDAP implementation of the UserIDProvider interface.
 *
 * @author Matt Tucker
 */
public class LdapUserIDProvider {

    private LdapManager manager;

    public LdapUserIDProvider() {
        manager = LdapManager.getInstance();
    }

    public int getUserCount() {
        int count = 0;
        // Note: the performance of this check may suffer badly for very large
        // numbers of users since we manually iterate through results to get
        // a count.
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{manager.getUsernameField()});
            String filter = "(" + manager.getUsernameField() + " + =*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);
            while (answer.hasMoreElements()) {
                count++;
                answer.nextElement();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return count;
    }

    public LongList getUserIDs() {
        LongList users = new LongList(500);
        // Otherwise, in LDAP-only mode.
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{manager.getUsernameField()});
            String filter = "(" + manager.getUsernameField() + " + =*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);

            while (answer.hasMoreElements()) {
                // Get the next userID.
                users.add(Long.parseLong((String)(((SearchResult)answer.next()).getAttributes().get("jiveUserID")).get()));
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return users;
    }

    public LongList getUserIDs(int startIndex, int numResults) {
        LongList users = new LongList();
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{manager.getUsernameField()});
            String filter = "(" + manager.getUsernameField() + " + =*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);
            for (int i = 0; i < startIndex; i++) {
                answer.next();
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next userID.
                    users.add(Long.parseLong((String)(((SearchResult)answer.next()).getAttributes().get("jiveUserID")).get()));
                }
                else {
                    break;
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return users;
    }

    /**
     * Helper function to retrieve username from userDN.
     *
     * @param userDN
     * @return username
     * @throws NamingException
     */
    private String getUsernameFromUserDN(String userDN) throws NamingException {
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Load record.
            String[] attributes = new String[]{manager.getUsernameField()};
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            return (String)attrs.get(manager.getUsernameField()).get();
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }
}