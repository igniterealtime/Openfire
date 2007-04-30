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
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.TransportLoginStatus;
import org.jivesoftware.openfire.gateway.TransportType;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import net.kano.joscar.*;
import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.net.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.auth.*;

import java.util.Locale;
import java.util.Arrays;

/**
 * Handles the login process with the OSCAR login server.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class LoginConnection extends BaseFlapConnection {
    public LoginConnection(ConnDescriptor cd, OSCARSession mainSession) {
        super(cd, mainSession); // Hand off to BaseFlapConnection
    }

    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("OSCAR login service state change from "+e.getOldState()+" to "+e.getNewState());
        if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
//            if (getMainSession().getTransport().getType().equals(TransportType.icq)) {
//                Log.debug("FINDME: Doing ICQ normal auth.");
//                ClientVersionInfo version = new ClientVersionInfo(
//                        "ICQBasic",
//                        0x010a, 0x0014, 0x0022, 0, 0x0911, 0x0000043d);
//                String password = oscarSession.getRegistration().getPassword();
//                // ICQ caps passwords at 8 characters.
//                if (password.length() > 8) {
//                    password = password.substring(0, 8);
//                }
//                getFlapProcessor().sendFlap(new LoginICQFlapCmd(oscarSession.getRegistration().getUsername(), password, version, Locale.US));
//            }
//            else {
                getFlapProcessor().sendFlap(new LoginFlapCmd());
                request(new KeyRequest(oscarSession.getRegistration().getUsername()));
//            }
        }
        else if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setFrom(this.getMainSession().getTransport().getJID());
            m.setTo(this.getMainSession().getJIDWithHighestPriority());
            m.setBody(LocaleUtils.getLocalizedString("gateway.oscar.connectionfailed", "gateway")+" " + e.getReason());
            this.getMainSession().getTransport().sendPacket(m);
            this.getMainSession().logOut();
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

    protected void handleFlapPacket(FlapPacketEvent e) {
//        FlapCommand cmd = e.getFlapCommand();
    }

    protected void handleSnacPacket(SnacPacketEvent e) { }

    protected void handleSnacResponse(SnacResponseEvent e) {
        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof KeyResponse) {
            Log.debug("Handling AIM-style auth.");

            KeyResponse kr = (KeyResponse) cmd;
            ByteBlock authkey = kr.getKey();
            ClientVersionInfo version = new ClientVersionInfo(
                        "AOL Instant Messenger, version 5.5.3415/WIN32",
                        -1, 5, 5, 0, 3415, 239);

            String pass = oscarSession.getRegistration().getPassword();
            if (oscarSession.getTransport().getType().equals(TransportType.icq)) {
                if (pass.length() > 8) {
                    pass = pass.substring(0,8);
                }
            }

            request(new AuthRequest(oscarSession.getRegistration().getUsername(), pass, version, Locale.US, authkey));
        }
        else if (cmd instanceof AuthResponse) {
            Log.debug("Got auth response!");
            AuthResponse ar = (AuthResponse) cmd;

            int error = ar.getErrorCode();
            if (error != -1) {
                String errormsg;
                switch (error) {
                    case (AuthResponse.ERROR_ACCOUNT_DELETED): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.accountdeleted", "gateway");
                        break;
                    }

                    case (AuthResponse.ERROR_BAD_INPUT): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.badinput", "gateway");
                        break;
                    }

                    case (AuthResponse.ERROR_BAD_PASSWORD): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.badpassword", "gateway");
                        break;
                    }

                    case (AuthResponse.ERROR_CLIENT_TOO_OLD): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.oldclient", "gateway");
                        break;
                    }

                    case (AuthResponse.ERROR_CONNECTING_TOO_MUCH_A):
                    case (AuthResponse.ERROR_CONNECTING_TOO_MUCH_B): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.connectedtoomuch", "gateway");
                        break;
                    }

                    case (AuthResponse.ERROR_INVALID_SN_OR_PASS_A):
                    case (AuthResponse.ERROR_INVALID_SN_OR_PASS_B): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.baduserorpass", "gateway");
                        break;
                    }

                    case (AuthResponse.ERROR_SIGNON_BLOCKED): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.accountsuspended", "gateway");
                        break;
                    }

                    default: {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.unknownerror", "gateway", Arrays.asList(error, ar.getErrorUrl()));
                    }
                }

                Message m = new Message();
                m.setType(Message.Type.error);
                m.setTo(getMainSession().getJID());
                m.setFrom(getMainSession().getTransport().getJID());
                m.setBody(errormsg);
                getMainSession().getTransport().sendPacket(m);

                Presence p = new Presence();
                p.setTo(getMainSession().getJID());
                p.setFrom(getMainSession().getTransport().getJID());
                p.setType(Presence.Type.unavailable);
                getMainSession().getTransport().sendPacket(p);
                getMainSession().setLoginStatus(TransportLoginStatus.LOGGED_OUT);
            }
            else {
                Log.debug("Got something else?");
                getMainSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
                oscarSession.startBosConn(ar.getServer(), ar.getPort(), ar.getCookie());
                Log.info("OSCAR connection to " + ar.getServer() + ":"
                        + ar.getPort());
            }

            disconnect();
        }
    }
}
