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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.kano.joscar.*;
import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.ratelim.*;
import net.kano.joscar.rv.*;
import net.kano.joscar.rvcmd.*;
import net.kano.joscar.rvcmd.addins.*;
import net.kano.joscar.rvcmd.chatinvite.*;
import net.kano.joscar.rvcmd.directim.*;
import net.kano.joscar.rvcmd.getfile.*;
import net.kano.joscar.rvcmd.icon.*;
import net.kano.joscar.rvcmd.sendbl.*;
import net.kano.joscar.rvcmd.sendfile.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.*;
import net.kano.joscar.snaccmd.buddy.*;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;
import net.kano.joscar.snaccmd.rooms.*;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

public abstract class BasicFlapConnection extends BaseFlapConnection {
    protected final ByteBlock cookie;
    protected boolean sentClientReady = false;

    public Map<String,FullUserInfo> buddystore = new HashMap<String,FullUserInfo>();

    protected int[] snacFamilies = null;
    protected SnacFamilyInfo[] snacFamilyInfos;
    protected RateLimitingQueueMgr rateMgr = new RateLimitingQueueMgr();
    protected RvProcessor rvProcessor = new RvProcessor(sp);
    protected RvProcessorListener rvListener = new RvProcessorListener() {
        public void handleNewSession(NewRvSessionEvent event) {
            Log.debug("new RV session: " + event.getSession());

            event.getSession().addListener(rvSessionListener);
        }
    };

    protected RvSessionListener rvSessionListener = new RvSessionListener() {
        public void handleRv(RecvRvEvent event) {
            RvCommand cmd = event.getRvCommand();

            RvSession session = event.getRvSession();
            SnacCommand snaccmd = event.getSnacCommand();
            if (!(snaccmd instanceof RecvRvIcbm)) return;
            RecvRvIcbm icbm = (RecvRvIcbm) snaccmd;
            Log.debug("got rendezvous on session <" + session + ">");
            Log.debug("- command: " + cmd);
        }
        public void handleSnacResponse(RvSnacResponseEvent event) {
            Log.debug("got SNAC response for <"
                    + event.getRvSession() + ">: "
                    + event.getSnacCommand());
        }
    };

    { // init
        sp.setSnacQueueManager(rateMgr);
        rvProcessor.registerRvCmdFactory(new DefaultRvCommandFactory());
        rvProcessor.addListener(rvListener);
    }

    public BasicFlapConnection(OSCARSession mainSession, ByteBlock cookie) {
        super(mainSession);
        this.cookie = cookie;
    }

    public BasicFlapConnection(String host, int port, OSCARSession mainSession, ByteBlock cookie) {
        super(host, port, mainSession);
        this.cookie = cookie;
    }

    public BasicFlapConnection(InetAddress ip, int port, OSCARSession mainSession,
            ByteBlock cookie) {
        super(ip, port, mainSession);
        this.cookie = cookie;
    }

    protected DateFormat dateFormat
            = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT);

    protected void handleFlapPacket(FlapPacketEvent e) {
        FlapCommand cmd = e.getFlapCommand();

        if (cmd instanceof LoginFlapCmd) {
            getFlapProcessor().sendFlap(new LoginFlapCmd(cookie));
        } else {
            Log.debug("got FLAP command on channel 0x"
                    + Integer.toHexString(e.getFlapPacket().getChannel())
                    + ": " + cmd);
        }
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
        SnacPacket packet = e.getSnacPacket();
        Log.debug("got snac packet type "
                + Integer.toHexString(packet.getFamily()) + "/"
                + Integer.toHexString(packet.getCommand()) + ": "
                + e.getSnacCommand());

        SnacCommand cmd = e.getSnacCommand();
        if (cmd instanceof ServerReadyCmd) {
            ServerReadyCmd src = (ServerReadyCmd) cmd;

            setSnacFamilies(src.getSnacFamilies());

            SnacFamilyInfo[] familyInfos = SnacFamilyInfoFactory
                    .getDefaultFamilyInfos(src.getSnacFamilies());

            setSnacFamilyInfos(familyInfos);

            oscarSession.registerSnacFamilies(this);

            request(new ClientVersionsCmd(familyInfos));
            request(new RateInfoRequest());

        } else if (cmd instanceof RecvImIcbm) {
            RecvImIcbm icbm = (RecvImIcbm) cmd;

            String sn = icbm.getSenderInfo().getScreenname();
            InstantMessage message = icbm.getMessage();
            String msg = null;
            msg = OscarTools.stripHtml(message.getMessage());

            Message jmessage = new Message();
            jmessage.setTo(oscarSession.getRegistration().getJID());
            jmessage.setBody(msg);
            jmessage.setType(Message.Type.chat);
            jmessage.setFrom(this.oscarSession.getTransport().convertIDToJID(sn));
            oscarSession.getTransport().sendPacket((Packet)jmessage);

            //sendRequest(new SnacRequest(new SendImIcbm(sn, msg), null));

            String str = dateFormat.format(new Date()) + " IM from "
                    + sn + ": " + msg;
            Log.debug(str);

        } else if (cmd instanceof WarningNotification) {
            WarningNotification wn = (WarningNotification) cmd;
            MiniUserInfo warner = wn.getWarner();
            if (warner == null) {
                Log.debug("*** You were warned anonymously to "
                        + wn.getNewLevel() + "%");
            } else {
                Log.debug("*** " + warner.getScreenname()
                        + " warned you up to " + wn.getNewLevel() + "%");
            }
        } else if (cmd instanceof BuddyStatusCmd) {
            BuddyStatusCmd bsc = (BuddyStatusCmd)cmd;
            FullUserInfo info = bsc.getUserInfo();
            buddystore.put(info.getScreenname(), info);
            Presence p = new Presence();
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(info.getScreenname()));

            if (info.getAwayStatus() == true) {
                p.setShow(Presence.Show.away);
            }

            ExtraInfoBlock[] extraInfo = info.getExtraInfoBlocks();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
                        ByteBlock msgBlock = data.getData();
                        int len = BinaryTools.getUShort(msgBlock, 0);
                        byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray(
);
                        String msg;
                        try {
                            msg = new String(msgBytes, "UTF-8");
                        }
                        catch (UnsupportedEncodingException e1) {
                            continue;
                        }
                        if (msg.length() > 0) {
                            p.setStatus(msg);
                        }
                    }
                }
            }
            oscarSession.getTransport().sendPacket(p);
        } else if (cmd instanceof BuddyOfflineCmd) {
            BuddyOfflineCmd boc = (BuddyOfflineCmd)cmd;
            buddystore.remove(boc.getScreenname());
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(boc.getScreenname()));
            oscarSession.getTransport().sendPacket(p);
        } else if (cmd instanceof RateChange) {
            RateChange rc = (RateChange) cmd;

            Log.debug("rate change: current avg is "
                    + rc.getRateInfo().getCurrentAvg());
        }
    }

    protected void handleSnacResponse(SnacResponseEvent e) {
        SnacPacket packet = e.getSnacPacket();
        Log.debug("got snac response type "
                + Integer.toHexString(packet.getFamily()) + "/"
                + Integer.toHexString(packet.getCommand()) + ": "
                + e.getSnacCommand());

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            RateInfoCmd ric = (RateInfoCmd) cmd;

            RateClassInfo[] rateClasses = ric.getRateClassInfos();

            int[] classes = new int[rateClasses.length];
            for (int i = 0; i < rateClasses.length; i++) {
                classes[i] = rateClasses[i].getRateClass();
//                Log.debug("- " + rateClasses[i] + ": " + Arrays.asList(rateClasses[i].getCommands()));
            }

            request(new RateAck(classes));
        }
    }

    public int[] getSnacFamilies() { return snacFamilies; }

    protected void setSnacFamilies(int[] families) {
        this.snacFamilies = (int[]) families.clone();
        Arrays.sort(snacFamilies);
    }

    protected void setSnacFamilyInfos(SnacFamilyInfo[] infos) {
        snacFamilyInfos = infos;
    }

    protected boolean supportsFamily(int family) {
        return Arrays.binarySearch(snacFamilies, family) >= 0;
    }

    protected void clientReady() {
        if (!sentClientReady) {
            sentClientReady = true;
            request(new ClientReadyCmd(snacFamilyInfos));
        }
    }

    protected SnacRequest dispatchRequest(SnacCommand cmd) {
        return dispatchRequest(cmd, null);
    }

    protected SnacRequest dispatchRequest(SnacCommand cmd,
            SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);
        dispatchRequest(req);
        return req;
    }

    protected void dispatchRequest(SnacRequest req) {
        oscarSession.handleRequest(req);
    }

    protected SnacRequest request(SnacCommand cmd,
            SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);

        handleReq(req);

        return req;
    }

    private void handleReq(SnacRequest request) {
        int family = request.getCommand().getFamily();
        if (snacFamilies == null || supportsFamily(family)) {
            // this connection supports this snac, so we'll send it here
            sendRequest(request);
        } else {
            oscarSession.handleRequest(request);
        }
    }

    /**
     * Retrieves and sends last known status.
     *
     * This retrieves the last known status of the user and sends it on
     * to the JID associated with this session.  Meant for probe packets.
     *
     * @param sn Screen name to check on.
     */
    public void getAndSendStatus(String sn) {
        if (buddystore.containsKey(sn)) {
            FullUserInfo info = buddystore.get(sn);
            buddystore.put(info.getScreenname(), info);
            Presence p = new Presence();
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(info.getScreenname()));

            if (info.getAwayStatus() == true) {
                p.setShow(Presence.Show.away);
            }

            ExtraInfoBlock[] extraInfo = info.getExtraInfoBlocks();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
                        ByteBlock msgBlock = data.getData();
                        int len = BinaryTools.getUShort(msgBlock, 0);
                        byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray(
);
                        String msg;
                        try {
                            msg = new String(msgBytes, "UTF-8");
                        }
                        catch (UnsupportedEncodingException e1) {
                            continue;
                        }
                        if (msg.length() > 0) {
                            p.setStatus(msg);
                        }
                    }
                }
            }
            oscarSession.getTransport().sendPacket(p);
        }
        else {
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(sn));
            oscarSession.getTransport().sendPacket(p);
        }
    }

}
