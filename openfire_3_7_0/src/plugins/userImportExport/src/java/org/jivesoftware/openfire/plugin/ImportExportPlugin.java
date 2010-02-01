package org.jivesoftware.openfire.plugin;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.fileupload.FileItem;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterItemProvider;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * The user import/export plugin provides a way to import and export Openfire
 * user data via the Admin Console. The user data consists of username, 
 * name, email address, password and roster list (aka "buddy list"). This plugin also 
 * can aid in the migration of users from other Jabber/XMPP based systems to Jive 
 * Openfire.
 * 
 * @author <a href="mailto:ryan@version2software.com">Ryan Graham</a>
 */
public class ImportExportPlugin implements Plugin {
	
	private static final Logger Log = LoggerFactory.getLogger(ImportExportPlugin.class);
	
    private UserManager userManager;
    private UserProvider provider;
    private String serverName;
    
    public ImportExportPlugin() {
        userManager = XMPPServer.getInstance().getUserManager();
        provider = UserManager.getUserProvider();
        serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
    }

    public void destroyPlugin() {
        userManager = null;
        provider = null;
        serverName = null;
    }
    
    /**
     * Convenience method that returns true if this UserProvider is read-only.
     *
     * @return true if the user provider is read-only.
     */
    public boolean isUserProviderReadOnly() {
        return provider.isReadOnly();
    }
    
    /**
     * Converts the user data that is to be exported to a byte[]. If a read-only
     * user store is being used a user's password will be the same as their username.
     *
     * @return a byte[] of the user data.
     * @throws IOException if there's a problem writing to the XMLWriter.
     */
    public byte[] exportUsersToByteArray() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
        writer.write(exportUsers());
        
        return out.toByteArray();
    }
    
    /**
     * Converts the exported user data to a String. If a read-only
     * user store is being used a user's password will be the same as their username.
     *
     * @return a formatted String representation of the user data.
     * @throws IOException if there's a problem writing to the XMLWriter.
     */
    public String exportUsersToString() throws IOException {
        StringWriter stringWriter = new StringWriter();
        XMLWriter writer = null;
        try {
           writer = new XMLWriter(stringWriter, OutputFormat.createPrettyPrint());
           writer.write(exportUsers());
        } catch (IOException ioe) {
            Log.error(ioe.getMessage(), ioe);
            throw ioe;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        return stringWriter.toString();
    }
    
    /**
     * Returns a list of usernames that were unable to be imported or whose rosters could not imported. Users are not able to be 
     * imported for the following reasons:
     * <li>Their username is not properly formatted.
     * <li>If a read-only user data store is being used and the user could not be found.
     * <li>If a writeable user data store is being used and the user already exists.
     *
     * @param file a FileItem containing the user data to be imported.
     * @param previousDomain a String an optional parameter that if supplied will replace the user roster entries domain names to 
     * server name of current Openfire installation.
     * @return True if FileItem matches the openfire user schema.
     * @throws IOException if there is a problem reading the FileItem.
     * @throws DocumentException if an error occurs during parsing.
     */
    public List<String> importUserData(FileItem file, String previousDomain) throws DocumentException, IOException {
        SAXReader reader = new SAXReader();
        Document document = reader.read(file.getInputStream());
        return importUsers(document, previousDomain);
    }
    
    /**
     * Returns whether or not the supplied FileItem matches the openfire user schema
     *
     * @param file a FileItem to be validated.
     * @return True if FileItem matches the openfire user schema.
     */
    public boolean validateImportFile(FileItem file) {
        try {
            return new UserSchemaValidator(file, "wildfire-user-schema.xsd.xml").validate();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            return false;
        }
    }
    
    private Document exportUsers() {
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement("Openfire");

        Collection<User> users = userManager.getUsers();
        for (User user : users) {
            Element userElement = root.addElement("User");
            String userName = user.getUsername();
            userElement.addElement("Username").addText(userName);
            
            try {
                userElement.addElement("Password").addText(AuthFactory.getPassword(user.getUsername()));
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
        List<String> invalidUsers = new ArrayList<String>();
        
        UserManager userManager = UserManager.getInstance();
        RosterItemProvider rosterItemProvider = RosterItemProvider.getInstance();
        
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
                    userName = Stringprep.nodeprep(userName);
                    
                    if (!isUserProviderReadOnly()) {
                       userManager.createUser(userName, password, name, email);
                    }
                    
                    //Check to see user exists before adding their roster, this is for read-only user providers.
                    userManager.getUser(userName);
                    for (RosterItem ri : rosterItems) {
                       rosterItemProvider.createItem(userName, ri);
                    }
                }
                catch (StringprepException se) {
                    Log.info("Invalid username " + userName);
                    invalidUsers.add(userName);
                }
                catch (UserAlreadyExistsException e) {
                    Log.info("User already exists " + userName);
                    invalidUsers.add(userName);
                }
                catch (UserNotFoundException e) {
                    Log.info("User not found " + userName);
                    invalidUsers.add(userName);
               }
            }
        }
        
        return invalidUsers;
    }
}
