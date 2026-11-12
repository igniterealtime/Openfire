/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.WebManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for the admin console's Read-only Certificate Stores page, which sets whether Openfire may modify the
 * certificate stores it uses ({@link CertificateStoreManager#READ_ONLY}).
 *
 * @author Matthew Vivian
 */
@WebServlet(value = "/security-certificate-stores-read-only.jsp")
public class CertificateStoresReadOnlyServlet extends HttpServlet
{
    /**
     * The JSP that renders the page.
     */
    static final String PAGE = "security-certificate-stores-read-only-page.jsp";

    /**
     * Where a successful save redirects to, so that the page shows its success message and a reload does not submit
     * the form again.
     */
    static final String SAVED_REDIRECT = "security-certificate-stores-read-only.jsp?saved=true";

    /**
     * The name of the read-only checkbox.
     */
    static final String CHECKBOX_PARAMETER = "readOnly";

    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response ) throws ServletException, IOException
    {
        render( request, response, new HashMap<>() );
    }

    @Override
    protected void doPost( final HttpServletRequest request, final HttpServletResponse response ) throws ServletException, IOException
    {
        final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf" );
        final String csrfParam = request.getParameter( "csrf" );
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) )
        {
            final Map<String, String> errors = new HashMap<>();
            errors.put( "csrf", "CSRF Failure!" );
            render( request, response, errors );
            return;
        }

        // An unticked checkbox is not submitted, so an absent parameter means the setting is to be off.
        final boolean readOnly = ParamUtils.getBooleanParameter( request, CHECKBOX_PARAMETER, false );
        if ( CertificateStoreManager.READ_ONLY.getValue() != readOnly )
        {
            CertificateStoreManager.READ_ONLY.setValue( readOnly );
            logEvent( request, response, "Updated the read-only setting of the certificate stores", CertificateStoreManager.READ_ONLY.getKey() + " = " + readOnly );
        }
        response.sendRedirect( SAVED_REDIRECT );
    }

    /**
     * Writes an event to the admin console's audit log.
     *
     * @param request the request that made the change
     * @param response the response to the request
     * @param summary a summary of the event
     * @param details the details of the event
     */
    protected void logEvent( final HttpServletRequest request, final HttpServletResponse response, final String summary, final String details )
    {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init( request, response, session, session.getServletContext() );
        webManager.logEvent( summary, details );
    }

    private void render( final HttpServletRequest request, final HttpServletResponse response, final Map<String, String> errors ) throws ServletException, IOException
    {
        final String csrf = StringUtils.randomString( 16 );
        CookieUtils.setCookie( request, response, "csrf", csrf, -1 );
        request.setAttribute( "csrf", csrf );
        request.setAttribute( "errors", errors );
        request.setAttribute( "readOnly", CertificateStoreManager.READ_ONLY.getValue() );
        request.setAttribute( "checkboxParameter", CHECKBOX_PARAMETER );
        request.getRequestDispatcher( PAGE ).forward( request, response );
    }
}
