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

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.Map;

import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginManagerListener;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.COMPRESSION_FILTER_NAME;
import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.TLS_FILTER_NAME;

/**
 * Debugger plugin that prints XML traffic to stdout. By default it will only print
 * raw XML traffic (by using a MINA filter). To turn on printing of interpreted XML
 * (i.e. parsed XML) just enable the system property <tt>plugin.debugger.interpretedAllowed</tt>.
 * There is no need to restart the plugin or the server.
 *
 * @author Gaston Dombiak
 */
public class DebuggerPlugin implements Plugin, PropertyEventListener {

    public static final Logger Log = LoggerFactory.getLogger( DebuggerPlugin.class );

    private RawPrintFilter defaultPortFilter;
    private RawPrintFilter oldPortFilter;
    private RawPrintFilter componentPortFilter;
    private RawPrintFilter multiplexerPortFilter;

    private InterpretedXMLPrinter interpretedPrinter;

    public void initializePlugin(final PluginManager pluginManager, final File pluginDirectory) {
        if (pluginManager.isExecuted()) {
            addInterceptors();
        } else {
            pluginManager.addPluginManagerListener(new PluginManagerListener() {
                public void pluginsMonitored() {
                    // Stop listening for plugin events
                    pluginManager.removePluginManagerListener(this);
                    // Start listeners
                    addInterceptors();
                }
            });
        }
    }

    protected void addFilterToChain( final SocketAcceptor acceptor, final String filterName, final IoFilter filter )
    {
        if ( acceptor == null )
        {
            Log.debug( "Not adding filter '{}' to acceptor that is null.", filterName );
            return;
        }

        final DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
        if ( chain.contains( COMPRESSION_FILTER_NAME ) )
        {
            Log.debug( "Adding filter '{}' as the first filter after the compression filter in acceptor {}", filterName, acceptor );
            chain.addAfter( COMPRESSION_FILTER_NAME, filterName, filter );
        }
        else if ( chain.contains( TLS_FILTER_NAME ) )
        {
            Log.debug( "Adding filter '{}' as the first filter after the TLS filter in acceptor {}", filterName, acceptor );
            chain.addAfter( TLS_FILTER_NAME, filterName, filter );
        }
        else
        {
            Log.debug( "Adding filter '{}' as the last filter in acceptor {}", filterName, acceptor );
            chain.addLast( filterName, filter );
        }
    }

    protected void removeFilterFromChain( final SocketAcceptor acceptor, final String filterName )
    {
        if ( acceptor == null )
        {
            Log.debug( "Not removing filter '{}' from acceptor that is null.", filterName );
            return;
        }

        if ( acceptor.getFilterChain().contains( filterName ) )
        {
            Log.debug( "Removing filter '{}' from acceptor {}", filterName, acceptor );
            acceptor.getFilterChain().remove( filterName );
        }
        else
        {
            Log.debug( "Unable to remove non-existing filter '{}' from acceptor {}", filterName, acceptor );
        }
    }

    private void addInterceptors()
    {
        defaultPortFilter = new RawPrintFilter("C2S");
        oldPortFilter = new RawPrintFilter("SSL");
        componentPortFilter = new RawPrintFilter("ExComp");
        multiplexerPortFilter = new RawPrintFilter("CM");

        // Add filter to filter chain builder
        final ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();

        addFilterToChain( connManager.getSocketAcceptor(), RawPrintFilter.FILTER_NAME, defaultPortFilter );
        addFilterToChain( connManager.getSSLSocketAcceptor(), RawPrintFilter.FILTER_NAME, oldPortFilter );
        addFilterToChain( connManager.getComponentAcceptor(), RawPrintFilter.FILTER_NAME, componentPortFilter );
        addFilterToChain( connManager.getMultiplexerSocketAcceptor(), RawPrintFilter.FILTER_NAME, multiplexerPortFilter );

        interpretedPrinter = new InterpretedXMLPrinter();
        if (JiveGlobals.getBooleanProperty("plugin.debugger.interpretedAllowed")) {
            // Add the packet interceptor that prints interpreted XML
            InterceptorManager.getInstance().addInterceptor(interpretedPrinter);
        }
        // Listen to property events
        PropertyEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        // Stop listening to property events
        PropertyEventDispatcher.removeListener(this);
        // Remove filter from filter chain builder
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        removeFilterFromChain( connManager.getSocketAcceptor(), RawPrintFilter.FILTER_NAME );
        removeFilterFromChain( connManager.getSSLSocketAcceptor(), RawPrintFilter.FILTER_NAME );
        removeFilterFromChain( connManager.getComponentAcceptor(), RawPrintFilter.FILTER_NAME );
        removeFilterFromChain( connManager.getMultiplexerSocketAcceptor(), RawPrintFilter.FILTER_NAME );

        // Remove the filters from existing sessions
        if (defaultPortFilter != null) {
            defaultPortFilter.shutdown();
        }
        if (oldPortFilter != null) {
            oldPortFilter.shutdown();
        }
        if (componentPortFilter != null) {
            componentPortFilter.shutdown();
        }
        if (multiplexerPortFilter != null) {
            multiplexerPortFilter.shutdown();
        }

        // Remove the packet interceptor that prints interpreted XML
        InterceptorManager.getInstance().removeInterceptor(interpretedPrinter);

        defaultPortFilter = null;
        oldPortFilter = null;
        componentPortFilter = null;
        interpretedPrinter = null;
        multiplexerPortFilter = null;
    }

    public RawPrintFilter getDefaultPortFilter() {
        return defaultPortFilter;
    }

    public RawPrintFilter getOldPortFilter() {
        return oldPortFilter;
    }

    public RawPrintFilter getComponentPortFilter() {
        return componentPortFilter;
    }

    public RawPrintFilter getMultiplexerPortFilter() {
        return multiplexerPortFilter;
    }

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.debugger.interpretedAllowed")) {
            if (Boolean.parseBoolean((String) params.get("value"))) {
                InterceptorManager.getInstance().addInterceptor(interpretedPrinter);
            }
            else {
                InterceptorManager.getInstance().removeInterceptor(interpretedPrinter);
            }
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.debugger.interpretedAllowed")) {
            InterceptorManager.getInstance().removeInterceptor(interpretedPrinter);
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }
}
