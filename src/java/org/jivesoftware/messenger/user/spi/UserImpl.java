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

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.auth.*;
import org.jivesoftware.messenger.user.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>Database implementation of the UserInfoProvider interface.</p>
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 * @author Iain Shigeoka
 * @see User
 */
public class UserImpl implements User, Cacheable {

    /**
     * Controls whether extended properties should be lazily loaded (not loaded
     * until requested). If the properties are infrequently used, this provides
     * a great speedup in initial object loading time. However, if your
     * application does use extended properties all the time, you may wish to
     * turn lazy loading off, as it's actually faster in total db lookup time
     * to load everything at once.
     */
    private static final boolean LAZY_PROP_LOADING = true;

    private static final Permissions USER_ADMIN_PERMS = new Permissions(Permissions.USER_ADMIN);
    UserPropertiesProvider propertiesProvider = UserProviderFactory.getUserPropertiesProvider();
    UserInfoProvider infoProvider = UserProviderFactory.getUserInfoProvider();
    AuthProvider authProvider = AuthProviderFactory.getAuthProvider();
    /**
     * User id of -1 is reserved for "anonymous user" and 0 is reserved for "all users".
     */
    private String username = null;
    private UserInfo userInfo;
    private Map properties;
    private Map vcardProps;

    /**
     * Create a new DbUser with all required fields.
     *
     * @param username the username for the user.
     */
    protected UserImpl(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setPassword(String password) throws UnauthorizedException {
        try {
            authProvider.updatePassword(username, password);
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
        }
    }

    public UserInfo getInfo() throws UserNotFoundException {
        if (userInfo == null) {
            userInfo = infoProvider.getInfo(username);
        }
        return userInfo;
    }

    public void saveInfo() throws UnauthorizedException {
        if (userInfo != null) {
            try {
                infoProvider.setInfo(username, userInfo);
            }
            catch (UserNotFoundException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    public String getProperty(String name) {
        return getProp(name, false);
    }

    private String getProp(String name, boolean isVcard) {
        if (LAZY_PROP_LOADING && properties == null) {
            loadPropertiesFromDb(isVcard);
        }

        if (isVcard) {
            return (String)vcardProps.get(name);
        }
        else {
            return (String)properties.get(name);
        }
    }

    private void loadPropertiesFromDb(boolean isVcard) {
        if (isVcard) {
            vcardProps = propertiesProvider.getVcardProperties(username);
        }
        else {
            properties = propertiesProvider.getUserProperties(username);
        }
    }

    public void setProperty(String name, String value) throws UnauthorizedException {
        setProp(name, value, false);
    }

    private void setProp(String name, String value, boolean isVcard) throws UnauthorizedException {
        if (LAZY_PROP_LOADING && properties == null) {
            loadPropertiesFromDb(isVcard);
        }
        // Make sure the property name and value aren't null.
        if (name == null || value == null || "".equals(name) || "".equals(value)) {
            throw new NullPointerException("Cannot set property with empty or null value.");
        }

        Map props;
        if (isVcard) {
            props = vcardProps;
        }
        else {
            props = properties;
        }

        // See if we need to update a property value or insert a new one.
        if (props.containsKey(name)) {
            // Only update the value in the database if the property value
            // has changed.
            if (!(value.equals(props.get(name)))) {
                props.put(name, value);
                updatePropertyInDb(name, value, isVcard);

                // Re-add user to cache.
                CacheManager.getCache("userCache").put(username, this);
            }
        }
        else {
            props.put(name, value);
            insertPropertyIntoDb(name, value, isVcard);

            // Re-add user to cache.
            CacheManager.getCache("userCache").put(username, this);
        }
    }

    private void insertPropertyIntoDb(String name, String value, boolean vcard) throws UnauthorizedException {
        if (vcard) {
            propertiesProvider.insertVcardProperty(username, name, value);
        }
        else {
            propertiesProvider.insertUserProperty(username, name, value);
        }
    }

    private void updatePropertyInDb(String name, String value, boolean vcard) throws UnauthorizedException {
        if (vcard) {
            propertiesProvider.updateVcardProperty(username, name, value);
        }
        else {
            propertiesProvider.updateUserProperty(username, name, value);
        }
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        deleteProp(name, false);
    }

    private void deleteProp(String name, boolean isVcard) throws UnauthorizedException {
        if (LAZY_PROP_LOADING && properties == null) {
            loadPropertiesFromDb(isVcard);
        }
        Map props;
        if (isVcard) {
            props = vcardProps;
        }
        else {
            props = properties;
        }

        // Only delete the property if it exists.
        if (props.containsKey(name)) {

            props.remove(name);
            deletePropertyFromDb(name, isVcard);

            // Re-add user to cache.
            CacheManager.getCache("userCache").put(username, this);
        }
    }

    private void deletePropertyFromDb(String name, boolean vcard) throws UnauthorizedException {
        if (vcard) {
            propertiesProvider.deleteVcardProperty(username, name);
        }
        else {
            propertiesProvider.deleteUserProperty(username, name);
        }
    }

    public Iterator getPropertyNames() {
        return getPropNames(false);
    }

    private Iterator getPropNames(boolean isVcard) {
        if (LAZY_PROP_LOADING && properties == null) {
            loadPropertiesFromDb(isVcard);
        }
        if (isVcard) {
            return Collections.unmodifiableSet(vcardProps.keySet()).iterator();
        }
        else {
            return Collections.unmodifiableSet(properties.keySet()).iterator();
        }
    }

    public CachedRoster getRoster() throws UnauthorizedException {
        CachedRoster roster = null;
        if (CacheManager.getCache("username2roster").get(username) != null) {
            // Check for a cached roster:
            roster = (CachedRoster)CacheManager.getCache("username2roster").get(username);
        }
        else {
            // Not in cache so load a new one:
            roster = new CachedRosterImpl(username);
            CacheManager.getCache("userame2roster").put(username, roster);
        }
        return roster;
    }

    public Permissions getPermissions(AuthToken auth) {
        if (auth.getUsername() == username) {
            return USER_ADMIN_PERMS;
        }
        else {
            return new Permissions(Permissions.NONE);
        }
    }

    public boolean isAuthorized(long permissionType) {
        return true;
    }

    public void setVCardProperty(String name, String value) throws UnauthorizedException {
        setProp(name, value, true);
    }

    public String getVCardProperty(String name) {
        return getProp(name, true);
    }

    public void deleteVCardProperty(String name) throws UnauthorizedException {
        deleteProp(name, true);
    }

    public Iterator getVCardPropertyNames() {
        return getPropNames(true);
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfLong();                // id
        size += CacheSizes.sizeOfString(username);      // username
        if (userInfo != null) {
            size += userInfo.getCachedSize();
        }
        size += CacheSizes.sizeOfMap(properties);       // properties
        size += CacheSizes.sizeOfMap(vcardProps);       // vcard properties
        return size;
    }

    /**
     * Returns a String representation of the User object using the username.
     *
     * @return a String representation of the User object.
     */
    public String toString() {
        return username;
    }

    public int hashCode() {
        return username.hashCode();
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof User) {
            return username.equals(((User)object).getUsername());
        }
        else {
            return false;
        }
    }
}
