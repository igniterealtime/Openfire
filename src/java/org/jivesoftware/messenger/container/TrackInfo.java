/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
