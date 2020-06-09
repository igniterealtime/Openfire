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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/**
 * Assists JSP writers in getting parameters and attributes.
 */
public class ParamUtils {

    private static final Logger Log = LoggerFactory.getLogger(ParamUtils.class);

    /**
     * Returns a parameter as a string.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @return the value of the parameter or null if the parameter was not
     *      found or if the parameter is a zero-length string.
     */
    public static String getParameter(HttpServletRequest request, String name) {
        return getParameter(request, name, false);
    }

    /**
     * Returns a parameter as a string.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param emptyStringsOK return the parameter values even if it is an empty string.
     * @return the value of the parameter or null if the parameter was not
     *      found.
     */
    public static String getParameter(HttpServletRequest request, String name,
            boolean emptyStringsOK)
    {
        String temp = request.getParameter(name);
        if (temp != null) {
            if (temp.equals("") && !emptyStringsOK) {
                return null;
            }
            else {
                return temp;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Returns a list of parameters of the same name
     *
     * @param request an HttpServletRequest object.
     * @param name the name of the parameter you want to get
     * @return an array of non-null, non-blank strings of the same name. This
     *         method will return an empty array if no parameters were found.
     */
    public static String[] getParameters(HttpServletRequest request, String name) {
        if (name == null) {
            return new String[0];
        }
        String[] paramValues = request.getParameterValues(name);
        if (paramValues == null || paramValues.length == 0) {
            return new String[0];
        }
        else {
            java.util.List<String> values = new java.util.ArrayList<>(paramValues.length);
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] != null && !"".equals(paramValues[i])) {
                    values.add(paramValues[i]);
                }
            }
            return values.toArray(new String[]{});
        }
    }

    /**
     * Returns a parameter as a String.
     *
     * @param request    the HttpServletRequest object, known as "request" in a
     *                   JSP page.
     * @param name       the name of the parameter you want to get
     * @param defaultVal the value to use if the supplied parameter is not present or is empty
     * @return either the value of parameter in the request, or the defaultVal
     */
    public static String getStringParameter(
        final HttpServletRequest request,
        final String name,
        final String defaultVal) {
        final String parameterValue = request.getParameter(name);
        if (parameterValue == null || parameterValue.isEmpty()) {
            return defaultVal;
        } else {
            return parameterValue;
        }
    }

    /**
     * Returns a parameter as a boolean.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @return true if the value of the parameter was "true", false otherwise.
     */
    public static boolean getBooleanParameter(HttpServletRequest request, String name) {
        return getBooleanParameter(request, name, false);
    }

    /**
     * Returns a parameter as a boolean.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param defaultVal the default value if the parameter is not present
     * @return true if the value of the parameter was "true", false otherwise.
     */
    public static boolean getBooleanParameter(HttpServletRequest request,
            String name, boolean defaultVal)
    {
        String temp = request.getParameter(name);
        if ("true".equals(temp) || "on".equals(temp)) {
            return true;
        }
        else if ("false".equals(temp) || "off".equals(temp)) {
            return false;
        }
        else {
            return defaultVal;
        }
    }

    /**
     * Returns a parameter as an int.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param defaultNum the default value if the parameter is not present/could not be parsed
     * @return the int value of the parameter specified or the default value if
     *      the parameter is not found.
     */
    public static int getIntParameter(HttpServletRequest request,
                                      String name, int defaultNum) {
        String temp = request.getParameter(name);
        if (temp != null && !temp.equals("")) {
            int num = defaultNum;
            try {
                num = Integer.parseInt(temp);
            }
            catch (Exception ex) {
                Log.trace("An exception occurred while trying to parse parameter '{}' value '{}' as an integer. Will return default value '{}'", name, temp, defaultNum, ex);
            }
            return num;
        }
        else {
            return defaultNum;
        }
    }

    /**
     * Returns a list of int parameters.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param defaultNum the default value of a parameter, if the parameter
     *      can't be converted into an int.
     * @return an array of integers
     */
    public static int[] getIntParameters(HttpServletRequest request,
                                         String name, int defaultNum) {
        String[] paramValues = request.getParameterValues(name);
        if (paramValues == null || paramValues.length == 0) {
            return new int[0];
        }
        int[] values = new int[paramValues.length];
        for (int i = 0; i < paramValues.length; i++) {
            try {
                values[i] = Integer.parseInt(paramValues[i]);
            }
            catch (Exception e) {
                values[i] = defaultNum;
            }
        }
        return values;
    }

    /**
     * Returns a parameter as a double.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param defaultNum the default value if the parameter is not present/could not be parsed
     * @return the double value of the parameter specified or the default value
     *      if the parameter is not found.
     */
    public static double getDoubleParameter(HttpServletRequest request, String name, double defaultNum) {
        String temp = request.getParameter(name);
        if (temp != null && !temp.equals("")) {
            double num = defaultNum;
            try {
                num = Double.parseDouble(temp);
            }
            catch (Exception ex) {
                Log.trace("An exception occurred while trying to parse parameter '{}' value '{}' as a double. Will return default value '{}'", name, temp, defaultNum, ex);
            }
            return num;
        }
        else {
            return defaultNum;
        }
    }

    /**
     * Returns a parameter as a long.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param defaultNum the default value of a parameter, if the parameter
     *      can't be converted into a long.
     * @return the long value of the parameter specified or the default value if
     *      the parameter is not found.
     */
    public static long getLongParameter(HttpServletRequest request, String name, long defaultNum) {
        String temp = request.getParameter(name);
        if (temp != null && !temp.equals("")) {
            long num = defaultNum;
            try {
                num = Long.parseLong(temp);
            }
            catch (Exception ex) {
                Log.trace("An exception occurred while trying to parse parameter '{}' value '{}' as a long. Will return default value '{}'", name, temp, defaultNum, ex);
            }
            return num;
        }
        else {
            return defaultNum;
        }
    }

    /**
     * Returns a list of long parameters.
     *
     * @param request the HttpServletRequest object, known as "request" in a
     *      JSP page.
     * @param name the name of the parameter you want to get
     * @param defaultNum the default value of a parameter, if the parameter
     *      can't be converted into a long.
     * @return an array of long parameters
     */
    public static long[] getLongParameters(HttpServletRequest request, String name,
            long defaultNum)
    {
        String[] paramValues = request.getParameterValues(name);
        if (paramValues == null || paramValues.length == 0) {
            return new long[0];
        }
        long[] values = new long[paramValues.length];
        for (int i = 0; i < paramValues.length; i++) {
            try {
                values[i] = Long.parseLong(paramValues[i]);
            }
            catch (Exception e) {
                values[i] = defaultNum;
            }
        }
        return values;
    }

    /**
     * Returns an attribute as a string.
     *
     * @param request the HttpServletRequest object, known as "request" in a JSP page.
     * @param name the name of the parameter you want to get
     * @return the value of the parameter or null if the parameter was not
     *      found or if the parameter is a zero-length string.
     */
    public static String getAttribute(HttpServletRequest request, String name) {
        return getAttribute(request, name, false);
    }

    /**
     * Returns an attribute as a string.
     *
     * @param request the HttpServletRequest object, known as "request" in a JSP page.
     * @param name the name of the parameter you want to get.
     * @param emptyStringsOK return the parameter values even if it is an empty string.
     * @return the value of the parameter or null if the parameter was not
     *      found.
     */
    public static String getAttribute(HttpServletRequest request, String name,
            boolean emptyStringsOK)
    {
        String temp = (String)request.getAttribute(name);
        if (temp != null) {
            if (temp.equals("") && !emptyStringsOK) {
                return null;
            }
            else {
                return temp;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Returns an attribute as a boolean.
     *
     * @param request the HttpServletRequest object, known as "request" in a JSP page.
     * @param name the name of the attribute you want to get.
     * @return true if the value of the attribute is "true", false otherwise.
     */
    public static boolean getBooleanAttribute(HttpServletRequest request, String name) {
        String temp = (String)request.getAttribute(name);
        if (temp != null && temp.equals("true")) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Returns an attribute as a int.
     *
     * @param request the HttpServletRequest object, known as "request" in a JSP page.
     * @param name the name of the attribute you want to get.
     * @param defaultNum the default value if the attribute is not present/cannot be parsed
     * @return the int value of the attribute or the default value if the
     *      attribute is not found or is a zero length string.
     */
    public static int getIntAttribute(HttpServletRequest request, String name, int defaultNum) {
        String temp = (String)request.getAttribute(name);
        if (temp != null && !temp.equals("")) {
            int num = defaultNum;
            try {
                num = Integer.parseInt(temp);
            }
            catch (Exception ex) {
                Log.trace("An exception occurred while trying to parse attribute '{}' value '{}' as an integer. Will return default value '{}'", name, temp, defaultNum, ex);
            }
            return num;
        }
        else {
            return defaultNum;
        }
    }

    /**
     * Returns an attribute as a long.
     *
     * @param request the HttpServletRequest object, known as "request" in a JSP page.
     * @param name the name of the attribute you want to get.
     * @param defaultNum the default value if the attribute is not present/cannot be parsed
     * @return the long value of the attribute or the default value if the
     *      attribute is not found or is a zero length string.
     */
    public static long getLongAttribute(HttpServletRequest request, String name, long defaultNum) {
        String temp = (String)request.getAttribute(name);
        if (temp != null && !temp.equals("")) {
            long num = defaultNum;
            try {
                num = Long.parseLong(temp);
            }
            catch (Exception ex) {
                Log.trace("An exception occurred while trying to parse attribute '{}' value '{}' as a long. Will return default value '{}'", name, temp, defaultNum, ex);
            }
            return num;
        }
        else {
            return defaultNum;
        }
    }
}
