/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.reporting;

import org.jivesoftware.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.ltd.getahead.dwr.Configuration;
import uk.ltd.getahead.dwr.DWRServlet;
import uk.ltd.getahead.dwr.impl.DefaultInterfaceProcessor;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Use the EnterpriseDWR servlet to register your own DWR mappings to Enteprise.
 */
public class MonitoringDWR extends DWRServlet {
    private Document document;

    public void configure(ServletConfig servletConfig, Configuration configuration) throws ServletException {

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            document = builder.newDocument();
            Element root = document.createElement("dwr");
            document.appendChild(root);

            Element allowElement = document.createElement("allow");

            // Build stats bean
            Element createElement = buildCreator("Stats", org.jivesoftware.openfire.reporting.stats.StatsAction.class.getName());

            Element convertConversationElement = document.createElement("convert");
            convertConversationElement.setAttribute("converter", "bean");
            convertConversationElement.setAttribute("match", org.jivesoftware.openfire.archive.ConversationInfo.class.getName());

            // Build conversation Element.
            Element conversationElement = buildCreator("conversations", org.jivesoftware.openfire.archive.ConversationUtils.class.getName());

            allowElement.appendChild(createElement);
            allowElement.appendChild(convertConversationElement);
            allowElement.appendChild(conversationElement);

            root.appendChild(allowElement);
        }
        catch (ParserConfigurationException e) {
            Log.error("error creating DWR configuration: " + e);
        }

        configuration.addConfig(document);

        // Specify the path for the Stat.js file 
        Object bean = container.getBean("interface");
        if (bean instanceof DefaultInterfaceProcessor) {
            DefaultInterfaceProcessor processor = (DefaultInterfaceProcessor)bean;
            processor.setOverridePath("/plugins/monitoring/dwr");
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

        public String getPathInfo() {
            String pathInfo = super.getPathInfo();
            return pathInfo.replaceAll("/monitoring/dwr", ""); 
        }
    }
}
