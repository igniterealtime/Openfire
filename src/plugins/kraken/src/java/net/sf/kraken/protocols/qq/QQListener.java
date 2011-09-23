/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.qq;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jqql.beans.DownloadFriendEntry;
import net.sf.jqql.beans.FriendOnlineEntry;
import net.sf.jqql.beans.NormalIM;
import net.sf.jqql.beans.QQFriend;
import net.sf.jqql.events.IQQListener;
import net.sf.jqql.events.QQEvent;
import net.sf.jqql.packets.in.*;
import net.sf.jqql.packets.in._08._08GetOnlineOpReplyPacket;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;

public class QQListener implements IQQListener {

    static Logger Log = Logger.getLogger(QQListener.class);

    private static String defaultGroupName = JiveGlobals.getProperty(
            "plugin.gateway.qq.defaultRosterName", "Friends");
//    public static final SimpleDateFormat sdf = new SimpleDateFormat(
//            "yyyy-MM-dd HH:mm:ss");
    private List<String> groupNames = new ArrayList<String>();
    private Map<Integer, QQFriend> friends = new HashMap<Integer, QQFriend>();
    private Map<Integer, String> friendGroup = new HashMap<Integer, String>();
//    private Map<Integer,
//                ClusterInfo> clusters = new HashMap<Integer, ClusterInfo>();
//    private Map<Integer,
//                Map<Integer, String>> clusterMembers = new Hashtable<Integer,
//            Map<Integer, String>>(); //group members
    
    /**
     * Creates a QQ session listener affiliated with a session.
     *
     * @param session The QQSession instance we are associated with.
     */
    public QQListener(QQSession session) {
        this.qqSessionRef = new WeakReference<QQSession>(session);
    }

    /**
     * The transport session we are affiliated with.
     */
    WeakReference<QQSession> qqSessionRef;

    /**
     * Returns the QQ session this listener is attached to.
     *
     * @return QQ session we are attached to.
     */
    public QQSession getSession() {
        return qqSessionRef.get();
    }
    
    public void qqEvent(QQEvent e) {
        Log.debug("QQ: Received - " + e.getSource() + " Event ID: 0x"+Integer.toHexString(e.type));
        switch (e.type) {
            case QQEvent.LOGIN_OK:
                processSuccessfulLogin();
                break;
            case QQEvent.LOGIN_FAIL:
                getSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                getSession().sessionDisconnectedNoReconnect(null);
                break;
            case QQEvent.LOGIN_UNKNOWN_ERROR:
            case QQEvent.ERROR_CONNECTION_BROKEN:
                getSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);
                getSession().sessionDisconnected(null);
                break;
            case QQEvent.USER_STATUS_CHANGE_OK:
                processStatusChangeOK((ChangeStatusReplyPacket)e.getSource());
                break;
            case QQEvent.USER_STATUS_CHANGE_FAIL:
                getSession().sessionDisconnected(null);
                break;
            case QQEvent.FRIEND_DOWNLOAD_GROUPS_OK:
                processGroupFriend(e);
                break;
            case QQEvent.FRIEND_GET_GROUP_NAMES_OK:
                processGroupNames(e);
                break;
            case QQEvent.USER_GET_INFO_OK:
                processFriendInfo(e);
                break;
    //        case QQEvent.QQ_GET_CLUSTER_INFO_SUCCESS:
    //            processClusterInfo(e);
    //            break;
    //        case QQEvent.QQ_GET_MEMBER_INFO_SUCCESS:
    //            processClusterMemberInfo(e);
    //            break;
    //        case QQEvent.QQ_RECEIVE_CLUSTER_IM:
    //            processClusterIM(e);
    //            break;
            case QQEvent.IM_RECEIVED:
                processNormalIM((ReceiveIMPacket)e.getSource());
                break;
            case QQEvent.ERROR_NETWORK:
            case QQEvent.ERROR_RUNTIME:
                getSession().setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);                
                getSession().sessionDisconnected(null);
                break;
            case QQEvent.FRIEND_GET_ONLINE_OK:
                processFriendOnline((_08GetOnlineOpReplyPacket)e.getSource());
                break;
            case QQEvent.FRIEND_STATUS_CHANGED:
                processFriendChangeStatus((FriendChangeStatusPacket)e.getSource());
                break;
            case QQEvent.FRIEND_GET_LIST_OK:
                processFriendList((GetFriendListReplyPacket)e.getSource());
                break;
            case QQEvent.LOGIN_TOUCH:
                getSession().getQQClient().sendToken1();
            default:
                break;
    
        }
    }

    private void processFriendList(GetFriendListReplyPacket p) {
        Log.debug("QQ: processing friend list");
        // For whatever reason, this no longer returns anything useful.  So we go another route to get info on our peeps.
//        try {
//            for (QQFriend f : p.friends) {
//                Log.debug("Found QQ friend: "+f);
//                friends.put(f.qqNum, f);
//
//                String groupName = friendGroup.get(f.qqNum);
//                if (groupName == null || groupName.trim().length() < 1) {
//                    groupName = defaultGroupName;
//                }
//                List<String> gl = new ArrayList<String>();
//                gl.add(groupName);
//                QQBuddy qqBuddy = new QQBuddy(getSession().getBuddyManager(), f, f.nick, gl);
//                getSession().getBuddyManager().storeBuddy(qqBuddy);
//            }
//            if (p.position != 0xFFFF) {
//                getSession().getQQClient().user_GetList(p.position);
//            }
//        } catch (Exception ex) {
//            Log.error("Failed to process friend list: ", ex);
//        }
        
        // Lets try the actual sync.
        try {
            getSession().getTransport().syncLegacyRoster(getSession().getJID(), getSession().getBuddyManager().getBuddies());
        }
        catch (UserNotFoundException ex) {
            Log.debug("Unable to sync QQ contact list for " + getSession().getJID());
        }
        
        getSession().getBuddyManager().activate();

        getSession().getQQClient().user_GetOnline();
    }

    private void processGroupFriend(QQEvent e) {
        Log.debug("QQ: Processing group friend.");
        try {
            DownloadGroupFriendReplyPacket p =
                    (DownloadGroupFriendReplyPacket) e.getSource();
            for (DownloadFriendEntry entry : p.friends) {
//                if (entry.isCluster()) {
//                    getSession().getQQClient().getClusterInfo(entry.qqNum);
//                } else {
                    if (groupNames != null && groupNames.size() > entry.group) {
                        String groupName = groupNames.get(entry.group);
                        friendGroup.put(entry.qqNum, groupName);
                        List<String> gl = new ArrayList<String>();
                        gl.add(groupName);
                        QQBuddy qqBuddy = new QQBuddy(getSession().getBuddyManager(), entry.qqNum, gl);
                        getSession().getBuddyManager().storeBuddy(qqBuddy);
                    } else {
                        friendGroup.put(entry.qqNum, defaultGroupName);
                        List<String> gl = new ArrayList<String>();
                        gl.add(defaultGroupName);
                        QQBuddy qqBuddy = new QQBuddy(getSession().getBuddyManager(), entry.qqNum, gl);
                        getSession().getBuddyManager().storeBuddy(qqBuddy);
                    }
//                }
                getSession().getQQClient().user_GetInfo(entry.qqNum);
            }
//            if (p.beginFrom != 0) {
//                getSession().getQQClient().getClusterOnlineMember(p.beginFrom);
//            }
        } catch (Exception ex) {
            Log.error("Failed to process group friend: ", ex);
        }
        getSession().getQQClient().user_GetList();
    }

    private void processFriendInfo(QQEvent e) {
        Log.debug("QQ: Processing friend info request");
        GetUserInfoReplyPacket p = (GetUserInfoReplyPacket) e.getSource();
        try {
            QQBuddy buddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(String.valueOf(p.contactInfo.qq)));
            buddy.setNickname(p.contactInfo.nick);
            buddy.contactInfo = p.contactInfo;
            getSession().getBuddyManager().storeBuddy(buddy);
        }
        catch (NotFoundException nfe) {
            Log.debug("QQ: Received buddy "+p.contactInfo.qq+" that we don't know about.");
        }
    }

    private void processGroupNames(QQEvent e) {
        Log.debug("QQ: Processing group names");
        try {
            groupNames.clear();
            groupNames.add(defaultGroupName);
            GroupDataOpReplyPacket p =
                    (GroupDataOpReplyPacket) e.getSource();
            groupNames.addAll(p.groupNames);
        } catch (Exception ex) {
            Log.error("Failed to process group names: ", ex);
        }
        getSession().getQQClient().user_DownloadGroups(0);
    }

//    private void processClusterInfo(QQEvent e) {
//        try {
//            ClusterCommandReplyPacket p = (ClusterCommandReplyPacket) e.
//                                          getSource();
//            ClusterInfo info = p.info;
//            if (QQ.QQ_CLUSTER_TYPE_PERMANENT == info.type) {
//                clusters.put(info.externalId, info);
//            }
//            List<String> gl = new ArrayList<String>();
//            gl.add(JiveGlobals.getProperty("plugin.gateway.qq.qqGroupName",
//                                           "QQ Group"));
//            TransportBuddy tb = new TransportBuddy(getSession().getBuddyManager(),
//                    String.valueOf(info.externalId), info.name, gl);
//            getSession().getBuddyManager().storeBuddy(tb);
//            Presence pp = new Presence();
//            pp.setFrom(getSession().getTransport().convertIDToJID(String.valueOf(info.externalId)));
//            pp.setTo(getSession().getJID());
//            pp.setShow(Presence.Show.chat);
//            getSession().getTransport().sendPacket(pp);
//            qqclient.getClusterMemberInfo(info.clusterId, p.members);
//        } catch (Exception ex) {
//            Log.error("Failed to process cluster info: ", ex);
//        }
//    }
//
//    private void processClusterMemberInfo(QQEvent e) {
//        try {
//            ClusterCommandReplyPacket p = (ClusterCommandReplyPacket) e.
//                                          getSource();
//            Map<Integer, String> cmm = new HashMap<Integer, String>();
//            for (Object obj : p.memberInfos) {
//                QQFriend m = (QQFriend) obj;
//                cmm.put(m.qqNum, m.nick);
//            }
//            int clusterId = 0;
//            for (ClusterInfo c : clusters.values()) {
//                if (c.clusterId == p.clusterId) {
//                    clusterId = c.externalId;
//                }
//            }
//            clusterMembers.put(clusterId, cmm);
//        } catch (Exception ex) {
//            Log.error("Failed to process cluster member info: ", ex);
//        }
//    }
    
    private void processSuccessfulLogin() {
        Log.debug("QQ: Processing successful login");
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);

        getSession().getQQClient().user_GetGroupNames();
    }

    private void processStatusChangeOK(ChangeStatusReplyPacket p) {
        Log.debug("QQ: Processing status change success");
//        if (!getSession().isLoggedIn()) {
//            getSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
//            getSession().getQQClient().getFriendList();
//            getSession().getQQClient().downloadGroup();
//            getSession().getQQClient().getFriendOnline();
//        }
    }

//    private void processClusterIM(QQEvent e) {
//        try {
//            ReceiveIMPacket p = (ReceiveIMPacket) e.getSource();
//            ClusterIM im = p.clusterIM;
//            if (clusters.get(im.externalId) == null) {
//                qqclient.downloadGroup();
//            }
//            String sDate = sdf.format(new Date(im.sendTime));
//            String clusterName = "";
//            try {
//                clusterName = clusters.get(im.externalId).name;
//            } catch (Exception ex) {
//                Log.debug("Failed to get cluster name: ", ex);
//            }
//            String senderName = " ";
//            try {
//                senderName = clusterMembers.get(im.externalId).get(im.sender);
//            } catch (Exception ex) {
//                Log.debug("Failed to get sender name: ", ex);
//            }
//            String msg = clusterName + "[" + im.externalId + "]"
//                         + senderName + "(" + im.sender + ") "
//                         + sDate + ":\n"
//                         + new String(im.messageBytes) + "\n";
//            Message m = new Message();
//            m.setType(Message.Type.chat);
//            m.setTo(getSession().getJID());
//            m.setFrom(getSession().getTransport().convertIDToJID(String.valueOf(im.externalId)));
//            String b = " ";
//            try {
//                b = new String(msg);
//            } catch (Exception ex) {
//                Log.debug("Failed to string-ify message: ", ex);
//            }
//            m.setBody(b);
//            getSession().getTransport().sendPacket(m);
//        } catch (Exception ex) {
//            Log.error("Failed to handle cluster IM: ", ex);
//        }
//    }

    /**
     * Handles a standard instant message being sent to us.
     * 
     * @param p Event of the message.
     */
    private void processNormalIM(ReceiveIMPacket p) {
        Log.debug("QQ: Processing normal IM received.");
        NormalIM im = p.normalIM;
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(String.valueOf(p.normalHeader.sender)),
                im.message
        );
    }

    /**
     * Handles an event when a friend has come online.
     * 
     * @param p Event to be handled.
     */
    private void processFriendOnline(_08GetOnlineOpReplyPacket p) {
        Log.debug("QQ: Processing friend online notification");
        try {
            for (FriendOnlineEntry f : p.onlineFriends) {
                Log.debug("QQ: Got an online friend");
                if (getSession().getBuddyManager().isActivated()) {
                    try {
                        QQBuddy qqBuddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(String.valueOf(f.status.qqNum)));
                        qqBuddy.setPresenceAndStatus(((QQTransport)getSession().getTransport()).convertQQStatusToXMPP(f.status.status), null);
                    }
                    catch (NotFoundException ee) {
                        // Not in our list.
                        Log.debug("QQ: Received presense notification for contact we don't care about: "+String.valueOf(f.status.qqNum));
                    }
                }
                else {
                    getSession().getBuddyManager().storePendingStatus(getSession().getTransport().convertIDToJID(String.valueOf(f.status.qqNum)), ((QQTransport)getSession().getTransport()).convertQQStatusToXMPP(f.status.status), null);
                }
            }
//            if (!p.finished) {
//                qqclient.getUser().user_GetOnline(p.position);
//            }
        } catch (Exception ex) {
            Log.error("Failed to handle friend online event: ", ex);
        }
    }

    /**
     * Handles an event where a friend changes their status.
     * 
     * @param p Event representing change.
     */
    private void processFriendChangeStatus(FriendChangeStatusPacket p) {
        Log.debug("QQ: Processing friend status change event");
        try {
            if (getSession().getBuddyManager().isActivated()) {
                try {
                    QQBuddy qqBuddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(String.valueOf(p.friendQQ)));
                    qqBuddy.setPresenceAndStatus(((QQTransport)getSession().getTransport()).convertQQStatusToXMPP(p.status), null);
                }
                catch (NotFoundException ee) {
                    // Not in our list.
                    Log.debug("QQ: Received presense notification for contact we don't care about: "+String.valueOf(p.friendQQ));
                }
            }
            else {
                getSession().getBuddyManager().storePendingStatus(getSession().getTransport().convertIDToJID(String.valueOf(p.friendQQ)), ((QQTransport)getSession().getTransport()).convertQQStatusToXMPP(p.status), null);
            }
        } catch (Exception ex) {
            Log.error("Failed to handle friend status change event: ", ex);
        }
    }

}
