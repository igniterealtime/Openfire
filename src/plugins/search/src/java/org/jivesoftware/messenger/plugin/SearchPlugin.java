/**
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/** 
 * Provides support for Jabber Search
 * (<a href="http://www.jabber.org/jeps/jep-0055.html">JEP-0055</a>).<p>
 *
 * The basic functionality is to query an information repository 
 * regarding the possible search fields, to send a search query, 
 * and to receive search results. This implementation below primarily uses
 * <a href="http://www.jabber.org/jeps/jep-0004.html">Data Forms</a>, but 
 * limited support for non-datforms searches has been added to support the
 * Miranda client.
 * <p/>
 * 
 * @author Ryan Graham 
 */
public class SearchPlugin implements Component, Plugin {
    private XMPPServer server;
    private UserManager userManager;
    private ComponentManager componentManager;
    private PluginManager pluginManager;

    private static final String SERVICE_NAME = "search";
    private static String serverName;

    private static String instructions = "The following fields are available for search. "
        + "Wildcard (*) characters are allowed as part the of query.";
    
    private static Element probeResult;

    private Collection<String> searchFields;

    public SearchPlugin() {
        server = XMPPServer.getInstance();
        serverName = server.getServerInfo().getName();
        // See if the installed provider supports searching. If not, workaround
        // by providing our own searching.
        UserManager manager = UserManager.getInstance();
        try {
            searchFields = manager.getSearchFields();
            userManager = UserManager.getInstance();
            searchFields = userManager.getSearchFields();
        }
        catch (UnsupportedOperationException uoe) {
            // Use a SearchPluginUserManager instead.
            searchFields = getSearchFields();
        }
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
        
        componentManager = ComponentManagerFactory.getComponentManager();
        try {
            componentManager.addComponent(SERVICE_NAME, this);
        }
		catch (Exception e) {
            componentManager.getLog().error(e);
        }

        if (probeResult == null) {
            probeResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:search"));

            //non-data form
            probeResult.addElement("instructions").addText(instructions);
            
            XDataFormImpl searchForm = new XDataFormImpl(DataForm.TYPE_FORM);
            searchForm.setTitle("User Search");
            searchForm.addInstruction(instructions);
			
			XFormFieldImpl field = new XFormFieldImpl("search");
            field.setType(FormField.TYPE_TEXT_SINGLE);			
            field.setLabel("Search");
            field.setRequired(false);
            searchForm.addField(field);
            
            Iterator iter = searchFields.iterator();
            while (iter.hasNext()) {
                String searchField = (String) iter.next();
                
                //non-data form
                probeResult.addElement(searchField.toLowerCase());

                field = new XFormFieldImpl(searchField);
                field.setType(FormField.TYPE_BOOLEAN);
				field.addValue("1");
                field.setLabel(searchField);
                field.setRequired(false);
                searchForm.addField(field);
            }

            probeResult.add(searchForm.asXMLElement());
        }
    }
    
    public void initialize(JID jid, ComponentManager componentManager) {
    }
    
    public void destroyPlugin() {
        pluginManager = null;
        try {
            componentManager.removeComponent(SERVICE_NAME);
            componentManager = null;
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        server = null;
        userManager = null;
    }

    public void shutdown() {
    }
    
    public void processPacket(Packet p) {        
        if (p instanceof IQ) {
            IQ packet = (IQ) p;

            Element childElement = (packet).getChildElement();
            String namespace = null;
            if (childElement != null) {
                namespace = childElement.getNamespaceURI();
            }

            if ("jabber:iq:search".equals(namespace)) {
                try {
                    IQ replyPacket = handleIQ(packet);
                    componentManager.sendPacket(this, replyPacket);
                }
                catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }

            }
            else if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                try {
                    IQ replyPacket = IQ.createResultIQ(packet);

                    Element responseElement = DocumentHelper.createElement(QName.get(
                            "query", "http://jabber.org/protocol/disco#info"));
                    responseElement.addElement("identity").addAttribute("category", "search")
                                                          .addAttribute("type", "text")
                                                          .addAttribute("name", "User Search");
                    responseElement.addElement("feature").addAttribute("var", "jabber:iq:search");
                    replyPacket.setChildElement(responseElement);

                    componentManager.sendPacket(this, replyPacket);
                }
                catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }
            }
            else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
                try {
                    IQ replyPacket = IQ.createResultIQ(packet);
                    Element responseElement = DocumentHelper.createElement(QName.get(
                            "query", "http://jabber.org/protocol/disco#info"));
                    
                    replyPacket.setChildElement(responseElement);
                    componentManager.sendPacket(this, replyPacket);
                }
                catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }
            }
        }
    }

    private IQ handleIQ(IQ packet) {
        if (IQ.Type.get.equals(packet.getType())) {
            return processGetPacket(packet);

        }
        else if (IQ.Type.set.equals(packet.getType())) {
            return processSetPacket(packet);
        }

        return null;
    }

    private IQ processGetPacket(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        replyPacket.setChildElement("query", "jabber:iq:search");
        replyPacket.setChildElement(probeResult.createCopy());

        return replyPacket;
    }

    private IQ processSetPacket(IQ packet) {
        List<User> users = new ArrayList<User>();
        
        Element incomingForm = packet.getChildElement();
        boolean isDataFormQuery = (incomingForm.element(QName.get("x", "jabber:x:data")) != null);
        
        Hashtable<String, String> searchList = extractSearchQuery(incomingForm);
        Enumeration<String> searchIter = searchList.keys();
        while (searchIter.hasMoreElements()) {
            String field = (String) searchIter.nextElement();
            String query = (String) searchList.get(field);
            
            Iterator foundIter = null;
            if (userManager != null) {
                if (query.length() > 0 && !query.equals("jabber:iq:search")) {
                    foundIter = userManager.findUsers(new HashSet<String>(
                            Arrays.asList((field))), query).iterator();
                }
            }
            else {
                foundIter = findUsers(field, query).iterator();
            }
            
            // Filter out all duplicate users.
            if (foundIter != null) {
                while (foundIter.hasNext()) {
                    User user = (User) foundIter.next();
                    if (!users.contains(user)) {
                        users.add(user);
                    }
                }
            }
        }
        
        if (isDataFormQuery) {
            return replyDataFormResult(users, packet);
        } else {
            return replyNonDataFormResult(users, packet);
        }
    }
    
    /**
     * nick, first, last, email fields have been hardcoded to support Miranda which does not
     * make a query to discover which fields are available to be searched
     */
    private  Hashtable<String, String> extractSearchQuery(Element incomingForm) {
        Hashtable<String, String> searchList = new Hashtable<String, String>();
        Element form = incomingForm.element(QName.get("x", "jabber:x:data"));
        if (form == null) {
            Element name = incomingForm.element("name");
            if (name != null) {
                searchList.put("Name", name.getTextTrim());
            }
            
            Element nick = incomingForm.element("nick");
            if (nick != null) {
                searchList.put("Username", nick.getTextTrim());
            }
            
            Element first = incomingForm.element("first");
            if (first != null) {
                searchList.put("Name", first.getTextTrim());
            }
            
            Element last = incomingForm.element("last");
            if (last != null) {
                searchList.put("Name", last.getTextTrim());
            }
            
            Element email = incomingForm.element("email");
            if (email != null) {
                searchList.put("Email", email.getTextTrim());
            }
        }
        else {
			List<String> searchFields = new ArrayList<String>();
			String search = "";
			
            Iterator fields = form.elementIterator("field");
            while (fields.hasNext()) {
                Element searchField = (Element) fields.next();
				
				String field = searchField.attributeValue("var");
				String value = searchField.element("value").getTextTrim();
				if (field.equals("search")) {
					search = value;
				}
				else if (value.equals("1")) {
					searchFields.add(field);
				}
            }
			
			Iterator iter = searchFields.iterator();
			while (iter.hasNext()) {
				String field = (String) iter.next();
				searchList.put(field, search);
			}
        }
		
        return searchList;
    }
    
    private IQ replyDataFormResult(List users, IQ packet) {
        XDataFormImpl searchResults = new XDataFormImpl(DataForm.TYPE_RESULT);
        
        XFormFieldImpl field = new XFormFieldImpl("jid");
        field.setLabel("JID");
        searchResults.addReportedField(field);

        for (String fieldName : searchFields) {
            field = new XFormFieldImpl(fieldName);
            field.setLabel(fieldName);
            searchResults.addReportedField(field);
        }

        Iterator userIter = users.iterator();
        while (userIter.hasNext()) {
            User user = (User) userIter.next();
            String username = user.getUsername();

            ArrayList<XFormFieldImpl> items = new ArrayList<XFormFieldImpl>();
            
            XFormFieldImpl fieldJID = new XFormFieldImpl("jid");
            fieldJID.addValue(username + "@" + serverName);
            items.add(fieldJID);

            XFormFieldImpl fieldUsername = new XFormFieldImpl("Username");
            fieldUsername.addValue(username);
            items.add(fieldUsername);

            XFormFieldImpl fieldName = new XFormFieldImpl("Name");
            fieldName.addValue(user.getName());
            items.add(fieldName);

            XFormFieldImpl fieldEmail = new XFormFieldImpl("Email");
            fieldEmail.addValue(user.getEmail());
            items.add(fieldEmail);

            searchResults.addItemFields(items);
        }

        Element reply = DocumentHelper.createElement(QName.get("query", "jabber:iq:search"));
        reply.add(searchResults.asXMLElement());

        IQ replyPacket = IQ.createResultIQ(packet);
        replyPacket.setChildElement(reply);
        
        return replyPacket;
    }

    private IQ replyNonDataFormResult(List users, IQ packet) {
        Element replyQuery = DocumentHelper.createElement(QName.get("query", "jabber:iq:search"));
        String serverName = XMPPServer.getInstance().getServerInfo().getName();
        Iterator userIter = users.iterator();
        while (userIter.hasNext()) {
            User user = (User) userIter.next();

            Element item = DocumentHelper.createElement("item");
            Attribute jib = DocumentHelper.createAttribute(item, "jid", user.getUsername() + "@" + serverName);
            item.add(jib);
            Element nick = DocumentHelper.createElement("nick");
            nick.addText(user.getName());
            item.add(nick);
            Element email = DocumentHelper.createElement("email");
            email.addText(user.getEmail());
            item.add(email);
            replyQuery.add(item);
        }
        IQ replyPacket = IQ.createResultIQ(packet);
        replyPacket.setChildElement(replyQuery);
        
        return replyPacket;
    }

    /**
     * Returns the collection of field names that can be used to search for a
     * user. Typical fields are username, name, and email. These values can be
     * used to contruct a data form.
     */
    public Collection<String> getSearchFields() {
        return Arrays.asList("Username", "Name", "Email");
    }

    /**
     * Finds a user using the specified field and query string. For example, a
     * field name of "email" and query of "jsmith@example.com" would search for
     * the user with that email address. Wildcard (*) characters are allowed as
     * part of queries.
     *
     * A possible future improvement would be to have a third parameter that
     * sets the maximum number of users returned and/or the number of users
     * that are searched.
     */
    public Collection<User> findUsers(String field, String query) {
        List<User> foundUsers = new ArrayList<User>();

        if (!getSearchFields().contains(field)) {
            return foundUsers;
        }

        int index = query.indexOf("*");
        if (index == -1) {
            Iterator users = userManager.getUsers().iterator();
            while (users.hasNext()) {
                User user = (User) users.next();
                if (field.equals("Username")) {
                    try {
                        foundUsers.add(userManager.getUser(query));
                        return foundUsers;
                    }
                    catch (UserNotFoundException e) {
                        Log.error("Error getting user", e);
                    }
                }
                else if (field.equals("Name")) {
                    if (query.equalsIgnoreCase(user.getName())) {
                        foundUsers.add(user);
                    }
                }
                else if (field.equals("Email")) {
                    if (user.getEmail() != null) {
                        if (query.equalsIgnoreCase(user.getEmail())) {
                            foundUsers.add(user);
                        }
                    }
                }
            }
        }
        else {
            String prefix = query.substring(0, index);
            Iterator users = userManager.getUsers().iterator();
            while (users.hasNext()) {
                User user = (User) users.next();

                String userInfo = "";
                if (field.equals("Username")) {
                    userInfo = user.getUsername();
                }
                else if (field.equals("Name")) {
                    userInfo = user.getName();
                }
                else if (field.equals("Email")) {
                    userInfo = user.getEmail() == null ? "" : user.getEmail();
                }

                if (index < userInfo.length()) {
                    if (userInfo.substring(0, index).equalsIgnoreCase(prefix)) {
                        foundUsers.add(user);
                    }
                }
            }
        }

        return foundUsers;
    }
}