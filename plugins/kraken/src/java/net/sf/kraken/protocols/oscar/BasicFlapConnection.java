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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.kano.joscar.BinaryTools;
import net.kano.joscar.ByteBlock;
import net.kano.joscar.flap.FlapCommand;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flapcmd.LoginFlapCmd;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.ratelim.RateLimitingQueueMgr;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacRequest;
import net.kano.joscar.snac.SnacRequestAdapter;
import net.kano.joscar.snac.SnacRequestListener;
import net.kano.joscar.snac.SnacRequestTimeoutEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snaccmd.ExtraInfoBlock;
import net.kano.joscar.snaccmd.ExtraInfoData;
import net.kano.joscar.snaccmd.FullUserInfo;
import net.kano.joscar.snaccmd.MiniUserInfo;
import net.kano.joscar.snaccmd.SnacFamilyInfoFactory;
import net.kano.joscar.snaccmd.buddy.BuddyOfflineCmd;
import net.kano.joscar.snaccmd.buddy.BuddyStatusCmd;
import net.kano.joscar.snaccmd.conn.ClientReadyCmd;
import net.kano.joscar.snaccmd.conn.ClientVersionsCmd;
import net.kano.joscar.snaccmd.conn.ExtraInfoAck;
import net.kano.joscar.snaccmd.conn.RateAck;
import net.kano.joscar.snaccmd.conn.RateClassInfo;
import net.kano.joscar.snaccmd.conn.RateInfoCmd;
import net.kano.joscar.snaccmd.conn.RateInfoRequest;
import net.kano.joscar.snaccmd.conn.ServerReadyCmd;
import net.kano.joscar.snaccmd.conn.SnacFamilyInfo;
import net.kano.joscar.snaccmd.conn.WarningNotification;
import net.kano.joscar.snaccmd.error.SnacError;
import net.kano.joscar.snaccmd.icbm.InstantMessage;
import net.kano.joscar.snaccmd.icbm.OldIcbm;
import net.kano.joscar.snaccmd.icbm.RecvImIcbm;
import net.kano.joscar.snaccmd.icbm.TypingCmd;
import net.kano.joscar.snaccmd.icon.IconDataCmd;
import net.kano.joscar.snaccmd.icon.IconRequest;
import net.kano.joscar.snaccmd.icon.UploadIconAck;
import net.kano.joscar.snaccmd.icon.UploadIconCmd;
import net.sf.kraken.avatars.Avatar;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportType;
import net.sf.kraken.util.StringUtils;
import net.sf.kraken.util.chatstate.ChatStateEventSource;

import org.apache.log4j.Logger;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Handles incoming FLAP packets.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public abstract class BasicFlapConnection extends AbstractFlapConnection {

    static Logger Log = Logger.getLogger(BasicFlapConnection.class);

    protected final ByteBlock cookie;
    protected boolean sentClientReady = false;

    protected int[] snacFamilies = null;
    protected Collection<SnacFamilyInfo> snacFamilyInfos;
    protected RateLimitingQueueMgr rateMgr = new RateLimitingQueueMgr();

    public BasicFlapConnection(ConnDescriptor cd, OSCARSession mainSession, ByteBlock cookie) {
        super(cd, mainSession);
        this.cookie = cookie;
        initBasicFlapConnection();
    }

    private void initBasicFlapConnection() {
        sp.setSnacQueueManager(rateMgr);
    }

    protected DateFormat dateFormat
            = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT);

    @Override
    protected void handleFlapPacket(FlapPacketEvent e) {
        Log.debug("OSCAR flap packet received: "+e);
        FlapCommand cmd = e.getFlapCommand();

        if (cmd instanceof LoginFlapCmd) {
            getFlapProcessor().sendFlap(new LoginFlapCmd(cookie));
        }
    }

    @Override
    protected void handleSnacPacket(SnacPacketEvent e) {
        Log.debug("OSCAR snac packet received: "+e);
        SnacCommand cmd = e.getSnacCommand();
        if (cmd instanceof ServerReadyCmd) {
            ServerReadyCmd src = (ServerReadyCmd) cmd;
            setSnacFamilies(src.getSnacFamilies());

            Collection<SnacFamilyInfo> familyInfos = SnacFamilyInfoFactory.getDefaultFamilyInfos(src.getSnacFamilies());
            setSnacFamilyInfos(familyInfos);

            getMainSession().registerSnacFamilies(this);

            request(new ClientVersionsCmd(familyInfos));
            request(new RateInfoRequest());
        }
        else if (cmd instanceof RecvImIcbm) {
            RecvImIcbm icbm = (RecvImIcbm) cmd;

            String sn = icbm.getSenderInfo().getScreenname();
            InstantMessage message = icbm.getMessage();
            String msg = StringUtils.convertFromHtml(message.getMessage());

            getMainSession().getTransport().sendMessage(
                    getMainSession().getJID(),
                    getMainSession().getTransport().convertIDToJID(sn),
                    msg
            );
        }
        else if (cmd instanceof OldIcbm) {
            OldIcbm oicbm = (OldIcbm) cmd;
            if (oicbm.getMessageType() == OldIcbm.MTYPE_PLAIN) {
                String uin = String.valueOf(oicbm.getSender());
                String msg = StringUtils.convertFromHtml(oicbm.getReason());
                Log.debug("Got ICBM message "+uin+" with "+msg+"\n"+oicbm);
//                InstantMessage message = oicbm.getMessage();
//                Log.debug("Got ICBM message "+uin+" with "+message+"\n"+oicbm);
//                String msg = StringUtils.unescapeFromXML(OscarTools.stripHtml(message.getMessage()));

                getMainSession().getTransport().sendMessage(
                        getMainSession().getJID(),
                        getMainSession().getTransport().convertIDToJID(uin),
                        msg
                );
            }
        }
        else if (cmd instanceof WarningNotification) {
            WarningNotification wn = (WarningNotification) cmd;
            MiniUserInfo warner = wn.getWarner();
            if (warner == null) {
                getMainSession().getTransport().sendMessage(
                        getMainSession().getJID(),
                        getMainSession().getTransport().getJID(),
                        LocaleUtils.getLocalizedString("gateway.aim.warninganon", "kraken", Arrays.asList(wn.getNewLevel().toString())),
                        Message.Type.headline
                );
            }
            else {
                Log.debug("*** " + warner.getScreenname()
                        + " warned you up to " + wn.getNewLevel() + "%");
                getMainSession().getTransport().sendMessage(
                        getMainSession().getJID(),
                        getMainSession().getTransport().getJID(),
                        LocaleUtils.getLocalizedString("gateway.aim.warningdirect", "kraken", Arrays.asList(warner.getScreenname(), wn.getNewLevel().toString())),
                        Message.Type.headline
                );
            }
        }
        else if (cmd instanceof ExtraInfoAck) {
            ExtraInfoAck eia = (ExtraInfoAck)cmd;
            List<ExtraInfoBlock> extraInfo = eia.getExtraInfos();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    final byte[] pendingAvatar = getMainSession().getSsiHierarchy().getPendingAvatarData();
                    if (JiveGlobals.getBooleanProperty("plugin.gateway."+getMainSession().getTransport().getType()+".avatars", true) && (data.getFlags() & ExtraInfoData.FLAG_UPLOAD_ICON) != 0 && pendingAvatar != null) {
                        Log.debug("OSCAR: Server has indicated that it wants our icon.");
                        request(new UploadIconCmd(ByteBlock.wrap(pendingAvatar)), new SnacRequestAdapter() {
                            @Override
                            public void handleResponse(SnacResponseEvent e) {
                                SnacCommand cmd = e.getSnacCommand();
                                if (cmd instanceof UploadIconAck && pendingAvatar != null) {
                                    UploadIconAck iconAck = (UploadIconAck) cmd;
                                    if (iconAck.getCode() == UploadIconAck.CODE_DEFAULT || iconAck.getCode() == UploadIconAck.CODE_SUCCESS) {
                                        ExtraInfoBlock iconInfo = iconAck.getIconInfo();
                                        if (iconInfo == null) {
                                          Log.debug("OSCAR: Got icon ack with no iconInfo: " + iconAck);
                                        }
                                        Log.debug("OSCAR: Successfully set icon.");
                                        try {
                                            MessageDigest md = MessageDigest.getInstance("MD5");
                                            md.update(pendingAvatar);
                                            getMainSession().getAvatar().setLegacyIdentifier(org.jivesoftware.util.StringUtils.encodeHex(md.digest()));
                                        }
                                        catch (NoSuchAlgorithmException ee) {
                                            Log.error("No algorithm found for MD5!", ee);
                                        }
                                    }
                                    else if (iconAck.getCode() == UploadIconAck.CODE_BAD_FORMAT) {
                                        Log.debug("OSCAR: Uploaded icon was not in an unaccepted format.");
                                    }
                                    else if (iconAck.getCode() == UploadIconAck.CODE_TOO_LARGE) {
                                        Log.debug("OSCAR: Uploaded icon was too large to be accepted.");
                                    }
                                    else {
                                        Log.debug("OSCAR: Got unknown code from UploadIconAck: " + iconAck.getCode());
                                    }
                                }
                                else if (cmd instanceof SnacError) {
                                    Log.debug("Got SnacError while setting icon: " + cmd);
                                }
                                
                                // Clear the pending binary data from Krakens memory.
                                getMainSession().getSsiHierarchy().clearPendingAvatar();
                            }
                        });
                    }
                }
            }
        }
        else if (cmd instanceof BuddyStatusCmd) {
            BuddyStatusCmd bsc = (BuddyStatusCmd)cmd;
            FullUserInfo info = bsc.getUserInfo();
            PresenceType pType = PresenceType.available;
            String vStatus = "";
            if (info.getAwayStatus()) {
                pType = PresenceType.away;
            }
            if ((info.getFlags() & FullUserInfo.MASK_WIRELESS) != 0) {
                pType = PresenceType.xa;
                vStatus = "Mobile: ";
            }

            if (getMainSession().getTransport().getType().equals(TransportType.icq) && info.getScreenname().matches("/^\\d+$/")) {
                pType = ((OSCARTransport)getMainSession().getTransport()).convertICQStatusToXMPP(info.getIcqStatus());
            }

            List<ExtraInfoBlock> extraInfo = info.getExtraInfoBlocks();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
                        ByteBlock msgBlock = data.getData();
                        int len = BinaryTools.getUShort(msgBlock, 0);
                        if (len >= 0) {
                            byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray();
                            String msg;
                            try {
                                msg = new String(msgBytes, "UTF-8");
                            }
                            catch (UnsupportedEncodingException e1) {
                                continue;
                            }
                            if (msg.length() > 0) {
                                vStatus = vStatus + msg;
                            }
                        }
                    }
                    else if (i.getType() == ExtraInfoBlock.TYPE_ICONHASH && JiveGlobals.getBooleanProperty("plugin.gateway."+getMainSession().getTransport().getType()+".avatars", true)) {
                        try {
                            OSCARBuddy oscarBuddy = getMainSession().getBuddyManager().getBuddy(getMainSession().getTransport().convertIDToJID(info.getScreenname()));
                            Avatar curAvatar = oscarBuddy.getAvatar();
                            if (curAvatar == null || !curAvatar.getLegacyIdentifier().equals(org.jivesoftware.util.StringUtils.encodeHex(i.getExtraData().getData().toByteArray()))) {
                                IconRequest req = new IconRequest(info.getScreenname(), i.getExtraData());
                                request(req, new SnacRequestAdapter() {
                                    @Override
                                    public void handleResponse(SnacResponseEvent e) {
                                        SnacCommand cmd = e.getSnacCommand();
                                        if (cmd instanceof IconDataCmd) {
                                            IconDataCmd idc = (IconDataCmd)cmd;
                                            if (idc.getIconData().getLength() > 0 && idc.getIconData().getLength() != 90) {
                                                Log.debug("Got icon data: "+idc);
                                                if (getMainSession().getBuddyManager().isActivated()) {
                                                    try {
                                                        OSCARBuddy oscarBuddy = getMainSession().getBuddyManager().getBuddy(getMainSession().getTransport().convertIDToJID(idc.getScreenname()));
                                                        oscarBuddy.setAvatar(new Avatar(getMainSession().getTransport().convertIDToJID(idc.getScreenname()), org.jivesoftware.util.StringUtils.encodeHex(idc.getIconInfo().getExtraData().getData().toByteArray()), idc.getIconData().toByteArray()));
                                                    }
                                                    catch (NotFoundException ee) {
                                                        // Apparently we don't care about this contact.
                                                    }
                                                    catch (IllegalArgumentException ee) {
                                                        Log.debug("OSCAR: Got null avatar, ignoring.");
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    @Override
                                    public void handleTimeout(SnacRequestTimeoutEvent e) {
                                        Log.debug("Time out while waiting for icon data.");
                                    }
                                });
                            }
                        }
                        catch (NotFoundException ee) {
                            // Apparently we don't care about this contact.
                        }
                    }
                }
            }
            if (getMainSession().getBuddyManager().isActivated()) {
                try {
                    OSCARBuddy oscarBuddy = getMainSession().getBuddyManager().getBuddy(getMainSession().getTransport().convertIDToJID(info.getScreenname()));
                    oscarBuddy.setPresenceAndStatus(pType, vStatus);
                }
                catch (NotFoundException ee) {
                    // Apparently we don't care about this contact.
                    Log.debug("OSCAR: Received presense notification for contact we don't care about: "+info.getScreenname());
                }
            }
            else {
                getMainSession().getBuddyManager().storePendingStatus(getMainSession().getTransport().convertIDToJID(info.getScreenname()), pType, vStatus);
            }
        }
        else if (cmd instanceof BuddyOfflineCmd) {
            BuddyOfflineCmd boc = (BuddyOfflineCmd)cmd;
            if (getMainSession().getBuddyManager().isActivated()) {
                try {
                    OSCARBuddy oscarBuddy = getMainSession().getBuddyManager().getBuddy(getMainSession().getTransport().convertIDToJID(boc.getScreenname()));
                    oscarBuddy.setPresence(PresenceType.unavailable);
                }
                catch (NotFoundException ee) {
                    // Apparently we don't care about this contact.
                }
            }
            else {
                getMainSession().getBuddyManager().storePendingStatus(getMainSession().getTransport().convertIDToJID(boc.getScreenname()), PresenceType.unavailable, null);
            }
        }
        else if (cmd instanceof TypingCmd) {
            TypingCmd tc = (TypingCmd) cmd;
            String sn = tc.getScreenname();

            final ChatStateEventSource chatStateEventSource = getMainSession().getTransport().getChatStateEventSource();
            final JID receiver = getMainSession().getJID();
            final JID sender = getMainSession().getTransport().convertIDToJID(sn);
            
            if (tc.getTypingState() == TypingCmd.STATE_TYPING) {
                chatStateEventSource.isComposing(sender, receiver);
            }
            else if (tc.getTypingState() == TypingCmd.STATE_PAUSED) {
                chatStateEventSource.sendIsPaused(sender, receiver);
            }
            else if (tc.getTypingState() == TypingCmd.STATE_NO_TEXT) {
                chatStateEventSource.isInactive(sender, receiver);
            }
        }
    }

    @Override
    protected void handleSnacResponse(SnacResponseEvent e) {
        Log.debug("OSCAR snac packet response: "+e);
        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            RateInfoCmd ric = (RateInfoCmd) cmd;
            List <RateClassInfo> rateClasses = ric.getRateClassInfos();

            int[] classes = new int[rateClasses.size()];
            for (int i = 0; i < rateClasses.size(); i++) {
                classes[i] = rateClasses.get(i).getRateClass();
            }

            request(new RateAck(classes));
        }
    }

    public int[] getSnacFamilies() { return snacFamilies; }

    protected void setSnacFamilies(int[] families) {
        this.snacFamilies = families.clone();
        Arrays.sort(snacFamilies);
    }

    protected void setSnacFamilyInfos(Collection<SnacFamilyInfo> infos) {
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
        getMainSession().handleRequest(req);
    }

    @Override
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
        }
        else {
            getMainSession().handleRequest(request);
        }
    }

}
