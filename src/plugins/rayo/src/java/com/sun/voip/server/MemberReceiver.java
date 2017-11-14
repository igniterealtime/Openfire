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

import com.sun.voip.AudioConversion;
import com.sun.voip.CallParticipant;
import com.sun.voip.CallEvent;
import com.sun.voip.DataUpdater;
import com.sun.voip.JitterManager;
import com.sun.voip.JitterObject;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.MixDataSource;
import com.sun.voip.Recorder;
import com.sun.voip.RtcpReceiver;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSocket;
import com.sun.voip.RtpReceiverPacket;
import com.sun.voip.SampleRateConverter;
import com.sun.voip.SdpManager;
import com.sun.voip.SpeechDetector;
import com.sun.voip.SpeexDecoder;
import com.sun.voip.SpeexException;
import com.sun.voip.TreatmentDoneListener;
import com.sun.voip.TreatmentManager;
import com.sun.voip.Util;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.nio.channels.DatagramChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import java.text.ParseException;

import java.awt.Point;

import org.ifsoft.*;
import org.ifsoft.rtp.*;

import org.jitsi.impl.neomedia.codec.audio.opus.Opus;

import org.xmpp.jnodes.IChannel;



/**
 * Receive RTP data for this ConferenceMember, add it to the mix
 * and keep statistics.
 */
public class MemberReceiver implements MixDataSource, TreatmentDoneListener {

    private ConferenceManager conferenceManager;
    private ConferenceMember member;
    private CallHandler callHandler;
    private CallParticipant cp;		             // caller parameters

    private boolean traceCall = false;

    private boolean isAutoMuted;	       // to suppress dtmf sounds


    /*
     * Each member can only be whispering in one group at a time.
     */
    private WhisperGroup whisperGroup;	// currently whispering in this group

    private WhisperGroup conferenceWhisperGroup;

    private byte telephoneEventPayload;

    private MediaInfo myMediaInfo;

    private double inputVolume = 1.0;

    private boolean readyToReceiveData = false;
    private boolean gotComfortPayload = false;       // flag COMFORT_PAYLOAD
    private byte comfortNoiseLevel;		     // comfort noise level

    private SpeechDetector speechDetector = null;
    private DtmfDecoder dtmfDecoder = null;

    private int dtmfPackets;

    private static boolean forwardDtmfKeys = true;

    private RtpReceiverPacket packet;

    private SpeexDecoder speexDecoder;

    private long opusDecoder = 0;
    private final int opusSampleRate = 48000;
    private final int frameSizeInMillis = 20;
    private final int outputFrameSize = 2;
    private final int opusChannels = 2;
    private int frameSizeInSamplesPerChannel = (opusSampleRate * frameSizeInMillis) / 1000;
    private int frameSizeInBytes = outputFrameSize * opusChannels * frameSizeInSamplesPerChannel;


    private int dropPackets;

    private boolean done = false;

    private int myMemberNumber;
    private static int memberNumber;
    private static Object memberNumberLock = new Object();

    /*
     * Statistics
     */
    private String timeStarted;

    private int packetsReceived = 0;
    private int packetsDropped = 0;
    private int comfortPayloadsReceived = 0;
    private int comfortPayloadsSent = 0;

    private long timeCurrentPacketReceived;
    private long timePreviousPacketReceived;
    private long totalTime = 0;
    private long timeToProcessMediaPackets;
    private int mediaPacketsReceived;
    private int lastMediaPacketsReceived;
    private int badPayloads;

    private long previousRtpTimestamp = 0;

    private boolean joinConfirmationReceived = false;

    private static String joinConfirmationKey = "1";

    private Cipher decryptCipher;

    private String encryptionKey;
    private String encryptionAlgorithm;

    private SampleRateConverter inSampleRateConverter;

    private DatagramChannel datagramChannel;
    private SelectionKey selectionKey;
    private RtcpReceiver rtcpReceiver;

    private JitterManager jitterManager;

    private ArrayList<MemberSender> forwardMemberList = new ArrayList<MemberSender>();

    private boolean initializationDone = false;

    private IChannel relayChannel = null;



    public void setChannel(IChannel relayChannel)
    {
        this.relayChannel = relayChannel;
    }

    public MemberReceiver(ConferenceMember member, CallParticipant cp, DatagramChannel datagramChannel) throws IOException
    {

    this.member = member;
    this.cp = cp;
    this.datagramChannel = datagramChannel;

    synchronized (memberNumberLock) {
        myMemberNumber = memberNumber++;
    }

    encryptionKey = cp.getEncryptionKey();
    encryptionAlgorithm = cp.getEncryptionAlgorithm();

    if (encryptionKey != null) {
        try {
        if (encryptionKey.length() < 8) {
            encryptionKey +=
            String.valueOf(System.currentTimeMillis());
        }

        if (encryptionKey.length() > 8 &&
            encryptionAlgorithm.equals("DES")) {

            encryptionKey = encryptionKey.substring(0, 8);
        }

        byte[] keyBytes = encryptionKey.getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes,
            encryptionAlgorithm);

            decryptCipher = Cipher.getInstance(encryptionAlgorithm);
            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);

        Logger.println("Call " + cp + " Voice data will be decrypted "
            + "using " + encryptionAlgorithm);
        } catch (Exception e) {
        Logger.println("Call " + cp
            + " Crypto initialization failed " + e.getMessage());
                throw new IOException(" Crypto initialization failed "
            + e.getMessage());
        }
    }

    timeStarted = Logger.getDate();
    }

    public ConferenceMember getMember()
    {
        return member;
    }

    public MediaInfo getMediaInfo()
    {
        return myMediaInfo;
    }

    public byte getTelephoneEventPayload()
    {
        return telephoneEventPayload;
    }

    /*
     * For debugging.
     */
    public void traceCall(boolean traceCall)
    {
        this.traceCall = traceCall;
    }

    public boolean traceCall()
    {
        return traceCall;
    }

    public String getPerformanceData() throws IOException {
    if (done) {
        throw new IOException("Call " + cp + " has ended");
    }

    String s = "PacketsReceived=" + packetsReceived;

    s += ":MissingPackets=" + packet.getOutOfSequencePackets();

    s += ":JitterBufferSize=" + jitterManager.getJitterBufferSize();

    return s;
    }

    public void setCnThresh(int cnThresh) {
    if (speechDetector == null) {
        Logger.println("Can't set cnThresh because there is no "
        + "speech detector");
        return;
    }

    speechDetector.setCnThresh(cnThresh);
    }

    public void setPowerThresholdLimit(float powerThresholdLimit) {
        if (speechDetector == null) {
            Logger.println("Can't set powerThresholdLimit because there is no "
                + "speech detector");
            return;
        }

        speechDetector.setPowerThresholdLimit(powerThresholdLimit);
    }

    public String getMemberState() {
    if (initializationDone == false) {
        return "";
    }

    String s = "";

    if (callHandler != null) {
        s += "\tBridge receive address for data from call "
            + callHandler.getReceiveAddress() + "\n";
    }

    s += "\tJoinConfirmationReceived " + joinConfirmationReceived + "\n";
    s += "\tTelephone Event Payload " + telephoneEventPayload + "\n";
    s += "\tIsAutoMuted " + isAutoMuted + "\n";
    s += "\tIsMuted " + cp.isMuted() + "\n";
    s += "\tIsConferenceMuted " + cp.isConferenceMuted() + "\n";
    s += "\tIsConferenceSilenced " + cp.isConferenceSilenced() + "\n";
    s += "\tReadyToReceiveData " + readyToReceiveData() + "\n";

    s += "\tInput volume ";

    s += inputVolume + " ";

    s += "\n";

        s += "\tSeconds since last Rtcp report "
        + rtcpReceiver.secondsSinceLastReport(member.getRtcpAddress()) + "\n";

    s += "\tMilliseconds since last packet received "
        + (System.currentTimeMillis() - timeCurrentPacketReceived + "\n");
    s += "\tMedia packets received " + mediaPacketsReceived + "\n";
    s += "\tMin jitter size " + jitterManager.getMinJitterBufferSize()
        + " packets\n";
    s += "\tMax jitter size " + jitterManager.getMaxJitterBufferSize()
        + " packets\n";
    s += "\tJitter Buffer size " + jitterManager.getJitterBufferSize()
        + "\n";
    s += "\tPacketLossConcealment class name "
        + jitterManager.getPlcClassName() + "\n";

    s += "\tWhispering in " + whisperGroup.toAbbreviatedString() + "\n";

    s += "\tComfort Payload Received " + gotComfortPayload + "\n";
    s += "\tForced to defer mixing " + forcedToDeferMixing + "\n";

    synchronized (forwardMemberList) {
        if (forwardMemberList.size() > 0) {
        s += "\tForwarding data to\n";
        for (MemberSender memberSender : forwardMemberList) {
            s += "\t\t" + memberSender + "\n";
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
    public void initialize(ConferenceManager conferenceManager, CallHandler callHandler, byte mediaPayload,
        byte telephoneEventPayload, RtcpReceiver rtcpReceiver) {

    this.conferenceManager = conferenceManager;
    this.telephoneEventPayload = telephoneEventPayload;
    this.rtcpReceiver = rtcpReceiver;
    this.callHandler = callHandler;

    Logger.writeFile("Call " + cp  + " MemberReceiver initialization started..." + cp.getProtocol());

    conferenceWhisperGroup =  conferenceManager.getWGManager().getConferenceWhisperGroup();

    MediaInfo conferenceMediaInfo = conferenceManager.getMediaInfo();

    int outSampleRate = conferenceMediaInfo.getSampleRate();
    int outChannels = conferenceMediaInfo.getChannels();

    jitterManager = new JitterManager("Call " + cp.toString());

    if (cp.voiceDetection()) {

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + cp + " starting speech Detector...");
        }
            speechDetector = new SpeechDetector(this.toString(), conferenceMediaInfo);
    }



    if (cp.getProtocol() != null && ("WebRtc".equals(cp.getProtocol()) || "Rtmfp".equals(cp.getProtocol()) || "Speaker".equals(cp.getProtocol())))
    {
        conferenceManager.getConferenceReceiver().addMember(this);

        if (cp.getJoinConfirmationTimeout() == 0)
        {
            joinConfirmationReceived = true;
            readyToReceiveData = true;
            playJoinTreatment();
        }

    } else {

        try {
            myMediaInfo = SdpManager.findMediaInfo(mediaPayload);
        } catch (ParseException e) {
            Logger.println("Call " + cp + " Invalid mediaPayload "
            + mediaPayload);

            callHandler.cancelRequest("Invalid mediaPayload " + mediaPayload);
            return;
        }

        Logger.println("My media info:  " + myMediaInfo);

        int inSampleRate = myMediaInfo.getSampleRate();
        int inChannels = myMediaInfo.getChannels();


        //if (cp.getPhoneNumber().indexOf("@") >= 0) {
            ConferenceReceiver conferenceReceiver = conferenceManager.getConferenceReceiver();
            conferenceManager.getConferenceReceiver().addMember(this);
        //}

        /*
         * For input treatments, the treatment manager does the resampling.
         */
        if (cp.getInputTreatment() == null) {
            if (inSampleRate != outSampleRate || inChannels != outChannels) {
                try {
                Logger.println("Call " + cp
                    + " resample received data from " + inSampleRate + "/"
                    + inChannels + " to " + outSampleRate
                    + "/" + outChannels);

                    inSampleRateConverter = new SampleRateConverter(
                    this.toString(), inSampleRate, inChannels,
                    outSampleRate, outChannels);
                } catch (IOException e) {
                    callHandler.cancelRequest(e.getMessage());
                return;
                }
            }
        }

        packet = new RtpReceiverPacket(cp.toString(), myMediaInfo.getEncoding(), inSampleRate, inChannels);

        if (initializationDone) {
            /*
             * This is a re-initialize
             */
            return;
        }

        //if (telephoneEventPayload == 0 && (cp.dtmfDetection() || cp.getJoinConfirmationTimeout() != 0)) {

            Logger.println("Call " + cp + " starting dtmf Detector..." + telephoneEventPayload + " " + cp.dtmfDetection());

            dtmfDecoder = new DtmfDecoder(this, myMediaInfo);
        //}

        if (myMediaInfo.getEncoding() == RtpPacket.SPEEX_ENCODING) {
                try {
                    speexDecoder = new SpeexDecoder(inSampleRate, inChannels);
                    Logger.println("Call " + cp + " created SpeexDecoder");
                } catch (SpeexException e) {
                    Logger.println("Call " + cp + e.getMessage());
                    callHandler.cancelRequest(e.getMessage());
                    return;
                }

        } else 	if (myMediaInfo.getEncoding() == RtpPacket.PCM_ENCODING) {

            try {
                opusDecoder = Opus.decoder_create(opusSampleRate, opusChannels);

                if (opusDecoder == 0)
                {
                    Logger.println("Call " + cp + " OPUS decoder creation error ");
                    callHandler.cancelRequest("OPUS decoder creation error ");
                    return;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        if (cp.getJoinConfirmationTimeout() == 0) {
                joinConfirmationReceived = true;
            readyToReceiveData = true;
            playJoinTreatment();
        }

        if (cp.getInputTreatment() != null &&
            cp.getInputTreatment().length() > 0) {

            String absolutePath = cp.getInputTreatment();

            try {
            if (cp.getRecordDirectory() != null) {
                    absolutePath = Recorder.getAbsolutePath(cp.getRecordDirectory(),
                    cp.getInputTreatment());
            }

                if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("Call " + cp
                + " New input treatment:  " + absolutePath);
                }

            synchronized (this) {
                    new InputTreatment(this, absolutePath,
                    0, conferenceMediaInfo.getSampleRate(),
                    conferenceMediaInfo.getChannels());
            }
            } catch (IOException e) {
                e.printStackTrace();

            Logger.println("MemberReceiver:  Invalid input treatment "
                + absolutePath + ":  " + e.getMessage());

                callHandler.cancelRequest("Invalid input treatment "
                + absolutePath + ":  " + e.getMessage());
                return;
            }
        }

        String forwardingCallId = cp.getForwardingCallId();

        if (forwardingCallId != null) {
            CallHandler forwardingCall = CallHandler.findCall(forwardingCallId);

            if (forwardingCall == null) {
            Logger.println("Invalid forwardingCallId:  " + forwardingCallId);
            callHandler.cancelRequest("Invalid forwardingCallId:  "
                + forwardingCallId);
            return;
            }

            ConferenceMember m = forwardingCall.getMember();

            m.getMemberReceiver().addForwardMember(member.getMemberSender());

            /*
             * If the source of the data is an input treatment, there
             * is no need to have the forwarding call receive data
             * from the remote side.
             */
            if (cp.getInputTreatment() != null) {
            m.setConferenceMuted(true);
            }
        }
    }

    initializationDone = true;

    Logger.writeFile("Call " + cp  + " MemberReceiver initialization done...");
    }

    public void addForwardMember(MemberSender memberSender) {
    synchronized (forwardMemberList) {
        if (forwardMemberList.contains(memberSender)) {
        Logger.println("Already forwarding data to " + memberSender);
        return;
        }

        forwardMemberList.add(memberSender);
    }
    }

    public void removeForwardMember(MemberSender memberSender) {
    synchronized (forwardMemberList) {
        forwardMemberList.remove(memberSender);
    }
    }

    public void treatmentDoneNotification(TreatmentManager treatmentManager) {
    treatmentDoneNotification(treatmentManager.getId());
    }

    public void treatmentDoneNotification(String treatment) {
        synchronized (conferenceManager) {
            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Input Treatment done " + treatment);
        }

        if (callHandler == null) {
        Logger.println("Call " + cp + " treatment done but no call handler.");
        return;
        }

            CallEvent callEvent = new CallEvent(CallEvent.TREATMENT_DONE);
            callEvent.setTreatmentId(treatment);
            callHandler.sendCallEventNotification(callEvent);
        }
    }

    public void restartInputTreatment() {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Call " + cp + " restartInputTreatment "
        + cp.getInputTreatment());
    }

    if (whisperGroup == null) {
        Logger.println("Call " + cp + " restartInputTreatment wg is null!");
        return;
    }

    synchronized (this) {
        if (cp.getInputTreatment() != null &&
            cp.getInputTreatment().length() > 0) {

        try {
            MediaInfo conferenceMediaInfo =
            conferenceManager.getMediaInfo();

                String absolutePath = cp.getInputTreatment();

            if (cp.getRecordDirectory() != null) {
                    absolutePath = Recorder.getAbsolutePath(
                cp.getRecordDirectory(), cp.getInputTreatment());
            }

                if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Call " + cp + " new input treatment "
                + absolutePath);
            }

                new InputTreatment(this, absolutePath, 0,
            conferenceMediaInfo.getSampleRate(),
                        conferenceMediaInfo.getChannels());
            } catch (IOException e) {
                Logger.println(cp + " Unable to restart input treatment "
            + cp.getInputTreatment() + ": " + e.getMessage());
                callHandler.cancelRequest(
            "unable to restart input treatment "
                + cp.getInputTreatment() + ": " + e.getMessage());
            }
        }
    }
    }

    private InputTreatment iTreatment;

    private Object lock = new Object();

    class InputTreatment extends Thread {

    TreatmentManager treatmentManager;
    TreatmentDoneListener treatmentDoneListener;
    private String treatment;
    private int repeatCount;
    private int sampleRate;
    private int channels;

    public InputTreatment(TreatmentDoneListener treatmentDoneListener,
        String treatment, int repeatCount, int sampleRate,
        int channels) {

        this.treatmentDoneListener = treatmentDoneListener;
            this.treatment = treatment;
            this.repeatCount = repeatCount;
            this.sampleRate = sampleRate;
            this.channels = channels;

        start();

    }

    public TreatmentManager getTreatmentManager() {
        return treatmentManager;
    }

    public void done() {
        if (treatmentManager == null) {
        return;
        }

        treatmentManager.removeTreatmentDoneListener(treatmentDoneListener);

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Calling stoptreatment for " + treatmentManager);
        }

        treatmentManager.stopTreatment();
    }

        public void run() {
        synchronized (lock) {
            if (iTreatment != null) {
            if (iTreatment.getTreatmentManager() != null) {
                    if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.println("Stopping previous input treatment "
                    + iTreatment.getTreatmentManager().getId());
            }
                iTreatment.done();
            } else {
                try {
                synchronized (iTreatment) {
                        iTreatment.wait();
                }
                        if (Logger.logLevel >= Logger.LOG_INFO) {
                        Logger.println(
                        "Stopping previous input treatment after waiting "
                        + iTreatment.getTreatmentManager().getId());
                }

                    iTreatment.done();
                } catch (InterruptedException e) {
                }
            }
            }

            iTreatment = this;
        }

        Logger.println("Trying to create treatment manager for "
        + treatment);

        try {
            treatmentManager = new TreatmentManager(
            treatment, repeatCount, sampleRate, channels);
        } catch (IOException e) {
                Logger.println("MemberReceiver:  Invalid input treatment "
                    + treatment + ":  " + e.getMessage());

                callHandler.cancelRequest("Invalid input treatment "
                    + treatment + ":  " + e.getMessage());

        synchronized (this) {
            notifyAll();
        }

        return;
        }

        treatmentManager.addTreatmentDoneListener(treatmentDoneListener);

        if (whisperGroup != null) {
        synchronized (whisperGroup) {
            inputTreatment = treatmentManager;
        }
        } else {
        inputTreatment = treatmentManager;
        }

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Created treatment manager for "
            + treatmentManager.getId());
        }

        synchronized (this) {
        notifyAll();
        }
    }
    }

    public void startInputTreatment(String treatment) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Call " + cp + " startInputTreatment");
    }

    cp.setPhoneNumber(treatment);
    cp.setInputTreatment(treatment);

    restartInputTreatment();
    }

    public void stopInputTreatment() {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Call " + cp + " stopInputTreatment");
    }

    synchronized (whisperGroup) {
        if (inputTreatment != null) {
            inputTreatment.stopTreatment();
        }
    }
    }

    private boolean datagramChannelRegistered;

    public SelectionKey register(Selector selector) throws IOException {
    try {
        selectionKey =
        datagramChannel.register(selector, SelectionKey.OP_READ);
    } catch (ClosedChannelException e) {
        callHandler.cancelRequest("register failed, channel closed!");
        throw new IOException("register failed, channel closed!");
    } catch (Exception e) {
        Logger.println("register exception! " + e.getMessage());
        throw new IOException("register exception!  " + e.getMessage());
    }

    datagramChannelRegistered = true;
    selectionKey.attach(this);
    return selectionKey;
    }

    public void unregister() {
    if (selectionKey != null) {
        selectionKey.cancel();
        selectionKey = null;
    }
    }
    public int getLinearBufferSize() {
    return RtpPacket.HEADER_SIZE + packet.getDataSize();
    }

    /*
     * Reset detectors if no packets are received.
     * Cancel call if no RTP or RTCP packets are received.
     */
    private int noDataCount;

    public boolean checkPacketsReceived() {
    if (callCancelled) {
        return false;
    }

    if (callIsDead()) {
        return false;
    }

        /*
         * 3 packets should be enough for the speech detector
         * and dtmf detector to know someone isn't speaking.
     * After we've given the detector 60 ms of silence pakcets,
     * we don't need to send it any more packets.
         */
        int last = lastMediaPacketsReceived;

        lastMediaPacketsReceived = mediaPacketsReceived;

        if (last != mediaPacketsReceived) {
            noDataCount = 0;
            return true;
        }

        noDataCount++;

        if (noDataCount != 3) {
             return true;
        }

    /*
     * Reset previous samples in sampleRateConverter
     */
    if (inSampleRateConverter != null) {
        inSampleRateConverter.reset();
    }

        /*
         * we haven't received any data for 3 packet periods (60 ms).
         * Make sure the speech detector knows we're not talking
         */
    if (speechDetector != null) {
            if (speechDetector.reset()) {
            callHandler.speakingChanged(false);
        }
    }

        /*
         * Make sure the dtmf detector knows there's silence
         */
    if (dtmfDecoder != null) {
            String dtmfKeys = dtmfDecoder.noDataReceived();

        if (dtmfKeys != null) {
        Logger.println("silence.  dtmf " + dtmfKeys);
        processDtmfKeys(dtmfKeys);
        }
    }

    return true;
    }

    private boolean callCancelled;

    private boolean callIsDead() {

    if (cp.getProtocol() != null && ("WebRtc".equals(cp.getProtocol()) || "Rtmfp".equals(cp.getProtocol()) || "Speaker".equals(cp.getProtocol())))
    {
        return false;
    }

    if (RtpSocket.getRtpTimeout() == 0) {
        return false;	// no timeout
    }

    String phoneNumber = cp.getPhoneNumber();

    if (phoneNumber != null && phoneNumber.indexOf("6666@") >= 0) {
        return false;  // don't timeout calls to the bridge.
    }

    /*
     * Only do this for sip calls.  For some reason, other calls
     * are getting timed out if this "if" is removed.
     */
    if (cp.isDistributedBridge() == true || phoneNumber.indexOf("sip:") < 0 || phoneNumber.indexOf("tel:") < 0) {
        return false;
    }

    long rtpElapsed;

    if (timeCurrentPacketReceived == 0) {
        rtpElapsed = 0;
    } else {
        rtpElapsed = (System.currentTimeMillis() - timeCurrentPacketReceived) / 1000;
    }

    long rtcpElapsed = rtcpReceiver.secondsSinceLastReport(member.getRtcpAddress());

    if (rtcpElapsed < RtpSocket.getRtpTimeout() || rtpElapsed < RtpSocket.getRtpTimeout()) {
        return false;
    }

    //if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp
                + " time since last RTCP report " + rtcpElapsed
        + " time since last RTP packet received " + rtpElapsed);
        //}

    /*
         * We have not received an RTP or RTCP packet in quite some
         * time.  Assume the call is dead.
     *
     * XXX There is a gateway (10.6.4.61)
     * which doesn't send RTCP packets.
     * For now, we'll only timeout calls with "sip:" in
     * the phone number.
         */
        Logger.println("Call " + cp
            + ":  Timeout, cancelling the call...");
        callHandler.cancelRequest("call timeout, no keepalive received");
    callCancelled = true;

    return true;
    }

    /**
     * Handle data which the ConferenceReceiver has sent to us and
     * add it to the current whisper group mix.
     *
     * There is a single ConferenceReceiver to which all CallParticipants
     * send data.  The ConferenceReceiver dispatches data to
     * the appropriate conference member by calling our receive method.
     *
     * The job of the conference member is to receive packets from
     * the call participant and add them to the appropriate whisper group.
     *
     * Each MemberReceiver keeps it's own list of data it has contributed
     * to the mix's linearMixBuffers.
     *
     * When a member receives data from the CallParticipant, the member
     * adds an element of int[] to its own list.  The member then adds
     * the data to the current whisper group.  The element index
     * of the member's list is the same index used for the whisper group's
     * linearMixBuffers.
     *
     * Adding and removing list elements is done synchronized on whisperGroup.
     */
    public void setDropPackets(int dropPackets) {
    this.dropPackets = dropPackets;
    }

    /*
     * With deferMixing set to true, when a packet arrives it is inserted
     * in the jitter buffer and mixing is done in saveCurrentContribution().
     * It may be better to do the mixing when the packet is received so
     * the work is done by a thread separate from the sender thread.
     */
    private static boolean deferMixing = false;
    private int forcedToDeferMixing;

    public static void deferMixing(boolean deferMixing) {
    MemberReceiver.deferMixing = deferMixing;
    }

    public static boolean deferMixing() {
    return deferMixing;
    }

    private void forwardData(int[] data) {
    for (MemberSender memberSender : forwardMemberList) {
         if (Logger.logLevel == -88) {
        Logger.println("Forwarding " + data.length + " to "
            + memberSender);
         }

         if (memberSender.memberIsReadyForSenderData()) {
             memberSender.sendData(data);
         }
    }
    }

    public void receive(InetSocketAddress fromAddress, byte[] receivedData, int length) {

    member.getMemberSender().setSendAddress(fromAddress);

    if (packet == null) return;
    /*
     * receivedData has a 12 byte RTP header at the beginning
     * and length includes the RTP header.
     */
    timeCurrentPacketReceived = System.currentTimeMillis();

    packetsReceived++;

    if (packetsReceived == 1) {
        Logger.println("Call " + cp + " got first packet, length "
        + length);

        packet.setBuffer(receivedData);

        /*
         * TODO:  Get the synchonization source for this call.
         */
    }

    if (cp.getInputTreatment() != null) {
        return;
    }

    if (dropPackets != 0) {
        if ((packetsReceived % dropPackets) == 0) {
        return;
        }
    }

    /*
     * For debugging
     */
    if (traceCall || Logger.logLevel == -11) {
        Logger.writeFile("Call " + cp + " got packet, len " + length);
    }

    /*
     * Decrypt data if it's encrypted
     */
    long start = 0;

    if (decryptCipher != null) {
        if (traceCall || Logger.logLevel == -1) {
            start = System.nanoTime();
        }

        receivedData = decrypt(receivedData);

        if (traceCall || Logger.logLevel == -1) {
        Logger.println("Call " + cp + " decrypt time "
            + ((System.nanoTime() - start) / 1000000000.)
            + " seconds");
        }
    }

    recordPacket(receivedData, length);

    packet.setBuffer(receivedData);
    packet.setLength(length);

    byte payload = packet.getRtpPayload();

    int elapsedTime = (int)
        (timeCurrentPacketReceived - timePreviousPacketReceived);

    if (gotComfortPayload || packetsReceived == 1) {
        /*
         * We don't want to count the time when the remote stopped
         * sending to us.
         */
        packet.setMark();    // make sure MARK bit is set

        if (gotComfortPayload) {
            gotComfortPayload = false;

            if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Call " + cp
                    + "  received packet after comfort payload");
            }
        }
    }

    if (packet.isMarkSet() == true) {
        elapsedTime = RtpPacket.PACKET_PERIOD;
    }

        totalTime += elapsedTime;

    synchronized (jitterManager) {
        /*
         * Insert place holder for this packet
         */
            jitterManager.insertPacket(packet.getRtpSequenceNumber(),
        elapsedTime);
    }

    int rtpTimestampAdjustment = length - RtpPacket.HEADER_SIZE;

    if (payload == RtpPacket.COMFORT_PAYLOAD || payload == 19) {
        /*
         * Asterisk seems to have a bug in which the bridge offers
         * 13 decimal as the comfort payload and asterisk replies with
         * 13 hex (19 decimal).
         * For now, we'll treat 19 as the comfort noise payload as well.
         */
        receiveComfortPayload(packet, elapsedTime);

        if (inSampleRateConverter != null) {
        inSampleRateConverter.reset();
        }

        if (speechDetector != null) {
        if (speechDetector.isSpeaking()) {
                callHandler.speakingChanged(false);
        }
            speechDetector.reset();
        }
    } else if (payload == 18) {
        /*
         * We sometimes get payload 18 which is undefined according to
         * the RFC.  The data looks like audio data.
         * But for now, we just drop the packet.
         */
         Logger.error("Call " + cp + " unexpected payload " + payload
        + " dropping packet ");

         Util.dump("bad payload 18 data", packet.getData(), 0, 16);
    } else if (payload == myMediaInfo.getPayload()) {
        if (traceCall || Logger.logLevel == -1) {
        start = System.nanoTime();
        }

        try {
            rtpTimestampAdjustment = receiveMedia(receivedData, length);
        } catch (SpeexException e) {
                Logger.println("speex decorder failed: " + e.getMessage());
                e.printStackTrace();
            callHandler.cancelRequest("Call " + cp + e.getMessage());
        return;
        }

            if (traceCall || Logger.logLevel == -1) {
                Logger.println("Call " + cp + " receiveMedia time "
                    + ((System.nanoTime() - start) / 1000000000.)
            + " seconds");
            }

        int processTime = (int)
        (System.currentTimeMillis() - timeCurrentPacketReceived);

        timeToProcessMediaPackets += processTime;
        mediaPacketsReceived++;
    } else if (payload != 0 && payload == telephoneEventPayload) {
        if (cp.ignoreTelephoneEvents() == false) {
            receiveDtmfPayload(packet);
        }
    } else {
        if ((badPayloads % 1000) == 0) {
        badPayloads++;

            Logger.error("Call " + cp + " unexpected payload " + payload
            + " length " + length);
            Util.dump("unexpected payload", receivedData, 0, 16);
        }

        if (badPayloads >= 1000 && mediaPacketsReceived == 0) {
        callHandler.cancelRequest("Call " + cp
            + " bad media payload being sent by call");
        }
    }

    packet.updateRtpHeader(rtpTimestampAdjustment);
    timePreviousPacketReceived = timeCurrentPacketReceived;
    }

    private void receiveComfortPayload(RtpReceiverPacket packet,
        int elapsedTime) {

    comfortNoiseLevel = packet.getComfortNoiseLevel();

    if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Call " + cp
            + ":  received comfort payload, level " + comfortNoiseLevel
        + " sequence " + packet.getRtpSequenceNumber());
    }

    comfortPayloadsReceived++;

    if (Logger.logLevel >= Logger.LOG_DEBUG) {
        log(packet);
    }
    }

    private int receiveMedia(byte[] receivedData, int length)
        throws SpeexException {

    long start = 0;

    int[] data = decodeToLinear(receivedData, length);

    if (inputVolume != 1.0) {
        callHandler.getMember().adjustVolume(data, inputVolume);
    }

    //Logger.println("Call " + cp  + " receiveMedia length " + length + " decoded int length " + data.length);

    int numberOfSamples = data.length;

        if (myMediaInfo.getEncoding() == RtpPacket.PCMU_ENCODING) {
        /*
         * The cisco gateway often gives us short packets
         * right before a comfort payload
         */
        numberOfSamples = length - RtpPacket.HEADER_SIZE;
    }

        if (traceCall || Logger.logLevel == -1) {
            start = System.nanoTime();
        }

    if (inSampleRateConverter != null) {
            if (traceCall || Logger.logLevel == -1) {
                start = System.nanoTime();
            }

        /*
         * XXX We never downsample here because the bridge
         * will never advertise a sample rate higher than
         * that of the conference.
         */
        try {
            data = inSampleRateConverter.resample(data);
        //Logger.println("length after resample " + data.length);
        } catch (IOException e) {
        Logger.println("Call " + cp    + " can't resample received data " + e.getMessage());
        callHandler.cancelRequest("Call " + cp
            + "can't resample received data " + e.getMessage());

        return 0;
        }

            if (traceCall || Logger.logLevel == -1) {
                Logger.println("Call " + cp + " resample time "
                    + ((System.nanoTime() - start) / 1000000000.)
            + " seconds");
            }
    }

    if (traceCall || Logger.logLevel == -1) {
        start = System.nanoTime();
    }

    /*
     * If there are calls to other bridges which need the data
     * from this member, then we send that data right now.
     * This reduced latency because this is before we put the
     * data in the jitter buffer.
     */
    forwardData(data);

    /*
     * data is a int[] with no RTP header
     */
    handleMedia(data, packet.getRtpSequenceNumber());

    if (traceCall || Logger.logLevel == -1) {
        Logger.println("Call " + cp + " handleMedia time "
        + ((System.nanoTime() - start) / 1000000000.)
        + " seconds");
    }

    if (Logger.logLevel >= Logger.LOG_DEBUG) {
        log(packet);
    }

    return numberOfSamples;
    }

    private int[] decodeToLinear(byte[] receivedData, int length) throws SpeexException
    {
        /*
         * receivedData has the 12 byte RTP header.
         */

        int[] data = new int[myMediaInfo.getSamplesPerPacket()];

        long start = 0;

        if (myMediaInfo.getEncoding() == RtpPacket.PCMU_ENCODING)
        {
            if (traceCall || Logger.logLevel == -1)
            {
                start = System.nanoTime();
            }

            /*
             * Convert ulaw data to linear. length is the ulaw
             * data length plus the RTP header length.
             *
             * If the incoming packet is shorter, than we expect,
             * the rest of <data> will be filled with 0 * which is PCM_SILENCE.
             */

            AudioConversion.ulawToLinear(receivedData, RtpPacket.HEADER_SIZE, length - RtpPacket.HEADER_SIZE, data);

            if (length < 172 && Logger.logLevel >= Logger.LOG_DETAIL) {
                Logger.println("Call " + cp + " received short packet "	+ length);
            }

            if (traceCall || Logger.logLevel == -1) {
                Logger.println("Call " + cp + " ulawToLinear time " + ((System.nanoTime() - start) / 1000000000.)   + " seconds");
            }

        } else if (myMediaInfo.getEncoding() == RtpPacket.PCM_ENCODING) {

            int inputOffset = RtpPacket.HEADER_SIZE;
            int inputLength = length - RtpPacket.HEADER_SIZE;

            int frameSizeInSamplesPerChannel = Opus.decoder_get_nb_samples(opusDecoder, receivedData, inputOffset, inputLength);

            if (frameSizeInSamplesPerChannel > 1)
            {
                int frameSizeInBytes = outputFrameSize * opusChannels * frameSizeInSamplesPerChannel;

                byte[] output = new byte[frameSizeInBytes];
                frameSizeInSamplesPerChannel = Opus.decode(opusDecoder, receivedData, inputOffset, inputLength, output, 0, frameSizeInSamplesPerChannel, 0);
                data = AudioConversion.bytesToLittleEndianInts(output);
            }


        } else if (myMediaInfo.getEncoding() == RtpPacket.SPEEX_ENCODING) {

            if (traceCall || Logger.logLevel == -1) {
                start = System.nanoTime();
            }

            data = speexDecoder.decodeToIntArray(receivedData, RtpPacket.HEADER_SIZE, length - RtpPacket.HEADER_SIZE);

            if (traceCall || Logger.logLevel == -1)
            {
                Logger.println("Call " + cp + " speex decode time " + ((System.nanoTime() - start) / 1000000000.) + " seconds");
            }

        } else {
            AudioConversion.bytesToInts(receivedData, RtpPacket.HEADER_SIZE,
            length - RtpPacket.HEADER_SIZE, data);
        }

        return data;
    }

    public synchronized void handleVP8Video(RTPPacket videoPacket)
    {
        ArrayList<ConferenceMember> memberList = conferenceManager.getMemberList();

        for (ConferenceMember member : memberList)
        {
            if (member == this.member) {
                continue;
            }

            member.getMemberSender().handleVP8Video(videoPacket);
        }
    }
    /*
     * data is a int[] with no RTP data and has been decoded
     * and resampled to the conference sample rate.
     */

    public synchronized void handleWebRtcMedia(int[] data, short sequenceNumber)
    {
        if (readyToReceiveData() == false) return;

        timeCurrentPacketReceived = System.currentTimeMillis();
        int elapsedTime = (int) (timeCurrentPacketReceived - timePreviousPacketReceived);

        synchronized (jitterManager) {
                jitterManager.insertPacket(sequenceNumber, elapsedTime);
        }

        if (inputVolume != 1.0) {
            callHandler.getMember().adjustVolume(data, inputVolume);
        }

        handleMedia(data, sequenceNumber);

        timePreviousPacketReceived = timeCurrentPacketReceived;
    }


    private void handleMedia(int[] data, short sequenceNumber)
    {

    if (dtmfDecoder != null) {
        if (checkDtmf(data) == true) {
        if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.writeFile("Call " + cp
            + " checkDtmf returned true, data length "
            + data.length);
        }
        return;
        }
    }

    if (isMuted()) {
            if (speechDetector != null &&
                    cp.voiceDetectionWhileMuted() == true) {

                /*
                 * Speech detection may be useful for PDA's in a
                 * conference room.  Even though you wouldn't want
                 * the voice from the PDA microphone to be added to the mix,
                 * it would be useful for members not in the conference room
                 * to know who is speaking.
                 */
                if (speechDetector.processData(data) == true) {
                    callHandler.speakingChanged(speechDetector.isSpeaking());
                }
        }
        return;
    }

    if (relayChannel != null)
    {
        try {
            relayChannel.pushReceiverAudio(data);
        } catch(Exception e) {}

        return;
    }


    long start = 0;

    if (traceCall || Logger.logLevel == -1) {
        start = System.nanoTime();
    }

    synchronized (whisperGroup) {
        if (traceCall || Logger.logLevel == -1) {
        Logger.println("Call " + cp + " handleMedia lock wait time "
            + ((System.nanoTime() - start) / 1000000000.)
            + " seconds");
        }

        if (joinConfirmationReceived == false) {
        /*
         * Drop this packet.  We're still waiting for confirmation.
         */
        return;
        }

        synchronized (jitterManager) {
            jitterManager.insertPacket(sequenceNumber, data);
        }

        if (deferMixing == false) {
        if (contributionValid) {
            forcedToDeferMixing++;
        } else {
                saveCurrentContribution();
        }
        }
    }

    if (speechDetector != null) {
        if (speechDetector.processData(data) == true) {
        callHandler.speakingChanged(speechDetector.isSpeaking());
        }
        }

    }

    private boolean checkDtmf(int[] data) {
    String dtmfKeys = dtmfDecoder.processData(data);

    if (CallHandler.dtmfSuppression() == true &&
            cp.dtmfSuppression() == true) {

        if (dtmfDecoder.dtmfDetected()) {
        if (isAutoMuted == false) {
            if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Call " + cp
                + " dtmf detected, setting automute ");
            }

            isAutoMuted = true;
            flushContributions();
        }
        } else {
        if (isAutoMuted == true) {
            if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
                Logger.println("Call " + cp + " automute now false");
            }
        }

        isAutoMuted = false;
        }
    }

    if (dtmfKeys != null) {
        processDtmfKeys(dtmfKeys);

        if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Call " + cp + " processed dtmf packet"
            + " with key " + dtmfKeys
            + " dtmfPackets " + dtmfPackets);
        }

        isAutoMuted = false;
        return true; 	// drop this packet
    }

    if (isAutoMuted) {
        return true;
    }

    return false;
    }

    /*
     * process dtmf payload
     */
    private long dtmfTimestamp = 0;

    private void receiveDtmfPayload(RtpReceiverPacket packet) {
    byte[] data = packet.getData();

    if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
        Util.dump("received telephoneEventPayload", data, 0, 16);
    }

    /*
     * First byte of data is the dtmf key
     */
    if (packet.isDtmfEndSet()) {
        /*
             * Very strange packets come from the Cisco gateway.
             * The first several have the end bit set followed
             * by a number which don't have the bit set.
             * Fortunately, all of the packets have the same timestamp
             * so we can filter on that.
             */
        if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
            Util.dump("Dtmf end set, ts " + Long.toHexString(dtmfTimestamp)
            + " pkt ts " + Long.toHexString(packet.getRtpTimestamp()),
            data, 0, 16);
        }

            if (dtmfTimestamp != packet.getRtpTimestamp()) {
                dtmfTimestamp = packet.getRtpTimestamp();

            /*
             * Key has been released
             * Now it's time to process the key
             */
        String dtmfKey = String.valueOf((int)data[RtpPacket.DATA]);

        if (data[RtpPacket.DATA] == 10) {
            dtmfKey = "*";
        } else if (data[RtpPacket.DATA] == 11) {
            dtmfKey = "#";
        }

            processDtmfKeys(dtmfKey);
        }
    } else {
        /*
         * Key is still pressed
         */
        if (traceCall || Logger.logLevel >= Logger.LOG_MOREINFO) {
            Util.dump("Got dtmf key payload key still pressed: ", data, 0, 16);
        }
        }
    }

    public static void setForwardDtmfKeys(boolean forwardDtmfKeys) {
    MemberReceiver.forwardDtmfKeys = forwardDtmfKeys;
    }

    public static boolean getForwardDtmfKeys() {
    return forwardDtmfKeys;
    }

    private ArrayList joinConfirmationListeners = new ArrayList();

    public void addJoinConfirmationListener(JoinConfirmationListener listener) {
    synchronized (joinConfirmationListeners) {
        joinConfirmationListeners.add(listener);
    }
    }

    public void removeJoinConfirmationListener(
            JoinConfirmationListener listener) {

    synchronized (joinConfirmationListeners) {
            joinConfirmationListeners.remove(listener);
        }
    }

    private void notifyJoinConfirmationListeners() {
    synchronized (joinConfirmationListeners) {
        for (int i = 0; i < joinConfirmationListeners.size(); i++) {
        JoinConfirmationListener listener = (JoinConfirmationListener)
            joinConfirmationListeners.get(i);

        listener.joinConfirmation();
        removeJoinConfirmationListener(listener);
        }
    }
    }

    /*
     * This is called when the dtmfDecoder detects a dtmf key.
     */
    private void processDtmfKeys(String dtmfKeys) {
    dtmfPackets++;

    Logger.println("Call " + cp + " got dtmf key " + dtmfKeys);

    if (joinConfirmationReceived == false) {
        if (!dtmfKeys.equals(joinConfirmationKey)) {
        Logger.println("Call " + cp
            + " jc false, dtmfKeys " + dtmfKeys
            + " != " + joinConfirmationKey);
        return;
        }

        joinConfirmationReceived = true;
        readyToReceiveData = true;
        notifyJoinConfirmationListeners();
        Logger.println("Call " + cp + " join confirmation received");

        playJoinTreatment();
    } else {
        /*
         * If the member is whispering in a whisper group
         * forward the dtmf key to the other members.
         *
         * This is intended for outgoing calls so that
         * someone can call AT&T conferencing for example,
         * and enter the meeting code.
         */
        if (forwardDtmfKeys == true) {
        if (whisperGroup != conferenceWhisperGroup) {
            Logger.writeFile("Call " + cp
                + " Forwarding dtmf key " + dtmfKeys);

            whisperGroup.forwardDtmf(this, dtmfKeys);
        }
        }
    }

    if (cp.dtmfDetection() == false) {
        /*
         * We enabled dtmf detection only so that the member could
         * confirm that it wants to join the conference.  Once confirmed,
         * dtmf detection is disabled only detection was explicitly enabled.
         */
        dtmfDecoder = null;
    }

    callHandler.dtmfKeys(dtmfKeys);
    }

    /**
     * Play audio treatment to all conference members indicating that
     * a member has joined the conference.
     */
    private void playJoinTreatment() {
        String joinTreatment;

        if ((joinTreatment = cp.getConferenceJoinTreatment()) != null) {
            Logger.writeFile("Call " + cp
                + ":  playing conference join treatment " + joinTreatment);

            try {
                conferenceManager.addTreatment(joinTreatment);
            } catch (IOException e) {
                Logger.println("Call " + cp
                    + " failed to start join treatment " + joinTreatment);
            }
        }
    }

    /*
     * Flush contributions to suppress dtmf sounds.
     * This doesn't work very well unless the buffers for the dtmf
     * sounds are ahead of the sender.  It takes 40ms of data to detect
     * a dtmf key so it's quite possible the first 20ms have already been
     * sent out.
     */
    public void flushContributions() {
    if (whisperGroup == null) {
        return;
    }
    }

    public void setInputVolume(double inputVolume) {
    this.inputVolume = inputVolume;
    }

    public double getInputVolume() {
    return inputVolume;
    }

    private TreatmentManager inputTreatment;

    private int[] previousContribution;
    private int[] currentContribution;
    private boolean contributionValid = false;

    public String getSourceId() {
    return cp.getCallId();
    }

    public boolean contributionIsInCommonMix() {
    return whisperGroup != null && whisperGroup.hasCommonMix();
    }

    public int[] getPreviousContribution() {
    return previousContribution;
    }

    public int[] getCurrentContribution() {
    return currentContribution;
    }

    public void invalidateCurrentContribution() {
        synchronized (whisperGroup) {
        previousContribution = currentContribution;
        currentContribution = null;
        contributionValid = false;
    }
    }

    public void saveCurrentContribution() {
    if (readyToReceiveData == false || whisperGroup == null) {
        previousContribution = null;
        currentContribution = null;
        return;
    }

        synchronized (whisperGroup) {
        if (contributionValid) {
        return;
        }

        currentContribution = null;

        contributionValid = true;

        if (inputTreatment == null) {
            synchronized (jitterManager) {
                try {
                    JitterObject jo = jitterManager.getFirstPacket();

                    currentContribution = (int[]) jo.data;
                } catch (NoSuchElementException e) {
                }
        }
        } else {
        /*
         * If there's an input treatment, there's no endpoint
         * and therefore no member contribution other than
         * the input treatment
         */
        inputTreatment.saveCurrentContribution();

        currentContribution = inputTreatment.getCurrentContribution();

        if (currentContribution == null) {
            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Call " + cp
                + " input treatment returned null");
            }

                inputTreatment = null;
        } else {
            forwardData(currentContribution);

            if (inputVolume != 1) {
                callHandler.getMember().adjustVolume(currentContribution, inputVolume);
            }
        }

        if (speechDetector != null) {
            if (currentContribution == null) {
            if (speechDetector.reset() == true) {
                    callHandler.speakingChanged(false);
            }
            } else {
                    if (speechDetector.processData(currentContribution) ==
                true) {

                            boolean isSpeaking = speechDetector.isSpeaking();

                            callHandler.speakingChanged(isSpeaking);

                            if (isSpeaking == false) {
                                currentContribution = null;
                            }
            }
            }
            }
        }

        if (currentContribution != null) {
            /*
             * Add this packet's data to the appropriate whisperGroup
             */
        if (whisperGroup.hasCommonMix()) {
                whisperGroup.addToLinearDataMix(currentContribution,
            doNotRecord());
        }

                recordAudio(currentContribution, currentContribution.length);

            if (Logger.logLevel == -89) {
            Logger.println("Call " + cp
            + " MemberReceiver contributed");
            }
            }
    }
    }

    private void log(RtpReceiverPacket rtpPacket) {
    long now = System.currentTimeMillis();

    long rtpTimestampChange = rtpPacket.getRtpTimestamp() -
        previousRtpTimestamp;

    previousRtpTimestamp = rtpPacket.getRtpTimestamp();

    String summary = "";
    String flags = "";
    String badTime = " ";
    String badTimestamp = " ";

    if (rtpPacket.isMarkSet()) {
        flags = "MARK ";
    } else {
        if (packetsReceived > 1) {
            if (now - timePreviousPacketReceived < 15) {
                badTime = "-";
            summary = "!";
            } else if (now - timePreviousPacketReceived > 25) {
                badTime = "+";
            summary = "!";
            }

            if (rtpTimestampChange > myMediaInfo.getSamplesPerPacket()) {
                    badTimestamp = ">";
            summary = "!";
                } else if (rtpTimestampChange < myMediaInfo.getSamplesPerPacket()) {
                    badTimestamp = "<";
            summary = "!";
                }
        }
    }

    if (rtpPacket.getRtpPayload() == RtpPacket.COMFORT_PAYLOAD) {
        flags += "COMFORT ";
    }

    String timestamp = Integer.toHexString(
                (int)(rtpPacket.getRtpTimestamp() & 0xffffffff));

    if (timestamp.length() != 8) {
        timestamp += "       ";		// for alignment
    }

        Logger.writeFile("R    "
        + (now - timePreviousPacketReceived) + badTime
        + "\t" + Integer.toHexString(
        (int)(rtpTimestampChange & 0xffffffff))
        + badTimestamp
            + "\t" + Integer.toHexString(rtpPacket.getRtpSequenceNumber())
            + "\t" + timestamp
        + "\t" + flags + cp + " R" + summary);
    }

    /**
     * Member is leaving a conference.  Print statistics for the member.
     */
    public void end() {
        if (done) {
            return;
        }

        done = true;

        if (speechDetector != null && speechDetector.isSpeaking()) {
                callHandler.speakingChanged(false);
        }

        synchronized (recordingLock) {
                if (recorder != null) {
                    recorder.done();
                    recorder = null;
                }
        }

        readyToReceiveData = false;

        if (datagramChannelRegistered && datagramChannel != null) {
            try {
                datagramChannel.close();

            if (Logger.logLevel >= Logger.LOG_DETAIL) {
                Logger.println("Call " + cp + " closed datagramChannel "
                    + datagramChannel);
            }
            datagramChannel = null;
            } catch (IOException e) {
                Logger.println("Call " + cp
                + " exception closing datagram channel " + e.getMessage());
            }
        } else {
            Logger.println("Call " + cp + " not closing datagramChannel");
        }

        if (joinConfirmationReceived == true) {
            String leaveTreatment;

                /**
                 * Play audio treatment to all conference members indicating that
                 * a member has left the conference.
                 */
            if ((leaveTreatment = cp.getConferenceLeaveTreatment()) != null) {
            try {
                    conferenceManager.addTreatment(leaveTreatment);
            } catch (IOException e) {
                Logger.println("Call " + cp
                + " failed to start leave treatment " + leaveTreatment);
            }
            }
        }

        if (opusDecoder != 0)
        {
            Opus.decoder_destroy(opusDecoder);
            opusDecoder = 0;
        }
    }

    public void printStatistics() {
    if (conferenceManager == null) {
        return;
    }

    synchronized (conferenceManager) {
        if (packet == null) {
        return;
        }

            Logger.writeFile("Call " + cp + ":  "
            + packetsReceived + " packets received");
            Logger.writeFile("Call " + cp + ":  "
            + packet.getShortPackets() + " short packets");
            Logger.writeFile("Call " + cp + ":  "
            + packetsDropped + " packets dropped");
            Logger.writeFile("Call " + cp + ":  "
            + packet.getOutOfSequencePackets()
        + " out of sequence packets");
            Logger.writeFile("Call " + cp + ":  "
        + packet.getWrongRtpTimestamp() + " incorrect RTP timestamp");
            Logger.writeFile("Call " + cp + ":  " + comfortPayloadsReceived
            + " comfort payloads received");
        Logger.writeFile("Call " + cp + ":  Forced to defer mixing "
        + forcedToDeferMixing);

        if (packetsReceived != 0) {
                Logger.writeFile("Call " + cp + ":  "
            + "total time " + totalTime);

                Logger.writeFile("Call " + cp + ":  "
                + ((float)totalTime / (double)packetsReceived)
                + " average milliseconds between receiving packets");

        Logger.writeFile("Call " + cp + ":  "
            + ((float)timeToProcessMediaPackets /
            (float)mediaPacketsReceived)
            + " average milliseconds to process a media packet");
        }

        Logger.writeFile("Call " + cp + ":  "
        + decryptCount + " packets decrypted");

        if (decryptCount != 0) {
        Logger.writeFile("Call " + cp + ":  "
            + (((float)decryptTime / (float)decryptCount) / 1000)
            + " microseconds average per decrypt");
        }

        if (speexDecoder != null) {
        int decodes = speexDecoder.getDecodes();
        long decodeTime = speexDecoder.getDecodeTime();

            if (decodes > 0) {
            Logger.writeFile("Call " + cp + ":  "
                + "Average Speex decode time "
                        + (((float)decodeTime / decodes) / 1000000000.)
            + " seconds");
        }
        }

        if (inSampleRateConverter != null) {
        inSampleRateConverter.printStatistics();
        }

        if (jitterManager != null) {
        synchronized (jitterManager) {
                jitterManager.printStatistics();
        }
        }

        Logger.writeFile("");

        if (dtmfDecoder != null) {
            dtmfDecoder.printStatistics();
                Logger.writeFile("");
        }

        if (speechDetector != null) {
        speechDetector.printStatistics();
                Logger.writeFile("");
        }

        Logger.flush();
    }
    }

    public boolean joinConfirmationReceived() {
    return joinConfirmationReceived;
    }

    /**
     * Indicate whether or not this member is ready to receive data
     * from the ConferenceReceiver thread.
     */
    public boolean readyToReceiveData() {
    if (initializationDone == false) {
        return false;
    }

    /*
     * We have to allow data in so we can detect a dtmf key for
     * join confirmation.
     */
    if (joinConfirmationReceived == false) {
        return true;
    }

        if (traceCall) {
        if (callHandler.isCallEstablished() == false
                || readyToReceiveData == false) {

            Logger.writeFile("readyToReceiveData " + readyToReceiveData +
            " established " + callHandler.isCallEstablished());
        }
    }

    return callHandler.isCallEstablished() && readyToReceiveData;
    }

    private boolean isMuted() {
    if (whisperGroup == null) {
        return true;
    }

        if (whisperGroup != conferenceWhisperGroup) {
        if (traceCall) {
        if (cp.isMuteWhisperGroup()) {
            Logger.writeFile("Call " + cp + " whispergroup muted");
        }
        }
        return cp.isMuteWhisperGroup();
    }

    if (traceCall) {
            if (isAutoMuted || cp.isMuted() || cp.isConferenceMuted() ||
        cp.isConferenceSilenced()) {
        Logger.writeFile("Call " + cp + " isAutoMuted " + isAutoMuted
            + " isMuted " + cp.isMuted()
            + " isConf muted " + cp.isConferenceMuted()
            + " isConf sileneced " + cp.isConferenceSilenced());
        }
    }

        return isAutoMuted || cp.isMuted() || cp.isConferenceMuted() ||
        cp.isConferenceSilenced();
    }

    /**
     * Mute or unmute a member
     *
     * @param isMuted boolean true if member should be muted, false otherwise.
     */
    public void setMuted(boolean isMuted) {
    if (traceCall || Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp + " mute is now " + isMuted);
    }

    cp.setMuted(isMuted);

    if (speechDetector == null) {
        return;
    }

    if (isMuted) {
        if (speechDetector.isSpeaking()) {
                callHandler.speakingChanged(false);
        }
    }

        speechDetector.reset();
    }

    /**
     * Mute or unmute a member from a whisper group
     *
     * @param isMuted boolean true if member should be muted, false otherwise.
     */
    public void setMuteWhisperGroup(boolean isMuteWhisperGroup) {
        if (traceCall || Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp + " muteWhisperGroup is now "
        + isMuteWhisperGroup);
        }

        cp.setMuteWhisperGroup(isMuteWhisperGroup);


        if (isMuteWhisperGroup) {
            synchronized (whisperGroup) {
        flushContributions();
        }
    }

        if (speechDetector == null) {
            return;
        }

        if (isMuteWhisperGroup) {
        if (speechDetector.isSpeaking()) {
                callHandler.speakingChanged(false);
        }
        }

        speechDetector.reset();
    }

    public void setPowerThresholdLimit(double powerThresholdLimit) {
        if (speechDetector == null) {
            Logger.println("Can't set powerThresholdLimit because there is no "
                + "speech detector");
            return;
        }

        speechDetector.setPowerThresholdLimit(powerThresholdLimit);
    }

    public void setMinJitterBufferSize(int minJitterBufferSize) {
    if (jitterManager == null) {
        return;
    }

    jitterManager.setMinJitterBufferSize(minJitterBufferSize);
    }

    public void setMaxJitterBufferSize(int maxJitterBufferSize) {
    if (jitterManager == null) {
        return;
    }

    jitterManager.setMaxJitterBufferSize(maxJitterBufferSize);
    }

    public void setPlcClassName(String plcClassName) {
    jitterManager.setPlcClassName(plcClassName);
    }

    public String getPlcClassName() {
    return jitterManager.getPlcClassName();
    }

    public InetSocketAddress getReceiveAddress() {
    return new InetSocketAddress(Bridge.getPrivateHost(),
        datagramChannel.socket().getLocalPort());
    }

    private Recorder recorder;
    private Integer recordingLock = new Integer(0);
    private boolean recordRtp;

    public String getFromRecordingFile() {
    if (recorder != null) {
        return recorder.getRecordPath();
    }

    return null;
    }

    private void recordPacket(byte[] data, int length) {
    if (cp.getFromRecordingFile() == null) {
        return;
    }

    if (recordRtp == false) {
        return;
    }

    synchronized (recordingLock) {
        if (recorder == null) {
            return;
        }

        try {
            recorder.writePacket(data, 0, length);
            } catch (IOException e) {
                Logger.println("Unable to record data " + e.getMessage());
            cp.setFromRecordingFile(null);
                recorder = null;
            }
    }
    }

    private void recordAudio(int[] data, int length) {
        if (cp.getFromRecordingFile() == null) {
            return;
        }

    if (recordRtp == true) {
        return;
    }

    synchronized (recordingLock) {
        if (recorder == null) {
            return;
        }

            try {
                recorder.write(data, 0, length);
            } catch (IOException e) {
                Logger.println("Unable to record data " + e.getMessage());
            cp.setFromRecordingFile(null);
                recorder = null;
            }
    }
    }

    public boolean doNotRecord() {
    return cp.doNotRecord();
    }

    public void setDoNotRecord(boolean doNotRecord) {
    cp.setDoNotRecord(doNotRecord);

    Logger.println("Call " + cp + " doNotRecord is " + doNotRecord);
    }

    public void setRecordFromMember(boolean enabled, String recordingFile,
        String recordingType) throws IOException {

    if (doNotRecord()) {
        if (enabled) {
        Logger.println("Call " + cp + " doesn't allow recording.");
        enabled = false;
        }
    }

        if (recorder != null) {
        recorder.done();
        recorder = null;
        }

    synchronized (recordingLock) {
        if (enabled == false) {
            cp.setFromRecordingFile(null);
        return;
        }

            if (recordingType == null) {
                recordingType = "Au";
            }

        recordRtp = false;

        if (recordingType.equalsIgnoreCase("Rtp")) {
                recordRtp = true;
            }

            synchronized (recordingLock) {
        MediaInfo m;

                try {
            if (recordingType.equalsIgnoreCase("Rtp")) {
                        m = SdpManager.findMediaInfo(
                            myMediaInfo.getEncoding(),
                myMediaInfo.getSampleRate(),
                            myMediaInfo.getChannels());
            } else {
            m = SdpManager.findMediaInfo(
                RtpPacket.PCM_ENCODING,
                conferenceManager.getMediaInfo().getSampleRate(),
                conferenceManager.getMediaInfo().getChannels());
            }
                } catch (ParseException e) {
                    Logger.println("Can't record rtp to " + recordingFile
            + " " + e.getMessage());
                    throw new IOException(e.getMessage());
                }

        Logger.println("Recording media " + m);

                recorder = new Recorder(cp.getRecordDirectory(),
            recordingFile, recordingType, m);

                cp.setFromRecordingFile(recordingFile);
                cp.setFromRecordingType(recordingType);
            }
    }
    }

    public WhisperGroup getWhisperGroup() {
    return whisperGroup;
    }

    public void setWhisperGroup(WhisperGroup whisperGroup) {
    if (this.whisperGroup != null) {
        synchronized (this.whisperGroup) {
            flushContributions();
        }
    }

    this.whisperGroup = whisperGroup;
    }

    public static void setJoinConfirmationKey(String key) {
    joinConfirmationKey = key;
    }

    public static String getJoinConfirmationKey() {
    return joinConfirmationKey;
    }

    private long decryptCount;
    private long decryptTime;

    private byte[] decrypt(byte[] data) {
    try {
        decryptCount++;
        long start = System.currentTimeMillis();

        byte[]clearText = decryptCipher.doFinal(data, 0, data.length);

        decryptTime += (System.currentTimeMillis() - start);
        return clearText;
    } catch (Exception e) {
        Logger.println("Call " + cp + " Decryption failed, length "
        + data.length + ": " + e.getMessage());
        callHandler.cancelRequest("Decryption failed: "
        + e.getMessage());
        return data;
    }
    }

    public String toString() {
    return myMemberNumber + " ===> " + cp.toString();
    }

    public String toAbbreviatedString() {
    String callId = myMemberNumber + " ===> " + cp.getCallId();

    if (callId.length() < 14) {
        return callId;
    }

    return callId.substring(0, 13);
    }

}
