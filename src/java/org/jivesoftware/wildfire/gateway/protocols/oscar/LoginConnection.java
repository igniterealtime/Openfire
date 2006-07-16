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

import net.kano.joscar.*;
import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.net.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.auth.*;

import java.net.InetAddress;

public class LoginConnection extends BaseFlapConnection {
    protected boolean loggedin = false;

    public LoginConnection(OSCARSession mainSession) {
        super(mainSession); // Hand off to BaseFlapConnection
    }

    public LoginConnection(String host, int port, OSCARSession mainSession) {
        super(host, port, mainSession); // Hand off to BaseFlapConnection
    }

    public LoginConnection(InetAddress ip, int port, OSCARSession mainSession) {
        super(ip, port, mainSession); // Hand off to BaseFlapConnection
    }

    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("state changed to: " + e.getNewState() + " (" + e.getReason() + ")");

        if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
            Log.debug("connected, sending flap version and key request");
            getFlapProcessor().sendFlap(new LoginFlapCmd());
            request(new KeyRequest(oscarSession.getRegistration().getUsername()));
        }
        else if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            Log.info("connection failed: " + e.getReason());
        }
        else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            if (!loggedin) {
                Log.info("connection lost: " + e.getReason());
            }
        }
    }

    protected void handleFlapPacket(FlapPacketEvent e) { }

    protected void handleSnacPacket(SnacPacketEvent e) { }

    protected void handleSnacResponse(SnacResponseEvent e) {
        SnacCommand cmd = e.getSnacCommand();
        Log.debug("snac response: "
                + Integer.toHexString(cmd.getFamily()) + "/"
                + Integer.toHexString(cmd.getCommand()) + ": " + cmd);

        if (cmd instanceof KeyResponse) {
            KeyResponse kr = (KeyResponse) cmd;

            ByteBlock authkey = kr.getKey();

            ClientVersionInfo version = new ClientVersionInfo(
                    "AOL Instant Messenger, version 5.2.3292/WIN32",
                    5, 1, 0, 3292, 238);

            request(new AuthRequest(oscarSession.getRegistration().getUsername(), oscarSession.getRegistration().getPassword(), version, authkey));

        } else if (cmd instanceof AuthResponse) {
            AuthResponse ar = (AuthResponse) cmd;

            int error = ar.getErrorCode();
            if (error != -1) {
                Log.error("connection error! code: " + error);
                if (ar.getErrorUrl() != null) {
                    Log.error("Error URL: " + ar.getErrorUrl());
                }
            } else {
                loggedin = true;
                oscarSession.startBosConn(ar.getServer(), ar.getPort(), ar.getCookie());
                Log.info("connecting to " + ar.getServer() + ":"
                        + ar.getPort());
            }

            disconnect();
        }
    }
}
