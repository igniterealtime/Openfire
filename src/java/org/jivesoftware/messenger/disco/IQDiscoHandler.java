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
package org.jivesoftware.messenger.disco;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.messenger.handler.IQHandler;

/**
 * Base class for handling disco request. So far this class is not of much help since practically
 * all the main behavior is located in each subclass.
 *
 * @author Gaston Dombiak
 */
public abstract class IQDiscoHandler extends IQHandler {

    public XMPPServer localServer;

    public IQDiscoHandler(String name) {
        super(name);
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        return trackInfo;
    }

}
