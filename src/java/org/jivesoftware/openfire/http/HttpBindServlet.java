/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Servlet which handles requests to the HTTP binding service. It determines if there is currently
 * an {@link HttpSession} related to the connection or if one needs to be created and then passes it
 * off to the {@link HttpBindManager} for processing of the client request and formulating of the
 * response.
 *
 * @author Alexander Wenckus
 */
public class HttpBindServlet extends HttpServlet {
	
	private static final Logger Log = LoggerFactory.getLogger(HttpBindServlet.class);

    private HttpSessionManager sessionManager;
    private HttpBindManager boshManager;

    private static XmlPullParserFactory factory;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    private ThreadLocal<XMPPPacketReader> localReader = new ThreadLocal<XMPPPacketReader>();

    public HttpBindServlet() {
    }


    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        boshManager = HttpBindManager.getInstance();
        sessionManager = boshManager.getSessionManager();
        sessionManager.start();
    }


    @Override
    public void destroy() {
        super.destroy();
        sessionManager.stop();
    }

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// add CORS headers for all HTTP responses (errors, etc.)
        setCORSHeaders(request, response);
		super.service(request, response);
	}

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        boolean isScriptSyntaxEnabled = boshManager.isScriptSyntaxEnabled();
                
        if(!isScriptSyntaxEnabled) {
            sendLegacyError(response, BoshBindingError.itemNotFound);
            return;
        }

        if (isContinuation(request, response)) {
            return;
        }
        String queryString = request.getQueryString();
        if (queryString == null || "".equals(queryString)) {
            sendLegacyError(response, BoshBindingError.badRequest);
            return;
        }
        queryString = URLDecoder.decode(queryString, "UTF-8");

        parseDocument(request, response, new ByteArrayInputStream(queryString.getBytes("UTF-8")));
    }

    private void sendLegacyError(HttpServletResponse response, BoshBindingError error)
            throws IOException
    {
        response.sendError(error.getLegacyErrorCode());
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (isContinuation(request, response)) {
            return;
        }

        parseDocument(request, response, request.getInputStream());
    }

    private void parseDocument(HttpServletRequest request, HttpServletResponse response,
                               InputStream documentContent)
            throws IOException {

        Document document;
        try {
            document = createDocument(documentContent);
        }
        catch (Exception e) {
            Log.warn("Error parsing user request. [" + request.getRemoteAddr() + "]");
            sendLegacyError(response, BoshBindingError.badRequest);
            return;
        }

        Element node = document.getRootElement();
        if (node == null || !"body".equals(node.getName())) {
            Log.warn("Body missing from request content. [" + request.getRemoteAddr() + "]");
            sendLegacyError(response, BoshBindingError.badRequest);
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
        HttpSession session = (HttpSession) request.getAttribute("request-session");
        if (session == null) {
            return false;
        }
        synchronized (session) {
            try {
                respond(session, request, response, session.consumeResponse((HttpConnection) request.getAttribute("connection")),
                        request.getMethod());
            }
            catch (HttpBindException e) {
                sendError(request, response, e.getBindingError(), session);
            }
        }
        return true;
    }

    private void sendError(HttpServletRequest request, HttpServletResponse response,
                           BoshBindingError bindingError, HttpSession session)
            throws IOException
    {
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            System.out.println(new Date()+": HTTP ERR("+session.getStreamID().getID() + "): " + bindingError.getErrorType().getType() + ", " + bindingError.getCondition() + ".");
        }
        try {
        	if ((session.getMajorVersion() == 1 && session.getMinorVersion() >= 6) ||
                	session.getMajorVersion() > 1) {
                respond(session, request, response, createErrorBody(bindingError.getErrorType().getType(),
                        bindingError.getCondition()), request.getMethod());
            }
            else {
                sendLegacyError(response, bindingError);
            }
        }
        finally {
            if (bindingError.getErrorType() == BoshBindingError.Type.terminate) {
                session.close();
            }
        }
    }

    private String createErrorBody(String type, String condition) {
        Element body = DocumentHelper.createElement("body");
        body.addNamespace("", "http://jabber.org/protocol/httpbind");
        body.addAttribute("type", type);
        body.addAttribute("condition", condition);
        return body.asXML();
    }

    private void handleSessionRequest(String sid, HttpServletRequest request,
                                      HttpServletResponse response, Element rootNode)
            throws IOException
    {
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            System.out.println(new Date()+": HTTP RECV(" + sid + "): " + rootNode.asXML());
        }
        long rid = getLongAttribue(rootNode.attributeValue("rid"), -1);
        if (rid <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Body missing RID (Request ID)");
            return;
        }

        HttpSession session = sessionManager.getSession(sid);
        if (session == null) {
        	if (Log.isDebugEnabled()) {
                Log.debug("Client provided invalid session: " + sid + ". [" +
                        request.getRemoteAddr() + "]");
        	}
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid SID.");
            return;
        }
        synchronized (session) {
            HttpConnection connection;
            try {
                connection = sessionManager.forwardRequest(rid, session,
                        request.isSecure(), rootNode);
            }
            catch (HttpBindException e) {
                sendError(request, response, e.getBindingError(), session);
                return;
            }
            catch (HttpConnectionClosedException nc) {
                Log.error("Error sending packet to client.", nc);
                return;
            }

            String type = rootNode.attributeValue("type");
            String restartStream = rootNode.attributeValue(new QName("restart", rootNode.getNamespaceForPrefix("xmpp")));
            int pauseDuration = getIntAttribue(rootNode.attributeValue("pause"), -1);

            if ("terminate".equals(type)) {
                session.close();
                respond(session, request, response, createEmptyBody(true), request.getMethod());
            }
            else if ("true".equals(restartStream) && rootNode.elements().size() == 0) {
                try {
					respond(session, request, response, createSessionRestartResponse(session), request.getMethod());
				}
				catch (DocumentException e) {
					Log.error("Error sending session restart response to client.", e);
				}
            }
            else if (pauseDuration > 0 && pauseDuration <= session.getMaxPause()) {
            	session.pause(pauseDuration);
                respond(session, request, response, createEmptyBody(false), request.getMethod());
                session.setLastResponseEmpty(true);
            }
            else {
                session.resetInactivityTimeout();
                connection.setContinuation(ContinuationSupport.getContinuation(request));
                request.setAttribute("request-session", connection.getSession());
                request.setAttribute("request", connection.getRequestId());
                request.setAttribute("connection", connection);
                try {
                    respond(session, request, response, session.consumeResponse(connection),
                            request.getMethod());
                }
                catch (HttpBindException e) {
                    sendError(request, response, e.getBindingError(), session);
                }
            }
        }
    }

    private String createSessionRestartResponse(HttpSession session) throws DocumentException {
        Element response = DocumentHelper.createElement("body");
        response.addNamespace("", "http://jabber.org/protocol/httpbind");
        response.addNamespace("stream", "http://etherx.jabber.org/streams");

        Element features = response.addElement("stream:features");
        for (Element feature : session.getAvailableStreamFeaturesElements()) {
            features.add(feature);
        }

        return response.asXML();
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
            X509Certificate[] certificates =
                    (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
            HttpConnection connection = new HttpConnection(rid, request.isSecure(), certificates);
            InetAddress address = InetAddress.getByName(request.getRemoteAddr());
            connection.setSession(sessionManager.createSession(address, rootNode, connection));
            if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
                System.out.println(new Date()+": HTTP RECV(" + connection.getSession().getStreamID().getID() + "): " + rootNode.asXML());
            }
            respond(request, response, connection, request.getMethod());
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

    // add request argument
    private void respond(HttpServletRequest request, HttpServletResponse response, HttpConnection connection, String method)
            throws IOException
    {
        String content;
        try {
            content = connection.getResponse();
        }
        catch (HttpBindTimeoutException e) {
            content = createEmptyBody(false);
            connection.getSession().setLastResponseEmpty(true);
        }

        respond(connection.getSession(), request, response, content, method);
    }

    // add request argument
    private void respond(HttpSession session, HttpServletRequest request, HttpServletResponse response, String content, String method)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("GET".equals(method) ? "text/javascript" : "text/xml");
        response.setCharacterEncoding("UTF-8");

        if ("GET".equals(method)) {
            if (JiveGlobals.getBooleanProperty("xmpp.httpbind.client.no-cache.enabled", true)) {
                // Prevent caching of responses
                response.addHeader("Cache-Control", "no-store");
                response.addHeader("Cache-Control", "no-cache");
                response.addHeader("Pragma", "no-cache");
            }
            content = "_BOSH_(\"" + StringEscapeUtils.escapeJavaScript(content) + "\")";
        }
        
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            System.out.println(new Date()+": HTTP SENT(" + session.getStreamID().getID() + "): " + content);
        }
        byte[] byteContent = content.getBytes("UTF-8");
        response.setContentLength(byteContent.length);
        response.getOutputStream().write(byteContent);
        response.getOutputStream().close();
    }

    private void setCORSHeaders(HttpServletRequest request, HttpServletResponse response) {
        // set CORS headers
        if (boshManager.isCORSEnabled()) {
            if (boshManager.isAllOriginsAllowed())
                // set the Access-Control-Allow-Origin header to * to allow all Origin to do the CORS  
                response.setHeader("Access-Control-Allow-Origin", HttpBindManager.HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT);
            else {
                // get the Origin header from the request and check if it is in the allowed Origin Map.
                // if it is allowed write it back to the Access-Control-Allow-Origin header of the respond. 
                String origin = request.getHeader("Origin");
                if (boshManager.isThisOriginAllowed(origin)) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                }
            }
            response.setHeader("Access-Control-Allow-Methods", HttpBindManager.HTTP_BIND_CORS_ALLOW_METHODS_DEFAULT);
            response.setHeader("Access-Control-Allow-Headers", HttpBindManager.HTTP_BIND_CORS_ALLOW_HEADERS_DEFAULT);
            response.setHeader("Access-Control-Max-Age", HttpBindManager.HTTP_BIND_CORS_MAX_AGE_DEFAULT);
        }
    }

    private static String createEmptyBody(boolean terminate) {
        Element body = DocumentHelper.createElement("body");
        if (terminate) { body.addAttribute("type", "terminate"); }
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

    private int getIntAttribue(String value, int defaultValue) {
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    private XMPPPacketReader getPacketReader() {
        // Reader is associated with a new XMPPPacketReader
        XMPPPacketReader reader = localReader.get();
        if (reader == null) {
            reader = new XMPPPacketReader();
            reader.setXPPFactory(factory);
            localReader.set(reader);
        }
        return reader;
    }

    private Document createDocument(InputStream request) throws
            DocumentException, IOException, XmlPullParserException
    {
        return getPacketReader().read("UTF-8", request);
    }
}
