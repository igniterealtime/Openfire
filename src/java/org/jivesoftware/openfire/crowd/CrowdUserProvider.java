/*
 * Copyright (C) 2012 Issa Gorissen <issa-gorissen@usa.net>. All rights reserved.
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
package org.jivesoftware.openfire.crowd;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlassian Crowd implementation of the UserProvider. We do not permit
 * modifications of users from this provider - only read-only access.
 */
public class CrowdUserProvider implements UserProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CrowdUserProvider.class);
    
    private static final int CACHE_TTL = 3600; // default ttl in seconds - 1 hour
    private static final String JIVE_CROWD_USERS_CACHE_TTL_SECS = "crowd.users.cache.ttl.seconds";
    
    private static final String SEARCH_FIELD_USERNAME = "Username";
    private static final String SEARCH_FIELD_NAME = "Name";
    private static final String SEARCH_FIELD_EMAIL = "Email";
    private static final Set<String> SEARCH_FIELDS = new TreeSet<>(Arrays.asList(
            new String[]{SEARCH_FIELD_USERNAME, SEARCH_FIELD_NAME, SEARCH_FIELD_EMAIL}));
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService crowdUserSync = Executors.newSingleThreadScheduledExecutor();
    
    private Map<String, org.jivesoftware.openfire.crowd.jaxb.User> usersCache = new TreeMap<>();
    private List<org.jivesoftware.openfire.crowd.jaxb.User> users = new ArrayList<>();
    
    public CrowdUserProvider() {
        String propertyValue = JiveGlobals.getProperty(JIVE_CROWD_USERS_CACHE_TTL_SECS);
        int ttl = (propertyValue == null || propertyValue.trim().length() == 0) ? CACHE_TTL : Integer.parseInt(propertyValue);
        
        crowdUserSync.scheduleAtFixedRate(new UserSynch(this), 0, ttl, TimeUnit.SECONDS);
        
        JiveGlobals.setProperty(JIVE_CROWD_USERS_CACHE_TTL_SECS, String.valueOf(ttl));
        
        // workaround to load the sync of groups with crowd
        new CrowdGroupProvider();
    }

    @Override
    public User loadUser(String username) throws UserNotFoundException {
        lock.readLock().lock();
        try {
            return getCrowdUser(username).getOpenfireUser();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    
    public org.jivesoftware.openfire.crowd.jaxb.User getCrowdUser(String username) throws UserNotFoundException {
        lock.readLock().lock();
        try {
            if (usersCache.containsKey(username)) {
                return usersCache.get(username);
            } else {
                throw new UserNotFoundException("User : '" + String.valueOf(username) + "'");
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    
    @Override
    public int getUserCount() {
        lock.readLock().lock();
        try {
            return usersCache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<User> getUsers() {
        lock.readLock().lock();
        try {
            Collection<User> results = new ArrayList<>();
            for (org.jivesoftware.openfire.crowd.jaxb.User user : usersCache.values()) {
                results.add(user.getOpenfireUser());
            }
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<String> getUsernames() {
        lock.readLock().lock();
        try {
            return usersCache.keySet();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<User> getUsers(int startIndex, int numResults) {
        lock.readLock().lock();
        try {
            Collection<User> results = new ArrayList<>(numResults);
            
            for (int i = 0, j = startIndex; i < numResults && j < users.size(); ++i, ++j) {
                results.add(users.get(j).getOpenfireUser());
            }
            
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return SEARCH_FIELDS;
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        lock.readLock().lock();
        try {
            ArrayList<User> results = new ArrayList<>();
            
            if (query != null && query.trim().length() > 0) {
                
                if (query.endsWith("*")) {
                    query = query.substring(0, query.length() - 1);
                }
                if (query.startsWith("*")) {
                    query = query.substring(1);
                }
                query = query.toLowerCase();
                
                if (SEARCH_FIELDS.containsAll(fields)) {
                    if (fields.contains(SEARCH_FIELD_USERNAME)) {
                        for (org.jivesoftware.openfire.crowd.jaxb.User user : users) {
                            if (user.name.toLowerCase().contains(query)) {
                                results.add(user.getOpenfireUser());
                            }
                        }
                        
                    } else if (fields.contains(SEARCH_FIELD_NAME)) {
                        for (org.jivesoftware.openfire.crowd.jaxb.User user : users) {
                            if (user.displayName.toLowerCase().contains(query)) {
                                results.add(user.getOpenfireUser());
                            }
                        }
                        
                    } else {
                        for (org.jivesoftware.openfire.crowd.jaxb.User user : users) {
                            if (user.email.toLowerCase().contains(query)) {
                                results.add(user.getOpenfireUser());
                            }
                        }

                    }
                }
            }
            
            return results;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
        lock.readLock().lock();
        try {
            ArrayList<User> foundUsers = (ArrayList<User>) findUsers(fields, query);
            
            Collection<User> results = new ArrayList<>(foundUsers.size());
            
            for (int i = 0, j = startIndex; i < numResults && j < foundUsers.size(); ++i, ++j) {
                results.add(foundUsers.get(j));
            }
            
            return results;
            
        } finally {
            lock.readLock().unlock();
        }
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

    
    
    
    
    
    /*
     * Not implemented methods
     */
    
    @Override
    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
        throw new UnsupportedOperationException("Create new user not implemented by this version of user provider");
    }

    @Override
    public void deleteUser(String username) {
        throw new UnsupportedOperationException("Delete a user not implemented by this version of user provider");
    }

    @Override
    public void setName(String username, String name) throws UserNotFoundException {
        throw new UnsupportedOperationException("Setting user name not implemented by this version of user provider");
    }

    @Override
    public void setEmail(String username, String email) throws UserNotFoundException {
        throw new UnsupportedOperationException("Setting user email not implemented by this version of user provider");
    }

    @Override
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException("Setting user creation date unsupported by this version of user provider");
    }

    @Override
    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException("Setting user modification date unsupported by this version of user provider");
    }
    

    
    
    
    static class UserSynch implements Runnable {
        CrowdUserProvider userProvider;
        
        public UserSynch(CrowdUserProvider userProvider) {
            this.userProvider = userProvider;
        }
        
        @Override
        public void run() {
            LOG.info("running synch with crowd...");
            CrowdManager manager = null;
            try {
                manager = CrowdManager.getInstance();
            } catch (Exception e) {
                LOG.error("Failure to load the Crowd manager", e);
                return;
            }
            
            List<org.jivesoftware.openfire.crowd.jaxb.User> allUsers = null;
            try {
                allUsers = manager.getAllUsers();
            } catch (RemoteException re) {
                LOG.error("Failure to fetch all crowd users", re);
                return;
            }

            if (allUsers != null && allUsers.size() > 0) {
                
                Map<String, org.jivesoftware.openfire.crowd.jaxb.User> usersMap = new TreeMap<>();
                for (org.jivesoftware.openfire.crowd.jaxb.User user : allUsers) {
                    usersMap.put(user.name, user);
                }
                
                userProvider.lock.writeLock().lock();
                try {
                    userProvider.users = allUsers;
                    userProvider.usersCache = usersMap;
                } finally {
                    userProvider.lock.writeLock().unlock();
                }
            }
            
            LOG.info("crowd synch done, returned " + allUsers.size() + " users");
            
        }
        
    }

}
