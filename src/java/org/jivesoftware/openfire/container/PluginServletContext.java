/*
 * Copyright 2016 IgniteRealtime.org
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

package org.jivesoftware.openfire.container;

import javax.servlet.*;
import javax.servlet.descriptor.JspConfigDescriptor;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * A Servlet Context to be used by Openfire plugins.
 *
 * This implementation is used do load resources from the plugin classpath. Other functionality is delegated to a proxy
 * implementation.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginServletContext implements ServletContext
{
    protected final ServletContext proxy;
    private final PluginManager pluginManager;
    private final Plugin plugin;

    public PluginServletContext( ServletContext proxy, PluginManager pluginManager, Plugin plugin )
    {
        this.proxy = proxy;
        this.pluginManager = pluginManager;
        this.plugin = plugin;
    }

    /**
     * The plugin classloader is an URL classloadeer, which will do lookups in directories with entries like these:
     * jar:file:/home/guus/github/Openfire/target/openfire/plugins/oauthresourceserver/lib/plugin-oauthresourceserver.jar!/
     * (note the trailing slash).
     *
     * To prevent lookup failures, strip any leading slash (which, when concatinated, would result "//").
     *
     * @param input A string (cannot be null)
     * @return The string without the first leading slash.
     */
    protected static String stripLeadingSlash( String input ) {
        if (input.startsWith("/"))
        {
            return input.substring(1);
        }
        else{
            return input;
        }
    }
    @Override
    public String getContextPath()
    {
        return proxy.getContextPath();
    }

    @Override
    public ServletContext getContext( String s )
    {
        return proxy.getContext( s );
    }

    @Override
    public int getMajorVersion()
    {
        return proxy.getMajorVersion();
    }

    @Override
    public int getMinorVersion()
    {
        return proxy.getMinorVersion();
    }

    @Override
    public int getEffectiveMajorVersion()
    {
        return proxy.getEffectiveMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion()
    {
        return proxy.getEffectiveMinorVersion();
    }

    @Override
    public String getMimeType( String s )
    {
        return proxy.getMimeType( s );
    }

    @Override
    public Set<String> getResourcePaths( String s )
    {
        final String pluginPath = "/plugins/" + PluginMetadataHelper.getName(plugin) + "/";
        final Set<String> proxyResults = proxy.getResourcePaths( pluginPath + s );
        final Set<String> results = new HashSet<>();
        for ( final String proxyResult : proxyResults )
        {
            results.add( proxyResult.replaceFirst( pluginPath, "" ) );
        }

        return results;
    }

    @Override
    public URL getResource( String s ) throws MalformedURLException
    {
        return pluginManager.getPluginClassloader( plugin ).getResource( stripLeadingSlash( s ) );
    }

    @Override
    public InputStream getResourceAsStream( String s )
    {
        return pluginManager.getPluginClassloader( plugin ).getResourceAsStream( stripLeadingSlash( s ) );
    }

    @Override
    public RequestDispatcher getRequestDispatcher( String s )
    {
        return proxy.getRequestDispatcher( s );
    }

    @Override
    public RequestDispatcher getNamedDispatcher( String s )
    {
        return proxy.getNamedDispatcher( s );
    }

    @Override
    public Servlet getServlet( String s ) throws ServletException
    {
        return proxy.getServlet( s );
    }

    @Override
    public Enumeration<Servlet> getServlets()
    {
        return proxy.getServlets();
    }

    @Override
    public Enumeration<String> getServletNames()
    {
        return proxy.getServletNames();
    }

    @Override
    public void log( String s )
    {
        proxy.log( s );
    }

    @Override
    public void log( Exception e, String s )
    {
        proxy.log( e, s );
    }

    @Override
    public void log( String s, Throwable throwable )
    {
        proxy.log( s, throwable );
    }

    @Override
    public String getRealPath( String s )
    {
        return null;
    }

    @Override
    public String getServerInfo()
    {
        return proxy.getServerInfo();
    }

    @Override
    public String getInitParameter( String s )
    {
        return proxy.getInitParameter( s );
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return proxy.getInitParameterNames();
    }

    @Override
    public boolean setInitParameter( String s, String s1 )
    {
        return proxy.setInitParameter( s, s1 );
    }

    @Override
    public Object getAttribute( String s )
    {
        return proxy.getAttribute( s );
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return proxy.getAttributeNames();
    }

    @Override
    public void setAttribute( String s, Object o )
    {
        proxy.setAttribute( s, o );
    }

    @Override
    public void removeAttribute( String s )
    {
        proxy.removeAttribute( s );
    }

    @Override
    public String getServletContextName()
    {
        return proxy.getServletContextName();
    }

    @Override
    public ServletRegistration.Dynamic addServlet( String s, String s1 )
    {
        return proxy.addServlet( s, s1 );
    }

    @Override
    public ServletRegistration.Dynamic addServlet( String s, Servlet servlet )
    {
        return proxy.addServlet( s, servlet );
    }

    @Override
    public ServletRegistration.Dynamic addServlet( String s, Class<? extends Servlet> aClass )
    {
        return proxy.addServlet( s, aClass );
    }

    @Override
    public <T extends Servlet> T createServlet( Class<T> aClass ) throws ServletException
    {
        return proxy.createServlet( aClass );
    }

    @Override
    public ServletRegistration getServletRegistration( String s )
    {
        return proxy.getServletRegistration( s );
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        return proxy.getServletRegistrations();
    }

    @Override
    public FilterRegistration.Dynamic addFilter( String s, String s1 )
    {
        return proxy.addFilter( s, s1 );
    }

    @Override
    public FilterRegistration.Dynamic addFilter( String s, Filter filter )
    {
        return proxy.addFilter( s, filter );
    }

    @Override
    public FilterRegistration.Dynamic addFilter( String s, Class<? extends Filter> aClass )
    {
        return proxy.addFilter( s, aClass );
    }

    @Override
    public <T extends Filter> T createFilter( Class<T> aClass ) throws ServletException
    {
        return proxy.createFilter( aClass );
    }

    @Override
    public FilterRegistration getFilterRegistration( String s )
    {
        return proxy.getFilterRegistration( s );
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        return proxy.getFilterRegistrations();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig()
    {
        return proxy.getSessionCookieConfig();
    }

    @Override
    public void setSessionTrackingModes( Set<SessionTrackingMode> set )
    {
        proxy.setSessionTrackingModes( set );
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        return proxy.getDefaultSessionTrackingModes();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        return proxy.getEffectiveSessionTrackingModes();
    }

    @Override
    public void addListener( String s )
    {
        proxy.addListener( s );
    }

    @Override
    public <T extends EventListener> void addListener( T t )
    {
        proxy.addListener( t );
    }

    @Override
    public void addListener( Class<? extends EventListener> aClass )
    {
        proxy.addListener( aClass );
    }

    @Override
    public <T extends EventListener> T createListener( Class<T> aClass ) throws ServletException
    {
        return proxy.createListener( aClass );
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor()
    {
        return proxy.getJspConfigDescriptor();
    }

    @Override
    public ClassLoader getClassLoader()
    {
        return pluginManager.getPluginClassloader( plugin );
    }

    @Override
    public void declareRoles( String... strings )
    {
        proxy.declareRoles( strings );
    }

    @Override
    public String getVirtualServerName()
    {
        return proxy.getVirtualServerName();
    }
}
