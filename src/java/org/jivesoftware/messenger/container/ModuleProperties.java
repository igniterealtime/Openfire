/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container;

import java.util.Iterator;

/**
 * A way of passing context information between the container and
 * a module. This allows all modules to gather important information
 * regarding their environment.
 * <p/>
 * Properties can by any arbitrary key/value String pair. However,
 * we strongly suggest that you use dots to separate attribute groups
 * using the format: &quot;parent.child.propName&quot; although this is not required.
 * The default property save/load implementation will store these
 * values in an XML file with the format:
 * <pre>
 * <p/>
 * &lt;parent&gt;
 *   &lt;child&gt;
 *     &lt;propName&gt;
 *       propValue
 *     &lt;/propName&gt;
 *   &lt;/child&gt;
 * &lt;/parent&gt;
 * <p/>
 * </pre>
 *
 * @author Iain Shigeoka
 */
public interface ModuleProperties {

    /**
     * Returns a Jive property. Jive properties are stored in the file
     * <tt>jive_config.xml</tt> that exists in the <tt>jiveHome</tt> directory.
     * Properties are always specified as "foo.bar.prop", which would map to
     * the following entry in the XML file:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name the name of the property to return.
     * @return the property value specified by name.
     */
    String getProperty(String name);

    /**
     * Return all values who's path matches the given property name as a String array,
     * or an empty array if the if there are no children. This allows you to retrieve
     * several values with the same property name.
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
    String[] getProperties(String name);

    /**
     * <p>Creates a ModuleProperties object as a new child element with the given
     * root node name.</p>
     * <p/>
     * <p>This method provides a convenient method of adding a new item to a series
     * of parallel properties. For example if there is an existing xml tree:</p>
     * <pre>
     * <a>
     *   <b>item-data</b>
     * </a>
     * </pre>
     * <p>And the following code is called:
     * <pre><code>
     * ModuleProperties childProp = parentProp.createChildProperty("a.b");
     * childProp.setProperty("b.c","foo");
     * </code></pre>
     * <p/>
     * <p>The resulting XML will look like:</p>
     * <pre>
     * <a>
     *   <b>item-data</b>
     *   <b>
     *     <c>foo</c>
     *   </b>
     * </a>
     * </pre>
     *
     * @param name the property name of the root of the new ModuleProperties object
     * @return The ModuleProperties object created or null if no property matched the given name
     */
    ModuleProperties createChildProperty(String name);

    /**
     * Return an iterator of ModuleProperties objects (possibly empty)
     * that are rooted in the given property name. Each sub-property object
     * represents a matching child element within the given root element.
     * This allows you to retrieve several complex property 'trees' with
     * the same property name. For example, consider the XML file entry:
     * <pre>
     * <a>
     *   <b>
     *     <c>
     *       <d>d-one</d>
     *       <e>e-one</e>
     *       <f>
     *         <g>g-one</g>
     *       </f>
     *     </c>
     *     <c>
     *       <d>d-two</d>
     *       <e>e-two</e>
     *       <f>
     *         <g>g-two</g>
     *       </f>
     *     </c>
     *     <c>
     *       <d>d-three</d>
     *       <e>e-three</e>
     *       <f>
     *         <g>g-three</g>
     *       </f>
     *     </c>
     *   </b>
     * </a>
     * </pre>
     * <p>If you call getChildProperties("a.b.c") you will receieve an Iterator
     * containing three ModuleProperties. If you call getProperty("c.f.g") on
     * the first ModuleProperties you'll receive "g-one", and the same call
     * on the second ModuleProperties in the Iterator will return "g-two".</p>
     * <p/>
     * <p>Note that the child ModuleProperties are linked to their parent property.
     * Changes to child ModuleProperties are automatically reflected in the parent
     * ModuleProperty. Deleting or changing the parent ModuleProperty can cause
     * child ModuleProperties to become invalid or detached from the parent.
     * Care must be taken not to acces child properties after they have been
     * deleted at the parent level.</p>
     *
     * @param name the name of the property to retrieve
     * @return Iterator of ModuleProperties objects that have roots at the given name.
     *         The child ModuleProperties objects will have the last element name as their
     *         new root property name.
     */
    Iterator getChildProperties(String name);

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
     * @param name   the name of the property.
     * @param values The array of values for the property (can be empty but not null)
     */
    void setProperties(String name, String[] values);

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created. Jive properties are stored in the file
     * <tt>jive_config.xml</tt> that exists in the <tt>jiveHome</tt> directory.
     * Properties are always specified as "foo.bar.prop", which would map to
     * the following entry in the XML file:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name  the name of the property being set.
     * @param value the value of the property being set.
     */
    void setProperty(String name, String value);

    /**
     * Deletes a Jive property. If the property doesn't exist, the method
     * does nothing.
     *
     * @param name the name of the property to delete.
     */
    void deleteProperty(String name);
}
