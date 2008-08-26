/**
 * $RCSfile: $
 * $Revision: 2772 $
 * $Date: 2005-09-05 01:50:45 -0300 (Mon, 05 Sep 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import org.xmpp.packet.IQ;

/**
 * An IQResultListener will be invoked when a previously IQ packet sent by the server was answered.
 * Use {@link IQRouter#addIQResultListener(String, IQResultListener)} to add a new listener that
 * will process the answer to the IQ packet being sent. The listener will automatically be
 * removed from the {@link IQRouter} as soon as a reply for the sent IQ packet is received. The
 * reply can be of type RESULT or ERROR.
 *
 * @author Gaston Dombiak
 */
public interface IQResultListener {

    /**
     * Notification method indicating that a previously sent IQ packet has been answered.
     * The received IQ packet might be of type ERROR or RESULT.
     *
     * @param packet the IQ packet answering a previously sent IQ packet.
     */
    void receivedAnswer(IQ packet);

    /**
    * Notification method indicating that a predefined time has passed without
    * receiving answer to a previously sent IQ packet.
    *
    * @param packetId The packet id of a previously sent IQ packet that wasn't answered.
    */
    void answerTimeout(String packetId);
}
