/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.messenger;

import org.jivesoftware.util.XPPWriter;
import java.util.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.*;

/**
 * <p>Storage of generic meta-data.</p>
 * <p>Meta-data is expected to be stored as XML. We use a simple
 * naming convention of meta-data key names: data is stored
 * heirarchically separated by dots. The last name may contain
 * a colon ':' character that is read as name:attribute.
 * For example setting X.Y.Z to someValue, would map to an XML snippet of:</p>
 * <pre>
 * &lt;X&gt;
 *     &lt;Y&gt;
 *         &lt;Z&gt;someValue&lt;/Z&gt;
 *     &lt;/Y&gt;
 * &lt;/X&gt;
 * </pre>
 * And X.Y.Z:key to anotherValue as:</p>
 * <pre>
 * &lt;X&gt;
 *     &lt;Y&gt;
 *         &lt;Z key="anotherValue" /&gt;
 *     &lt;/Y&gt;
 * &lt;/X&gt;
 * </pre>
 * <p>Some XML cannot be built or accessed using this naming
 * convention (e.g. a typical Roster reset packet). More complex XML
 * packet should be represented using the XMPPDOMFragment. The
 * MetaDataFragment class is designed to provide 80% of XML
 * manipulation capabilities with the simplest 20% of code and API size
 * making it convenient for meta-data, simple IQ packets, etc.</p>
 *
 * @author Iain Shigeoka
 * @see XMPPDOMFragment
 */
public class MetaDataFragment extends PayloadFragment {

    Element root = DocumentHelper.createElement("root");

    /**
     * Parsing the XML DOM every time we need a property is slow. Therefore,
     * we use a Map to cache property values that are accessed more than once.
     */
    private Map propertyCache = new HashMap();

    public void setName(String name) {
        this.name = name;
        ((Element)root.elements().get(0)).setName(name);
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
        QName qName = DocumentHelper.createQName(name, DocumentHelper.createNamespace("", namespace));
        ((Element)root.elements().get(0)).setQName(qName);
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
        Iterator fragIter = null;
        if (fragments != null && !fragments.isEmpty()) {
            fragIter = getFragments();
        }

        XPPWriter.write((Element)root.elements().get(0), xmlSerializer, fragIter, version);
    }

    public XMPPFragment createDeepCopy() {
        MetaDataFragment meta = null;

        Iterator metaElements = root.elementIterator();
        if (metaElements.hasNext()) {
            meta = new MetaDataFragment((Element)metaElements.next());
            while (metaElements.hasNext()) {
                meta.root.add((Element)metaElements.next());
            }
        }
        else {
            meta = new MetaDataFragment(namespace, name);
        }
        Iterator fragmentIter = getFragments();
        while (fragmentIter.hasNext()) {
            meta.addFragment(((XMPPFragment)fragmentIter.next()).createDeepCopy());
        }

        return meta;
    }

    /**
     * Create an empty meta-data chunk.
     */
    public MetaDataFragment(String namespace, String name) {
        super(namespace, name);
        QName qName = DocumentHelper.createQName(name, DocumentHelper.createNamespace("", namespace));
        root.add(DocumentHelper.createElement(qName));
    }

    /**
     * Create a meta-data chunk based on a DOM tree.
     */
    public MetaDataFragment(Element root) {
        super(root.getNamespaceURI(), root.getName());
        this.root.add((Element)root.clone());
    }

    /**
     * Returns the value of the specified property. A <tt>null</tt> answer does not necessarily mean
     * that the property does not exist. Use {@link #includesProperty(String)} to find out whether
     * the property exists or not.
     *
     * @param name the name of the property to get.
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name) {
        String value = (String)propertyCache.get(name);

        if (value == null) {
            String[] propName = parsePropertyName(name);

            // Grab the attribute if there is one
            String lastName = propName[propName.length - 1];
            String attName = null;
            int attributeIndex = lastName.indexOf(':');
            if (attributeIndex >= 0) {
                propName[propName.length - 1] = lastName.substring(0, attributeIndex);
                attName = lastName.substring(attributeIndex + 1);
            }

            // Search for this property by traversing down the XML hierarchy.
            Element element = root;
            for (int i = 0; i < propName.length; i++) {
                element = element.element(propName[i]);
                if (element == null) {
                    break;
                }
            }
            if (element != null) {
                if (attName == null) {
                    value = element.getTextTrim();
                }
                else {
                    value = element.attributeValue(attName);
                }

                // At this point, we found a matching property, so return its value.
                // Empty strings are returned as null.
                if ("".equals(value)) {
                    value = null;
                }
                else {
                    // Add to cache so that getting property next time is fast.
                    propertyCache.put(name, value);
                }
            }
        }
        return value;
    }

    /**
     * Returns true if the specified property is included in the XML hierarchy. A property could
     * have a value associated or not. If the property has an associated value then
     * {@link #getProperty(String)} will return a String otherwise <tt>null</tt> will be answered.
     *
     * @param name the name of the property to find out.
     * @return true if the specified property is included in the XML hierarchy.
     */
    public synchronized boolean includesProperty(String name) {
        String value = (String)propertyCache.get(name);

        if (value == null) {
            String[] propName = parsePropertyName(name);

            // Grab the attribute if there is one
            String lastName = propName[propName.length - 1];
            String attName = null;
            int attributeIndex = lastName.indexOf(':');
            if (attributeIndex >= 0) {
                propName[propName.length - 1] = lastName.substring(0, attributeIndex);
                attName = lastName.substring(attributeIndex + 1);
            }

            // Search for this property by traversing down the XML hierarchy.
            Element element = root;
            for (int i = 0; i < propName.length; i++) {
                element = element.element(propName[i]);
                if (element == null) {
                    break;
                }
            }

            if (element != null) {
                if (attName == null){
                    // The property exists so return true
                    return true;
                } else {
                    // The property exists if the attribute exists in the element
                    return element.attribute(attName) != null;
                }
            }
            else {
                // The property does not exist so return false
                return false;
            }
        }
        return true;
    }

    /**
     * Return all values who's path matches the given property name as a String array,
     * or an empty array if the if there are no children. You MAY NOT use the atttribute
     * markup (using a ':' in the last element name) with this call.
     * <p/>
     * getProperties() allows you to retrieve several values with the same property name.
     * For example, consider the XML file entry:
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
    public String[] getProperties(String name) {
        String[] propName = parsePropertyName(name);

        // Search for this property by traversing down the XML heirarchy, stopping one short.
        Element element = root;
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        Iterator iter = element.elementIterator(propName[propName.length - 1]);
        ArrayList props = new ArrayList();
        while (iter.hasNext()) {
            Element e = (Element)iter.next();
            props.add(e.getName());
        }
        String[] childrenNames = new String[props.size()];
        return (String[])props.toArray(childrenNames);
    }

    /**
     * Sets a property to an array of values.  You MAY NOT use the atttribute
     * markup (using a ':' in the last element name) with this call. Multiple values matching the
     * same property is mapped to an XML file as multiple elements containing each value.
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
     * @param name   the name of the property.
     * @param values The array of values for the property (can be empty but not null)
     */
    public void setProperties(String name, String[] values) {
        String[] propName = parsePropertyName(name);
        setProperty(name, values[0]);
        // Search for this property by traversing down the XML heirarchy, stopping one short.
        Element element = root;
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return;
            }
        }
        String childName = propName[propName.length - 1];
        // We found matching property, clear all children.
        List toRemove = new ArrayList();
        Iterator iter = element.elementIterator(childName);
        while (iter.hasNext()) {
            toRemove.add(iter.next());
        }
        for (iter = toRemove.iterator(); iter.hasNext();) {
            element.remove((Element)iter.next());
        }
        // Add the new children
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                element.addElement(childName).setText(values[i]);
            }
        }
    }

    /**
     * Return all children property names of a parent property as a String array,
     * or an empty array if the if there are no children. You MAY NOT use the atttribute
     * markup (using a ':' in the last element name) with this call.
     * For example, given the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>, then
     * the child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt>.
     *
     * @param parent the name of the parent property.
     * @return all child property values for the given parent.
     */
    public String[] getChildrenProperties(String parent) {
        String[] propName = parsePropertyName(parent);

        // Search for this property by traversing down the XML heirarchy.
        Element element = root;
        for (int i = 0; i < propName.length; i++) {
            element = element.element(propName[i]);
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
     * Returns all recursive children of the given parent property or an empty string array
     * if no children exist. The list of children is depth-first so the array is optimized
     * for easy displaying.
     *
     * @param parent the parent property.
     * @return all recursive children of the given property in depth-first order or an empty
     *         string array if no children exist.
     */
    public String[] getRecursiveChildrenProperties(String parent) {
        String[] properties = getChildrenProperties(parent);
        if (properties.length == 0) {
            return properties;
        }
        else {
            List list = new ArrayList(15);
            for (int i = 0; i < properties.length; i++) {
                String propName = parent + "." + properties[i];
                list.add(propName);
                list.addAll(Arrays.asList(getRecursiveChildrenProperties(propName)));
            }
            return (String[])list.toArray(new String[]{});
        }
    }

    /**
     * Sets the value of the specified property. If the property doesn't
     * currently exist, it will be automatically created.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     */
    public synchronized void setProperty(String name, String value) {
        if (name == null) return;
        if (value == null) value = "";

        // Set cache correctly with prop name and value.
        propertyCache.put(name, value);

        String[] propName = parsePropertyName(name);

        // Search for this property by traversing down the XML heirarchy.
        Element element = root;
        for (int i = 0; i < propName.length - 1; i++) {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        String lastName = propName[propName.length - 1];
        int attributeIndex = lastName.indexOf(':');
        if (attributeIndex >= 0) {
            String eleName = lastName.substring(0, attributeIndex);
            String attName = lastName.substring(attributeIndex + 1);
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(eleName) == null) {
                element.addElement(eleName);
            }
            element.element(eleName).addAttribute(attName, value);
        }
        else {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(lastName) == null) {
                element.addElement(lastName);
            }
            // Set the value of the property in this node.
            element.element(lastName).setText(value);
        }
    }

    /**
     * Deletes the specified property. You may use the atttribute markup (using a ':' in the last
     * element name) with this call. This method removes both the containing text, and the
     * element itself along with any attributes associated with that element.</p>
     *
     * @param name the property to delete.
     */
    public synchronized void deleteProperty(String name) {
        // Remove property from cache.
        propertyCache.remove(name);

        String[] propName = parsePropertyName(name);

        // Search for this property by traversing down the XML heirarchy.
        Element element = root;
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            // Can't find the property so return.
            if (element == null) {
                return;
            }
        }
        String lastName = propName[propName.length - 1];
        int attributeIndex = lastName.indexOf(':');
        if (attributeIndex >= 0) {
            String eleName = lastName.substring(0, attributeIndex);
            String attName = lastName.substring(attributeIndex + 1);
            Attribute attrib = element.element(eleName).attribute(attName);
            if (attrib != null) {
                element.element(eleName).remove(attrib);
            }
        }
        else {
            // Found the correct element to remove, so remove it...
            element.remove(element.element(lastName));
        }
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
        List propName = new ArrayList(5);
        // Use a StringTokenizer to tokenize the property name.
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        while (tokenizer.hasMoreTokens()) {
            propName.add(tokenizer.nextToken());
        }
        return (String[])propName.toArray(new String[propName.size()]);
    }

    /**
     * <p>Tries to convert any given fragment into a metadata fragment.</p>
     *
     * @param fragment The fragment to convert
     * @return The converted fragment or null if the fragment could not be converted
     */
    public static MetaDataFragment convertToMetaData(XMPPFragment fragment) {
        MetaDataFragment meta = null;
        if (fragment instanceof MetaDataFragment) {
            meta = (MetaDataFragment)fragment;
        }
        else if (fragment instanceof XMPPDOMFragment) {
            XMPPDOMFragment dom = (XMPPDOMFragment)fragment;
            meta = new MetaDataFragment(dom.getRootElement());
            Iterator frags = dom.getFragments();
            while (frags.hasNext()) {
                meta.addFragment((XMPPFragment)frags.next());
            }
        }
        else {
            // TODO: as a last resort, should read the fragment in using an empty serializer and rebuild the dom tree
            throw new IllegalArgumentException();
        }
        return meta;
    }

    /**
     * <p>Tries to convert this fragment into a metadata fragment.</p>
     *
     * @return The converted fragment
     */
    public XMPPDOMFragment convertToDOMFragment() {
        XMPPDOMFragment dom = new XMPPDOMFragment(root.createCopy());
        return dom;
    }
}
