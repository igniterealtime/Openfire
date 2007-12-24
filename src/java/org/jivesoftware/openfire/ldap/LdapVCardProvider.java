/**
 * $Revision: 1217 $
 * $Date: 2005-04-11 14:11:06 -0700 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.ldap;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.openfire.vcard.VCardProvider;
import org.jivesoftware.openfire.vcard.DefaultVCardProvider;
import org.xmpp.packet.JID;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.util.*;

/**
 * Read-only LDAP provider for vCards.Configuration consists of adding a provider:<p/>
 *
 * <pre>
 * &lt;provider&gt;
 *   &lt;vcard&gt;
 *  	&lt;className&gt;org.jivesoftware.openfire.ldap.LdapVCardProvider&lt;/className&gt;
 *    &lt;/vcard&gt;
 * &lt;/provider&gt;
 * </pre><p/>
 *
 * and an xml vcard-mapping to openfire.xml.<p/>
 *
 * The vcard attributes can be configured by adding an <code>attrs="attr1,attr2"</code>
 * attribute to the vcard elements.<p/>
 *
 * Arbitrary text can be used for the element values as well as <code>MessageFormat</code>
 * style placeholders for the ldap attributes. For example, if you wanted to map the LDAP
 * attribute <code>displayName</code> to the vcard element <code>FN</code>, the xml
 * nippet would be:<br><pre>&lt;FN attrs=&quot;displayName&quot;&gt;{0}&lt;/FN&gt;</pre><p/>
 *
 * The vCard XML must be escaped in CDATA and must also be well formed. It is the exact
 * XML this provider will send to a client after after stripping <code>attr</code> attributes
 * and populating the placeholders with the data retrieved from LDAP. This system should
 * be flexible enough to handle any client's vCard format. An example mapping follows.<br>
 * <pre>
 *    &lt;ldap&gt;
 *      &lt;vcard-mapping&gt;
 *        &lt;![CDATA[
 *    		&lt;vCard xmlns='vcard-temp'&gt;
 *    			&lt;FN attrs=&quot;displayName&quot;&gt;{0}&lt;/FN&gt;
 *    			&lt;NICKNAME attrs=&quot;uid&quot;&gt;{0}&lt;/NICKNAME&gt;
 *    			&lt;BDAY attrs=&quot;dob&quot;&gt;{0}&lt;/BDAY&gt;
 *    			&lt;ADR&gt;
 *    				&lt;HOME/&gt;
 *    				&lt;EXTADR&gt;Ste 500&lt;/EXTADR&gt;
 *    				&lt;STREET&gt;317 SW Alder St&lt;/STREET&gt;
 *    				&lt;LOCALITY&gt;Portland&lt;/LOCALITY&gt;
 *    				&lt;REGION&gt;Oregon&lt;/REGION&gt;
 *    				&lt;PCODE&gt;97204&lt;/PCODE&gt;
 *    				&lt;CTRY&gt;USA&lt;/CTRY&gt;
 *    			&lt;/ADR&gt;
 *    			&lt;TEL&gt;
 *    				&lt;HOME/&gt;
 *    				&lt;VOICE/&gt;
 *    				&lt;NUMBER attrs=&quot;telephoneNumber&quot;&gt;{0}&lt;/NUMBER&gt;
 *    			&lt;/TEL&gt;
 *    			&lt;EMAIL&gt;
 *    				&lt;INTERNET/&gt;
 *    				&lt;USERID attrs=&quot;mail&quot;&gt;{0}&lt;/USERID&gt;
 *    			&lt;/EMAIL&gt;
 *    			&lt;TITLE attrs=&quot;title&quot;&gt;{0}&lt;/TITLE&gt;
 *    			&lt;ROLE attrs=&quot;&quot;&gt;{0}&lt;/ROLE&gt;
 *    			&lt;ORG&gt;
 *    				&lt;ORGNAME attrs=&quot;o&quot;&gt;{0}&lt;/ORGNAME&gt;
 *    				&lt;ORGUNIT attrs=&quot;&quot;&gt;{0}&lt;/ORGUNIT&gt;
 *    			&lt;/ORG&gt;
 *    			&lt;URL attrs=&quot;labeledURI&quot;&gt;{0}&lt;/URL&gt;
 *    			&lt;DESC attrs=&quot;uidNumber,homeDirectory,loginShell&quot;&gt;
 *    				uid: {0} home: {1} shell: {2}
 *    			&lt;/DESC&gt;
 *    		&lt;/vCard&gt;
 *        ]]&gt;
 *      &lt;/vcard-mapping&gt;
 *    &lt;/ldap&gt;
 * </pre><p>
 * <p/>
 * An easy way to get the vcard format your client needs, assuming you've been
 * using the database store, is to do a <code>SELECT value FROM jivevcard WHERE
 * username='some_user'</code> in your favorite sql querier and paste the result
 * into the <code>vcard-mapping</code> (don't forget the CDATA).
 *
 * @author rkelly
 */
public class LdapVCardProvider implements VCardProvider, PropertyEventListener {

    private LdapManager manager;
    private VCardTemplate template;
    private Boolean dbStorageEnabled = false;

    /**
     * The default vCard provider is used to handle the vCard in the database.
     *
     * This is used/created only if we are storing avatars in the database.
     */
    private DefaultVCardProvider defaultProvider = null;

    public LdapVCardProvider() {
        manager = LdapManager.getInstance();
        initTemplate();
        // Listen to property events so that the template is always up to date
        PropertyEventDispatcher.addListener(this);
        // If avatars will be loaded from the database, load the DefaultVCardProvider
        if (JiveGlobals.getBooleanProperty("ldap.avatarDBStorage", false)) {
            defaultProvider = new DefaultVCardProvider();
            dbStorageEnabled = true;
        }
    }

    /**
     * Initializes the VCard template as set by the administrator.
     */
    private void initTemplate() {
        String property = JiveGlobals.getXMLProperty("ldap.vcard-mapping");
        Log.debug("LdapVCardProvider: Found vcard mapping: '" + property);
        try {
            // Remove CDATA wrapping element
            if (property.startsWith("<![CDATA[")) {
                property = property.substring(9, property.length()-3);
            }
            Document document = DocumentHelper.parseText(property);
            template = new VCardTemplate(document);
        }
        catch (Exception e) {
            Log.error("Error loading vcard mapping: " + e.getMessage());
        }

        Log.debug("LdapVCardProvider: attributes size==" + template.getAttributes().length);
    }

    /**
     * Creates a mapping of requested LDAP attributes to their values for the given user.
     *
     * @param username User we are looking up in LDAP.
     * @return Map of LDAP attribute to setting.
     */
    private Map<String, String> getLdapAttributes(String username) {
        // Un-escape username
        username = JID.unescapeNode(username);
        Map<String, String> map = new HashMap<String, String>();

        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);

            ctx = manager.getContext(manager.getUsersBaseDN(username));
            Attributes attrs = ctx.getAttributes(userDN, template.getAttributes());

            for (String attribute : template.getAttributes()) {
                javax.naming.directory.Attribute attr = attrs.get(attribute);
                String value;
                if (attr == null) {
                    Log.debug("LdapVCardProvider: No ldap value found for attribute '" + attribute + "'");
                    value = "";
                }
                else {
                    Object ob = attrs.get(attribute).get();
                    if(ob instanceof String) {
                        value = (String)ob;
                    } else {
                        value = Base64.encodeBytes((byte[])ob);
                    }
                }
                Log.debug("LdapVCardProvider: Ldap attribute '" + attribute + "'=>'" + value + "'");
                map.put(attribute, value);
            }
            return map;
        }
        catch (Exception e) {
            Log.error(e);
            return Collections.emptyMap();
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception e) {
                // Ignore.
            }
        }
    }

    /**
     * Loads the avatar from LDAP, based off the vcard template.
     *
     * If enabled, will replace a blank PHOTO element with one from a DB stored vcard.
     *
     * @param username User we are loading the vcard for.
     * @return The loaded vcard element, or null if none found.
     */
    public Element loadVCard(String username) {
        // Un-escape username.
        username = JID.unescapeNode(username);
        Map<String, String> map = getLdapAttributes(username);
        Log.debug("LdapVCardProvider: Getting mapped vcard for " + username);
        Element vcard = new VCard(template).getVCard(map);
        // If we have a vcard from ldap, but it doesn't have an avatar filled in, then we
        // may fill it with a locally stored vcard element.
        if (dbStorageEnabled && vcard != null && (vcard.element("PHOTO") == null || vcard.element("PHOTO").element("BINVAL") == null || vcard.element("PHOTO").element("BINVAL").getText().matches("\\s*"))) {
            Element avatarElement = loadAvatarFromDatabase(username);
            if (avatarElement != null) {
                Log.debug("LdapVCardProvider: Adding avatar element from local storage");
                Element currentElement = vcard.element("PHOTO");
                if (currentElement != null) {
                    vcard.remove(currentElement);
                }
                vcard.add(avatarElement);
            }
        }
        Log.debug("LdapVCardProvider: Returning vcard");
        return vcard;
    }

    /**
     * Loads the avatar element from the user's DB stored vcard.
     *
     * @param username User whose vcard/avatar element we are loading.
     * @return Loaded avatar element or null if not found.
     */
    private Element loadAvatarFromDatabase(String username) {
        Element vcardElement = defaultProvider.loadVCard(username);
        Element avatarElement = null;
        if (vcardElement != null && vcardElement.element("PHOTO") != null) {
            avatarElement = vcardElement.element("PHOTO").createCopy();
        }
        return avatarElement;
    }

    /**
     * Handles when a user creates a new vcard.
     *
     * @param username User that created a new vcard.
     * @param vCardElement vCard element containing the new vcard.
     * @throws UnsupportedOperationException If an invalid field is changed or we are in readonly mode.
     */
    public void createVCard(String username, Element vCardElement) throws UnsupportedOperationException {
        if (dbStorageEnabled && defaultProvider != null) {
            if (isValidVCardChange(username, vCardElement)) {
                updateOrCreateVCard(username, vCardElement);
            }
            else {
                throw new UnsupportedOperationException("LdapVCardProvider: Invalid vcard changes.");
            }
        }
        else {
            throw new UnsupportedOperationException("LdapVCardProvider: VCard changes not allowed.");
        }
    }

    /**
     * Handles when a user updates their vcard.
     *
     * @param username User that updated their vcard.
     * @param vCardElement vCard element containing the new vcard.
     * @throws UnsupportedOperationException If an invalid field is changed or we are in readonly mode.
     */
    public void updateVCard(String username, Element vCardElement) throws UnsupportedOperationException {
        if (dbStorageEnabled && defaultProvider != null) {
            if (isValidVCardChange(username, vCardElement)) {
                updateOrCreateVCard(username, vCardElement);
            }
            else {
                throw new UnsupportedOperationException("LdapVCardProvider: Invalid vcard changes.");
            }
        }
        else {
            throw new UnsupportedOperationException("LdapVCardProvider: VCard changes not allowed.");
        }
    }

    /**
     * Handles when a user deletes their vcard.
     *
     * @param username User that deketed their vcard.
     * @throws UnsupportedOperationException If an invalid field is changed or we are in readonly mode.
     */
    public void deleteVCard(String username) throws UnsupportedOperationException {
        if (dbStorageEnabled && defaultProvider != null) {
            defaultProvider.deleteVCard(username);
        }
        else {
            throw new UnsupportedOperationException("LdapVCardProvider: Attempted to delete vcard in read-only mode.");
        }
    }

    /**
     * Updates or creates a local copy of the passed vcard.
     *
     * @param username User we are setting the vcard for.
     * @param vCardElement vCard element we are storing.
     */
    private void updateOrCreateVCard(String username, Element vCardElement) {
        Element vcard = vCardElement.createCopy();
        // Trim away everything but the PHOTO element
        for (Object obj : vcard.elements()) {
            Element elem = (Element)obj;
            if (!elem.getName().equals("PHOTO")) {
                vcard.remove(elem);
            }
        }
        // If the vcard exists, update it, otherwise create it.
        try {
            defaultProvider.createVCard(username, vcard);
        }
        catch (AlreadyExistsException e) {
            try {
                defaultProvider.updateVCard(username, vcard);
            }
            catch (NotFoundException ee) {
                Log.error("LdapVCardProvider: Unable to find vcard, despite having been told it existed.");
            }
        }
    }

    /**
     * Simple helper function to check if an element is empty (null or contains whitespace)
     *
     * @param elem element to check.
     * @return True if the string is null or all whitespace/empty.
     */
    private Boolean isEmptyElement(Element elem) {
        if (elem == null) return true;
        return elem.getText().matches("\\s*");
    }

    /**
     * Compares two tree paths for equal contents.
     *
     * @param path Path to compare, separate pieces of path as elements in array
     * @param firstElem First element to compare
     * @param secondElem Second element to compare
     * @return True or false if the path contents are different
     */
    private Boolean isPathEqual(List<String> path, Element firstElem, Element secondElem) {
        Element currentA = firstElem;
        Element currentB = secondElem;
        for (String node : path) {
            if (currentA.element(node) != null && currentB.element(node) != null) {
                currentA = currentA.element(node);
                currentB = currentB.element(node);
            }
            else {
                // Don't have the node in both trees, they are "equal".
                return true;
            }
        }
        // Current A and current B are pointing to the same equivalent node now
        if (!isEmptyElement(currentA) && !isEmptyElement(currentB)) {
            // Both not empty, lets compare
            return currentA.getText().equals(currentB.getText());
        }
        else if (isEmptyElement(currentA) && isEmptyElement(currentB)) {
            // Both empty, no problem, no change
            return true;
        }
        else {
            // Hrm, one empty, one not, that's not the same.  ;)
            return false;
        }
    }

    /**
     * Returns true or false if the change to the existing vcard is valid (only to PHOTO element)
     *
     * @param username User who's LDAP-based vcard we will compare with.
     * @param othervCard Other vCard Element we will compare against.
     * @return True or false if the changes made were valid (only to PHOTO element)
     */
    private Boolean isValidVCardChange(String username, Element othervCard) {
        if (othervCard == null) {
            // Well if there's nothing to change, of course it's valid.
            return true;
        }
        // Un-escape username.
        username = JID.unescapeNode(username);
        Map<String, String> map = getLdapAttributes(username);
        // Retrieve LDAP created vcard for comparison
        Element vcard = new VCard(template).getVCard(map);
        if (vcard == null) {
            // This person has no vcard at all, may not change it!
            return false;
        }
        // Check Name
        if (!isPathEqual(Arrays.asList("N","GIVEN"), vcard, othervCard)) return false;
        // Check Email
        if (!isPathEqual(Arrays.asList("EMAIL","USERID"), vcard, othervCard)) return false;
        // Check Full Name
        if (!isPathEqual(Arrays.asList("FN"), vcard, othervCard)) return false;
        // Check Nickname
        if (!isPathEqual(Arrays.asList("NICKNAME"), vcard, othervCard)) return false;
        // Check Birthday
        if (!isPathEqual(Arrays.asList("BDAY"), vcard, othervCard)) return false;
        // Check Photo/Avatar
        // We allow this, so moving on
        // Check Addresses
        for (Object obja : vcard.elements("ADR")) {
            Element firstelem = (Element)obja;
            if (firstelem.element("HOME") != null) {
                for (Object objb : othervCard.elements("ADR")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("HOME") != null) {
                        // Check Home - Street Address
                        if (!isPathEqual(Arrays.asList("STREET"), firstelem, secondelem)) return false;
                        // Check Home - City
                        if (!isPathEqual(Arrays.asList("LOCALITY"), firstelem, secondelem)) return false;
                        // Check Home - State/Province
                        if (!isPathEqual(Arrays.asList("REGION"), firstelem, secondelem)) return false;
                        // Check Home - Postal Code
                        if (!isPathEqual(Arrays.asList("PCODE"), firstelem, secondelem)) return false;
                        // Check Home - Country
                        if (!isPathEqual(Arrays.asList("CTRY"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("WORK") != null) {
                for (Object objb : othervCard.elements("ADR")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("WORK") != null) {
                        // Check Business - Street Address
                        if (!isPathEqual(Arrays.asList("STREET"), firstelem, secondelem)) return false;
                        // Check business - City
                        if (!isPathEqual(Arrays.asList("LOCALITY"), firstelem, secondelem)) return false;
                        // Check Business - State/Province
                        if (!isPathEqual(Arrays.asList("REGION"), firstelem, secondelem)) return false;
                        // Check Business - Postal Code
                        if (!isPathEqual(Arrays.asList("PCODE"), firstelem, secondelem)) return false;
                        // Check Business - Country
                        if (!isPathEqual(Arrays.asList("CTRY"), firstelem, secondelem)) return false;
                    }
                }
            }
        }
        // Check Phone Numbers
        for (Object obja : vcard.elements("TEL")) {
            Element firstelem = (Element)obja;
            if (firstelem.element("HOME") != null && firstelem.element("VOICE") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("HOME") != null && secondelem.element("VOICE") != null) {
                        // Check Home - Phone Number
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("HOME") != null && firstelem.element("CELL") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("HOME") != null && secondelem.element("CELL") != null) {
                        // Check Home - Mobile Number
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("HOME") != null && firstelem.element("FAX") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("HOME") != null && secondelem.element("FAX") != null) {
                        // Check Home - Fax
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("HOME") != null && firstelem.element("PAGER") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("HOME") != null && secondelem.element("PAGER") != null) {
                        // Check Home - Pager
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            if (firstelem.element("WORK") != null && firstelem.element("VOICE") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("WORK") != null && secondelem.element("VOICE") != null) {
                        // Check Business - Phone Number
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("WORK") != null && firstelem.element("CELL") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("WORK") != null && secondelem.element("CELL") != null) {
                        // Check Business - Mobile Number
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("WORK") != null && firstelem.element("FAX") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("WORK") != null && secondelem.element("FAX") != null) {
                        // Check Business - Fax
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
            else if (firstelem.element("WORK") != null && firstelem.element("PAGER") != null) {
                for (Object objb : othervCard.elements("TEL")) {
                    Element secondelem = (Element)objb;
                    if (secondelem.element("WORK") != null && secondelem.element("PAGER") != null) {
                        // Check Business - Pager
                        if (!isPathEqual(Arrays.asList("NUMBER"), firstelem, secondelem)) return false;
                    }
                }
            }
        }
        // Check Business - Job Title
        if (!isPathEqual(Arrays.asList("TITLE"), vcard, othervCard)) return false;
        // Check Business - Department
        if (!isPathEqual(Arrays.asList("ORG","ORGUNIT"), vcard, othervCard)) return false;
        // Well.. we're through the gauntlet.  Guess we're good.
        return true;
    }


    public boolean isReadOnly() {
        return !dbStorageEnabled;
    }

    public void propertySet(String property, Map params) {
        if ("ldap.vcardDBStorage".equals(property)) {
            Boolean enabled = Boolean.parseBoolean((String)params.get("value"));
            if (enabled) {
                if (defaultProvider == null) {
                    defaultProvider = new DefaultVCardProvider();
                    dbStorageEnabled = true;
                }
            }
            else {
                if (defaultProvider != null) {
                    dbStorageEnabled = false;
                    defaultProvider = null;
                }
            }
        }
    }

    public void propertyDeleted(String property, Map params) {
        if ("ldap.vcardDBStorage".equals(property)) {
            if (defaultProvider != null) {
                dbStorageEnabled = false;
                defaultProvider = null;
            }
        }
    }

    public void xmlPropertySet(String property, Map params) {
        if ("ldap.vcard-mapping".equals(property)) {
            initTemplate();
            // Reset cache of vCards
            VCardManager.getInstance().reset();
        }
    }

    public void xmlPropertyDeleted(String property, Map params) {
        //Ignore
    }

    /**
     * Class to hold a <code>Document</code> representation of a vcard mapping
     * and unique attribute placeholders. Used by <code>VCard</code> to apply
     * a <code>Map</code> of ldap attributes to ldap values via
     * <code>MessageFormat</code>
     *
     * @author rkelly
     */
    private static class VCardTemplate {

        private Document document;

        private String[] attributes;

        public VCardTemplate(Document document) {
            Set<String> set = new HashSet<String>();
            this.document = document;
            treeWalk(this.document.getRootElement(), set);
            attributes = set.toArray(new String[set.size()]);
        }

        public String[] getAttributes() {
            return attributes;
        }

        public Document getDocument() {
            return document;
        }

        private void treeWalk(Element element, Set<String> set) {
            for (int i = 0, size = element.nodeCount(); i < size; i++) {
                Node node = element.node(i);
                if (node instanceof Element) {
                    Element emement = (Element) node;

                    StringTokenizer st = new StringTokenizer(emement.getTextTrim(), ", //{}");
                    while (st.hasMoreTokens()) {
                        // Remove enclosing {}
                        String string = st.nextToken().replaceAll("(\\{)([\\d\\D&&[^}]]+)(})", "$2");
                        Log.debug("VCardTemplate: found attribute " + string);
                        set.add(string);
                    }
                    treeWalk(emement, set);
                }
            }
        }
    }

    /**
     * vCard class that converts vcard data using a template.
     */
    private static class VCard {

        private VCardTemplate template;

        public VCard(VCardTemplate template) {
            this.template = template;
        }

        public Element getVCard(Map<String, String> map) {
            Document document = (Document) template.getDocument().clone();
            Element element = document.getRootElement();
            return treeWalk(element, map);
        }

        private Element treeWalk(Element element, Map<String, String> map) {
            for (int i = 0, size = element.nodeCount(); i < size; i++) {
                Node node = element.node(i);
                if (node instanceof Element) {
                    Element emement = (Element) node;

                    String elementText = emement.getTextTrim();
                    if (elementText != null && !"".equals(elementText)) {
                        String format = emement.getStringValue();

                        StringTokenizer st = new StringTokenizer(elementText, ", //{}");
                        while (st.hasMoreTokens()) {
                            // Remove enclosing {}
                            String field = st.nextToken();
                            String attrib = field.replaceAll("(\\{)(" + field + ")(})", "$2");
                            String value = map.get(attrib);
                            format = format.replaceFirst("(\\{)(" + field + ")(})", value);
                        }
                        emement.setText(format);
                    }
                    treeWalk(emement, map);
                }
            }
            return element;
        }
    }
}
