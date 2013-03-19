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
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.kano.joscar.snac.SnacRequest;

import org.apache.log4j.Logger;

/**
 * Handles incoming SNAC packets.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class SnacManager {

    static Logger Log = Logger.getLogger(SnacManager.class);

    protected Map<Integer,List<BasicFlapConnection>> conns = new HashMap<Integer,List<BasicFlapConnection>>();
    protected PendingSnacMgr pendingSnacs = new PendingSnacMgr();
    protected List<PendingSnacListener> listeners = new ArrayList<PendingSnacListener>();
    protected Map<BasicFlapConnection,int[]> supportedFamilies = new IdentityHashMap<BasicFlapConnection,int[]>();

    public SnacManager() { }

    public SnacManager(PendingSnacListener listener) {
        addListener(listener);
    }

    public void register(BasicFlapConnection conn) {
        Log.debug("Registrating snac handler "+conn);
        int[] families = conn.getSnacFamilies();
        supportedFamilies.put(conn, families);

        for (int familyCode : families) {
            List<BasicFlapConnection> handlers = conns.get(familyCode);

            if (handlers == null) {
                handlers = new LinkedList<BasicFlapConnection>();
                conns.put(familyCode, handlers);
            }

            if (!handlers.contains(conn)) {
                handlers.add(conn);
            }
        }
    }

    public void dequeueSnacs(BasicFlapConnection conn) {
        int[] infos = supportedFamilies.get(conn);

        if (infos != null) {
            for (int familyCode : infos) {
                if (pendingSnacs.isPending(familyCode)) {
                    dequeueSnacs(familyCode);
                }
            }
        }
    }

    protected void dequeueSnacs(int familyCode) {
        List<SnacRequest> pending = pendingSnacs.getPending(familyCode);
        
        pendingSnacs.setPending(familyCode, false);

        for (PendingSnacListener listener : listeners) {
            listener.dequeueSnacs(pending);
        }
    }

    public void unregister(BasicFlapConnection conn) {
        for (List<BasicFlapConnection> handlers : conns.values()) {
            handlers.remove(conn);
        }
    }

    public BasicFlapConnection getConn(int familyCode) {
        List<BasicFlapConnection> handlers = conns.get(familyCode);

        if (handlers == null || handlers.size() == 0) {
            return null;
        }

        return handlers.get(0);
    }


    public boolean isPending(int familyCode) {
        return pendingSnacs.isPending(familyCode);
    }

    public void addRequest(SnacRequest request) {
        int family = request.getCommand().getFamily();
        if (!isPending(family)) {
            throw new IllegalArgumentException("Family 0x"
                    + Integer.toHexString(family) + " is not pending");             
        }
        pendingSnacs.add(request);
    }

    public void addListener(PendingSnacListener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(PendingSnacListener l) {
        listeners.remove(l);
    }

    public void setPending(int family, boolean pending) {
        pendingSnacs.setPending(family, pending);
    }
}
