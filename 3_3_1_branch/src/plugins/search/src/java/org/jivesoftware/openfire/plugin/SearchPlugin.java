/**
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.plugin;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.forms.DataForm;
import org.jivesoftware.openfire.forms.FormField;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.openfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.io.File;
import java.util.*;

/** 
 * Provides support for Jabber Search
 * (<a href="http://www.xmpp.org/extensions/xep-0055.html">XEP-0055</a>).<p>
 *
 * The basic functionality is to query an information repository 
 * regarding the possible search fields, to send a search query, 
 * and to receive search results. This implementation was primarily designed to use
 * <a href="http://www.xmpp.org/extensions/xep-0004.html">Data Forms</a>, but 
 * also supports non-dataform searches.
 * <p/>
 * 
 * @author <a href="mailto:ryan@version2software.com">Ryan Graham</a>
 */
public class SearchPlugin implements Component, Plugin, PropertyEventListener {
    public static final String SERVICENAME = "plugin.search.serviceName";
    public static final String SERVICEENABLED = "plugin.search.serviceEnabled";
    public static final String EXCLUDEDFIELDS = "plugin.search.excludedFields";
   
    private UserManager userManager;
    private ComponentManager componentManager;
    private PluginManager pluginManager;

    private String serviceName;
    private boolean serviceEnabled;
    private Collection<String> exculudedFields;
    
    private static String serverName;

    private TreeMap<String, String> fieldLookup = new TreeMap<String, String>(new CaseInsensitiveComparator());
    private Map<String, String> reverseFieldLookup = new HashMap<String, String>();

    public SearchPlugin() {
        serviceName = JiveGlobals.getProperty(SERVICENAME, "search");
        serviceEnabled = JiveGlobals.getBooleanProperty(SERVICEENABLED, true);
        exculudedFields = StringUtils.stringToCollection(JiveGlobals.getProperty(EXCLUDEDFIELDS, ""));
        
        serverName = XMPPServer.getInstance().getServerInfo().getName();
        userManager = UserManager.getInstance();
               
        // Some clients, such as Miranda, are hard-coded to search specific fields,
        // so we map those fields to the fields that Openfire actually supports.
        fieldLookup.put("jid", "Username");
        fieldLookup.put("username", "Username");
        fieldLookup.put("first", "Name");
        fieldLookup.put("last", "Name");
        fieldLookup.put("nick", "Name");
        fieldLookup.put("name", "Name");
        fieldLookup.put("email", "Email");
    }
    
    public String getName() {
        return pluginManager.getName(this);
    }

    public String getDescription() {
        return pluginManager.getDescription(this);
    }
    
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        pluginManager = manager;
        
        componentManager = ComponentManagerFactory.getComponentManager();
        try {
            componentManager.addComponent(serviceName, this);
        }
        catch (ComponentException e) {
            componentManager.getLog().error(e);
        }
        PropertyEventDispatcher.addListener(this);
    }
    
    public void initialize(JID jid, ComponentManager componentManager) {
    }

    public void start() {
    }

    public void destroyPlugin() {
        PropertyEventDispatcher.removeListener(this);
        pluginManager = null;
        try {
            componentManager.removeComponent(serviceName);
            componentManager = null;
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        serviceName = null;
        userManager = null;
        exculudedFields = null;
        serverName = null;
        fieldLookup = null;
        reverseFieldLookup = null;
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
                    if (replyPacket != null) {
                        componentManager.sendPacket(this, replyPacket);
                    }
                }
                catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }
            }
            else if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                try {
                    IQ replyPacket = IQ.createResultIQ(packet);

                    Element responseElement = replyPacket
                            .setChildElement("query", "http://jabber.org/protocol/disco#info");
                    responseElement.addElement("identity").addAttribute("category", "directory")
                                                          .addAttribute("type", "user")
                                                          .addAttribute("name", "User Search");
                    responseElement.addElement("feature").addAttribute("var", "jabber:iq:search");

                    componentManager.sendPacket(this, replyPacket);
                }
                catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }
            }
            else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
                try {
                    IQ replyPacket = IQ.createResultIQ(packet);
                    replyPacket.setChildElement("query", "http://jabber.org/protocol/disco#items");
                    componentManager.sendPacket(this, replyPacket);
                }
                catch (ComponentException e) {
                    componentManager.getLog().error(e);
                }
            }
        }
    }

    private IQ handleIQ(IQ packet) {
        if (!serviceEnabled) {
            return replyDisabled(packet);
        }
        
        if (IQ.Type.get.equals(packet.getType())) {
            return processGetPacket(packet);
        }
        else if (IQ.Type.set.equals(packet.getType())) {
            return processSetPacket(packet);
        }
        else if (IQ.Type.result.equals(packet.getType()) || IQ.Type.error.equals(packet.getType())) {
            // Ignore
        }
        else {
            // Unknown type was sent so return an error
            IQ reply = new IQ(IQ.Type.error, packet.getID());
            reply.setFrom(packet.getTo());
            reply.setTo(packet.getFrom());
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }

        return null;
    }
    
    private IQ replyDisabled(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        Element reply = replyPacket.setChildElement("query", "jabber:iq:search");
        XDataFormImpl unavailableForm = new XDataFormImpl(DataForm.TYPE_CANCEL);
        unavailableForm.setTitle(LocaleUtils.getLocalizedString("advance.user.search.title", "search"));
        unavailableForm.addInstruction(LocaleUtils.getLocalizedString("search.service_unavailable", "search"));
        reply.add(unavailableForm.asXMLElement());

        return replyPacket;
    }

    private IQ processGetPacket(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        
        Element queryResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:search"));
        
        String instructions = LocaleUtils.getLocalizedString("advance.user.search.details", "search");
        
        // non-data form
        queryResult.addElement("instructions").addText(instructions);
        
        XDataFormImpl searchForm = new XDataFormImpl(DataForm.TYPE_FORM);
        searchForm.setTitle(LocaleUtils.getLocalizedString("advance.user.search.title", "search"));
        searchForm.addInstruction(instructions);
     
        XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
        field.setType(FormField.TYPE_HIDDEN);
        field.addValue("jabber:iq:search");
        searchForm.addField(field);
        
        field = new XFormFieldImpl("search"); 
        field.setType(FormField.TYPE_TEXT_SINGLE);
        field.setLabel(LocaleUtils.getLocalizedString("advance.user.search.search", "search"));
        field.setRequired(true);
        searchForm.addField(field);
        
        for (String searchField : getFilteredSearchFields()) {
            // non-data form
            queryResult.addElement(searchField);

            field = new XFormFieldImpl(searchField);
            field.setType(FormField.TYPE_BOOLEAN);
            field.addValue("1");
            field.setLabel(searchField);
            field.setRequired(false);
            searchForm.addField(field);
        }

        queryResult.add(searchForm.asXMLElement());
        replyPacket.setChildElement(queryResult);

        return replyPacket;
    }

    private IQ processSetPacket(IQ packet) {
        Set<User> users = new HashSet<User>();
        
        Element incomingForm = packet.getChildElement();
        boolean isDataFormQuery = (incomingForm.element(QName.get("x", "jabber:x:data")) != null);
        
        Hashtable<String, String> searchList = extractSearchQuery(incomingForm);
        Enumeration<String> searchIter = searchList.keys();
        while (searchIter.hasMoreElements()) {
            String field = searchIter.nextElement();
            String query = searchList.get(field);
            
            Collection<User> foundUsers = new ArrayList<User>();
            if (userManager != null) {
                if (query.length() > 0 && !query.equals("jabber:iq:search")) {
                    foundUsers.addAll(userManager.findUsers(new HashSet<String>(
                            Arrays.asList((field))), query));
                }
            }
            else {
                foundUsers.addAll(findUsers(field, query));
            }
            
            //occasionally null a User is returned so filter them out
            for (User user : foundUsers) {
                if (user != null) {
                    users.add(user);
                }
            }
        }
        
        if (isDataFormQuery) {
            return replyDataFormResult(users, packet);
        }
        else {
            return replyNonDataFormResult(users, packet);
        }
    }
    
    private Hashtable<String, String> extractSearchQuery(Element incomingForm) {
        Hashtable<String, String> searchList = new Hashtable<String, String>();
        Element form = incomingForm.element(QName.get("x", "jabber:x:data"));
        if (form == null) {
            //since not all clients request which fields are available for searching
            //attempt to match submitted fields with available search fields
            Iterator iter = incomingForm.elementIterator();
            while (iter.hasNext()) {
                Element element = (Element) iter.next();
                String name = element.getName();
                
                if (fieldLookup.containsKey(name)) {
                    //make best effort to map the fields submitted by   
                    //the client to those that Openfire can search
                    reverseFieldLookup.put(fieldLookup.get(name), name);
                    searchList.put(fieldLookup.get(name), element.getText());
                }
            }
        }
        else {
            List<String> searchFields = new ArrayList<String>();
            String search = "";
         
            Iterator fields = form.elementIterator("field");
            while (fields.hasNext()) {
                Element searchField = (Element) fields.next();
            
                String field = searchField.attributeValue("var");
                String value = "";
                if (searchField.element("value") != null) {
                    value = searchField.element("value").getTextTrim();
                }
                if (field.equals("search")) {
                    search = value;
                }
                else if (value.equals("1")) {
                    searchFields.add(field);
                }
            }
         
            for (String field : searchFields) {
                searchList.put(field, search);
            }
        }
      
        return searchList;
    }
    
    private IQ replyDataFormResult(Set<User> users, IQ packet) {
        XDataFormImpl searchResults = new XDataFormImpl(DataForm.TYPE_RESULT);
        
        XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
        field.setType(FormField.TYPE_HIDDEN);
        searchResults.addField(field);
        
        field = new XFormFieldImpl("jid");
        field.setLabel("JID");
        searchResults.addReportedField(field);

        for (String fieldName : getFilteredSearchFields()) {
            field = new XFormFieldImpl(fieldName);
            field.setLabel(fieldName);
            searchResults.addReportedField(field);
        }

        for (User user : users) {
            String username = user.getUsername();

            ArrayList<XFormFieldImpl> items = new ArrayList<XFormFieldImpl>();
            
            XFormFieldImpl fieldJID = new XFormFieldImpl("jid");
            fieldJID.addValue(username + "@" + serverName);
            items.add(fieldJID);

            XFormFieldImpl fieldUsername = new XFormFieldImpl("Username");
            fieldUsername.addValue(username);
            items.add(fieldUsername);

            XFormFieldImpl fieldName = new XFormFieldImpl("Name");
            fieldName.addValue(removeNull(user.getName()));
            items.add(fieldName);

            XFormFieldImpl fieldEmail = new XFormFieldImpl("Email");
            fieldEmail.addValue(removeNull(user.getEmail()));
            items.add(fieldEmail);

            searchResults.addItemFields(items);
        }

        IQ replyPacket = IQ.createResultIQ(packet);
        Element reply = replyPacket.setChildElement("query", "jabber:iq:search");
        reply.add(searchResults.asXMLElement());

        return replyPacket;
    }

    private IQ replyNonDataFormResult(Set<User> users, IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        Element replyQuery = replyPacket.setChildElement("query", "jabber:iq:search");

        for (User user : users) {
            Element item = replyQuery.addElement("item");
            item.addAttribute("jid", user.getUsername() + "@" + serverName);

            //return to the client the same fields that were submitted
            for (String field : reverseFieldLookup.keySet()) {
                if ("Username".equals(field)) {
                    Element element = item.addElement(reverseFieldLookup.get(field));
                    element.addText(user.getUsername());
                }
             
                if ("Name".equals(field)) {
                    Element element = item.addElement(reverseFieldLookup.get(field));
                    element.addText(removeNull(user.getName()));
                }
             
                if ("Email".equals(field)) {
                    Element element = item.addElement(reverseFieldLookup.get(field));
                    element.addText(removeNull(user.getEmail()));
                }
            }
        }

        return replyPacket;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String name) {
        changeServiceName(name);
        JiveGlobals.setProperty(SERVICENAME, name);
    }
    
    public boolean getServiceEnabled() {
        return serviceEnabled;
    }
    
    public void setServiceEnabled(boolean enabled) {
        serviceEnabled = enabled;
        JiveGlobals.setProperty(SERVICEENABLED, enabled ? "true" : "false");
    }
    
    /**
     * Returns the collection of searchable field names that does not include the fields
     * listed in the EXCLUDEDFIELDS property list.
     */
    public Collection<String> getFilteredSearchFields() {
       Collection<String> searchFields;
       
       // See if the installed provider supports searching. If not, workaround
       // by providing our own searching.
       try {
           searchFields = new ArrayList<String>(userManager.getSearchFields());
       }
       catch (UnsupportedOperationException uoe) {
           // Use a SearchPluginUserManager instead.
          searchFields = getSearchPluginUserManagerSearchFields();
       }
       
       searchFields.removeAll(exculudedFields);
       
       return searchFields;
    }
    
    public void setExcludedFields(Collection<String> exculudedFields) {
       this.exculudedFields = exculudedFields;
       JiveGlobals.setProperty(EXCLUDEDFIELDS, StringUtils.collectionToString(exculudedFields));
    }
    
    public void propertySet(String property, Map params) {
        if (property.equals(SERVICEENABLED)) {
            this.serviceEnabled = Boolean.parseBoolean((String)params.get("value"));
        }
        else if (property.equals(SERVICENAME)) {
            changeServiceName((String)params.get("value"));
        }
        else if (property.equals(EXCLUDEDFIELDS)) {
            exculudedFields = StringUtils.stringToCollection(JiveGlobals.getProperty(EXCLUDEDFIELDS, (String)params.get("value")));
        }
    }

    public void propertyDeleted(String property, Map params) {
        if (property.equals(SERVICEENABLED)) {
            this.serviceEnabled = true;
        }
        else if (property.equals(SERVICENAME)) {
            changeServiceName("search");
        }
        else if (property.equals(EXCLUDEDFIELDS)) {
           exculudedFields = new ArrayList<String>();
        }
    }

    public void xmlPropertySet(String property, Map params) {
        // not used
    }

    public void xmlPropertyDeleted(String property, Map params) {
        // not used
    }
    
    private void changeServiceName(String serviceName) {
        if (serviceName == null) {
            throw new NullPointerException("Service name cannot be null");
        }
        
        if (this.serviceName.equals(serviceName)) {
            return;
        }

        // Re-register the service.
        try {
            componentManager.removeComponent(this.serviceName);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        
        try {
            componentManager.addComponent(serviceName, this);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        
        this.serviceName = serviceName;
    }
    
    private class CaseInsensitiveComparator implements Comparator<String> {
        public int compare(String s1, String s2) {
            return s1.compareToIgnoreCase(s2);
        }
    }
    
    private String removeNull(String s) {
        if (s == null) {
            return "";
        }
       
        return s.trim();
    }

    /**
     * Returns the collection of field names that can be used to search for a
     * user. Typical fields are username, name, and email. These values can be
     * used to contruct a data form.
     */
    public Collection<String> getSearchPluginUserManagerSearchFields() {
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

        if (!getSearchPluginUserManagerSearchFields().contains(field)) {
            return foundUsers;
        }

        int index = query.indexOf("*");
        if (index == -1) {
            Collection<User> users = userManager.getUsers();
            for (User user : users) {
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
            Collection<User> users = userManager.getUsers();
            for (User user : users) {
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
