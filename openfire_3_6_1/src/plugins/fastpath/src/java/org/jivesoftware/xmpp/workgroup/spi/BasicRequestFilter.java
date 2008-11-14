/**
 * $RCSfile$
 * $Revision: 18406 $
 * $Date: 2005-02-07 14:32:46 -0800 (Mon, 07 Feb 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.spi;

import org.jivesoftware.xmpp.workgroup.RequestFilter;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.xmpp.packet.PacketError;

public class BasicRequestFilter implements RequestFilter {
    public PacketError.Condition filter(Request request) {
        return null;
    }
}
