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
package org.jivesoftware.messenger.muc;

import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Interface for any object that can accept chat messages and presence
 * for delivery.
 *
 * @author Gaston Dombiak
 */
public interface ChatDeliverer {
    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     * @throws UnauthorizedException Thrown if unauthorized
     */
    void send(Message packet) throws UnauthorizedException;

    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     * @throws UnauthorizedException Thrown if unauthorized
     */
    void send(Presence packet) throws UnauthorizedException;

    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     * @throws UnauthorizedException Thrown if unauthorized
     */
    void send(IQ packet) throws UnauthorizedException;
}
