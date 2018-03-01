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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.DOMReader;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterItemProvider;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

/**
 * Export and import Users in the Openfire XML format.
 *
 * @author Anno van Vliet
 *
 */
public class OpenfireExporter implements InExporter {
  private static final Logger Log = LoggerFactory.getLogger(OpenfireExporter.class);
  private final String serverName;
  private final UserManager userManager;
  private final RosterItemProvider rosterItemProvider;
  
  /**
   */
  public OpenfireExporter() {
    serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    userManager = UserManager.getInstance();
    rosterItemProvider = RosterManager.getRosterItemProvider();


  }
  
  /**
   * @param serverName
   * @param userManager
   * @param rosterItemProvider
   */
  public OpenfireExporter(String serverName, UserManager userManager, RosterItemProvider rosterItemProvider) {
    super();
    this.serverName = serverName;
    this.userManager = userManager;
    this.rosterItemProvider = rosterItemProvider;
  }


  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.plugin.Exporter#exportUsers()
   */
  @Override
  public Document exportUsers() {
    Log.debug("exportUsers");
    Document document = DocumentHelper.createDocument();
    Element root = document.addElement("Openfire");

    Collection<User> users = userManager.getUsers();
    for (User user : users) {
        Element userElement = root.addElement("User");
        String userName = user.getUsername();
        userElement.addElement("Username").addText(userName);
        
        try {
          String pw = AuthFactory.getPassword(user.getUsername());
          userElement.addElement("Password").addText(pw);
        }
        catch (UserNotFoundException e) {
            Log.info("User " + userName + " not found, setting their password to their username");
            userElement.addElement("Password").addText(userName);
        }
        catch (UnsupportedOperationException e) {
           Log.info("Unable to retrieve " + userName + " password, setting their password to their username");
           userElement.addElement("Password").addText(userName);
        }
        userElement.addElement("Email").addText(user.getEmail() == null ? "" : user.getEmail());
        
        String name = user.getName();
        userElement.addElement("Name").addText(name == null ? "" : name);
        
        //creation and modified date are not used as part of the import process but are exported
        //for historical purposes, should they be formatted differently?
        userElement.addElement("CreationDate").addText(String.valueOf(user.getCreationDate().getTime()));
        userElement.addElement("ModifiedDate").addText(String.valueOf(user.getModificationDate().getTime()));
        
        Element rosterElement = userElement.addElement("Roster");
        Collection<RosterItem> roster = user.getRoster().getRosterItems();
        for (RosterItem ri : roster) {
            Element itemElement = rosterElement.addElement("Item");
            itemElement.addAttribute("jid", ri.getJid().toBareJID());
            itemElement.addAttribute("askstatus", String.valueOf(ri.getAskStatus().getValue()));
            itemElement.addAttribute("recvstatus", String.valueOf(ri.getRecvStatus().getValue()));
            itemElement.addAttribute("substatus", String.valueOf(ri.getSubStatus().getValue()));
            itemElement.addAttribute("name", ri.getNickname());
            
            List<String> groups = ri.getGroups();
            for (String group : groups) {
                if (group != null && group.trim().length() > 0) {
                  itemElement.addElement("Group").addText(group);
                }
            }
        }
    }

    return document;
}


  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.plugin.InExporter#validate()
   */
  @Override
  public boolean validate(InputStream file) {
    Log.debug("validate");
    
    org.w3c.dom.Document doc = new UserSchemaValidator(file, "wildfire-user-schema.xsd.xml").validateAndParse();
    
    return ( doc != null);
  }

  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.plugin.InExporter#importUsers(java.io.InputStream, java.lang.String, boolean)
   */
  @Override
  public List<String> importUsers(InputStream inputStream, String previousDomain, boolean isUserProviderReadOnly) {
    Log.debug("importUsers");
        
    DOMReader xmlReader = new DOMReader();            
    Document doc = xmlReader.read(new UserSchemaValidator(inputStream).validateAndParse());
    
    return importUsers(doc, previousDomain, isUserProviderReadOnly);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.jivesoftware.openfire.plugin.InExporter#importUsers(org.dom4j.Document,
   * java.lang.String)
   */
  @SuppressWarnings("unchecked")
  private List<String> importUsers(Document document, String previousDomain, boolean isUserProviderReadOnly) {
    Log.debug("importUsers");
    List<String> invalidUsers = new ArrayList<String>();

    Element users = document.getRootElement();

    Iterator<Element> usersIter = users.elementIterator("User");
    while (usersIter.hasNext()) {
      Element user = usersIter.next();

      String userName = null;
      String password = null;
      String email = null;
      String name = null;
      List<RosterItem> rosterItems = new ArrayList<RosterItem>();

      Iterator<Element> userElements = user.elementIterator();
      while (userElements.hasNext()) {
        Element userElement = userElements.next();

        String nameElement = userElement.getName();
        if ("Username".equals(nameElement)) {
          userName = userElement.getText();
        } else if ("Password".equals(nameElement)) {
          password = userElement.getText();
        } else if ("Name".equals(nameElement)) {
          name = userElement.getText();
        } else if ("Email".equals(nameElement)) {
          email = userElement.getText();
        } else if ("Roster".equals(nameElement)) {
          Iterator<Element> rosterIter = userElement.elementIterator("Item");

          while (rosterIter.hasNext()) {
            Element rosterElement = rosterIter.next();

            String jid = rosterElement.attributeValue("jid");
            String askstatus = rosterElement.attributeValue("askstatus");
            String recvstatus = rosterElement.attributeValue("recvstatus");
            String substatus = rosterElement.attributeValue("substatus");
            String nickname = rosterElement.attributeValue("name");

            List<String> groups = new ArrayList<String>();
            Iterator<Element> groupIter = rosterElement.elementIterator("Group");
            while (groupIter.hasNext()) {
              Element group = groupIter.next();
              String groupName = group.getText();
              if (groupName != null && groupName.trim().length() > 0) {
                groups.add(groupName);
              }
            }

            // used for migration
            if (previousDomain != null) {
              jid = jid.replace(previousDomain, serverName);
            }

            rosterItems.add(new RosterItem(new JID(jid), RosterItem.SubType.getTypeFromInt(Integer.parseInt(substatus)),
                RosterItem.AskType.getTypeFromInt(Integer.parseInt(askstatus)), RosterItem.RecvType.getTypeFromInt(Integer.parseInt(recvstatus)),
                nickname, groups));
          }
        }
      }

      if (userName != null) {
        try {
          userName = Stringprep.nodeprep(userName);

          if (!isUserProviderReadOnly && (password != null)) {
            userManager.createUser(userName, password, name, email);
          }

          // Check to see user exists before adding their roster, this is for
          // read-only user providers.
          userManager.getUser(userName);
          for (RosterItem ri : rosterItems) {
            rosterItemProvider.createItem(userName, ri);
          }
        } catch (StringprepException se) {
          Log.info("Invalid username " + userName);
          invalidUsers.add(userName);
        } catch (UserAlreadyExistsException e) {
          Log.info("User already exists " + userName);
          invalidUsers.add(userName);
        } catch (UserNotFoundException e) {
          Log.info("User not found " + userName);
          invalidUsers.add(userName);
        }
      }
    }

    return invalidUsers;
  }
  
}
