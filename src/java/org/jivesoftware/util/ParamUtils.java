/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.util;

import javax.servlet.http.HttpServletRequest;

/**
 * This class assists skin writers in getting parameters.
 */
public class ParamUtils {

    /**
     * Gets a parameter as a string.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return The value of the parameter or null if the parameter was not
     *         found or if the parameter is a zero-length string.
     */
    public static String getParameter(HttpServletRequest request, String name) {
        return getParameter(request, name, false);
    }

    /**
     * Gets a parameter as a string.
     *
     * @param request        The HttpServletRequest object, known as "request" in a
     *                       JSP page.
     * @param name           The name of the parameter you want to get
     * @param emptyStringsOK Return the parameter values even if it is an empty string.
     * @return The value of the parameter or null if the parameter was not
     *         found.
     */
    public static String getParameter(HttpServletRequest request,
                                      String name, boolean emptyStringsOK) {
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
     * Gets a parameter as a boolean.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return True if the value of the parameter was "true", false otherwise.
     */
    public static boolean getBooleanParameter(HttpServletRequest request,
                                              String name) {
        return getBooleanParameter(request, name, false);
    }

    /**
     * Gets a parameter as a boolean.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return True if the value of the parameter was "true", false otherwise.
     */
    public static boolean getBooleanParameter(HttpServletRequest request,
                                              String name, boolean defaultVal) {
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
     * Gets a parameter as an int.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return The int value of the parameter specified or the default value if
     *         the parameter is not found.
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
     * Gets a list of int parameters.
     *
     * @param request    The HttpServletRequest object, known as "request" in a
     *                   JSP page.
     * @param name       The name of the parameter you want to get
     * @param defaultNum The default value of a parameter, if the parameter
     *                   can't be converted into an int.
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
     * Gets a parameter as a double.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return The double value of the parameter specified or the default value
     *         if the parameter is not found.
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
     * Gets a parameter as a long.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return The long value of the parameter specified or the default value if
     *         the parameter is not found.
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
     * Gets a list of long parameters.
     *
     * @param request    The HttpServletRequest object, known as "request" in a
     *                   JSP page.
     * @param name       The name of the parameter you want to get
     * @param defaultNum The default value of a parameter, if the parameter
     *                   can't be converted into a long.
     */
    public static long[] getLongParameters(HttpServletRequest request,
                                           String name, long defaultNum) {
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
     * Gets a parameter as a string.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the parameter you want to get
     * @return The value of the parameter or null if the parameter was not
     *         found or if the parameter is a zero-length string.
     */
    public static String getAttribute(HttpServletRequest request, String name) {
        return getAttribute(request, name, false);
    }

    /**
     * Gets a parameter as a string.
     *
     * @param request        The HttpServletRequest object, known as "request" in a
     *                       JSP page.
     * @param name           The name of the parameter you want to get
     * @param emptyStringsOK Return the parameter values even if it is an empty string.
     * @return The value of the parameter or null if the parameter was not
     *         found.
     */
    public static String getAttribute(HttpServletRequest request,
                                      String name, boolean emptyStringsOK) {
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
     * Gets an attribute as a boolean.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the attribute you want to get
     * @return True if the value of the attribute is "true", false otherwise.
     */
    public static boolean getBooleanAttribute(HttpServletRequest request,
                                              String name) {
        String temp = (String)request.getAttribute(name);
        if (temp != null && temp.equals("true")) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Gets an attribute as a int.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the attribute you want to get
     * @return The int value of the attribute or the default value if the
     *         attribute is not found or is a zero length string.
     */
    public static int getIntAttribute(HttpServletRequest request,
                                      String name, int defaultNum) {
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
     * Gets an attribute as a long.
     *
     * @param request The HttpServletRequest object, known as "request" in a
     *                JSP page.
     * @param name    The name of the attribute you want to get
     * @return The long value of the attribute or the default value if the
     *         attribute is not found or is a zero length string.
     */
    public static long getLongAttribute(HttpServletRequest request,
                                        String name, long defaultNum) {
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