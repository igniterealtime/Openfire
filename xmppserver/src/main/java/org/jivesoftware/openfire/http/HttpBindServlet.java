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

import org.apache.commons.text.StringEscapeUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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

        final HttpBindBody body;
        try {
            body = HttpBindBody.from( content );
        } catch (Exception ex) {
            Log.warn("Error parsing request data from [" + remoteAddress + "]", ex);
            sendLegacyError(context, BoshBindingError.badRequest);
            return;
        }

        final Long rid = body.getRid();
        if (rid == null || rid <= 0) {
            Log.info("Root element 'body' does not contain a valid RID attribute value in parsed request data from [" + remoteAddress + "]");
            sendLegacyError(context, BoshBindingError.badRequest, "Body-element is missing a RID (Request ID) value, or the provided value is a non-positive integer.");
            return;
        }

        // Process the parsed document.
        if (body.getSid() == null) {
            // When there's no Session ID, this should be a request to create a new session. If there's additional content,
            // something is wrong.
            if (!body.isEmpty()) {
                // invalid session request; missing sid
                Log.info("Root element 'body' does not contain a SID attribute value in parsed request data from [" + remoteAddress + "]");
                sendLegacyError(context, BoshBindingError.badRequest);
                return;
            }

            // We have a new session
            createNewSession(context, body);
        }
        else {
            // When there exists a Session ID, new data for an existing session is being provided.
            handleSessionRequest(context, body);
        }
    }

    protected void createNewSession(AsyncContext context, HttpBindBody body)
            throws IOException
    {
        final long rid = body.getRid();

        try {
            final HttpConnection connection = new HttpConnection(rid, context);

            SessionEventDispatcher.dispatchEvent( null, SessionEventDispatcher.EventType.pre_session_created, connection, context );

            connection.setSession(sessionManager.createSession(body, connection));
            if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
                Log.info("HTTP RECV(" + connection.getSession().getStreamID().getID() + "): " + body.asXML());
            }

            SessionEventDispatcher.dispatchEvent( connection.getSession(), SessionEventDispatcher.EventType.post_session_created, connection, context );
        }
        catch (UnauthorizedException | HttpBindException e) {
            // Server wasn't initialized yet.
            sendLegacyError(context, BoshBindingError.internalServerError, "Server has not finished initialization." );
        }
    }

    private void handleSessionRequest(AsyncContext context, HttpBindBody body)
            throws IOException
    {
        final String sid = body.getSid();
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            Log.info("HTTP RECV(" + sid + "): " + body.asXML());
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

        synchronized (session) {
            try {
                session.forwardRequest(body, context);
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
                response.setHeader("Cache-Control", "no-store");
                response.addHeader("Cache-Control", "no-cache");
                response.setHeader("Pragma", "no-cache");
            }
            content = "_BOSH_(\"" + StringEscapeUtils.escapeEcmaScript(content) + "\")";
        }
        
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            Log.info("HTTP SENT(" + session.getStreamID().getID() + "): " + content);
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
            Log.info("HTTP ERR(" + session.getStreamID().getID() + "): " + bindingError.getErrorType().getType() + ", " + bindingError.getCondition() + ".");
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
            if( Log.isTraceEnabled() ) {
                Log.trace("Data is available to be read from [" + remoteAddress + "]");
            }

            final ServletInputStream inputStream = context.getRequest().getInputStream();

            byte b[] = new byte[1024];
            int length;
            while (inputStream.isReady() && (length = inputStream.read(b)) != -1) {
                outStream.write(b, 0, length);
            }
        }

        @Override
        public void onAllDataRead() throws IOException {
            if( Log.isTraceEnabled() ) {
                Log.trace("All data has been read from [" + remoteAddress + "]");
            }
            processContent(context, outStream.toString(StandardCharsets.UTF_8.name()));
        }

        @Override
        public void onError(Throwable throwable) {
            if( Log.isWarnEnabled() ) {
                Log.warn("Error reading request data from [" + remoteAddress + "]", throwable);
            }
            try {
                sendLegacyError(context, BoshBindingError.badRequest);
            } catch (IOException ex) {
                Log.debug("Error while sending an error to ["+remoteAddress +"] in response to an earlier data-read failure.", ex);
            }
        }
    }

    private static class WriteListenerImpl implements WriteListener {

        private final AsyncContext context;
        private final InputStream data;
        private final String remoteAddress;

        public WriteListenerImpl(AsyncContext context, byte[] data) {
            this.context = context;
            this.data = new ByteArrayInputStream( data );
            this.remoteAddress = getRemoteAddress(context);
        }

        @Override
        public void onWritePossible() throws IOException {
            // This method may be invoked multiple times and by different threads, e.g. when writing large byte arrays.
            // Make sure a write/complete operation is only done, if no other write is pending, i.e. if isReady() == true
            // Otherwise WritePendingException is thrown.
            if( Log.isTraceEnabled() ) {
                Log.trace("Data can be written to [" + remoteAddress + "]");
            }
            synchronized ( context )
            {
                final ServletOutputStream servletOutputStream = context.getResponse().getOutputStream();
                while ( servletOutputStream.isReady() )
                {
                    final byte[] buffer = new byte[8*1024];
                    final int len = data.read( buffer );
                    if ( len < 0 )
                    {
                        // EOF - all done!
                        context.complete();
                        return;
                    }

                    // This is an async write, will never block.
                    servletOutputStream.write( buffer, 0, len );

                    // When isReady() returns false for the next iteration, the
                    // interface contract guarantees that onWritePossible() will
                    // be called once a write is possible again.
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if( Log.isWarnEnabled() ) {
                Log.warn("Error writing response data to [" + remoteAddress + "]", throwable);
            }
            synchronized ( context )
            {
                context.complete();
            }
        }
    }
}
