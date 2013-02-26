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

import java.util.Date;
import java.util.List;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.LEBinaryTools;
import net.kano.joscar.OscarTools;
import net.kano.joscar.flap.FlapCommand;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flapcmd.CloseFlapCmd;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ClientConn;
import net.kano.joscar.net.ClientConnEvent;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snaccmd.InfoData;
import net.kano.joscar.snaccmd.conn.MyInfoRequest;
import net.kano.joscar.snaccmd.conn.ServerReadyCmd;
import net.kano.joscar.snaccmd.conn.ServiceRedirect;
import net.kano.joscar.snaccmd.error.SnacError;
import net.kano.joscar.snaccmd.icbm.ParamInfo;
import net.kano.joscar.snaccmd.icbm.ParamInfoCmd;
import net.kano.joscar.snaccmd.icbm.ParamInfoRequest;
import net.kano.joscar.snaccmd.icbm.SetParamInfoCmd;
import net.kano.joscar.snaccmd.icq.MetaShortInfoCmd;
import net.kano.joscar.snaccmd.icq.OfflineMsgDoneCmd;
import net.kano.joscar.snaccmd.icq.OfflineMsgIcqAckCmd;
import net.kano.joscar.snaccmd.icq.OfflineMsgIcqCmd;
import net.kano.joscar.snaccmd.loc.LocRightsCmd;
import net.kano.joscar.snaccmd.loc.LocRightsRequest;
import net.kano.joscar.snaccmd.loc.SetInfoCmd;
import net.kano.joscar.snaccmd.ssi.ActivateSsiCmd;
import net.kano.joscar.snaccmd.ssi.AuthFutureCmd;
import net.kano.joscar.snaccmd.ssi.AuthReplyCmd;
import net.kano.joscar.snaccmd.ssi.BuddyAddedYouCmd;
import net.kano.joscar.snaccmd.ssi.BuddyAuthRequest;
import net.kano.joscar.snaccmd.ssi.ModifyItemsCmd;
import net.kano.joscar.snaccmd.ssi.SsiDataCmd;
import net.kano.joscar.snaccmd.ssi.SsiDataRequest;
import net.kano.joscar.snaccmd.ssi.SsiItem;
import net.kano.joscar.snaccmd.ssi.SsiRightsRequest;
import net.kano.joscar.ssiitem.BuddyItem;
import net.kano.joscar.ssiitem.DefaultSsiItemObjFactory;
import net.kano.joscar.ssiitem.GroupItem;
import net.kano.joscar.ssiitem.IconItem;
import net.kano.joscar.ssiitem.SsiItemObj;
import net.kano.joscar.ssiitem.SsiItemObjectFactory;
import net.kano.joscar.ssiitem.VisibilityItem;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.Presence;

/**
 * Handles BOS related packets.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class BOSConnection extends BasicFlapConnection {

    static Logger Log = Logger.getLogger(BOSConnection.class);

    protected SsiItemObjectFactory itemFactory = new DefaultSsiItemObjFactory();

    public BOSConnection(ConnDescriptor cd, OSCARSession mainSession, ByteBlock cookie) {
        super(cd, mainSession, cookie); // Hand off to BasicFlapConnection
    }

    @Override
    protected void clientReady() {
        super.clientReady();
        startKeepAlive();
    }

    @Override
    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("OSCAR bos service state change from "+e.getOldState()+" to "+e.getNewState()+" Reason: "+e.getReason()+" User:"+getMainSession().getJID());
//        if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED && e.getOldState() == ClientFlapConn.STATE_CONNECTED && getMainSession().isLoggedIn()) {
//            getMainSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.oscar.disconnected", "kraken"));
//        }
        // TODO: Evaulate whether we should check reason and triggered reconnect if possible
        if (e.getNewState().equals(ClientConn.STATE_NOT_CONNECTED) && e.getReason() != null) {
            Log.info ("OSCAR bos disconnected with Reason! call sessionDisconnectedNoReconnect for User:" + getMainSession().getJID());
            if (getMainSession()!= null) {
                getMainSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.oscar.disconnected", "kraken"));
            }
        }
    }

    @Override
    protected void handleFlapPacket(FlapPacketEvent e) {
//        Log.debug("OSCAR bos flap packet received: "+e);
        FlapCommand cmd = e.getFlapCommand();

        if (cmd instanceof CloseFlapCmd) {
            CloseFlapCmd cfc = (CloseFlapCmd)cmd;
            if (cfc.getCode() == CloseFlapCmd.CODE_LOGGED_IN_ELSEWHERE) {
                getMainSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
                getMainSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.oscar.multilogin", "kraken"));
            }
            else {
                getMainSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);                
                getMainSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.oscar.disconnected", "kraken"));
            }
        }
        super.handleFlapPacket(e);
    }

    @Override
    protected void handleSnacPacket(SnacPacketEvent e) {
//        Log.debug("OSCAR bos snac packet received: "+e);
        super.handleSnacPacket(e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof ServerReadyCmd) {
            request(new ParamInfoRequest());
            request(new LocRightsRequest());
            request(new SsiRightsRequest());
            request(new SsiDataRequest());
        }
        else if (cmd instanceof BuddyAddedYouCmd) {
            BuddyAddedYouCmd bay = (BuddyAddedYouCmd)cmd;

            Presence p = new Presence();
            p.setType(Presence.Type.subscribe);
            p.setTo(getMainSession().getJID());
            p.setFrom(getMainSession().getTransport().convertIDToJID(bay.getUin()));
            getMainSession().getTransport().sendPacket(p);
        }
        else if (cmd instanceof BuddyAuthRequest) {
            BuddyAuthRequest bar = (BuddyAuthRequest)cmd;

            Presence p = new Presence();
            p.setType(Presence.Type.subscribe);
            p.setTo(getMainSession().getJID());
            p.setFrom(getMainSession().getTransport().convertIDToJID(bar.getScreenname()));
            getMainSession().getTransport().sendPacket(p);

            // Auto-accept auth request. (for now)
            // TODO: Evaluate handling this in a non-automated fashion.
            request(new AuthReplyCmd(bar.getScreenname(), null, true));
        }
        else if (cmd instanceof AuthReplyCmd) {
            AuthReplyCmd ar = (AuthReplyCmd)cmd;

            if (ar.isAccepted()) {
                Presence p = new Presence();
                p.setType(Presence.Type.subscribed);
                p.setTo(getMainSession().getJID());
                p.setFrom(getMainSession().getTransport().convertIDToJID(ar.getSender()));
                getMainSession().getTransport().sendPacket(p);
            }
            else {
                Presence p = new Presence();
                p.setType(Presence.Type.unsubscribed);
                p.setTo(getMainSession().getJID());
                p.setFrom(getMainSession().getTransport().convertIDToJID(ar.getSender()));
                getMainSession().getTransport().sendPacket(p);
            }
        }
        else if (cmd instanceof ModifyItemsCmd) {
            ModifyItemsCmd mic = (ModifyItemsCmd)cmd;

            List<SsiItem> items = mic.getItems();
            for (SsiItem item : items) {
                SsiItemObj obj = itemFactory.getItemObj(item);
                if (obj instanceof BuddyItem) {
                    BuddyItem bi = (BuddyItem)obj;
                    Log.debug("AIM got buddy item " + bi);
                    getMainSession().getSsiHierarchy().gotBuddy(bi);
                }
                else if (obj instanceof GroupItem) {
                    GroupItem gi = (GroupItem)obj;
                    Log.debug("AIM got group item " + gi);
                    getMainSession().getSsiHierarchy().gotGroup(gi);
                }
            }
        }
    }

    @Override
    protected void handleSnacResponse(SnacResponseEvent e) {
        super.handleSnacResponse(e);

//        Log.debug("OSCAR bos snac response received: "+e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof LocRightsCmd) {
            request(new SetInfoCmd(new InfoData("oscargateway",
                    null, getMainSession().getCapabilities(), null)));
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

            getMainSession().connectToService(sr.getSnacFamily(), sr.getRedirectHost(),
                    sr.getCookie());

        }
        else if (cmd instanceof SsiDataCmd) {
            SsiDataCmd sdc = (SsiDataCmd) cmd;

            List<SsiItem> items = sdc.getItems();
            for (SsiItem item : items) {
                SsiItemObj obj = itemFactory.getItemObj(item);
                if (obj instanceof BuddyItem) {
                    BuddyItem bi = (BuddyItem)obj;
                    Log.debug("OSCAR: got buddy item " + bi);
                    getMainSession().getSsiHierarchy().gotBuddy(bi);
                }
                else if (obj instanceof GroupItem) {
                    GroupItem gi = (GroupItem)obj;
                    Log.debug("OSCAR: got group item " + gi);
                    getMainSession().getSsiHierarchy().gotGroup(gi);
                }
                else if (obj instanceof IconItem) {
                    IconItem ii = (IconItem)obj;
                    Log.debug("OSCAR: got icon item " + ii);
                    getMainSession().getSsiHierarchy().gotIconItem(ii);
                }
                else if (obj instanceof VisibilityItem) {
                    VisibilityItem vi = (VisibilityItem)obj;
                    Log.debug("OSCAR: got visibility item " + vi);
                    getMainSession().getSsiHierarchy().gotVisibilityItem(vi);
                }
                else {
                    Log.debug("OSCAR: got item we're not handling " + obj);
                }
            }

            if (sdc.getLastModDate() != 0) {
                request(new ActivateSsiCmd());
                clientReady();

                getMainSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
                getMainSession().gotCompleteSSI();
            }
        }
        else if (cmd instanceof OfflineMsgIcqCmd) {
            OfflineMsgIcqCmd omic = (OfflineMsgIcqCmd)cmd;

            String sn = String.valueOf(omic.getFromUIN());
            Date whenSent = omic.getDate();
            ByteBlock block = omic.getIcqData();
            final int len = LEBinaryTools.getUShort(block, 12) - 1;
            String msg = OscarTools.getString(block.subBlock(14, len), null);
            msg = StringUtils.unescapeFromXML(OscarTools.stripHtml(msg));

            // TODO: Translate offline message note
            getMainSession().getTransport().sendOfflineMessage(
                    getMainSession().getJID(),
                    getMainSession().getTransport().convertIDToJID(sn),
                    msg,
                    whenSent,
                    "Offline Message"
            );
        }
        else if (cmd instanceof OfflineMsgDoneCmd) {
            request(new OfflineMsgIcqAckCmd(getMainSession().getUIN(), (int)getMainSession().nextIcqId()));
        }
        else if (cmd instanceof MetaShortInfoCmd) {
//            MetaShortInfoCmd msic = (MetaShortInfoCmd)cmd;
//            Log.debug("RECEIVED META SHORT INFO: "+msic);
//            getMainSession().updateRosterNickname(String.valueOf(msic.getUIN()), msic.getNickname());
        }
        else if (cmd instanceof AuthReplyCmd) {
            AuthReplyCmd ar = (AuthReplyCmd)cmd;

            if (ar.isAccepted()) {
                Presence p = new Presence();
                p.setType(Presence.Type.subscribed);
                p.setTo(getMainSession().getJID());
                p.setFrom(getMainSession().getTransport().convertIDToJID(ar.getSender()));
                getMainSession().getTransport().sendPacket(p);
            }
            else {
                Presence p = new Presence();
                p.setType(Presence.Type.unsubscribed);
                p.setTo(getMainSession().getJID());
                p.setFrom(getMainSession().getTransport().convertIDToJID(ar.getSender()));
                getMainSession().getTransport().sendPacket(p);
            }
        }
        else if (cmd instanceof AuthFutureCmd) {
            AuthFutureCmd af = (AuthFutureCmd)cmd;

            Presence p = new Presence();
            p.setType(Presence.Type.subscribe);
            p.setTo(getMainSession().getJID());
            p.setFrom(getMainSession().getTransport().convertIDToJID(af.getUin()));
            getMainSession().getTransport().sendPacket(p);
        }
        else if (cmd instanceof SnacError) {
            SnacError se = (SnacError)cmd;
            if (se.getErrorCode() == SnacError.CODE_REFUSED_BY_CLIENT) {
                getMainSession().getTransport().sendMessage(
                        getMainSession().getJID(),
                        getMainSession().getTransport().getJID(),
                        LocaleUtils.getLocalizedString("gateway.aim.msgrefused","kraken")
                );
            }
            //TODO: Tons more errors that can be caught.  Gotta catch 'em all!  =)  (please don't sue me Nintendo)
        }
    }
}
