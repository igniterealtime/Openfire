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

package org.jivesoftware.admin;

import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.WebManager;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A simple filter which checks for the auth token in the user's session. If it's not there
 * the filter will redirect to the login page.
 */
public class AuthCheckFilter implements Filter {

    private static Set<String> excludes = new ConcurrentHashSet<String>();

    private ServletContext context;
    private String defaultLoginPage;

    /**
     * Adds a new string that when present in the requested URL will skip
     * the "is logged" checking.
     *
     * @param exclude the string to exclude.
     */
    public static void addExclude(String exclude) {
        excludes.add(exclude);
    }

    /**
     * Removes a string that when present in the requested URL will skip
     * the "is logged" checking.
     *
     * @param exclude the string that was being excluded.
     */
    public static void removeExclude(String exclude) {
        excludes.remove(exclude);
    }

    public void init(FilterConfig config) throws ServletException {
        context = config.getServletContext();
        defaultLoginPage = config.getInitParameter("defaultLoginPage");
        String excludesProp = config.getInitParameter("excludes");
        if (excludesProp != null) {
            StringTokenizer tokenizer = new StringTokenizer(excludesProp, ",");
            while (tokenizer.hasMoreTokens()) {
                String tok = tokenizer.nextToken().trim();
                excludes.add(tok);
            }
        }
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        // Reset the defaultLoginPage variable
        String loginPage = defaultLoginPage;
        if (loginPage == null) {
            loginPage = request.getContextPath() + "/login.jsp";
        }
        // Get the page we're on:
        String url = request.getRequestURL().toString();
        // See if it's contained in the exclude list. If so, skip filter execution
        boolean doExclude = false;
        for (String exclude : excludes) {
            if (url.indexOf(exclude) > -1) {
                doExclude = true;
                break;
            }
        }
        if (!doExclude) {
            WebManager manager = new WebManager();
            manager.init(request, response, request.getSession(), context);
            if (manager.getUser() == null) {
                response.sendRedirect(getRedirectURL(request, loginPage, null));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    public void destroy() {
    }

    private String getRedirectURL(HttpServletRequest request, String loginPage,
            String optionalParams)
    {
        StringBuilder buf = new StringBuilder();
        try {
            buf.append(request.getRequestURI());
            String qs = request.getQueryString();
            if (qs != null) {
                buf.append("?").append(qs);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        try {
            return loginPage + "?url=" + URLEncoder.encode(buf.toString(), "ISO-8859-1")
                    + (optionalParams != null ? "&"+optionalParams : "");
        }
        catch (Exception e) {
            Log.error(e);
            return null;
        }
    }
}
