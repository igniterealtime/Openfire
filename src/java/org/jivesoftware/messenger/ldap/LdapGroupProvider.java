/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.ldap;

import org.jivesoftware.util.*;
import org.jivesoftware.messenger.user.*;
import org.jivesoftware.messenger.group.*;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Vector;
import java.text.MessageFormat;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;

/**
 * LDAP implementation of the GroupProvider interface.  All data in the directory is
 * treated as read-only so any set operations will result in an exception.
 *
 * @author Greg Ferguson and Cameron Moore
 */
public class LdapGroupProvider implements GroupProvider
{

    private LdapManager manager;
    private UserManager userManager;
    private int groupCount;
    private long expiresStamp;
    private String[] standardAttributes;

    /**
     * Constructor of the LdapGroupProvider class.
     * Gets an LdapManager instance from the LdapManager class.
     *
     */
    public LdapGroupProvider() {
        manager = LdapManager.getInstance();
        userManager = UserManager.getInstance();
        groupCount = -1;
        expiresStamp = System.currentTimeMillis();
        standardAttributes = new String[3];
        standardAttributes[0] = manager.getGroupNameField();
        standardAttributes[1] = manager.getGroupDescriptionField();
        standardAttributes[2] = manager.getGroupMemberField();
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param group Name of the group to be created.
     * @throws UnsupportedOperationException when called.
     */
    public Group createGroup (String group)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param group Group that should be deleted.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup (Group group)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param group Group that should be deleted.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup (String group)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Will populate a group object based on the group name
     * that is given.
     *
     * @param group Name of group that should be retrieved.
     * @return a populated group based on the name provided.
     */
    public Group getGroup (String group)
    {
        String filter = MessageFormat.format(manager.getGroupSearchFilter(),"*");
        String searchFilter = "(&"+filter+"("+
                              manager.getGroupNameField()+"="+group+"))";
        Collection<Group> groups = populateGroups(searchForGroups(searchFilter,standardAttributes));
        if (groups.size() > 1)
            return null; //if multiple groups found return null
        for (Group g : groups)
            return g; //returns the first group found
        return null;
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param name Current name of group.
     * @param s Desired name of group.
     * @throws UnsupportedOperationException when called.
     */
    public void setName(String name, String s)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param description Current description of group.
     * @param s Desired description of group.
     * @throws UnsupportedOperationException when called.
     */
    public void setDescription(String description,String s)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Will return the current number of groups in the
     * LDAP server.
     *
     * @return the number of groups in the LDAP server.
     */
    public int getGroupCount()
    {
        // Cache group count for 5 minutes.
        if (groupCount != -1 && System.currentTimeMillis() < expiresStamp) {
            return groupCount;
        }
        int count = 0;

        if (manager.isDebugEnabled()) {
            Log.debug("Trying to get the number of groups in the system.");
        }

        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(),"*");
        String returningAttributes[]= { manager.getGroupNameField() };
        NamingEnumeration<SearchResult> answer = searchForGroups(searchFilter,returningAttributes);
        for (; answer.hasMoreElements(); count++)
        {
           try
           {
              answer.next();
           }
           catch (Exception e) { }
        }

        this.groupCount = count;
        this.expiresStamp = System.currentTimeMillis() + JiveConstants.MINUTE *5;
        return count;
    }

    /**
     * Will return a collecion of all groups in the system.
     *
     * @return collection of all groups in the system.
     */
    public Collection<Group> getGroups()
    {
        String filter = MessageFormat.format(manager.getGroupSearchFilter(),"*");
        return populateGroups(searchForGroups(filter,standardAttributes));
    }

    /**
     * Will return a collecion of groups in the system
     * based on the start index and number of groups desired.  
     * Useful when displaying a certain number of groups 
     * per page on a webpage.
     *
     * @param start starting index
     * @param num number of groups you want
     * @return collection of groups.
     */
    public Collection<Group> getGroups(int start, int num)
    {
        ArrayList<Group> returnCollection = new ArrayList<Group>();

        // get an enumeration of all groups in the system

        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(),"*");
        NamingEnumeration<SearchResult> answer = searchForGroups(searchFilter,standardAttributes);

        //place all groups that are wanted into an enumeration

        Vector<SearchResult> v = new Vector<SearchResult>();
        for (int i = 1; answer.hasMoreElements() && i <= (start+num); i++)
        {
           try
           {
              SearchResult sr = answer.next();
              if (i >= start)
                 v.add(sr);
           }
           catch (Exception e) { }
        }

        return populateGroups(v.elements());
    }

    /**
     * Will return a collection of groups in the
     * system that the user provided belongs to.
     *
     * @param user a user
     * @return collection of groups.
     */
    public Collection<Group> getGroups(User user)
    {
        String username = JID.unescapeNode(user.getUsername());
        if (!manager.getPosixEnabled())
        {
           try
           {
              username = manager.findUserDN(username) + "," +
                         manager.getBaseDN();
           }
           catch (Exception e)
           {
              return new ArrayList<Group>();
           }
        }

        String filter = MessageFormat.format(manager.getGroupSearchFilter(),username);
        return populateGroups(searchForGroups(filter,standardAttributes));
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param groupName Name of a group.
     * @param username Name of a user.
     * @param administrator True if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void addMember(String groupName, String username, boolean administrator)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param groupName Name of a group.
     * @param username Name of a user.
     * @param administrator True if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void updateMember(String groupName, String username, boolean administrator)
    	throws UnsupportedOperationException
    {
	    throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException because
     * LDAP operations are treated as read only.
     *
     * @param groupName Name of a group.
     * @param username Name of a user.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteMember(String groupName, String username)
    	throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always returns true because all LDAP operations
     * are treated as read only.
     *
     * @return true always because all LDAP functions are read only.
     */
    public boolean isReadOnly()
    {
        return true;
    }


    /**
     * An auxilary method used to perform LDAP queries based on a
     * provided LDAP search filter.
     *
     * @return an enumeration of SearchResult.
     * @param searchFilter LDAP search filter used to query.
     */
    private NamingEnumeration<SearchResult> searchForGroups (String searchFilter, 
                                                             String[] returningAttributes)
    {
       if (manager.isDebugEnabled()) {
           Log.debug("Trying to find all groups in the system.");
       }
       DirContext ctx = null;
       NamingEnumeration<SearchResult> answer = null;
       try
       {
          ctx = manager.getContext();
          if (manager.isDebugEnabled()) {
             Log.debug("Starting LDAP search...");
             Log.debug("Using groupSearchFilter: "+searchFilter);
          }

          // Search for the dn based on the groupname.
          SearchControls searchControls = new SearchControls();
          searchControls.setReturningAttributes(returningAttributes);
          searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
          answer = ctx.search("",searchFilter,searchControls);

          if (manager.isDebugEnabled()) {
             Log.debug("... search finished");
          }
       }
       catch (Exception e)
       {
          if (manager.isDebugEnabled())
             Log.debug("Error while searching for groups.",e);
       }
       return answer;
    }

    /**
     * An auxilary method used to populate LDAP groups based on a
     * provided LDAP search result.
     *
     * @return a collection of groups.
     * @param answer LDAP search result.
     */
    private Collection<Group> populateGroups (Enumeration<SearchResult> answer)
    {
       if (manager.isDebugEnabled()) {
          Log.debug("Starting to populate groups with users.");
       }

       TreeMap<String,Group> groups = new TreeMap<String,Group>();

       DirContext ctx = null;
       try
       {
          ctx = manager.getContext();
       }
       catch (Exception e)
       {
          return new ArrayList<Group>();
       }

       SearchControls ctrls = new SearchControls();
       ctrls.setReturningAttributes( new String[]{manager.getUsernameField()} );
       ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);

       String userSearchFilter = MessageFormat.format(manager.getSearchFilter(),"*");

       while (answer.hasMoreElements())
       {
          String name = "";
          try
          {
             Attributes a = (((SearchResult)answer.nextElement()).getAttributes());
             String description;
             try
             {
                name = ((String)((a.get(manager.getGroupNameField())).get()));
                description = ((String)((a.get(manager.getGroupDescriptionField())).get()));
             }
             catch (Exception e)
             {
                description = "";
             }
             TreeSet<String> members = new TreeSet<String>();
             Attribute member = a.get(manager.getGroupMemberField());
             NamingEnumeration ne = member.getAll();
             while (ne.hasMore())
             {
                String userName = (String)ne.next();
                if (!manager.getPosixEnabled())
                {   //userName is full dn if not posix
                    try
                    {
                       // Get the CN using LDAP
                       LdapName ldapname = new LdapName(userName);
                       String ldapcn = ldapname.get(ldapname.size()-1);

                       // We have to do a new search to find the username field

                       String combinedFilter = "(&("+ldapcn+")"+userSearchFilter+")";
                       NamingEnumeration usrAnswer = ctx.search("",combinedFilter,ctrls);
                       if (usrAnswer.hasMoreElements())
                       {
                    	   userName = (String)((SearchResult)usrAnswer.next()).getAttributes().get(
                                   manager.getUsernameField()).get();
                       }
                       else
                          throw new UserNotFoundException();
                    }
                    catch (Exception e)
                    {
                       if (manager.isDebugEnabled())
                          Log.debug("Error populating user with DN: "+userName,e);
                    }
                }
                try
                {
                    User user = userManager.getUser(JID.escapeNode(userName));
                    members.add(user.getUsername());
                }
                catch (UserNotFoundException e)
                {
                    if (manager.isDebugEnabled())
                       Log.debug("User not found: "+userName);
                }
             }
             if (manager.isDebugEnabled())
            	 Log.debug("Adding group \""+name+"\" with "+members.size()+" members.");
             Group g = new Group(this,name,description,members,new ArrayList<String>());
             groups.put(name,g);
          }
          catch (Exception e)
          {
             if (manager.isDebugEnabled())
                Log.debug("Error while populating group, "+name+".",e);
          }
       }
       if (manager.isDebugEnabled())
          Log.debug("Finished populating group(s) with users.");

       try
       {
          ctx.close();
       }
       catch (Exception e) { }

       return groups.values();
    }
}