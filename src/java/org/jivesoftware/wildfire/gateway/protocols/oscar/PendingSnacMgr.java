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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingSnacMgr {
    protected Map snacs = new HashMap();

    public boolean isPending(int familyCode) {
        Integer family = new Integer(familyCode);

        return snacs.containsKey(family);
    }

    public void add(SnacRequest request) {
        Integer family = new Integer(request.getCommand().getFamily());

        List pending = (List) snacs.get(family);

        pending.add(request);
    }

    public SnacRequest[] getPending(int familyCode) {
        Integer family = new Integer(familyCode);

        List pending = (List) snacs.get(family);

        return (SnacRequest[]) pending.toArray(new SnacRequest[0]);
    }

    public void setPending(int familyCode, boolean pending) {
        Integer family = new Integer(familyCode);

        if (pending) snacs.put(family, new ArrayList());
        else snacs.remove(family);
    }
}
