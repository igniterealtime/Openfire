/**
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin;

import java.io.File;
import java.util.*;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
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
 * and to receive search results. This implementation below uses the
 * <a href="http://www.jabber.org/jeps/jep-0004.html">Data Forms</a>.
 * <p/>
 * 
 * @author Ryan Graham 
 */
public class SearchPlugin implements Component, Plugin {

    private UserManager userManager = null;
    private ComponentManager componentManager = null;
    private PluginManager pluginManager = null;

    private static final String SERVICE_NAME = "search";

    private static Element probeResult;

    private Collection<String> searchFields;

    public SearchPlugin() {
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
        } catch (Exception e) {
            componentManager.getLog().error(e);
        }

        if (probeResult == null) {
            probeResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:search"));

            XDataFormImpl searchForm = new XDataFormImpl(DataForm.TYPE_FORM);
            searchForm.setTitle("User Search");
            searchForm.addInstruction("The following fields are available for search. "
                            + "Wildcard (*) characters are allowed as part the of query.");
            
            Iterator iter = searchFields.iterator();
            while (iter.hasNext()) {
                String searchField = (String) iter.next();

                XFormFieldImpl field = new XFormFieldImpl(searchField);
                field.setType(FormField.TYPE_TEXT_SINGLE);
                field.setLabel(initCap(searchField));
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
        } catch (Exception e) {
            componentManager.getLog().error(e);
        }
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
                } catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }

            } else if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                try {
                    IQ replyPacket = IQ.createResultIQ(packet);

                    Element responseElement = DocumentHelper.createElement(QName.get(
                            "query", "http://jabber.org/protocol/disco#info"));
                    responseElement.addElement("identity").addAttribute("category", "search");
                    responseElement.addElement("feature").addAttribute("var", "jabber:iq:search");
                    replyPacket.setChildElement(responseElement);

                    componentManager.sendPacket(this, replyPacket);
                } catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }
            }
        }
    }

    private IQ handleIQ(IQ packet) {
        if (IQ.Type.get.equals(packet.getType())) {
            return processGetPacket(packet);

        } else if (IQ.Type.set.equals(packet.getType())) {
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
        XDataFormImpl searchResults = new XDataFormImpl(DataForm.TYPE_RESULT);
        
        for (String fieldName: searchFields) {
            XFormFieldImpl field = new XFormFieldImpl(fieldName);
            field.setLabel(initCap(fieldName));
            searchResults.addReportedField(field);
        }

        List<User> users = new ArrayList<User>();

        Element incomingForm = packet.getChildElement();
        Element form = incomingForm.element(QName.get("x", "jabber:x:data"));
        Iterator fields = form.elementIterator("field");
        while (fields.hasNext()) {
            Element searchField = (Element) fields.next();

            Iterator iter = searchField.elementIterator("value");
            while (iter.hasNext()) {
                Element queryField = (Element) iter.next();

                Iterator foundIter = null;
                if (userManager != null) {                    
                    String query = queryField.getTextTrim();
                    //psi returns every field even if it is empty
                    if (query.length() > 0) {
                        foundIter = userManager.findUsers(new HashSet<String>(
                                Arrays.asList(searchField.attributeValue("var"))), query).iterator();
                    }
                }
                else {
                    foundIter = findUsers(searchField.attributeValue("var"),
                        queryField.getTextTrim()).iterator();
                }
                
                // filter out all duplicate users
                if (foundIter != null) {
                    while (foundIter.hasNext()) {
                        User user = (User) foundIter.next();
                        if (!users.contains(user)) {
                            users.add(user);
                        }
                    }
                }
            }
        }

        Iterator userIter = users.iterator();
        while (userIter.hasNext()) {
            User user = (User) userIter.next();

            ArrayList<XFormFieldImpl> items = new ArrayList<XFormFieldImpl>();

            XFormFieldImpl fieldUsername = new XFormFieldImpl("Username");
            fieldUsername.addValue(user.getUsername());
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

    private static String initCap(String s) {
        if (s == null) {
            return null;
        }

        StringTokenizer st = new StringTokenizer(s);
        StringBuffer sb = new StringBuffer();
        while (st.hasMoreTokens()) {
            String t = st.nextToken();
            String first = t.substring(0, 1).toUpperCase();
            String rest = t.substring(1).toLowerCase();
            sb.append(first + rest);
        }

        return sb.toString().trim();
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
                    } catch (UserNotFoundException e) {
                        Log.error("Error getting user", e);
                    }

                } else if (field.equals("Name")) {
                    if (query.equalsIgnoreCase(user.getName())) {
                        foundUsers.add(user);
                    }

                } else if (field.equals("Email")) {
                    if (user.getEmail() != null) {
                        if (query.equalsIgnoreCase(user.getEmail())) {
                            foundUsers.add(user);
                        }
                    }
                }
            }
        } else {
            String prefix = query.substring(0, index);
            Iterator users = userManager.getUsers().iterator();
            while (users.hasNext()) {
                User user = (User) users.next();

                String userInfo = "";
                if (field.equals("Username")) {
                    userInfo = user.getUsername();
                } else if (field.equals("Name")) {
                    userInfo = user.getName();
                } else if (field.equals("Email")) {
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
