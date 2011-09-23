/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.web;

import redstone.xmlrpc.XmlRpcServlet;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.apache.log4j.Logger;

import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 * @author Daniel Henninger
 */
@SuppressWarnings("serial")
public class GatewayXMLRPC extends XmlRpcServlet implements PropertyEventListener {

    static Logger Log = Logger.getLogger(GatewayXMLRPC.class);

    XMLRPCConduit conduit;

    @Override
    public void init(ServletConfig servletConfig) {
        try {
            super.init(servletConfig);
            conduit = new XMLRPCConduit();
            PropertyEventDispatcher.addListener(this);

            this.getXmlRpcServer().addInvocationHandler("Manager", conduit);

            AuthCheckFilter.addExclude("kraken/xml-rpc");
        }
        catch (ServletException e) {
            Log.error("Error while loading XMLRPC servlet: ", e);
        }
    }

    @Override
    public void destroy() {
        AuthCheckFilter.removeExclude("kraken/xml-rpc");
        PropertyEventDispatcher.removeListener(this);        
        conduit = null;
    }

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.gateway.xmlrpc.password") && conduit != null) {
            conduit.authPassword = (String)params.get("value");
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.gateway.xmlrpc.password") && conduit != null) {
            conduit.authPassword = null;
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        propertySet(property, params);
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        propertyDeleted(property, params);
    }

}
