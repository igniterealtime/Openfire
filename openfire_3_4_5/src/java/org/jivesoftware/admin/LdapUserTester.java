/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.admin;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.Base64;
import org.jivesoftware.openfire.ldap.LdapManager;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.SortControl;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * Class that assists during the testing of the user-ldap mapping.
 *
 * @author Gaston Dombiak
 */
public class LdapUserTester {

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
        List<String> usernames = new ArrayList<String>();
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
                Log.error(e);
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
            catch (Exception ignored) {
                // Ignore.
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
        Map<String, String> userAttributes = new HashMap<String, String>();
        // Un-escape username.
        username = JID.unescapeNode(username);
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Build list of attributes to load from LDAP
            Map<String, PropertyMapping> ldapMappings = getLdapAttributes();
            Set<String> fields = new HashSet<String>();
            for (PropertyMapping mapping : ldapMappings.values()) {
                fields.addAll(mapping.getFields());
            }
            fields.add(manager.getUsernameField());
            // Load records
            ctx = manager.getContext(manager.getUsersBaseDN(username));
            Attributes attrs = ctx.getAttributes(userDN, fields.toArray(new String[]{}));
            // Build answer
            for (Map.Entry<String, PropertyMapping> entry : ldapMappings.entrySet()) {
                String attribute = entry.getKey();
                PropertyMapping mapping = entry.getValue();
                String value = mapping.getDisplayFormat();
                for (String field : mapping.getFields()) {
                    Attribute ldapField = attrs.get(field);
                    if (ldapField != null) {
                        String answer;
                        Object ob = ldapField.get();
                        if (ob instanceof String) {
                            answer = (String) ob;
                        } else {
                            answer = Base64.encodeBytes((byte[]) ob);
                        }
                        value = value.replace("{" + field + "}", answer);
                    }
                }
                userAttributes.put(attribute, value);
            }
        }
        catch (Exception e) {
            Log.error(e);
            // TODO something else?
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
        return userAttributes;
    }

    private Map<String, PropertyMapping> getLdapAttributes() {
        Map<String, PropertyMapping> map = new HashMap<String, PropertyMapping>();

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

    private static class PropertyMapping {
        /**
         * Format how user property is going to appear (e.g. {firstname}, {lastname}
         */
        private String displayFormat;
        /**
         * LDAP fields that compose the user property
         */
        private Collection<String> fields = new ArrayList<String>();


        public PropertyMapping(String displayFormat) {
            this.displayFormat = displayFormat;

            StringTokenizer st = new StringTokenizer(displayFormat.trim(), ", //{}");
            while (st.hasMoreTokens()) {
                fields.add(st.nextToken().replaceFirst("(\\{)([\\d\\D&&[^}]]+)(})", "$2"));
            }
        }

        public String getDisplayFormat() {
            return displayFormat;
        }

        public Collection<String> getFields() {
            return fields;
        }
    }
}
