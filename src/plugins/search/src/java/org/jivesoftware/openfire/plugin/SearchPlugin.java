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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.IQDiscoItemsHandler;
import org.jivesoftware.openfire.forms.DataForm;
import org.jivesoftware.openfire.forms.FormField;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.openfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.openfire.resultsetmanager.ResultSet;
import org.jivesoftware.openfire.resultsetmanager.ResultSetImpl;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.*;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

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
	public static final String NAMESPACE_JABBER_IQ_SEARCH = "jabber:iq:search";
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

	/**
	 * A list of field names that are valid in jabber:iq:search
	 */
	public final static Collection<String> validSearchRequestFields = new ArrayList<String>();
	static {
		validSearchRequestFields.add("first");
		validSearchRequestFields.add("last");
		validSearchRequestFields.add("nick");
		validSearchRequestFields.add("email");
		validSearchRequestFields.add("x"); // extended info

		// result set management (XEP-0059)
		validSearchRequestFields.add("set");
	}

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
    
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xmpp.component.Component#getName()
	 */
    public String getName() {
        return pluginManager.getName(this);
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xmpp.component.Component#getDescription()
	 */
    public String getDescription() {
        return pluginManager.getDescription(this);
    }
    
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.openfire.container.Plugin#initializePlugin(org.jivesoftware.openfire.container.PluginManager,
	 *      java.io.File)
	 */
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
    
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xmpp.component.Component#initialize(org.xmpp.packet.JID,
	 *      org.xmpp.component.ComponentManager)
	 */
    public void initialize(JID jid, ComponentManager componentManager) {
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xmpp.component.Component#start()
	 */
    public void start() {
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.openfire.container.Plugin#destroyPlugin()
	 */
    public void destroyPlugin() {
        PropertyEventDispatcher.removeListener(this);
        pluginManager = null;
        try {
            componentManager.removeComponent(serviceName);
            componentManager = null;
        }
        catch (Exception e) {
            if (componentManager != null) {
                componentManager.getLog().error(e);
            }
        }
        serviceName = null;
        userManager = null;
        exculudedFields = null;
        serverName = null;
        fieldLookup = null;
        reverseFieldLookup = null;
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xmpp.component.Component#shutdown()
	 */
    public void shutdown() {
    }
    
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xmpp.component.Component#processPacket(org.xmpp.packet.Packet)
	 */
    public void processPacket(Packet p) {
        if (!(p instanceof IQ)) {
            return;
        }
        final IQ packet = (IQ) p;

        if (packet.getType().equals(IQ.Type.error)
                || packet.getType().equals(IQ.Type.result)) {
            return;
        }

        // Packet p is an IQ stanza of type GET or SET. Therefor, it _must_ be
        // replied to.
        final IQ replyPacket = handleIQRequest(packet);

        try {
            componentManager.sendPacket(this, replyPacket);
        } catch (ComponentException e) {
            componentManager.getLog().error(e);
        }

    }

    /**
     * Handles IQ requests. This method throws an IllegalArgumentException if an
     * IQ stanza is supplied that is not a request (if the stanza is not of type
     * 'get' or 'set'). This method will either throw an Exception, or return a
     * non-null IQ stanza of type 'error' or 'result', as XMPP Core specifies
     * that <strong>all</strong> IQ request stanza's (type 'get' or 'set') MUST
     * be replied to.
     *
     * @param iq
     *            The IQ stanza that forms the request.
     * @return The response to the request.
     */
    private IQ handleIQRequest(IQ iq) {
        final IQ replyPacket; // 'final' to ensure that it is set.

        if (iq == null) {
            throw new IllegalArgumentException("Argument 'iq' cannot be null.");
        }

        final IQ.Type type = iq.getType();
        if (type != IQ.Type.get && type != IQ.Type.set) {
            throw new IllegalArgumentException(
                    "Argument 'iq' must be of type 'get' or 'set'");
        }

        final Element childElement = iq.getChildElement();
        if (childElement == null) {
            replyPacket = IQ.createResultIQ(iq);
            replyPacket
                    .setError(new PacketError(
                            Condition.bad_request,
                            org.xmpp.packet.PacketError.Type.modify,
                            "IQ stanzas of type 'get' and 'set' MUST contain one and only one child element (RFC 3920 section 9.2.3)."));
            return replyPacket;
        }

        final String namespace = childElement.getNamespaceURI();
        if (namespace == null) {
            replyPacket = IQ.createResultIQ(iq);
            replyPacket.setError(Condition.feature_not_implemented);
            return replyPacket;
        }

        if (namespace.equals(NAMESPACE_JABBER_IQ_SEARCH)) {
            replyPacket = handleSearchRequest(iq);
        } else if (namespace.equals(IQDiscoInfoHandler.NAMESPACE_DISCO_INFO)) {
            replyPacket = handleDiscoInfo(iq);
        } else if (namespace.equals(IQDiscoItemsHandler.NAMESPACE_DISCO_ITEMS)) {
            replyPacket = IQ.createResultIQ(iq);
            replyPacket.setChildElement("query",
                    IQDiscoItemsHandler.NAMESPACE_DISCO_ITEMS);
        } else {
            // don't known what to do with this.
            replyPacket = IQ.createResultIQ(iq);
            replyPacket.setError(Condition.feature_not_implemented);
        }

        return replyPacket;
    }

    /**
     * Creates a response specific to the search plugin to Disco#Info requests.
     *
     * @param iq
     *            The IQ stanza that contains the request.
     * @return An IQ stanza, formulated as an answer to the received request.
     */
    private static IQ handleDiscoInfo(IQ iq) {
        if (iq == null) {
            throw new IllegalArgumentException("Argument 'iq' cannot be null.");
        }

        if (!iq.getChildElement().getNamespaceURI().equals(
                IQDiscoInfoHandler.NAMESPACE_DISCO_INFO)
                || iq.getType() != Type.get) {
            throw new IllegalArgumentException(
                    "This is not a valid disco#info request.");
        }

        final IQ replyPacket = IQ.createResultIQ(iq);

        final Element responseElement = replyPacket.setChildElement("query",
                IQDiscoInfoHandler.NAMESPACE_DISCO_INFO);
        responseElement.addElement("identity").addAttribute("category",
                "directory").addAttribute("type", "user").addAttribute("name",
                "User Search");
        responseElement.addElement("feature").addAttribute("var",
                NAMESPACE_JABBER_IQ_SEARCH);
        responseElement.addElement("feature").addAttribute("var",
                IQDiscoInfoHandler.NAMESPACE_DISCO_INFO);
        responseElement.addElement("feature").addAttribute("var",
                ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT);

        return replyPacket;
    }

    private IQ handleSearchRequest(IQ packet) {
        if (!serviceEnabled) {
            return replyDisabled(packet);
        }

        switch (packet.getType()) {
        case get:
            return processGetPacket(packet);

        case set:
            return processSetPacket(packet);

        default:
            // we can safely ignore 'error' and 'result' typed iq stanzas.
            return null;
        }
    }

    /**
     * Constructs a IQ result stanza, based on the request stanza that is
     * provided as an argument. The stanza tells the recipient that this service
     * is currently unavailable.
     *
     * @param packet
     *            The request IQ stanza to which a result will be returned.
     * @return A result stanza, telling the user that this service is
     *         unavailable.
     */
    private static IQ replyDisabled(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        Element reply = replyPacket.setChildElement("query",
                NAMESPACE_JABBER_IQ_SEARCH);
        XDataFormImpl unavailableForm = new XDataFormImpl(DataForm.TYPE_CANCEL);
        unavailableForm.setTitle(LocaleUtils.getLocalizedString(
                "advance.user.search.title", "search"));
        unavailableForm.addInstruction(LocaleUtils.getLocalizedString(
                "search.service_unavailable", "search"));
        reply.add(unavailableForm.asXMLElement());

        return replyPacket;
    }

    /**
     * Processes an IQ stanza of type 'get', which in the context of 'Jabber
     * Search' is a request for available search fields.
     *
     * @param packet
     *            An IQ stanza of type 'get'
     * @return A result IQ stanza that contains the possbile search fields.
     */
    private IQ processGetPacket(IQ packet) {
        if (!packet.getType().equals(IQ.Type.get)) {
            throw new IllegalArgumentException(
                    "This method only accepts 'get' typed IQ stanzas as an argument.");
        }
        IQ replyPacket = IQ.createResultIQ(packet);

        Element queryResult = DocumentHelper.createElement(QName.get("query",
                NAMESPACE_JABBER_IQ_SEARCH));

        String instructions = LocaleUtils.getLocalizedString(
                "advance.user.search.details", "search");

        // non-data form
        queryResult.addElement("instructions").addText(instructions);
        queryResult.addElement("first");
        queryResult.addElement("last");
        queryResult.addElement("nick");
        queryResult.addElement("email");

        XDataFormImpl searchForm = new XDataFormImpl(DataForm.TYPE_FORM);
        searchForm.setTitle(LocaleUtils.getLocalizedString(
                "advance.user.search.title", "search"));
        searchForm.addInstruction(instructions);

        XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
        field.setType(FormField.TYPE_HIDDEN);
        field.addValue(NAMESPACE_JABBER_IQ_SEARCH);
        searchForm.addField(field);

        field = new XFormFieldImpl("search");
        field.setType(FormField.TYPE_TEXT_SINGLE);
        field.setLabel(LocaleUtils.getLocalizedString(
                "advance.user.search.search", "search"));
        field.setRequired(true);
        searchForm.addField(field);

        for (String searchField : getFilteredSearchFields()) {
            field = new XFormFieldImpl(searchField);
            field.setType(FormField.TYPE_BOOLEAN);
            field.addValue("1");
            field.setLabel(LocaleUtils.getLocalizedString(
                "advance.user.search." + searchField.toLowerCase(), "search"));
            field.setRequired(false);
            searchForm.addField(field);
        }

        queryResult.add(searchForm.asXMLElement());
        replyPacket.setChildElement(queryResult);

        return replyPacket;
    }

    /**
     * Processes an IQ stanza of type 'set', which in the context of 'Jabber
     * Search' is a search request.
     *
     * @param packet
     *            An IQ stanza of type 'get'
     * @return A result IQ stanza that contains the possbile search fields.
     */
    private IQ processSetPacket(IQ packet) {
        if (!packet.getType().equals(IQ.Type.set)) {
            throw new IllegalArgumentException(
                    "This method only accepts 'set' typed IQ stanzas as an argument.");
        }

        final IQ resultIQ;

        // check if the request complies to the XEP-0055 standards
        if (!isValidSearchRequest(packet)) {
            resultIQ = IQ.createResultIQ(packet);
            resultIQ.setError(Condition.bad_request);
            return resultIQ;
        }

        final Element incomingForm = packet.getChildElement();
        final boolean isDataFormQuery = (incomingForm.element(QName.get("x",
                "jabber:x:data")) != null);
        final Set<User> searchResults = performSearch(incomingForm);

        final Element rsmElement = incomingForm.element(QName.get("set",
                ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT));

        final boolean applyRSM = rsmElement != null && !searchResults.isEmpty();

        if (applyRSM) {
            // apply RSM
            final List<User> rsmResults;
            final ResultSet<User> rs = new ResultSetImpl<User>(searchResults);
            try {
                rsmResults = rs.applyRSMDirectives(rsmElement);
            } catch (NullPointerException e) {
                final IQ itemNotFound = IQ.createResultIQ(packet);
                itemNotFound.setError(Condition.item_not_found);
                return itemNotFound;
            }
            if (isDataFormQuery) {
                resultIQ = replyDataFormResult(rsmResults, packet);
            } else {
                resultIQ = replyNonDataFormResult(rsmResults, packet);
            }

            // add the additional 'set' element.
            final Element set = rs.generateSetElementFromResults(rsmResults);
            resultIQ.getChildElement().add(set);

        } else {
            // don't apply RSM
            if (isDataFormQuery) {
                resultIQ = replyDataFormResult(searchResults, packet);
            } else {
                resultIQ = replyNonDataFormResult(searchResults, packet);
            }
        }

        return resultIQ;
    }

    /**
     * This method checks if the search request that was received is a valid
     * JABBER:IQ:SEARCH request. In other words, it checks if the search request
     * is spec compliant (XEP-0055). It does this by checking:
     * <ul>
     * <li>if the IQ stanza is of type 'set';</li>
     * <li>if a child element identified by the jabber:iq:search namespace is
     * supplied;</li>
     * <li>if the stanza child element is has valid children itself.</li>
     * </ul>
     *
     * @param iq
     *            The IQ object that should include a jabber:iq:search request.
     * @return ''true'' if the supplied IQ stanza is a spec compliant search
     *         request, ''false'' otherwise.
     */
    public static boolean isValidSearchRequest(IQ iq) {

        if (iq == null) {
            throw new IllegalArgumentException("Argument 'iq' cannot be null.");
        }

        if (iq.getType() != IQ.Type.set) {
            return false;
        }

        final Element childElement = iq.getChildElement();
        if (childElement == null) {
            return false;
        }

        if (!childElement.getNamespaceURI().equals(NAMESPACE_JABBER_IQ_SEARCH)) {
            return false;
        }

        if (!childElement.getName().equals("query")) {
            return false;
        }

        final List<Element> fields = childElement.elements();
        if (fields.size() == 0) {
            return false;
        }

        for (Element element : fields) {
            final String name = element.getName();
            if (!validSearchRequestFields.contains(name)) {
                return false;
            }

            // TODO: check dataform validity.
            // if (name.equals("x") && !isValidDataForm(element))
            // {
            // return false;
            // }

            if (name.equals("set") && !ResultSet.isValidRSMRequest(element)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Performs a search based on form data, and returns the search results.
     *
     * @param incomingForm
     *            The form containing the search data
     * @return A set of users that matches the search criteria.
     */
    private Set<User> performSearch(Element incomingForm) {
        Set<User> users = new HashSet<User>();

        Hashtable<String, String> searchList = extractSearchQuery(incomingForm);

        for (Entry<String, String> entry : searchList.entrySet()) {
            String field = entry.getKey();
            String query = entry.getValue();

            Collection<User> foundUsers = new ArrayList<User>();
            if (userManager != null) {
                if (query.length() > 0
                        && !query.equals(NAMESPACE_JABBER_IQ_SEARCH)) {
                    foundUsers
                            .addAll(userManager.findUsers(new HashSet<String>(
                                    Arrays.asList((field))), query));
                }
            } else {
                foundUsers.addAll(findUsers(field, query));
            }

            // occasionally a null User is returned so filter them out
            for (User user : foundUsers) {
                if (user != null) {
                    users.add(user);
                }
            }
        }
        return users;
    }

    /**
     * This utilty method extracts the search query from the request. A query is
     * defined as a set of key->value pairs, where the key denotes a search
     * field, and the value contains the value that was filled out by the user
     * for that field.
     *
     * The query can be specified in one of two ways. The first way is a query
     * is formed is by filling out any of the the standard search fields. The
     * other search method makes use of extended data forms. Search queries that
     * are supplied to this {@link #extractSearchQuery(Element)} that make use
     * of this last method get forwarded to
     * {@link #extractExtendedSearchQuery(Element)}.
     *
     * @param incomingForm
     *            The form from which to extract the query
     * @return The search query for a particular user search request.
     */
    private Hashtable<String, String> extractSearchQuery(Element incomingForm) {
        if (incomingForm.element(QName.get("x", "jabber:x:data")) != null) {
            // forward the request.
            return extractExtendedSearchQuery(incomingForm);
        }

        final Hashtable<String, String> searchList = new Hashtable<String, String>();

        // since not all clients request which fields are available for
        // searching attempt to match submitted fields with available search
        // fields
        Iterator<Element> iter = incomingForm.elementIterator();
        while (iter.hasNext()) {
            Element element = iter.next();
            String name = element.getName();

            if (fieldLookup.containsKey(name)) {
                // make best effort to map the fields submitted by
                // the client to those that Openfire can search
                reverseFieldLookup.put(fieldLookup.get(name), name);
                searchList.put(fieldLookup.get(name), element.getText());
            }
        }

        return searchList;
    }

    /**
     * Extracts a search query from a data form that makes use of data forms to
     * specify the search request. This 'extended' way of constructing a search
     * request is documented in XEP-0055, chapter 3.
     *
     * @param incomingForm
     *            The form from which to extract the query
     * @return The search query for a particular user search request.
     * @see #extractSearchQuery(Element)
     */
    private Hashtable<String, String> extractExtendedSearchQuery(
            Element incomingForm) {
        final Element dataform = incomingForm.element(QName.get("x",
                "jabber:x:data"));

        Hashtable<String, String> searchList = new Hashtable<String, String>();
        List<String> searchFields = new ArrayList<String>();
        String search = "";

        Iterator<Element> fields = dataform.elementIterator("field");
        while (fields.hasNext()) {
            Element searchField = fields.next();

            String field = searchField.attributeValue("var");
            String value = "";
            if (searchField.element("value") != null) {
                value = searchField.element("value").getTextTrim();
            }
            if (field.equals("search")) {
                search = value;
            } else if (value.equals("1")) {
                searchFields.add(field);
            }
        }

        for (String field : searchFields) {
            searchList.put(field, search);
        }

        return searchList;
    }

    /**
     * Constructs a query that is returned as an IQ packet that contains the search results.
     *
     * @param users set of users that will be used to construct the search results
     * @param packet the IQ packet sent by the client
     * @return the iq packet that contains the search results
     */
    private IQ replyDataFormResult(Collection<User> users, IQ packet) {
        XDataFormImpl searchResults = new XDataFormImpl(DataForm.TYPE_RESULT);

        XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
        field.setType(FormField.TYPE_HIDDEN);
        searchResults.addField(field);

        field = new XFormFieldImpl("jid");
        field.setLabel("JID");
        searchResults.addReportedField(field);

        for (String fieldName : getFilteredSearchFields()) {
            field = new XFormFieldImpl(fieldName);
            field.setLabel(LocaleUtils.getLocalizedString(
                "advance.user.search." + fieldName.toLowerCase(), "search"));
            searchResults.addReportedField(field);
        }

        for (User user : users) {
            String username = JID.unescapeNode(user.getUsername());

            ArrayList<XFormFieldImpl> items = new ArrayList<XFormFieldImpl>();

            XFormFieldImpl fieldJID = new XFormFieldImpl("jid");
            fieldJID.addValue(username + "@" + serverName);
            items.add(fieldJID);

            XFormFieldImpl fieldUsername = new XFormFieldImpl(LocaleUtils.getLocalizedString("advance.user.search.username", "search"));
            fieldUsername.addValue(username);
            items.add(fieldUsername);

            XFormFieldImpl fieldName = new XFormFieldImpl(LocaleUtils.getLocalizedString("advance.user.search.name", "search"));
            fieldName.addValue(removeNull(user.getName()));
            items.add(fieldName);

            XFormFieldImpl fieldEmail = new XFormFieldImpl(LocaleUtils.getLocalizedString("advance.user.search.email", "search"));
            fieldEmail.addValue(removeNull(user.getEmail()));
            items.add(fieldEmail);

            searchResults.addItemFields(items);
        }

        IQ replyPacket = IQ.createResultIQ(packet);
        Element reply = replyPacket.setChildElement("query",
                NAMESPACE_JABBER_IQ_SEARCH);
        reply.add(searchResults.asXMLElement());

        return replyPacket;
    }

    /**
     * Constructs a query that is returned as an IQ packet that contains the search results.
     *
     * @param users set of users that will be used to construct the search results
     * @param packet the IQ packet sent by the client
     * @return the iq packet that contains the search results
     */
    private IQ replyNonDataFormResult(Collection<User> users, IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        Element replyQuery = replyPacket.setChildElement("query",
                NAMESPACE_JABBER_IQ_SEARCH);

        for (User user : users) {
            Element item = replyQuery.addElement("item");
            String username = JID.unescapeNode(user.getUsername());
            item.addAttribute("jid", username + "@" + serverName);

            // return to the client the same fields that were submitted
            for (String field : reverseFieldLookup.keySet()) {
                if ("Username".equals(field)) {
                    Element element = item.addElement(reverseFieldLookup
                            .get(field));
                    element.addText(username);
                }

                if ("Name".equals(field)) {
                    Element element = item.addElement(reverseFieldLookup
                            .get(field));
                    element.addText(removeNull(user.getName()));
                }

                if ("Email".equals(field)) {
                    Element element = item.addElement(reverseFieldLookup
                            .get(field));
                    element.addText(removeNull(user.getEmail()));
                }
            }
        }

        return replyPacket;
    }

    /**
     * Returns the service name of this component, which is "search" by default.
     *
     * @return the service name of this component.
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * Sets the service name of this component, which is "search" by default. If the name
     * is different than the existing name the plugin will remove itself from the ComponentManager
     * and then add itself back using the new name.
     *
     * @param name the service name of this component.
     */
    public void setServiceName(String name) {
        changeServiceName(name);
        JiveGlobals.setProperty(SERVICENAME, name);
    }
    
    /**
     * Checks if the search service is enabled.
     * 
	 * @return true if search service is enabled.
    */
    public boolean getServiceEnabled() {
        return serviceEnabled;
    }
    
   /**
    * Enables or disables the search service. When disabled, when a client tries 
    * to do a search they will receive an XForm informing that the service is
    * unavailable.
    *
    * @param enabled true if group permission checking should be disabled.
    */
    public void setServiceEnabled(boolean enabled) {
        serviceEnabled = enabled;
        JiveGlobals.setProperty(SERVICEENABLED, enabled ? "true" : "false");
    }
    
    /**
     * Returns the collection of searchable field names that does not include the fields
     * listed in the EXCLUDEDFIELDS property list.
     *
     * @return  collection of searchable field names.
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
    
    /**
     * Restricts which fields can be searched on and shown to clients. This can be used
     * in the case of preventing users email addresses from being revealed as part of
     * the search results. 
     *
     * @param exculudedFields fields that can not be searched on or shown to the client
     */
    public void setExcludedFields(Collection<String> exculudedFields) {
       this.exculudedFields = exculudedFields;
       JiveGlobals.setProperty(EXCLUDEDFIELDS, StringUtils.collectionToString(exculudedFields));
    }
    
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.util.PropertyEventListener#propertySet(java.lang.String,
	 *      java.util.Map)
	 */
	public void propertySet(String property, Map<String, Object> params) {
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.util.PropertyEventListener#propertyDeleted(java.lang.String,
	 *      java.util.Map)
	 */
	public void propertyDeleted(String property, Map<String, Object> params) {
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.util.PropertyEventListener#xmlPropertySet(java.lang.String,
	 *      java.util.Map)
	 */
	public void xmlPropertySet(String property, Map<String, Object> params) {
        // not used
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.util.PropertyEventListener#xmlPropertyDeleted(java.lang.String,
	 *      java.util.Map)
	 */
	public void xmlPropertyDeleted(String property, Map<String, Object> params) {
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
    
	/**
	 * Comparator that compares String objects, ignoring capitalization.
	 */
    private class CaseInsensitiveComparator implements Comparator<String> {
        public int compare(String s1, String s2) {
            return s1.compareToIgnoreCase(s2);
        }
    }
    
	/**
	 * Returns the trimmed argument, or an empty String object of null was
	 * supplied as an argument.
	 * 
	 * @param s
	 *            The String to be trimmed.
	 * @return String object that does not start or end with whitespace
	 *         characters.
	 */
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
     *
     * @return the collection of field names that can be used to search.
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
