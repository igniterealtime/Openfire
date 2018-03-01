/*
 * Copyright 2016 Anno van Vliet
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
package org.jivesoftware.openfire.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterItem.AskType;
import org.jivesoftware.openfire.roster.RosterItem.RecvType;
import org.jivesoftware.openfire.roster.RosterItem.SubType;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.xmpp.packet.JID;


/**
 * A Dummy implementation for the UserProvider for testing purposes.
 *
 * @author Anno van Vliet
 *
 */
public class TestUserProvider implements UserProvider {

  public class TestRoster extends Roster {
    private final Collection<RosterItem> rosteritems;

    /**
     */
    public TestRoster() {
      rosteritems = new ArrayList<RosterItem>();
      JID jid = new JID("roster@jid.test");
      SubType subStatus = SubType.BOTH;
      AskType askStatus = AskType.NONE;
      RecvType recvStatus = RecvType.SUBSCRIBE;
      String nickname = "nick";
      List<String> groups = null;
      rosteritems.add(new RosterItem(1, jid, subStatus, askStatus, recvStatus, nickname, groups));
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.roster.Roster#getRosterItems()
     */
    @Override
    public Collection<RosterItem> getRosterItems() {
      logger.finest("getRosterItems");
      return rosteritems;
    }
  }

  public class TestUser extends User {

    private final Roster roster;

    /**
     * @param username
     * @param name
     * @param email
     * @param creationDate
     * @param modificationDate
     */
    public TestUser(String username, String name, String email, Date creationDate, Date modificationDate) {
      super(username, name, email, creationDate, modificationDate);
      roster = new TestRoster();
      
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.user.User#getRoster()
     */
    @Override
    public Roster getRoster() {
      logger.finest("getRoster");
      return roster;
    }
    
    
  }

  private static Logger logger = Logger.getLogger(TestUserProvider.class.getName());
  private final Map<String,User> userList;
  
  
  /**
   */
  public TestUserProvider() {
    userList = new TreeMap<String,User>();
   
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#loadUser(java.lang.String)
   */
  @Override
  public User loadUser(String username) throws UserNotFoundException {
    logger.finest("loadUser");
    return userList.get(username);
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#createUser(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
    logger.finest("createUser");
    Date creationDate = new Date();
    Date modificationDate = new Date();

    User u = new TestUser(username, name, email, creationDate, modificationDate);
    userList.put(username, u);
    return u;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#deleteUser(java.lang.String)
   */
  @Override
  public void deleteUser(String username) {
    logger.finest("deleteUser");
    userList.remove(username);
    
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#getUserCount()
   */
  @Override
  public int getUserCount() {
    logger.finest("getUserCount");
    return userList.size();
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#getUsers()
   */
  @Override
  public Collection<User> getUsers() {
    logger.finest("getUsers");
    return userList.values();
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#getUsernames()
   */
  @Override
  public Collection<String> getUsernames() {
    logger.finest("getUsernames");
    return userList.keySet();
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#getUsers(int, int)
   */
  @Override
  public Collection<User> getUsers(int startIndex, int numResults) {
    logger.finest("getUsers");
    return null;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#setName(java.lang.String, java.lang.String)
   */
  @Override
  public void setName(String username, String name) throws UserNotFoundException {
    logger.finest("setName");
    
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#setEmail(java.lang.String, java.lang.String)
   */
  @Override
  public void setEmail(String username, String email) throws UserNotFoundException {
    logger.finest("setEmail");
    
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#setCreationDate(java.lang.String, java.util.Date)
   */
  @Override
  public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
    logger.finest("setCreationDate");
    
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#setModificationDate(java.lang.String, java.util.Date)
   */
  @Override
  public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
    logger.finest("setModificationDate");
    
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#getSearchFields()
   */
  @Override
  public Set<String> getSearchFields() throws UnsupportedOperationException {
    logger.finest("getSearchFields");
    return null;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#findUsers(java.util.Set, java.lang.String)
   */
  @Override
  public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
    logger.finest("findUsers");
    return null;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#findUsers(java.util.Set, java.lang.String, int, int)
   */
  @Override
  public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
    logger.finest("findUsers");
    return null;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#isReadOnly()
   */
  @Override
  public boolean isReadOnly() {
    logger.finest("isReadOnly");
    return false;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#isNameRequired()
   */
  @Override
  public boolean isNameRequired() {
    logger.finest("isNameRequired");
    return false;
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.user.UserProvider#isEmailRequired()
   */
  @Override
  public boolean isEmailRequired() {
    logger.finest("isEmailRequired");
    return false;
  }
}
