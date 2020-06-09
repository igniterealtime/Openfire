/*
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

package org.jivesoftware.admin;

import org.jivesoftware.util.Base64;
import org.jivesoftware.openfire.ldap.LdapManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * Class that assists during the testing of the user-ldap mapping.
 *
 * @author Gaston Dombiak
 */
public class LdapUserTester {

    private static final Logger Log = LoggerFactory.getLogger(LdapUserTester.class);

    /**
     * Constants to access user properties
     */
    public static final String NAME = "Name";
    public static final String EMAIL = "Email";
    public static final String FULL_NAME = "FullName";
    public static final String NICKNAME = "Nickname";
    public static final String BIRTHDAY = "Birthday";
    public static final String PHOTO = "Photo";
    public static final String HOME_STREET = "HomeStreet";
    public static final String HOME_CITY = "HomeCity";
    public static final String HOME_STATE = "HomeState";
    public static final String HOME_ZIP = "HomeZip";
    public static final String HOME_COUNTRY = "HomeCountry";
    public static final String HOME_PHONE = "HomePhone";
    public static final String HOME_MOBILE = "HomeMobile";
    public static final String HOME_FAX = "HomeFax";
    public static final String HOME_PAGER = "HomePager";
    public static final String BUSINESS_STREET = "BusinessStreet";
    public static final String BUSINESS_CITY = "BusinessCity";
    public static final String BUSINESS_STATE = "BusinessState";
    public static final String BUSINESS_ZIP = "BusinessZip";
    public static final String BUSINESS_COUNTRY = "BusinessCountry";
    public static final String BUSINESS_JOB_TITLE = "BusinessJobTitle";
    public static final String BUSINESS_DEPARTMENT = "BusinessDepartment";
    public static final String BUSINESS_PHONE = "BusinessPhone";
    public static final String BUSINESS_MOBILE = "BusinessMobile";
    public static final String BUSINESS_FAX = "BusinessFax";
    public static final String BUSINESS_PAGER = "BusinessPager";

    private LdapManager manager;
    private LdapUserProfile profile;

    public LdapUserTester(LdapManager manager, LdapUserProfile profile) {
        this.manager = manager;
        this.profile = profile;
    }

    /**
     * Returns a list of usernames with a sample of the users found in LDAP.
     *
     * @param maxSample the max size of the sample to return.
     * @return a list of usernames with a sample of the users found in LDAP.
     * @throws NamingException if something goes wrong....
     */
    public List<String> getSample(int maxSample) throws NamingException {
        List<String> usernames = new ArrayList<>();
        LdapContext ctx = null;

        try {
            ctx = manager.getContext();

            // Sort on username field.
            Control[] searchControl;
            try {
                searchControl = new Control[]{
                        new SortControl(new String[]{manager.getUsernameField()}, Control.NONCRITICAL)
                };
            } catch (IOException e) {
                Log.error(e.getMessage(), e);
                return Collections.emptyList();
            }
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the username.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            } else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[]{manager.getUsernameField()});
            // Limit results to those we'll need to process
            searchControls.setCountLimit(maxSample);
            String filter = MessageFormat.format(manager.getSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);

            while (answer.hasMoreElements()) {
                // Get the next userID.
                String username = (String) ((SearchResult) answer.next()).getAttributes().get(
                        manager.getUsernameField()).get();
                // Escape username and add to results.
                usernames.add(JID.escapeNode(username));
            }
            // Close the enumeration.
            answer.close();
        } finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to get a sample of data from LDAP.", ex);
            }
        }
        return usernames;
    }

    /**
     * Returns a list of attributes and their LDAP values found in LDAP for the specified username.
     *
     * @param username the username of the user to get his attributes from LDAP.
     * @return a list of attributes and their LDAP values found in LDAP for the specified username.
     */
    public Map<String, String> getAttributes(String username) {
        Map<String, String> userAttributes = new HashMap<>();
        // Un-escape username.
        username = JID.unescapeNode(username);
        DirContext ctx = null;
        try {
            Rdn[] userRDN = manager.findUserRDN(username);
            // Build list of attributes to load from LDAP
            Map<String, PropertyMapping> ldapMappings = getLdapAttributes();
            Set<String> fields = new HashSet<>();
            for (PropertyMapping mapping : ldapMappings.values()) {
                fields.addAll(mapping.getFields());
            }
            fields.add(manager.getUsernameField());
            // Load records
            ctx = manager.getContext(manager.getUsersBaseDN(username));
            Attributes attrs = ctx.getAttributes(LdapManager.escapeForJNDI(userRDN), fields.toArray(new String[]{}));
            // Build answer
            for (Map.Entry<String, PropertyMapping> entry : ldapMappings.entrySet()) {
                String attribute = entry.getKey();
                String value = getPropertyValue(entry.getValue(), attrs);
                userAttributes.put(attribute, value);
            }
        }
        catch (Exception e) {
            Log.error("An error occurred while trying to get attributes for user: {}", username, e);
            // TODO something else?
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to get attributes for user {}.", username, ex);
            }
        }
        return userAttributes;
    }

    public static String getPropertyValue( final PropertyMapping mapping, final Attributes attributes ) throws NamingException
    {
        String value = mapping.getDisplayFormat();
        for (String field : mapping.getFields()) {
            Attribute ldapField = attributes.get(field);
            if (ldapField != null) {
                String answer;
                Object ob = ldapField.get();
                if (ob instanceof String) {
                    answer = (String) ob;
                } else {
                    answer = Base64.encodeBytes((byte[]) ob);
                }
                if ( mapping.isFirstMatchOnly()) {
                    // find and use the first non-null value.
                    if ( answer == null || answer.isEmpty() ) {
                        continue;
                    }
                    value = value.replace("{VALUE}", answer);
                    break;
                } else {
                    // replace all fields with values.
                    value = value.replace("{" + field + "}", answer);
                }
            }
        }

        return value;
    }

    private Map<String, PropertyMapping> getLdapAttributes() {
        Map<String, PropertyMapping> map = new HashMap<>();

        if (profile.getName() != null && profile.getName().trim().length() > 0) {
            map.put(NAME, new PropertyMapping(profile.getName()));
        }
        if (profile.getEmail() != null && profile.getEmail().trim().length() > 0) {
            map.put(EMAIL, new PropertyMapping(profile.getEmail()));
        }
        if (profile.getFullName() != null && profile.getFullName().trim().length() > 0) {
            map.put(FULL_NAME, new PropertyMapping(profile.getFullName()));
        }
        if (profile.getNickname() != null && profile.getNickname().trim().length() > 0) {
            map.put(NICKNAME, new PropertyMapping(profile.getNickname()));
        }
        if (profile.getBirthday() != null && profile.getBirthday().trim().length() > 0) {
            map.put(BIRTHDAY, new PropertyMapping(profile.getBirthday()));
        }
        if (profile.getPhoto() != null && profile.getPhoto().trim().length() > 0) {
            map.put(PHOTO, new PropertyMapping(profile.getPhoto()));
        }
        if (profile.getHomeStreet() != null && profile.getHomeStreet().trim().length() > 0) {
            map.put(HOME_STREET, new PropertyMapping(profile.getHomeStreet()));
        }
        if (profile.getHomeCity() != null && profile.getHomeCity().trim().length() > 0) {
            map.put(HOME_CITY, new PropertyMapping(profile.getHomeCity()));
        }
        if (profile.getHomeState() != null && profile.getHomeState().trim().length() > 0) {
            map.put(HOME_STATE, new PropertyMapping(profile.getHomeState()));
        }
        if (profile.getHomeZip() != null && profile.getHomeZip().trim().length() > 0) {
            map.put(HOME_ZIP, new PropertyMapping(profile.getHomeZip()));
        }
        if (profile.getHomeCountry() != null && profile.getHomeCountry().trim().length() > 0) {
            map.put(HOME_COUNTRY, new PropertyMapping(profile.getHomeCountry()));
        }
        if (profile.getHomePhone() != null && profile.getHomePhone().trim().length() > 0) {
            map.put(HOME_PHONE, new PropertyMapping(profile.getHomePhone()));
        }
        if (profile.getHomeMobile() != null && profile.getHomeMobile().trim().length() > 0) {
            map.put(HOME_MOBILE, new PropertyMapping(profile.getHomeMobile()));
        }
        if (profile.getHomeFax() != null && profile.getHomeFax().trim().length() > 0) {
            map.put(HOME_FAX, new PropertyMapping(profile.getHomeFax()));
        }
        if (profile.getHomePager() != null && profile.getHomePager().trim().length() > 0) {
            map.put(HOME_PAGER, new PropertyMapping(profile.getHomePager()));
        }
        if (profile.getBusinessStreet() != null && profile.getBusinessStreet().trim().length() > 0) {
            map.put(BUSINESS_STREET, new PropertyMapping(profile.getBusinessStreet()));
        }
        if (profile.getBusinessCity() != null && profile.getBusinessCity().trim().length() > 0) {
            map.put(BUSINESS_CITY, new PropertyMapping(profile.getBusinessCity()));
        }
        if (profile.getBusinessState() != null && profile.getBusinessState().trim().length() > 0) {
            map.put(BUSINESS_STATE, new PropertyMapping(profile.getBusinessState()));
        }
        if (profile.getBusinessZip() != null && profile.getBusinessZip().trim().length() > 0) {
            map.put(BUSINESS_ZIP, new PropertyMapping(profile.getBusinessZip()));
        }
        if (profile.getBusinessCountry() != null && profile.getBusinessCountry().trim().length() > 0) {
            map.put(BUSINESS_COUNTRY, new PropertyMapping(profile.getBusinessCountry()));
        }
        if (profile.getBusinessJobTitle() != null && profile.getBusinessJobTitle().trim().length() > 0) {
            map.put(BUSINESS_JOB_TITLE, new PropertyMapping(profile.getBusinessJobTitle()));
        }
        if (profile.getBusinessDepartment() != null && profile.getBusinessDepartment().trim().length() > 0) {
            map.put(BUSINESS_DEPARTMENT, new PropertyMapping(profile.getBusinessDepartment()));
        }
        if (profile.getBusinessPhone() != null && profile.getBusinessPhone().trim().length() > 0) {
            map.put(BUSINESS_PHONE, new PropertyMapping(profile.getBusinessPhone()));
        }
        if (profile.getBusinessMobile() != null && profile.getBusinessMobile().trim().length() > 0) {
            map.put(BUSINESS_MOBILE, new PropertyMapping(profile.getBusinessMobile()));
        }
        if (profile.getBusinessFax() != null && profile.getBusinessFax().trim().length() > 0) {
            map.put(BUSINESS_FAX, new PropertyMapping(profile.getBusinessFax()));
        }
        if (profile.getBusinessPager() != null && profile.getBusinessPager().trim().length() > 0) {
            map.put(BUSINESS_PAGER, new PropertyMapping(profile.getBusinessPager()));
        }

        return map;
    }

    public static class PropertyMapping {
        /**
         * Format how user property is going to appear (e.g. {firstname}, {lastname}
         */
        private String displayFormat;
        /**
         * LDAP fields that compose the user property
         */
        private Collection<String> fields = new ArrayList<>();

        private final boolean firstMatchOnly;

        public  PropertyMapping(String displayFormat) {
            // Versions of Openfire prior to 4.5.0 saved attributes without { and } characters (making it hard to
            // reconstruct the original displayFormat. If these characters are not present in the value, wrap the entire
            // value in them to simulate the post-4.5.0 behavior.
            final String template;
            if ( displayFormat.contains( "{" ) ) {
                template = displayFormat;
            } else {
                template = "{" + displayFormat + "}";
            }

            // Process the displayformat / template to identify the fields that are part of it, and whether or not it's
            // a format that uses only the first matching (non-empty) value, or a combination of all available values
            final List<String> splitted = LdapManager.splitFilter(template);
            firstMatchOnly = splitted.size() > 1;
            if ( splitted.size() == 1 ) {
                this.displayFormat = splitted.get(0);
                StringTokenizer st = new StringTokenizer(template.trim(), ", /{}");
                while (st.hasMoreTokens()) {
                    fields.add(st.nextToken().replaceFirst("(\\{)([\\d\\D&&[^}]]+)(})", "$2"));
                }
            } else {
                this.displayFormat = "{VALUE}";
                for ( final String split : splitted ) {
                    fields.add( split.replaceFirst("(\\{)([\\d\\D&&[^}]]+)(})", "$2") );
                }
            }
        }

        public String getDisplayFormat() {
            return displayFormat;
        }

        public Collection<String> getFields() {
            return fields;
        }

        public boolean isFirstMatchOnly() {
            return firstMatchOnly;
        }
    }
}
