/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.Log;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import java.io.IOException;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 * @author Matt Tucker
 */
public class PluginServlet extends HttpServlet {

    private static Map<String,HttpServlet> servlets;
    private static File pluginDirectory;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlets = new ConcurrentHashMap<String,HttpServlet>();
        pluginDirectory = new File(JiveGlobals.getMessengerHome(), "plugins");
    }

    public void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        else {
            // Handle JSP requests.
            if (pathInfo.endsWith(".jsp")) {
                handleJSP(pathInfo, request, response);
            }
            // Handle image requests.
            else if (pathInfo.endsWith(".gif") || pathInfo.endsWith(".png")) {
                handleImage(pathInfo, request, response);
            }
            // Anything else results in a 404.
            else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
    }

    public static void registerServlets(Plugin plugin, File webXML) {
        if (!webXML.exists()) {
            Log.error("Could not register plugin servlets, file " + webXML.getAbsolutePath() +
                    " does not exist.");
            return;
        }
        // Find the name of the plugin directory given that the webXML file
        // lives in plugins/[pluginName]/web/web.xml
        String pluginName = webXML.getParentFile().getParent();
        try {
            SAXReader saxReader = new SAXReader();
            Document doc = saxReader.read(webXML);
            // Find all <servlet> entries to discover name to class mapping.
            List classes = doc.selectNodes("//servlet");
            Map<String,Class> classMap = new HashMap<String,Class>();
            for (int i=0; i<classes.size(); i++) {
                Element servletElement = (Element)classes.get(i);
                String name = servletElement.element("servlet-name").getTextTrim();
                String className = servletElement.element("servlet-class").getTextTrim();
                classMap.put(name, plugin.getClass().getClassLoader().loadClass(className));
            }
            // Find all <servelt-mapping> entries to discover name to URL mapping.
            List names = doc.selectNodes("//servlet-mapping");
            for (int i=0; i<names.size(); i++) {
                Element nameElement = (Element)names.get(i);
                String name = nameElement.element("servlet-name").getTextTrim();
                String url = nameElement.element("url-pattern").getTextTrim();
                // Register the servlet for the URL.
                Class servletClass = classMap.get(name);
                Object instance = servletClass.newInstance();
                if (instance instanceof HttpServlet) {
                    servlets.put(pluginName + url, (HttpServlet)instance);
                }
            }
        }
        catch (Throwable e) {
            e.printStackTrace();
        }

        //plugin.getClass().getClassLoader().loadClass("");
    }

    /**
     * Handles a request for a JSP page. It checks to see if a servlet is mapped
     * for the JSP URL. If one is found, request handling is passed to it. If no
     * servlet is found, a 404 error is returned.
     *
     * @param pathInfo the extra path info.
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    private void handleJSP(String pathInfo, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
    {
        // Strip the starting "/" from the path to find the JSP URL.
        String jspURL = pathInfo.substring(1);
        HttpServlet servlet = servlets.get(jspURL);
        if (servlet != null) {
            servlet.service(request, response);
            return;
        }
        else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
    }

    private void handleImage(String pathInfo, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
    {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
    }

}
