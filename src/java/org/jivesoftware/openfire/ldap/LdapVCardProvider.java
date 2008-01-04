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
     * The default vCard provider is used to handle the vCard in the database. vCard
     * fields that can be overriden are stored in the database.
     *
     * This is used/created only if we are storing avatars in the database.
     */
    private DefaultVCardProvider defaultProvider = null;

    public LdapVCardProvider() {
        manager = LdapManager.getInstance();
        initTemplate();
        // Listen to property events so that the template is always up to date
        PropertyEventDispatcher.addListener(this);
        // DB vcard provider used for loading properties overwritten in the DB
        defaultProvider = new DefaultVCardProvider();
        // Check of avatars can be overwritten (and stored in the database)
        dbStorageEnabled = JiveGlobals.getBooleanProperty("ldap.override.avatar", false);
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
                    Log.debug("LdapVCardProvider: Found attribute "+attribute+" of type: "+ob.getClass());
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
     * Returns a merged LDAP vCard combined with a PHOTO element provided in specified vCard.
     *
     * @param username User whose vCard this is.
     * @param mergeVCard vCard element that we are merging PHOTO element from into the LDAP vCard.
     * @return vCard element after merging in PHOTO element to LDAP data.
     */
    private Element getMergedVCard(String username, Element mergeVCard) {
        // Un-escape username.
        username = JID.unescapeNode(username);
        Map<String, String> map = getLdapAttributes(username);
        Log.debug("LdapVCardProvider: Retrieving LDAP mapped vcard for " + username);
        Element vcard = new VCard(template).getVCard(map);
        if (mergeVCard == null) {
            // No vcard passed in?  Hrm.  Fine, return LDAP vcard.
            return vcard;
        }
        Element photoElement = mergeVCard.element("PHOTO").createCopy();
        if (photoElement == null || photoElement.element("BINVAL") == null || photoElement.element("BINVAL").getText().matches("\\s*")) {
            // We were passed something null or empty, so lets just return the LDAP based vcard.
            return vcard;
        }
        // Now we need to check that the LDAP vcard doesn't have a PHOTO element that's filled in.
        if (!((vcard.element("PHOTO") == null || vcard.element("PHOTO").element("BINVAL") == null || vcard.element("PHOTO").element("BINVAL").getText().matches("\\s*")))) {
            // Hrm, it does, return the original vcard;
            return vcard;
        }
        Log.debug("LdapVCardProvider: Merging avatar element from passed vcard");
        Element currentElement = vcard.element("PHOTO");
        if (currentElement != null) {
            vcard.remove(currentElement);
        }
        vcard.add(photoElement);
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
    public Element createVCard(String username, Element vCardElement)
            throws UnsupportedOperationException, AlreadyExistsException {
        throw new UnsupportedOperationException("LdapVCardProvider: VCard changes not allowed.");
    }

    /**
     * Handles when a user updates their vcard.
     *
     * @param username User that updated their vcard.
     * @param vCardElement vCard element containing the new vcard.
     * @throws UnsupportedOperationException If an invalid field is changed or we are in readonly mode.
     */
    public Element updateVCard(String username, Element vCardElement) throws UnsupportedOperationException {
        if (dbStorageEnabled && defaultProvider != null) {
            if (isValidVCardChange(username, vCardElement)) {
                Element mergedVCard = getMergedVCard(username, vCardElement);
                try {
                    defaultProvider.updateVCard(username, mergedVCard);
                } catch (NotFoundException e) {
                    try {
                        defaultProvider.createVCard(username, mergedVCard);
                    } catch (AlreadyExistsException e1) {
                        // Ignore
                    }
                }
                return mergedVCard;
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
        throw new UnsupportedOperationException("LdapVCardProvider: Attempted to delete vcard in read-only mode.");
    }

    /**
     * Returns true or false if the change to the existing vcard is valid (only to PHOTO element)
     *
     * @param username User who's LDAP-based vcard we will compare with.
     * @param newvCard New vCard Element we will compare against.
     * @return True or false if the changes made were valid (only to PHOTO element)
     */
    private Boolean isValidVCardChange(String username, Element newvCard) {
        if (newvCard == null) {
            // Well if there's nothing to change, of course it's valid.
            Log.debug("LdapVCardProvider: No new vcard provided (no changes), accepting.");
            return true;
        }
        // Un-escape username.
        username = JID.unescapeNode(username);
        Map<String, String> map = getLdapAttributes(username);
        // Retrieve LDAP created vcard for comparison
        Element ldapvCard = new VCard(template).getVCard(map);
        if (ldapvCard == null) {
            // This person has no vcard at all, may not change it!
            Log.debug("LdapVCardProvider: User has no LDAP vcard, nothing they can change, rejecting.");
            return false;
        }
        // If the LDAP vcard has a non-empty PHOTO element set, then there is literally no way this will be accepted.
        Element ldapPhotoElem = ldapvCard.element("PHOTO");
        if (ldapPhotoElem != null) {
            Element ldapBinvalElem = ldapPhotoElem.element("BINVAL");
            if (ldapBinvalElem != null && !ldapBinvalElem.getTextTrim().matches("\\s*")) {
                // LDAP is providing a valid PHOTO element, byebye!
                Log.debug("LdapVCardProvider: LDAP has a PHOTO element set, no way to override, rejecting.");
                return false;
            }
        }
        // Retrieve database vcard, if it exists
        Element dbvCard = defaultProvider.loadVCard(username);
        if (dbvCard != null) {
            Element dbPhotoElem = dbvCard.element("PHOTO");
            if (dbPhotoElem == null) {
                // DB has no photo, lets accept what we got.
                Log.debug("LdapVCardProvider: Database has no PHOTO element, accepting update.");
                return true;
            }
            else {
                Element newPhotoElem = newvCard.element("PHOTO");
                // Note: NodeComparator never seems to consider these equal, even if they are?
                if (!dbPhotoElem.asXML().equals(newPhotoElem.asXML())) {
                    Log.debug("LdapVCardProvider: DB photo element is:\n"+dbPhotoElem.asXML());
                    Log.debug("LdapVCardProvider: New photo element is:\n"+newPhotoElem.asXML());
                    // Photo element was changed.  Ignore all other changes and accept this.
                    Log.debug("LdapVCardProvider: PHOTO element changed, accepting update.");
                    return true;
                }
            }
        }
        // Ok, either something bad changed or nothing changed.  Either way, user either:
        // 1. should not have tried to change something 'readonly'
        // 2. shouldn't have bothered submitting no changes
        // So we'll consider this a bad return.
        Log.debug("LdapVCardProvider: PHOTO element didn't change, no reason to accept this, rejecting.");
        return false;
    }


    public boolean isReadOnly() {
        return !dbStorageEnabled;
    }

    public void propertySet(String property, Map params) {
        if ("ldap.override.avatar".equals(property)) {
            dbStorageEnabled = Boolean.parseBoolean((String)params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map params) {
        if ("ldap.override.avatar".equals(property)) {
            dbStorageEnabled = false;
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
