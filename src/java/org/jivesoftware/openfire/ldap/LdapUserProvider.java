/**
 * $RCSfile$
 * $Revision: 3055 $
 * $Date: 2005-11-10 21:57:51 -0300 (Thu, 10 Nov 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.ldap;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * LDAP implementation of the UserProvider interface. All data in the directory is
 * treated as read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker
 */
public class LdapUserProvider implements UserProvider {

    // LDAP date format parser.
    private static SimpleDateFormat ldapDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private LdapManager manager;
    private String baseDN;
    private String alternateBaseDN;
    private Map<String, String> searchFields;
    private int userCount = -1;
    private long expiresStamp = System.currentTimeMillis();

    public LdapUserProvider() {
        manager = LdapManager.getInstance();
        baseDN = manager.getBaseDN();
        alternateBaseDN = manager.getAlternateBaseDN();
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
        if(username.contains("@")) {
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0,username.lastIndexOf("@"));
        }
        // Un-escape username.
        username = JID.unescapeNode(username);
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Load record.
            String[] attributes = new String[]{
                manager.getUsernameField(), manager.getNameField(),
                manager.getEmailField(), "createTimestamp", "modifyTimestamp"
            };
            ctx = manager.getContext(manager.getUsersBaseDN(username));
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            String name = null;
            Attribute nameField = attrs.get(manager.getNameField());
            if (nameField != null) {
                name = (String)nameField.get();
            }
            String email = null;
            Attribute emailField = attrs.get(manager.getEmailField());
            if (emailField != null) {
                email = (String)emailField.get();
            }
            Date creationDate = new Date();
            Attribute creationDateField = attrs.get("createTimestamp");
            if (creationDateField != null && "".equals(((String) creationDateField.get()).trim())) {
                creationDate = parseLDAPDate((String) creationDateField.get());
            }
            Date modificationDate = new Date();
            Attribute modificationDateField = attrs.get("modifyTimestamp");
            if (modificationDateField != null && "".equals(((String) modificationDateField.get()).trim())) {
                modificationDate = parseLDAPDate((String)modificationDateField.get());
            }
            // Escape the username so that it can be used as a JID.
            username = JID.escapeNode(username);
            return new User(username, name, email, creationDate, modificationDate);
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
        int pageSize = JiveGlobals.getXMLProperty("ldap.pagedResultsSize", -1);
        LdapContext ctx = null;
        LdapContext ctx2 = null;
        try {
            ctx = manager.getContext(baseDN);

            // Set up request controls, if appropriate.
            List<Control> tmpRequestControls = new ArrayList<Control>();
            if (pageSize > -1) {
                // Server side paging.
                tmpRequestControls.add(new PagedResultsControl(pageSize, Control.NONCRITICAL));
            }
            Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
            ctx.setRequestControls(requestControls);

            // Search for the dn based on the username.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            byte[] cookie = null;

            // Run through all pages of results (one page is also possible  ;)  )
            do {
                // Examine results on this page
                while (answer.hasMoreElements()) {
                    count++;
                    answer.nextElement();
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc =
                                    (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();

                            // Re-activate paged results
                            ctx.setRequestControls(new Control[]{
                                new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                            break;
                        }
                    }
                }
            } while (cookie != null);

            // Add count of users found in alternate DN
            if (alternateBaseDN != null) {
                ctx2 = manager.getContext(alternateBaseDN);
                answer = ctx2.search("", filter, searchControls);
                cookie = null;
                // Run through all pages of results (one page is also possible  ;)  )
                do {
                    // Examine results on this page
                    while (answer.hasMoreElements()) {
                        count++;
                        answer.nextElement();
                    }
                    // Examine the paged results control response
                    Control[] controls = ctx.getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            if (control instanceof PagedResultsResponseControl) {
                                PagedResultsResponseControl prrc =
                                        (PagedResultsResponseControl) control;
                                cookie = prrc.getCookie();

                                // Re-activate paged results
                                ctx.setRequestControls(new Control[]{
                                    new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                                break;
                            }
                        }
                    }
                } while (cookie != null);
            }
            // Close the enumeration.
            answer.close();
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
            try {
                if (ctx2 != null) {
                    ctx2.close();
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

    public Collection<String> getUsernames() {
        Set<String> usernames = new HashSet<String>();
        LdapContext ctx = null;
        LdapContext ctx2 = null;
        int pageSize = JiveGlobals.getXMLProperty("ldap.pagedResultsSize", -1);
        Boolean clientSideSort = JiveGlobals.getXMLProperty("ldap.clientSideSorting", false);
        try {
            ctx = manager.getContext(baseDN);

            // Set up request controls, if appropriate.
            List<Control> tmpRequestControls = new ArrayList<Control>();
            if (!clientSideSort) {
                // Server side sort on username field.
                tmpRequestControls.add(new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL));
            }
            if (pageSize > -1) {
                // Server side paging.
                tmpRequestControls.add(new PagedResultsControl(pageSize, Control.NONCRITICAL));
            }
            Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
            ctx.setRequestControls(requestControls);

            // Search for the dn based on the username.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            byte[] cookie = null;

            // Run through all pages of results (one page is also possible  ;)  )
            do {
                // Examine all of the results on this page
                while (answer.hasMoreElements()) {
                    // Get the next userID.
                    String username = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getUsernameField()).get();
                    // Escape username and add to results.
                    usernames.add(JID.escapeNode(username));
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc =
                                    (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();

                            // Re-activate paged results
                            ctx.setRequestControls(new Control[]{
                                new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                            break;
                        }
                    }
                }
            } while (cookie != null);
            // Add usernames found in alternate DN
            if (alternateBaseDN != null) {
                ctx2 = manager.getContext(alternateBaseDN);
                ctx2.setRequestControls(requestControls);
                answer = ctx2.search("", filter, searchControls);
                cookie = null;

                // Run through all pages of results (one page is also possible  ;)  )
                do {
                    // Examine all of the results on this page
                    while (answer.hasMoreElements()) {
                        // Get the next userID.
                        String username = (String) ((SearchResult) answer.next()).getAttributes().get(
                                manager.getUsernameField()).get();
                        // Escape username and add to results.
                        usernames.add(JID.escapeNode(username));
                    }
                    // Examine the paged results control response
                    Control[] controls = ctx.getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            if (control instanceof PagedResultsResponseControl) {
                                PagedResultsResponseControl prrc =
                                        (PagedResultsResponseControl) control;
                                cookie = prrc.getCookie();

                                // Re-activate paged results
                                ctx.setRequestControls(new Control[]{
                                    new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                                break;
                            }
                        }
                    }
                } while (cookie != null);
            }

            // Close the enumeration.
            answer.close();
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
            try {
                if (ctx2 != null) {
                    ctx2.setRequestControls(null);
                    ctx2.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
	    // If client-side sorting is enabled, do it.
        if (clientSideSort) {
            Collections.sort(new ArrayList<String>(usernames));
        }
        return usernames;
    }
    
    public Collection<User> getUsers() {
        return getUsers(-1, -1);
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<String>();
        int pageSize = JiveGlobals.getXMLProperty("ldap.pagedResultsSize", -1);
        Boolean clientSideSort = JiveGlobals.getXMLProperty("ldap.clientSideSorting", false);
        LdapContext ctx = null;
        LdapContext ctx2 = null;
        try {
            ctx = manager.getContext(baseDN);

            // Set up request controls, if appropriate.
            List<Control> tmpRequestControls = new ArrayList<Control>();
            if (!clientSideSort) {
                // Server side sort on username field.
                tmpRequestControls.add(new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL));
            }
            if (pageSize > -1) {
                // Server side paging.
                tmpRequestControls.add(new PagedResultsControl(pageSize, Control.NONCRITICAL));
            }
            Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
            ctx.setRequestControls(requestControls);

            // Search for the dn based on the username.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getUsernameField() });
            // Limit results to those we'll need to process unless client-side sorting
            // is turned on.
            if (!clientSideSort) {
                searchControls.setCountLimit(startIndex+numResults);
            }
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            // If client-side sorting is enabled, read in all results, sort, then get a sublist.
            NamingEnumeration answer2 = null;
            if (alternateBaseDN != null) {
                ctx2 = manager.getContext(alternateBaseDN);
                ctx2.setRequestControls(requestControls);
                answer2 = ctx2.search("", filter, searchControls);
            }
            // If server side sort, we'll skip the initial ones we don't want, and stop when we've hit
            // the amount we do want.
            int skip = -1;
            int lastRes = -1;
            if (!clientSideSort) {
                skip = startIndex;
                lastRes = startIndex + numResults;

            }
            byte[] cookie = null;
            int count = 0;
            // Run through all pages of results (one page is also possible  ;)  )
            do {
                // Examine all results on this page
                while (answer.hasMoreElements()) {
                    count++;
                    if (skip > -1 && count < skip) {
                        continue;
                    }
                    if (lastRes > -1 && count > lastRes) {
                        break;
                    }
                    // Get the next userID.
                    String username = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getUsernameField()).get();
                    // Remove usernameSuffix if set
                    String suffix = manager.getUsernameSuffix();
                    if(suffix.length() > 0 && username.endsWith(suffix)) {
                        username = username.substring(0,username.length()-suffix.length());
                    }
                    // Escape username and add to results.
                    usernames.add(JID.escapeNode(username));
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc =
                                    (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();

                            // Re-activate paged results
                            ctx.setRequestControls(new Control[]{
                                new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                            break;
                        }
                    }
                }
            } while (cookie != null);
            if (count <= lastRes && alternateBaseDN != null) {
                cookie = null;
                // Run through all pages of results (one page is also possible  ;)  )
                do {
                    // Examine all results on this page
                    while (answer2.hasMoreElements()) {
                        count++;
                        if (skip > -1 && count < skip) {
                            continue;
                        }
                        if (lastRes > -1 && count > lastRes) {
                            break;
                        }
                        // Get the next userID.
                        String username = (String) ((SearchResult) answer2.next()).getAttributes().get(
                                manager.getUsernameField()).get();
                        // Remove usernameSuffix if set
                        String suffix = manager.getUsernameSuffix();
                        if(suffix.length() > 0 && username.endsWith(suffix)) {
                            username = username.substring(0,username.length()-suffix.length());
                        }
                        // Escape username and add to results.
                        usernames.add(JID.escapeNode(username));
                    }
                    // Examine the paged results control response
                    Control[] controls = ctx.getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            if (control instanceof PagedResultsResponseControl) {
                                PagedResultsResponseControl prrc =
                                        (PagedResultsResponseControl) control;
                                cookie = prrc.getCookie();

                                // Re-activate paged results
                                ctx.setRequestControls(new Control[]{
                                    new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                                break;
                            }
                        }
                    }
                } while (cookie != null);
            }
            if (clientSideSort) {
                // If we're doing client side sorting, we pulled everything in and now we sort and trim.
                Collections.sort(new ArrayList<String>(usernames));
                int endIndex = Math.min(startIndex + numResults, usernames.size()-1);
                usernames = usernames.subList(startIndex, endIndex);
            }
            // Close the enumeration.
            answer.close();
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
            try {
                if (ctx2 != null) {
                    ctx2.setRequestControls(null);
                    ctx2.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
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

    public void setSearchFields(String fieldList) {
        this.searchFields = new LinkedHashMap<String,String>();
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
        JiveGlobals.setXMLProperty("ldap.searchFields", fieldList);
    }

    public Collection<User> findUsers(Set<String> fields, String query)
            throws UnsupportedOperationException
    {
        return findUsers(fields, query, -1, -1);
    }

    public Collection<User> findUsers(Set<String> fields, String query, int startIndex,
            int numResults) throws UnsupportedOperationException
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
        int pageSize = JiveGlobals.getXMLProperty("ldap.pagedResultsSize", -1);
        Boolean clientSideSort = JiveGlobals.getXMLProperty("ldap.clientSideSorting", false);
        List<String> usernames = new ArrayList<String>();
        LdapContext ctx = null;
        LdapContext ctx2 = null;
        try {
            ctx = manager.getContext(baseDN);

            // Set up request controls, if appropriate.
            List<Control> tmpRequestControls = new ArrayList<Control>();
            if (!clientSideSort) {
                // Server side sort on username field.
                tmpRequestControls.add(new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL));
            }
            if (pageSize > -1) {
                // Server side paging.
                tmpRequestControls.add(new PagedResultsControl(pageSize, Control.NONCRITICAL));
            }
            Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
            ctx.setRequestControls(requestControls);

            // Search for the dn based on the username.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getUsernameField() });
            String searchFilter = MessageFormat.format(manager.getSearchFilter(),"*");
            StringBuilder filter = new StringBuilder();
            //Add the global search filter so only those users the directory administrator wants to include
            //are returned from the directory
            filter.append("(&(");
            filter.append(searchFilter);
            filter.append(")");
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
            filter.append(")");
            NamingEnumeration answer = ctx.search("", filter.toString(), searchControls);
            // If server side sort, we'll skip the initial ones we don't want, and stop when we've hit
            // the amount we do want.
            int skip = -1;
            int lastRes = -1;
            if (!clientSideSort) {
                skip = startIndex;
                lastRes = startIndex + numResults;

            }
            byte[] cookie = null;
            int count = 0;
            // Run through all pages of results (one page is also possible  ;)  )
            do {
                // Examine all results on this page
                while (answer.hasMoreElements()) {
                    count++;
                    if (skip > -1 && count < skip) {
                        continue;
                    }
                    if (lastRes > -1 && count > lastRes) {
                        break;
                    }

                    // Get the next userID.
                    String username = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getUsernameField()).get();
                    // Escape username and add to results.
                    usernames.add(JID.escapeNode(username));
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc =
                                    (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();

                            // Re-activate paged results
                            ctx.setRequestControls(new Control[]{
                                new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                            break;
                        }
                    }
                }
            } while (cookie != null);
            if (count <= lastRes && alternateBaseDN != null) {
                ctx2 = manager.getContext(alternateBaseDN);
                ctx2.setRequestControls(requestControls);
                answer = ctx2.search("", filter.toString(), searchControls);
                cookie = null;
                // Run through all pages of results (one page is also possible  ;)  )
                do {
                    // Examine all results on this page
                    while (answer.hasMoreElements()) {
                        count++;
                        if (skip > -1 && count < skip) {
                            continue;
                        }
                        if (lastRes > -1 && count > lastRes) {
                            break;
                        }

                        // Get the next userID.
                        String username = (String)((SearchResult)answer.next()).getAttributes().get(
                                manager.getUsernameField()).get();
                        // Remove usernameSuffix if set
                        String suffix = manager.getUsernameSuffix();
                        if(suffix.length() > 0 && username.endsWith(suffix)) {
                            username = username.substring(0,username.length()-suffix.length());
                        }
                        // Escape username and add to results.
                        usernames.add(JID.escapeNode(username));
                    }
                    // Examine the paged results control response
                    Control[] controls = ctx.getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            if (control instanceof PagedResultsResponseControl) {
                                PagedResultsResponseControl prrc =
                                        (PagedResultsResponseControl) control;
                                cookie = prrc.getCookie();

                                // Re-activate paged results
                                ctx.setRequestControls(new Control[]{
                                    new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
                                break;
                            }
                        }
                    }
                } while (cookie != null);
            }
            // Close the enumeration.
            answer.close();
            // If client-side sorting is enabled, sort.
            if (clientSideSort) {
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
            try {
                if (ctx2 != null) {
                    ctx2.setRequestControls(null);
                    ctx2.close();
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

    public boolean isNameRequired() {
        return false;
    }

    public boolean isEmailRequired() {
        return false;
    }

    /**
     * Parses dates/time stamps stored in LDAP. Some possible values:
     *
     * <ul>
     *      <li>20020228150820</li>
     *      <li>20030228150820Z</li>
     *      <li>20050228150820.12</li>
     *      <li>20060711011740.0Z</li>
     * </ul>
     *
     * @param dateText the date string.
     * @return the Date.
     */
    private static Date parseLDAPDate(String dateText) {
        // If the date ends with a "Z", that means that it's in the UTC time zone. Otherwise,
        // Use the default time zone.
        boolean useUTC = false;
        if (dateText.endsWith("Z")) {
            useUTC = true;
        }
        Date date = new Date();
        try {
            if (useUTC) {
                ldapDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            else {
                ldapDateFormat.setTimeZone(TimeZone.getDefault());
            }
            date = ldapDateFormat.parse(dateText);
        }
        catch (Exception e) {
            Log.error(e);
        }
        return date;
    }
}
