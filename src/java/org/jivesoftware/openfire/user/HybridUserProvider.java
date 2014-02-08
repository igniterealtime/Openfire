/*
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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
 *
 */

package org.jivesoftware.openfire.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegate UserProvider operations among up to three configurable provider implementation classes.
 *
 * @author Marc Seeger
 * @author Chris Neasbitt
 * @author Tom Evans
 */

public class HybridUserProvider implements UserProvider {

	private static final Logger Log = LoggerFactory.getLogger(HybridUserProvider.class);

    private List<UserProvider> userproviders = null;

    public HybridUserProvider() {

		// Migrate user provider properties
		JiveGlobals.migrateProperty("hybridUserProvider.primaryProvider.className");
		JiveGlobals.migrateProperty("hybridUserProvider.secondaryProvider.className");
		JiveGlobals.migrateProperty("hybridUserProvider.tertiaryProvider.className");

        userproviders = new ArrayList<UserProvider>();

		// Load primary, secondary, and tertiary user providers.
        String primaryClass = JiveGlobals.getProperty("hybridUserProvider.primaryProvider.className");
        if (primaryClass == null) {
            Log.error("A primary UserProvider must be specified via openfire.xml or the system properties");
            return;
        }
        try {
            Class c = ClassUtils.forName(primaryClass);
            UserProvider primaryProvider = (UserProvider) c.newInstance();
            userproviders.add(primaryProvider);
            Log.debug("Primary user provider: " + primaryClass);
        } catch (Exception e) {
            Log.error("Unable to load primary user provider: " + primaryClass +
                    ". Users in this provider will be disabled.", e);
            return;
        }
        String secondaryClass = JiveGlobals.getProperty("hybridUserProvider.secondaryProvider.className");
        if (secondaryClass != null) {
            try {
                Class c = ClassUtils.forName(secondaryClass);
                UserProvider secondaryProvider = (UserProvider) c.newInstance();
                userproviders.add(secondaryProvider);
                Log.debug("Secondary user provider: " + secondaryClass);
            } catch (Exception e) {
                Log.error("Unable to load secondary user provider: " + secondaryClass, e);
            }
        }
        String tertiaryClass = JiveGlobals.getProperty("hybridUserProvider.tertiaryProvider.className");
        if (tertiaryClass != null) {
            try {
                Class c = ClassUtils.forName(tertiaryClass);
                UserProvider tertiaryProvider = (UserProvider) c.newInstance();
                userproviders.add(tertiaryProvider);
                Log.debug("Tertiary user provider: " + tertiaryClass);
            } catch (Exception e) {
                Log.error("Unable to load tertiary user provider: " + tertiaryClass, e);
            }
        }
    }


    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {

        User returnvalue = null;

        // create the user (first writable provider wins)
        for (UserProvider provider : userproviders) {
        	if (provider.isReadOnly()) {
        		continue;
        	}
        	returnvalue = provider.createUser(username, password, name, email);
        	if (returnvalue != null) {
        		break;
        	}
        }

        if (returnvalue == null) {
        	throw new UnsupportedOperationException();
        }
        return returnvalue;
    }


    public void deleteUser(String username) {

    	boolean isDeleted = false;

    	for (UserProvider provider : userproviders) {
    		if (provider.isReadOnly()) {
    			continue;
    		}
    		provider.deleteUser(username);
    		isDeleted = true;
    	}

    	// all providers are read-only
    	if (!isDeleted) {
    		throw new UnsupportedOperationException();
    	}
    }


    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {

    	List<User> userList = new ArrayList<User>();
    	boolean isUnsupported = false;

    	for (UserProvider provider : userproviders) {

    		// validate search fields for each provider
    		Set<String> validFields = provider.getSearchFields();
    		for (String field : fields) {
    			if (!validFields.contains(field)) {
    				continue;
    			}
    		}

    		try {
    			userList.addAll(provider.findUsers(fields, query));
    		} catch (UnsupportedOperationException uoe) {
    			Log.warn("UserProvider.findUsers is not supported by this UserProvider: " + provider.getClass().getName());
    			isUnsupported = true;
    		}
    	}

    	if (isUnsupported && userList.size() == 0) {
    		throw new UnsupportedOperationException();
    	}
        return userList;
    }


    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {

    	List<User> userList = new ArrayList<User>();
    	boolean isUnsupported = false;
    	int totalMatchedUserCount = 0;

    	for (UserProvider provider : userproviders) {

    		// validate search fields for each provider
    		Set<String> validFields = provider.getSearchFields();
    		for (String field : fields) {
    			if (!validFields.contains(field)) {
    				continue;
    			}
    		}

    		try {
        		Collection<User> providerResults = provider.findUsers(fields, query);
        		totalMatchedUserCount += providerResults.size();
        		if (startIndex >= totalMatchedUserCount) {
        			continue;
        		}
        		int providerStartIndex = Math.max(0, startIndex - totalMatchedUserCount);
        		int providerResultMax = numResults - userList.size();
        		List<User> providerList = providerResults instanceof List<?> ? 
        				(List<User>) providerResults : new ArrayList<User>(providerResults);
        		userList.addAll(providerList.subList(providerStartIndex, providerResultMax));
    			if (userList.size() >= numResults) {
    				break;
    			}
    		} catch (UnsupportedOperationException uoe) {
    			Log.warn("UserProvider.findUsers is not supported by this UserProvider: " + provider.getClass().getName());
    			isUnsupported = true;
    		}
    	}

    	if (isUnsupported && userList.size() == 0) {
    		throw new UnsupportedOperationException();
    	}
        return userList;
    }


    public Set<String> getSearchFields() throws UnsupportedOperationException {

    	Set<String> returnvalue = new HashSet<String>();

        for (UserProvider provider : userproviders) {
        	returnvalue.addAll(provider.getSearchFields());
        }

        // no search fields were returned
        if (returnvalue.size() == 0) {
            throw new UnsupportedOperationException();
        }
        return returnvalue;
    }


    public int getUserCount() {
        int count = 0;
        for (UserProvider provider : userproviders) {
            count += provider.getUserCount();
        }
        return count;
    }

    public Collection<String> getUsernames() {

        List<String> returnvalue = new ArrayList<String>();

        for (UserProvider provider : userproviders){
        	returnvalue.addAll(provider.getUsernames());
        }
        return returnvalue;
    }


    public Collection<User> getUsers() {
        List<User> returnvalue = new ArrayList<User>();

        for (UserProvider provider : userproviders){
        	returnvalue.addAll(provider.getUsers());
        }

        return returnvalue;
    }

    public Collection<User> getUsers(int startIndex, int numResults) {

    	List<User> userList = new ArrayList<User>();
    	int totalUserCount = 0;

    	for (UserProvider provider : userproviders) {
    		int providerStartIndex = Math.max((startIndex - totalUserCount), 0);
    		totalUserCount += provider.getUserCount();
    		if (startIndex >= totalUserCount) {
    			continue;
    		}
    		int providerResultMax = numResults - userList.size();
    		userList.addAll(provider.getUsers(providerStartIndex, providerResultMax));
			if (userList.size() >= numResults) {
				break;
			}
    	}
        return userList;
    }

    public boolean isReadOnly() {
        return false;
    }

    public boolean isNameRequired() {
        return false;
    }

    public boolean isEmailRequired() {
        return false;
    }

    public User loadUser(String username) throws UserNotFoundException {

    	for (UserProvider provider : userproviders) {
    		try {
    			return provider.loadUser(username);
    		}
    		catch (UserNotFoundException unfe) {
    			if (Log.isDebugEnabled()) {
        			Log.debug("User " + username + " not found by UserProvider " + provider.getClass().getName());
    			}
    		}
    	}
        //if we get this far, no provider was able to load the user
        throw new UserNotFoundException();
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {

    	boolean isUnsupported = false;

    	for (UserProvider provider : userproviders) {
    		try {
    			provider.setCreationDate(username, creationDate);
    			return;
    		}
    		catch (UnsupportedOperationException uoe) {
    			Log.warn("UserProvider.setCreationDate is not supported by this UserProvider: " + provider.getClass().getName());
    			isUnsupported = true;
    		}
    		catch (UserNotFoundException unfe) {
    			if (Log.isDebugEnabled()) {
        			Log.debug("User " + username + " not found by UserProvider " + provider.getClass().getName());
    			}
    		}
    	}
    	if (isUnsupported) {
    		throw new UnsupportedOperationException();
    	}
    	else {
            throw new UserNotFoundException();
    	}
    }

    public void setEmail(String username, String email) throws UserNotFoundException {

    	boolean isUnsupported = false;

    	for (UserProvider provider : userproviders) {
    		try {
    			provider.setEmail(username, email);
    			return;
    		}
    		catch (UnsupportedOperationException uoe) {
    			Log.warn("UserProvider.setEmail is not supported by this UserProvider: " + provider.getClass().getName());
    			isUnsupported = true;
    		}
    		catch (UserNotFoundException unfe) {
    			if (Log.isDebugEnabled()) {
        			Log.debug("User " + username + " not found by UserProvider " + provider.getClass().getName());
    			}
    		}
    	}
    	if (isUnsupported) {
    		throw new UnsupportedOperationException();
    	}
    	else {
            throw new UserNotFoundException();
    	}
    }


    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {

    	boolean isUnsupported = false;

    	for (UserProvider provider : userproviders) {
    		try {
    			provider.setModificationDate(username, modificationDate);
    			return;
    		}
    		catch (UnsupportedOperationException uoe) {
    			Log.warn("UserProvider.setModificationDate is not supported by this UserProvider: " + provider.getClass().getName());
    			isUnsupported = true;
    		}
    		catch (UserNotFoundException unfe) {
    			if (Log.isDebugEnabled()) {
        			Log.debug("User " + username + " not found by UserProvider " + provider.getClass().getName());
    			}
    		}
    	}
    	if (isUnsupported) {
    		throw new UnsupportedOperationException();
    	}
    	else {
            throw new UserNotFoundException();
    	}
    }

    public void setName(String username, String name) throws UserNotFoundException {

    	boolean isUnsupported = false;

    	for (UserProvider provider : userproviders) {
    		try {
    			provider.setName(username, name);
    			return;
    		}
    		catch (UnsupportedOperationException uoe) {
    			Log.warn("UserProvider.setName is not supported by this UserProvider: " + provider.getClass().getName());
    			isUnsupported = true;
    		}
    		catch (UserNotFoundException unfe) {
    			if (Log.isDebugEnabled()) {
        			Log.debug("User " + username + " not found by UserProvider " + provider.getClass().getName());
    			}
    		}
    	}
    	if (isUnsupported) {
    		throw new UnsupportedOperationException();
    	}
    	else {
            throw new UserNotFoundException();
    	}
    }
}

