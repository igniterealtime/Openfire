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

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XPPReader;
import org.dom4j.Document;
import org.dom4j.Element;

import java.util.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * <p>A model for admin tab and sidebar info. This class loads in xml definitions of the data and
 * produces an in-memory model. There is an internal class, {@link Item} which is the main part
 * of the model. Items hold info like name, id, url and description as well as an arbritrary number
 * of sub items. Based on this we can make a tree model of the data.</p>
 *
 * <p>This class loads its data from the <tt>admin-sidebar.xml</tt> file which is assumed to be in
 * the main application jar file. In addition, it will load files from
 * <tt>META-INF/admin-sidebar.xml</tt> if they're found. This allows developers to extend the
 * functionality of the admin console to provide more options. See the main
 * <tt>admin-sidebar.xml</tt> file for documentation of its format.</p>
 *
 * <p>Note: IDs in the XML file must be unique because an internal mapping is kept of IDs to
 * nodes.</p>
 */
public class AdminConsole {

    private static Map<String,Item> items;
    private static String appName;
    private static String logoImage;

    static {
        init();
    }

    private static void init() {
        items = Collections.synchronizedMap(new LinkedHashMap<String,Item>());
        load();
    }

    /** Not instantiatable */
    private AdminConsole() {
    }

    /**
     * Adds XML stream to the tabs/sidebar model.
     *
     * @param in the XML input stream.
     * @throws Exception if an error occurs when parsing the XML or adding it to the model.
     */
    public static void addXMLSource(InputStream in) throws Exception {
        addToModel(in);
    }

    /**
     * Returns the name of the application.
     */
    public static String getAppName() {
        return appName;
    }

    /**
     * Returns the URL (relative or absolute) of the main logo image for the admin console.
     */
    public static String getLogoImage() {
        return logoImage;
    }

    /**
     * Returns all root items. Getting the iterator from this collection returns
     * all root items (should be used as tabs in the admin tool).
     *
     * @return a collection of all items - the root items are returned by calling the
     *      <tt>iterator()</tt> method.
     */
    public static Collection<Item> getItems() {
        List<Item> rootItems = new ArrayList<Item>();
        for (Item i : items.values()) {
            if (i.getParent() == null) {
                rootItems.add(i);
            }
        }
        return rootItems;
    }

    /**
     * Returns an item given its ID or <tt>null</tt> if it can't be found.
     *
     * @param id the ID of the item.
     * @return an item given its ID or <tt>null</tt> if it can't be found.
     */
    public static Item getItem(String id) {
        return items.get(id);
    }

    /**
     * Returns the root item given a child item. In other words, a lookup is done on the ID for
     * the corresponding item - that item is assumed to be a leaf and this method returns the
     * root ancestor of it.
     *
     * @param id the ID of the child item.
     * @return the root ancestor of the specified child item.
     */
    public static Item getRootByChildID(String id) {
        if (id == null) {
            return null;
        }
        Item child = getItem(id);
        Item root = null;
        if (child != null) {
            Item parent = child.getParent();
            root = parent;
            while (parent != null) {
                parent = parent.getParent();
                if (parent != null) {
                    root = parent;
                }
            }
        }
        return root;
    }

    /**
     * Returns <tt>true</tt> if the given item is a sub-menu item.
     *
     * @param item the item to test.
     * @return <tt>true</tt> if the given item is a sub-menu item, <tt>false</tt> otherwise.
     */
    public static boolean isSubMenItem(Item item) {
        int parentCount = 0;
        Item parent = item.getParent();
        while (parent != null) {
            parentCount++;
            parent = parent.getParent();
        }
        return parentCount >= 3;
    }

    /**
     * Returns the ID of the page ID associated with this sub page ID.
     * @param subPageID the subPageID to use to look up the page ID.
     * @return the associated pageID or <tt>null</tt> if it can't be found.
     */
    public static String lookupPageID(String subPageID) {
        String pageID = null;
        Item item = getItem(subPageID);
        if (item != null) {
            Item parent = item.getParent();
            if (parent != null) {
                parent = parent.getParent();
                if (parent != null) {
                    pageID = parent.getId();
                }
            }
        }
        return pageID;
    }

    /**
     * A simple class to model an item. Each item has attributes used by the admin console to
     * display it like ID, name, URL and description. Also, from each item you can get its parent
     * (because an Item goes in a tree structure) and any children items it has.
     */
    public static class Item {

        private String id;
        private String name;
        private String description;
        private String url;
        private boolean active;
        private Map<String,Item> items;
        private Item parent;
        private static int idSeq = 0;

        /**
         * Creates a new item given its main attributes.
         */
        public Item(String id, String name, String description, String url) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.url = url;
            init();
        }

        /**
         * Creates a new item given its main attributes and the parent item (this helps set up
         * the tree structure).
         */
        public Item(String id, String name, String description, String url, Item parent) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.url = url;
            this.parent = parent;
            init();
        }

        private void init() {
            items = Collections.synchronizedMap(new LinkedHashMap<String,Item>());
            if (id == null) {
                id = String.valueOf(idSeq++);
            }
        }

        /**
         * Returns the ID of the item.
         */
        public String getId() {
            return id;
        }

        /**
         * Returns the name of the item - this is the display name.
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         */
        void setName(String name) {
            this.name = name;
        }

        /**
         * Returns the description of the item.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Sets the description.
         */
        void setDescription(String description) {
            this.description = description;
        }

        /**
         * Returns the URL for this item.
         */
        public String getUrl() {
            return url;
        }

        /**
         * Sets the URL for this item.
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * Returns true if this items is active - in the admin console this would mean it's selected.
         */
        public boolean isActive() {
            return active;
        }

        /**
         * Sets the item as active - in the admin console this would mean it's selected.
         */
        public void setActive(boolean active) {
            this.active = active;
        }

        /**
         * Returns the parent item or <tt>null</tt> if this is a root item.
         */
        public Item getParent() {
            return parent;
        }

        /**
         * Sets the parent item.
         */
        public void setParent(Item parent) {
            this.parent = parent;
        }

        public void addItem(Item item) {
            items.put(item.getId(), item);
        }

        /**
         * Returns the items as a collection. Use the Collection API to get/set/remove items.
         */
        public Collection<Item> getItems() {
            return items.values();
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null) {
                return false;
            }
            if (!(o instanceof Item)) {
                return false;
            }
            Item i = (Item) o;
            if (id == null || !id.equals(i.id)) {
                return false;
            }
            return true;
        }

        /**
         * Returns the ID of the item.
         */
        public String toString() {
            return id;
        }
    }

    private static void load() {
        // Load the admin-sidebar.xml file from the jiveforums.jar file:
        InputStream in = ClassUtils.getResourceAsStream("/admin-sidebar.xml");
        if (in == null) {
            Log.error("Failed to load admin-sidebar.xml file from Jive Messenger classes - admin "
                    + "console will not work correctly.");
            return;
        }
        try {
            addToModel(in);
        }
        catch (Exception e) {
            Log.error("Failure when parsing main admin-sidebar.xml file", e);
        }
        try {
            in.close();
        }
        catch (Exception ignored) {}

        // Load other admin-sidebar.xml files from the classpath
        ClassLoader[] classLoaders = getClassLoaders();
        for (int i=0; i<classLoaders.length; i++) {
            URL url = null;
            try {
                if (classLoaders[i] != null) {
                    Enumeration e = classLoaders[i].getResources("/META-INF/admin-sidebar.xml");
                    while (e.hasMoreElements()) {
                        url = (URL)e.nextElement();
                        in = url.openStream();
                        addToModel(in);
                        try {
                            in.close();
                        }
                        catch (Exception ignored) {}
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
    }

    private static void addToModel(InputStream in) throws Exception {

        Document doc = XPPReader.parseDocument(new InputStreamReader(in), AdminConsole.class);
        // Set any global properties
        String globalAppname = getProperty(doc, "global.appname");
        if (globalAppname != null) {
            appName = globalAppname;
        }
        String globalLogoImage = getProperty(doc, "global.logo-image");
        if (globalLogoImage != null) {
            logoImage = globalLogoImage;
        }

        // Get all children of the 'tabs' element - should be 'tab' items:
        List tabs = doc.getRootElement().elements("tab");
        for (int i=0; i<tabs.size(); i++) {
            Element tab = (Element)tabs.get(i);

            // Create a new top level item with data from the xml file:
            String id = tab.attributeValue("id");
            String name = tab.attributeValue("name");
            String description = tab.attributeValue("description");
            Item item = new Item(id, name, description, null);
            // Add that item to the item collection
            items.put(id, item);

            // Delve down into this item's sidebars - build up a model of these then add into
            // the item above.
            List sidebars = tab.elements("sidebar");
            for (int j=0; j<sidebars.size(); j++) {
                Element sidebar = (Element)sidebars.get(j);

                name = sidebar.attributeValue("name");
                // Create a new item, set its name
                Item sidebarItem = new Item(null, name, null, null);
                // Get all items of this sidebar:
                List subitems = sidebar.elements("item");
                for (int k=0; k<subitems.size(); k++) {
                    Element subitem = (Element)subitems.get(k);
                    // Get the id, name, descr and url attributes:
                    String subID = subitem.attributeValue("id");
                    String subName = subitem.attributeValue("name");
                    String subDescr = subitem.attributeValue("description");
                    String subURL = subitem.attributeValue("url");
                    // Build an item with this, add it to the subItem we made above
                    Item kItem = new Item(subID, subName, subDescr, subURL, sidebarItem);
                    items.put(kItem.getId(), kItem);
                    sidebarItem.addItem(kItem);
                    // Build any sub-sub menus:
                    subAddtoModel(subitem, kItem);
                    // If this is the first item, set the root menu item's URL as this URL:
                    if (j==0 && k == 0) {
                        item.setUrl(subURL);
                    }
                }
                // Add the subItem to the item created above
                sidebarItem.setParent(item);
                items.put(sidebarItem.getId(), sidebarItem);
                item.addItem(sidebarItem);
            }
        }
    }

    private static String getProperty(Document doc, String propName) {
        String[] name = parsePropertyName(propName);
        String value = null;
        // Search for this property by traversing down the XML heirarchy.
        Element element = doc.getRootElement();
        for (int i = 0; i < name.length; i++) {
            element = element.element(name[i]);
            if (element == null) {
                value = null;
                break;
            }
        }
        // At this point, we found a matching property, so return its value.
        // Empty strings are returned as null.
        if (element != null) {
            value = element.getTextTrim();
            if ("".equals(value)) {
                value = null;
            }
        }
        return value;
    }

    private static String getAttribute(Document doc, String propName, String attribute) {
        String[] name = parsePropertyName(propName);
        String value = null;
        // Search for this property by traversing down the XML heirarchy.
        Element element = doc.getRootElement();
        for (int i = 0; i < name.length; i++) {
            element = element.element(name[i]);
            if (element == null) {
                value = null;
                break;
            }
        }
        // At this point, we found a matching property, so return its value.
        // Empty strings are returned as null.
        value = element.attributeValue(attribute);
        if ("".equals(value)) {
            value = null;
        }
        return value;
    }

    private static Element[] getChildElements(Document doc, String propName) {
        String[] name = parsePropertyName(propName);
        // Search for this property by traversing down the XML heirarchy.
        Element element = doc.getRootElement();
        for (int i = 0; i < name.length; i++) {
            element = element.element(name[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new Element[]{};
            }
        }
        // We found matching property, return names of children.
        List children = element.elements();
        int childCount = children.size();
        Element[] elements = new Element[childCount];
        for (int i=0; i<childCount; i++) {
            elements[i] = (Element)children.get(i);
        }
        return elements;
    }

    private static String[] parsePropertyName(String name) {
        List propName = new ArrayList(5);
        // Use a StringTokenizer to tokenize the property name.
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        while (tokenizer.hasMoreTokens()) {
            propName.add(tokenizer.nextToken());
        }
        return (String[])propName.toArray(new String[propName.size()]);
    }

    private static void subAddtoModel(Element parentElement, Item parentItem) {

        List subsidebars = parentElement.elements("subsidebar");
        for (int i=0; i<subsidebars.size(); i++) {
            Element subsidebar = (Element)subsidebars.get(i);
            String subsidebarName = subsidebar.attributeValue("name");
            Item subsidebarItem = new Item(null, subsidebarName, null, null, parentItem);
            // Get the items under it
            List subitems = subsidebar.elements("item");
            for (int j=0; j<subitems.size(); j++) {
                Element item = (Element)subitems.get(j);
                String id = item.attributeValue("id");
                String name = item.attributeValue("name");
                String url = item.attributeValue("url");
                String descr = item.attributeValue("description");
                Item newItem = new Item(id, name, descr, url, subsidebarItem);
                subsidebarItem.addItem(newItem);
                items.put(id, newItem);
            }
            parentItem.addItem(subsidebarItem);
        }
    }

    /**
     * Returns an array of class loaders to load resources from.
     */
    private static ClassLoader[] getClassLoaders() {
        ClassLoader[] classLoaders = new ClassLoader[3];
        classLoaders[0] = AdminConsole.class.getClass().getClassLoader();
        classLoaders[1] = Thread.currentThread().getContextClassLoader();
        classLoaders[2] = ClassLoader.getSystemClassLoader();
        return classLoaders;
    }

    // Called by test classes to wipe and reload the internal data
    private static void clear() {
        init();
    }
}
