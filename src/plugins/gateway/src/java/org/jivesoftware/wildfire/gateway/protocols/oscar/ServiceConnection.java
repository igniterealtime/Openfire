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


import net.kano.joscar.flap.ClientFlapConn;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.ByteBlock;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snaccmd.conn.RateInfoCmd;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.net.ClientConnEvent;

import java.net.InetAddress;

/**
 * Represents a connection to a particular OSCAR service.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class ServiceConnection extends BasicFlapConnection {

    protected int serviceFamily;

    public ServiceConnection(String host, int port, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(host, port, mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    public ServiceConnection(InetAddress ip, int port, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(ip, port, mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    protected void clientReady() {
        oscarSession.serviceReady(this);
        super.clientReady();
    }

    protected void handleStateChange(ClientConnEvent e) {
        if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            oscarSession.serviceFailed(this);
        } else if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
            oscarSession.serviceConnected(this);
        } else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            oscarSession.serviceDied(this);
        }
    }

    protected void handleFlapPacket(FlapPacketEvent e) {
        super.handleFlapPacket(e);
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
        super.handleSnacPacket(e);
    }

    protected void handleSnacResponse(SnacResponseEvent e) {
        super.handleSnacResponse(e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            // this is all we need.
            clientReady();
        }
    }
}
