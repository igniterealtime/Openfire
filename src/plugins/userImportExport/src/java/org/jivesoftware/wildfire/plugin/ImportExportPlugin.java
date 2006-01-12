package org.jivesoftware.wildfire.plugin;

import org.apache.commons.fileupload.FileItem;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.wildfire.roster.RosterItemProvider;
import org.jivesoftware.wildfire.user.User;
import org.jivesoftware.wildfire.user.UserAlreadyExistsException;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.user.UserProvider;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * The user import/export plugin provides a way to import and export Wildfire
 * user data via the Admin Console. The user data consists of jid (aka "username"), 
 * name, email address, password and roster list (aka "buddy list"). This plugin also 
 * can aid in the migration of users from other Jabber/XMPP based systems to Jive 
 * Wildfire.
 * 
 * @author Ryan Graham
 */
public class ImportExportPlugin implements Plugin {
    private UserManager userManager;
    private UserProvider provider;
    
    private String serverName;
    
    public ImportExportPlugin() {
        userManager = XMPPServer.getInstance().getUserManager();
        provider = UserManager.getUserProvider();
        
        serverName = XMPPServer.getInstance().getServerInfo().getName();
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
    }

    public void destroyPlugin() {
        userManager = null;
        provider = null;
        serverName = null;
    }
    
    public boolean isUserProviderReadOnly() {
        return provider.isReadOnly();
    }
    
    public byte[] exportUsersToFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
        writer.write(exportUsers());
        
        return out.toByteArray();
    }
    
    public String exportUsersToString() throws IOException {
        StringWriter stringWriter = new StringWriter();
        XMLWriter writer = null;
        try {
	        writer = new XMLWriter(stringWriter, OutputFormat.createPrettyPrint());
	        writer.write(exportUsers());
        } catch (IOException ioe) {
            Log.error(ioe);
            throw ioe;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        return stringWriter.toString();
    }
    
    public List<String> importUserData(FileItem file, String previousDomain) throws IOException, DocumentException {        
        SAXReader reader = new SAXReader();
        Document document = reader.read(file.getInputStream());
        return importUsers(document, previousDomain);
    }
    
    public boolean validateImportFile(FileItem file) {
        try { 
            return new UserSchemaValidator(file, "wildfire-user-schema.xsd.xml").validate();
        } 
        catch (Exception e) {
            Log.error(e);
            return false;
        } 
    }
    
    private Document exportUsers() {
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement("Wildfire");

        Collection<User> users = userManager.getUsers();
        for (User user : users) {
            Element userElement = root.addElement("User");
            String userName = user.getUsername();
            userElement.addElement("Username").addText(userName);
			
            try {
                userElement.addElement("Password").addText(provider.getPassword(user.getUsername()));
            }
            catch (UserNotFoundException e) {
                //this should never happen
                Log.info("User not found: " + userName + ", setting password to their username");
                userElement.addElement("Password").addText(userName);
            }
            userElement.addElement("Email").addText(user.getEmail() == null ? "" : user.getEmail());
			
            String name = user.getName();
            userElement.addElement("Name").addText(name == null ? "" : name);
			
            //creation and modified datte are not used as part of the import process but are exported
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
				
                Element groupElement = itemElement.addElement("Group");
                List<String> groups = ri.getGroups();
                for (String group : groups) {
                    groupElement.addText(group);
                }
            }
        }

        return document;
    }
    
    private List<String> importUsers(Document document, String previousDomain) {        
        List<String> duplicateUsers = new ArrayList<String>();
        
        UserManager userManager = UserManager.getInstance();
        RosterItemProvider rosterItemProvider = RosterItemProvider.getInstance();
    	
        Map<String, List<RosterItem>>  rosterMap = new HashMap<String, List<RosterItem>>();
        
        Element users = document.getRootElement();
        
        Iterator usersIter = users.elementIterator("User");
        while (usersIter.hasNext()) {
            Element user = (Element) usersIter.next();
            
            String userName = null;
            String password = null;
            String email = null;
            String name = null;
            List<RosterItem> rosterItems = new ArrayList<RosterItem>();
            
            Iterator userElements = user.elementIterator();
            while (userElements.hasNext()) {
                Element userElement = (Element) userElements.next();
				
                String nameElement = userElement.getName();
				
                if ("Username".equals(nameElement)) {
                    userName = userElement.getText();
                }
                else if ("Password".equals(nameElement)) {
                    password = userElement.getText();
                }
                else if ("Name".equals(nameElement)) {
                    name = userElement.getText();
                }
                else if ("Email".equals(nameElement)) {
                    email = userElement.getText();
                }
                else if ("Roster".equals(nameElement)) {
                    Iterator rosterIter = userElement.elementIterator("Item");    	
                    
                    while (rosterIter.hasNext()) {
                        Element rosterElement = (Element) rosterIter.next();
                        
                        String jid = rosterElement.attributeValue("jid");
                        String askstatus = rosterElement.attributeValue("askstatus");
                        String recvstatus = rosterElement.attributeValue("recvstatus");
                        String substatus = rosterElement.attributeValue("substatus");
                        String nickname = rosterElement.attributeValue("name");
                        
                        List<String> groups = new ArrayList<String>();
                        Iterator groupIter = rosterElement.elementIterator("Group");
                        while (groupIter.hasNext()) {
                            Element group = (Element) groupIter.next();
                            groups.add(group.getText());
                        }
                        
                        //used for migration
                        if (previousDomain != null) {
                            jid = jid.replace(previousDomain, serverName);
                        }
                        
                        rosterItems.add(new RosterItem(new JID(jid),
                                        RosterItem.SubType.getTypeFromInt(Integer.parseInt(substatus)),
                                        RosterItem.AskType.getTypeFromInt(Integer.parseInt(askstatus)),
                                        RosterItem.RecvType.getTypeFromInt(Integer.parseInt(recvstatus)),
                                        nickname,
                                        groups));
                    }
                }
            }
            
            if ((userName != null) && (password != null)) {
                try {
                    userManager.createUser(userName, password, name, email);                    
                    rosterMap.put(userName, rosterItems);
                }
                catch (UserAlreadyExistsException e) {
                    Log.info("User already exists: " + userName);
                    duplicateUsers.add(userName);
                }
            }
        }
        
        //this prevents a user from adding a non-existent user to their roster
        for (String userName: rosterMap.keySet()) {
            for (RosterItem ri: rosterMap.get(userName)) {
                try {
                    // If the contact is a local user then check that the user exists
                    if (serverName.equals(ri.getJid().getDomain())) {
                        userManager.getUser(removeDoman(ri.getJid()));
                    }
                    rosterItemProvider.createItem(userName, ri);
                }
                catch (UserNotFoundException  e) {
                    Log.info("User '" + removeDoman(ri.getJid()) + "' not found, will not be added to '" + userName + "' roster.");
                }
                catch (UserAlreadyExistsException e) {
                    Log.info("User '" + removeDoman(ri.getJid()) + "' already belongs to '" + userName + "' roster.");
                }
            }
        }
        
        return duplicateUsers;
    }

    private static String removeDoman(JID jid) {
        StringTokenizer tokens = new StringTokenizer(jid.toBareJID(), "@");
        if (tokens.hasMoreTokens()) {
            return tokens.nextToken();
        }

        return null;
    }
}
