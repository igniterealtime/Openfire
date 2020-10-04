/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.admin;

import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * A model for admin tab and sidebar info. This class loads in XML definitions of the
 * data and produces an in-memory model.<p>
 *
 * This class loads its data from the {@code admin-sidebar.xml} file which is assumed
 * to be in the main application jar file. In addition, it will load files from
 * {@code META-INF/admin-sidebar.xml} if they're found. This allows developers to
 * extend the functionality of the admin console to provide more options. See the main
 * {@code admin-sidebar.xml} file for documentation of its format.
 */
public class AdminConsole {

    private static final Logger Log = LoggerFactory.getLogger(AdminConsole.class);

    private static Element coreModel;
    private static Map<String,Element> overrideModels;
    private static Element generatedModel;

    static {
        overrideModels = new LinkedHashMap<>();
        load();
    }

    /** Not instantiatable */
    private AdminConsole() {

    }

    /**
     * Adds XML stream to the tabs/sidebar model.
     *
     * @param name the name.
     * @param in the XML input stream.
     * @throws Exception if an error occurs when parsing the XML or adding it to the model.
     */
    public static void addModel(String name, InputStream in) throws Exception {
        Document doc = getDocument(in);
        addModel(name, (Element)doc.selectSingleNode("/adminconsole"));
    }

    /**
     * Adds an &lt;adminconsole&gt; Element to the tabs/sidebar model.
     *
     * @param name the name.
     * @param element the Element
     * @throws Exception if an error occurs.
     */
    public static synchronized void addModel(String name, Element element) throws Exception {
        overrideModels.put(name, element);
        rebuildModel();
    }

    /**
     * Removes an &lt;adminconsole&gt; Element from the tabs/sidebar model.
     *
     * @param name the name.
     */
    public static synchronized void removeModel(String name) {
        overrideModels.remove(name);
        rebuildModel();
    }

    /**
     * Returns the name of the application.
     *
     * @return the name of the application.
     */
    public static synchronized String getAppName() {
        Element appName = (Element)generatedModel.selectSingleNode("//adminconsole/global/appname");
        if (appName != null) {
            String pluginName = appName.attributeValue("plugin");
            return getAdminText(appName.getText(), pluginName);
        }
        else {
            return null;
        }
    }

    /**
     * Returns the URL of the main logo image for the admin console.
     *
     * @return the logo image.
     */
    public static synchronized String getLogoImage() {
        Element globalLogoImage = (Element)generatedModel.selectSingleNode(
                "//adminconsole/global/logo-image");
        if (globalLogoImage != null) {
            String pluginName = globalLogoImage.attributeValue("plugin");
            return getAdminText(globalLogoImage.getText(), pluginName);
        }
        else {
            return null;
        }
    }

    /**
     * Returns the URL of the login image for the admin console.
     *
     * @return the login image.
     */
    public static synchronized String getLoginLogoImage() {
        Element globalLoginLogoImage = (Element)generatedModel.selectSingleNode(
                "//adminconsole/global/login-image");
        if (globalLoginLogoImage != null) {
            String pluginName = globalLoginLogoImage.attributeValue("plugin");
            return getAdminText(globalLoginLogoImage.getText(), pluginName);
        }
        else {
            return null;
        }
    }

    /**
     * Returns the version string displayed in the admin console.
     *
     * @return the version string.
     */
    public static synchronized String getVersionString() {
        Element globalVersion = (Element)generatedModel.selectSingleNode(
                "//adminconsole/global/version");
        if (globalVersion != null) {
            String pluginName = globalVersion.attributeValue("plugin");
            return getAdminText(globalVersion.getText(), pluginName);
        }
        else {
            // Default to the Openfire version if none has been provided via XML.
            XMPPServer xmppServer = XMPPServer.getInstance();
            return xmppServer.getServerInfo().getVersion().getVersionString();
        }
    }

    /**
     * Returns the model. The model should be considered read-only.
     *
     * @return the model.
     */
    public static synchronized Element getModel() {
        return generatedModel;
    }

    /**
     * Convenience method to select an element from the model by its ID. If an
     * element with a matching ID is not found, {@code null} will be returned.
     *
     * @param id the ID.
     * @return the element.
     */
    public static synchronized Element getElemnetByID(String id) {
        return (Element)generatedModel.selectSingleNode("//*[@id='" + id + "']");
    }

    /**
     * Returns a text element for the admin console, applying the appropriate locale.
     * Internationalization logic will only be applied if the String is specially encoded
     * in the format "${key.name}". If it is, the String is pulled from the resource bundle.
     * If the pluginName is not {@code null}, the plugin's resource bundle will be used
     * to look up the key.
     *
     * @param string the String.
     * @param pluginName the name of the plugin that the i18n String can be found in,
     *      or {@code null} if the standard Openfire resource bundle should be used.
     * @return the string, or if the string is encoded as an i18n key, the value from
     *      the appropriate resource bundle.
     */
    public static String getAdminText(String string, String pluginName) {
        if (string == null) {
            return null;
        }
        // Look for the key symbol:
        if (string.indexOf("${") == 0 && string.indexOf("}") == string.length()-1) {
            return LocaleUtils.getLocalizedString(string.substring(2, string.length()-1), pluginName);
        }
        return string;
    }

    private static void load() {
        // Load the core model as the admin-sidebar.xml file from the classpath.
        InputStream in = ClassUtils.getResourceAsStream("/admin-sidebar.xml");
        if (in == null) {
            Log.error("Failed to load admin-sidebar.xml file from Openfire classes - admin "
                    + "console will not work correctly.");
            return;
        }
        try {
            Document doc = getDocument(in);
            coreModel = (Element)doc.selectSingleNode("/adminconsole");
        }
        catch (Exception e) {
            Log.error("Failure when parsing main admin-sidebar.xml file", e);
        }
        try {
            in.close();
        }
        catch (Exception ex) {
            Log.debug("An exception occurred while trying to close the input stream that was used to read admin-sidebar.xml", ex);
        }

        // Load other admin-sidebar.xml files from the classpath
        ClassLoader[] classLoaders = getClassLoaders();
        for (ClassLoader classLoader : classLoaders) {
            URL url = null;
            try {
                if (classLoader != null) {
                    Enumeration e = classLoader.getResources("/META-INF/admin-sidebar.xml");
                    while (e.hasMoreElements()) {
                        url = (URL) e.nextElement();
                        try {
                            in = url.openStream();
                            addModel("admin", in);
                        }
                        finally {
                            try {
                                if (in != null) {
                                    in.close();
                                }
                            }
                            catch (Exception ex) {
                                Log.debug("An exception occurred while trying to close the input stream that was used to read admin-sidebar.xml", ex);
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                String msg = "Failed to load admin-sidebar.xml";
                if (url != null) {
                    msg += " from resource: " + url.toString();
                }
                Log.warn(msg, e);
            }
        }
        rebuildModel();
    }

    /**
     * Rebuilds the generated model.
     */
    private static synchronized void rebuildModel() {
        Document doc = DocumentFactory.getInstance().createDocument();
        generatedModel = coreModel.createCopy();
        doc.add(generatedModel);

        // Add in all overrides.
        for (Element element : overrideModels.values()) {
            // See if global settings are overriden.
            Element appName = (Element)element.selectSingleNode("//adminconsole/global/appname");
            if (appName != null) {
                Element existingAppName = (Element)generatedModel.selectSingleNode(
                        "//adminconsole/global/appname");
                existingAppName.setText(appName.getText());
                if (appName.attributeValue("plugin") != null) {
                    existingAppName.addAttribute("plugin", appName.attributeValue("plugin"));
                }
            }
            Element appLogoImage = (Element)element.selectSingleNode("//adminconsole/global/logo-image");
            if (appLogoImage != null) {
                Element existingLogoImage = (Element)generatedModel.selectSingleNode(
                        "//adminconsole/global/logo-image");
                existingLogoImage.setText(appLogoImage.getText());
                if (appLogoImage.attributeValue("plugin") != null) {
                    existingLogoImage.addAttribute("plugin", appLogoImage.attributeValue("plugin"));
                }
            }
            Element appLoginImage = (Element)element.selectSingleNode("//adminconsole/global/login-image");
            if (appLoginImage != null) {
                Element existingLoginImage = (Element)generatedModel.selectSingleNode(
                        "//adminconsole/global/login-image");
                existingLoginImage.setText(appLoginImage.getText());
                if (appLoginImage.attributeValue("plugin") != null) {
                    existingLoginImage.addAttribute("plugin", appLoginImage.attributeValue("plugin"));
                }
            }
            Element appVersion = (Element)element.selectSingleNode("//adminconsole/global/version");
            if (appVersion != null) {
                Element existingVersion = (Element)generatedModel.selectSingleNode(
                        "//adminconsole/global/version");
                if (existingVersion != null) {
                    existingVersion.setText(appVersion.getText());
                    if (appVersion.attributeValue("plugin") != null) {
                        existingVersion.addAttribute("plugin", appVersion.attributeValue("plugin"));
                    }
                }
                else {
                    ((Element)generatedModel.selectSingleNode(
                            "//adminconsole/global")).add(appVersion.createCopy());
                }
            }
            // Tabs
            for (Object o : element.selectNodes("//tab")) {
                Element tab = (Element) o;
                String id = tab.attributeValue("id");
                Element existingTab = getElemnetByID(id);
                // Simple case, there is no existing tab with the same id.
                if (existingTab == null) {
                    // Make sure that the URL on the tab is set. If not, default to the
                    // url of the first item.
                    if (tab.attributeValue("url") == null) {
                        Element firstItem = (Element) tab.selectSingleNode(
                                "//item[@url]");
                        if (firstItem != null) {
                            tab.addAttribute("url", firstItem.attributeValue("url"));
                        }
                    }
                    generatedModel.add(tab.createCopy());
                }
                // More complex case -- a tab with the same id already exists.
                // In this case, we have to overrite only the difference between
                // the two elements.
                else {
                    overrideEntry(existingTab, tab);
                }
            }
        }

        // OF-1484: Order everything explicitly.
        orderModel();
    }

    /**
     * Sorts all tabs, their containing sidebars, and their containing entries based on the value of their 'order'
     * attributes.
     */
    private static void orderModel()
    {
        final Visitor visitor = new VisitorSupport()
        {
            @Override
            public void visit( Element node )
            {
                // This orders only the elements from the content, which can get messy if mixed content is of importance.
                // At the time of writing, the content other than elements was whitespace text (for indentation), which
                // is safe to ignore.
                Collections.sort( node.content(), new ElementByOrderAttributeComparator() );
                super.visit( node );
            }
        };
        generatedModel.accept( visitor );
    }

    private static void overrideSidebar(Element sidebar, Element overrideSidebar) {
        // Override name.
        overrideCommonAttributes(sidebar, overrideSidebar);
        // Override entries.
        for (Iterator i=overrideSidebar.elementIterator(); i.hasNext(); ) {
            Element entry = (Element)i.next();
            String id = entry.attributeValue("id");
            Element existingEntry = getElemnetByID(id);
            // Simple case, there is no existing sidebar with the same id.
            if (existingEntry == null) {
                sidebar.add(entry.createCopy());
            }
            // More complex case -- an entry with the same id already exists.
            // In this case, we have to overrite only the difference between
            // the two elements.
            else {
                overrideEntry(existingEntry, entry);
            }
        }
    }

    private static void overrideEntry(Element entry, Element overrideEntry) {
        // Override name.
        overrideCommonAttributes(entry, overrideEntry);
        if (overrideEntry.attributeValue("url") != null) {
            entry.addAttribute("url", overrideEntry.attributeValue("url"));
        }
        if (overrideEntry.attributeValue("description") != null) {
            entry.addAttribute("description", overrideEntry.attributeValue("description"));
        }
        // Override any sidebars contained in the entry.
        for (Iterator i=overrideEntry.elementIterator(); i.hasNext(); ) {
            Element sidebar = (Element)i.next();
            String id = sidebar.attributeValue("id");
            Element existingSidebar = getElemnetByID(id);
            // Simple case, there is no existing sidebar with the same id.
            if (existingSidebar == null) {
                entry.add(sidebar.createCopy());
            }
            // More complex case -- a sidebar with the same id already exists.
            // In this case, we have to overrite only the difference between
            // the two elements.
            else {
                overrideSidebar(existingSidebar, sidebar);
            }
        }
    }

    private static void overrideCommonAttributes(Element entry, Element overrideEntry) {
        if (overrideEntry.attributeValue("name") != null) {
            entry.addAttribute("name", overrideEntry.attributeValue("name"));
        }
        if (overrideEntry.attributeValue("plugin") != null) {
            entry.addAttribute("plugin", overrideEntry.attributeValue("plugin"));
        }
        if (overrideEntry.attributeValue("order") != null) {
            entry.addAttribute("order", overrideEntry.attributeValue("order"));
        }
    }

    /**
     * Returns an array of class loaders to load resources from.
     *
     * @return an array of class loaders to load resources from.
     */
    private static ClassLoader[] getClassLoaders() {
        ClassLoader[] classLoaders = new ClassLoader[3];
        classLoaders[0] = AdminConsole.class.getClass().getClassLoader();
        classLoaders[1] = Thread.currentThread().getContextClassLoader();
        classLoaders[2] = ClassLoader.getSystemClassLoader();
        return classLoaders;
    }

    /**
     * A comparator that compares Nodes by the value of their 'order' attribute, if the node is an Element. When it is
     * not, or when the 'order' attribute is absent, or cannot be parsed as an integer, the value '0' is used.
     *
     * @author Guus der Kinderen, guus.der.kinderen@gmail.com
     */
    private static class ElementByOrderAttributeComparator implements Comparator<Node>
    {
        @Override
        public int compare( Node o1, Node o2 )
        {
            try
            {
                final int p1 = o1 instanceof Element ? Integer.valueOf( ((Element)o1).attributeValue( "order", "0" ) ) : 0;
                final int p2 = o2 instanceof Element ? Integer.valueOf( ((Element)o2).attributeValue( "order", "0" ) ) : 0;
                return Integer.compare( p1, p2 );
            }
            catch ( NumberFormatException e )
            {
                Log.warn( "Unable to sort admin console tabs, as a non-numeric 'order' attribute value was found.", e );
                return 0;
            }
        }
    }

    private static Document getDocument(InputStream in) throws SAXException, DocumentException {
        SAXReader saxReader = new SAXReader();
        saxReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        saxReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        saxReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        saxReader.setIgnoreComments(true);
        return saxReader.read(in);
    }
}
