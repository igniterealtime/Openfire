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

import org.jivesoftware.messenger.user.*;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.SortControl;
import java.text.MessageFormat;
import java.util.*;

/**
 * LDAP implementation of the UserProvider interface. All data in the directory is
 * treated as read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker
 */
public class LdapUserProvider implements UserProvider {

    private LdapManager manager;
    private Map<String, String> searchFields;
    private int userCount = -1;
    private long expiresStamp = System.currentTimeMillis();

    public LdapUserProvider() {
        manager = LdapManager.getInstance();
        searchFields = new LinkedHashMap<String,String>();
        String fieldList = JiveGlobals.getXMLProperty("ldap.searchFields");
        // If the value isn't present, default to to username, name, and email.
        if (fieldList == null) {
            searchFields.put("Username", manager.getUsernameField());
            searchFields.put("Name", manager.getNameField());
            searchFields.put("Email", manager.getEmailField());
        }
        else {
            try {
                for (StringTokenizer i=new StringTokenizer(fieldList, ","); i.hasMoreTokens(); ) {
                    String[] field = i.nextToken().split("/");
                    searchFields.put(field[0], field[1]);
                }
            }
            catch (Exception e) {
                Log.error("Error parsing LDAP search fields: " + fieldList, e);
            }
        }
    }

    public User loadUser(String username) throws UserNotFoundException {
        // Un-escape username.
        username = JID.unescapeNode(username);
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Load record.
            String[] attributes = new String[]{
                manager.getUsernameField(), manager.getNameField(),
                manager.getEmailField()
            };
            ctx = manager.getContext();
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            String name = null;
            String email = null;
            Attribute nameField = attrs.get(manager.getNameField());
            if (nameField != null) {
                name = (String)nameField.get();
            }
            Attribute emailField = attrs.get(manager.getEmailField());
            if (emailField != null) {
                email = (String)emailField.get();
            }
            // Escape the username so that it can be used as a JID.
            username = JID.escapeNode(username);
            return new User(username, name, email, new Date(), new Date());
        }
        catch (Exception e) {
            throw new UserNotFoundException(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
    }

    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException
    {
        throw new UnsupportedOperationException();
    }

    public void deleteUser(String username) {
        throw new UnsupportedOperationException();
    }

    public int getUserCount() {
        // Cache user count for 5 minutes.
        if (userCount != -1 && System.currentTimeMillis() < expiresStamp) {
            return userCount;
        }
        int count = 0;
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
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
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        this.userCount = count;
        this.expiresStamp = System.currentTimeMillis() + JiveConstants.MINUTE *5;
        return count;
    }

    public Collection<User> getUsers() {
        List<String> usernames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, constraints);
            while (answer.hasMoreElements()) {
                // Get the next userID.
                String username = (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getUsernameField()).get();
                // Escape username and add to results.
                usernames.add(JID.escapeNode(username));
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        // If client-side sorting is enabled, do it.
        if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
            Collections.sort(usernames);
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            // Limit results to those we'll need to process unless client-side sorting
            // is turned on.
            if (!(Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting")))) {
                constraints.setCountLimit(startIndex+numResults);
            }
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, constraints);
            // If client-side sorting is enabled, read in all results, sort, then get a sublist.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                while (answer.hasMoreElements()) {
                    // Get the next userID.
                    String username = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getUsernameField()).get();
                    // Escape username and add to results.
                    usernames.add(JID.escapeNode(username));
                }
                Collections.sort(usernames);
                usernames = usernames.subList(startIndex, startIndex+numResults);
            }
            // Otherwise, only read in certain results.
            else {
                for (int i=0; i < startIndex; i++) {
                    if (answer.hasMoreElements()) {
                        answer.next();
                    }
                    else {
                        return Collections.emptyList();
                    }
                }
                // Now read in desired number of results (or stop if we run out of results).
                for (int i = 0; i < numResults; i++) {
                    if (answer.hasMoreElements()) {
                        // Get the next userID.
                        String username = (String)((SearchResult)answer.next()).getAttributes().get(
                                manager.getUsernameField()).get();
                        // Escape username and add to results.
                        usernames.add(JID.escapeNode(username));
                    }
                    else {
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    public void setPassword(String username, String password) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setName(String username, String name) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setEmail(String username, String email) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return Collections.unmodifiableSet(searchFields.keySet());
    }

    public Collection<User> findUsers(Set<String> fields, String query)
            throws UnsupportedOperationException
    {
        if (fields.isEmpty() || query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        if (!searchFields.keySet().containsAll(fields)) {
            throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
        }
        // Make the query be a wildcard search by default. So, if the user searches for
        // "John", make the search be "John*" instead.
        if (!query.endsWith("*")) {
            query = query + "*";
        }
        List<String> usernames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            StringBuilder filter = new StringBuilder();
            if (fields.size() > 1) {
                filter.append("(|");
            }
            for (String field:fields) {
                String attribute = searchFields.get(field);
                filter.append("(").append(attribute).append("=").append(query).append(")");
            }
            if (fields.size() > 1) {
                filter.append(")");
            }
            NamingEnumeration answer = ctx.search("", filter.toString(), constraints);
            while (answer.hasMoreElements()) {
                // Get the next userID.
                String username = (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getUsernameField()).get();
                // Escape username and add to results.
                usernames.add(JID.escapeNode(username));
            }
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(usernames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public Collection<User> findUsers(Set<String> fields, String query, int startIndex,
            int numResults) throws UnsupportedOperationException
    {
        if (fields.isEmpty()) {
            return Collections.emptyList();
        }
        if (!searchFields.keySet().containsAll(fields)) {
            throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
        }
        List<String> usernames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            StringBuilder filter = new StringBuilder();
            if (fields.size() > 1) {
                filter.append("(|");
            }
            for (String field:fields) {
                String attribute = searchFields.get(field);
                filter.append("(").append(attribute).append("=").append(query).append(")");
            }
            if (fields.size() > 1) {
                filter.append(")");
            }
            // TODO: used paged results is supported by LDAP server.
            NamingEnumeration answer = ctx.search("", filter.toString(), constraints);
            for (int i=0; i < startIndex; i++) {
                if (answer.hasMoreElements()) {
                    answer.next();
                }
                else {
                    return Collections.emptyList();
                }
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next userID.
                    String username = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getUsernameField()).get();
                    // Escape username and add to results.
                    usernames.add(JID.escapeNode(username));
                }
                else {
                    break;
                }
            }
            
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(usernames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public boolean isReadOnly() {
        return true;
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}