/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
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

import java.beans.*;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * An abstract BeanInfo implementation that automatically constructs
 * PropertyDescriptors and handles i18n through ResourceBundles.
 *
 * @author Jive Software
 * @see java.beans.BeanInfo
 */
public abstract class JiveBeanInfo implements BeanInfo {

    private ResourceBundle bundle;

    public JiveBeanInfo() {
        //Get the locale that should be used, then load the resource bundle.
        Locale currentLocale = JiveGlobals.getLocale();
        try {
            bundle = ResourceBundle.getBundle("bean_" + getName(),
                    currentLocale);
        }
        catch (Exception e) {
            // Ignore any exception when trying to load bundle.
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
            // Attempt to load the displayName and shortDescription explicitly.
            String displayName = bundle.getString("displayName");
            if (displayName != null) {
                descriptor.setDisplayName(displayName);
            }
            String shortDescription = bundle.getString("shortDescription");
            if (shortDescription != null) {
                descriptor.setShortDescription(shortDescription);
            }
            // Add any other properties that are specified.
            Enumeration enumeration = bundle.getKeys();
            while (enumeration.hasMoreElements()) {
                String key = (String)enumeration.nextElement();
                String value = bundle.getString(key);
                if (value != null) {
                    descriptor.setValue(key, value);
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
                    newDescriptor.setDisplayName(bundle.getString(properties[i] + ".displayName"));
                    newDescriptor.setShortDescription(bundle.getString(properties[i] + ".shortDescription"));
                }
                descriptors[i] = newDescriptor;
            }
            return descriptors;
        }
        catch (IntrospectionException ie) {
            Log.error(ie);
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