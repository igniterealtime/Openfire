/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.http;

import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.net.MXParser;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.dom4j.io.XMPPPacketReader;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.mortbay.util.ajax.ContinuationSupport;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import java.io.IOException;
import java.net.InetAddress;

/**
 * Handles requests to the HTTP Bind service.
 *
 * @author Alexander Wenckus
 */
public class HttpBindServlet extends HttpServlet {
    private HttpSessionManager sessionManager;

    private static XmlPullParserFactory factory;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    public HttpBindServlet(HttpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }


    @Override public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        sessionManager.start();
    }


    @Override public void destroy() {
        super.destroy();
        sessionManager.stop();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        if (isContinuation(request, response)) {
            return;
        }
        Document document;
        try {
            document = createDocument(request);
        }
        catch (Exception e) {
            Log.warn("Error parsing user request. [" + request.getRemoteAddr() + "]");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Unable to parse request content: " + e.getMessage());
            return;
        }

        Element node = document.getRootElement();
        if (node == null || !"body".equals(node.getName())) {
            Log.warn("Body missing from request content. [" + request.getRemoteAddr() + "]");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Body missing from request content.");
            return;
        }

        String sid = node.attributeValue("sid");
        // We have a new session
        if (sid == null) {
            createNewSession(request, response, node);
        }
        else {
            handleSessionRequest(sid, request, response, node);
        }
    }

    private boolean isContinuation(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        HttpConnection connection = (HttpConnection) request.getAttribute("request-connection");
        if (connection == null) {
            return false;
        }
        synchronized (connection.getSession()) {
            respond(response, connection);
        }
        return true;
    }

    private void handleSessionRequest(String sid, HttpServletRequest request,
                                      HttpServletResponse response, Element rootNode)
            throws IOException
    {
        long rid = getLongAttribue(rootNode.attributeValue("rid"), -1);
        if (rid <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Body missing RID (Request ID)");
            return;
        }

        HttpSession session = sessionManager.getSession(sid);
        if (session == null) {
            Log.warn("Client provided invalid session: " + sid + ". [" +
                    request.getRemoteAddr() + "]");
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid SID.");
            return;
        }
        synchronized (session) {
            HttpConnection connection;
            try {
                connection = sessionManager.forwardRequest(rid, session,
                        request.isSecure(),  rootNode);
            }
            catch (HttpBindException e) {
                response.sendError(e.getHttpError(), e.getMessage());
                if(e.shouldCloseSession()) {
                    session.close();
                }
                return;
            }
            catch (HttpConnectionClosedException nc) {
                Log.error("Error sending packet to client.", nc);
                return;
            }
            
            String type = rootNode.attributeValue("type");
            if ("terminate".equals(type)) {
                session.close();
                respond(response, createEmptyBody().getBytes("utf-8"));
            }
            else {
                connection
                        .setContinuation(ContinuationSupport.getContinuation(request, connection));
                request.setAttribute("request-connection", connection);
                respond(response, connection);
            }
        }
    }

    private void createNewSession(HttpServletRequest request, HttpServletResponse response,
                                  Element rootNode)
            throws IOException
    {
        long rid = getLongAttribue(rootNode.attributeValue("rid"), -1);
        if (rid <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Body missing RID (Request ID)");
            return;
        }

        try {
            HttpConnection connection = new HttpConnection(rid, request.isSecure());
            InetAddress address = InetAddress.getByName(request.getRemoteAddr());
            connection.setSession(sessionManager.createSession(address, rootNode, connection));
            respond(response, connection);
        }
        catch (UnauthorizedException e) {
            // Server wasn't initialized yet.
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server Not initialized");
        }
        catch (HttpBindException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    private void respond(HttpServletResponse response, HttpConnection connection)
            throws IOException
    {
        byte[] content;
        try {
            content = connection.getDeliverable().getBytes("utf-8");
        }
        catch (HttpBindTimeoutException e) {
            content = createEmptyBody().getBytes("utf-8");
        }

        respond(response, content);
    }

    private void respond(HttpServletResponse response, byte [] content) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/xml");
        response.setCharacterEncoding("utf-8");

        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

    private String createEmptyBody() {
        Element body = DocumentHelper.createElement("body");
        body.addNamespace("", "http://jabber.org/protocol/httpbind");
        return body.asXML();
    }

    private long getLongAttribue(String value, long defaultValue) {
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    private Document createDocument(HttpServletRequest request) throws
            DocumentException, IOException, XmlPullParserException {
        // Reader is associated with a new XMPPPacketReader
        XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);

        return reader.read(request.getInputStream());
    }
}
