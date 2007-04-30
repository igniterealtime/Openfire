/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 *
 * Heavily inspired by joscardemo of the Joust Project: http://joust.kano.net/
 */

package org.jivesoftware.openfire.gateway.protocols.oscar;


import net.kano.joscar.flap.ClientFlapConn;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.ByteBlock;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snaccmd.conn.RateInfoCmd;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.net.ClientConnEvent;
import net.kano.joscar.net.ConnDescriptor;
import org.jivesoftware.util.Log;

/**
 * Represents a connection to a particular OSCAR service.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class ServiceConnection extends BasicFlapConnection {

    protected int serviceFamily;

    public ServiceConnection(ConnDescriptor cd, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(cd, mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    protected void clientReady() {
        oscarSession.serviceReady(this);
        super.clientReady();
    }

    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("OSCAR service state change from "+e.getOldState()+" to "+e.getNewState());
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
