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
        Collection<Group> groups = getGroupBasedOnFilter(searchFilter);
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
        DirContext ctx = null;
        NamingEnumeration answer = null;
        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(),"*");
        try
        {
           ctx = manager.getContext();
           if (manager.isDebugEnabled()) {
              Log.debug("Starting LDAP search...");
              Log.debug("Using groupSearchFilter: "+searchFilter);
           }

           // Search for the dn based on the groupname.
           SearchControls ctrls = new SearchControls();
           ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
           answer = ctx.search("",searchFilter,ctrls);

           if (manager.isDebugEnabled()) {
              Log.debug("... search finished");
           }
        }
        catch (Exception e)
        {
           if (manager.isDebugEnabled())
              Log.debug("Error while searching for groups.",e);
        }
        try
        {
           while (answer.hasMoreElements())
           {
              count++;
              answer.next();
           }
        }
        catch (Exception e){ }

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
        return getGroupBasedOnFilter(filter);
    }

    /**
     * Will return a collecion of groups in the system
     * based on the start index and end index.  Useful when
     * displaying a certain number of groups per page
     * on a webpage.
     *
     * @param start starting index
     * @param end ending index
     * @return collection of groups.
     */
    public Collection<Group> getGroups(int start, int end)
    {
        ArrayList<Group> returnCollection = new ArrayList<Group>();
    	Collection<Group> groups = getGroups();
        Iterator<Group> it = groups.iterator();
        for (int i = 0; i < groups.size(); i++)
        {
           Group g = it.next();
           if (i >= start && i <= end)
              returnCollection.add(g);
           if (i > end)
              break;
        }
        return returnCollection;
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
        return getGroupBasedOnFilter(filter);
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
     * @return a collection of groups.
     * @param searchFilter LDAP search filter used to query.
     */
    public Collection<Group> getGroupBasedOnFilter (String searchFilter)
    {
       TreeMap<String,Group> groups = new TreeMap<String,Group>();
       boolean debug = Log.isDebugEnabled();
       if (debug) {
           Log.debug("Trying to find all groups in the system.");
       }
       DirContext ctx = null;
       NamingEnumeration answer = null;
       try
       {
          ctx = manager.getContext();
          if (manager.isDebugEnabled()) {
             Log.debug("Starting LDAP search...");
             Log.debug("Using groupSearchFilter: "+searchFilter);
          }

          // Search for the dn based on the groupname.
          SearchControls searchControls = new SearchControls();
          String returnedAtts[]= { manager.getGroupNameField(),
                                   manager.getGroupDescriptionField(),
                                   manager.getGroupMemberField() };
          searchControls.setReturningAttributes(returnedAtts);
          searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
          answer = ctx.search("",searchFilter,searchControls);

          if (manager.isDebugEnabled()) {
             Log.debug("... search finished");
             Log.debug("Starting to populate groups with users.");
          }
       }
       catch (Exception e)
       {
          if (manager.isDebugEnabled())
             Log.debug("Error while searching for groups.",e);
          return groups.values();
       }

       SearchControls ctrls = new SearchControls();
       ctrls.setReturningAttributes( new String[]{manager.getUsernameField()} );
       ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);

       while (answer.hasMoreElements())
       {
          String name = "";
          try
          {
             Attributes a = (((SearchResult)answer.next()).getAttributes());
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
                       Name ldapname = new LdapName(userName);
                       String ldapcn = ldapname.get(ldapname.size()-1);

                       // We have to do a new search to find the username field

                       NamingEnumeration usrAnswer = ctx.search("",ldapcn,ctrls);
                       if (usrAnswer.hasMoreElements())
                       {
                    	   userName = (String)((SearchResult)usrAnswer.next()).getAttributes().get(
                                   manager.getUsernameField()).get();
                       }
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