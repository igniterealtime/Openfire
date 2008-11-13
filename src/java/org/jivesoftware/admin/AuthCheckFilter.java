/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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

    /**
     * Returns true if a URL passes an exclude rule.
     *
     * @param url the URL to test.
     * @param exclude the exclude rule.
     * @return true if the URL passes the exclude test.
     */
    public static boolean testURLPassesExclude(String url, String exclude) {
        // If the exclude rule includes a "?" character, the url must exactly match the exclude rule.
        // If the exclude rule does not contain the "?" character, we chop off everything starting at the first "?"
        // in the URL and then the resulting url must exactly match the exclude rule. If the exclude ends with a "*"
        // character then the URL is allowed if it exactly matches everything before the * and there are no ".."
        // characters after the "*". All data in the URL before
        // the "@" character is chopped.

        if (url.contains("@")) {
            url = url.substring(url.indexOf("@"));
        }

        if (exclude.endsWith("*")) {
            if (url.startsWith(exclude.substring(0, exclude.length()-1))) {
                // Now make sure that there are no ".." characters in the rest of the URL.
                if (!url.contains("..")) {
                    return true;
                }
            }
        }
        else if (exclude.contains("?")) {
            if (url.equals(exclude)) {
                return true;
            }
        }
        else {
            int paramIndex = url.indexOf("?");
            if (paramIndex != -1) {
                url = url.substring(0, paramIndex);
            }
            if (url.equals(exclude)) {
                return true;
            }
        }
        return false;
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
        String url = request.getRequestURI().substring(1);
        if (url.startsWith("plugins/")) {
            url = url.substring("plugins/".length());
        }
        // See if it's contained in the exclude list. If so, skip filter execution
        boolean doExclude = false;
        for (String exclude : excludes) {
            if (testURLPassesExclude(url, exclude)) {
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
