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

import org.jivesoftware.util.Log;

import net.kano.joscar.flap.ClientFlapConn;
import net.kano.joscar.flap.FlapPacketListener;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.snac.*;
import net.kano.joscar.flapcmd.DefaultFlapCmdFactory;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snaccmd.DefaultClientFactoryList;
import net.kano.joscar.net.*;

/**
 * Base class for all FLAP handlers.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public abstract class BaseFlapConnection extends ClientFlapConn {
    protected ClientSnacProcessor sp = new ClientSnacProcessor(getFlapProcessor());
    OSCARSession oscarSession;

    public BaseFlapConnection(ConnDescriptor cd, OSCARSession mainSession) {
        super(cd); // Hand off to ClientFlapConn
        initBaseFlapConnection();
        oscarSession = mainSession;
    }

    private void initBaseFlapConnection() {
        getFlapProcessor().setFlapCmdFactory(new DefaultFlapCmdFactory());

        sp.addPreprocessor(new FamilyVersionPreprocessor());
        sp.getCmdFactoryMgr().setDefaultFactoryList(new DefaultClientFactoryList());

        addConnListener(new ClientConnListener() {
            public void stateChanged(ClientConnEvent e) {
                handleStateChange(e);
            }
        });
        getFlapProcessor().addPacketListener(new FlapPacketListener() {
            public void handleFlapPacket(FlapPacketEvent e) {
                BaseFlapConnection.this.handleFlapPacket(e);
            }
        });
        getFlapProcessor().addExceptionHandler(new ConnProcessorExceptionHandler() {
            public void handleException(ConnProcessorExceptionEvent event) {
                Log.error(event.getType() + " FLAP ERROR: "
                        + event.getException().getMessage() + " " + event.getReason());
            }
        });
        sp.addPacketListener(new SnacPacketListener() {
            public void handleSnacPacket(SnacPacketEvent e) {
                BaseFlapConnection.this.handleSnacPacket(e);
            }
        });
    }

    protected SnacRequestListener genericReqListener = new SnacRequestAdapter() {
        public void handleResponse(SnacResponseEvent e) {
            handleSnacResponse(e);
        }
    };

    public SnacRequestListener getGenericReqListener() {
        return genericReqListener;
    }

    public ClientSnacProcessor getSnacProcessor() {
        return sp;
    }

    public OSCARSession getMainSession() { return oscarSession; }

    void sendRequest(SnacRequest req) {
        if (!req.hasListeners()) req.addListener(genericReqListener);
        sp.sendSnac(req);
    }

    SnacRequest request(SnacCommand cmd) {
        return request(cmd, null);
    }

    SnacRequest request(SnacCommand cmd, SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);
        sendRequest(req);
        return req;
    }

    protected abstract void handleStateChange(ClientConnEvent e);
    protected abstract void handleFlapPacket(FlapPacketEvent e);
    protected abstract void handleSnacPacket(SnacPacketEvent e);
    protected abstract void handleSnacResponse(SnacResponseEvent e);
}
