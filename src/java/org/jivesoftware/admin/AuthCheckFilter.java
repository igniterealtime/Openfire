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

import org.jivesoftware.util.WebManager;
import org.jivesoftware.util.Log;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.StringTokenizer;
import java.util.ArrayList;

/**
 * A simple filter which checks for the auth token in the user's session. If it's not there
 * the filter will redirect to the login page.
 */
public class AuthCheckFilter implements Filter {

    private ServletContext context;
    private String loginPage;
    private List<String> excludes;

    public void init(FilterConfig config) throws ServletException {
        context = config.getServletContext();
        loginPage = config.getInitParameter("loginPage");
        if (loginPage == null) {
            loginPage = "login.jsp";
        }
        String excludesProp = config.getInitParameter("excludes");
        if (excludesProp != null) {
            StringTokenizer tokenizer = new StringTokenizer(excludesProp, ",");
            excludes = new ArrayList<String>();
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
                response.sendRedirect(getRedirectURL(request,null));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    public void destroy() {
    }

    private String getRedirectURL(HttpServletRequest request, String optionalParams) {
        StringBuffer buf = new StringBuffer();
        try {
            StringBuffer rURL = request.getRequestURL();
            int pos = rURL.lastIndexOf("/");
            if ((pos+1) <= rURL.length()) {
                buf.append(rURL.substring(pos+1, rURL.length()));
            }
            String qs = request.getQueryString();
            if (qs != null) {
                buf.append("?").append(qs);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        try {
            String url= loginPage + "?url=" + URLEncoder.encode(buf.toString(), "ISO-8859-1")
                    + (optionalParams != null ? "&"+optionalParams : "");
            return url;
        }
        catch (Exception e) {
            Log.error(e);
            return null;
        }
    }
}
