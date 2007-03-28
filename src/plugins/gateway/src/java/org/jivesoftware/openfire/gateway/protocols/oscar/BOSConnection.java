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

import net.kano.joscar.*;
import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.net.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.*;
import net.kano.joscar.snaccmd.icq.OfflineMsgIcqCmd;
import net.kano.joscar.snaccmd.icq.OfflineMsgDoneCmd;
import net.kano.joscar.snaccmd.icq.OfflineMsgIcqAckCmd;
import net.kano.joscar.snaccmd.icq.MetaShortInfoCmd;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;
import net.kano.joscar.snaccmd.loc.*;
import net.kano.joscar.snaccmd.ssi.*;
import net.kano.joscar.ssiitem.*;

import java.util.Arrays;
import java.util.List;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.openfire.gateway.TransportLoginStatus;
import org.xmpp.packet.Presence;

/**
 * Handles BOS related packets.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class BOSConnection extends BasicFlapConnection {            
    protected SsiItemObjectFactory itemFactory = new DefaultSsiItemObjFactory();

    private static final List<CapabilityBlock> MY_CAPS = Arrays.asList(
        CapabilityBlock.BLOCK_ICQCOMPATIBLE
    );

    public BOSConnection(ConnDescriptor cd, OSCARSession mainSession, ByteBlock cookie) {
        super(cd, mainSession, cookie); // Hand off to BasicFlapConnection
    }

    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("OSCAR bos service state change from "+e.getOldState()+" to "+e.getNewState());
        if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED && e.getOldState() == ClientFlapConn.STATE_CONNECTED && getMainSession().isLoggedIn()) {
            getMainSession().bosDisconnected();
        }
    }

    protected void handleFlapPacket(FlapPacketEvent e) {
        Log.debug("OSCAR bos flap packet received: "+e);
        super.handleFlapPacket(e);
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
        Log.debug("OSCAR bos snac packet received: "+e);
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

        Log.debug("OSCAR bos snac response received: "+e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof LocRightsCmd) {
            request(new SetInfoCmd(new InfoData("oscargateway",
                    null, MY_CAPS, null)));
            request(new MyInfoRequest());
        }
        else if (cmd instanceof ParamInfoCmd) {
            ParamInfoCmd pic = (ParamInfoCmd) cmd;

            ParamInfo info = pic.getParamInfo();

            request(new SetParamInfoCmd(new ParamInfo(0,
                    info.getFlags() | ParamInfo.FLAG_TYPING_NOTIFICATION, 8000,
                    info.getMaxSenderWarning(), info.getMaxReceiverWarning(),
                    0)));
        }
        else if (cmd instanceof ServiceRedirect) {
            ServiceRedirect sr = (ServiceRedirect) cmd;

            oscarSession.connectToService(sr.getSnacFamily(), sr.getRedirectHost(),
                    sr.getCookie());

        }
        else if (cmd instanceof SsiDataCmd) {
            SsiDataCmd sdc = (SsiDataCmd) cmd;

            List<SsiItem> items = sdc.getItems();
            for (SsiItem item : items) {
                SsiItemObj obj = itemFactory.getItemObj(item);
                if (obj instanceof BuddyItem) {
                    Log.debug("AIM got buddy item " + obj);
                    oscarSession.gotBuddy((BuddyItem) obj);
                }
                else if (obj instanceof GroupItem) {
                    Log.debug("AIM got group item " + obj);
                    oscarSession.gotGroup((GroupItem) obj);
                }
            }

            if (sdc.getLastModDate() != 0) {
                request(new ActivateSsiCmd());
                clientReady();

                Presence p = new Presence();
                p.setTo(oscarSession.getJID());
                p.setFrom(oscarSession.getTransport().getJID());
                oscarSession.getTransport().sendPacket(p);

                oscarSession.setLoginStatus(TransportLoginStatus.LOGGED_IN);
                oscarSession.gotCompleteSSI();
            }
        }
        else if (cmd instanceof OfflineMsgIcqCmd) {
            OfflineMsgIcqCmd omic = (OfflineMsgIcqCmd)cmd;

            String sn = String.valueOf(omic.getFromUIN());
            //String msg = "Offline message sent at "+new Date(omic.getDate().getTime()).toString()+"\n"+OscarTools.stripHtml(omic.getContents()).trim();
            String msg = "Offline message received:\n"+ StringUtils.unescapeFromXML(OscarTools.stripHtml(omic.getContents()).trim());
            EncodedStringInfo encmsg = MinimalEncoder.encodeMinimally(msg);
            InstantMessage imsg = new InstantMessage(encmsg.getImEncoding().getCharsetCode(), ByteBlock.wrap(encmsg.getData()));

            oscarSession.getTransport().sendMessage(
                    oscarSession.getJIDWithHighestPriority(),
                    oscarSession.getTransport().convertIDToJID(sn),
                    imsg.getMessage()
            );
        }
        else if (cmd instanceof OfflineMsgDoneCmd) {
            request(new OfflineMsgIcqAckCmd(oscarSession.getUIN(), (int)oscarSession.nextIcqId()));
        }
        else if (cmd instanceof MetaShortInfoCmd) {
//            MetaShortInfoCmd msic = (MetaShortInfoCmd)cmd;
//            Log.debug("RECEIVED META SHORT INFO: "+msic);
//            oscarSession.updateRosterNickname(String.valueOf(msic.getUIN()), msic.getNickname());
        }
        else if (cmd instanceof BuddyAddedYouCmd) {
            BuddyAddedYouCmd bay = (BuddyAddedYouCmd)cmd;

            Presence p = new Presence();
            p.setType(Presence.Type.subscribe);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(bay.getUin()));
            oscarSession.getTransport().sendPacket(p);
        }
        else if (cmd instanceof BuddyAuthRequest) {
            BuddyAuthRequest bar = (BuddyAuthRequest)cmd;

            Presence p = new Presence();
            p.setType(Presence.Type.subscribe);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(bar.getScreenname()));
            oscarSession.getTransport().sendPacket(p);
        }
        else if (cmd instanceof AuthReplyCmd) {
            AuthReplyCmd ar = (AuthReplyCmd)cmd;

            if (ar.isAccepted()) {
                Presence p = new Presence();
                p.setType(Presence.Type.subscribed);
                p.setTo(oscarSession.getJID());
                p.setFrom(oscarSession.getTransport().convertIDToJID(ar.getSender()));
                oscarSession.getTransport().sendPacket(p);
            }
            else {
                Presence p = new Presence();
                p.setType(Presence.Type.unsubscribed);
                p.setTo(oscarSession.getJID());
                p.setFrom(oscarSession.getTransport().convertIDToJID(ar.getSender()));
                oscarSession.getTransport().sendPacket(p);
            }
        }
        else if (cmd instanceof AuthFutureCmd) {
            AuthFutureCmd af = (AuthFutureCmd)cmd;

            Presence p = new Presence();
            p.setType(Presence.Type.subscribe);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(af.getUin()));
            oscarSession.getTransport().sendPacket(p);
        }
    }
}
