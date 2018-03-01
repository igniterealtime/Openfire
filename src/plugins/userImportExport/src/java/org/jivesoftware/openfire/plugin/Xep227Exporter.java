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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.DOMReader;
import org.jivesoftware.openfire.OfflineMessage;
import org.jivesoftware.openfire.OfflineMessageStore;
import org.jivesoftware.openfire.PrivateStorage;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterItem.AskType;
import org.jivesoftware.openfire.roster.RosterItem.RecvType;
import org.jivesoftware.openfire.roster.RosterItem.SubType;
import org.jivesoftware.openfire.roster.RosterItemProvider;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

/**
 * A In and Exporter which conforms to XEP-0227.
 *
 * @author Anno van Vliet
 *
 */
public class Xep227Exporter implements InExporter {
  
  /**
   * constants defining field and attribute names
   */
  private static final String V_CARD_NAME = "vCard";
  private static final String ASK_SUBSCRIBE_ENUM = "subscribe";
  private static final String STAMP_NAME = "stamp";
  private static final String DELAY_ELEMENT_NAME = "delay";
  private static final String FROM_NAME = "from";
  private static final String MESSAGE_ELEMENT_NAME = "message";
  private static final String OFFLINE_MESSAGES_ELEMENT_NAME = "offline-messages";
  private static final String GROUP_ELEMENT_NAME = "group";
  private static final String SUBSCRIPTION_NAME = "subscription";
  private static final String ASK_NAME = "ask";
  private static final String ITEM_ELEMENT_NAME = "item";
  private static final String QUERY_ELEMENT_NAME = "query";
  private static final String PASSWORD_NAME = "password";
  private static final String NAME_NAME = "name";
  private static final String USER_ELEMENT_NAME = "user";
  private static final String JID_NAME = "jid";
  private static final String HOST_ELEMENT_NAME = "host";
  private static final String SERVER_DATA_ELEMENT_NAME = "server-data";
  
  /**
   * the relevant namespaces
   */
  private static final String JABBER_CLIENT_NS = "jabber:client";
  private static final String VCARD_TEMP_NS = "vcard-temp";
  private static final String JABBER_IQ_ROSTER_NS = "jabber:iq:roster";
  private static final String URN_XMPP_PIE_0_NS = "urn:xmpp:pie:0";
  
  private static final Namespace JABBER_MSG_NS = new Namespace("", JABBER_CLIENT_NS);

  private static final Logger Log = LoggerFactory.getLogger(Xep227Exporter.class);

  private final String serverName;
  private final OfflineMessageStore offlineMessagesStore;
  private final VCardManager vCardManager;
  //private final PrivateStorage privateStorage;
  private final UserManager userManager;
  private final RosterItemProvider rosterItemProvider;

  private final DateFormat dateformater = new SimpleDateFormat(XMPPDateTimeFormat.XMPP_DATETIME_FORMAT, Locale.US);

  /**
   */
  public Xep227Exporter() {
    offlineMessagesStore = XMPPServer.getInstance()
        .getOfflineMessageStore();
    serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    //TODO not yet implemented
    //privateStorage = XMPPServer.getInstance().getPrivateStorage();
    vCardManager = VCardManager.getInstance();
    
    userManager = UserManager.getInstance();
    rosterItemProvider = RosterManager.getRosterItemProvider();
    

  }

  /**
   * Constructor used for testing purposes.
   * 
   * @param serverName
   * @param offlineMessagesStore
   * @param vCardManager
   * @param privateStorage
   * @param userManager
   * @param rosterItemProvider
   */
  public Xep227Exporter(String serverName, OfflineMessageStore offlineMessagesStore, VCardManager vCardManager, PrivateStorage privateStorage,
      UserManager userManager, RosterItemProvider rosterItemProvider) {
    super();
    this.serverName = serverName;
    this.offlineMessagesStore = offlineMessagesStore;
    this.vCardManager = vCardManager;
    //this.privateStorage = privateStorage;
    this.userManager = userManager;
    this.rosterItemProvider = rosterItemProvider;
  }



  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.plugin.Exporter#exportUsers(org.jivesoftware.openfire.user.UserManager)
   */
  @Override
  public Document exportUsers() {
    Log.debug("exportUsers");
    Document document = DocumentHelper.createDocument();

    Element root = document.addElement(SERVER_DATA_ELEMENT_NAME, URN_XMPP_PIE_0_NS);
    Element host = root.addElement(HOST_ELEMENT_NAME);

    host.addAttribute(JID_NAME, serverName);

    Collection<User> users = userManager.getUsers();
    for (User user : users) {

      String userName = user.getUsername();

      Element userElement = host.addElement(USER_ELEMENT_NAME);

      exportUser(userElement, user);

      exportOfflineMessages(serverName, userElement, userName);

      exportRoster(userElement, user);

      exportVCard(userElement, userName);

      exportPrivateStorage(userName, userElement);

    }

    return document;
  }
  
  
  /**
   * Adding heading of an user and its parameters
   * 
   * @param userElement
   *            DOM element
   * @param user
   *            User object
   */
  private void exportUser(Element userElement, User user) {

    String userName = user.getUsername();
    userElement.addAttribute(NAME_NAME, userName);

    try {
      String pw = AuthFactory.getPassword(userName);
      userElement.addAttribute(PASSWORD_NAME, pw );

    } catch (UserNotFoundException e) {
      Log.info("User " + userName
          + " not found, setting their password to their username");
      userElement.addAttribute(PASSWORD_NAME, userName);
    } catch (UnsupportedOperationException e) {
      Log.info("Unable to retrieve " + userName
          + " password, setting their password to their username");
      userElement.addAttribute(PASSWORD_NAME, userName);
    }

  }

  /**
   * Add roster and its groups to a DOM element
   * 
   * @param userElement
   *            DOM element
   * @param user
   *            User
   */
  private void exportRoster(Element userElement, User user) {
    Element rosterElement = userElement.addElement(QUERY_ELEMENT_NAME,
        JABBER_IQ_ROSTER_NS);

    Collection<RosterItem> roster = user.getRoster().getRosterItems();
    for (RosterItem ri : roster) {
      Element itemElement = rosterElement.addElement(ITEM_ELEMENT_NAME);

      itemElement.addAttribute(JID_NAME, ri.getJid().toBareJID());
      itemElement.addAttribute(NAME_NAME, ri.getNickname());
      itemElement.addAttribute(SUBSCRIPTION_NAME, ri.getSubStatus()
          .getName());
      if ( ri.getAskStatus() == AskType.SUBSCRIBE ) {
        itemElement.addAttribute(ASK_NAME, ASK_SUBSCRIBE_ENUM);
      }

      /**
       * Adding groups
       */
      Element groupElement = itemElement.addElement(GROUP_ELEMENT_NAME);
      List<String> groups = ri.getGroups();
      for (String group : groups) {
        groupElement.addText(group);
      }

    }
  }

  /**
   * Adding offline messages, if there are some.
   * 
   * @param hostname
   *            host name
   * @param userElement
   *            DOM element
   * @param userName
   *            user name
   */
  @SuppressWarnings("unchecked")
  private void exportOfflineMessages(String hostname, Element userElement,
      String userName) {
    Collection<OfflineMessage> offlineMessages = offlineMessagesStore
        .getMessages(userName, false);

    if (!offlineMessages.isEmpty()) {
      Element offlineElement = userElement.addElement(OFFLINE_MESSAGES_ELEMENT_NAME);

      for (OfflineMessage offMessage : offlineMessages) {
        
        Element messageElement = offlineElement.addElement(new QName(MESSAGE_ELEMENT_NAME, JABBER_MSG_NS));
        for ( Object att : offMessage.getElement().attributes() ) {
          Attribute attribute = (Attribute) att;
          messageElement.addAttribute(attribute.getQName(),attribute.getValue());
        }
        
        for (Iterator<Element> iterator = offMessage.getElement().elementIterator(); iterator
            .hasNext();) {
          Element element = iterator.next();
          messageElement.add(element.createCopy(new QName(element.getName(), (element.getNamespace() == Namespace.NO_NAMESPACE ? JABBER_MSG_NS : element.getNamespace()))));
          
        }
        
        /**
         * Adding delay element
         */
        Element delayElement = messageElement.addElement("delay", "urn:xmpp:delay");
        delayElement.addAttribute(FROM_NAME, hostname);
        delayElement.addAttribute("stamp", XMPPDateTimeFormat.format(offMessage.getCreationDate()));
        delayElement.addText("Offline Storage");
      }

    }

  }

  /**
   * Adding vcard element
   * 
   * @param userElement
   *            DOM element
   * @param userName
   *            user name
   */
  @SuppressWarnings("unchecked")
  private void exportVCard(Element userElement, String userName) {
    Element vCard = vCardManager.getVCard(userName);
    if (vCard != null) {
      Element vCardElement = userElement
          .addElement(V_CARD_NAME, VCARD_TEMP_NS);
      for (Iterator<Element> iterator = vCard.elementIterator(); iterator
          .hasNext();) {
        Element element = iterator.next();
        vCardElement.add(element.createCopy());
        
      }
    }
  }

  /**
   * Add all the private stored information (XEP-0049)
   * <b>Note: this method is not supported in the available openfire releases.
     *  
   * </b>  
   * @param userName User name
   * @param userElement User element
   */
  private void exportPrivateStorage(String userName, Element userElement) {
//    Element result = privateStorage.getAll(userName);
//    if (result.elements().size() > 0) {
//      userElement.add(result.createCopy());
//    }
  }


  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.plugin.InExporter#validate()
   */
  @Override
  public boolean validate(InputStream file) {
    Log.debug("validate");
    
    org.w3c.dom.Document doc = new UserSchemaValidator(file, "pie.xsd", "jabber-iq-roster.xsd", "jabber-iq-private.xsd","xml.xsd","stanzaerror.xsd","jabber-client.xsd").validateAndParse();
    
    return ( doc != null );
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

  
  /* (non-Javadoc)
   * @see org.jivesoftware.openfire.plugin.InExporter#importUsers(org.dom4j.Document, java.lang.String, boolean)
   */
  @SuppressWarnings("unchecked")
  private List<String> importUsers(Document document, String previousDomain, boolean isUserProviderReadOnly) {

    List<String> invalidUsers = new ArrayList<String>();

    Element hosts = document.getRootElement();

    Iterator<Element> hostsIter = hosts.elementIterator(HOST_ELEMENT_NAME);
    while (hostsIter.hasNext()) {
      Element host = hostsIter.next();

      Iterator<Element> usersIter = host.elementIterator(USER_ELEMENT_NAME);
      while (usersIter.hasNext()) {
        Element user = usersIter.next();
        
        importUser(user,previousDomain,isUserProviderReadOnly,invalidUsers);
      }
    }

    return invalidUsers;
  }

  /**
   * @param user
   * @param previousDomain
   * @param isUserProviderReadOnly
   * @param invalidUsers
   */
  @SuppressWarnings("unchecked")
  private void importUser(Element user, String previousDomain, boolean isUserProviderReadOnly, List<String> invalidUsers) {
    Log.debug("importUser");

    List<RosterItem> rosterItems = new ArrayList<RosterItem>();
    List<OfflineMessage> offlineMessages = new ArrayList<OfflineMessage>();
    Element vCardElement = null;
    
    String userName = user.attributeValue(NAME_NAME);
    String password = user.attributeValue(PASSWORD_NAME);

    Iterator<Element> userElements = user.elementIterator();
    while (userElements.hasNext()) {
      Element userElement = userElements.next();

      String nameElement = userElement.getName();
      if (OFFLINE_MESSAGES_ELEMENT_NAME.equals(nameElement)) {

        importOffLineMessages(userElement, offlineMessages);

      } else if (QUERY_ELEMENT_NAME.equals(nameElement) && JABBER_IQ_ROSTER_NS.equals(userElement.getNamespaceURI())) {

        importUserRoster(userElement, rosterItems, previousDomain);

      } else if (V_CARD_NAME.equals(nameElement) && VCARD_TEMP_NS.equals(userElement.getNamespaceURI())) {

        vCardElement = userElement;

      }
    }

    if ( userName != null ) {
      try {
        userName = Stringprep.nodeprep(userName);

        if (!isUserProviderReadOnly && (password != null) ) {
          userManager.createUser(userName, password, userName, null);
        }
        
        if ( !isUserProviderReadOnly && vCardElement != null ) {
          try {
            vCardManager.setVCard(userName, vCardElement);
          } catch (Exception e) {
            Log.warn("Error updating VCard:" + userName + ":" + e.getMessage());
            Log.debug("", e);
            
          }
        }

        // Check to see user exists before adding their roster, this is for
        // read-only user providers.
        userManager.getUser(userName);
        for (RosterItem ri : rosterItems) {
          rosterItemProvider.createItem(userName, ri);
        }
        for (OfflineMessage offlineMessage : offlineMessages) {
          offlineMessagesStore.addMessage(offlineMessage);
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
      } catch (Exception e) {
        Log.warn("Error updating User:" + userName + ":" + e.getLocalizedMessage());
        invalidUsers.add(userName);
      }
    }

  }

  /**
   * @param userElement
   * @param rosterItems
   * @param previousDomain
   */
  @SuppressWarnings("unchecked")
  private void importUserRoster(Element userElement, List<RosterItem> rosterItems, String previousDomain) {
    Log.debug("importUserRoster");

    Iterator<Element> rosterIter = userElement.elementIterator(ITEM_ELEMENT_NAME);

    while (rosterIter.hasNext()) {
      Element rosterElement = rosterIter.next();

      String jid = rosterElement.attributeValue(JID_NAME);
      String nickname = rosterElement.attributeValue(NAME_NAME);
      String substatus = rosterElement.attributeValue(SUBSCRIPTION_NAME);
      String askstatus = rosterElement.attributeValue(ASK_NAME);

      List<String> groups = new ArrayList<String>();
      Iterator<Element> groupIter = rosterElement.elementIterator(GROUP_ELEMENT_NAME);
      while (groupIter.hasNext()) {
        Element group = groupIter.next();
        String groupName = group.getText();
        if (groupName != null && groupName.trim().length() > 0) {
          groups.add(groupName);
        }
      }

      // used for migration
      if (previousDomain != null && jid != null ) {
        jid = jid.replace(previousDomain, serverName);
      }

      try {
        rosterItems.add(new RosterItem(new JID(jid), 
            ( substatus != null ? SubType.valueOf(substatus.toUpperCase()) : SubType.BOTH ), 
            ( ASK_SUBSCRIBE_ENUM.equals(askstatus) ? AskType.SUBSCRIBE : AskType.NONE), 
            RecvType.NONE, 
            nickname, groups));

      } catch (Exception e) {
        Log.warn("Adding User Roster failed:" + e.getLocalizedMessage());
        Log.debug("", e);

      }
    }

  }

  /**
   * @param userElement
   * @param offlineMessages
   */
  @SuppressWarnings("unchecked")
  private void importOffLineMessages(Element userElement, List<OfflineMessage> offlineMessages) {
    Log.debug("importOffLineMessages");
    // TODO Auto-generated method stub

    Iterator<Element> messageIter = userElement.elementIterator(MESSAGE_ELEMENT_NAME);

    while (messageIter.hasNext()) {
      Element msgElement = messageIter.next();

      String creationDateStr = null;
      if (msgElement.element(DELAY_ELEMENT_NAME) != null) {
        creationDateStr = msgElement.element(DELAY_ELEMENT_NAME).attributeValue(STAMP_NAME);
      }

      Date creationDate = null;
      try {
        if (creationDateStr != null) {
          creationDate = dateformater.parse(creationDateStr);
        }
      } catch (ParseException e) {
        Log.warn("Date not parsable:" + e.getLocalizedMessage());
      }

      offlineMessages.add(new OfflineMessage(creationDate, msgElement));
    }
  }

}
