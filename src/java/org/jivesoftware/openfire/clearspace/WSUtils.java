/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.clearspace;

import org.dom4j.Element;
import org.dom4j.Node;
import org.xmpp.packet.JID;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Several utilities to handle REST webservices.
 */
public class WSUtils {

    /*
     * Date formats to parse and format REST dates. There are two types, with and without milliseconds
     */
    private static final SimpleDateFormat dateFormatMil = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final SimpleDateFormat dateFormatNoMil = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /**
     * Returns the text of the first an element with name 'return'.
     *
     * @param element the element to search for a return element.
     * @return the text of the return element.
     */
    protected static String getReturn(Element element) {
        return getElementText(element, "return");
    }

    /**
     * Returns the text of the first an element with name 'name'.
     *
     * @param node the element to search for a "name" element.
     * @param name the name of the element to search
     * @return the text of the corresponding element
     */
    protected static String getElementText(Node node, String name) {
        Node n = node.selectSingleNode(name);
        if (n != null) {
            return n.getText();
        }
        return null;
    }

    /**
     * Modifies the text of the elmement with name 'name'.
     *
     * @param node     the element to search
     * @param name     the name to search
     * @param newValue the new value of the text
     */
    protected static void modifyElementText(Node node, String name, String newValue) {
        Node n = node.selectSingleNode(name);
        n.setText(newValue);
    }

    protected static void modifyElementText(Element element, String[] path, String newValue) {
        Element e = element;
        for (String s : path) {
            Element subElement = e.element(s);
            if (subElement == null) {
                // Add the element if associated string was not in path.
                subElement = e.addElement(s);
            }
            e = subElement;
        }
        e.setText(newValue);
    }


    /**
     * Parse REST responses of the type String[], that are XML of the form:
     * <p/>
     * <something>
     * <return>text1</return>
     * <return>text2</return>
     * <return>text3</return>
     * </something>
     *
     * @param element Element from REST response to be parsed.
     * @return An array of strings from the REST response.
     */
    protected static List<String> parseStringArray(Element element) {
        List<String> list = new ArrayList<String>();
        @SuppressWarnings("unchecked")
        List<Node> nodes = (List<Node>) element.selectNodes("return");
        for (Node node : nodes) {
            list.add(node.getText());
        }
        return list;
    }

    /**
     * Parse REST responses of the type String[] that represent usernames, that are XML of the form:
     * <p/>
     * <something>
     * <return>text1</return>
     * <return>text2</return>
     * <return>text3</return>
     * </something>
     *
     * @param element Element from REST response to be parsed.
     * @return An array of strings from the REST response.
     */
    protected static List<String> parseUsernameArray(Element element) {
        List<String> list = new ArrayList<String>();
        @SuppressWarnings("unchecked")
        List<Node> nodes = (List<Node>) element.selectNodes("return");
        for (Node node : nodes) {
            String username = node.getText();
            // Escape username.
            username = JID.escapeNode(username);
            list.add(username);
        }
        return list;
    }

    protected static String marshallList(List<String> data) {
        String result = "";

        for (String s : data) {
            result += s + ",";
        }

        return result.substring(0, result.length() - 1);
    }

    /**
     * Parses a date of the form 1969-12-31T21:00:00-03:00, or 2008-02-13T18:54:29.147-03:00.
     * If the string is null or there is a problem parsing the date, returns null.
     *
     * @param date the string to parse
     * @return the corresponding date, or null if t
     */
    public static Date parseDate(String date) {
        if (date == null) {
            return null;
        }
        // REST writes dates time zone with ':', somthing like -3:00
        // to parse it they should be removed
        int index = date.lastIndexOf(":");
        date = date.substring(0, index) + date.substring(index + 1);
        Date d = null;
        try {
            if (date.length() == 24) {
                d = dateFormatNoMil.parse(date);
            } else {
                d = dateFormatMil.parse(date);
            }
        } catch (ParseException e) {
            // can't parse it, return null
        }
        return d;
    }

    /**
     * Formats a date into yyyy-MM-dd'T'HH:mm:ss.SSSZ, for example 2008-02-13T18:54:29.147-03:00
     *
     * @param date the date to format
     * @return a string representation of the date
     */
    public static String formatDate(Date date) {
        // REST writes dates time zone with ':', somthing like -3:00
        // to format it they should be added
        String d = dateFormatMil.format(date);
        d = d.substring(0, d.length() - 2) + ":" + d.substring(d.length() - 2);
        return d;
    }
}
