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
package org.jivesoftware.messenger.starter;

import org.jivesoftware.messenger.container.starter.ServerStarter;

/**
 * <p>A server starter for Jive Messenger running in standalone
 * mode. The starter is primarily responsible for inspecting the
 * commandline args (currently ignored) and starting the server
 * with the correct bootstrap container name.</p>
 *
 * @author Iain Shigeoka
 */
public class XMPPServerStarter extends ServerStarter {

    /**
     * Starts Messenger.
     *
     * @param args The command line arguments (currently unused)
     */
    public static void main(String[] args) {
        // Update Build to use the weblogic parser.
        //System.setProperty("javax.xml.stream.XMLInputFactory", "weblogic.xml.stax.XMLStreamInputFactory");
        //System.setProperty("javax.xml.stream.XMLOutputFactory", "weblogic.xml.stax.XMLStreamOutputFactory");
        new XMPPServerStarter().start();
    }


    protected String getBootContainerClassName() {
        return "org.jivesoftware.messenger.XMPPBootContainer";
    }
}
