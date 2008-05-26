/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.plugin;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;

import java.io.File;
import java.util.Map;

/**
 * Debugger plugin that prints XML traffic to stdout. By default it will only print
 * raw XML traffic (by using a MINA filter). To turn on printing of interpreted XML
 * (i.e. parsed XML) just enable the system property <tt>plugin.debugger.interpretedAllowed</tt>.
 * There is no need to restart the plugin or the server.
 *
 * @author Gaston Dombiak
 */
public class DebuggerPlugin implements Plugin, PropertyEventListener {
    private RawPrintFilter defaultPortFilter;
    private RawPrintFilter oldPortFilter;
    private RawPrintFilter componentPortFilter;
    private RawPrintFilter multiplexerPortFilter;

    private InterpretedXMLPrinter interpretedPrinter;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        // Add filter to filter chain builder
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        defaultPortFilter = new RawPrintFilter("C2S");
        SocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        if (socketAcceptor != null) {
            socketAcceptor.getFilterChain().addBefore("xmpp", "rawDebugger", defaultPortFilter);
        }
        oldPortFilter = new RawPrintFilter("SSL");
        SocketAcceptor sslAcceptor = connManager.getSSLSocketAcceptor();
        if (sslAcceptor != null) {
            sslAcceptor.getFilterChain().addBefore("xmpp", "rawDebugger", oldPortFilter);
        }

        componentPortFilter = new RawPrintFilter("ExComp");
        SocketAcceptor componentAcceptor = connManager.getComponentAcceptor();
        if (componentAcceptor != null) {
            componentAcceptor.getFilterChain().addBefore("xmpp", "rawDebugger", componentPortFilter);
        }

        multiplexerPortFilter = new RawPrintFilter("CM");
        SocketAcceptor multiplexerAcceptor = connManager.getMultiplexerSocketAcceptor();
        if (multiplexerAcceptor != null) {
            multiplexerAcceptor.getFilterChain().addBefore("xmpp", "rawDebugger", multiplexerPortFilter);
        }

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
        if (connManager.getSocketAcceptor() != null &&
                connManager.getSocketAcceptor().getFilterChain().contains("rawDebugger")) {
            connManager.getSocketAcceptor().getFilterChain().remove("rawDebugger");
        }
        if (connManager.getSSLSocketAcceptor() != null &&
                connManager.getSSLSocketAcceptor().getFilterChain().contains("rawDebugger")) {
            connManager.getSSLSocketAcceptor().getFilterChain().remove("rawDebugger");
        }
        if (connManager.getComponentAcceptor() != null &&
                connManager.getComponentAcceptor().getFilterChain().contains("rawDebugger")) {
            connManager.getComponentAcceptor().getFilterChain().remove("rawDebugger");
        }
        if (connManager.getMultiplexerSocketAcceptor() != null &&
                connManager.getMultiplexerSocketAcceptor().getFilterChain().contains("rawDebugger")) {
            connManager.getMultiplexerSocketAcceptor().getFilterChain().remove("rawDebugger");
        }
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
