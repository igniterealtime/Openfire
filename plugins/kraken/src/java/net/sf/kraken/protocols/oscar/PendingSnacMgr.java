/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.oscar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kano.joscar.DefensiveTools;
import net.kano.joscar.snac.SnacRequest;

/**
 * Handles pending SNAC commands.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class PendingSnacMgr {

    protected Map<Integer,List<SnacRequest>> snacs = new HashMap<Integer,List<SnacRequest>>();

    public boolean isPending(int familyCode) {
        return snacs.containsKey(familyCode);
    }

    public void add(SnacRequest request) {
        Integer family = request.getCommand().getFamily();

        List<SnacRequest> pending = snacs.get(family);

        pending.add(request);
    }

    public List<SnacRequest> getPending(int familyCode) {
        List<SnacRequest> pending = snacs.get(familyCode);
        return DefensiveTools.getUnmodifiableCopy(pending);
    }

    public void setPending(int familyCode, boolean pending) {
        if (pending) {
            snacs.put(familyCode, new ArrayList<SnacRequest>());
        }
        else {
            snacs.remove(familyCode);
        }
    }
}
