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
import com.sun.voip.CallEvent;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.MixDataSource;
import com.sun.voip.RtcpReceiver;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSocket;
import com.sun.voip.SdpManager;
import com.sun.voip.TreatmentDoneListener;
import com.sun.voip.TreatmentManager;
import com.sun.voip.Util;

import java.io.IOException;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import java.nio.channels.DatagramChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import java.text.ParseException;

/**
 * Receive RTP data for this ConferenceMember, add it to the mix
 * and keep statistics.
 */
public class ConferenceMember implements TreatmentDoneListener,
    MixDataSource, JoinConfirmationListener {

    private ConferenceManager conferenceManager;
    private CallParticipant cp;

    private WGManager wgManager;
    private CallHandler callHandler;
    private MemberSender memberSender;
    private MemberReceiver memberReceiver;

    private MediaInfo myMediaInfo;

    private MixManager mixManager;

    private ArrayList whisperGroups;

    private boolean done = false;

    private String timeStarted;

    private static Integer statisticsLock = new Integer(0);

    private boolean initializationDone = false;

    private boolean migrating;

    private WhisperGroup initialWhisperGroup;
    private WhisperGroup conferenceWhisperGroup;
    private WhisperGroup whisperGroup;

    private boolean traceCall;

    private boolean joinedDistributedConference;

    private static int firstRtpPort;
    private static int lastRtpPort;

    private DatagramChannel datagramChannel;
    private RtcpReceiver rtcpReceiver;
    private static RtcpReceiver loneRtcpReceiver;

    InetSocketAddress rtcpAddress;

    private static long startTime;
    private static int applyCount;
    private static int pmCount;
    private static int replaced;
    private static long applyTime;

    static class CallbackListener implements SenderCallbackListener {

        public void senderCallback() {
        long start = System.nanoTime();

        if (startTime == 0) {
        startTime = start;
        }

        int pmCount = ConferenceMember.pmCount;

            applyPrivateMixes();

        if (pmCount == ConferenceMember.pmCount) {
        return;
        }

        long now = System.nanoTime();

        applyTime += (now - start);

        double secondsToApply = applyTime / 1000000000.;

        if (++applyCount == 500 && Logger.logLevel >= Logger.LOG_INFO) {
        double elapsed = (now - startTime) / 1000000000.;

        Logger.println("elapsed " + elapsed + " seconds, applied "
            + ConferenceMember.pmCount
            + " pm's in " + secondsToApply + " seconds, avg per pm "
            + (secondsToApply / pmCount)
            + ", avg time to apply pm's "
            + (secondsToApply / applyCount)
            + " seconds, replaced " + replaced);

        applyCount = 0;
        ConferenceMember.pmCount = 0;
        applyTime = 0;
        replaced = 0;
        }
        }

    }

    static {
        ConferenceSender.addSenderCallbackListener(new CallbackListener());

        String s = System.getProperty("com.sun.voip.server.FIRST_RTP_PORT");

        if (s != null && s.length() > 0) {
            try {
                firstRtpPort = Integer.parseInt(s);

                if ((firstRtpPort & 1) != 0) {
                    firstRtpPort++;
                }

        if (firstRtpPort != 0) {
                    Logger.println("First RTP Port is " + firstRtpPort);
        }
            } catch (NumberFormatException e) {
                Logger.println(
                    "Invalid first RTP port, using next available: " + s);
            }
        }

        s = System.getProperty("com.sun.voip.server.LAST_RTP_PORT");

        if (firstRtpPort > 0 && s != null && s.length() > 0) {
            try {
                lastRtpPort = Integer.parseInt(s);

        if (lastRtpPort <= firstRtpPort + 1) {
            Logger.println("Last RTP port is less than first,"
            + " no limit set.");

            lastRtpPort = 0;
        }
            } catch (NumberFormatException e) {
                Logger.println(
                    "Invalid last RTP port, no limit set: " + s);
            }
    }
    }

    public ConferenceMember(ConferenceManager conferenceManager,
        CallParticipant cp) throws IOException {

    this.conferenceManager = conferenceManager;
    this.cp = cp;

    initializeChannel();

    memberSender = new MemberSender(cp, datagramChannel);

    memberReceiver = new MemberReceiver(this, cp, datagramChannel);

    memberReceiver.addJoinConfirmationListener(this);

    wgManager = conferenceManager.getWGManager();

    whisperGroups = wgManager.getWhisperGroups();

        mixManager = new MixManager(this,
        conferenceManager.getMediaInfo().getSamplesPerPacket(),
        conferenceManager.getMediaInfo().getChannels());

    timeStarted = Logger.getDate();

    addMemberDoneListener(this);
    }

    private void initializeChannel() throws IOException {
    datagramChannel = conferenceManager.getConferenceReceiver().getChannel(cp);

    if (datagramChannel != null) {
        synchronized (datagramChannel) {
            if (loneRtcpReceiver == null) {
            int rtcpPort = datagramChannel.socket().getLocalPort() + 1;

            Logger.println("Starting lone RtcpReceiver on port "
            + rtcpPort);

                    loneRtcpReceiver = new RtcpReceiver(
                new DatagramSocket(rtcpPort), true);
        }
            rtcpReceiver = loneRtcpReceiver;
        }
        return;
    }

    /*
     * We are trying to find a pair of sockets with consecutive port nums.
     * The first socket must have an even port.
     *
     * If we find a socket that we don't like, we have to keep it open
     * otherwise when we try to find another socket, we may get the same
     * one as before.
     *
     * So we make a list of the bad sockets and close them all
     * after we're done.
     */
    ArrayList badChannels = new ArrayList();

    int nextRtpPort = firstRtpPort;

    try {
        while (true) {
            datagramChannel = DatagramChannel.open();

        if (Logger.logLevel >= Logger.LOG_DETAIL) {
            Logger.println("Call " + cp
                + " Opened datagram channel " + datagramChannel);
        }

            datagramChannel.configureBlocking(false);

            DatagramSocket socket = datagramChannel.socket();

        socket.setReceiveBufferSize(RtpSocket.MAX_RECEIVE_BUFFER);

        socket.setSoTimeout(0);

        InetSocketAddress bridgeAddress = Bridge.getLocalBridgeAddress();

            InetSocketAddress isa =
            new InetSocketAddress(bridgeAddress.getAddress(), nextRtpPort);

        if (nextRtpPort > 0) {
            nextRtpPort += 2;

            if (lastRtpPort != 0 && (nextRtpPort + 1) > lastRtpPort) {
            Logger.println("No more RTP ports available, last is "
                + lastRtpPort);

            closeBadChannels(badChannels);

            throw new IOException(
                "No more RTP ports available, last is " + lastRtpPort);
            }
        }

        try {
                socket.bind(isa);

            int localPort = socket.getLocalPort();

            if ((localPort & 1) != 0) {
            /*
             * Port is odd, can't use this datagramSocket
             */
            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Call " + cp
                    + " skipping DatagramSocket with odd port "
                    + localPort);
            }

            badChannels.add(datagramChannel);
                continue;
            }

            Logger.writeFile("Call " + cp + " RTCP Port "
            + (localPort + 1));

                rtcpReceiver = new RtcpReceiver(
            new DatagramSocket(localPort + 1), false);
            break;
        } catch (SocketException e) {
            /*
             * Couldn't bind, can't use this DatagramSocket.
             */
            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Call " + cp
                + " skipping DatagramSocket " + e.getMessage());
            }
            badChannels.add(datagramChannel);
            continue;
        }
        }
    } catch (Exception e) {
        closeBadChannels(badChannels);

        throw new IOException("Call " + cp
        + " MemberReceiver exception! " + e.getMessage());
    }

    closeBadChannels(badChannels);

    if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp + " port "
                + datagramChannel.socket().getLocalPort());
    }
    }

    private void closeBadChannels(ArrayList badChannels) {
    while (badChannels.size() > 0) {
        /*
         * Now close all the channels we couldn't use
         */
        DatagramChannel dc = (DatagramChannel) badChannels.remove(0);

        try {
            dc.close();

        if (Logger.logLevel >= Logger.LOG_DETAIL) {
            Logger.println("Closed datagram channel " + dc);
        }
        } catch (IOException e) {
        Logger.println("Unable to close channel! " + e.getMessage());
        }
    }
    }

    public static void setFirstRtpPort(int firstRtpPort) {
    ConferenceMember.firstRtpPort = firstRtpPort;
    }

    public static int getFirstRtpPort() {
    return firstRtpPort;
    }

    public static void setLastRtpPort(int firstRtpPort) {
    ConferenceMember.lastRtpPort = lastRtpPort;
    }

    public static int getLastRtpPort() {
    return lastRtpPort;
    }

    public String getTimeStarted() {
    return timeStarted;
    }

    /*
     * For debugging.
     */
    public void traceCall(boolean traceCall) {
    this.traceCall = traceCall;
    memberSender.traceCall(traceCall);
    memberReceiver.traceCall(traceCall);
    }

    public boolean traceCall() {
    return traceCall;
    }

    public String getMemberState() {
    if (initializationDone == false) {
        return "\tNot Initialized\n";
    }

    String s = "";

    s += memberReceiver.getMemberState();

    s += memberSender.getMemberState();

    s += "\tMediaInfo " + memberReceiver.getMediaInfo() + "\n";

    s += wgManager.getWhisperGroups(this);

    synchronized (conferenceManager) {
        synchronized (privateMixesForMe) {
        if (privateMixesForMe.size() > 0) {
                s += "\tOthers with Private mixes\n";

                for (int i = 0; i < privateMixesForMe.size(); i++) {
                ConferenceMember member = (ConferenceMember)
                    privateMixesForMe.get(i);

                s += "\t    " + member + "\n";
                }
        }
        }

        synchronized (mixManager) {
            ArrayList mixDescriptors = mixManager.getMixDescriptors();

            s += "\tMixDescriptors " + mixDescriptors.size() + "\n";

                for (int i = 0; i < mixDescriptors.size(); i++) {
                    MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

            s += "\t    " + md.toAbbreviatedString();

                    if (whisperGroup == md.getMixDataSource()) {
                        s += " + ";
            }

            s += "\n";
        }
        }
    }

    return s;
    }

    /**
     * Initialize this member.  The call has been established and
     * we now know the port at which the member (CallParticipant)
     * listens for data.
     */
    public void initialize(CallHandler callHandler,    InetSocketAddress memberAddress, byte mediaPayload,
        byte receivePayload, byte telephoneEventPayload,    InetSocketAddress rtcpAddress)
    {

        this.callHandler = callHandler;

        if (cp.getProtocol() != null && "WebRtc".equals(cp.getProtocol()) == false && "Rtmfp".equals(cp.getProtocol()) == false && "Speaker".equals(cp.getProtocol()) == false)
        {
            if (rtcpAddress != null) {
                this.rtcpAddress = rtcpAddress;
            } else {
                rtcpAddress = new InetSocketAddress(memberAddress.getAddress(),
                memberAddress.getPort());
            }

            Logger.writeFile("Call " + cp  + " Initializing sender with member address " + memberAddress);

            memberSender.initialize(conferenceManager, callHandler, memberAddress,  mediaPayload, telephoneEventPayload);
            memberReceiver.initialize(conferenceManager, callHandler,  receivePayload, telephoneEventPayload, rtcpReceiver);

            if (mediaPayload != receivePayload) {
                Logger.println("Call " + cp
                    + " send payload " + mediaPayload
                        + " receive payload " + receivePayload);
            }

            try {
                myMediaInfo = SdpManager.findMediaInfo(receivePayload);

                if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("Call " + cp + " media info " + myMediaInfo
                    + " telephoneEventPayload " + telephoneEventPayload);
                }
            } catch (ParseException e) {
                Logger.println("Call " + cp + " Invalid receivePayload "
                + receivePayload);

                callHandler.cancelRequest("Invalid receive payload "
                + receivePayload);
                return;
            }

        } else {

            Logger.writeFile("Call " + cp  + " Initializing " + cp.getPhoneNumber());

            memberSender.initialize(conferenceManager, callHandler, memberAddress,  mediaPayload, telephoneEventPayload);
            memberReceiver.initialize(conferenceManager, callHandler,  receivePayload, telephoneEventPayload, rtcpReceiver);
        }


        MixManager oldMixManager = mixManager;

            mixManager = new MixManager(this,
            conferenceManager.getMediaInfo().getSamplesPerPacket(),
            conferenceManager.getMediaInfo().getChannels());

        synchronized (mixManager) {
                mixManager.addMix(memberReceiver, -1.0D); // subtract self (mix-minus)

            /*
             * Restore private mixes which were set before we were initialized
             */
            synchronized (oldMixManager) {
                    ArrayList mixDescriptors = oldMixManager.getMixDescriptors();

                    for (int i = 0; i < mixDescriptors.size(); i++) {
                        MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

                        if (md.isPrivateMix()) {
                    Logger.println("Call " + cp
                    + " restoring private mix: " + md);

                            mixManager.addMix(md);
                }
                    }
                }
        }

        conferenceWhisperGroup = wgManager.getConferenceWhisperGroup();

        if (initializationDone) {
                addCall(conferenceWhisperGroup);
                setWhispering(conferenceWhisperGroup);

            Logger.println("Call " + cp
            + " Whispering in conference whisper group");
            return;
        }

        synchronized (conferenceManager) {
            String whisperGroupId = null;

            if ((whisperGroupId = cp.getWhisperGroupId()) != null) {
            try {
                addCall(whisperGroupId);
                setWhispering(whisperGroupId);
                    } catch (ParseException e) {
                callHandler.cancelRequest("Invalid whisper group "
                    + whisperGroupId + " " + e.getMessage());
                        return;
            }
            }
        }

        if (cp.getJoinConfirmationTimeout() == 0) {
            addCall(conferenceWhisperGroup);

        if (whisperGroup == null) {
                setWhispering(conferenceWhisperGroup);
        }
        }

        if (whisperGroup == null) {
            try {
                addCall("initial-" + cp);
                setWhispering("initial-" + cp);
            initialWhisperGroup =
                wgManager.findWhisperGroup("initial-" + cp);
            } catch (ParseException e) {
                    callHandler.cancelRequest("Call " + cp
                + " Can't add call to initial whisper group "
                + e.getMessage());
                    return;
            }
        }

        initializationDone = true;

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp
                + " ConferenceMember initialization done...");
        }

        conferenceManager.joinDistributedConference(this);
        joinedDistributedConference = false;
    }

    public void reinitialize(ConferenceManager conferenceManager) {

        reinitialize(conferenceManager, true);
    }

    public void reinitialize(ConferenceManager conferenceManager, boolean initialize) {
    synchronized (conferenceManager) {
        Logger.println("Call " + this + " Reinitializing");

        synchronized (this.conferenceManager) {
                /*
                 * Remove member from whatever whisper groups it's in.
                 */
                synchronized (whisperGroups) {
                    for (int i = 0; i < whisperGroups.size(); i++) {
                        WhisperGroup whisperGroup = (WhisperGroup)
                whisperGroups.get(i);

                        removeCall(whisperGroup.getId());
                    }
                }

                this.conferenceManager = conferenceManager;

                wgManager = conferenceManager.getWGManager();

                whisperGroups = wgManager.getWhisperGroups();

            /*
             * We have to reinitialize here if the conference parameters
             * changed.  For example, an incoming call usually comes in
             * over the phone lines at PCMU/8000/1.  The conference used
             * for the incoming call handler is also PCMU/8000/1.
                 *
             * When the call is transferred to the actual conference,
             * the conference parameters may be different.
             */

            if (initialize)
            {
                initialize(callHandler, memberSender.getSendAddress(), memberSender.getMediaInfo().getPayload(), memberReceiver.getMediaInfo().getPayload(), (byte) memberReceiver.getTelephoneEventPayload(), rtcpAddress);
            }
        }
    }
    conferenceManager.joinDistributedConference(this);
    joinedDistributedConference = true;
    }

    public boolean joinedDistributedConference() {
    return joinedDistributedConference;
    }

    public void cancelRequest(String s) {
        if (callHandler != null) {
            callHandler.cancelRequest(s);
        }
    }

    public void joinConfirmation() {
    addCall(conferenceWhisperGroup);
    setWhispering(conferenceWhisperGroup);
    }

    public MemberSender getMemberSender() {
    return memberSender;
    }

    public MemberReceiver getMemberReceiver() {
    return memberReceiver;
    }

    public InetSocketAddress getRtcpAddress() {
    return rtcpAddress;
    }

    /*
     * Maintain list of MixDescriptors.
     *
     * Notes:
     *
     * - A member m is always whispering in one and only one whisper group.
     * - A descriptor is attenatuated based on where m is whispering
     * - m has full volume for members in the whisper group in
     *   which m is whispering.
     * - if m is whispering in wg and wg has attenuation 0, then
     *   all other wg's are attenuated to 0 for m.
     * - otherwise m has wg attenution for the wg's to which m belongs
     *   but is not whispering in.
     * - if m has a private mix pm for m1
     *       - if m doesn't belong to wg in which m1 is whispering,
     *            the effective volume ev is 0.
     *       - if m & m1 are talking in the same wg, ev is pm volume minus 1
     *		  It's minus 1 because m1 already is mixed in the wg with
     *		  volume 1.
     *	     - otherwise, ev is pm volume * wg attenuation
     * - pm descriptors for member m and all other members with pm's for m
     *   must be adjusted when a member m starts or stops talking or
     *   when AWAY/RETURN changes.
     *
     * Events:
     *
     * Member m has been added to whisper group wg.
     *         A MixDescriptor for wg is added for the member.
     *	       If whisper group member is whispering in has 0 attenuation,
     *	       md attenuation is 0.  Otherwise md attenuation is wg attenuation.
     *
     *	       No other descriptors need to be adjusted.
     *	       Private mixes don't have to be adjusted because m has not
     *	       started talking in a different whisper group.
     *
     * Member m has been removed from wg.
     *          The MixDescriptor for wg is removed for the member.
     *
     *	        No other descriptors need to be adjusted since m had to
     *		stop talking before being removed.
     *
     * Member m started talking in wg.
     *          A MixDescriptor for wg with 1.0 attenuation is added.
     *
     *		If wg has 0 attenuation, all other md's have 0 attenuation.
     *
     *		Otherwise, other md's are attenuated to their wg's attenuation.
     *
     * 		If m has private mixes for other members,
     *		those pm's must be attenuated properly based on
     *		where m and the other members are talking.
     *
     *		If other members have pm' for m, then
     *		those pm's needot be adjusted based on where m
     *		and the other members are talking.
     *
     * Member m stopped talking in wg.
     *		MixDescriptor for wg is attenuated to wg attenuation.
     *
     *		Nothing more to do until member starts talking in new wg.
     *
     * Member m is AWAY.  All md's for m are muted.
     *		 All pm's which other members have for m are muted.
     *		 XXX May not need to do this since m is muted and
     *		 will not be generating any voice data.
     *
     * Member is back from AWAY.  All md's for m are unmuted.
     *		 All pm's which other members have for m are unmuted.
     *		 XXX May not need to do this since m is muted and
     *		 will not be generating any voice data.
     *
     * Member m has a private mix for m1.
     *		 A MixDescriptor for m is added describing the desired
     *		 volume for m1.  If m is not a member of the whisper group
     * 		 in which m1 is whispering, ev is 0.
     *           If m is not talking in wg, ev must be adjusted by
     *	         wg attentuation.
     */
    private void adjustPrivateMixDescriptors() {
    synchronized (conferenceManager) {
        /*
         * Adjust private mixes this member has for other members
         */
        synchronized (mixManager) {
            ArrayList mixDescriptors = mixManager.getMixDescriptors();

        for (int i = 0; i < mixDescriptors.size(); i++) {
            MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

                    if (md.isPrivateMix() == false) {
                        continue;	// nothing to adjust
                    }

                    MemberReceiver mr = (MemberReceiver) md.getMixDataSource();

            adjustPrivateMixDescriptor(this, mr.getMember(), md);
        }

            /*
             * Adjust private mixes other members have for this member
             */
            synchronized (privateMixesForMe) {
            for (int i = 0; i < privateMixesForMe.size(); i++) {
                ConferenceMember member = (ConferenceMember)
                    privateMixesForMe.get(i);

                /*
                 * Adjust private mixes member has for this call.
                 */
                adjustPrivateMixDescriptor(member, this);
            }
            }
        }
    }
    }

    private void adjustPrivateMixDescriptor(ConferenceMember m1,
        ConferenceMember m2) {

    MixManager mixManager = m1.getMixManager();

        synchronized (mixManager) {
            ArrayList mixDescriptors = mixManager.getMixDescriptors();

        MixDescriptor md =
            mixManager.findMixDescriptor(m2.getMemberReceiver());

        if (md == null) {
            /*
             * m1 doesn't have a private mix for m2.
             */
            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Call " + m1 + " no pm for " + m2);
            }
            return;
        }

        adjustPrivateMixDescriptor(m1, m2, md);
    }
    }

    /*
     * m1 has a private mix for m2.  Adjust the mix descriptor attenuation
     * and muting.
     */
    private void adjustPrivateMixDescriptor(ConferenceMember m1,
        ConferenceMember m2, MixDescriptor md) {

    if (m1.getWhisperGroup() == null) {
        /*
         * m1 hasn't finished initializing
         */
        return;
    }

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Call " + m1 + " adjustPrivateMixDescriptor:  "
            + " md " + md + " pm for " + m2);
    }

    if (m2.getWhisperGroup() == null ||
        m2.getWhisperGroup().isMember(m1) == false) {

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + m1 + " not member of "
            + m2.getWhisperGroup() + ", ev is 0");
        }

        mixManager.setAttenuation(md, 0);
        return;
    }

    double attenuation;

    if (m1.getWhisperGroup() == m2.getWhisperGroup()) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + m1 + " full volume");
        }

        /*
         * Both members are talking in the same whisper group.
         */
        attenuation = 1.0;
    } else {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + m1 + " attenuating to "
            + m1.getWhisperGroup().getAttenuation());
        }

        /*
         * Members are talking in different whisper groups
         */
        attenuation = m1.getWhisperGroup().getAttenuation();
    }

    mixManager.setAttenuation(md, attenuation);

    /*
     * Since both members belong to the same whisper group,
     * the attenuated value needs to be subtracted from the private mix
     * because the member's data is already in the mix by that amount.
     */
    //mixManager.adjustVolume(md, -attenuation);

    if (m1.isConferenceMuted()) {
        mixManager.setMuted(md, true);
    } else {
        mixManager.setMuted(md, false);

        if (m1.isConferenceSilenced()) {
        /*
         * Conference is silenced for m1
         *
         * Mute if the member is talking in the conferenceWhisperGroup
         */
        MemberReceiver memberReceiver = (MemberReceiver)
            md.getMixDataSource();

        if (memberReceiver.getWhisperGroup() ==
            conferenceWhisperGroup) {

            Logger.println("Call " + cp
            + " mute pm for member in main conf");

            mixManager.setMuted(md, true);
        }
        }
    }
    }

    public MixManager getMixManager() {
    return mixManager;
    }

    public void adjustVolume(int[] data, double volume) {
        mixManager.adjustVolume(data, volume);
    }

    private ArrayList privateMixesForMe = new ArrayList();

    public ArrayList getPrivateMixesForMe() {
    return privateMixesForMe;
    }

    public void setPrivateMixForMe(ConferenceMember member) {
    synchronized (conferenceManager) {
        synchronized (privateMixesForMe) {
            if (privateMixesForMe.contains(member) == false) {
            privateMixesForMe.add(member);
            }
        }
    }
    }

    public void removePrivateMixForMe(ConferenceMember member) {
    synchronized (conferenceManager) {
        synchronized (privateMixesForMe) {
            privateMixesForMe.remove(member);
        }
    }
    }

    private double round(double d) {
    return Math.round(d * 1000) / 1000.;
    }

    /*
     * Map of members with private mixes
     */
    private static HashMap<ConferenceMember, HashMap> mixMap =
        new HashMap<ConferenceMember, HashMap>();

    /*
     * Set a private mix that this call has for member.
     */
    public void setPrivateMix(ConferenceMember member, double[] spatialValues) {
    if (cp.getInputTreatment() != null && cp.isRecorder() == false) {
        return;  // an input treatment doesn't need private mixes.  ignore.
    }

        synchronized (mixMap) {
        HashMap<ConferenceMember, double[]> mixesToApply =
        mixMap.get(this);

        if (mixesToApply == null) {
        mixesToApply = new HashMap<ConferenceMember, double[]>();

        mixMap.put(this, mixesToApply);
        }

        if (Logger.logLevel >= Logger.LOG_INFO) {
            if (mixesToApply.get(member) != null) {
            Logger.println(this + " Replacing mix for " + member);
        }
        }

            if (mixesToApply.put(member, spatialValues) != null) {
        replaced++;
        }
        }
    }

    static class PrivateMix {

    public ConferenceMember memberWithMix;
    public ConferenceMember member;
    public double[] spatialValues;

    public PrivateMix(ConferenceMember memberWithMix,
        ConferenceMember member, double[] spatialValues) {

        this.memberWithMix = memberWithMix;
        this.member = member;
        this.spatialValues = spatialValues;
    }

    }

    public static void applyPrivateMixes() {
    ArrayList<PrivateMix> mixes = new ArrayList();

        synchronized (mixMap) {
            Set<ConferenceMember> keySet = mixMap.keySet();

            for (ConferenceMember memberWithMix : keySet) {
            HashMap<ConferenceMember, double[]> mixesToApply =
            mixMap.get(memberWithMix);

                Set<ConferenceMember> memberSet = mixesToApply.keySet();

        if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Applying " + memberSet.size()
            + " private mixes for " + memberWithMix);
        }

        for (ConferenceMember member : memberSet) {
                    double[] spatialValues = (double[]) mixesToApply.get(member);

            mixes.add(new PrivateMix(memberWithMix, member, spatialValues));

            pmCount++;
        }
            }

            mixMap.clear();
        }

    for (PrivateMix mix : mixes) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println("Applying pm for " + mix.memberWithMix
            + " from " + mix.member + " " + mix.spatialValues[0] + ":"
            + mix.spatialValues[1] + ":" + mix.spatialValues[2]
            + ":" + mix.spatialValues[3]);
        }

        synchronized (mix.member.getConferenceManager()) {
            mix.memberWithMix.applyPrivateMix(mix.member, mix.spatialValues);
        }
    }
    }

    private void applyPrivateMix(ConferenceMember member, double[] spatialValues) {
        MixDescriptor md = null;

    synchronized (conferenceManager) {
        synchronized (mixManager) {
        boolean remove = false;

        if (whisperGroup == null || whisperGroup.hasCommonMix()) {
            if (MixDescriptor.isNop(spatialValues, 1)) {
            /*
             * There's a common mix and the member is already
             * mixed in at full volume
             */
                remove = true;
            }
        } else {
            if (MixDescriptor.isZeroVolume(spatialValues[3])) {
            /*
             * There's no common mix, just remove mixDescriptor
             * for member to get zero volume.
             */
            remove = true;
            }
        }

        if (remove) {
                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                    Logger.println("Call " + this
                    + " removing private mix for " + member);
                }

                mixManager.removeMix(member.getMemberReceiver());

                member.removePrivateMixForMe(this);
            return;
            }

            if (getCallHandler() == null ||
            getCallHandler().isCallEstablished() == false) {

                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("skipping " + this);
            }
            return;
            }

            if (member.getCallHandler() == null ||
            member.getCallHandler().isCallEstablished() == false) {

                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("skipping " + member);
            }
            return;
            }

            md = mixManager.setPrivateMix(member.getMemberReceiver(),
            spatialValues);

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + this + " private mix for "
            + member + " " + md);
        }

        if (md == null) {
            if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(this + " pm already set for "
                + member + " vol " + spatialValues[3]);
            }
            return;
        }

            /*
             * Attenuate this private mix.
             * No other mix descriptors need to be attenuated.
             */
        if (member.getWhisperGroup() != null) {
            /*
             * If member isn't done initializing,
             * we can't adjust this descriptor because
             * the member is not yet whispering.
             */
                adjustPrivateMixDescriptor(this, member, md);
        }

            member.setPrivateMixForMe(this);
        }
    }
    }

    private void removePrivateMix(ConferenceMember member) {
    synchronized (mixMap) {
        synchronized (mixManager) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(cp + " removing private mix for " + member.getMemberReceiver());
        }

                mixManager.removeMix(member.getMemberReceiver());
        }

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(member + " removing private mix for " + getMemberReceiver());
        }

            member.removePrivateMixForMe(this);

        HashMap<ConferenceMember, double[]> mixesToApply =
        mixMap.get(this);

        if (mixesToApply == null) {
        return;
        }

        mixesToApply.remove(member);
    }
    }


    static HashMap<String, ArrayList> memberDoneListeners =
    new HashMap<String, ArrayList>();

    public void addMemberDoneListener(ConferenceMember member) {
    synchronized (memberDoneListeners) {
        String conferenceId = member.getConferenceManager().getId();

        ArrayList<ConferenceMember> memberList =
        memberDoneListeners.get(conferenceId);

        if (memberList == null) {
        memberList = new ArrayList<ConferenceMember>();
        memberDoneListeners.put(conferenceId, memberList);

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Created member done list for " + conferenceId);
        }
        }

        if (memberList.contains(member)) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Member already in done list for " + conferenceId);
        }
        return;
        }

        memberList.add(member);
    }
    }

    private void notifyMemberDoneListeners() {
    String conferenceId = conferenceManager.getId();

    ArrayList<ConferenceMember> membersToNotify =
        new ArrayList<ConferenceMember>();

    synchronized (memberDoneListeners) {
        ArrayList<ConferenceMember> memberList =
            memberDoneListeners.get(conferenceId);

        if (memberList == null) {
        return;
        }

        for (ConferenceMember member : memberList) {
        if (this != member) {
            membersToNotify.add(member);
        }
        }

        memberList.remove(this);

        if (memberList.size() == 0) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Removing member done list for " + conferenceId);
        }
        memberDoneListeners.remove(conferenceId);
        }
    }

    for (ConferenceMember member : membersToNotify) {
        member.memberDoneNotification(this);
    }
    }

    public void memberDoneNotification(ConferenceMember member) {
    if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println("Call " + cp + " got memberDoneNotification for "
        + member);
    }

    /*
     * Remove private mix for member if we have one.
     */
    removePrivateMix(member);

    memberReceiver.removeForwardMember(member.getMemberSender());
    }

    public String getAbbreviatedMixDescriptors() {
    synchronized (mixManager) {
        return mixManager.toAbbreviatedString();
    }
    }

    public MixDescriptor findMixDescriptor(ConferenceMember member) {
    synchronized (mixManager) {
        MixDescriptor mixDescriptor = (MixDescriptor)
               mixManager.findMixDescriptor((MixDataSource)
               member.getMemberReceiver());

        return mixDescriptor;
    }
    }

    public String getMixDescriptors() {
    synchronized (mixManager) {
        return mixManager.toString();
    }
    }

    /**
     * Mute or unmute the conference from a member
     */
    public void setConferenceMuted(boolean isConferenceMuted) {
        if (traceCall || Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp + " muteConference is now "
        + isConferenceMuted);
        }

        cp.setConferenceMuted(isConferenceMuted);

    attenuateWhisperGroups();
    adjustPrivateMixDescriptors();
    }

    public boolean isConferenceMuted() {
    return cp.isConferenceMuted();
    }

    /**
     * Silence or unSilence the main conference from a member
     */
    public void setConferenceSilenced(boolean isConferenceSilenced) {
        if (traceCall || Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp + " conferenceSilenced is now "
                + isConferenceSilenced);
        }

        cp.setConferenceSilenced(isConferenceSilenced);

    attenuateWhisperGroups();
    adjustPrivateMixDescriptors();
    }

    public boolean isConferenceSilenced() {
        return cp.isConferenceSilenced();
    }

    private void muteConferenceWhisperGroup(boolean isMuted) {
    synchronized (mixManager) {
            ArrayList mixDescriptors = mixManager.getMixDescriptors();

            for (int i = 0; i < mixDescriptors.size(); i++) {
                MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

                mixManager.setMuted(md, false);

                if (md.getMixDataSource() == conferenceWhisperGroup) {
                    mixManager.setMuted(md, isMuted);
            break;
            }
        }
    }
    }

    /**
     * Member is leaving a conference.  Print statistics for the member.
     */
    public void end() {
        if (done) {
            return;
    }

        done = true;

    removeCallFromAllWhisperGroups();

    memberSender.end();
    memberReceiver.end();

    if (rtcpReceiver != null && rtcpReceiver != loneRtcpReceiver) {
        rtcpReceiver.end();
    }

    /*
     * Don't leave whisper groups or change private mixes if
     * the call is migrating.
     */
    if (migrating == false) {
        notifyMemberDoneListeners();

        synchronized (conferenceManager) {
        removeMyPrivateMixes();

        synchronized (privateMixesForMe) {
            removePrivateMixesForMe();
            }
        }
    }

    printStatistics();
    }

    private void removeMyPrivateMixes() {
    ArrayList pmToRemove = new ArrayList();

    /*
     * Make a list of private mixes this call has for others
     * Then remove the private mixes.
     * We can't remove as we go through the list because
     * we will be changing the list.
     */
    synchronized (mixManager) {
        ArrayList mixDescriptors = mixManager.getMixDescriptors();

        for (int i = 0; i < mixDescriptors.size(); i++) {
                MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

            if (md.isPrivateMix() == false) {
                    continue;  	// not a private mix
                }

            MemberReceiver mr = (MemberReceiver) md.getMixDataSource();

            pmToRemove.add(mr.getMember());
        }

        for (int i = 0; i < pmToRemove.size(); i++) {
            ConferenceMember member = (ConferenceMember) pmToRemove.get(i);

            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + cp
                + " removing private mix for " + member);
            }

            removePrivateMix(member);
        }
    }
    }

    private void removePrivateMixesForMe() {
        /*
         * Make a list of private mixes this call has for others
         * Then remove the private mixes.
         * We can't remove as we go through the list because
         * we will be changing the list.
         */
        ArrayList pmToRemove = new ArrayList();

    /*
     * Remove private mixes other members have for this call.
     */
    synchronized (privateMixesForMe) {
        for (int i = 0; i < privateMixesForMe.size(); i++) {
            ConferenceMember member = (ConferenceMember)
            privateMixesForMe.get(i);

            pmToRemove.add(member);
        }
    }

        for (int i = 0; i < pmToRemove.size(); i++) {
            ConferenceMember member = (ConferenceMember)
                pmToRemove.get(i);

            member.removePrivateMix(this);

            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Call " + member
                    + " removing private mix for " + this);
            }
        }
    }

    public void migrating() {
    migrating = true;
    }

    public void printStatistics() {
    synchronized (statisticsLock) {
        memberSender.printStatistics();
        memberReceiver.printStatistics();
    }
    }

    public ConferenceManager getConferenceManager() {
    return conferenceManager;
    }

    /**
     * Get CallParticipant for this member
     */
    public CallParticipant getCallParticipant() {
    return cp;
    }

    /**
     * Get CallHandler for this member
     */
    public CallHandler getCallHandler() {
    return callHandler;
    }

    public WhisperGroup getWhisperGroup() {
    return memberReceiver.getWhisperGroup();
    }

    public void setNoCommonMix(String whisperGroupId) throws ParseException {
    WhisperGroup whisperGroup = wgManager.findWhisperGroup(whisperGroupId);

    if (whisperGroup == null) {
        throw new ParseException("No such whisper group:  "
        + whisperGroupId, 0);
    }

    if (this.whisperGroup != whisperGroup) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Not in same wg, this " + this.whisperGroup
        + " other " + whisperGroup);
        }
        return;
    }

    if (cp.getInputTreatment() != null && cp.isRecorder() == false) {
        return;  // input treatments don't have descriptors
    }

    synchronized (mixManager) {
        if (whisperGroup.hasCommonMix()) {
                mixManager.addMix(whisperGroup, 1);
                mixManager.addMix(memberReceiver, -1); 	// mix-minus
        } else {
                mixManager.removeMix(whisperGroup);
                mixManager.removeMix(memberReceiver); // no need for mix-minus
        }
    }

    double[] spatialValues = new double[4];

    /*
     * If there's a common mix, add descriptors of zero volume for
     * all other non-inputTreatment members.
     *
     * If there is no common mix, remove all descriptors for all calls,
     * effectively making the volume zero for each other call.
     */
    synchronized (conferenceManager) {
        ArrayList<ConferenceMember> memberList = conferenceManager.getMemberList();

        for (ConferenceMember member : memberList) {
        if (member == this) {
            continue;
        }

        setPrivateMix(member, spatialValues);
        }
    }
    }

    public void addCall(String whisperGroupId) throws ParseException {
        synchronized (conferenceManager) {
        synchronized (whisperGroups) {
            WhisperGroup whisperGroup =
            wgManager.findWhisperGroup(whisperGroupId);

            if (whisperGroup == null) {
            Logger.println("Call " + cp + " Whisper group "
                        + whisperGroupId + " doesn't exist.  "
                        + "Automatically creating it with attenuation 0 "
            + "and locked");

                    try {
                        whisperGroup = conferenceManager.createWhisperGroup(
                whisperGroupId, 0.0D);
            whisperGroup.setTransient(true);
            whisperGroup.setLocked(true);
                    } catch (ParseException e) {
                        Logger.println("Can't create whisper group "
                + whisperGroupId + " " + e.getMessage());

                        throw new ParseException("Can't create whisper group "
                            + whisperGroupId + " " + e.getMessage(), 0);
            }
        }

        addCall(whisperGroup);
        }
    }
    }

    private void addCall(WhisperGroup whisperGroup) {
        synchronized (conferenceManager) {
        synchronized (whisperGroups) {
        whisperGroup.addCall(this);

        double attenuation = whisperGroup.getAttenuation();

        if (this.whisperGroup != null &&
                this.whisperGroup.getAttenuation() == 0) {

            /*
             * Call is whispering in a 0 attenuation group.
             * Descriptor must have 0 attenuation.
             */
                attenuation = 0;
        }

        synchronized(mixManager) {
            if (whisperGroup.hasCommonMix() == true &&
                (cp.getInputTreatment() == null ||
                cp.isRecorder() == true)) {

                    mixManager.addMix(whisperGroup, attenuation);
            } else {
                mixManager.removeMix(memberReceiver);  // no mix minus
            }
        }

        if (whisperGroup.getAttenuation() == 0) {
            /*
             * If the call is in a whisper group with full attenuation
             * the call is immediately set to whispering so
             * it can't hear anything else.
             */
                    if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Call " + cp + " entered 0 attenuation "
                + "start whispering now!");
            }

            setWhispering(whisperGroup);

                    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                synchronized (mixManager) {
                            mixManager.showDescriptors();
            }
                    }

                    return;
        }

                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            synchronized (mixManager) {
                    mixManager.showDescriptors();
            }
        }
        }
        }
    }

    private void removeCallFromAllWhisperGroups() {
        synchronized (conferenceManager) {
        synchronized (whisperGroups) {
                for (int i = 0; i < whisperGroups.size(); i++) {
                    WhisperGroup whisperGroup =
                        (WhisperGroup)whisperGroups.get(i);

                    removeCall(whisperGroup.getId());
        }
        }
    }
    }

    public void removeCall(String whisperGroupId) {
        /*
         * We must grab the lock so we don't deadlock with the sender thread.
         */
        synchronized (conferenceManager) {
        synchronized (whisperGroups) {
            WhisperGroup whisperGroup =
            wgManager.findWhisperGroup(whisperGroupId);

            if (whisperGroup == null) {
            Logger.println("Whisper group doesn't exist for "
            + whisperGroupId + "!");
            return;
        }

        if (this.whisperGroup == whisperGroup) {
            /*
             * Start talking in the main conference.
             */
            setWhispering(conferenceWhisperGroup);
        }

        wgManager.removeCall(whisperGroup, this);

        synchronized (mixManager) {
                mixManager.removeMix(whisperGroup);
        }
        }
    }
    }

    /*
     * For call migration, preserve settings old call had.
     */
    public void migrate(ConferenceMember oldMember) {
        synchronized (conferenceManager) {
        /*
         * If old member was muted, make new member be muted.
         */
        getMemberReceiver().setMuted(oldMember.getCallParticipant().isMuted());

        /*
         * copy private mixes old call has to current call
         */
        copyOldPrivateMixes(oldMember);

        /*
         * Also update private mixes other calls have for old call
         * to be for new call
         */
        updateOtherPrivateMixes(oldMember);

        /*
         * remove old member from all whisper groups
         * and add new member to the whisper groups the
         * old member was in.
         */
        wgManager.migrate(oldMember, this);

        /*
         * If the old member was whispering in a whisper group,
         * set the new member to be whispering.
         */
        WhisperGroup whisperGroup = oldMember.getWhisperGroup();

        setWhispering(whisperGroup);
        }
    }

    private void copyOldPrivateMixes(ConferenceMember oldMember) {
    /*
     * copy private mixes the oldMember has
     */
    MixManager mixManager = oldMember.getMixManager();

    synchronized (mixManager) {
            ArrayList mixDescriptors = mixManager.getMixDescriptors();

        for (int i = 0; i < mixDescriptors.size(); i++) {
                MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

            if (md.isPrivateMix() == false) {
                continue;	// not a private mix
            }

            MemberReceiver mr = (MemberReceiver) md.getMixDataSource();

            if (mr.getMember() == oldMember) {
            continue;	// it's a descriptor for oldMember (mix minus)
            }

            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("pre-migrate member " + oldMember
                + " has pm for " + mr);

                Logger.println("pre-migrate member " + oldMember
                + " mix descriptors " + oldMember.getMixDescriptors());
            }

        synchronized (mixMap) {
                applyPrivateMix(mr.getMember(), md.getSpatialValues());
        }

            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Call " + cp + " Set private mix for "
                + mr + " to " + md);
        }
        }

        /*
         * Now go through the md's for the old member and remove
         * all the private mixes it has
         */
            for (int i = 0; i < mixDescriptors.size(); i++) {
                MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

                if (md.isPrivateMix() == false) {
                    continue;   // not a private mix
                }

                MemberReceiver mr = (MemberReceiver) md.getMixDataSource();

                if (mr.getMember() == oldMember) {
                    continue;   // it's a descriptor for oldMember (mix minus)
                }

        /*
         * Remove private mix oldMember has
         */
            if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(oldMember + " removing pm for " + mr.getMember());
        }
        oldMember.removePrivateMix(mr.getMember());
        }
    }
    }

    /*
     * Update private mixes of all other members
     * who have a private mix for the oldMember.
     */
    private void updateOtherPrivateMixes(ConferenceMember oldMember) {
    ArrayList privateMixesForMe = oldMember.getPrivateMixesForMe();

    if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println(oldMember + " private mixes for me " +
            oldMember.getPrivateMixesForMe().size());
    }

    ConferenceMember[] pmArrayForMe = (ConferenceMember[])
         privateMixesForMe.toArray(new ConferenceMember[0]);

    for (int i = 0; i < pmArrayForMe.length; i++) {
        ConferenceMember m = pmArrayForMe[i];

        MixManager mixManager = m.getMixManager();

        synchronized (mixManager) {
            MixDescriptor[] mixDescriptors = (MixDescriptor[])
            mixManager.getMixDescriptors().toArray(new MixDescriptor[0]);

                if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("member with pm " + m
                        + " descriptors before...");
                    mixManager.showDescriptors();
                }

                for (int j = 0; j < mixDescriptors.length; j++) {
                    MixDescriptor md = mixDescriptors[j];

            if (md.isPrivateMix() == false) {
            Logger.println(this + " Skipping md for " + md);
            continue; 	// not a private mix
            }

                MemberReceiver mr = (MemberReceiver) md.getMixDataSource();

            if (mr.getMember() != oldMember) {
            Logger.println(this + " md " + md + " not an md for old member " + mr);
            continue;	// not a pm for old Member.
            }

                    if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("setting pm for " + m
                + " to new member " + this.getMemberReceiver());
            }

            /*
             * Now remove private mix other call has for oldMember
             */
            m.removePrivateMix(oldMember);

            /*
             * Set private mix for new member
             */
            synchronized (mixMap) {
                m.applyPrivateMix(this, md.getSpatialValues());
            }

                    if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("member with pm " + m
                + " descriptors after...");
                    mixManager.showDescriptors();
            }
            break;
            }
        }
    }
    }

    public void setWhispering(String whisperGroupId) throws ParseException {
        synchronized (conferenceManager) {
        synchronized (whisperGroups) {
                WhisperGroup whisperGroup =
            wgManager.findWhisperGroup(whisperGroupId);

                if (whisperGroup == null) {
            Logger.println("Call " + cp
            + " invalid whisper group " + whisperGroupId);

            throw new ParseException("Call " + cp
            + " invalid whisper group " + whisperGroupId, 0);
        }

        if (whisperGroup.isMember(this) == false) {
            Logger.println("Call " + cp
            + " is not a member of whisper group "
            + whisperGroupId);

            throw new ParseException("Call " + cp
            + " is not a member of whisper group "
            + whisperGroupId, 0);
        }

        if (this.whisperGroup == whisperGroup) {
            Logger.println("Call " + cp + " already whispering in "
            + whisperGroup);
            return;
        }

        if (this.whisperGroup.isLocked()) {
                Logger.println("Calls in a locked whisper group "
                + "cannot stop whispering until the call "
                + "is removed from the wg");

                throw new ParseException("Calls in a locked whisper group "
                + "cannot stop whispering until the call "
                + "is removed from the wg", 0);
            }

        setWhispering(whisperGroup);
        }
    }
    }

    public void setWhispering(WhisperGroup newWhisperGroup) {
        synchronized (conferenceManager) {
            synchronized (whisperGroups) {
        if (whisperGroup == null) {
            /*
             * This is the first time.  We're still initializing.
             */
            whisperGroup = newWhisperGroup;
                whisperGroup.setWhispering(true, this);
            memberReceiver.setWhisperGroup(whisperGroup);

            synchronized (mixManager) {
            if (whisperGroup.hasCommonMix() == true &&
                    (cp.getInputTreatment() == null ||
                    cp.isRecorder() == true)) {

                        mixManager.addMix(whisperGroup, 1.0D);
            }
                adjustPrivateMixDescriptors();
            }
            return;
        }

        if (initialWhisperGroup != null) {
            String id = initialWhisperGroup.getId();

            initialWhisperGroup = null;

            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Removing initial wg " + id);
            }
            removeCall(id);
        }

        if (newWhisperGroup == whisperGroup) {
                Logger.writeFile("Call " + this
            + " is already whispering to " + whisperGroup);
            return;
        }

        synchronized (whisperGroup) {
            /*
             * Stop whispering in old whisper group
             */
                    whisperGroup.setWhispering(false, this);

                    /*
                     * flush any left over contributions
                     */
                    memberReceiver.flushContributions();

            /*
             * Start whispering in new whisper group
             */
            whisperGroup = newWhisperGroup;
            memberReceiver.setWhisperGroup(whisperGroup);

            Logger.println("Call " + cp
                + " Now whispering in " + whisperGroup);

                whisperGroup.setWhispering(true, this);

            synchronized (mixManager) {
            if (whisperGroup.hasCommonMix() == true &&
                    (cp.getInputTreatment() == null ||
                    cp.isRecorder() == true)) {

                        mixManager.addMix(whisperGroup, 1.0D);
            }

                attenuateWhisperGroups();
                adjustPrivateMixDescriptors();
            }
        }
        }
    }
    }

    /*
     * Attenuate whisperGroups which we are not speaking in.
     */
    private void attenuateWhisperGroups() {
    if (whisperGroup == null) {
        return;  // not done initializing
    }

    synchronized (conferenceManager) {
            synchronized(mixManager) {
            ArrayList mixDescriptors = mixManager.getMixDescriptors();

                /*
                 * Attenuate whisperGroups
                 */
                for (int i = 0; i < mixDescriptors.size(); i++) {
                    MixDescriptor md = (MixDescriptor) mixDescriptors.get(i);

            if (md.getMixDataSource() instanceof WhisperGroup ==
                false) {

                continue;
            }

            WhisperGroup wg = (WhisperGroup) md.getMixDataSource();

            muteDescriptor(wg, md);

            if (wg == whisperGroup) {
                mixManager.setAttenuation(md, 1);
                continue;	// we're whispering in this one
            }

                    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                        Logger.println("Call " + cp
                            + " talking in " + whisperGroup
                            + ", attenuating " + wg
                            + " to " + wg.getAttenuation());
                    }

            if (whisperGroup.getAttenuation() == 0) {
                /*
                 * I am whispering in a 0 attenuation whisper group.
                 * I should not hear any other whisper group data.
                 */
                        mixManager.setAttenuation(md, 0);
                continue;
            }

                    mixManager.setAttenuation(md, wg.getAttenuation());
                }
        }
        }
    }

    /*
     * Decide whether or not to mute this whisper group descriptor
     */
    private void muteDescriptor(WhisperGroup wg, MixDescriptor md) {
    if (cp.isConferenceMuted()) {
        synchronized (mixManager) {
                mixManager.setMuted(md, true);
        }
        return;
    }

    if (cp.isConferenceSilenced() == false) {
        synchronized (mixManager) {
            mixManager.setMuted(md, false);
        }
        return;
    }

    /*
     * The conference is silenced.  Mute whisper groups
     * we're not talking in.  Also mute the conference whisper group.
     */
    if (wg != whisperGroup || wg == conferenceWhisperGroup) {
        synchronized (mixManager) {
                mixManager.setMuted(md, true);
        }
        return;
    }

    /*
     * We're whispering in some wg.
     */
    synchronized (mixManager) {
        mixManager.setMuted(md, false);
    }
    }

    public void setInputVolume(double volume) {
    memberReceiver.setInputVolume(volume);
    }

    public double getInputVolume() {
    return memberReceiver.getInputVolume();
    }

    public void setOutputVolume(double volume) {
    memberSender.setOutputVolume(volume);
    }

    public double getOutputVolume() {
    return memberSender.getOutputVolume();
    }

    public void saveCurrentContribution() {
    memberReceiver.saveCurrentContribution();

    synchronized (memberTreatments) {
            if (currentTreatment == null) {
        return;
        }

        currentTreatment.saveCurrentContribution();
    }
    }

    public String getSourceId() {
    return cp.getCallId();
    }

    public boolean contributionIsInCommonMix() {
    return memberReceiver.contributionIsInCommonMix();
    }

    public int[] getPreviousContribution() {
    return memberReceiver.getPreviousContribution();
    }

    public int[] getCurrentContribution() {
    return memberReceiver.getCurrentContribution();
    }

    public void invalidateCurrentContribution() {
    memberReceiver.invalidateCurrentContribution();
    }

    public boolean sendData() {

    if (cp.getInputTreatment() != null && cp.getToRecordingFile() == null) {
        /*
         * We don't send data to a call playing an input treatment
         * unless that call is recording.
         */
        return true;
    }

    /*
         * Since we know we get called here every 20ms, use this opportunity
         * to check if we've received any data or not and
         * handle appropriate timeouts.
         */
    if (memberReceiver.checkPacketsReceived() == false) {
        return false;
    }

    int timeout = cp.getCallTimeout();

    if (timeout > 0) {
        timeout -= RtpPacket.PACKET_PERIOD;

        if (timeout <= 0) {
            cp.setCallTimeout(0);
        callHandler.cancelRequest("Call timeout");
        return false;
        }

        cp.setCallTimeout(timeout);
    }

    synchronized (mixManager) {
            return memberSender.sendData(mixManager.mix());
    }
    }

    /*
     * Add a treatment to be played to this member
     * We only play one treatment at a time.
     */
    ArrayList memberTreatments = new ArrayList();
    TreatmentManager currentTreatment;

    public void addTreatment(TreatmentManager treatmentManager) {
        synchronized (conferenceManager) {
        synchronized (memberTreatments) {
            memberTreatments.add(treatmentManager);

        if (currentTreatment == null) {
            startNextTreatment();
        }
        }
    }
    }

    private void startNextTreatment() {
    if (memberTreatments.size() == 0) {
        currentTreatment = null;
        return;
    }

        currentTreatment = (TreatmentManager) memberTreatments.get(0);
    currentTreatment.addTreatmentDoneListener(this);

    synchronized (mixManager) {
        mixManager.addMix(currentTreatment, 1.0D);
    }

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + cp
                + " Starting next treatment " + currentTreatment.getId());
    }
    }

    public void treatmentDoneNotification(TreatmentManager treatmentManager) {
        synchronized (conferenceManager) {
        synchronized (memberTreatments) {
            memberTreatments.remove(treatmentManager);

        synchronized (mixManager) {
            mixManager.removeMix(treatmentManager);
        }

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println(
            "Treatment done " + treatmentManager.getId());
            Logger.println(
            "treatments left " + memberTreatments.size());
        }

        CallEvent callEvent = new CallEvent(CallEvent.TREATMENT_DONE);
        callEvent.setTreatmentId(treatmentManager.getId());
        callHandler.sendCallEventNotification(callEvent);

        startNextTreatment();
        }
    }
    }

    public void pauseTreatment(String treatmentId, boolean isPaused) {
        synchronized (conferenceManager) {
            synchronized (memberTreatments) {
                for (int i = 0; i < memberTreatments.size(); i++) {
                    TreatmentManager treatmentManager = (TreatmentManager)
                        memberTreatments.get(i);

            if (treatmentId == null) {
                        treatmentManager.pause(isPaused);
            } else {
                if (treatmentManager.getId().equals(treatmentId)) {
                            treatmentManager.pause(isPaused);
                return;
                }
                    }
            }
            }
    }
    }

    public void stopTreatment(String treatmentId) {
        synchronized (conferenceManager) {
            synchronized (memberTreatments) {
                if (treatmentId == null) {
            /*
             * Before stopping the current treatment (if any)
             * we have to remove any following treatments.
             * Otherwise when this thread calls stopTreatment(),
             * the next treatment will be started.
             */
            memberTreatments.clear();

            if (currentTreatment != null) {
            currentTreatment.stopTreatment();
            }

            return;
        }

                for (int i = 0; i < memberTreatments.size(); i++) {
                    TreatmentManager treatmentManager = (TreatmentManager)
                        memberTreatments.get(i);

                    if (treatmentManager.getId().equals(treatmentId)) {
            if (treatmentManager == currentTreatment) {
                            treatmentManager.stopTreatment();
            } else {
                memberTreatments.remove(i);
            }

            return;
                    }
                }
            }
    }
    }

    public boolean hasTreatments() {
    return memberTreatments.size() > 0;
    }

    public String toString() {
    return cp.toString();
    }

    public String toAbbreviatedString() {
    String callId = cp.getCallId();

    if (callId.length() < 14) {
        return callId;
    }

    return cp.getCallId().substring(0, 13);
    }

}
