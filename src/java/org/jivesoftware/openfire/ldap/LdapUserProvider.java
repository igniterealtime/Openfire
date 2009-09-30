/**
 * $RCSfile$
 * $Revision: 3055 $
 * $Date: 2005-11-10 21:57:51 -0300 (Thu, 10 Nov 2005) $
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

package org.jivesoftware.openfire.ldap;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import javax.naming.directory.*;
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
    private Map<String, String> searchFields;
    private int userCount = -1;
    private long expiresStamp = System.currentTimeMillis();

    public LdapUserProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("ldap.searchFields");

        manager = LdapManager.getInstance();
        searchFields = new LinkedHashMap<String,String>();
        String fieldList = JiveGlobals.getProperty("ldap.searchFields");
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
        this.userCount = manager.retrieveListCount(
                manager.getUsernameField(),
                MessageFormat.format(manager.getSearchFilter(), "*")
        );
        this.expiresStamp = System.currentTimeMillis() + JiveConstants.MINUTE *5;
        return this.userCount;
    }

    public Collection<String> getUsernames() {
        return manager.retrieveList(
                manager.getUsernameField(),
                MessageFormat.format(manager.getSearchFilter(), "*"),
                -1,
                -1,
                null
        );
    }
    
    public Collection<User> getUsers() {
        return getUsers(-1, -1);
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> userlist = manager.retrieveList(
                manager.getUsernameField(),
                MessageFormat.format(manager.getSearchFilter(), "*"),
                startIndex,
                numResults,
                manager.getUsernameSuffix()
        );
        return new UserCollection(userlist.toArray(new String[userlist.size()]));
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
        JiveGlobals.setProperty("ldap.searchFields", fieldList);
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
        StringBuilder filter = new StringBuilder();
        //Add the global search filter so only those users the directory administrator wants to include
        //are returned from the directory
        filter.append("(&(");
        filter.append(MessageFormat.format(manager.getSearchFilter(),"*"));
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
        List<String> userlist = manager.retrieveList(
                manager.getUsernameField(),
                filter.toString(),
                startIndex,
                numResults,
                manager.getUsernameSuffix()
        );
        return new UserCollection(userlist.toArray(new String[userlist.size()]));
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
