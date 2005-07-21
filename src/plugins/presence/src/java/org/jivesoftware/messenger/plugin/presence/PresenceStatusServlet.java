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

package org.jivesoftware.messenger.plugin.presence;

import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.plugin.PresencePlugin;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Presence;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Servlet that provides information about the presence status of the users in the system.
 * The information may be provided in XML format or in graphical mode. Use the <b>type</b>
 * parameter to specify the type of information to get. Possible values are <b>img</b> and
 * <b>xml</b>. If no type was defined then an image representation is assumed.<p>
 * <p/>
 * The request <b>MUST</b> include the <b>username</b> parameter. This parameter will be used
 * to locate the local user in the server. If this parameter is missing from the request then
 * an error will be logged and nothing will be returned.
 *
 * @author Gaston Dombiak
 */
public class PresenceStatusServlet extends HttpServlet {

    private PresencePlugin plugin;
    private XMLPresenceProvider xmlProvider;
    private ImagePresenceProvider imageProvider;

    byte available[];
    byte away[];
    byte chat[];
    byte dnd[];
    byte offline[];
    byte xa[];

    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        plugin =
                (PresencePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("presence");
        xmlProvider = new XMLPresenceProvider();
        imageProvider = new ImagePresenceProvider(this);
        available = loadResource("/images/user-green-16x16.gif");
        away = loadResource("/images/user-yellow-16x16.gif");
        chat = loadResource("/images/user-green-16x16.gif");
        dnd = loadResource("/images/user-red-16x16.gif");
        offline = loadResource("/images/user-clear-16x16.gif");
        xa = loadResource("/images/user-yellow-16x16.gif");
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String sender = request.getParameter("sender");
        String username = request.getParameter("username");
        String type = request.getParameter("type");
        type = type == null ? "img" : type;

        try {
            Presence presence = plugin.getUserPresence(sender, username);
            if ("img".equals(type)) {
                imageProvider.sendInfo(request, response, presence);
            }
            else if ("xml".equals(type)) {
                xmlProvider.sendInfo(request, response, presence);
            }
            else {
                Log.warn("The presence servlet received an invalid request of type: " + type);
                // TODO Do something
            }
        }
        catch (UserNotFoundException e) {
            if ("img".equals(type)) {
                imageProvider.sendUserNotFound(request, response);
            }
            else if ("xml".equals(type)) {
                xmlProvider.sendUserNotFound(request, response);
            }
            else {
                Log.warn("The presence servlet received an invalid request of type: " + type);
                // TODO Do something
            }
        }
        catch (IllegalArgumentException e) {
            if ("img".equals(type)) {
                imageProvider.sendUserNotFound(request, response);
            }
            else if ("xml".equals(type)) {
                xmlProvider.sendUserNotFound(request, response);
            }
            else {
                Log.warn("The presence servlet received an invalid request of type: " + type);
                // TODO Do something
            }
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    public void destroy() {
        super.destroy();
        available = null;
        away = null;
        chat = null;
        dnd = null;
        offline = null;
        xa = null;
    }

    private byte[] loadResource(String path) {
        ServletContext context = getServletContext();
        InputStream in = context.getResourceAsStream(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int i = in.read(); i > -1; i = in.read()) {
                out.write(i);
            }
        }
        catch (IOException e) {
            Log.error("error loading:" + path);
        }
        return out.toByteArray();
    }

}
