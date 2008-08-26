/**
 * $Revision: 243 $
 * $Date: 2004-11-09 10:37:52 -0800 (Tue, 09 Nov 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A utility class that provides methods that are useful for dealing with
 * Java Beans.
 */
public class BeanUtils {

    /**
     * The date format recognized for parsing/formattig dates.
     */
    public static final String DATE_FORMAT = "MM/dd/yyyy";

    private static DateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT);

    /**
     * Sets the properties of a Java Bean based on the String name/value pairs in
     * the specifieed Map. Because this method has to know how to convert a
     * String value into the correct type for the bean, only a few bean property
     * types are supported. They are: String, boolean, int, long, float, double,
     * Color, and Class.<p>
     *
     * If key/value pairs exist in the Map that don't correspond to properties
     * of the bean, they will be ignored.
     *
     * @param bean the JavaBean to set properties on.
     * @param properties String name/value pairs of the properties to set.
     */
    public static void setProperties(Object bean, Map<String, String> properties) {
        try {
            // Loop through all the property names in the Map
            for (String propName : properties.keySet()) {
                try {
                    // Create a property descriptor for the named property. If
                    // the bean doesn't have the named property, an
                    // Introspection will be thrown.
                    PropertyDescriptor descriptor = new PropertyDescriptor(
                            propName, bean.getClass());
                    // Load the class type of the property.
                    Class propertyType = descriptor.getPropertyType();
                    // Get the value of the property by converting it from a
                    // String to the correct object type.
                    Object value = decode(propertyType, properties.get(propName));
                    // Set the value of the bean.
                    descriptor.getWriteMethod().invoke(bean, value);
                }
                catch (IntrospectionException ie) {
                    // Ignore. This exception means that the key in the map
                    // does not correspond to a property of the bean.
                }
                catch (InvocationTargetException ite) {
                    // Ignore. This exception most often occurs when a
                    // value in the map is null and the target method doesn't
                    // support null properties.
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Sets the properties of a Java Bean based on the request's properties. Because
     * this method has to know how to convert a String value into the correct type
     * for the bean, only a few bean property types are supported. They are: String,
     * boolean, int, long, float, double, Color, and Class.<p>
     *
     * If key/value pairs exist in the Map that don't correspond to properties
     * of the bean, they will be ignored.
     *
     * @param bean the JavaBean to set properties on.
     * @param request the HTTP request.
     */
    public static void setProperties(Object bean, HttpServletRequest request) {
        for (Enumeration propNames = request.getParameterNames(); propNames.hasMoreElements();) {
            String propName = (String) propNames.nextElement();
            try {
                // Create a property descriptor for the named property. If
                // the bean doesn't have the named property, an
                // Introspection will be thrown.
                PropertyDescriptor descriptor = new PropertyDescriptor(
                        propName, bean.getClass());
                // Load the class type of the property.
                Class propertyType = descriptor.getPropertyType();
                // Get the value of the property by converting it from a
                // String to the correct object type.
                Object value = decode(propertyType, request.getParameter(propName));
                // Set the value of the bean.
                descriptor.getWriteMethod().invoke(bean, value);
            }
            catch (IntrospectionException ie) {
                // Ignore. This exception means that the key in the map
                // does not correspond to a property of the bean.
            }
            catch (InvocationTargetException ite) {
                // Ignore. This exception most often occurs when a
                // value in the map is null and the target method doesn't
                // support null properties.
            }
            catch (IllegalAccessException e) {
                Log.error(e);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * Gets the properties from a Java Bean and returns them in a Map of String
     * name/value pairs. Because this method has to know how to convert a
     * bean property into a String value, only a few bean property
     * types are supported. They are: String, boolean, int, long, float, double,
     * Color, and Class.
     *
     * @param bean a Java Bean to get properties from.
     * @return a Map of all properties as String name/value pairs.
     */
    public static Map<String, String> getProperties(Object bean) {
        Map<String, String> properties = new HashMap<String, String>();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
            // Loop through all properties of the bean.
            PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
            String [] names = new String[descriptors.length];
            for (int i=0; i<names.length; i++) {
                // Determine the property name.
                String name = descriptors[i].getName();
                //Class type = descriptors[i].getPropertyType();
                // Decode the property value using the property type and
                // encoded String value.
                Object value = descriptors[i].getReadMethod().invoke(bean,(java.lang.Object[]) null);
                // Add to Map, encoding the value as a String.
                properties.put(name, encode(value));
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        return properties;
    }

    /**
     * Returns the PropertyDescriptor array for the specified Java Bean Class.
     * The method also does a special check to see of the bean has a BeanInfo
     * class that extends the JiveBeanInfo class. If yes, we load the
     * PropertyDescriptor array directly from that BeanInfo class rather than
     * through the Introspector in order to preserve the desired ordering of
     * properties.
     *
     * @param beanClass the Class of the JavaBean.
     * @return the PropertyDescriptor array for the specified Java Bean Class.
     * @throws java.beans.IntrospectionException
     */
    public static PropertyDescriptor[] getPropertyDescriptors(Class beanClass)
            throws IntrospectionException
    {
        // See if the Java Bean has a BeanInfo class that implements
        // JiveBeanInfo. If so, return the PropertyDescriptor from that
        // class. This will bypass properties of parent classes, but this is
        // the normal behavior of classes that implement JiveBeanInfo.
        try {
            JiveBeanInfo beanInfo = (JiveBeanInfo)ClassUtils.forName(
                    beanClass.getName() + "BeanInfo").newInstance();
            return beanInfo.getPropertyDescriptors();
        }
        catch (Exception e) {
            // Ignore.
        }
        // Otherwise, return the PropertyDescriptors from the Introspector.
        return Introspector.getBeanInfo(beanClass).getPropertyDescriptors();
    }

    /**
     * Encodes a bean property value as a String. If the object type is not
     * supported, null will be returned.
     *
     * @param value an Object to encode in a String representation.
     * @return the encoded bean.
     */
    private static String encode(Object value) {
        if (value instanceof String) {
            return (String)value;
        }
        if (value instanceof Boolean ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Float ||
            value instanceof Double)
        {
            return value.toString();
        }
        if (value instanceof Date) {
            try {
                return dateFormatter.format((Date)value);
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        if (value instanceof Color) {
            Color color = (Color)value;
            return color.getRed() +","+ color.getGreen() +","+ color.getBlue();
        }
        if (value instanceof Class) {
            return ((Class)value).getName();
        }
        return null;
    }

    /**
     * Decodes a String into an object of the specified type. If the object
     * type is not supported, null will be returned.
     *
     * @param type the type of the property.
     * @param value the encode String value to decode.
     * @return the String value decoded into the specified type.
     * @throws Exception
     */
    private static Object decode(Class type, String value) throws Exception {
        if (type.getName().equals("java.lang.String")) {
            return value;
        }
        if (type.getName().equals("boolean")) {
            return Boolean.valueOf(value);
        }
        if (type.getName().equals("int")) {
            return Integer.valueOf(value);
        }
        if (type.getName().equals("long")) {
            return Long.valueOf(value);
        }
        if (type.getName().equals("float")) {
            return Float.valueOf(value);
        }
        if (type.getName().equals("double")) {
            return Double.valueOf(value);
        }
        if (type.getName().equals("java.util.Date")) {
            try {
                return dateFormatter.parse(value);
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        if (type.getName().equals("java.awt.Color")) {
            StringTokenizer tokens = new StringTokenizer(value, ",");
            int red = Integer.parseInt(tokens.nextToken());
            int green = Integer.parseInt(tokens.nextToken());
            int blue = Integer.parseInt(tokens.nextToken());
            return new Color(red, green, blue);
        }
        if (type.getName().equals("java.lang.Class")) {
            return ClassUtils.forName(value);
        }
        return null;
    }

    // This class is not instantiable.
    private BeanUtils() {
        // do nothing.
    }
}