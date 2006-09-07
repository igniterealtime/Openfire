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
import org.xmpp.packet.Message;

import net.kano.joscar.*;
import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.net.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.auth.*;

import java.net.InetAddress;

/**
 * Handles the login process with the OSCAR login server.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
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
        if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
            getFlapProcessor().sendFlap(new LoginFlapCmd());
            request(new KeyRequest(oscarSession.getRegistration().getUsername()));
        }
        else if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setFrom(this.getMainSession().getTransport().getJID());
            m.setTo(this.getMainSession().getJIDWithHighestPriority());
            m.setBody("Connection failed: " + e.getReason());
            this.getMainSession().getTransport().sendPacket(m);                            
            this.getMainSession().logOut();
        }
        else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            if (!loggedin) {
                Message m = new Message();
                m.setType(Message.Type.error);
                m.setFrom(this.getMainSession().getTransport().getJID());
                m.setTo(this.getMainSession().getJIDWithHighestPriority());
                m.setBody("Connection lost: " + e.getReason());
                this.getMainSession().getTransport().sendPacket(m);
                this.getMainSession().logOut();
            }
        }
    }

    protected void handleFlapPacket(FlapPacketEvent e) { }

    protected void handleSnacPacket(SnacPacketEvent e) { }

    protected void handleSnacResponse(SnacResponseEvent e) {
        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof KeyResponse) {
            KeyResponse kr = (KeyResponse) cmd;

            ByteBlock authkey = kr.getKey();

            ClientVersionInfo version = new ClientVersionInfo(
                    "AOL Instant Messenger, version 5.2.3292/WIN32",
                    5, 1, 0, 3292, 238);

            request(new AuthRequest(oscarSession.getRegistration().getUsername(), oscarSession.getRegistration().getPassword(), version, authkey));

        }
        else if (cmd instanceof AuthResponse) {
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
                Log.info("OSCAR connection to " + ar.getServer() + ":"
                        + ar.getPort());
            }

            disconnect();
        }
    }
}
