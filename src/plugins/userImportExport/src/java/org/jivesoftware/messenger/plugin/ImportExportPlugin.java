package org.jivesoftware.messenger.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.jivesoftware.util.XMLWriter;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.roster.RosterItem;
import org.jivesoftware.messenger.roster.RosterItemProvider;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.UserProvider;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

public class ImportExportPlugin implements Plugin {
	private XMPPServer server;
	private UserManager userManager;
    private PluginManager pluginManager;
    private static UserProvider provider;
    
    private static String serverName;    
    
    public ImportExportPlugin() {
    	server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        provider = userManager.getUserProvider();
        
        serverName = server.getServerInfo().getName();
    }

    public String getName() {
        return pluginManager.getName(this);
    }

    public String getDescription() {
        return pluginManager.getDescription(this);
    }

    public String getAuthor() {
        return pluginManager.getAuthor(this);
    }

    public String getVersion() {
        return pluginManager.getVersion(this);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        pluginManager = manager;
    }

    public void destroyPlugin() {
        server = null;
        userManager = null;
        pluginManager = null;
        provider = null;
    }
    
    public boolean isUserProviderReadOnly() {
        return provider.isReadOnly();
    }
    
    public boolean exportUserData(String file) throws IOException {
        if (!createExportDirectory()) {
            return false;
        }        
        
        if (!file.endsWith(".xml")) {
            file += ".xml";
        }
        String exportFilePath = exportDirectory() + File.separator + file;
        
        XMLWriter writer = new XMLWriter(new FileWriter(exportFilePath), OutputFormat.createPrettyPrint());
        writer.write(exportUsers());
        writer.close();
        
        return true;
    }
    
    private boolean createExportDirectory() {
        boolean isDirReady = true;
        
        if (!(new File(exportDirectory())).exists()) {
            isDirReady = (new File(exportDirectory())).mkdirs();
        }
        
        return isDirReady;
    }
    
    public boolean validateImportFile(String file) {
        String importFilePath = exportDirectory() + File.separator + file;        
        
        try {
            UserSchemaValidator validator = new UserSchemaValidator(importFilePath, "messenger-user-schema.xsd.xml");
            return validator.validate();
        }
        catch (Exception e) {
            Log.error(e);
            return false;
        }
    }
    
    public List importUserData(String file) throws MalformedURLException, DocumentException {
        String importFilePath = exportDirectory() + File.separator + file;
        
        SAXReader reader = new SAXReader();
		Document document = reader.read(new File(importFilePath).toURL());
		return importUsers(document);
    }
    
    private Document exportUsers() {        
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement("JiveMessenger");

        Iterator users = userManager.getUsers().iterator();
		while (users.hasNext()) {
			User user = (User) users.next();
			
			Element userElement = root.addElement("User");
            String userName = user.getUsername();
			userElement.addElement("Jid").addText(userName);
			try {
                userElement.addElement("Password").addText(provider.getPassword(userName));
            }
            catch (UserNotFoundException e) {
                //this should never happen
                Log.info("User not found: " + userName + ", setting password to their username");
                userElement.addElement("Password").addText(userName);                
            }
			userElement.addElement("Email").addText(user.getEmail() == null ? "" : user.getEmail());
			userElement.addElement("Name").addText(user.getName() == null ? "" : user.getName());
			
            //creation and modified datte are not used as part of the import process but are exported
            //for historical purposes, should they be formatted differently?
			userElement.addElement("CreationDate").addText(String.valueOf(user.getCreationDate().getTime()));
			userElement.addElement("ModifiedDate").addText(String.valueOf(user.getModificationDate().getTime()));
			
			Element rosterElement = userElement.addElement("Roster");
			Iterator roster = user.getRoster().getRosterItems().iterator();
			while (roster.hasNext()) {
				RosterItem ri = (RosterItem) roster.next();
				
				Element itemElement = rosterElement.addElement("Item");
				itemElement.addAttribute("jid", removeDoman(ri.getJid()));
				itemElement.addAttribute("askstatus", String.valueOf(ri.getAskStatus().getValue()));
				itemElement.addAttribute("recvstatus", String.valueOf(ri.getRecvStatus().getValue()));
				itemElement.addAttribute("substatus", String.valueOf(ri.getSubStatus().getValue()));
				itemElement.addAttribute("name", ri.getNickname());
				
				Element groupElement = itemElement.addElement("Group");
				Iterator groups = ri.getGroups().iterator();
				while (groups.hasNext()) {
					String group = (String) groups.next();
					groupElement.addText(group);
				}
			}
		}

        return document;
    }
    
    public List<String> importUsers(Document document) throws DocumentException {
        List<String> duplicateUsers = new ArrayList<String>();
        
    	UserManager userManager = UserManager.getInstance();
        RosterItemProvider rosterItemProvider = RosterItemProvider.getInstance();
    	
        Map<String, List>  rosterMap = new HashMap<String, List>();
        
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
				
				if ("Jid".equals(nameElement)) {
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
                        
                        rosterItems.add(new RosterItem(new JID(jid + "@" + serverName),
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
        Iterator i = rosterMap.keySet().iterator();
        while (i.hasNext()) {
            String userName = (String) i.next();
            
            Iterator rosterIter = rosterMap.get(userName).iterator();
            while (rosterIter.hasNext()) {
                RosterItem ri = (RosterItem) rosterIter.next();
                
                try {
                    userManager.getUser(removeDoman(ri.getJid()));
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
    
    public static String exportDirectory() {
        return JiveGlobals.getHomeDirectory() + File.separator + "export";
    }
}
