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


import net.kano.joscar.ByteBlock;
import net.kano.joscar.flap.ClientFlapConn;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ClientConnEvent;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snaccmd.conn.RateInfoCmd;

import org.apache.log4j.Logger;

/**
 * Represents a connection to a particular OSCAR service.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class ServiceConnection extends BasicFlapConnection {

    static Logger Log = Logger.getLogger(ServiceConnection.class);

    protected int serviceFamily;

    public ServiceConnection(ConnDescriptor cd, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(cd, mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    @Override
    protected void clientReady() {
        getMainSession().serviceReady(this);
        super.clientReady();
    }

    @Override
    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("OSCAR service state change from "+e.getOldState()+" to "+e.getNewState());
        if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            getMainSession().serviceFailed(this);
        } else if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
            getMainSession().serviceConnected(this);
        } else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            getMainSession().serviceDied(this);
        }
    }

    @Override
    protected void handleFlapPacket(FlapPacketEvent e) {
        super.handleFlapPacket(e);
    }

    @Override
    protected void handleSnacPacket(SnacPacketEvent e) {
        super.handleSnacPacket(e);
    }

    @Override
    protected void handleSnacResponse(SnacResponseEvent e) {
        super.handleSnacResponse(e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            // this is all we need.
            clientReady();
        }
    }

}
