/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Information about tracker classes and the object to receive updates.</p>
 *
 * @author Iain Shigeoka
 */
public class TrackInfo {

    /**
     * The classes to use
     *
     * @see BasicModule#getTrackInfo();
     */
    private Map trackerClasses = new HashMap();

    /**
     * Create a new TrackInfo object.
     */
    public TrackInfo() {
    }

    /**
     * <p>Obtain the tracker classes that a service tracker can register.</p>
     * <p/>
     * <p>The map is keyed on Class to track, and the result is the name of
     * a field to assign the service to when it is found.</p>
     *
     * @return The tracker classes to be tracked and corresponding field names to alter
     */
    public Map getTrackerClasses() {
        return trackerClasses;
    }
}
