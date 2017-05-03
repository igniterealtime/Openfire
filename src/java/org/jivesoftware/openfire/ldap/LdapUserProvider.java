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

package org.jivesoftware.openfire.ldap;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserCollection;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * LDAP implementation of the UserProvider interface. All data in the directory is
 * treated as read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker
 */
public class LdapUserProvider implements UserProvider {

	private static final Logger Log = LoggerFactory.getLogger(LdapUserProvider.class);

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
        searchFields = new LinkedHashMap<>();
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

    @Override
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
            
            // As defined by RFC5803.
            Attribute authPassword = attrs.get("authPassword");
            User user = new User(username, name, email, creationDate, modificationDate);
            if (authPassword != null) {
            	// The authPassword attribute can be multivalued.
            	// Not sure if this is the right API to loop through them.
            	NamingEnumeration values = authPassword.getAll();
            	while (values.hasMore()) {
            		Attribute authPasswordValue = (Attribute) values.next();
	        		String[] parts = ((String) authPasswordValue.get()).split("$");
	        		String[] authInfo = parts[1].split(":");
	        		String[] authValue = parts[2].split(":");
	
	        		String scheme = parts[0].trim();
	
	            	// We only support SCRAM-SHA-1 at the moment.
	        		if ("SCRAM-SHA-1".equals(scheme)) {
	            		int iterations = Integer.valueOf(authInfo[0].trim());
	            		String salt = authInfo[1].trim();
	            		String storedKey = authValue[0].trim();
	            		String serverKey = authValue[1].trim();
	            		
	            		user.setSalt(salt);
	            		user.setStoredKey(storedKey);
	            		user.setServerKey(serverKey);
	            		user.setIterations(iterations);
	            		
	            		break;
	        		}
            	}
            }
            return user;
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

    @Override
    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteUser(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
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

    @Override
    public Collection<String> getUsernames() {
        return manager.retrieveList(
                manager.getUsernameField(),
                MessageFormat.format(manager.getSearchFilter(), "*"),
                -1,
                -1,
                null,
                true
        );
    }
    
    @Override
    public Collection<User> getUsers() {
        return getUsers(-1, -1);
    }

    @Override
    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> userlist = manager.retrieveList(
                manager.getUsernameField(),
                MessageFormat.format(manager.getSearchFilter(), "*"),
                startIndex,
                numResults,
                manager.getUsernameSuffix(),
                true
        );
        return new UserCollection(userlist.toArray(new String[userlist.size()]));
    }

    @Override
    public void setName(String username, String name) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEmail(String username, String email) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return Collections.unmodifiableSet(searchFields.keySet());
    }

    public void setSearchFields(String fieldList) {
        this.searchFields = new LinkedHashMap<>();
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

    @Override
    public Collection<User> findUsers(Set<String> fields, String query)
            throws UnsupportedOperationException
    {
        return findUsers(fields, query, -1, -1);
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query, int startIndex,
            int numResults) throws UnsupportedOperationException
    {
        if (fields.isEmpty() || query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        
        query = LdapManager.sanitizeSearchFilter(query, true);
        
        // Make the query be a wildcard search by default. So, if the user searches for
        // "John", make the search be "John*" instead.
        if (!query.endsWith("*")) {
            query = query + "*";
        }

        if (!searchFields.keySet().containsAll(fields)) {
            throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
        }
        StringBuilder filter = new StringBuilder();
        //Add the global search filter so only those users the directory administrator wants to include
        //are returned from the directory
        filter.append("(&(");
        filter.append(MessageFormat.format(manager.getSearchFilter(),"*"));
        filter.append(')');
        if (fields.size() > 1) {
            filter.append("(|");
        }
        for (String field:fields) {
            String attribute = searchFields.get(field);
            filter.append('(').append(attribute).append('=')
            	.append( query ).append(")");
        }
        if (fields.size() > 1) {
            filter.append(')');
        }
        filter.append(')');
        List<String> userlist = manager.retrieveList(
                manager.getUsernameField(),
                filter.toString(),
                startIndex,
                numResults,
                manager.getUsernameSuffix(),
                true
        );
        return new UserCollection(userlist.toArray(new String[userlist.size()]));
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isNameRequired() {
        return false;
    }

    @Override
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
            Log.error(e.getMessage(), e);
        }
        return date;
    }
}
