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

import javax.servlet.http.HttpServletRequest;

/**
 * Assists JSP writers in getting parameters and attributes.
 */
public class ParamUtils {

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
            java.util.List values = new java.util.ArrayList(paramValues.length);
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] != null && !"".equals(paramValues[i])) {
                    values.add(paramValues[i]);
                }
            }
            return (String[])values.toArray(new String[]{});
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
            catch (Exception ignored) {
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
            catch (Exception ignored) {
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
            catch (Exception ignored) {
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
            catch (Exception ignored) {
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
            catch (Exception ignored) {
            }
            return num;
        }
        else {
            return defaultNum;
        }
    }
}