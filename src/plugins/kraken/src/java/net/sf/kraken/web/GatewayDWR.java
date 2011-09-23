/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.web;

import java.io.IOException;

import uk.ltd.getahead.dwr.DWRServlet;
import uk.ltd.getahead.dwr.Configuration;
import uk.ltd.getahead.dwr.impl.DefaultInterfaceProcessor;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.log4j.Logger;

/**
 * IM Gateway DWR servlet
 *
 * Handles DWR configuration/etc for AJAX interaction.
 *
 * @author Daniel Henninger
 */
@SuppressWarnings("serial")
public class GatewayDWR extends DWRServlet {

    static Logger Log = Logger.getLogger(GatewayDWR.class);

    private Document document;

    @Override
    public void configure(ServletConfig servletConfig, Configuration configuration) throws ServletException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            document = builder.newDocument();
            Element root = document.createElement("dwr");
            document.appendChild(root);
            Element allowElement = document.createElement("allow");

            allowElement.appendChild(buildCreator("ConfigManager", "net.sf.kraken.web.ConfigManager"));
            allowElement.appendChild(buildCreator("ConnectionTester", "net.sf.kraken.web.ConnectionTester"));

            root.appendChild(allowElement);
        }
        catch (ParserConfigurationException e) {
            Log.error("Error configuring DWR for gateway plugin: ", e);
        }

        configuration.addConfig(document);
        
        // Specify the path for the js files 
        Object bean = container.getBean("interface");
        if (bean instanceof DefaultInterfaceProcessor) {
            DefaultInterfaceProcessor processor = (DefaultInterfaceProcessor)bean;
            processor.setOverridePath("/plugins/kraken/dwr");
        }
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
    
    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
	    throws IOException, ServletException {

		super.doPost(new MyServletRequestWrapper(httpServletRequest), httpServletResponse);
	}
    
    /**
     * Custom HTTP request wrapper that overrides the path to use
     */
    private static class MyServletRequestWrapper extends HttpServletRequestWrapper {
        public MyServletRequestWrapper(HttpServletRequest httpServletRequest) {
            super(httpServletRequest);
        }

        @Override
        public String getPathInfo() {
            String pathInfo = super.getPathInfo();
            return pathInfo.replaceAll("/kraken/dwr", "");
        }
    }

}
