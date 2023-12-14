/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
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

    /**
     * List of IP addresses that are not allowed to access the admin console.
     */
    public static final SystemProperty<Set<String>> IP_ACCESS_BLOCKLIST = SystemProperty.Builder.ofType(Set.class)
        .setKey("adminConsole.access.ip-blocklist")
        .setDefaultValue(Collections.emptySet())
        .setDynamic(true)
        .buildSet(String.class);

    /**
     * List of IP addresses that are allowed to access the admin console. When empty, this list is ignored.
     */
    public static final SystemProperty<Set<String>> IP_ACCESS_ALLOWLIST = SystemProperty.Builder.ofType(Set.class)
        .setKey("adminConsole.access.ip-allowlist")
        .setDefaultValue(Collections.emptySet())
        .setDynamic(true)
        .buildSet(String.class);

    /**
     * Controls if IP Access lists are applied to excluded URLs.
     */
    public static final SystemProperty<Boolean> IP_ACCESS_IGNORE_EXCLUDES = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("adminConsole.access.ignore-excludes")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    /**
     * Controls whether wildcards are allowed in URLs that are excluded from auth checks.
     */
    public static final SystemProperty<Boolean> ALLOW_WILDCARDS_IN_EXCLUDES = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("adminConsole.access.allow-wildcards-in-excludes")
        .setDefaultValue(false)
        .setDynamic(true)
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
        // If the url doesn't decode to UTF-8 then return false, it could be trying to get around our rules with nonstandard encoding
        // If the exclude rule includes a "?" character, the url must exactly match the exclude rule.
        // If the exclude rule does not contain the "?" character, we chop off everything starting at the first "?"
        // in the URL and then the resulting url must exactly match the exclude rule. If the exclude ends with a "*"
        // (wildcard) character, and wildcards are allowed in excludes, then the URL is allowed if it exactly
        // matches everything before the * and there are no ".." even encoded ones characters after the "*".

        String decodedUrl = null;
        try {
            decodedUrl = URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            return false;
        }

        if (exclude.endsWith("*") && ALLOW_WILDCARDS_IN_EXCLUDES.getValue()) {
            if (url.startsWith(exclude.substring(0, exclude.length()-1))) {
                // Now make sure that there are no ".." characters in the rest of the URL.
                if (!decodedUrl.contains("..")) {
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
                addExclude(tok);
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
        response.setHeader("X-Frame-Options", JiveGlobals.getProperty("adminConsole.frame-options", "SAMEORIGIN"));
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

        if (!doExclude || IP_ACCESS_IGNORE_EXCLUDES.getValue()) {
            if (!passesBlocklist(req) || !passesAllowList(req)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
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
        // reset excludes to an empty set to prevent state carry over
        excludes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
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

    /**
     * Verifies that the remote address of the request is <em>not</em> on the blocklist.
     *
     * If this method returns 'false', the request should not be allowed to be serviced.
     *
     * @param req The request for which the check the remote address.
     * @return true if the remote address of the request is not on the blacklist.
     */
    public static boolean passesBlocklist(@Nonnull final ServletRequest req) {
        // In a proxied setup, org.jivesoftware.openfire.container.AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED should be
        // set to 'true' to have the below report the true 'peer' address.
        final String remoteAddr = removeBracketsFromIpv6Address(req.getRemoteAddr());
        final boolean result = !isOnList(IP_ACCESS_BLOCKLIST.getValue(), remoteAddr);
        Log.debug("IP address '{}' {} pass the block list.", remoteAddr, result ? "does" : "does not");
        return result;
    }

    /**
     * Verifies that the remote address of the request is either on the allowlist, or the allowlist is empty.
     *
     * If this method returns 'false', the request should not be allowed to be serviced.
     *
     * @param req The request for which the check the remote address.
     * @return true if the remote address of the request is on the allowlist, or when the allowlist is empty.
     */
    public static boolean passesAllowList(@Nonnull final ServletRequest req) {
        // In a proxied setup, org.jivesoftware.openfire.container.AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED should be
        // set to 'true' to have the below report the true 'peer' address.
        final String remoteAddr = removeBracketsFromIpv6Address(req.getRemoteAddr());
        final Set<String> allowList = IP_ACCESS_ALLOWLIST.getValue();
        final boolean result = allowList.isEmpty() || isOnList(allowList, remoteAddr);
        Log.debug("IP address '{}' {} pass the allow list.", remoteAddr, result ? "does" : "does not");
        return result;
    }

    /**
     * Checks if a particular IP address is on a list of addresses.
     *
     * The IP address is expected to be an IPv4 or IPv6 address. The list can contain IPv4 and IPv6 addresses, but also
     * IPv4 and IP46 address ranges. Ranges can be expressed as dash separated strings (eg: "192.168.0.0-192.168.255.255")
     * or in CIDR notation (eg: "192.168.0.0/16").
     *
     * @param list The list of addresses
     * @param ipAddress the address to check
     * @return <tt>true</tt> if the address is detected in the list, otherwise <tt>false</tt>.
     */
    public static boolean isOnList(@Nonnull final Set<String> list, @Nonnull final String ipAddress) {
        Ipv4 remoteIpv4;
        try {
            remoteIpv4 = Ipv4.of(ipAddress);
        } catch (IllegalArgumentException e) {
            Log.trace("Address '{}' is not an IPv4 address.", ipAddress);
            remoteIpv4 = null;
        }
        Ipv6 remoteIpv6;
        try {
            remoteIpv6 = Ipv6.of(ipAddress);
        } catch (IllegalArgumentException e) {
            Log.trace("Address '{}' is not an IPv6 address.", ipAddress);
            remoteIpv6 = null;
        }

        if (remoteIpv4 == null && remoteIpv6 == null) {
            Log.warn("Unable to parse '{}' as an IPv4 or IPv6 address!", ipAddress);
        }

        for (final String item : list) {
            // Check if the remote address is an exact match on the list.
            if (item.equals(ipAddress)) {
                return true;
            }

            // Check if the remote address is a match for an address range on the list.
            if (remoteIpv4 != null) {
                Ipv4Range range;
                try {
                    range = Ipv4Range.parse(item);
                } catch (IllegalArgumentException e) {
                    Log.trace("List entry '{}' is not an IPv4 range.", item);
                    range = null;
                }
                if (range != null && range.contains(remoteIpv4)) {
                    return true;
                }
            }
            if (remoteIpv6 != null) {
                Ipv6Range range;
                try {
                    range = Ipv6Range.parse(item);
                } catch (IllegalArgumentException e) {
                    Log.trace("List entry '{}' is not an IPv6 range.", item);
                    range = null;
                }
                if (range != null && range.contains(remoteIpv6)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * When the provided input is an IPv6 literal that is enclosed in brackets (the [] style as expressed in
     * https://tools.ietf.org/html/rfc2732 and https://tools.ietf.org/html/rfc6874), this method returns the value
     * stripped from those brackets (the IPv6 address, instead of the literal). In all other cases, the input value is
     * returned.
     *
     * @param address The value from which to strip brackets.
     * @return the input value, stripped from brackets if applicable.
     */
    @Nonnull
    public static String removeBracketsFromIpv6Address(@Nonnull final String address)
    {
        final String result;
        if (address.startsWith("[") && address.endsWith("]")) {
            result = address.substring(1, address.length()-1);
            try {
                Ipv6.parse(result);
                // The remainder is a valid IPv6 address. Return the original value.
                return result;
            } catch (IllegalArgumentException e) {
                // The remainder isn't a valid IPv6 address. Return the original value.
                return address;
            }
        }
        // Not a bracket-enclosed string. Return the original input.
        return address;
    }

    public static void loadSetupExcludes() {
        Arrays.stream(JiveGlobals.setupExcludePaths).forEach(AuthCheckFilter::addExclude);
    }


}
