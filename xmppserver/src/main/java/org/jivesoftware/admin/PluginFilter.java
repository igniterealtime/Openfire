/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A servlet filter that plugin classes can use to dynamically register and un-register filter logic.
 *
 * This implementation assumes, but does not enforce, that filters installed by plugins are applied to URL patterns that
 * match the plugin. When filters installed by different plugins are applied to the same URL, the behavior of this
 * implementation is undetermined.
 *
 * @author Matt Tucker
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginFilter implements Filter {

    private static final Logger Log = LoggerFactory.getLogger( PluginFilter.class );

    private static final Map<String, List<Filter>> filters = new ConcurrentHashMap<>();

    /**
     * Adds a filter to the list of filters that will be run on every request of which the URL matches the URL that
     * is registered with this filter. More specifically, the request URL should be equal to, or start with, the filter
     * URL.
     *
     * Multiple filters can be registered on the same URL, in which case they will be executed in the order in which
     * they were added.
     *
     * Adding a filter does not initialize the plugin instance.
     *
     * @param filterUrl The URL pattern to which the filter is to be applied. Cannot be null nor an empty string.
     * @param filter The filter. Cannot be null.
     */
    public static void addPluginFilter( String filterUrl, Filter filter )
    {
        if ( filterUrl == null || filterUrl.isEmpty() || filter == null )
        {
            throw new IllegalArgumentException();
        }
        if ( !filters.containsKey( filterUrl ) )
        {
            filters.put( filterUrl, new ArrayList<>() );
        }

        final List<Filter> urlFilters = PluginFilter.filters.get( filterUrl );
        if ( urlFilters.contains( filter ) )
        {
            Log.warn( "Cannot add filter '{}' as it was already added for URL '{}'!", filter, filterUrl );
        }
        else
        {
            urlFilters.add( filter );
            Log.debug( "Added filter '{}' for URL '{}'", filter, filterUrl );
        }
    }

    /**
     * Removes a filter that is applied to a certain URL.
     *
     * Removing a filter does not destroy the plugin instance.
     *
     * @param filterUrl The URL pattern to which the filter is applied. Cannot be null nor an empty string.
     * @param filterClassName The filter class name. Cannot be null or empty string.
     * @return The filter instance that was removed, or null if the URL and name combination did not match a filter.
     */
    public static Filter removePluginFilter( String filterUrl, String filterClassName )
    {
        if ( filterUrl == null || filterUrl.isEmpty() || filterClassName == null || filterClassName.isEmpty() )
        {
            throw new IllegalArgumentException();
        }

        Filter result = null;
        if ( filters.containsKey( filterUrl ) )
        {
            final List<Filter> urlFilters = PluginFilter.filters.get( filterUrl );
            final Iterator<Filter> iterator = urlFilters.iterator();
            while ( iterator.hasNext() )
            {
                final Filter filter = iterator.next();
                if ( filter.getClass().getName().equals( filterClassName ) )
                {
                    iterator.remove();
                    result = filter; // assumed to be unique, but check the entire collection to avoid leaks.
                }
            }
            if ( urlFilters.isEmpty() )
            {
                filters.remove( filterUrl );
            }
        }

        if ( result == null )
        {
            Log.warn( "Unable to removed filter of class '{}' for URL '{}'. No such filter is present.", filterClassName, filterUrl );
        }
        else
        {
            Log.debug( "Removed filter '{}' for URL '{}'", result, filterUrl );
        }
        return result;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /**
     * This class is a Filter implementation itself. It acts as a dynamic proxy to filters that are registered by
     * Openfire plugins.
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
            FilterChain filterChain) throws IOException, ServletException
    {
        if ( servletRequest instanceof HttpServletRequest )
        {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            final String requestPath = ( httpServletRequest.getContextPath() + httpServletRequest.getServletPath() + httpServletRequest.getPathInfo() ).toLowerCase();

            final List<Filter> applicableFilters = new ArrayList<>();
            for ( final Map.Entry<String, List<Filter>> entry : filters.entrySet() )
            {
                String filterUrl = entry.getKey();
                if ( filterUrl.endsWith( "*" ))
                {
                    filterUrl = filterUrl.substring( 0, filterUrl.length() -1 );
                }
                filterUrl = filterUrl.toLowerCase();

                if ( requestPath.startsWith( filterUrl ) )
                {
                    applicableFilters.addAll(entry.getValue());
                }
            }
            if ( !applicableFilters.isEmpty() )
            {
                Log.debug( "Wrapping filter chain in order to run plugin-specific filters." );
                filterChain = new FilterChainInjector( filterChain, applicableFilters );
            }
        }
        else
        {
            Log.warn( "ServletRequest is not an instance of an HttpServletRequest." );
        }

        // Plugin filtering is done. Progress down the filter chain that was initially provided.
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
        // If the destroy method is being called, the Openfire instance is being shutdown.
        // Therefore, clear out the list of plugin filters.
        filters.clear();
    }

    /**
     * A wrapper that can be used to inject a list of filters into an existing a filter chain.
     *
     * An instance of this class is expected to be created within the execution of a 'parent' filter chain. After
     * instantiation, the caller is expected to invoke #doFilter once, after which all provided filters will be
     * invoked. Afterwards, the original filter chain (as supplied in the constructor) will be resumed.
     *
     * @author Guus der Kinderen, guus.der.kinderen@gmail.com
     */
    private static class FilterChainInjector implements FilterChain
    {
        private static final Logger Log = LoggerFactory.getLogger( FilterChainInjector.class );

        private final FilterChain parentChain;
        private final List<Filter> filters;
        private int index = 0;

        /**
         * Creates a new instance.
         *
         * @param parentChain the chain to which the filters are to be appended (cannot be null).
         * @param filters The filters to append (cannot be null, but can be empty).
         */
        private FilterChainInjector( FilterChain parentChain, List<Filter> filters )
        {
            if ( parentChain == null || filters == null )
            {
                throw new IllegalArgumentException();
            }
            this.parentChain = parentChain;
            this.filters = filters;
        }

        @Override
        public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException
        {
            if ( index < filters.size() )
            {
                Log.trace( "Executing injected filter {} of {}...", index + 1, filters.size() );
                filters.get( index++ ).doFilter( servletRequest, servletResponse, this );
            }
            else
            {
                Log.trace( "Executed all injected filters. Resuming original chain." );
                parentChain.doFilter( servletRequest, servletResponse );
            }
        }
    }
}
