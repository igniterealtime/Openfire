/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;

/**
 * Central manager for JMX MBeans.
 *
 * @author Iain Shigeoka
 */
public class MBeanManager {

//    private static MBeanServer server;

    static {
//        server = MBeanServerFactory.createMBeanServer();
        // For now, we'll run an HTMLAdaptor server so that we easily interact with
        // the MBean server.
//        int portNumber = 8090;
//        HtmlAdaptorServer html = new HtmlAdaptorServer(portNumber);
//        ObjectName html_name = null;
//        try {
//            html_name = new ObjectName("Adaptor:name=html,port=" + portNumber);
//            server.registerMBean(html, html_name);
//        }
//        catch (Exception e) {
//            Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
//        }
//        html.start();
    }

    /**
     * Register an MBean with the MBeanServer.
     *
     * @param object     the MBean to register.
     * @param objectName a valid MBean object name.
     */
    public static void registerMBean(Object object, String objectName) {
        try {
//            server.registerMBean(object, new ObjectName(objectName));
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    private MBeanManager() {

    }
}
