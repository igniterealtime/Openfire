/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip.server;

import com.sun.voip.CallParticipant;
import com.sun.voip.ConferenceEvent;
import com.sun.voip.DistributedBridge;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.RtpPacket;
import com.sun.voip.SdpManager;
import com.sun.voip.TreatmentManager;

import java.io.IOException;

import java.text.ParseException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.util.NoSuchElementException;
import java.util.ArrayList;

import org.ifsoft.rayo.RayoComponent;

/**
 * Manage a conference consisting of members.
 * Each conference has a unique String identifying it.
 * Members can join and leave the conference.
 */
public class ConferenceManager {
    private static ArrayList conferenceList = new ArrayList();

    private String             conferenceId;	      // conference identifier

    private String	       displayName;	      // user readable name
    private String	       callId;	      // target call id

    private ArrayList 	       memberList;	      // for iterating members

    private boolean	       isFirstMember = true;

    private boolean	       privateCall = false;

    private boolean	       transferCall = false;

    private CallParticipant	heldCall = null;

    private String 		   groupName = null;

    /*
     * If useSingleSender is true, a single
     * conferenceSender will be used for all conferences.
     */
    private static boolean useSingleSender = true;
    private static ConferenceSender loneConferenceSender;

    private static int loneReceiverPort = 0;
    private static ConferenceReceiver loneConferenceReceiver;

    private ConferenceSender   conferenceSender;      // sender thread
    private WGManager	       wgManager;	      // whisper group manager
    private ConferenceReceiver conferenceReceiver;    // receiver thread

    private boolean	       permanent = false;

    private static int	       totalMembers = 0;

    private String 	       mediaPreference;	      // new media preference
    private MediaInfo	       mediaInfo;

    private long	       conferenceStartTime;

    private static 	       DistributedBridge distributedBridge;

    private boolean done = false;

    static {
    String s = System.getProperty("com.sun.voip.server.LONE_RECEIVER_PORT");

    if (s != null && s.length() > 0) {
        try {
        loneReceiverPort = Integer.parseInt(s);
        } catch (NumberFormatException e) {
        Logger.println("Invalid port for lone receiver:  " + s);
        }
    }
    }

    /**
     * Constructor
     *
     * Create a new conference
     * @param conferenceId String identifying the conference
     */
    private ConferenceManager(String conferenceId, String mediaPreference,
        String displayName) throws SocketException {

        this.conferenceId = conferenceId;

    memberList = new ArrayList();

    try {
        setMediaInfo(mediaPreference);
    } catch (ParseException e) {
        Logger.println(conferenceId
        + ":  Can't set meeting media setting to "
        + mediaPreference + ": " + e.getMessage());
    }

    this.displayName = displayName;

    if (useSingleSender == true) {
        if (loneConferenceSender == null) {
        loneConferenceSender = new ConferenceSender(conferenceList);
        }
        conferenceSender = loneConferenceSender;
    } else {
        conferenceSender = new ConferenceSender(this);
    }

    if (loneReceiverPort != 0) {
        if (loneConferenceReceiver == null) {
        loneConferenceReceiver =
                new ConferenceReceiver("Singleton", loneReceiverPort);  // start receiver
        }
        conferenceReceiver = loneConferenceReceiver;
    } else {
        conferenceReceiver = new ConferenceReceiver(conferenceId, 0);  // start receiver
    }
    }

    public void setMediaInfo(String mediaPreference) throws ParseException {
    /*
     * Conference id may be qualified by the media parameters.
     * The syntax is <conferenceId>:<PCM[U]|SPEEX/<sampleRate>/<channels>
     */
    mediaInfo = parseMediaPreference(mediaPreference);
    mediaPreference = null;

    if (wgManager == null) {
        /*
         * Use the conference Id as the name of the main conference
         * whisper group.
         */
        wgManager = new WGManager(conferenceId, mediaInfo);
    } else {
        wgManager.setMediaInfo(mediaInfo);
    }
    }

    private MediaInfo parseMediaPreference(String mediaPreference)
        throws ParseException {

    if (mediaPreference == null) {
        if (mediaInfo != null) {
        return mediaInfo;
        }

        return SdpManager.findMediaInfo(RtpPacket.PCMU_ENCODING, 8000, 1);
    }

    int ix;

    int encoding = RtpPacket.PCMU_ENCODING;
    int sampleRate = 8000;
    int channels = 1;

    try {
        if (mediaPreference.indexOf("PCMU/") == 0) {
        encoding = RtpPacket.PCMU_ENCODING;
        mediaPreference = mediaPreference.substring(5);
        } else if (mediaPreference.indexOf("PCM/") == 0) {
        encoding = RtpPacket.PCM_ENCODING;
        mediaPreference = mediaPreference.substring(4);
        } else if (mediaPreference.indexOf("SPEEX/") == 0) {
        encoding = RtpPacket.SPEEX_ENCODING;
        mediaPreference = mediaPreference.substring(6);
        } else if (mediaPreference.indexOf("PCM") == 0) {
            // do nothing
        } else {
        Logger.println("Invalid media specification " + mediaPreference);
        }

        if ((ix = mediaPreference.indexOf("/")) < 0) {
        Logger.println("Invalid media specification " + mediaPreference);
        } else {
            sampleRate = Integer.parseInt(mediaPreference.substring(0, ix));
            channels = Integer.parseInt(mediaPreference.substring(ix + 1));
        }
    } catch (IndexOutOfBoundsException e) {
        Logger.println("Invalid media specification " + mediaPreference);
    } catch (NumberFormatException e) {
        Logger.println("Invalid media specification " + mediaPreference);
    }

    if (sampleRate == 8000 && channels == 1 &&
            encoding == RtpPacket.PCM_ENCODING) {

        encoding = RtpPacket.PCMU_ENCODING;
    }

    MediaInfo mediaInfo =
        SdpManager.findMediaInfo(encoding, sampleRate, channels);

    Logger.println("conference " + conferenceId
        + " using media settings " + mediaInfo);

    conferenceStartTime = System.currentTimeMillis();

    return mediaInfo;
    }

    public long getConferenceStartTime() {
        return conferenceStartTime;
    }

    public WGManager getWGManager() {
    return wgManager;
    }

    public static WhisperGroup createWhisperGroup(String conferenceId,
        String whisperGroupId, double attenuation) throws ParseException {

    synchronized (conferenceList) {
        ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

        return conferenceManager.createWhisperGroup(whisperGroupId,
        attenuation);
    }
    }

    public WhisperGroup createWhisperGroup(String whisperGroupId,
            double attenuation) throws ParseException {

    synchronized (conferenceList) {
        return wgManager.createWhisperGroup(whisperGroupId, attenuation);
    }
    }

    public static void destroyWhisperGroup(String conferenceId,
        String whisperGroupId) throws ParseException {

        synchronized (conferenceList) {
            ConferenceManager conferenceManager =
                findConferenceManager(conferenceId);

            conferenceManager.destroyWhisperGroup(whisperGroupId);
        }
    }

    public void destroyWhisperGroup(String whisperGroupId)
        throws ParseException {

    synchronized (conferenceList) {
        synchronized (this) {
            wgManager.destroyWhisperGroup(whisperGroupId);
        }
    }
    }

    public static String getAbbreviatedWhisperGroupInfo(boolean showMembers) {
    String s = "";

    synchronized (conferenceList) {
            for (int i = 0; i < conferenceList.size(); i++) {
            ConferenceManager conferenceManager = (ConferenceManager)
                conferenceList.get(i);

            s += "Whisper groups for conference "
            + conferenceManager.getId() + "\n";

            s += conferenceManager.getWGManager().getAbbreviatedWhisperGroupInfo(true);
            s += "\n";
        }
    }

    return s;
    }

    public static String getWhisperGroupInfo() {
    String s = "";

    synchronized (conferenceList) {
            for (int i = 0; i < conferenceList.size(); i++) {
            ConferenceManager conferenceManager = (ConferenceManager)
                conferenceList.get(i);

            s += "Whisper groups for conference "
            + conferenceManager.getId() + "\n";
            s += conferenceManager.getWGManager().getWhisperGroupInfo();
            s += "\n";
        }
    }

    return s;
    }

    public static void setTransientWhisperGroup(String conferenceId,
        String whisperGroupId, boolean isTransient) throws ParseException {

    synchronized (conferenceList) {
        ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

        conferenceManager.getWGManager().setTransientWhisperGroup(
        whisperGroupId, isTransient);
    }
    }

    public static void setLockedWhisperGroup(String conferenceId,
        String whisperGroupId, boolean isLocked) throws ParseException {

    synchronized (conferenceList) {
        ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

        conferenceManager.getWGManager().setLockedWhisperGroup(
        whisperGroupId, isLocked);
    }
    }

    public static void setWhisperGroupAttenuation(String conferenceId,
        String whisperGroupId, double attenuation) throws ParseException {

    synchronized (conferenceList) {
        ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

        conferenceManager.getWGManager().setWhisperGroupAttenuation(
        whisperGroupId, attenuation);
    }
    }

    public static void setWhisperGroupNoCommonMix(String conferenceId,
        String whisperGroupId, boolean noCommonMix) throws ParseException {

    synchronized (conferenceList) {
        ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

        synchronized (conferenceManager) {
        WGManager wgManager = conferenceManager.getWGManager();

        synchronized (wgManager.getWhisperGroups()) {
                wgManager.setWhisperGroupNoCommonMix(whisperGroupId,
            noCommonMix);

            ArrayList memberList = conferenceManager.getMemberList();

            synchronized (memberList) {
            for (int i = 0; i < memberList.size(); i++) {
                ConferenceMember member = (ConferenceMember)
                memberList.get(i);

                /*
                 * Tell members
                 */
                if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("Call " + member
                    + " no common mix");
                }
                member.setNoCommonMix(whisperGroupId);
            }
            }
        }
        }
    }
    }

    private void end() {
        try {
            recordConference(false, null, null);
        } catch (ParseException e) {
            Logger.println(conferenceId
                + ":  Failed to stop recording conference! "
                + e.getMessage());
        }

    Logger.writeFile("ending conf " + conferenceId
        + ":  permanent " + permanent
        + ", mediaPreference " + mediaPreference);

    if (permanent) {
        conferenceSender.printStatistics();

        if (mediaPreference != null) {
        try {
            setMediaInfo(mediaPreference);
        } catch (ParseException e) {
            Logger.println(conferenceId
            + ":  Can't change meeting media setting to "
            + mediaPreference + ": " + e.getMessage());
        }

        mediaPreference = null;
        }
    } else {
        if (done) {
            return;
        }

        done = true;

        ConferenceManager.conferenceEventNotification(new ConferenceEvent(ConferenceEvent.CONFERENCE_ENDED, conferenceId));

        synchronized(conferenceList) {
            conferenceList.remove(this);
        }

        if (conferenceReceiver != loneConferenceReceiver) {
            conferenceReceiver.end();
        }

        conferenceSender.printStatistics();
    }

    int activeConferences = 0;

    synchronized(conferenceList) {
            for (int i = 0; i < conferenceList.size(); i++) {
            ConferenceManager conferenceManager =
            (ConferenceManager) conferenceList.get(i);

            if (conferenceManager.getMemberList().size() > 0) {
            activeConferences++;
            }
        }
    }

    Logger.println("");
    Logger.println("Conference:  '" + conferenceId + "' has ended.  "
        + "conferences still in progress:  " + activeConferences);
    Logger.println("");

    Logger.flush();

    if (totalMembers == 0) {
            /*
             * This is a great time to do a full garbage collection
             */
         Logger.println("No conferences in progress, doing a full GC...");
             System.gc();
    }
    }

    public static void setDistributedBridge(DistributedBridge distributedBridge) {
    ConferenceManager.distributedBridge = distributedBridge;
    }

    private static void conferenceEventNotification(ConferenceEvent event)
    {
        if (distributedBridge != null)
        {
            try {
                distributedBridge.conferenceEventNotification(event);
            } catch (Exception e) {
                Logger.println("conferenceEventNotification exception:  " + e.getMessage());
                e.printStackTrace();
            }
        }

        RayoComponent.self.notifyConferenceMonitors(event);
    }

    public static int getNumberOfConferences() {
    return conferenceList.size();
    }

    public static int getTotalMembers() {
    return totalMembers;
    }

    public static int getNumberOfMembers(String conferenceId)
        throws ParseException {

    ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

    return conferenceManager.getNumberOfMembers();
    }

    public int getNumberOfMembers() {
    if (distributedBridge != null) {
        return distributedBridge.getNumberOfMembers(conferenceId);
    }

    return getMemberList().size();
    }

    /**
     * Get the conference identifier.
     *
     * @return conferenceId String identifying this conference
     */
    public String getId() {
    return conferenceId;
    }


    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public static String getCallId(String conferenceId)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            return conferenceManager.getCallId();
        } catch (ParseException e) {
            return null;
        }
    }

    public static void setCallId(String conferenceId, String callId)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            conferenceManager.setCallId(callId);
        } catch (ParseException e) {

        }
    }

    public String getDisplayName() {
        return displayName;
    }


    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public static String getDisplayName(String conferenceId)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            return conferenceManager.getDisplayName();
        } catch (ParseException e) {
            return null;
        }
    }

    public static void setDisplayName(String conferenceId, String displayName)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            conferenceManager.setDisplayName(displayName);
        } catch (ParseException e) {

        }
    }

    public void setPermanent(boolean permanent) {
    this.permanent = permanent;
    }

    public boolean isPermanent() {
    return permanent;
    }

    /**
     * Get the conferenceSender for this conference.
     *
     * @return conferenceSender ConferenceSender for this conference
     */
    public ConferenceSender getConferenceSender() {
        return conferenceSender;
    }

    public ConferenceReceiver getConferenceReceiver() {
    return conferenceReceiver;
    }

    private void endConferenceSender() {
    synchronized (this) {
        if (conferenceSender != loneConferenceSender) {
            conferenceSender.end();
        }
        if (conferenceReceiver != loneConferenceReceiver) {
            conferenceReceiver.end();
        }
    }
    }

    public void setMediaPreference(String mediaPreference) {
    this.mediaPreference = mediaPreference;
    permanent = true;
    }

    /**
     * Get the memberList for this conference.
     *
     * @return memberList ArrayList of members for this conference.
     */
    public ArrayList getMemberList() {
    return memberList;
    }

    /**
     * Is this the first member.  The first member to call this gets true.
     * Others get false.  This is so that the first member can get a special
     * audio treatment.
     * @return true if this is the first member, false otherwise
     */
    public boolean isFirstMember()
    {
        synchronized (memberList) {
            if (isFirstMember == false) {
                return false;
            }

            isFirstMember = false;

            return memberList.isEmpty();
        }
    }

    public boolean isTransferCall()
    {
        return transferCall;
    }

    public static boolean isTransferCall(String conferenceId)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            return conferenceManager.isTransferCall();
        } catch (ParseException e) {
            return false;
        }
    }

    public void setTransferCall(boolean transferCall)
    {
        this.transferCall = transferCall;
    }

    public boolean isPrivateCall()
    {
        return privateCall;
    }

    public void setPrivateCall(boolean privateCall)
    {
        this.privateCall = privateCall;
    }

    public CallParticipant getHeldCall()
    {
        return heldCall;
    }


    public static CallParticipant getHeldCall(String conferenceId)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            return conferenceManager.getHeldCall();
        } catch (ParseException e) {
            return null;
        }
    }

    public void setHeldCall(CallParticipant heldCall)
    {
        this.heldCall = heldCall;
    }

    public static void setHeldCall(String conferenceId, CallParticipant heldCall)
    {
        try {
            ConferenceManager conferenceManager = findConferenceManager(conferenceId);
            conferenceManager.setHeldCall(heldCall);
        } catch (ParseException e) {

        }
    }

    public String getGroupName()
    {
        return groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
    }

    /**
     * Add a new member to the conference
     *
     * @param cp  CallParticipant wishing to join the conference.
     * @return ConferenceMember
     */
    public ConferenceMember joinConference(CallParticipant cp)
        throws IOException {

        if (conferenceJoinTreatment != null) {
            cp.setConferenceJoinTreatment(conferenceJoinTreatment);
        }

        if (conferenceLeaveTreatment != null) {
            cp.setConferenceLeaveTreatment(conferenceLeaveTreatment);
        }

        if (conferenceAnswerTreatment != null) {
            cp.setCallEstablishedTreatment(conferenceAnswerTreatment);
        cp.setCallAnsweredTreatment(null);
        cp.setJoinConfirmationTimeout(0);
        }

    if (displayName != null) {
        //cp.setDisplayName(displayName);
        cp.setConferenceDisplayName(displayName);
    }

    ConferenceMember member = new ConferenceMember(this, cp);

    joinConference(member);
    return member;
    }

    private void joinConference(ConferenceMember member) throws IOException {
        synchronized (memberList) {
            memberList.add(member);
        totalMembers++;

        String s = "";

        conferenceReceiver.addMember(member);

            Logger.println("conferenceManager:  '" + conferenceId + "',"
                + " new member " + member + s
                + " total members:  " + memberList.size());
        }

        ConferenceEvent event = new ConferenceEvent(ConferenceEvent.MEMBER_JOINED, conferenceId);
        event.setCallId(member.getCallParticipant().getCallId());
        event.setMemberAddress(member.getMemberSender().getSendAddress());
        event.setMemberCount(memberList.size());
        conferenceEventNotification(event);
    }

    public static boolean hasCommonMix(String conferenceId) {
    synchronized(conferenceList) {
        try {
                ConferenceManager conferenceManager =
            findConferenceManager(conferenceId);
            return conferenceManager.hasCommonMix();
        } catch (ParseException e) {
        Logger.println(e.getMessage());
        }

        return false;
    }
    }

    public boolean hasCommonMix() {
    return wgManager.hasCommonMix();
    }

    public void joinDistributedConference(ConferenceMember member) {
    ConferenceEvent event = new ConferenceEvent(
        ConferenceEvent.MEMBER_JOINED, conferenceId);

    event.setCallId(member.getCallParticipant().getCallId());
    event.setMemberAddress(
        member.getMemberSender().getSendAddress());
    event.setIsDistributedBridge(
            member.getCallParticipant().isDistributedBridge());

    conferenceEventNotification(event);
    }

    /*
     * Transfer an incoming call to the target conference.
     */
    public void transferMember(ConferenceManager newConferenceManager,  ConferenceMember member) throws IOException
    {
        leave(member, true);		   // leave the temporary conference
        member.reinitialize(newConferenceManager, true);
        newConferenceManager.joinConference(member); // join the new conference
    }

    /**
     * Remove a member from a conference
     * @param member ConferenceMember leaving the conference
     */
    public void leave(ConferenceMember member) {
    leave(member, false);
    }

    /*
     * keepMember is set to true when a member transfers
     * from one conference to another.
     * This is used for incoming calls which require a temporary conference
     * until the caller specified the desired conference to enter.
     */
    public void leave(ConferenceMember member, boolean keepMember) {
    conferenceReceiver.removeMember(member);

    synchronized (this) {
        synchronized (memberList) {
            memberList.remove(member);
            totalMembers--;

            if (keepMember == false) {
                    member.end();
            }

            Logger.println("conferenceManager:  '" + conferenceId
            + "':  member " + member.toString()
            + " leaving, remaining:  " + memberList.size());
        }

        if (member.joinedDistributedConference())
        {
            ConferenceEvent event = new ConferenceEvent(ConferenceEvent.MEMBER_LEFT, conferenceId);

            event.setCallId(member.getCallParticipant().getCallId());
            event.setMemberAddress(member.getMemberSender().getSendAddress());
            event.setIsDistributedBridge(member.getCallParticipant().isDistributedBridge());
            ConferenceManager.conferenceEventNotification(event);
        }

        ConferenceEvent event = new ConferenceEvent(ConferenceEvent.MEMBER_LEFT, conferenceId);
        event.setCallId(member.getCallParticipant().getCallId());
        event.setMemberAddress(member.getMemberSender().getSendAddress());
        event.setMemberCount(memberList.size());
        ConferenceManager.conferenceEventNotification(event);

        boolean endOfDistributedConference = true;

        synchronized (memberList) {
        for (int i = 0; i < memberList.size(); i++) {
            ConferenceMember m = (ConferenceMember) memberList.get(i);

            if (m.getCallParticipant().isDistributedBridge() == false) {
            endOfDistributedConference = false;
            break;
            }
        }
        }

        if (endOfDistributedConference) {
        endAllCalls();
        }

        if (memberList.size() == 0) {
            end();	// last member left, the conference is over
        }
    }
    }

    private void endAllCalls() {
    synchronized (memberList) {
        for (int i = 0; i < memberList.size(); i++) {
                 ConferenceMember member = (ConferenceMember)
                     memberList.get(i);

         member.cancelRequest("End of Distributed Conference");
        }
    }
    }

    /**
     * Get the ArrayList identifying all conferences.
     * @return ArrayList list of Conferences
     */
    public static ArrayList getConferenceList() {
    return conferenceList;
    }

    public static void useSingleSender(boolean useSingleSender) {
    if (ConferenceManager.useSingleSender == useSingleSender) {
        return;
    }

    if (useSingleSender == true) {
        ConferenceManager.useSingleSender = true;

        loneConferenceSender = new ConferenceSender(conferenceList);

            for (int i = 0; i < conferenceList.size(); i++) {
                ConferenceManager conferenceManager =
            (ConferenceManager) conferenceList.get(i);

        synchronized (conferenceManager) {
            conferenceManager.endConferenceSender();

            conferenceManager.setNewConferenceSender(
            loneConferenceSender);
        }
        }
    } else {
        synchronized (conferenceList) {
        ConferenceManager.useSingleSender = false;

        if (loneConferenceSender != null) {
                loneConferenceSender.end();
            loneConferenceSender = null;
        }

                for (int i = 0; i < conferenceList.size(); i++) {
                    ConferenceManager conferenceManager =
                        (ConferenceManager) conferenceList.get(i);

            conferenceManager.setNewConferenceSender();
                }
        }
    }
    }

    public static void setLoneReceiverPort(int loneReceiverPort)
        throws ParseException {

    if (ConferenceManager.loneReceiverPort == loneReceiverPort) {
        return;
    }

    if (totalMembers != 0) {
        Logger.println(
        "Can't change loneReceiverPort while conferences are in progress");
        throw new ParseException(
        "Can't change loneReceiverPort while conferences are in progress", 0);
    }

    ConferenceManager.loneReceiverPort = loneReceiverPort;
    }

    private void setNewConferenceSender(ConferenceSender conferenceSender) {
        synchronized (this) {
            this.conferenceSender = conferenceSender;
    }
    }

    private void setNewConferenceSender() {
        synchronized (this) {
            conferenceSender = new ConferenceSender(this);
    }
    }

    public static boolean useSingleSender() {
    return useSingleSender;
    }

    public static int loneReceiverPort() {
    return loneReceiverPort;
    }

    /**
     * Create a new conference with the specified media settings
     */
    public static void createConference(String conferenceId,
        String mediaPreference, String displayName) throws ParseException {

    synchronized(conferenceList) {
        ConferenceManager conferenceManager = getConference(conferenceId, mediaPreference, displayName, true);

        if (conferenceManager.getMemberList().size() == 0) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Conference " + conferenceId
                + " setting media preference to "
                + mediaPreference);
        }

        conferenceManager.setPermanent(true);

        try {
            conferenceManager.setMediaInfo(mediaPreference);
        } catch (ParseException e) {
            try {
                removeConference(conferenceId);
            } catch (ParseException ee) {
            }

            Logger.println(conferenceId
            + ":  Can't change meeting media setting to "
            + mediaPreference + ": " + e.getMessage());
            throw new ParseException(conferenceId
            + ":  Can't change meeting media setting to "
            + mediaPreference + ": " + e.getMessage(), 0);
        }
        } else {
        /*
         * Defer until conference ends
         */
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Conference " + conferenceId
                + " defer setting media preference to "
                + mediaPreference);
        }

        conferenceManager.setMediaPreference(mediaPreference);
        }
    }
    }

    public static void removeConference(String conferenceId)
        throws ParseException {

    synchronized(conferenceList) {
            ConferenceManager conferenceManager =
        findConferenceManager(conferenceId);

        if (conferenceManager.getMemberList().size() > 0) {
        throw new ParseException("can't remove conference:  '"
                    + conferenceId
            + "' because there are still calls in progress", 0);
        }

        conferenceManager.setPermanent(false);
        conferenceManager.end();
    }
    }

    /*
     * End a conference
     */
    public static void endConference(String conferenceId)
        throws ParseException {

    ConferenceManager conferenceManager;

    synchronized (conferenceList) {
            conferenceManager = findConferenceManager(conferenceId);

        ArrayList memberList = conferenceManager.getMemberList();

        synchronized (memberList) {
        for (int i = 0; i < memberList.size(); i++) {
                    ConferenceMember member = (ConferenceMember) memberList.get(i);

            CallHandler callHandler = member.getCallHandler();
            callHandler.cancelRequest("Conference forced to end");
        }

            conferenceManager.end();
        }
    }
    }

    private static boolean allowShortNames = true;

    public static void setAllowShortNames(boolean allowShortNames) {
    ConferenceManager.allowShortNames = allowShortNames;
    }

    public static boolean allowShortNames() {
    return allowShortNames;
    }

    public static ConferenceManager findConferenceManager(
        String conferenceId) throws ParseException {

    for (int i = 0; i < conferenceList.size(); i++) {
        ConferenceManager conferenceManager = (ConferenceManager)
        conferenceList.get(i);

        if (conferenceManager.getId().equals(conferenceId)) {
        return conferenceManager;
        }

        if (allowShortNames == true) {
            String displayName = conferenceManager.getDisplayName();

            if (displayName != null && displayName.equals(conferenceId)) {
            return conferenceManager;
        }
        }
    }

        throw new ParseException("Non-existent conference "
            + conferenceId, 0);
    }

    /**
     * Start a new conference or return existing Conference with the
     * specified id
     * @param conferenceId String identifier of conference
     * @return ConferenceManager
     */
    public static ConferenceManager getConference(String conferenceId) {
    return getConference(conferenceId, null, null, false);
    }

    public static ConferenceManager getConference(CallParticipant cp) {

    return getConference(cp.getConferenceId(), cp.getMediaPreference(),
        cp.getConferenceDisplayName(), false);
    }

    public static ConferenceManager getConference(String conferenceId,
        String mediaPreference, String displayName) {

    return getConference(conferenceId, mediaPreference, displayName,
        false);
    }

    public static ConferenceManager getConference(String conferenceId,
        String mediaPreference, String displayName, boolean permanent) {

    ConferenceManager conferenceManager;

    try {
        conferenceManager = findConferenceManager(conferenceId);

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("found existing conference:  '"
            + conferenceId + "'");
        }
        return conferenceManager;
    } catch (ParseException e) {
    }

    try {
        conferenceManager =
        new ConferenceManager(conferenceId, mediaPreference,
            displayName);
    } catch (SocketException e) {
        Logger.error("Can't create conference " + conferenceId
            + " " + e.getMessage());

        return null;
    }

    synchronized(conferenceList) {
        conferenceList.add(conferenceManager);
    }

    Logger.println("starting new conference:  '"
        + conferenceId + "'.  "
        + " conferences in progress:  " + conferenceList.size());

    conferenceManager.setPermanent(permanent);

    String id = conferenceManager.getId();

    if (displayName != null) {
        id += ":" + mediaPreference + ":" + displayName;
    }

    ConferenceManager.conferenceEventNotification(new ConferenceEvent(ConferenceEvent.CONFERENCE_STARTED, id));

    return conferenceManager;
    }

    public MediaInfo getMediaInfo() {
    return mediaInfo;
    }

    public static void dropDb() {
    if (distributedBridge != null) {
        distributedBridge.dropDb();
    } else {
        Logger.println(
        "The Distributed Conference Manager is not installed");
    }
    }

    public static String getDistributedConferenceInfo() {
    if (distributedBridge != null) {
        return distributedBridge.getDistributedConferenceInfo();
    }

        Logger.println("The Distributed Bridge Manager is not installed");
    return "The Distributed Conference Manager is not installed";
    }

    /**
     * Display information about each conference
     * @param requestHandler RequestHandler with socket to write output to
     */
    public static String getBriefConferenceInfo() {
    return getConferenceInfo(0);
    }

    public static String getAbbreviatedConferenceInfo() {
    return getConferenceInfo(1);
    }

    public static String getDetailedConferenceInfo() {
    return getConferenceInfo(2);
    }

    private static String getConferenceInfo(int format) {
    synchronized(conferenceList) {
        if (conferenceList.size() == 0) {
        if (format != 0) {
                return ("\n\n\n\n");
        }

        return "";
        }

        String s = "";

        for (int i = 0; i < conferenceList.size(); i++) {
        ConferenceManager conferenceManager = (ConferenceManager)
            conferenceList.get(i);

        String id = conferenceManager.getId();

        String displayName = conferenceManager.getDisplayName();

        if (format != 1) {
            if (displayName != null) {
            id += " '" + displayName + "'";
            }
        } else {
            if (displayName == null) {
            if (id.length() >= 14) {
                    id = id.substring(0, 13);
            }
            } else {
                id = "'" + displayName + "'";
            }
        }

        s += "Conference Id: " + id + " ";

        s += conferenceManager.getMediaInfo().toString();

        s += " Members=" + conferenceManager.getMemberList().size();

        if (conferenceManager.isPermanent()) {
            s += " persistent";
        }

        String recordingFile =
            conferenceManager.getWGManager().getRecordingFile();

        if (recordingFile != null) {
            s += " Recording to " + recordingFile;
        }

        s += "\n";

        if (format == 0) {
            continue;
        }

        /*
         * Copy the member list so we can avoid unnecessary
         * synchronization
         */
        ArrayList memberList = (ArrayList)
            conferenceManager.getMemberList().clone();

        for (int n = 0; n < memberList.size(); n++) {
                    ConferenceMember member = (ConferenceMember)
            memberList.get(n);

            MemberSender memberSender = member.getMemberSender();
            MemberReceiver memberReceiver = member.getMemberReceiver();

            String info = " ";

            MediaInfo transmitMediaInfo = memberSender.getMediaInfo();

            if (transmitMediaInfo != null) {
                info += transmitMediaInfo.toString();
            }

            MediaInfo receiveMediaInfo = memberReceiver.getMediaInfo();

            if (receiveMediaInfo != null) {
            if (transmitMediaInfo.getEncoding() !=
                    receiveMediaInfo.getEncoding() ||
                    transmitMediaInfo.getSampleRate() !=
                    receiveMediaInfo.getSampleRate() ||
                    transmitMediaInfo.getChannels() !=
                    receiveMediaInfo.getChannels()) {

                /*
                 * The member is transmitting at a different
                 * media setting than it is receiving.
                 */
                info += " Transmit:"
                + memberReceiver.getMediaInfo();
            }
            }

            CallParticipant cp = member.getCallParticipant();

            if (cp.isMuted()) {
                info += " MUTED";
            }

            if (cp.isConferenceMuted()) {
                info += " CONFERENCE_MUTED";
            }

            if (cp.isConferenceSilenced()) {
            info += " MAIN_CONFERENCE_SILENCED";
            }

            if (memberReceiver.doNotRecord() == true) {
                info += " RECORDED NOT ALLOWED";
            }

                    if (memberReceiver.getFromRecordingFile() != null) {
                    info += " Recording from member in "
                    + memberReceiver.getFromRecordingFile();
                }

            if (cp.isRecorder()) {
                String toRecordingFile =
                memberSender.getCallParticipant().getToRecordingFile();

                        if (toRecordingFile != null) {
                        info += " Recording to member in " + toRecordingFile;
                    } else {
                info += " Recorder";
            }
            }

                if (cp.speexEncode()) {
                    info += " SpeexEncode";
                }

                //
                // For debugging
                //
            if (memberSender.getSendAddress() != null) {
            String gateway = "";

            String address =
                memberSender.getSendAddress().toString();

            int ix = address.indexOf("/");

            if (ix >= 0) {
                address = address.substring(ix + 1);

                if ((ix = address.indexOf(":")) >= 0) {
                address = address.substring(0, ix);
                }
            }

            if (address.equals("10.6.4.192")) {
                gateway = " Menlo Park Gateway";
            } else if (address.equals("129.148.75.22")) {
                gateway = " Burlington Gateway";
            } else if (address.equals("10.1.224.22")) {
                gateway = " Broomfield Gateway";
            }

            info += gateway;
            }

            if (format == 2) {
                info += conferenceManager.getWGManager().getWhisperGroupInfo(member);
            }

            cp = member.getCallParticipant();

            id = cp.toString();

            if (format == 1) {
            id = cp.toConsiseString();
            }

            s += "    " + id + info + "\n";
        }

        s += "\n";
            }

        return s + "\n";
    }
    }

    /**
     * Record a specified conference
     */
    public static void recordConference(String conferenceId, boolean enabled,
        String recordingFile, String recordingType) throws ParseException {

        synchronized(conferenceList) {
        ConferenceManager conferenceManager;

        if ((conferenceManager = findConferenceManager(conferenceId)) !=
            null) {

        conferenceManager.recordConference(enabled, recordingFile,
            recordingType);

        return;
        }
        throw new ParseException("No such conference " + conferenceId, 0);
    }
    }

    public void recordConference(boolean enabled, String recordingFile,
        String recordingType) throws ParseException {

    if (wgManager == null) {
        return;
    }

    try {
        wgManager.recordConference(enabled, recordingFile, recordingType);
    } catch (IOException e) {
        throw new ParseException(e.getMessage(), 0);
    }
    }

    public static void setConferenceJoinTreatment(String conferenceId,
            String treatment) throws ParseException {

        synchronized(conferenceList) {
        ConferenceManager conferenceManager;

        conferenceManager = findConferenceManager(conferenceId);

        conferenceManager.setConferenceJoinTreatment(treatment);
        }
    }

    private String conferenceJoinTreatment;

    public void setConferenceJoinTreatment(String treatment) {
        conferenceJoinTreatment = treatment;
    }

    private String conferenceLeaveTreatment;

    public static void setConferenceLeaveTreatment(String conferenceId,
            String treatment) throws ParseException {

        synchronized(conferenceList) {
        ConferenceManager conferenceManager;

        conferenceManager = findConferenceManager(conferenceId);

        conferenceManager.setConferenceLeaveTreatment(treatment);
        }
    }

    public void setConferenceLeaveTreatment(String treatment) {
        conferenceLeaveTreatment = treatment;
    }

    private String conferenceAnswerTreatment;

    public static void setConferenceAnswerTreatment(String conferenceId,
            String treatment) throws ParseException {

        synchronized(conferenceList) {
            ConferenceManager conferenceManager;

            conferenceManager = findConferenceManager(conferenceId);

            conferenceManager.setConferenceAnswerTreatment(treatment);
        }
    }

    public void setConferenceAnswerTreatment(String treatment) {
    if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println("Setting conference answer treatment to " + treatment);
    }
        conferenceAnswerTreatment = treatment;
    }

    public String getConferenceAnswerTreatment() {
    return conferenceAnswerTreatment;
    }

    public static void playTreatmentToAllConferences(String treatment,
        double[] volume) throws ParseException {
    }

    public static void playTreatmentToAllConferences(String treatment)
        throws ParseException {

        synchronized(conferenceList) {
            for (int i = 0; i < conferenceList.size(); i++) {
                ConferenceManager conferenceManager =
                    (ConferenceManager) conferenceList.get(i);

        playTreatment(conferenceManager.getId(), treatment);
        }
    }
    }

    public static void playTreatment(String conferenceId,
        String treatment, double[] volume) throws ParseException {
    }

    /**
     * Play a treatment to the specified conference
     */
    public static void playTreatment(String conferenceId,
        String treatment) throws ParseException {

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("playing treatment " + treatment + " to "
        + conferenceId);
    }

        synchronized(conferenceList) {
        ConferenceManager conferenceManager;

        conferenceManager = findConferenceManager(conferenceId);

        try {
        conferenceManager.addTreatment(treatment);
        } catch (IOException e) {
        throw new ParseException("bad treatment "
            + " " + e.getMessage(), 0);
        }
        return;
        }
    }

    public void addTreatment(String treatment) throws IOException {
    if (!done) {
        synchronized (this) {
        if (wgManager.hasCommonMix()) {
                wgManager.addConferenceTreatment(new TreatmentManager(
            treatment, 0, mediaInfo.getSampleRate(),
            mediaInfo.getChannels()));
        } else {
                    synchronized (memberList) {
                        for (int i = 0; i < memberList.size(); i++) {
                            ConferenceMember member = (ConferenceMember)
                                memberList.get(i);

                member.addTreatment(new TreatmentManager(
                    treatment, 0, mediaInfo.getSampleRate(),
                    mediaInfo.getChannels()));
            }
            }
        }
        }
    }
    }

    public static void pauseTreatment(String conferenceId, String treatment,
        boolean isPaused) throws ParseException {

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("pausing treatment " + treatment + " to "
                + conferenceId);
        }

        synchronized(conferenceList) {
            ConferenceManager conferenceManager;

            conferenceManager = findConferenceManager(conferenceId);

            conferenceManager.getWGManager().pauseConferenceTreatment(
                treatment, isPaused);
        }
    }

    public static void stopTreatment(String conferenceId, String treatment)
        throws ParseException {

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("stopping treatment " + treatment + " to "
        + conferenceId);
    }

        synchronized(conferenceList) {
            ConferenceManager conferenceManager;

            conferenceManager = findConferenceManager(conferenceId);

            conferenceManager.getWGManager().removeConferenceTreatment(
        treatment);
        }
    }

    /**
     * get conference statistics
     */
    public static void printStatistics() {
        synchronized(conferenceList) {
        if (loneConferenceSender != null) {
        loneConferenceSender.printStatistics();
        }

            for (int i = 0; i < conferenceList.size(); i++) {
                ConferenceManager conferenceManager =
            (ConferenceManager) conferenceList.get(i);

        ConferenceSender conferenceSender =
                    conferenceManager.getConferenceSender();

        if (loneConferenceSender == null) {
            conferenceSender.printStatistics();
        }

                ArrayList memberList = conferenceManager.getMemberList();

                for (int n = 0; n < memberList.size(); n++) {
                    ConferenceMember member = (ConferenceMember)
                        memberList.get(n);

                    member.printStatistics();
                }
            }
        }
        Logger.flush();
    }

}
