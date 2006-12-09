/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.web;

import uk.ltd.getahead.dwr.DWRServlet;
import uk.ltd.getahead.dwr.Configuration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.jivesoftware.util.Log;

/**
 * IM Gateway DWR servlet
 *
 * Handles DWR configuration/etc for AJAX interaction.
 *
 * @author Daniel Henninger
 */
public class GatewayDWR extends DWRServlet {

    private Document document;

    public void configure(ServletConfig servletConfig, Configuration configuration) throws ServletException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            document = builder.newDocument();
            Element root = document.createElement("dwr");
            document.appendChild(root);
            Element allowElement = document.createElement("allow");

            Element timElement = buildCreator("ConfigManager", "org.jivesoftware.wildfire.gateway.web.ConfigManager");
            allowElement.appendChild(timElement);

            root.appendChild(allowElement);
        }
        catch (ParserConfigurationException e) {
            Log.error("Error configuring DWR for gateway plugin: ", e);
        }

        configuration.addConfig(document);
    }

    /**
     * Builds a create element within the DWR servlet.
     * @param javascriptID the javascript variable name to use.
     * @param qualifiedClassName the fully qualified class name.
     * @return the Element.
     */
    private Element buildCreator(String javascriptID, String qualifiedClassName) {
        Element element = document.createElement("create");
        element.setAttribute("creator", "new");
        element.setAttribute("javascript", javascriptID);
        Element parameter = document.createElement("param");
        parameter.setAttribute("name", "class");
        parameter.setAttribute("value", qualifiedClassName);
        element.appendChild(parameter);

        return element;
    }

}
