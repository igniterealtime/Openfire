/**
 * $RCSfile$
 * $Revision: 19261 $
 * $Date: 2005-07-08 15:28:55 -0700 (Fri, 08 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.xmpp.workgroup.utils;

import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WorkgroupBeanInfo implements BeanInfo {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupBeanInfo.class);
	
    private ResourceBundle bundle;

    public WorkgroupBeanInfo() {
        List<String> bundleNames = new ArrayList<String>();
        String prefix = "bean_";
        // fully qualified class name: bean_com.foo.MyClass.properties
        bundleNames.add(prefix + getClass().toString());
        // just class name: bean_MyClass.properties
        bundleNames.add(prefix + getName().toString());
        //Get the locale that should be used, then load the resource bundle.
        Locale currentLocale = JiveGlobals.getLocale();
        for (int i = 0, n = bundleNames.size(); i < n; i++) {
            String name = bundleNames.get(i);
            try {
                // TODO - possibly use other class loaders?
                bundle = ResourceBundle.getBundle(name, currentLocale);
                break;
            }
            catch (Exception e) {
                // Ignore any exception when trying to load bundle.
            }
        }
    }

    /**
     * Returns the names of the properties of the bean that should be exposed.
     *
     * @return the names of the properties that should be exposed.
     */
    public abstract String[] getPropertyNames();

    /**
     * Returns the bean Class.
     *
     * @return the Class of the JavaBean that the BeanInfo is for.
     */
    public abstract Class getBeanClass();

    /**
     * Returns the name of the class that the bean info applies to (which
     * corresponds to the resource bundle that will be loaded). For example,
     * for the class <tt>com.foo.ExampleClass</tt>, the name would be
     * <tt>ExampleClass</tt>.
     *
     * @return the name of the JavaBean that the BeanInfo is for.
     */
    public abstract String getName();

    // BeanInfo Interface

    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor descriptor = new BeanDescriptor(getBeanClass());
        try {
            // Attempt to load the name, displayName and shortDescription explicitly.
            try {
                String name = bundle.getString("name");
                if (name != null) {
                    descriptor.setName(name);
                }
            }
            catch (MissingResourceException ignored) {
            }
            // Get the name
            try {
                String displayName = bundle.getString("displayName");
                if (displayName != null) {
                    descriptor.setDisplayName(displayName);
                }
            }
            catch (MissingResourceException ignored) {
            }
            // Get the short description
            try {
                String shortDescription = bundle.getString("shortDescription");
                if (shortDescription != null) {
                    descriptor.setShortDescription(shortDescription);
                }
            }
            catch (MissingResourceException ignored) {
            }
            // Get a large description field
            try {
                String description = bundle.getString("description");
                if (description != null) {
                    descriptor.setValue("description", description);
                }
            }
            catch (MissingResourceException ignored) {
            }
            // Add any other properties that are specified.
            Enumeration<String> e = bundle.getKeys();
            while (e.hasMoreElements()) {
                String key = e.nextElement();
                try {
                    String value = bundle.getString(key);
                    if (value != null) {
                        descriptor.setValue(key, value);
                    }
                }
                catch (MissingResourceException ignored) {
                }
            }
        }
        catch (Exception e) {
            // Ignore any exceptions. We may get some if we try to load a
            // a property that doesn't appear in the resource bundle.
        }
        return descriptor;
    }

    public PropertyDescriptor[] getPropertyDescriptors() {
        Class beanClass = getBeanClass();
        String[] properties = getPropertyNames();
        PropertyDescriptor[] descriptors = new PropertyDescriptor[properties.length];
        try {
            // For each property, create a property descriptor and set the
            // name and description using the localized data.
            for (int i = 0; i < descriptors.length; i++) {
                PropertyDescriptor newDescriptor =
                        new PropertyDescriptor(properties[i], beanClass);
                if (bundle != null) {
                    try {
                        newDescriptor.setDisplayName(bundle.getString(properties[i] + ".displayName"));
                    }
                    catch (MissingResourceException ignored) {
                    }
                    try {
                        newDescriptor.setShortDescription(bundle.getString(properties[i] + ".shortDescription"));
                    }
                    catch (MissingResourceException ignored) {
                    }
                    // Check to see if the property should be a large text field. This
                    // is a hint to the GUI saying that a large text field should be
                    // used to set this value.
                    try {
                        String largeText = bundle.getString(properties[i] + ".useLargeTextField");
                        if ("true".equals(largeText)) {
                            newDescriptor.setValue("useLargeTextField", "true");
                        }
                    }
                    catch (MissingResourceException ignored) {
                    }
                }
                descriptors[i] = newDescriptor;
            }

            return descriptors;
        }
        catch (IntrospectionException ie) {
            Log.error(ie.getMessage(), ie);
            throw new Error(ie.toString());
        }
    }

    public int getDefaultPropertyIndex() {
        return -1;
    }

    public EventSetDescriptor[] getEventSetDescriptors() {
        return null;
    }

    public int getDefaultEventIndex() {
        return -1;
    }

    public MethodDescriptor[] getMethodDescriptors() {
        return null;
    }

    public BeanInfo[] getAdditionalBeanInfo() {
        return null;
    }

    public java.awt.Image getIcon(int iconKind) {
        return null;
    }
}