/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.admin.servlet;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.update.UpdateManager;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@WebServlet(value = "/plugin-admin.jsp")
@MultipartConfig
public class PluginServlet extends HttpServlet
{
    private final static Logger Log = LoggerFactory.getLogger(PluginServlet.class);

    /**
     * Defines if the admin console can be used to upload plugins.
     */
    public static final SystemProperty<Boolean> PLUGINS_UPLOAD_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("plugins.upload.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    /**
     * Determines if the content-type of uploaded plugin files is verified.
     */
    public static final SystemProperty<Boolean> CONTENTTYPE_CHECK_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("plugins.upload.content-type-check.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    /**
     * Defines the expected content-type of uploaded plugin files.
     */
    public static final SystemProperty<List<String>> EXPECTED_CONTENTTYPE = SystemProperty.Builder.ofType(List.class)
        .setKey("plugins.upload.content-type-check.expected-value")
        .setDefaultValue(List.of("application/x-java-archive", "application/java-archive"))
        .setDynamic(true)
        .buildList(String.class);

    protected void doCommon(HttpServletRequest request, HttpServletResponse response)
    {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init(request, response, session, session.getServletContext());

        final PluginManager pluginManager = webManager.getXMPPServer().getPluginManager();
        final UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

        request.setAttribute("webManager", webManager);
        request.setAttribute("pluginManager", pluginManager);
        request.setAttribute("updateManager", updateManager);

        request.setAttribute("plugins", pluginManager.getMetadataExtractedPlugins());
        request.setAttribute("uploadEnabled", PLUGINS_UPLOAD_ENABLED.getValue());
        request.setAttribute("serverVersion", XMPPServer.getInstance().getServerInfo().getVersion());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        doCommon(request, response);

        final String csrfParam = StringUtils.randomString(15);
        CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
        request.setAttribute("csrf", csrfParam);

        request.getRequestDispatcher("plugin-admin-jsp.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        doCommon(request, response);

        final WebManager webManager = (WebManager) request.getAttribute("webManager"); // set in doCommon()
        final PluginManager pluginManager = (PluginManager) request.getAttribute("pluginManager"); // set in doCommon()
        final boolean uploadEnabled = (boolean) request.getAttribute("uploadEnabled");

        final String deletePlugin = ParamUtils.getParameter(request, "deleteplugin");
        final String reloadPlugin = ParamUtils.getParameter(request, "reloadplugin");
        final boolean uploadPlugin = request.getParameter("uploadplugin") != null;

        final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        final String csrfParam = ParamUtils.getParameter(request, "csrf");

        final boolean csrfError = csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam);

        // Generate a new CSRF value, to be used when the page is used another time (e.g. to upload a new plugin).
        final String newCsrfParam = StringUtils.randomString(15);
        CookieUtils.setCookie(request, response, "csrf", newCsrfParam, -1);
        request.setAttribute("csrf", newCsrfParam);

        if (csrfError) {
            response.sendRedirect("plugin-admin.jsp?csrfError=true");
            return;
        }

        if (deletePlugin != null) {
            pluginManager.deletePlugin( deletePlugin );
            webManager.logEvent("deleted plugin "+deletePlugin, null);
            response.sendRedirect("plugin-admin.jsp?deletesuccess=true");
            return;
        }

        if (reloadPlugin != null) {
            if ( pluginManager.reloadPlugin(reloadPlugin) ) {
                webManager.logEvent("reloaded plugin "+reloadPlugin, null);
                response.sendRedirect("plugin-admin.jsp?reloadsuccess=true");
                return;
            } else {
                response.sendRedirect( "plugin-admin.jsp?reloadsuccess=false" );
                return;
            }
        }

        if (uploadEnabled && uploadPlugin) {
            boolean installed = false;

            try {
                // Parse the request
                final Part uploadedPlugin = request.getPart("uploadfile");
                final String fileName = uploadedPlugin.getSubmittedFileName();
                final String contentType = uploadedPlugin.getContentType();
                Log.debug("Uploaded plugin '{}' content type: '{}'.", fileName, contentType );
                if (fileName == null) {
                    Log.error( "Ignoring uploaded file: No filename specified for file upload." );
                    response.sendRedirect("plugin-admin.jsp?uploadsuccess=false");
                    return;
                }

                if (CONTENTTYPE_CHECK_ENABLED.getValue() && EXPECTED_CONTENTTYPE.getValue().stream().noneMatch(v -> v.equalsIgnoreCase(contentType))) {
                    Log.error("Ignoring uploaded file: Content type '{}' of uploaded file '{}' does not match any of the expected content types: {}", contentType, fileName, String.join(", ", EXPECTED_CONTENTTYPE.getValue()));
                    response.sendRedirect("plugin-admin.jsp?uploadsuccess=false");
                    return;
                }

                try (final InputStream is = uploadedPlugin.getInputStream()) {
                    installed = XMPPServer.getInstance().getPluginManager().installPlugin(is, fileName);
                    if (!installed) {
                        Log.error("Plugin manager failed to install plugin: " + fileName);
                    } else {
                        webManager.logEvent("uploaded plugin " + fileName, null);
                    }
                } catch (IOException e) {
                    Log.error("Unable to open file stream for uploaded file: " + fileName, e);
                    response.sendRedirect("plugin-admin.jsp?uploadsuccess=false");
                    return;
                }
            }
            catch (Exception e) {
                Log.error("Unable to upload plugin file.", e);
            }
            response.sendRedirect("plugin-admin.jsp?uploadsuccess=" + installed);
        }
    }
}
