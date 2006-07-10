/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 *
 * Heavily inspired by joscardemo of the Joust Project: http://joust.kano.net/
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import org.jivesoftware.util.Log;

import net.kano.joscar.snac.SnacRequest;

public interface PendingSnacListener {
    void dequeueSnacs(SnacRequest[] pending);
}
