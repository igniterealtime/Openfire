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
package org.jivesoftware.openfire.container;

import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.openfire.XMPPServer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Servlet is used for retrieval of plugin icons.
 *
 * @author Derek DeMoro
 */
public class PluginIconServlet extends HttpServlet {

    @Override
	public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        String pluginName = ParamUtils.getParameter(request, "plugin");
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin != null) {
            // Try looking for PNG file first then default to GIF.
            File icon = new File(pluginManager.getPluginDirectory(plugin), "logo_small.png");
            boolean isPng = true;
            if (!icon.exists()) {
                icon = new File(pluginManager.getPluginDirectory(plugin), "logo_small.gif");
                isPng = false;
            }
            if (icon.exists()) {
                // Clear any empty lines added by the JSP declaration. This is required to show
                // the image in resin!
                response.reset();
                if (isPng) {
                    response.setContentType("image/png");
                }
                else {
                    response.setContentType("image/gif");
                }
                try (InputStream in = new FileInputStream(icon)) {
                    try (OutputStream ost = response.getOutputStream()) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) >= 0) {
                            ost.write(buf, 0, len);
                        }
                        ost.flush();
                    }
                } catch (IOException ioe) {
                    throw new ServletException(ioe);
                }
            }
        }
    }
}
