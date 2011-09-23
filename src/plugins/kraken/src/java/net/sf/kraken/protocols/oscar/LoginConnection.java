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

import java.util.Arrays;
import java.util.Locale;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.flap.ClientFlapConn;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flapcmd.LoginFlapCmd;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ClientConnEvent;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snaccmd.auth.AuthRequest;
import net.kano.joscar.snaccmd.auth.AuthResponse;
import net.kano.joscar.snaccmd.auth.ClientVersionInfo;
import net.kano.joscar.snaccmd.auth.KeyRequest;
import net.kano.joscar.snaccmd.auth.KeyResponse;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.TransportLoginStatus;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.util.LocaleUtils;

/**
 * Handles the login process with the OSCAR login server.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class LoginConnection extends AbstractFlapConnection {

    static Logger Log = Logger.getLogger(LoginConnection.class);

    public LoginConnection(ConnDescriptor cd, OSCARSession mainSession) {
        super(cd, mainSession); // Hand off to AbstractFlapConnection
    }

    @Override
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
                request(new KeyRequest(getMainSession().getRegistration().getUsername()));
//            }
        }
        else if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            getMainSession().setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
            getMainSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.oscar.connectionfailed", "kraken")+" " + e.getReason());
        }
        else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            //TODO: Do we need to catch these?
//            if (!loggedin) {
//                Message m = new Message();
//                m.setType(Message.Type.error);
//                m.setFrom(this.getMainSession().getTransport().getJID());
//                m.setTo(this.getMainSession().getJID());
//                m.setBody("Connection lost: " + e.getReason());
//                this.getMainSession().getTransport().sendPacket(m);
//                this.getMainSession().logOut();
//            }
        }
    }

    @Override
    protected void handleFlapPacket(FlapPacketEvent e) {
//        FlapCommand cmd = e.getFlapCommand();
    }

    @Override
    protected void handleSnacPacket(SnacPacketEvent e) { }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleSnacResponse(SnacResponseEvent e) {
        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof KeyResponse) {
            Log.debug("Handling AIM-style auth.");

            KeyResponse kr = (KeyResponse) cmd;
            ByteBlock authkey = kr.getKey();
//            ClientVersionInfo version = new ClientVersionInfo(
//                        "AOL Instant Messenger, version 5.5.3415/WIN32",
//                        -1, 5, 5, 0, 3415, 239);
            ClientVersionInfo version = new ClientVersionInfo("Apple iChat", 0x311a, 1, 0, 0, 0x0184, 0xc6);


            String pass = getMainSession().getRegistration().getPassword();
            if (getMainSession().getTransport().getType().equals(TransportType.icq)) {
                if (pass.length() > 8) {
                    pass = pass.substring(0,8);
                }
            }

            request(new AuthRequest(getMainSession().getRegistration().getUsername(), pass, version, Locale.US, authkey));
        }
        else if (cmd instanceof AuthResponse) {
            Log.debug("Got auth response!");
            AuthResponse ar = (AuthResponse) cmd;

            int error = ar.getErrorCode();
            if (error != -1) {
                String errormsg;
                switch (error) {
                    case (AuthResponse.ERROR_ACCOUNT_DELETED): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.accountdeleted", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                        break;
                    }

                    case (AuthResponse.ERROR_BAD_INPUT): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.badinput", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                        break;
                    }

                    case (AuthResponse.ERROR_BAD_PASSWORD): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.badpassword", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                        break;
                    }

                    case (AuthResponse.ERROR_CLIENT_TOO_OLD): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.oldclient", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
                        break;
                    }

                    case (AuthResponse.ERROR_CONNECTING_TOO_MUCH_A):
                    case (AuthResponse.ERROR_CONNECTING_TOO_MUCH_B): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.connectedtoomuch", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
                        break;
                    }

                    case (AuthResponse.ERROR_INVALID_SN_OR_PASS_A):
                    case (AuthResponse.ERROR_INVALID_SN_OR_PASS_B): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.baduserorpass", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                        break;
                    }

                    case (AuthResponse.ERROR_SIGNON_BLOCKED): {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.accountsuspended", "kraken");
                        getMainSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
                        break;
                    }

                    default: {
                        errormsg = LocaleUtils.getLocalizedString("gateway.oscar.unknownerror", "kraken", Arrays.asList(error, ar.getErrorUrl()));
                        getMainSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);
                    }
                }

                getMainSession().sessionDisconnectedNoReconnect(errormsg);
            }
            else {
                Log.debug("Got something else?");
                getMainSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
                getMainSession().startBosConn(ar.getServer(), ar.getPort(), ar.getCookie());
                Log.debug("OSCAR connection to " + ar.getServer() + ":"
                        + ar.getPort());
            }

            disconnect();
        }
    }
}
