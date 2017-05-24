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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.dom.DOMElement;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.openfire.OfflineMessage;
import org.jivesoftware.openfire.OfflineMessageStore;
import org.jivesoftware.openfire.PrivateStorage;
import org.jivesoftware.openfire.roster.DefaultRosterItemProvider;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterItemProvider;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.JiveGlobals;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * XEPP-0227 compliant import and export.
 *
 * @author Anno van Vliet
 *
 */
public class Xep227ExporterTest {
 
  public class TestVCardManager extends VCardManager {
    
    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.vcard.VCardManager#getVCard(java.lang.String)
     */
    @Override
    public Element getVCard(String username) {
      logger.finest("getVCard");
      return new DOMElement("VCARD");
    }
  }

  public class TestOfflineMessageStore extends OfflineMessageStore {
    
    //private Collection<OfflineMessage> offMsgs = Collections.emptyList();

    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.OfflineMessageStore#getMessages(java.lang.String, boolean)
     */
    @Override
    public Collection<OfflineMessage> getMessages(String username, boolean delete) {
      logger.finest("getMessages");
      Collection<OfflineMessage> offMsgs = new ArrayList<OfflineMessage>();
      Element element = new DOMElement("message");
      Date creationDate = new Date();
      OfflineMessage offmsg = new OfflineMessage(creationDate, element);
      offmsg.setFrom(new JID("of@server.id"));
      offmsg.setTo(new JID("of@server.id"));
      offmsg.setBody("text");
      offMsgs.add(offmsg);
      return offMsgs;
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.OfflineMessageStore#addMessage(org.xmpp.packet.Message)
     */
    @Override
    public void addMessage(Message message) {
      logger.finest("addMessage:" + message);
      
      assertNotNull(message);
    }
    
  }

  public class TestRosterItemProvider extends DefaultRosterItemProvider implements RosterItemProvider {
    
    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.roster.DefaultRosterItemProvider#createItem(java.lang.String, org.jivesoftware.openfire.roster.RosterItem)
     */
    @Override
    public RosterItem createItem(String username, RosterItem item) throws UserAlreadyExistsException {
      logger.finest("createItem:" + username + " - " + item);
      return item;
    }
  }

  private static Logger logger = Logger.getLogger(Xep227ExporterTest.class.getName());
  
  private OfflineMessageStore offlineMessagesStore;
  private VCardManager vCardManager;
  private PrivateStorage privateStorage;
  private UserManager userManager;
  private RosterItemProvider rosterItemProvider;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    
    URL url = this.getClass().getResource("/test-openfire.xml");
    File f = new File(url.getFile());
    JiveGlobals.setConfigName(f.getName());
    JiveGlobals.setHomeDirectory(f.getParent());
    JiveGlobals.setProperty("provider.user.className",
        "org.jivesoftware.openfire.plugin.TestUserProvider");
    
    offlineMessagesStore = new TestOfflineMessageStore();
    vCardManager = new TestVCardManager();
    privateStorage = null;
    userManager = UserManager.getInstance();
    rosterItemProvider = new TestRosterItemProvider();
    
    //Empty users
    List<User> l = new ArrayList<>(userManager.getUsers());
    for (User user : l ) {
      userManager.deleteUser(user);
      
    }
  }

  /**
   * Test method for {@link org.jivesoftware.openfire.plugin.OpenfireExporter#exportUsers(org.jivesoftware.openfire.user.UserManager)}.
   * @throws UserAlreadyExistsException 
   * @throws IOException 
   */
  @Test
  public void testExportUsers() throws UserAlreadyExistsException, IOException {
    
    InExporter testobject = new Xep227Exporter("serverName", offlineMessagesStore, vCardManager, privateStorage, userManager, null);
    
    for (int i = 0; i < 10; i++) {
      userManager.createUser("username" + i,"pw" , "name" + i, "email" + i);
    }
    
    Document result = testobject.exportUsers();
    
    assertNotNull(result);
    
    assertEquals(1, result.nodeCount());
    assertNotNull(result.node(0));
    Element elem = ((Element)result.node(0));
    assertEquals(1, elem.nodeCount());
    assertNotNull(elem.node(0));
    elem = ((Element)elem.node(0));
    assertEquals(10, elem.nodeCount());
    assertNotNull(elem.node(0));
    elem = ((Element)elem.node(0));
    assertEquals(3, elem.nodeCount());
    assertEquals(2, elem.attributeCount());
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
    writer.write(result);
    
    logger.fine(out.toString() );
    
    assertTrue("Invalid input", testobject.validate(new ByteArrayInputStream(out.toByteArray())));

  }
  
  /**
   * @throws IOException 
   * @throws DocumentException 
   * 
   */
  @Test
  public void testValidateUser() throws DocumentException, IOException {
    logger.finest("testImportUser");

    InExporter testobject = new Xep227Exporter("serverName", offlineMessagesStore, vCardManager, privateStorage, userManager, rosterItemProvider);

    InputStream stream = this.getClass().getResourceAsStream("/test-xepp227-import.xml");

    assertTrue("Invalid input", testobject.validate(stream));

    stream.close();


  }
  
  /**
   * @throws IOException 
   * @throws DocumentException 
   * 
   */
  @Test
  public void testImportUser() throws DocumentException, IOException {
    logger.finest("testImportUser");

    InExporter testobject = new Xep227Exporter("serverName", offlineMessagesStore, vCardManager, privateStorage, userManager, rosterItemProvider);

    InputStream stream = this.getClass().getResourceAsStream("/test-xepp227-import.xml");

    String previousDomain = null;
    boolean isUserProviderReadOnly = false;
    
    List<String> res = testobject.importUsers(stream, previousDomain, isUserProviderReadOnly);
    
    assertNotNull(res);
    assertEquals(0, res.size());

    stream.close();
    
    Collection<User> users = userManager.getUsers();
    assertEquals(4, users.size());
    

  }

  /**
   * Test if XInclude is working
   * 
   * @throws IOException 
   * @throws DocumentException 
   * 
   */
  @Test
  public void testImportXInclude() throws DocumentException, IOException {
    logger.finest("testImportIncludeUser");

    InExporter testobject = new Xep227Exporter("serverName", offlineMessagesStore, vCardManager, privateStorage, userManager, rosterItemProvider);

    String IMPORT_FILE_NAME = "/test-export-xinclude/xep227.xml";
    URL streamurl = this.getClass().getResource(IMPORT_FILE_NAME);
    assertNotNull(streamurl);
    logger.fine("testImportIncludeUser:"+ streamurl.getFile());
    InputStream stream = new FileInputStream(streamurl.getFile());
    
    assertTrue("Invalid input", testobject.validate(stream));
    
    stream.close();
    
    String previousDomain = null;
    boolean isUserProviderReadOnly = false;
    
    stream = this.getClass().getResourceAsStream(IMPORT_FILE_NAME);
    
    List<String> res = testobject.importUsers(stream, previousDomain, isUserProviderReadOnly);
    
    assertNotNull(res);
    assertEquals(0, res.size());

    stream.close();
    
    Collection<User> users = userManager.getUsers();
    assertEquals(2, users.size());
    

  }

}
