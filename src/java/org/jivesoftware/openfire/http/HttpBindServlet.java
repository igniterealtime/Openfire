/*
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

import java.io.*;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
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

    private ThreadLocal<XMPPPacketReader> localReader = new ThreadLocal<>();

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
        if (boshManager.isCORSEnabled())
        {
            if (boshManager.isAllOriginsAllowed()) {
                // Set the Access-Control-Allow-Origin header to * to allow all Origin to do the CORS
                response.setHeader("Access-Control-Allow-Origin", HttpBindManager.HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT);
            } else {
                // Get the Origin header from the request and check if it is in the allowed Origin Map.
                // If it is allowed write it back to the Access-Control-Allow-Origin header of the respond.
                final String origin = request.getHeader("Origin");
                if (boshManager.isThisOriginAllowed(origin)) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                }
            }
            response.setHeader("Access-Control-Allow-Methods", HttpBindManager.HTTP_BIND_CORS_ALLOW_METHODS_DEFAULT);
            response.setHeader("Access-Control-Allow-Headers", HttpBindManager.HTTP_BIND_CORS_ALLOW_HEADERS_DEFAULT);
            response.setHeader("Access-Control-Max-Age", HttpBindManager.HTTP_BIND_CORS_MAX_AGE_DEFAULT);
        }
        super.service(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        final AsyncContext context = request.startAsync();

        boolean isScriptSyntaxEnabled = boshManager.isScriptSyntaxEnabled();
                
        if(!isScriptSyntaxEnabled) {
            sendLegacyError(context, BoshBindingError.itemNotFound);
            return;
        }

        String queryString = request.getQueryString();
        if (queryString == null || "".equals(queryString)) {
            sendLegacyError(context, BoshBindingError.badRequest);
            return;
        } else if ("isBoshAvailable".equals(queryString)) {
            response.setStatus(HttpServletResponse.SC_OK);
            context.complete();
            return;
        }
        queryString = URLDecoder.decode(queryString, "UTF-8");

        processContent(context, queryString);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        final AsyncContext context = request.startAsync();

        // Asynchronously reads the POSTed input, then triggers #processContent.
        try {
            request.getInputStream().setReadListener(new ReadListenerImpl(context));
        } catch (IllegalStateException e) {
            Log.warn("Error when setting read listener", e);
            context.complete();
        }
    }

    protected void processContent(AsyncContext context, String content)
            throws IOException {
        final String remoteAddress = getRemoteAddress(context);

        // Parse document from the content.
        Document document;
        try {
            document = getPacketReader().read(new StringReader(content), "UTF-8");
        } catch (Exception ex) {
            Log.warn("Error parsing request data from [" + remoteAddress + "]", ex);
            sendLegacyError(context, BoshBindingError.badRequest);
            return;
        }
        if (document == null) {
            Log.info("The result of parsing request data from [" + remoteAddress + "] was a null-object.");
            sendLegacyError(context, BoshBindingError.badRequest);
            return;
        }

        final Element node = document.getRootElement();
        if (node == null || !"body".equals(node.getName())) {
            Log.info("Root element 'body' is missing from parsed request data from [" + remoteAddress + "]");
            sendLegacyError(context, BoshBindingError.badRequest);
            return;
        }

        final long rid = getLongAttribute(node.attributeValue("rid"), -1);
        if (rid <= 0) {
            Log.info("Root element 'body' does not contain a valid RID attribute value in parsed request data from [" + remoteAddress + "]");
            sendLegacyError(context, BoshBindingError.badRequest, "Body-element is missing a RID (Request ID) value, or the provided value is a non-positive integer.");
            return;
        }

        // Process the parsed document.
        final String sid = node.attributeValue("sid");
        if (sid == null) {
            // When there's no Session ID, this should be a request to create a new session. If there's additional content,
            // something is wrong.
            if (node.elements().size() > 0) {
                // invalid session request; missing sid
                Log.info("Root element 'body' does not contain a SID attribute value in parsed request data from [" + remoteAddress + "]");
                sendLegacyError(context, BoshBindingError.badRequest);
                return;
            }

            // We have a new session
            createNewSession(context, node);
        }
        else {
            // When there exists a Session ID, new data for an existing session is being provided.
            handleSessionRequest(sid, context, node);
        }
    }

    protected void createNewSession(AsyncContext context, Element rootNode)
            throws IOException
    {
        final long rid = getLongAttribute(rootNode.attributeValue("rid"), -1);

        try {
            final X509Certificate[] certificates = (X509Certificate[]) context.getRequest().getAttribute("javax.servlet.request.X509Certificate");
            final HttpConnection connection = new HttpConnection(rid, context.getRequest().isSecure(), certificates, context);
            final InetAddress address = InetAddress.getByName(context.getRequest().getRemoteAddr());
            connection.setSession(sessionManager.createSession(address, rootNode, connection));
            if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
                Log.info(new Date() + ": HTTP RECV(" + connection.getSession().getStreamID().getID() + "): " + rootNode.asXML());
            }
        }
        catch (UnauthorizedException | HttpBindException e) {
            // Server wasn't initialized yet.
            sendLegacyError(context, BoshBindingError.internalServerError, "Server has not finished initialization." );
        }
    }

    private void handleSessionRequest(String sid, AsyncContext context, Element rootNode)
            throws IOException
    {
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            Log.info(new Date() + ": HTTP RECV(" + sid + "): " + rootNode.asXML());
        }

        HttpSession session = sessionManager.getSession(sid);
        if (session == null) {
            if (Log.isDebugEnabled()) {
                Log.debug("Client provided invalid session: " + sid + ". [" +
                    context.getRequest().getRemoteAddr() + "]");
            }
            sendLegacyError(context, BoshBindingError.itemNotFound, "Invalid SID value.");
            return;
        }

        final long rid = getLongAttribute(rootNode.attributeValue("rid"), -1);

        synchronized (session) {
            try {
                session.forwardRequest(rid, context.getRequest().isSecure(), rootNode, context);
            }
            catch (HttpBindException e) {
                sendError(session, context, e.getBindingError());
            }
            catch (HttpConnectionClosedException nc) {
                Log.error("Error sending packet to client.", nc);
                context.complete();
            }
        }
    }

    private XMPPPacketReader getPacketReader()
    {
        // Reader is associated with a new XMPPPacketReader
        XMPPPacketReader reader = localReader.get();
        if (reader == null) {
            reader = new XMPPPacketReader();
            reader.setXPPFactory(factory);
            localReader.set(reader);
        }
        return reader;
    }

    public static void respond(HttpSession session, AsyncContext context, String content, boolean async) throws IOException
    {
        final HttpServletResponse response = ((HttpServletResponse) context.getResponse());
        final HttpServletRequest request = ((HttpServletRequest) context.getRequest());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("GET".equals(request.getMethod()) ? "text/javascript" : "text/xml");
        response.setCharacterEncoding("UTF-8");

        if ("GET".equals(request.getMethod())) {
            if (JiveGlobals.getBooleanProperty("xmpp.httpbind.client.no-cache.enabled", true)) {
                // Prevent caching of responses
                response.addHeader("Cache-Control", "no-store");
                response.addHeader("Cache-Control", "no-cache");
                response.addHeader("Pragma", "no-cache");
            }
            content = "_BOSH_(\"" + StringEscapeUtils.escapeJavaScript(content) + "\")";
        }
        
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            System.out.println(new Date() + ": HTTP SENT(" + session.getStreamID().getID() + "): " + content);
        }

        final byte[] byteContent = content.getBytes(StandardCharsets.UTF_8);
        // BOSH communication should not use Chunked encoding.
        // This is prevented by explicitly setting the Content-Length header.
        response.setContentLength(byteContent.length);

        if (async) {
            response.getOutputStream().setWriteListener(new WriteListenerImpl(context, byteContent));
        } else {
            context.getResponse().getOutputStream().write(byteContent);
            context.getResponse().getOutputStream().flush();
            context.complete();
        }
    }

    private void sendError(HttpSession session, AsyncContext context, BoshBindingError bindingError)
            throws IOException
    {
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            System.out.println(new Date() + ": HTTP ERR(" + session.getStreamID().getID() + "): " + bindingError.getErrorType().getType() + ", " + bindingError.getCondition() + ".");
        }
        try {
            if ((session.getMajorVersion() == 1 && session.getMinorVersion() >= 6) || session.getMajorVersion() > 1)
            {
                final String errorBody = createErrorBody(bindingError.getErrorType().getType(), bindingError.getCondition());
                respond(session, context, errorBody, true);
            } else {
                sendLegacyError(context, bindingError);
            }
        }
        finally {
            if (bindingError.getErrorType() == BoshBindingError.Type.terminate) {
                Log.debug( "Closing session due to error: {}. Affected session: {}", bindingError, session );
                session.close();
            }
        }
    }

    protected static void sendLegacyError(AsyncContext context, BoshBindingError error, String message)
            throws IOException
    {
        final HttpServletResponse response = (HttpServletResponse) context.getResponse();
        if (message == null || message.trim().length() == 0) {
            response.sendError(error.getLegacyErrorCode());
        } else {
            response.sendError(error.getLegacyErrorCode(), message);
        }
        context.complete();
    }

    protected static void sendLegacyError(AsyncContext context, BoshBindingError error)
            throws IOException
    {
        sendLegacyError(context, error, null);
    }

    protected static String createErrorBody(String type, String condition) {
        final Element body = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        body.addAttribute("type", type);
        body.addAttribute("condition", condition);
        return body.asXML();
    }

    protected static long getLongAttribute(String value, long defaultValue) {
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

    protected static int getIntAttribute(String value, int defaultValue) {
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

    protected static String getRemoteAddress(AsyncContext context)
    {
        String remoteAddress = null;
        if (context.getRequest() != null && context.getRequest().getRemoteAddr() != null) {
            remoteAddress = context.getRequest().getRemoteAddr();
        }

        if (remoteAddress == null || remoteAddress.trim().length() == 0) {
            remoteAddress = "<UNKNOWN ADDRESS>";
        }

        return remoteAddress;
    }

    class ReadListenerImpl implements ReadListener {

        private final AsyncContext context;
        private final ByteArrayOutputStream outStream = new ByteArrayOutputStream(1024);
        private final String remoteAddress;

        ReadListenerImpl(AsyncContext context) {
            this.context = context;
            this.remoteAddress = getRemoteAddress(context);
        }

        @Override
        public void onDataAvailable() throws IOException {
            Log.trace("Data is available to be read from [" + remoteAddress + "]");

            final ServletInputStream inputStream = context.getRequest().getInputStream();

            byte b[] = new byte[1024];
            int length;
            while (inputStream.isReady() && (length = inputStream.read(b)) != -1) {
                outStream.write(b, 0, length);
            }
        }

        @Override
        public void onAllDataRead() throws IOException {
            Log.trace("All data has been read from [" + remoteAddress + "]");
            processContent(context, outStream.toString(StandardCharsets.UTF_8.name()));
        }

        @Override
        public void onError(Throwable throwable) {
            Log.warn("Error reading request data from [" + remoteAddress + "]", throwable);
            try {
                sendLegacyError(context, BoshBindingError.badRequest);
            } catch (IOException ex) {
                Log.debug("Error while sending an error to ["+remoteAddress +"] in response to an earlier data-read failure.", ex);
            }
        }
    }

    private static class WriteListenerImpl implements WriteListener {

        private final AsyncContext context;
        private final byte[] data;
        private final String remoteAddress;
        private volatile boolean written;

        public WriteListenerImpl(AsyncContext context, byte[] data) {
            this.context = context;
            this.data = data;
            this.remoteAddress = getRemoteAddress(context);
        }

        @Override
        public void onWritePossible() throws IOException {
            // This method may be invoked multiple times and by different threads, e.g. when writing large byte arrays.
            Log.trace("Data can be written to [" + remoteAddress + "]");
            ServletOutputStream servletOutputStream = context.getResponse().getOutputStream();
            while (servletOutputStream.isReady()) {
                // Make sure a write/complete operation is only done, if no other write is pending, i.e. if isReady() == true
                // Otherwise WritePendingException is thrown.
                if (!written) {
                    written = true;
                    servletOutputStream.write(data);
                    // After this write isReady() may return false, indicating the write is not finished.
                    // In this case onWritePossible() is invoked again as soon as the isReady() == true again,
                    // in which case we would only complete the request.
                } else {
                    context.complete();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Log.warn("Error writing response data to [" + remoteAddress + "]", throwable);
            context.complete();
        }
    }
}
