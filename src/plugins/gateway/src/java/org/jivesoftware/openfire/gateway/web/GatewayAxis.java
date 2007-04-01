/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.web;

import org.apache.axis.transport.http.AxisServlet;
import org.apache.axis.configuration.XMLStringProvider;
import org.apache.axis.AxisEngine;
import org.apache.axis.EngineConfiguration;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.jivesoftware.util.Log;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Daniel Henninger
 */
public class GatewayAxis extends AxisServlet {

    private static Document document;

    /*public void init() throws javax.servlet.ServletException {
        webInfPath = "../../gateway/WEB-INF";
        homeDir = "../../gateway/";

        System.out.println("webInfPath = "+webInfPath);
        System.out.println("homeDir = "+homeDir);
    }*/

    protected static Map getEngineEnvironment(HttpServlet servlet) {
        Map environment = new HashMap();

        System.out.println("'sup yo");

        ServletContext context = servlet.getServletContext();
        environment.put(AxisEngine.ENV_SERVLET_CONTEXT, context);

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            document = builder.newDocument();

            Element root = document.createElement("service");
            root.setAttribute("name", "ConnectionTester");
            root.setAttribute("provider", "java:RPC");

            Element parameter = document.createElement("parameter");
            parameter.setAttribute("name", "allowedMethods");
            parameter.setAttribute("value", "*");
            root.appendChild(parameter);

            parameter = document.createElement("parameter");
            parameter.setAttribute("name", "className");
            parameter.setAttribute("value", "org.jivesoftware.openfire.gateway.web.ConnectionTester");
            root.appendChild(parameter);

            document.appendChild(root);

            EngineConfiguration config = new XMLStringProvider(document.toString());
            environment.put(EngineConfiguration.PROPERTY_NAME, config);
        }
        catch (ParserConfigurationException e) {
            Log.error("Error configuring Axis for gateway plugin: ", e);
        }

        return environment;
    }

}
