/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

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
                InputStream in = null;
                OutputStream ost = null;
                try {
                    in = new FileInputStream(icon);
                    ost = response.getOutputStream();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) >= 0) {
                        ost.write(buf, 0, len);
                    }
                    ost.flush();
                }
                catch (IOException ioe) {

                }
                finally {
                    if (in != null) {
                        try {
                            in.close();
                        }
                        catch (Exception e) {
                        }
                    }
                    if (ost != null) {
                        try {
                            ost.close();
                        }
                        catch (Exception e) {
                        }
                    }
                }
            }
        }
    }
}
