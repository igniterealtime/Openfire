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

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import net.kano.joscar.flap.ClientFlapConn;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flap.FlapPacketListener;
import net.kano.joscar.flapcmd.DefaultFlapCmdFactory;
import net.kano.joscar.flapcmd.KeepaliveFlapCmd;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ClientConnEvent;
import net.kano.joscar.net.ClientConnListener;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.net.ConnProcessorExceptionEvent;
import net.kano.joscar.net.ConnProcessorExceptionHandler;
import net.kano.joscar.snac.ClientSnacProcessor;
import net.kano.joscar.snac.FamilyVersionPreprocessor;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacPacketListener;
import net.kano.joscar.snac.SnacRequest;
import net.kano.joscar.snac.SnacRequestAdapter;
import net.kano.joscar.snac.SnacRequestListener;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snaccmd.DefaultClientFactoryList;

import org.apache.log4j.Logger;

/**
 * Base class for all FLAP handlers.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public abstract class AbstractFlapConnection extends ClientFlapConn {

    static Logger Log = Logger.getLogger(AbstractFlapConnection.class);

    /**
     * Timer to send keep alive messages.
     */
    public Timer timer = new Timer();

    /**
     * Interval at which keepalive is sent.
     */
    private int timerInterval = 180000; // 3 minutes

    /**
     * Status checker.
     */
    KeepAliveSender keepAliveSender;

    private class KeepAliveSender extends TimerTask {
        /**
         * Send keepalive to OSCAR.
         */
        @Override
        public void run() {
            try {
                getFlapProcessor().sendFlap(new KeepaliveFlapCmd());
            }
            catch (Exception e) {
                // Probably in the middle of a shutdown.
            }
        }
    }

    public void startKeepAlive() {
        if (keepAliveSender == null) {
            keepAliveSender = new KeepAliveSender();
            timer.schedule(keepAliveSender, timerInterval, timerInterval);
        }
    }

    public void stopKeepAlive() {
        if (timer != null) {
            try {
                timer.cancel();
            }
            catch (Exception e) {
                // Ok then.
            }
            timer = null;
        }
        if (keepAliveSender != null) {
            try {
                keepAliveSender.cancel();
            }
            catch (Exception e) {
                // Ok then
            }
            keepAliveSender = null;
        }
    }

    protected ClientSnacProcessor sp = new ClientSnacProcessor(getFlapProcessor());
    WeakReference<OSCARSession> oscarSessionRef;

    public AbstractFlapConnection(ConnDescriptor cd, OSCARSession mainSession) {
        super(cd); // Hand off to ClientFlapConn
        initBaseFlapConnection();
        oscarSessionRef = new WeakReference<OSCARSession>(mainSession);
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
                AbstractFlapConnection.this.handleFlapPacket(e);
            }
        });
        getFlapProcessor().addExceptionHandler(new ConnProcessorExceptionHandler() {
            public void handleException(ConnProcessorExceptionEvent event) {
                Log.debug(event.getType() + " FLAP ERROR: "
                        + event.getException().getMessage() + " " + event.getReason(), event.getException());
            }
        });
        sp.addPacketListener(new SnacPacketListener() {
            public void handleSnacPacket(SnacPacketEvent e) {
                AbstractFlapConnection.this.handleSnacPacket(e);
            }
        });
    }

    protected SnacRequestListener genericReqListener = new SnacRequestAdapter() {
        @Override
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

    public OSCARSession getMainSession() { return oscarSessionRef.get(); }

    void sendRequest(SnacRequest req) {
        Log.debug("Sending SNAC request: "+req);
        if (!req.hasListeners()) req.addListener(genericReqListener);
        sp.sendSnac(req);
    }

    SnacRequest request(SnacCommand cmd) {
        Log.debug("Sending SNAC command: "+cmd);
        return request(cmd, null);
    }

    SnacRequest request(SnacCommand cmd, SnacRequestListener listener) {
        Log.debug("Setting up SNAC request and listener: "+cmd+","+listener);
        SnacRequest req = new SnacRequest(cmd, listener);
        sendRequest(req);
        return req;
    }

    protected abstract void handleStateChange(ClientConnEvent e);
    protected abstract void handleFlapPacket(FlapPacketEvent e);
    protected abstract void handleSnacPacket(SnacPacketEvent e);
    protected abstract void handleSnacResponse(SnacResponseEvent e);
}
