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
import net.kano.joscar.snaccmd.*;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;
import net.kano.joscar.snaccmd.loc.*;
import net.kano.joscar.snaccmd.ssi.*;
import net.kano.joscar.ssiitem.*;

import java.net.InetAddress;

public class BOSConnection extends BasicFlapConnection {
    protected SsiItemObjectFactory itemFactory = new DefaultSsiItemObjFactory();

    public BOSConnection(OSCARSession mainSession, ByteBlock cookie) {
        super(mainSession, cookie); // HAnd off to BasicFlapConnection
    }

    public BOSConnection(String host, int port, OSCARSession mainSession, ByteBlock cookie) {
        super(host, port, mainSession, cookie); // HAnd off to BasicFlapConnection
    }

    public BOSConnection(InetAddress ip, int port, OSCARSession mainSession, ByteBlock cookie) {
        super(ip, port, mainSession, cookie); // HAnd off to BasicFlapConnection
    }

    protected void handleStateChange(ClientConnEvent e) {
        //Log.debug("main connection state changed from "
        //        + e.getOldState() + " to " + e.getNewState() + ": "
        //        + e.getReason());
    }

    protected void handleFlapPacket(FlapPacketEvent e) {
        super.handleFlapPacket(e);
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
        super.handleSnacPacket(e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof ServerReadyCmd) {
            request(new ParamInfoRequest());
            request(new LocRightsRequest());
            request(new SsiRightsRequest());
            request(new SsiDataRequest());
        }
    }

    protected void handleSnacResponse(SnacResponseEvent e) {
        super.handleSnacResponse(e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof LocRightsCmd) {
            request(new SetInfoCmd(new InfoData("oscargateway",
                    null, new CapabilityBlock[] {
                        CapabilityBlock.BLOCK_ICQCOMPATIBLE,
                    }, null)));
            request(new MyInfoRequest());
        } else if (cmd instanceof ParamInfoCmd) {
            ParamInfoCmd pic = (ParamInfoCmd) cmd;

            ParamInfo info = pic.getParamInfo();

            request(new SetParamInfoCmd(new ParamInfo(0,
                    info.getFlags() | ParamInfo.FLAG_TYPING_NOTIFICATION, 8000,
                    info.getMaxSenderWarning(), info.getMaxReceiverWarning(),
                    0)));

        } else if (cmd instanceof YourInfoCmd) {
            YourInfoCmd yic = (YourInfoCmd) cmd;

            FullUserInfo info = yic.getUserInfo();

            //Log.debug("got my user info: " + info);

        } else if (cmd instanceof UserInfoCmd) {
            UserInfoCmd uic = (UserInfoCmd) cmd;

            String sn = uic.getUserInfo().getScreenname();
            //Log.debug("user info for " + sn + ": "
            //        + uic.getInfoData());

        } else if (cmd instanceof ServiceRedirect) {
            ServiceRedirect sr = (ServiceRedirect) cmd;

            //Log.debug("connecting to " + sr.getRedirectHost()
            //        + " for 0x" + Integer.toHexString(sr.getSnacFamily()));

            oscarSession.connectToService(sr.getSnacFamily(), sr.getRedirectHost(),
                    sr.getCookie());

        } else if (cmd instanceof SsiDataCmd) {
            SsiDataCmd sdc = (SsiDataCmd) cmd;

            SsiItem[] items = sdc.getItems();
            for (int i = 0; i < items.length; i++) {
                SsiItemObj obj = itemFactory.getItemObj(items[i]);
                if (obj instanceof BuddyItem) {
                    oscarSession.gotBuddy((BuddyItem)obj);
                }
                else if (obj instanceof GroupItem) {
                    oscarSession.gotGroup((GroupItem)obj);
                }
                //Log.debug("- " + (obj == null ? (Object) items[i]
                //        : (Object) obj));
            }

            if (sdc.getLastModDate() != 0) {
                //Log.debug("done with SSI");
                request(new ActivateSsiCmd());
                clientReady();
                oscarSession.gotCompleteSSI();
            }
        }
    }
}
