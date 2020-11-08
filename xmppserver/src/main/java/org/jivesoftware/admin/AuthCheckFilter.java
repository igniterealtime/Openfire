/*
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

package org.jivesoftware.admin;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.WebManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple filter which checks for the auth token in the user's session. If it's not there
 * the filter will redirect to the login page.
 */
public class AuthCheckFilter implements Filter {

    public static final SystemProperty<Class> SERVLET_REQUEST_AUTHENTICATOR = SystemProperty.Builder.ofType(Class.class)
        .setKey("adminConsole.servlet-request-authenticator")
        .setBaseClass(ServletRequestAuthenticator.class)
        .setDefaultValue(null)
        .setDynamic(true)
        .addListener(AuthCheckFilter::initAuthenticator)
        .build();

    private static ServletRequestAuthenticator servletRequestAuthenticator;

    private static void initAuthenticator(final Class clazz) {
        // Check if we need to reset the auth provider class
        if(clazz == null && servletRequestAuthenticator != null) {
            servletRequestAuthenticator = null;
        } else if (clazz != null && (servletRequestAuthenticator == null || !clazz.equals(servletRequestAuthenticator.getClass()))) {
            try {
                servletRequestAuthenticator = (ServletRequestAuthenticator)clazz.newInstance();
            }
            catch (final Exception e) {
                Log.error("Error loading ServletRequestAuthenticator {}", clazz.getName(), e);
                servletRequestAuthenticator = null;
            }
        }
    }

    private static final Logger Log = LoggerFactory.getLogger(AuthCheckFilter.class);
    private static AuthCheckFilter instance;

    private static Set<String> excludes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private final AdminManager adminManager;
    private final LoginLimitManager loginLimitManager;

    private ServletContext context;
    private String defaultLoginPage;

    public AuthCheckFilter() {
        this(AdminManager.getInstance(), LoginLimitManager.getInstance());
    }

    /* Exposed for test use only */
    AuthCheckFilter(final AdminManager adminManager, final LoginLimitManager loginLimitManager) {
        this.adminManager = adminManager;
        this.loginLimitManager = loginLimitManager;
        initAuthenticator(SERVLET_REQUEST_AUTHENTICATOR.getValue());
        AuthCheckFilter.instance = this;
    }

    /**
     * Returns a singleton instance of the AuthCheckFilter.
     *
     * @return an instance.
     */
    public static AuthCheckFilter getInstance() {
        return instance;
    }

    /**
     * Indicates if the currently-installed ServletRequestAuthenticator is an instance of a specific class.
     *
     * @param clazz the class to check
     * @return {@code true} if the currently-installed ServletRequestAuthenticator is an instance of clazz, otherwise {@code false}.
     */
    public static boolean isServletRequestAuthenticatorInstanceOf(Class<? extends ServletRequestAuthenticator> clazz) {
        final AuthCheckFilter instance = getInstance();
        if (instance == null) {
            // We've not yet been instantiated
            return false;
        }
        return servletRequestAuthenticator != null && clazz.isAssignableFrom(servletRequestAuthenticator.getClass());
    }

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

        if (exclude.endsWith("*")) {
            if (url.startsWith(exclude.substring(0, exclude.length()-1))) {
                // Now make sure that there are no ".." characters in the rest of the URL.
                if (!url.contains("..") && !url.toLowerCase().contains("%2e")) {
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

    @Override
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

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        // Do not allow framing; OF-997
        response.setHeader("X-Frame-Options", JiveGlobals.getProperty("adminConsole.frame-options", "same"));
        // Reset the defaultLoginPage variable
        String loginPage = defaultLoginPage;
        if (loginPage == null) {
            loginPage = request.getContextPath() + (AuthFactory.isOneTimeAccessTokenEnabled() ? "/loginToken.jsp" : "/login.jsp" );
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
            boolean haveOneTimeToken = manager.getAuthToken() instanceof AuthToken.OneTimeAuthToken;
            User loggedUser = manager.getUser();
            boolean loggedAdmin = loggedUser == null ? false : adminManager.isUserAdmin(loggedUser.getUsername(), true);
            if (!haveOneTimeToken && !loggedAdmin && !authUserFromRequest(request)) {
                response.sendRedirect(getRedirectURL(request, loginPage, null));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean authUserFromRequest(final HttpServletRequest request) {

        final String userFromRequest = servletRequestAuthenticator == null ? null : servletRequestAuthenticator.authenticateRequest(request);
        if (userFromRequest == null) {
            // The user is not authenticated
            return false;
        }

        if (!adminManager.isUserAdmin(userFromRequest, true)) {
            // The user is not authorised
            Log.warn("The user '" + userFromRequest + "' is not an Openfire administrator.");
            return false;
        }

        // We're authenticated and authorised, so record the login,
        loginLimitManager.recordSuccessfulAttempt(userFromRequest, request.getRemoteAddr());

        // Set the auth token
        request.getSession().setAttribute("jive.admin.authToken", AuthToken.generateUserToken( userFromRequest ));

        // And proceed
        return true;
    }

    @Override
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
                buf.append('?').append(qs);
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        try {
            return loginPage + "?url=" + URLEncoder.encode(buf.toString(), "ISO-8859-1")
                    + (optionalParams != null ? "&"+optionalParams : "");
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            return null;
        }
    }
}
