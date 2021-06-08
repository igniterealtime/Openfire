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

package org.jivesoftware.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.text.StringEscapeUtils;
import org.dom4j.Attribute;
import org.dom4j.CDATA;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the the ability to use simple XML property files. Each property is
 * in the form X.Y.Z, which would map to an XML snippet of:
 * <pre>
 * &lt;X&gt;
 *     &lt;Y&gt;
 *         &lt;Z&gt;someValue&lt;/Z&gt;
 *     &lt;/Y&gt;
 * &lt;/X&gt;
 * </pre>
 * The XML file is passed in to the constructor and must be readable and
 * writable. Setting property values will automatically persist those value
 * to disk. The file encoding used is UTF-8.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class XMLProperties {

    private static final Logger Log = LoggerFactory.getLogger(XMLProperties.class);
    private static final String ENCRYPTED_ATTRIBUTE = "encrypted";

    private Path file;
    private Document document;

    /**
     * Parsing the XML file every time we need a property is slow. Therefore,
     * we use a Map to cache property values that are accessed more than once.
     */
    private Map<String, String> propertyCache = new HashMap<>();

    /**
     * Creates a new empty XMLPropertiesTest object.
     *
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties() throws IOException {
       buildDoc(new StringReader("<root />"));
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param fileName the full path the file that properties should be read from
     *                 and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties(String fileName) throws IOException {
        this(Paths.get(fileName));
    }

    /**
     * Loads XML properties from a stream.
     *
     * @param in the input stream of XML.
     * @throws IOException if an exception occurs when reading the stream.
     */
    public XMLProperties(InputStream in) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            buildDoc(reader);
        }
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param file the file that properties should be read from and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    @Deprecated
    public XMLProperties(File file) throws IOException {
        this(file.toPath());
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param file the file that properties should be read from and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties(Path file) throws IOException {
        this.file = file;
        if (Files.notExists(file)) {
            // Attempt to recover from this error case by seeing if the
            // tmp file exists. It's possible that the rename of the
            // tmp file failed the last time Jive was running,
            // but that it exists now.
            Path tempFile;
            tempFile = file.getParent().resolve(file.getFileName() + ".tmp");
            if (Files.exists(tempFile)) {
                Log.error("WARNING: " + file.getFileName() + " was not found, but temp file from " +
                        "previous write operation was. Attempting automatic recovery." +
                        " Please check file for data consistency.");
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            // There isn't a possible way to recover from the file not
            // being there, so throw an error.
            else {
                throw new NoSuchFileException("XML properties file does not exist: "
                        + file.getFileName());
            }
        }
        // Check read and write privs.
        if (!Files.isReadable(file)) {
            throw new IOException("XML properties file must be readable: " + file.getFileName());
        }
        if (!Files.isWritable(file)) {
            throw new IOException("XML properties file must be writable: " + file.getFileName());
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
             buildDoc(reader);
        }
    }

    /**
     * Returns the value of the specified property.
     *
     * @param name the name of the property to get.
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name) {
        return getProperty(name, true);
    }

    /**
     * Returns the value of the specified property.
     *
     * @param name the name of the property to get.
     * @param ignoreEmpty Ignore empty property values (return null)
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name, boolean ignoreEmpty) {
        String value = propertyCache.get(name);
        if (value != null) {
            return value;
        }

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String aPropName : propName) {
            element = element.element(aPropName);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return null.
                return null;
            }
        }
        // At this point, we found a matching property, so return its value.
        // Empty strings are returned as null.
        value = element.getTextTrim();
        if (ignoreEmpty && "".equals(value)) {
            return null;
        }
        else {
            // check to see if the property is marked as encrypted
            if (JiveGlobals.isXMLPropertyEncrypted(name)) {
                Attribute encrypted = element.attribute(ENCRYPTED_ATTRIBUTE);
                if (encrypted != null) {
                    value = JiveGlobals.getPropertyEncryptor().decrypt(value);
                } else {
                    // rewrite property as an encrypted value
                    Log.info("Rewriting XML property " + name + " as an encrypted value");
                    setProperty(name, value);
                }
            }
            // Add to cache so that getting property next time is fast.
            propertyCache.put(name, value);
            return value;
        }
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @param ignored unused parameter
     * @return all child property values for the given node name.
     */
    public List<String> getProperties(String name, boolean ignored) {
        List<String> result = new ArrayList<>();
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return result;
            }
        }
        // We found matching property, return names of children.
        Iterator<Element> iter = element.elementIterator(propName[propName.length - 1]);
        Element prop;
        String value;
        boolean updateEncryption = false;
        while (iter.hasNext()) {
            prop = iter.next();
            // Empty strings are skipped.
            value = prop.getTextTrim();
            if (!"".equals(value)) {
                // check to see if the property is marked as encrypted
                if (JiveGlobals.isXMLPropertyEncrypted(name)) {
                    Attribute encrypted = prop.attribute(ENCRYPTED_ATTRIBUTE);
                    if (encrypted != null) {
                        value = JiveGlobals.getPropertyEncryptor().decrypt(value);
                    } else {
                        // rewrite property as an encrypted value
                        prop.addAttribute(ENCRYPTED_ATTRIBUTE, "true");
                        updateEncryption = true;
                    }
                }
                result.add(value);
            }
        }
        if (updateEncryption) {
            Log.info("Rewriting values for XML property " + name + " using encryption");
            saveProperties();
        }
        return result;
    }
    
    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @deprecated Retained for backward compatibility. Prefer getProperties(String, boolean)
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public String[] getProperties(String name) {
        return (String[]) getProperties(name, false).toArray();
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public Iterator getChildProperties(String name) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return Collections.EMPTY_LIST.iterator();
            }
        }
        // We found matching property, return values of the children.
        Iterator<Element> iter = element.elementIterator(propName[propName.length - 1]);
        ArrayList<String> props = new ArrayList<>();
        Element prop;
        String value;
        while (iter.hasNext()) {
            prop = iter.next();
            value = prop.getText();
            // check to see if the property is marked as encrypted
            if (JiveGlobals.isPropertyEncrypted(name) && Boolean.parseBoolean(prop.attribute(ENCRYPTED_ATTRIBUTE).getText())) {
                value = JiveGlobals.getPropertyEncryptor().decrypt(value);
            }
            props.add(value);
        }
        return props.iterator();
    }

    /**
     * Returns the value of the attribute of the given property name or {@code null}
     * if it doesn't exist.
     *
     * @param name the property name to lookup - ie, "foo.bar"
     * @param attribute the name of the attribute, ie "id"
     * @return the value of the attribute of the given property or {@code null} if
     *      it doesn't exist.
     */
    public String getAttribute(String name, String attribute) {
        if (name == null || attribute == null) {
            return null;
        }
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String child : propName) {
            element = element.element(child);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                break;
            }
        }
        if (element != null) {
            // Get its attribute values
            return element.attributeValue(attribute);
        }
        return null;
    }

    /**
     * Removes the given attribute from the XML document.
     *
     * @param name the property name to lookup - ie, "foo.bar"
     * @param attribute the name of the attribute, ie "id"
     * @return the value of the attribute of the given property or {@code null} if
     *      it did not exist.
     */
    public String removeAttribute(String name, String attribute) {
        if (name == null || attribute == null) {
            return null;
        }
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String child : propName) {
            element = element.element(child);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                break;
            }
        }
        String result = null;
        if (element != null) {
            // Get the attribute value and then remove the attribute
            Attribute attr = element.attribute(attribute);
            result = attr.getValue();
            element.remove(attr);
        }
        return result;
    }

    /**
     * Sets a property to an array of values. Multiple values matching the same property
     * is mapped to an XML file as multiple elements containing each value.
     * For example, using the name "foo.bar.prop", and the value string array containing
     * {"some value", "other value", "last value"} would produce the following XML:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name the name of the property.
     * @param values the values for the property (can be empty but not null).
     */
    public void setProperties(String name, List<String> values) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            // If we don't find this part of the property in the XML hierarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        String childName = propName[propName.length - 1];
        // We found matching property, clear all children.
        List<Element> toRemove = new ArrayList<>();
        Iterator<Element> iter = element.elementIterator(childName);
        while (iter.hasNext()) {
            toRemove.add(iter.next());
        }
        for (iter = toRemove.iterator(); iter.hasNext();) {
            element.remove(iter.next());
        }
        // Add the new children.
        for (String value : values) {
            Element childElement = element.addElement(childName);
            if (value.startsWith("<![CDATA[")) {
                Iterator<Node> it = childElement.nodeIterator();
                while (it.hasNext()) {
                    Node node = it.next();
                    if (node instanceof CDATA) {
                        childElement.remove(node);
                        break;
                    }
                }
                childElement.addCDATA(value.substring(9, value.length()-3));
            }
            else {
                String propValue = value;
                // check to see if the property is marked as encrypted
                if (JiveGlobals.isPropertyEncrypted(name)) {
                    propValue = JiveGlobals.getPropertyEncryptor().encrypt(value);
                    childElement.addAttribute(ENCRYPTED_ATTRIBUTE, "true");
                }
                childElement.setText(propValue);
            }
        }
        saveProperties();

        // Generate event.
        Map<String, Object> params = new HashMap<>();
        params.put("value", values);
        PropertyEventDispatcher.dispatchEvent(name,
                PropertyEventDispatcher.EventType.xml_property_set, params);
    }
    
    /**
     * Adds the given value to the list of values represented by the property name.
     * The property is created if it did not already exist.
     * 
     * @param propertyName The name of the property list to change
     * @param value The value to be added to the list
     * @return True if the value was added to the list; false if the value was already present
     */
    public boolean addToList(String propertyName, String value) {
        
        List<String> properties = getProperties(propertyName, true);
        boolean propertyWasAdded = properties.add(value);
        if (propertyWasAdded) {
            setProperties(propertyName, properties);
        }
        return propertyWasAdded;
    }
    
    /**
     * Removes the given value from the list of values represented by the property name.
     * The property is deleted if it no longer contains any values.
     * 
     * @param propertyName The name of the property list to change
     * @param value The value to be removed from the list
     * @return True if the value was removed from the list; false if the value was not found
     */
    public boolean removeFromList(String propertyName, String value) {
        
        List<String> properties = getProperties(propertyName, true);
        boolean propertyWasRemoved = properties.remove(value);
        if (propertyWasRemoved) {
            setProperties(propertyName, properties);
        }
        return propertyWasRemoved;
    }

    /**
     * Returns a list of names for all properties found in the XML file.
     *
     * @return Names for all properties in the file
     */
    public List<String> getAllPropertyNames() {
        List<String> result = new ArrayList<>();
        for (String propertyName : getChildPropertyNamesFor(document.getRootElement(), "")) {
            if (getProperty(propertyName) != null) {
                result.add(propertyName);
            }
        }
        return result;
    }
    
    private List<String> getChildPropertyNamesFor(Element parent, String parentName) {
        List<String> result = new ArrayList<>();
        for (Element child : parent.elements()) {
            String childName = parentName + (parentName.isEmpty() ? "" : ".") + child.getName();
            if (!result.contains(childName)) {
                result.add(childName);
                result.addAll(getChildPropertyNamesFor(child, childName));
            }
        }
        return result;
    }

    /**
     * Return all children property names of a parent property as a String array,
     * or an empty array if the if there are no children. For example, given
     * the properties {@code X.Y.A}, {@code X.Y.B}, and {@code X.Y.C}, then
     * the child properties of {@code X.Y} are {@code A}, {@code B}, and
     * {@code C}.
     *
     * @param parent the name of the parent property.
     * @return all child property values for the given parent.
     */
    public String[] getChildrenProperties(String parent) {
        String[] propName = parsePropertyName(parent);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String aPropName : propName) {
            element = element.element(aPropName);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        List children = element.elements();
        int childCount = children.size();
        String[] childrenNames = new String[childCount];
        for (int i = 0; i < childCount; i++) {
            childrenNames[i] = ((Element)children.get(i)).getName();
        }
        return childrenNames;
    }

    /**
     * Sets the value of the specified property. If the property doesn't
     * currently exist, it will be automatically created.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     * @return {@code true} if the property was correctly saved to file, otherwise {@code false}
     */
    public synchronized boolean setProperty(String name, String value) {
        if (name == null) {
            return false;
        }
        if (!StringEscapeUtils.escapeXml10(name).equals(name)) {
            throw new IllegalArgumentException("Property name cannot contain XML entities.");
        }
        if (value == null) {
            value = "";
        }

        // Set cache correctly with prop name and value.
        propertyCache.put(name, value);

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String aPropName : propName) {
            // If we don't find this part of the property in the XML hierarchy
            // we add it as a new node
            if (element.element(aPropName) == null) {
                element.addElement(aPropName);
            }
            element = element.element(aPropName);
        }
        // Set the value of the property in this node.
        if (value.startsWith("<![CDATA[")) {
            Iterator it = element.nodeIterator();
            while (it.hasNext()) {
                Node node = (Node) it.next();
                if (node instanceof CDATA) {
                    element.remove(node);
                    break;
                }
            }
            element.addCDATA(value.substring(9, value.length() - 3));
        } else {
            String propValue = value;
            // check to see if the property is marked as encrypted
            if (JiveGlobals.isXMLPropertyEncrypted(name)) {
                propValue = JiveGlobals.getPropertyEncryptor(true).encrypt(value);
                element.addAttribute(ENCRYPTED_ATTRIBUTE, "true");
            }
            element.setText(propValue);
        }
        // Write the XML properties to disk
        final boolean saved = saveProperties();

        // Generate event.
        Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        PropertyEventDispatcher.dispatchEvent(name, PropertyEventDispatcher.EventType.xml_property_set, params);
        return saved;
    }

    /**
     * Deletes the specified property.
     *
     * @param name the property to delete.
     */
    public synchronized void deleteProperty(String name) {
        // Remove property from cache.
        propertyCache.remove(name);

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            // Can't find the property so return.
            if (element == null) {
                return;
            }
        }
        // Found the correct element to remove, so remove it...
        element.remove(element.element(propName[propName.length - 1]));
        if (element.elements().size() == 0) {
            element.getParent().remove(element);
        }
        // .. then write to disk.
        saveProperties();

        JiveGlobals.setPropertyEncrypted(name, false);
        // Generate event.
        Map<String, Object> params = Collections.emptyMap();
        PropertyEventDispatcher.dispatchEvent(name, PropertyEventDispatcher.EventType.xml_property_deleted, params);
    }

    /**
     * Convenience routine to migrate an XML property into the database
     * storage method.  Will check for the XML property being null before
     * migrating.
     *
     * @param name the name of the property to migrate.
     */
    public void migrateProperty(String name) {
        final String xmlPropertyValue = getProperty(name);
        if (xmlPropertyValue != null) {
            final String databasePropertyValue = JiveGlobals.getProperty(name);
            if (databasePropertyValue == null) {
                Log.debug("JiveGlobals: Migrating XML property '" + name + "' into database.");
                JiveGlobals.setProperty(name, xmlPropertyValue);
                if (JiveGlobals.isXMLPropertyEncrypted(name)) {
                    JiveGlobals.setPropertyEncrypted(name, true);
                }
                deleteProperty(name);
            } else if (databasePropertyValue.equals(xmlPropertyValue)) {
                Log.debug("JiveGlobals: Deleting duplicate XML property '" + name + "' that is already in database.");
                if (JiveGlobals.isXMLPropertyEncrypted(name)) {
                    JiveGlobals.setPropertyEncrypted(name, true);
                }
                deleteProperty(name);
            } else if (!databasePropertyValue.equals(xmlPropertyValue)) {
                Log.warn("XML Property '" + name + "' differs from what is stored in the database.  Please make property changes in the database instead of the configuration file.");
            }
        }
    }

    /**
     * Builds the document XML model up based the given reader of XML data.
     * @param in the input stream used to build the xml document
     * @throws java.io.IOException thrown when an error occurs reading the input stream.
     */
    private void buildDoc(Reader in) throws IOException {
        try {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            document = xmlReader.read(in);
        }
        catch (Exception e) {
            Log.error("Error reading XML properties", e);
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Saves the properties to disk as an XML document. A temporary file is
     * used during the writing process for maximum safety.
     *
     * @return false if the file could not be saved, otherwise true
     */
    private synchronized boolean saveProperties() {
        if (file == null) {
            Log.error("Unable to save XML properties; no file specified");
            return false;
        }

        // Write data out to a temporary file first.
        final Path tempFile = file.getParent().resolve(file.getFileName() + ".tmp");
        try (final Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
            xmlWriter.write(document);
        } catch (final Exception e) {
            Log.error("Unable to write properties to tmpFile {}", tempFile, e);
            // There were errors so abort replacing the old property file.
            return false;
        }

        // No errors occurred, so delete the main file.
        // Delete the old file so we can replace it.
        try {
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            Log.error("Error deleting existing property file {}: ", tempFile, e);
            return false;
        }
        // Copy new contents to the file.
        try {
            Files.copy(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (final Exception e) {
            Log.error("Error copying new property file from {} to {}:", tempFile, file, e);
            // There were errors so abort replacing the old property file.
            return false;
        }

        // If no errors, delete the temp file.
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            Log.error("Error deleting temp file {}", tempFile, e);
        }
        return true;
    }

    /**
     * Returns an array representation of the given Jive property. Jive
     * properties are always in the format "prop.name.is.this" which would be
     * represented as an array of four Strings.
     *
     * @param name the name of the Jive property.
     * @return an array representation of the given Jive property.
     */
    private String[] parsePropertyName(String name) {
        List<String> propName = new ArrayList<>(5);
        // Use a StringTokenizer to tokenize the property name.
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        while (tokenizer.hasMoreTokens()) {
            propName.add(tokenizer.nextToken());
        }
        return propName.toArray(new String[0]);
    }

    public void setProperties(Map<String, String> propertyMap) {
        for (String propertyName : propertyMap.keySet()) {
            String propertyValue = propertyMap.get(propertyName);
            setProperty(propertyName, propertyValue);
        }
    }
}
