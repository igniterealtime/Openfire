/**
 * $RCSfile$
 * $Revision: 1709 $
 * $Date: 2005-07-26 11:55:27 -0700 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.admin;

import javax.servlet.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A servlet filter that plugin classes can use to dynamically register and un-register
 * filter logic. The filter logic that each plugin can register is fairly limited;
 * instead of having full control over the filter chain, each instance of
 * {@link SimpleFilter} only has the ability to use the ServletRequest and ServletResponse
 * objects and then return <tt>true</tt> if further filters in the chain should be run.
 *
 * @author Matt Tucker
 */
public class PluginFilter implements Filter {

    private static List<SimpleFilter> pluginFilters = new CopyOnWriteArrayList<SimpleFilter>();

    /**
     * Adds a filter to the list of filters that will be run on every request.
     * This method should be called by plugins when starting up.
     *
     * @param filter the filter.
     */
    public static void addPluginFilter(SimpleFilter filter) {
        pluginFilters.add(filter);
    }

    /**
     * Removes a filter from the list of filters that will be run on every request.
     * This method should be called by plugins when shutting down.
     *
     * @param filter the filter.
     */
    public static void removePluginFilter(SimpleFilter filter) {
        pluginFilters.remove(filter);
    }

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
            FilterChain filterChain) throws IOException, ServletException
    {
        boolean continueChain = true;
        // Process each plugin filter.
        for (SimpleFilter filter : pluginFilters) {
            if (!filter.doFilter(servletRequest, servletResponse)) {
                // The filter returned false so no further filters in the
                // chain should be run.
                continueChain = false;
                break;
            }
        }
        if (continueChain) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    public void destroy() {
        // If the destroy method is being called, the Openfire instance is being shutdown.
        // Therefore, clear out the list of plugin filters.
        pluginFilters.clear();
    }

    /**
     * A simplified version of a servlet filter. Instead of having full control
     * over the filter chain, a simple filter can only control whether further
     * filters in the chain are run.
     */
    public interface SimpleFilter {

        /**
         * The doFilter method of the Filter is called by the PluginFilter each time a
         * request/response pair is passed through the chain due to a client request
         * for a resource at the end of the chain. This method should return <tt>true</tt> if
         * the additional filters in the chain should be processed or <tt>false</tt>
         * if no additional filters should be run.<p>
         *
         * Note that the filter will apply to all requests for JSP pages in the admin console
         * and not just requests in the respective plugins. To only apply filtering to
         * individual plugins, examine the context path of the request and only filter
         * relevant requests.
         *
         * @param request the servlet request.
         * @param response the servlet response
         * @throws IOException if an IOException occurs.
         * @throws ServletException if a servlet exception occurs.
         * @return true if further filters in the chain should be run.
         */
        public boolean doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException;

    }
}