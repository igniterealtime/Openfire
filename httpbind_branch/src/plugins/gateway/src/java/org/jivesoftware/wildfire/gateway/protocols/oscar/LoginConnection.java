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

/**
 * Handles the login process with the OSCAR login server.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class LoginConnection extends BaseFlapConnection {
    protected boolean loggedin = false;

    public LoginConnection(ConnDescriptor cd, OSCARSession mainSession) {
        super(cd, mainSession); // Hand off to BaseFlapConnection
    }

    protected void handleStateChange(ClientConnEvent e) {
        if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
            getFlapProcessor().sendFlap(new LoginFlapCmd());
            request(new KeyRequest(oscarSession.getRegistration().getUsername()));
        }
        else if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            //TODO: Do we need to catch these?
//            Message m = new Message();
//            m.setType(Message.Type.error);
//            m.setFrom(this.getMainSession().getTransport().getJID());
//            m.setTo(this.getMainSession().getJIDWithHighestPriority());
//            m.setBody("Connection failed: " + e.getReason());
//            this.getMainSession().getTransport().sendPacket(m);
//            this.getMainSession().logOut();
        }
        else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            //TODO: Do we need to catch these?
//            if (!loggedin) {
//                Message m = new Message();
//                m.setType(Message.Type.error);
//                m.setFrom(this.getMainSession().getTransport().getJID());
//                m.setTo(this.getMainSession().getJIDWithHighestPriority());
//                m.setBody("Connection lost: " + e.getReason());
//                this.getMainSession().getTransport().sendPacket(m);
//                this.getMainSession().logOut();
//            }
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
                    "AOL Instant Messenger, version 5.5.3415/WIN32",
                    -1, 5, 5, 0, 3415, 239);

            request(new AuthRequest(oscarSession.getRegistration().getUsername(), oscarSession.getRegistration().getPassword(), version, authkey));

        }
        else if (cmd instanceof AuthResponse) {
            AuthResponse ar = (AuthResponse) cmd;

            int error = ar.getErrorCode();
            if (error != -1) {
                String errormsg;
                switch (error) {
                    case (AuthResponse.ERROR_ACCOUNT_DELETED): {
                        errormsg = "This account has been deleted.";
                        break;
                    }

                    case (AuthResponse.ERROR_BAD_INPUT): {
                        errormsg = "Illegal screen name/uin specified.";
                        break;
                    }

                    case (AuthResponse.ERROR_BAD_PASSWORD): {
                        errormsg = "Incorrect password specified.";
                        break;
                    }

                    case (AuthResponse.ERROR_CLIENT_TOO_OLD): {
                        errormsg = "Plugin is identifying itself as too old of a client.  Please contact the develop.";
                        break;
                    }

                    case (AuthResponse.ERROR_CONNECTING_TOO_MUCH_A):
                    case (AuthResponse.ERROR_CONNECTING_TOO_MUCH_B): {
                        errormsg = "You have connected too many times in too short of a time frame.  Please wait around 15 minutes before trying again.";
                        break;
                    }

                    case (AuthResponse.ERROR_INVALID_SN_OR_PASS_A):
                    case (AuthResponse.ERROR_INVALID_SN_OR_PASS_B): {
                        errormsg = "Invalid screen name or password specified.  Please re-register with a valid screen name and password.";
                        break;
                    }

                    case (AuthResponse.ERROR_SIGNON_BLOCKED): {
                        errormsg = "Your account has been temporarily suspended.";
                        break;
                    }

                    default: {
                        errormsg = "Unknown error code returned from AIM: "+error+"\nURL: "+ar.getErrorUrl();
                    }
                }

                Message m = new Message();
                m.setType(Message.Type.error);
                m.setTo(getMainSession().getJID());
                m.setFrom(getMainSession().getTransport().getJID());
                m.setBody(errormsg);
                getMainSession().getTransport().sendPacket(m);
            }
            else {
                loggedin = true;
                oscarSession.startBosConn(ar.getServer(), ar.getPort(), ar.getCookie());
                Log.info("OSCAR connection to " + ar.getServer() + ":"
                        + ar.getPort());
            }

            disconnect();
        }
    }
}
