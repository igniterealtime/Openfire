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

package org.jivesoftware.net.spi;

import org.jivesoftware.net.ConnectionManager;
import org.jivesoftware.net.policies.BasicAcceptPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

import org.jivesoftware.net.AcceptManager;
import org.jivesoftware.net.AcceptPort;
import org.jivesoftware.net.*;
import org.jivesoftware.messenger.container.*;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.*;

public class AcceptManagerImpl extends BasicModule implements AcceptManager {

    private List<AcceptPort> ports = new ArrayList<AcceptPort>();
    private AcceptPolicy policy = new BasicAcceptPolicy(true);

    private ConnectionManager connManager;

    public AcceptManagerImpl() {
        super("Accept Manager");
    }

    public AcceptPolicy getGlobalAcceptPolicy() {
        if (policy == null){
            throw new IllegalStateException(
                    "Must initialize and start module before use");
        }
        return policy;
    }

    public int getAcceptPortCount() {
        return ports.size();
    }

    public Iterator getAcceptPorts() {
        return ports.iterator();
    }

    public Iterator getAcceptPorts(BasicResultFilter filter) {
        return filter.filter(ports.iterator());
    }

    public AcceptPort getAcceptPort(InetSocketAddress portAddress)
            throws NotFoundException {
        AcceptPort matchedPort = null;
        Iterator portIter = ports.iterator();
        while (portIter.hasNext()){
            AcceptPort acceptPort = (AcceptPort) portIter.next();
            if (acceptPort.getInetSocketAddress().equals(portAddress)){
                matchedPort = acceptPort;
            }
        }
        if (matchedPort == null){
            throw new NotFoundException(portAddress.toString());
        }
        return matchedPort;
    }

    public AcceptPort createAcceptPort(InetSocketAddress portAddress)
            throws AlreadyExistsException {

        if (connManager == null){
            throw new IllegalStateException("Connection Manager not ready");
        }

        Iterator portIter = ports.iterator();
        while (portIter.hasNext()){
            AcceptPort acceptPort = (AcceptPort) portIter.next();
            if (acceptPort.getInetSocketAddress().equals(portAddress)){
                throw new AlreadyExistsException(portAddress.toString());
            }
        }
        AcceptPort acceptPort = new AcceptPortImpl("port" + ports.size(),
                connManager, portAddress);
        ports.add(acceptPort);
        return acceptPort;
    }

    public void deleteAcceptPort(AcceptPort acceptPort) {
        ports.remove(acceptPort);

        JiveGlobals.deleteProperty("acceptPorts");
        for (int i=0; i<ports.size(); i++) {
            ((AcceptPortImpl)ports.get(i)).setContext("acceptPorts.port" + i);
            ((AcceptPortImpl)ports.get(i)).savePort();
        }

        try {
            acceptPort.close();
        } catch (IOException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(ConnectionManager.class,"");
        return trackInfo;
    }

    public void initialize(Container container) {
        super.initialize(container);
    }

    protected void serviceAdded(Object service) {
        if (service instanceof ConnectionManager){
            connManager = (ConnectionManager) service;
            for (String propName : JiveGlobals.getProperties("acceptPorts")) {
                AcceptPort port = new AcceptPortImpl(propName, connManager);
                ports.add(port);
            }
        }
    }
}
