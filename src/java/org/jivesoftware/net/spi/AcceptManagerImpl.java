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
import java.util.Iterator;
import java.util.LinkedList;

import org.jivesoftware.net.AcceptManager;
import org.jivesoftware.net.AcceptPort;
import org.jivesoftware.net.*;
import org.jivesoftware.messenger.container.*;
import org.jivesoftware.util.*;

public class AcceptManagerImpl extends BasicModule implements AcceptManager {

    private ModuleContext context;
    private LinkedList ports = new LinkedList();
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
        return new AcceptPortImpl(context.createChildProperty("accept.port"),
                                  connManager,
                                  portAddress);
    }

    public void deleteAcceptPort(AcceptPort acceptPort) {
        ports.remove(acceptPort);
        // TODO: save remaining ports

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

    public void initialize(ModuleContext context, Container container) {
        super.initialize(context, container);
        this.context = context;
    }

    protected void serviceAdded(Object service) {
        if (service instanceof ConnectionManager){
            connManager = (ConnectionManager) service;
            Iterator portIter = context.getChildProperties("portIter.port");
            while (portIter.hasNext()){
                AcceptPort port = new AcceptPortImpl((ModuleProperties) portIter.next(),
                                                     connManager);
                ports.add(port);
            }
        }
    }
}
