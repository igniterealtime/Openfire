/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.webdav;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Base64;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.XMPPServer;
import org.xmpp.packet.JID;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletInputStream;
import java.io.*;

/**
 * This is a work in progress and is checked in for future use.
 */

/**
 * Implements a very light WebDAV-ish servlet for specific purposes.  It does not support WebDAV extensions
 * to the HTTP protocol.  Instead, it supports the set of commands: GET, PUT, and DELETE.  This serves
 * as a WebDAV like storage interface for MUC shared files.  It handles part of XEP-0129: WebDAV File Transfers,
 * but not all of it.  We don't handle the PROPPATCH command, for example, as the user has no rights to set
 * the permissions on MUC shared files.
 *
 * TODO: How to handle a remote account?  As posed in the WebDAV XEP, we should send a message and wait for response.
 * TODO: How to handle SparkWeb?  Reasking for a username and password would suck.  Need some form of SSO.
 *    Maybe the SSO could be some special token provided during sign-on that the client could store and use
 *    for auth.
 *
 * @author Daniel Henninger
 */
public class WebDAVLiteServlet extends HttpServlet {

    // Storage directory under the Openfire install root
    private static String WEBDAV_SUBDIR = "mucFiles";

    /**
     * Retrieves a File object referring to a file in a service and room.  Leaving file as null
     * will get you the directory that would contain all of the files for a particular service and room.
     *
     * @param service Subdomain of the conference service we are forming a file path for.
     * @param room Conference room we are forming a file path for.
     * @param file Optional (can be null) filename for a path to a specific file (otherwise, directory).
     * @return The File reference constructed for the service, room, and file combination.
     */
    private File getFileReference(String service, String room, String file) {
        return new File(JiveGlobals.getHomeDirectory(), WEBDAV_SUBDIR+File.separator+service+File.separator+room+(file != null ? File.separator+file : ""));
    }
    
    /**
     * Verifies that the user is authenticated via some mechanism such as Basic Auth.  If the
     * authentication fails, this method will alter the HTTP response to include a request for
     * auth and send the unauthorized response back to the client.
     *
     * TODO: Handle some form of special token auth, perhaps provided a room connection?
     * TODO: If it's not a local account, we should try message auth access?  XEP-0070?
     * TODO: Should we support digest auth as well?
     *
     * @param request Object representing the HTTP request.
     * @param response Object representing the HTTP response.
     * @return True or false if the user is authenticated.
     * @throws ServletException If there was a servlet related exception.
     * @throws IOException If there was an IO error while setting the error.
     */
    private Boolean isAuthenticated(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        JID jid;
        try {
            if (auth == null || !request.getAuthType().equals(HttpServletRequest.BASIC_AUTH)) {
                throw new Exception("No authorization or improper authorization provided.");
            }
            auth = auth.substring(auth.indexOf(" "));
            String decoded = new String(Base64.decode(auth));
            int i = decoded.indexOf(":");
            String username = decoded.substring(0,i);
            if (!username.contains("@")) {
                throw new Exception("Not a valid JID.");
            }
            jid = new JID(username);
            if (XMPPServer.getInstance().isLocal(jid)) {
                String password = decoded.substring(i+1, decoded.length());
                if (AuthFactory.authenticate(username, password) == null) {
                    throw new Exception("Authentication failed.");
                }
            }
            else {
                // TODO: Authenticate a remote user, probably via message auth.
                throw new Exception("Not a local account.");
            }
            return true;
        }
        catch (Exception e) {
            /**
             * This covers all possible authentication issues.  Eg:
             * - not enough of auth info passed in
             * - failed auth
             */
            response.setHeader("WWW-Authenticate", "Basic realm=\"Openfire WebDAV\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    /**
     * Verifies that the authenticated user is a member of a conference service and room, or else
     * they are not entitled to view any of the files in the room.
     *
     * @param request Object representing the HTTP request.
     * @param response Object representing the HTTP response.
     * @param service Subdomain of the conference service they are trying to access files for.
     * @param room Room in the conference service they are trying to access files for.
     * @return True or false if the user is authenticated.
     * @throws ServletException If there was a servlet related exception.
     * @throws IOException If there was an IO error while setting the error.
     */
    private Boolean isAuthorized(HttpServletRequest request, HttpServletResponse response,
                                 String service, String room) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        JID jid;
        try {
            if (auth == null || !request.getAuthType().equals(HttpServletRequest.BASIC_AUTH)) {
                throw new Exception("No authorization or improper authorization provided.");
            }
            auth = auth.substring(auth.indexOf(" "));
            String decoded = new String(Base64.decode(auth));
            int i = decoded.indexOf(":");
            String username = decoded.substring(0,i);
            if (!username.contains("@")) {
                throw new Exception("Not a valid JID.");
            }
            jid = new JID(username);
            XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(service).getChatRoom(room).getOccupantsByBareJID(jid.toBareJID());
            return true;
        }
        catch (Exception e) {
            /**
             * This covers all possible authorization issues.  Eg:
             * - accessing a room that doesn't exist
             * - accessing a room that user isn't a member of
             */
            response.setHeader("WWW-Authenticate", "Basic realm=\"Openfire WebDAV\"");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
    }

    /**
     * Initialize the WebDAV servlet, auto-creating it's file root if it doesn't exist.
     *
     * @param servletConfig Configuration settings of the servlet from web.xml.
     * @throws ServletException If there was an exception setting up the servlet.
     */
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        File webdavDir = new File(JiveGlobals.getHomeDirectory(), WEBDAV_SUBDIR);
        if (!webdavDir.exists()) {
            webdavDir.mkdirs();
        }
    }

    /**
     * Handles a GET request for files or for a file listing.
     *
     * @param request Object representing the HTTP request.
     * @param response Object representing the HTTP response.
     * @throws ServletException If there was a servlet related exception.
     * @throws IOException If there was an IO error while setting the error.
     */
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws
                ServletException, IOException {
        // Verify authentication
        if (!isAuthenticated(request, response)) return;

        String path = request.getPathInfo();
        Log.debug("WebDAVLiteServlet: GET with path = "+path);
        if (path == null || !path.startsWith("/rooms/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String[] pathPcs = path.split("/");
        if (pathPcs.length < 4 || pathPcs.length > 5) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String service = pathPcs[2];
        String room = pathPcs[3];

        // Verify authorization
        if (!isAuthorized(request, response, service, room)) return;
        
        if (pathPcs.length == 5) {
            // File retrieval
            String filename = pathPcs[4];
            File file = getFileReference(service, room, filename);
            Log.debug("WebDAVListServlet: File path = "+file.getAbsolutePath());
            Log.debug("WebDAVListServlet: Service = "+service+", room = "+room+", file = "+filename);
            if (file.exists()) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/octet-stream");
                response.setContentLength((int)file.length());
                FileInputStream fileStream = new FileInputStream(file);
                byte[] byteArray = new byte[(int)file.length()];
                new DataInputStream(fileStream).readFully(byteArray);
                fileStream.close();
                response.getOutputStream().write(byteArray);
            }
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
        else {
            // File listing
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");
            response.setCharacterEncoding("utf-8");
            String content = "Files available for "+room+"@"+service+":\n";
            File fileDir = getFileReference(service, room, null);
            Log.debug("WebDAVListServlet: File path = "+fileDir.getAbsolutePath());
            if (fileDir.exists()) {
                File[] files = fileDir.listFiles();
                for (File file : files) {
                    content += file.getName()+"\n";
                }
            }
            response.getOutputStream().write(content.getBytes());
            Log.debug("WebDAVListServlet: Service = "+service+", room = "+room);
        }
    }

    /**
     * Handles a PUT request for uploading files.
     *
     * @param request Object representing the HTTP request.
     * @param response Object representing the HTTP response.
     * @throws ServletException If there was a servlet related exception.
     * @throws IOException If there was an IO error while setting the error.
     */
    @Override
    protected void doPut(HttpServletRequest request,
                         HttpServletResponse response) throws
                ServletException, IOException {
        // Verify authentication
        if (!isAuthenticated(request, response)) return;

        String path = request.getPathInfo();
        Log.debug("WebDAVLiteServlet: PUT with path = "+path);
        if (request.getContentLength() <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (path == null || !path.startsWith("/rooms/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String[] pathPcs = path.split("/");
        if (pathPcs.length != 5) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String service = pathPcs[2];
        String room = pathPcs[3];
        String filename = pathPcs[4];

        // Verify authorization
        if (!isAuthorized(request, response, service, room)) return;

        Log.debug("WebDAVListServlet: Service = "+service+", room = "+room+", file = "+filename);
        File file = getFileReference(service, room, filename);
        Boolean overwriteFile = file.exists();
        FileOutputStream fileStream = new FileOutputStream(file, false);
        ServletInputStream inputStream = request.getInputStream();
        byte[] byteArray = new byte[request.getContentLength()];
        int bytesRead = 0;
        while (bytesRead != -1) {
            bytesRead = inputStream.read(byteArray, bytesRead, request.getContentLength());   
        }
        fileStream.write(byteArray);
        fileStream.close();
        inputStream.close();
        if (overwriteFile) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.setHeader("Location", request.getRequestURI());
        }
        else {
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setHeader("Location", request.getRequestURI());
        }
    }

    /**
     * Handles a DELETE request for deleting files.
     *
     * @param request Object representing the HTTP request.
     * @param response Object representing the HTTP response.
     * @throws ServletException If there was a servlet related exception.
     * @throws IOException If there was an IO error while setting the error.
     */
    @Override
    protected void doDelete(HttpServletRequest request,
                         HttpServletResponse response) throws
                ServletException, IOException {
        // Verify authentication
        if (!isAuthenticated(request, response)) return;
        
        String path = request.getPathInfo();
        Log.debug("WebDAVLiteServlet: DELETE with path = "+path);
        if (path == null || !path.startsWith("/rooms/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String[] pathPcs = path.split("/");
        if (pathPcs.length != 5) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String service = pathPcs[2];
        String room = pathPcs[3];
        String filename = pathPcs[4];

        // Verify authorization
        if (!isAuthorized(request, response, service, room)) return;

        Log.debug("WebDAVListServlet: Service = "+service+", room = "+room+", file = "+filename);
        File file = getFileReference(service, room, filename);
        if (file.exists()) {
            file.delete();
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
        else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

}
