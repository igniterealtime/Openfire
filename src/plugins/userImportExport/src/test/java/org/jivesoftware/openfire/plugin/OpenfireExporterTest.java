package org.jivesoftware.openfire.plugin;/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.openfire.roster.DefaultRosterItemProvider;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterItemProvider;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.junit.Before;
import org.junit.Test;

/**
 * Test Openfire flavour of Import and export
 *
 * @author Anno van Vliet
 *
 */
public class OpenfireExporterTest {

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

  private static Logger logger = Logger.getLogger(OpenfireExporterTest.class.getName());
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
    
    userManager = UserManager.getInstance();
    rosterItemProvider = new TestRosterItemProvider();

  }

  /**
   * Test method for {@link org.jivesoftware.openfire.plugin.OpenfireExporter#exportUsers(org.jivesoftware.openfire.user.UserManager)}.
   * @throws UserAlreadyExistsException 
   * @throws IOException 
   */
  @Test
  public void testExportUsers() throws UserAlreadyExistsException, IOException {
    
    InExporter testobject = new OpenfireExporter( "serverName", userManager, rosterItemProvider);
    
    for (int i = 0; i < 10; i++) {
      userManager.createUser("username" + i,"pw" , "name" + i, "email" + i);
    }
    
    Document result = testobject.exportUsers();
    
    assertNotNull(result);
    
    assertEquals(1, result.nodeCount());
    assertNotNull(result.node(0));
    Element elem = ((Element)result.node(0));
    assertEquals(10, elem.nodeCount());
    assertNotNull(elem.node(0));
    assertEquals(7, ((Element)elem.node(0)).nodeCount());

    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
    writer.write(result);
    
    logger.fine(out.toString() );
    
    assertNotNull(testobject.validate(new ByteArrayInputStream(out.toByteArray())));

  }
  
  /**
   * @throws IOException 
   * @throws DocumentException 
   * 
   */
  @Test
  public void testImportUser() throws DocumentException, IOException {
    logger.finest("testImportUser");

    InExporter testobject = new OpenfireExporter("serverName",userManager,rosterItemProvider);

    String TEST_IMPORT_FILE = "/test-openfire-import.xml";
    
    InputStream stream = this.getClass().getResourceAsStream(TEST_IMPORT_FILE);
    
    assertTrue("Invalid input", testobject.validate(stream));
    
    stream.close();
    
    String previousDomain = null;
    boolean isUserProviderReadOnly = false;
    stream = this.getClass().getResourceAsStream(TEST_IMPORT_FILE);
    
    List<String> res = testobject.importUsers(stream, previousDomain, isUserProviderReadOnly);
    
    assertNotNull(res);
    assertEquals(0, res.size());

    stream.close();

  }
  
}
