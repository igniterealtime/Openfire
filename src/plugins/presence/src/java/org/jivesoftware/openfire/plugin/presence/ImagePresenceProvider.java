/**
 * $RCSfile$
 * $Revision: 1682 $
 * $Date: 2005-07-22 18:13:38 -0300 (Fri, 22 Jul 2005) $
 *
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

package org.jivesoftware.openfire.plugin.presence;

import org.jivesoftware.util.Log;
import org.xmpp.packet.Presence;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * The ImagePresenceProvider provides information about the users presence by returning
 * images. There are many ways to specify the images to use.
 *
 * <ul>
 *  <li>Use a single parameter in the URL - Use the <b>images</b> parameter that will include a
 *      --IMAGE-- token. The --IMAGE-- token would be filtered and would be replaced with
 *      codes like "dnd", "offline", "away", etc.</li>
 *  <li>Use a parameter for each possible presence type - Possible parameters are: <b>offline</b>,
 *      <b>available</b>, <b>away</b>, <b>chat</b>, <b>dnd</b>, <b>xa</b> and <b>forbidden</b>. If
 *      the parameter was not passed then the default image will be used.</li>
 *  <li>Do not pass any parameter - When no parameter was passed the default images will be
 *      used.</li>
 * </ul>
 *
 * If the required user was not found or the user making the request is not allowed to see the
 * user presence then the image specified in the <b>forbidden</b> parameter will be used.
 * However, if the request does not include the <b>forbidden</b> parameter then the default
 * image for user offline will be used.
 *
 * @author Gaston Dombiak
 *
 */
class ImagePresenceProvider extends PresenceInfoProvider {

    private PresenceStatusServlet servlet;
    private Map<String, byte[]> imageCache = new HashMap<String, byte[]>();
    private Map<String, String> imageTypeCache = new HashMap<String, String>();

    public ImagePresenceProvider(PresenceStatusServlet servlet) {
        this.servlet = servlet;
    }

    public void sendInfo(HttpServletRequest request,
            HttpServletResponse response, Presence presence) throws IOException {
        if (presence == null) {
            writeImageContent(request, response, "offline", servlet.offline);
        }
        else if (presence.getShow() == null) {
            writeImageContent(request, response, "available", servlet.available);
        }
        else if (presence.getShow().equals(org.xmpp.packet.Presence.Show.away)) {
            writeImageContent(request, response, "away", servlet.away);
        }
        else if (presence.getShow().equals(org.xmpp.packet.Presence.Show.chat)) {
            writeImageContent(request, response, "chat", servlet.chat);
        }
        else if (presence.getShow().equals(org.xmpp.packet.Presence.Show.dnd)) {
            writeImageContent(request, response, "dnd", servlet.dnd);
        }
        else if (presence.getShow().equals(org.xmpp.packet.Presence.Show.xa)) {
            writeImageContent(request, response, "xa", servlet.xa);
        }
    }

    public void sendUserNotFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        writeImageContent(request, response, "forbidden", servlet.offline);
    }

    private void writeImageContent(HttpServletRequest request, HttpServletResponse response,
            String presenceType, byte[] defaultImage) throws IOException {
        String images = request.getParameter("images");
        if (request.getParameter(presenceType) != null) {
            writeImageContent(request.getParameter(presenceType), defaultImage, response);
        }
        else if (images != null) {
            response.sendRedirect(images.replace("--IMAGE--", presenceType));
        }
        else {
            writeImageContent(null, defaultImage, response);
        }
    }

    private void writeImageContent(String url, byte[] defaultContent, HttpServletResponse response)
            throws IOException {
        ServletOutputStream os = response.getOutputStream();
        byte[] imageContent = defaultContent;
        String contentType = "image/gif";
        if (url != null) {
            try {
                byte[] cachedContent = imageCache.get(url);
                if (cachedContent == null) {
                    URLConnection connection = new URL(url).openConnection();
                    InputStream in = connection.getInputStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte buffer[] = new byte[1024 * 4];
                    int last_read_bytes = 0;
                    while ((last_read_bytes = in.read(buffer)) != -1) {
                        bytes.write(buffer, 0, last_read_bytes);
                    }
                    if (bytes.size() > 0) {
                        imageCache.put(url, bytes.toByteArray());
                        imageTypeCache.put(url, connection.getContentType());
                    }
                }
                if (imageTypeCache.get(url) != null) {
                    contentType = imageTypeCache.get(url);
                    imageContent = imageCache.get(url);
                }
            }
            catch (IOException e) {
                Log.error(e);
            }
        }
        response.setContentType(contentType);
        os.write(imageContent);
        os.flush();
        os.close();
    }

}
