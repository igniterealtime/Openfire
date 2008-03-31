/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.admin;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.XMPPServer;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.DocumentFactory;
import org.dom4j.io.SAXReader;

import java.util.*;
import java.io.InputStream;
import java.net.URL;

/**
 * A model for admin tab and sidebar info. This class loads in XML definitions of the
 * data and produces an in-memory model.<p>
 *
 * This class loads its data from the <tt>admin-sidebar.xml</tt> file which is assumed
 * to be in the main application jar file. In addition, it will load files from
 * <tt>META-INF/admin-sidebar.xml</tt> if they're found. This allows developers to
 * extend the functionality of the admin console to provide more options. See the main
 * <tt>admin-sidebar.xml</tt> file for documentation of its format.
 */
public class AdminConsole {

    private static Element coreModel;
    private static Map<String,Element> overrideModels;
    private static Element generatedModel;

    static {
        overrideModels = new LinkedHashMap<String,Element>();
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
        SAXReader saxReader = new SAXReader();
        Document doc = saxReader.read(in);
        addModel(name, (Element)doc.selectSingleNode("/adminconsole"));
    }

    /**
     * Adds an &lt;adminconsole&gt; Element to the tabs/sidebar model.
     *
     * @param name the name.
     * @param element the Element
     * @throws Exception if an error occurs.
     */
    public static void addModel(String name, Element element) throws Exception {
        overrideModels.put(name, element);
        rebuildModel();
    }

    /**
     * Removes an &lt;adminconsole&gt; Element from the tabs/sidebar model.
     *
     * @param name the name.
     */
    public static void removeModel(String name) {
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
     * element with a matching ID is not found, <tt>null</tt> will be returned.
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
     * If the pluginName is not <tt>null</tt>, the plugin's resource bundle will be used
     * to look up the key.
     *
     * @param string the String.
     * @param pluginName the name of the plugin that the i18n String can be found in,
     *      or <tt>null</tt> if the standard Openfire resource bundle should be used.
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
            SAXReader saxReader = new SAXReader();
            Document doc = saxReader.read(in);
            coreModel = (Element)doc.selectSingleNode("/adminconsole");
        }
        catch (Exception e) {
            Log.error("Failure when parsing main admin-sidebar.xml file", e);
        }
        try {
            in.close();
        }
        catch (Exception ignored) {
            // Ignore.
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
                            catch (Exception ignored) {
                                // Ignore.
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
                    overrideTab(existingTab, tab);
                }
            }
        }
    }

    private static void overrideTab(Element tab, Element overrideTab) {
        // Override name, url, description.
        if (overrideTab.attributeValue("name") != null) {
            tab.addAttribute("name", overrideTab.attributeValue("name"));
        }
        if (overrideTab.attributeValue("url") != null) {
            tab.addAttribute("url", overrideTab.attributeValue("url"));
        }
        if (overrideTab.attributeValue("description") != null) {
            tab.addAttribute("description", overrideTab.attributeValue("description"));
        }
        if (overrideTab.attributeValue("plugin") != null) {
            tab.addAttribute("plugin", overrideTab.attributeValue("plugin"));
        }
        // Override sidebar items.
        for (Iterator i=overrideTab.elementIterator(); i.hasNext(); ) {
            Element sidebar = (Element)i.next();
            String id = sidebar.attributeValue("id");
            Element existingSidebar = getElemnetByID(id);
            // Simple case, there is no existing sidebar with the same id.
            if (existingSidebar == null) {
                tab.add(sidebar.createCopy());
            }
            // More complex case -- a sidebar with the same id already exists.
            // In this case, we have to overrite only the difference between
            // the two elements.
            else {
                overrideSidebar(existingSidebar, sidebar);
            }
        }
    }

    private static void overrideSidebar(Element sidebar, Element overrideSidebar) {
        // Override name.
        if (overrideSidebar.attributeValue("name") != null) {
            sidebar.addAttribute("name", overrideSidebar.attributeValue("name"));
        }
        if (overrideSidebar.attributeValue("plugin") != null) {
            sidebar.addAttribute("plugin", overrideSidebar.attributeValue("plugin"));
        }
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
        if (overrideEntry.attributeValue("name") != null) {
            entry.addAttribute("name", overrideEntry.attributeValue("name"));
        }
        if (overrideEntry.attributeValue("url") != null) {
            entry.addAttribute("url", overrideEntry.attributeValue("url"));
        }
        if (overrideEntry.attributeValue("description") != null) {
            entry.addAttribute("description", overrideEntry.attributeValue("description"));
        }
        if (overrideEntry.attributeValue("plugin") != null) {
            entry.addAttribute("plugin", overrideEntry.attributeValue("plugin"));
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
}