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
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.Recorder;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSenderPacket;
import com.sun.voip.SampleRateConverter;
import com.sun.voip.SdpManager;
import com.sun.voip.SpeexEncoder;
import com.sun.voip.SpeexException;
import com.sun.voip.TreatmentManager;
import com.sun.voip.Util;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import java.util.ArrayList;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import java.text.ParseException;
import org.xmpp.jnodes.IChannel;

import org.ifsoft.*;
import org.ifsoft.rtp.*;

import org.jitsi.impl.neomedia.codec.audio.opus.Opus;

import com.jcumulus.server.rtmfp.packet.AudioPacket;

/**
 * Send RTP data to this ConferenceMember,
 */
public class MemberSender {
    private ConferenceManager conferenceManager;
    private CallHandler callHandler;
    private CallParticipant cp;		             // caller parameters
    private boolean traceCall = false;
    private byte telephoneEventPayload;
    private MediaInfo myMediaInfo;
    private MediaInfo conferenceMediaInfo;
    private double outputVolume = 1.0;
    private boolean mustSetMarkBit = true;           // true for first packet
    private String dtmfKeyToSend;
    private int dtmfSendSequence;
    private RtpSenderPacket senderPacket;
    private SpeexEncoder speexEncoder;

    private long opusEncoder = 0;
    private final int opusSampleRate = 48000;
    private final int frameSizeInMillis = 20;
    private final int outputFrameSize = 2;
    private final int opusChannels = 2;
    private int frameSizeInSamplesPerChannel = (opusSampleRate * frameSizeInMillis) / 1000;
    private int frameSizeInBytes = outputFrameSize * opusChannels * frameSizeInSamplesPerChannel;

    private InetSocketAddress memberAddress;
    private boolean done = false;

    /*
     * Statistics
     */
    private int packetsSent = 0;
    private double totalTimeToGetData;
    private int comfortPayloadsSent = 0;
    private Cipher encryptCipher;
    private String encryptionKey;
    private String encryptionAlgorithm;
    private int mySamplesPerPacket;
    private SampleRateConverter outSampleRateConverter;
    private int outSampleRate;
    private int outChannels;
    private DatagramChannel datagramChannel;
    private boolean initializationDone = false;
    private IChannel relayChannel;
    private long startTime = 0;

    public MemberSender(CallParticipant cp, DatagramChannel datagramChannel) throws IOException
    {
        this.cp = cp;

        this.datagramChannel = datagramChannel;

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

                encryptCipher = Cipher.getInstance(encryptionAlgorithm);
                encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);

            Logger.println("Call " + cp + " Voice data will be encrypted "
                + "using " + encryptionAlgorithm);
            } catch (Exception e) {
                Logger.println("Call " + cp
                    + " Crypto initialization failed " + e.getMessage());
                        throw new IOException(" Crypto initialization failed "
                    + e.getMessage());
            }
        }

        startTime = System.currentTimeMillis();
    }


    public void setChannel(IChannel relayChannel)
    {
        this.relayChannel = relayChannel;
    }

    public IChannel getChannel()
    {
        return this.relayChannel;
    }

    public InetSocketAddress getSendAddress() {
    return memberAddress;
    }

    public void setSendAddress(InetSocketAddress sendAddress) {
    if (memberAddress == null || memberAddress.equals(sendAddress)) {
        return;
    }
/*	BAO not sure why this is here

    if (memberAddress.getAddress().equals(sendAddress.getAddress()) == false) {
        Logger.println("Call " + cp
        + " Attempt to change remote IP Address "
        + memberAddress.getAddress() + " to "
        + sendAddress.getAddress() + " rejected!");
        return;
    }
*/

    Logger.println("Call " + cp + " member address changed from " + memberAddress + " to " + sendAddress);
    memberAddress = sendAddress;
    }

    /*
     * For debugging.
     */
    public void traceCall(boolean traceCall) {
    this.traceCall = traceCall;
    }

    public boolean traceCall() {
    return traceCall;
    }

    public String getMemberState() {
    if (initializationDone == false) {
        return "";
    }

    String s = "";

    s += "\tAddress to send data to call " + memberAddress + "\n";
    s += "\tSending Speex encoded data " + cp.speexEncode() + "\n";

    s += "\tOutput volume " + outputVolume;

        s += "\n";

    return s;
    }

    /**
     * Initialize this member.  The call has been established and
     * we now know the port at which the member (CallParticipant)
     * listens for data.
     */
    public void initialize(ConferenceManager conferenceManager,
        CallHandler callHandler, InetSocketAddress memberAddress,
        byte mediaPayload, byte telephoneEventPayload) {

    this.conferenceManager = conferenceManager;
    this.memberAddress = memberAddress;
    this.telephoneEventPayload = telephoneEventPayload;
    this.callHandler = callHandler;

    Logger.writeFile("Call " + cp + " MemberSender initialization started ..." + cp.getProtocol());

    conferenceMediaInfo = conferenceManager.getMediaInfo();

    outSampleRate = conferenceMediaInfo.getSampleRate();
    outChannels = conferenceMediaInfo.getChannels();

    try {
        myMediaInfo = SdpManager.findMediaInfo(mediaPayload);
    } catch (ParseException e) {
        Logger.println("Call " + cp + " Invalid mediaPayload "
        + mediaPayload);

        callHandler.cancelRequest("Invalid mediaPayload " + mediaPayload);
        return;
    }

    int inSampleRate = myMediaInfo.getSampleRate();
    int inChannels = myMediaInfo.getChannels();

    /*
     * No data is ever sent to an input treatment unless it's a recorder
     */
    if (cp.getInputTreatment() == null || cp.isRecorder() == true) {
        if (inSampleRate != outSampleRate || inChannels != outChannels) {
                Logger.println("Call " + cp
                    + " resample data to send from " + inSampleRate + "/"
                    + inChannels + " to " + outSampleRate
                    + "/" + outChannels);

            try {
                outSampleRateConverter = new SampleRateConverter(
                this.toString(), outSampleRate, outChannels,
                inSampleRate, inChannels);
            } catch (IOException e) {
                callHandler.cancelRequest(e.getMessage());
                return;
            }
        }
    }

    senderPacket = new RtpSenderPacket(myMediaInfo.getEncoding(), inSampleRate, inChannels);

    if (myMediaInfo.getEncoding() == RtpPacket.SPEEX_ENCODING) {
        try {
            speexEncoder = new SpeexEncoder(inSampleRate, inChannels);
        Logger.println("Call " + cp + " created SpeexEncoder");
        } catch (SpeexException e) {
        Logger.println("Call " + cp
            + " Speex initialization for encoding failed:  "
            + e.getMessage());
        callHandler.cancelRequest(e.getMessage());
                return;
            }
    }

    if (myMediaInfo.getEncoding() == RtpPacket.PCM_ENCODING) {

        try {
            opusEncoder = Opus.encoder_create(opusSampleRate, opusChannels);

            if (opusEncoder == 0)
            {
                Logger.println("Call " + cp + " OPUS encoder creation error ");
                callHandler.cancelRequest("OPUS encoder creation error ");
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    initializationDone = true;

    Logger.writeFile("Call " + cp + " MemberSender initialization done...");
    }

    public MediaInfo getMediaInfo() {
    return myMediaInfo;
    }

    public void setOutputVolume(double outputVolume) {
        this.outputVolume = outputVolume;
    }

    public double getOutputVolume() {
        return outputVolume;
    }

    private long timePreviousPacketSent;

    public synchronized void handleVP8Video(RTPPacket videoPacket)
    {
        try {
            //getWebRTCParticipant().pushVideo(videoPacket);

        } catch (Exception e) {

        }
    }

    public synchronized boolean sendData(int[] dataToSend)
    {

    if (dtmfKeyToSend != null) {
        if (telephoneEventPayload != 0) {
            sendDtmfKey();
        return true;
        } else {
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Call " + cp
                + " Telephone event payload not supported.  "
                + "Can't send " + dtmfKeyToSend);
        }
        dtmfKeyToSend = null;
        }
    }


    long start = System.nanoTime();

    if (dataToSend == null)
    {
        if (Logger.logLevel == -77)
        {
            Logger.println("Call " + cp + " no data to send");
        }

        if (comfortNoiseType == CN_USE_PAYLOAD) {

            if (senderPacket.getRtpPayload() != RtpPacket.COMFORT_PAYLOAD)
            {
                if (Logger.logLevel == -77) {
                        Logger.println("Call " + cp + " sending comfort payload");
                }

                if (relayChannel == null)
                {
                    if (cp.getRtmfpSendStream() == null && "SIP".equals(cp.getProtocol()))
                        sendComfortNoisePayload();

                } else relayChannel.sendComfortNoisePayload();
            }
        }

        mustSetMarkBit = true;
        return false;
    }

    if (senderPacket.getRtpPayload() == RtpPacket.COMFORT_PAYLOAD) {
        senderPacket.adjustRtpTimestamp();	// account for pause
    }

    senderPacket.setRtpPayload(myMediaInfo.getPayload());

    if (mustSetMarkBit) {
            if (Logger.logLevel == -77 ||
                    Logger.logLevel >= Logger.LOG_MOREINFO) {

                Logger.println("Setting MARK for " + cp);
            }

            if (Logger.logLevel != -77) {
                /*
                 * Adjust RTP timestamp to account for long pause
                 */
                if (timePreviousPacketSent != 0) {
                    senderPacket.adjustRtpTimestamp(
            System.currentTimeMillis() - timePreviousPacketSent);
                }
            }

        senderPacket.setMark(); 	/* Set MARK_BIT */

        mustSetMarkBit = false;
    }

    if (outputVolume != 1.0) {
        callHandler.getMember().adjustVolume(dataToSend, outputVolume);
    }

    dataToSend = normalize(dataToSend);

    try {
        /*
         * Resample if needed
         */
        if (outSampleRateConverter != null) {
            dataToSend = outSampleRateConverter.resample(dataToSend);
        }
    } catch (IOException e) {
        Logger.println("Call " + cp + " can't resample data to send! "
        + e.getMessage());
        callHandler.cancelRequest("Call " + cp
        + " can't resample data to send! " + e.getMessage());
        return false;
    }

    byte[] rtpData = senderPacket.getData();

        if (Logger.logLevel == -37) {
            boolean silence = true;

            for (int i = RtpPacket.HEADER_SIZE;
            i < rtpData.length - RtpPacket.HEADER_SIZE; i++) {

                if (rtpData[i] != 0) {
                    silence = false;
                    break;
                }
            }

            if (silence) {
                //Logger.println("Call " + cp + " sending silence");
        return false;
            }
    }

    //Util.dump("Call " + cp + " sending data " + dataToSend.length,
    //    dataToSend, 0, 8);

    //Logger.println("Call " + cp + " Sending data...");

    byte[] opusBytes = null;

    if (myMediaInfo.getEncoding() == RtpPacket.PCMU_ENCODING) {
        /*
         * Convert to ulaw
         */
        AudioConversion.linearToUlaw(dataToSend, rtpData, RtpPacket.HEADER_SIZE);

        senderPacket.setLength(rtpData.length);

        //Util.dump("Call " + cp + " sending ulaw data " + rtpData.length,
        //    rtpData, 0, 16);

    } else if (myMediaInfo.getEncoding() == RtpPacket.SPEEX_ENCODING) {
        try {
                if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
                    Logger.writeFile("Call " + cp + " speex encoding data ");
                }

                int length = speexEncoder.encode(dataToSend, rtpData,  RtpPacket.HEADER_SIZE);

                senderPacket.setLength(length + RtpPacket.HEADER_SIZE);

            } catch (SpeexException e) {
                Logger.println("Call " + this + ":  " + e.getMessage());
        return false;
        }

    } else if (myMediaInfo.getEncoding() == RtpPacket.PCM_ENCODING) {

        if (relayChannel != null && relayChannel.encode())
        {
            byte[] input = AudioConversion.littleEndianIntsToBytes(dataToSend);
            byte[] output = new byte[Opus.MAX_PACKET];

            int outLength = Opus.encode(opusEncoder, input, 0, frameSizeInSamplesPerChannel, output, 0, output.length);
            opusBytes = new byte[outLength];
            System.arraycopy(output, 0, opusBytes, 0, outLength);

            System.arraycopy(output, 0, rtpData, RtpPacket.HEADER_SIZE, outLength);
            senderPacket.setLength(outLength + RtpPacket.HEADER_SIZE);

            //Logger.println("RtpPacket.PCM_ENCODING " + outLength);
        }

    } else {
        AudioConversion.intsToBytes(dataToSend, rtpData, RtpPacket.HEADER_SIZE);
    }

    recordPacket(rtpData, senderPacket.getLength());
    recordAudio(rtpData, RtpPacket.HEADER_SIZE,	senderPacket.getLength() - RtpPacket.HEADER_SIZE);

    /*
     * Encrypt data if required
     */
    if (needToEncrypt()) {
        encrypt(rtpData, senderPacket.getLength());
    }

        if (Logger.logLevel == -78) {
        Logger.println("Call " + cp + " sending data from socket "
        + datagramChannel.socket().getLocalAddress()
        + ":" + datagramChannel.socket().getLocalPort()
        + " to " + senderPacket.getSocketAddress());
    }

    /*
     * If this is an input treatment, the only reason we're
     * here is to record the data.
     * There is no need to send data to the call.
     * In fact the send and receive addresses are the same
     * so that if we sent data, it would also be received!
     */

    if (relayChannel == null)
    {
        if (cp.getRtmfpSendStream() != null)	// RTMFP
        {
            if (RtmfpCallAgent.publishHandlers.containsKey(cp.getRtmfpSendStream()) )
            {
                int ts = (int)(System.currentTimeMillis() - startTime);

                byte[] rtmfp = new byte[rtpData.length + 1 - RtpPacket.HEADER_SIZE];
                rtmfp[0] = (byte) 130;
                System.arraycopy(rtpData, RtpPacket.HEADER_SIZE, rtmfp, 1, rtmfp.length - 1);

                RtmfpCallAgent.publishHandlers.get(cp.getRtmfpSendStream()).B(ts, new AudioPacket(rtmfp,  rtmfp.length), 0);
            }

        } else if ("SIP".equals(cp.getProtocol())) {	// SIP

            if (cp.getInputTreatment() == null) {
                try {
                    senderPacket.setSocketAddress(memberAddress);

                    datagramChannel.send(ByteBuffer.wrap(senderPacket.getData(), 0, senderPacket.getLength()), memberAddress);

                        if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
                        Logger.writeFile("Call " + cp + " back from sending data");
                    }
                } catch (Exception e) {
                    if (!done) {
                    Logger.error("Call " + cp + " sendData " + e.getMessage());
                        e.printStackTrace();
                    }
                    return false;
                }
            }

        } else {		//do nothing;

            return true;
        }

    } else {	// WebRTC

        try {

            if (relayChannel.encode())
                relayChannel.pushAudio(senderPacket.getData(), opusBytes);
            else
                relayChannel.pushAudio(dataToSend);

        } catch (Exception e) {

            return false;
        }
    }

    senderPacket.setBuffer(rtpData);

    if (Logger.logLevel >= Logger.LOG_DEBUG) {
        log(true);
    }

    timePreviousPacketSent = System.currentTimeMillis();

    if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
        Logger.println("Call " + cp + " sendLength " + rtpData.length);
    }

    totalTimeToGetData += (System.nanoTime() - start);
    packetsSent++;
    senderPacket.updateRtpHeader(rtpData.length);
    return true;
    }

    public static int[] normalize(int[] audio)
    {
        int length = audio.length;
        // Scan for max peak value here
        float peak = 0;
        for (int n = 0; n < length; n++) {
            float val = Math.abs(audio[n]);
            if (val > peak) {
                peak = val;
            }
        }

        // Peak is now the loudest point, calculate ratio
        float r1 = 32768 / peak;

        // Don't increase by over 500% to prevent loud background noise, and
        // normalize to 90%
        float ratio = Math.min(r1, 5) * .90f;

        for (int n = 0; n < length; n++) {
            audio[n] *= ratio;
        }

        return audio;

    }
    /*
     * It takes 12 packets to generate a dtmf key!
     * The first 3 are silence packets with the MARK bit set.
     * All the rest have the same RTP timestamp.
     * Next 3 packets have the MARK bit set and a duration of 0.
     * The next 3 packets have the MARK bit clear and a duration of
     * 400, 800, and 1200, respectively.
     * The last 3 packets have the MARK bit clear, the END bit set,
     * and a duration of 1304.
     *
     * The next packet sent has the MARK bit set.
     *
     * This is what we receive if a dtmf key is pressed on the lucent phone.
     * So the assumption is that this is what we should generate for dtmf keys.
     */
    private void sendDtmfKey() {
    dtmfSendSequence++;

    if (dtmfSendSequence == 1) {
        Logger.println("Sending dtmf key " + dtmfKeyToSend
            + " to " + cp + " sequence " + dtmfSendSequence);
    } else {
        Logger.writeFile("Sending dtmf key " + dtmfKeyToSend
            + " to " + cp + " sequence " + dtmfSendSequence);
    }

    byte[] data = senderPacket.getData();

    if (dtmfSendSequence <= 3) {
            /*
             * Send Silence with the MARK BIT
             */
        int size = RtpPacket.getDataSize(myMediaInfo.getEncoding(),
        myMediaInfo.getSampleRate(), myMediaInfo.getChannels());

        size += RtpPacket.HEADER_SIZE;

        int silence = AudioConversion.PCMU_SILENCE;

        if (myMediaInfo.getEncoding() == RtpPacket.PCM_ENCODING) {
        silence = AudioConversion.PCM_SILENCE;
        }

            for (int i = RtpPacket.HEADER_SIZE; i < size; i++) {
                data[i] = AudioConversion.PCMU_SILENCE;
            }
            senderPacket.setLength(size);
            senderPacket.setMark();
            sendPacket();
        senderPacket.incrementRtpSequenceNumber();
        Logger.writeFile("Sending silence with MARK set");
        return;
        }

        senderPacket.setRtpPayload(telephoneEventPayload);
        senderPacket.setLength(RtpPacket.DATA + 4);

        data[RtpPacket.DATA + 0] = getTelephoneEvent(dtmfKeyToSend);
        data[RtpPacket.DATA + 1] = (byte)6;   // volume level

    if (dtmfSendSequence <= 6) {
            /*
             * These 3 packets have MARK bit set and duration of 0
             */
            senderPacket.setMark();
            data[RtpPacket.DATA + 2] = (byte)0;
            data[RtpPacket.DATA + 3] = (byte)0;

            sendPacket();
        senderPacket.incrementRtpSequenceNumber();
        Logger.writeFile("Sending MARK duration 0");
        return;
        }

        /*
         * Next 3 packets have MARK bit clear, duration of 400, 800,
     * and 1200.
         */
    if (dtmfSendSequence == 7) {
        senderPacket.clearMark();
            data[RtpPacket.DATA + 2] = (byte)((400 >> 8) & 0xff);
            data[RtpPacket.DATA + 3] = (byte)(400 & 0xff);
            sendPacket();
        senderPacket.incrementRtpSequenceNumber();
        Logger.writeFile("Sending duration 400");
        return;
    }

    if (dtmfSendSequence == 8) {
        senderPacket.clearMark();
            data[RtpPacket.DATA + 2] = (byte)((800 >> 8) & 0xff);
            data[RtpPacket.DATA + 3] = (byte)(800 & 0xff);
            sendPacket();
        senderPacket.incrementRtpSequenceNumber();
        Logger.writeFile("Sending duration 800");
        return;
    }

    if (dtmfSendSequence == 9) {
        senderPacket.clearMark();
            data[RtpPacket.DATA + 2] = (byte)((1200 >> 8) & 0xff);
            data[RtpPacket.DATA + 3] = (byte)(1200 & 0xff);
            sendPacket();
        senderPacket.incrementRtpSequenceNumber();
        Logger.writeFile("Sending duration 1200");
        return;
    }

        /*
         * Last 3 packets have MARK bit clear, END bit set, and a
     * duration of 1304.
         */
    if (dtmfSendSequence <= 12) {
        senderPacket.clearMark();
            data[RtpPacket.DATA + 1] |= (byte)0x80; // end
            data[RtpPacket.DATA + 2] = (byte)((1304 >> 8) & 0xff);
            data[RtpPacket.DATA + 3] = (byte)(1304 & 0xff);
            sendPacket();
        senderPacket.incrementRtpSequenceNumber();
        Logger.writeFile("Sending END set duration 1304");
        return;
        }

    Logger.writeFile("Done sending dtmf key...");

        if (dtmfKeyToSend != null) {
            if (dtmfKeyToSend.length() == 1) {
                dtmfKeyToSend = null;
            } else {
                dtmfKeyToSend = dtmfKeyToSend.substring(1);
            }
        }

        dtmfSendSequence = 0;

    senderPacket.adjustRtpTimestamp(2400);
    }

    private byte getTelephoneEvent(String dtmfKeyToSend) {
    byte dtmfKey = (byte) -1;

    try {
        dtmfKey = (byte) Integer.parseInt(dtmfKeyToSend);
    } catch (NumberFormatException e) {
    }

    if (dtmfKey >= 0 && dtmfKey <= 9) {
        return dtmfKey;
    }

    if (dtmfKeyToSend.equals("*")) {
        return 10;
    }

    if (dtmfKeyToSend.equals("#")) {
        return 11;
    }

    if (dtmfKey >= 12 && dtmfKey <= 15) {
        return dtmfKey;
    }

    return 15;
    }

    private void sendPacket() {
    try {
        senderPacket.setSocketAddress(memberAddress);

        datagramChannel.send(
        ByteBuffer.wrap(senderPacket.getData()), memberAddress);
    } catch (IOException e) {
        if (!done) {
            Logger.error("Call " + cp + " sendPacket:  "
                + e.getMessage());
        }
    }
    }

    private static final int CN_DISABLE     = 0;    // disable comfort noise
    private static final int CN_ADD_NOISE   = 1;    // add noise to every packet
    private static final int CN_USE_PAYLOAD = 2;    // use cn payload change
    private static int comfortNoiseType     = CN_USE_PAYLOAD;

    public static void setComfortNoiseType(int comfortNoiseType) {
        MemberSender.comfortNoiseType = comfortNoiseType;
    }

    public static int getComfortNoiseType() {
        return comfortNoiseType;
    }

    public boolean sendComfortNoisePayload() {
    /*
     * Set payload and packet size
     */
    senderPacket.setComfortPayload();

    int len = senderPacket.getLength();

        /*
     * A packet with COMFORT_PAYLOAD has one byte of data to
     * indicate the noise volume level to generate.
     */
    senderPacket.setComfortNoiseLevel(RtpPacket.comfortNoiseLevel);

    byte[] data = senderPacket.getData();

    if (needToEncrypt()) {
            senderPacket.setLength(RtpPacket.DATA + 1);

        encrypt(data, senderPacket.getLength());
    }

        senderPacket.setSocketAddress(memberAddress);

    try {
        datagramChannel.send(ByteBuffer.wrap(senderPacket.getData()), memberAddress);
    } catch (IOException e) {
        if (!done) {
        Logger.println("Call " + cp + " sendComfortNoisePayload "
            + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    senderPacket.setBuffer(data);

        senderPacket.updateRtpHeader(len);

    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("Call " + cp + " Sent comfort noise payload "
            + "with level " + RtpPacket.comfortNoiseLevel);
    }

    if (Logger.logLevel >= Logger.LOG_DEBUG) {
        log(false);
    }

    comfortPayloadsSent++;
    return true;
    }

    /*
     * For debugging
     */
    long previousSendTime;

    private void log(boolean contributedToTheMix) {
    long sendTimeChange;

    long now = System.currentTimeMillis();

    if (previousSendTime == 0) {
        sendTimeChange = RtpPacket.PACKET_PERIOD;
    } else {
        sendTimeChange = now - previousSendTime;
    }

    previousSendTime = now;

    String summary = "";
    String flags = "";
    String badTime = " ";
    String badTimestamp = " ";

    if (senderPacket.isMarkSet()) {
        flags = "MARK ";
    } else {
        if (sendTimeChange < 15) {
            badTime = "-";
        summary = "!";
        } else if (sendTimeChange > 25) {
            badTime = "+";
        summary = "!";
        }
    }

    if (senderPacket.getRtpPayload() == RtpPacket.COMFORT_PAYLOAD) {
        flags += "COMFORT ";
    }

    String timestamp = Integer.toHexString(
            (int)(senderPacket.getRtpTimestamp() & 0xffffffff));

    if (timestamp.length() != 8) {
            timestamp += "      ";         // for alignment
    }

    /*
     * The rtpTimestampChange is always the same since
     * we send fixed size RTP Packets.  To save time logging,
     * we use the constant a0 (160 bytes).
     */
    Logger.writeFile("S "
        + " " + sendTimeChange + badTime
        + "\ta0"
        + badTimestamp
        + "\t"
        + Integer.toHexString(senderPacket.getRtpSequenceNumber() & 0xffff)
        + "\t" + timestamp
        + "\t" + flags + cp
        + " " + memberAddress);
    }

    /**
     * Member is leaving a conference.  Print statistics for the member.
     */
    public void end() {
        if (done) {
            return;
        }

        done = true;

        synchronized (recordingLock) {
                if (recorder != null) {
                    recorder.done();
                    recorder = null;
                }
        }

        if (opusEncoder != 0)
        {
            Opus.encoder_destroy(opusEncoder);
            opusEncoder = 0;
        }
    }

    public void printStatistics() {
    if (conferenceManager == null) {
        return;
    }

    synchronized (conferenceManager) {
        if (packetsSent == 0) {
        return;
        }

            Logger.writeFile("Call " + cp + ":  " + packetsSent
        + " packets sent");

            Logger.writeFile("Call " + cp + ":  " + comfortPayloadsSent
            + " comfort payloads sent");

        if (packetsSent != 0) {
                Logger.writeFile("Call " + cp + ":  "
            + ((float)totalTimeToGetData / 1000000000. / packetsSent)
            + " average seconds to get data to send");
        }

        Logger.writeFile("Call " + cp + ":  "
        + encryptCount + " packets encrypted");

        if (encryptCount != 0) {
        Logger.writeFile("Call " + cp + ":  "
            + (((float)encryptTime / (float)encryptCount) / 1000)
            + " milliseconds average per encrypt");
        }

            if (speexEncoder != null) {
        int encodes = speexEncoder.getEncodes();

        if (encodes > 0) {
            long encodeTime = speexEncoder.getEncodeTime();
            int bytesEncoded = speexEncoder.getBytesEncoded();

                    Logger.writeFile("Average Speex Encode time " +
                        (((float)encodeTime / encodes) / 1000000000.)
            + " seconds");

                    if (bytesEncoded > 0) {
                        Logger.writeFile("Average compression ratio " +
                            ((encodes * speexEncoder.getPcmPacketSize()) / bytesEncoded)
                + " to 1");
            }
                }
        }

        if (outSampleRateConverter != null) {
        outSampleRateConverter.printStatistics();
        }

            Logger.writeFile("");

        Logger.flush();
    }
    }

    public boolean memberIsReadyForSenderData() {
    return initializationDone;
    }

    /**
     * Get CallParticipant for this member
     */
    public CallParticipant getCallParticipant() {
    return cp;
    }

    public boolean mustSetMarkBit() {
    return mustSetMarkBit;
    }

    public void mustSetMarkBit(boolean mustSetMarkBit) {
    this.mustSetMarkBit = mustSetMarkBit;
    }

    private Recorder recorder;
    private Integer recordingLock = new Integer(0);
    private boolean recordRtp;

    private void recordPacket(byte[] data, int length) {
    if (cp.getToRecordingFile() == null) {
        return;
    }

    if (recordRtp == false) {
        return;
    }

    synchronized(recordingLock) {
        if (recorder == null) {
            return;
        }

        try {
            recorder.writePacket(data, 0, length);
            } catch (IOException e) {
                Logger.println("Unable to record data " + e.getMessage());
            cp.setToRecordingFile(null);
                recorder = null;
            }
    }
    }

    private void recordAudio(byte[] data, int offset, int length) {
    if (cp.getToRecordingFile() == null) {
        return;
    }

    if (recordRtp == true) {
        return;
    }

    synchronized(recordingLock) {
        if (recorder == null) {
            return;
        }

            try {
                recorder.write(data, offset, length);
            } catch (IOException e) {
                Logger.println("Unable to record data " + e.getMessage());
            cp.setToRecordingFile(null);
                recorder = null;
            }
    }
    }

    public void setRecordToMember(boolean enabled, String recordingFile,
        String recordingType) throws IOException {

    synchronized (recordingLock) {
        if (enabled == false) {
            if (recorder != null) {
            recorder.done();
            }
            cp.setToRecordingFile(null);
        return;
        }

        if (recordingType == null) {
            recordingType = "";
        }

        recordRtp = false;

        if (recordingType.equalsIgnoreCase("Rtp")) {
        recordRtp = true;
        }

            recorder = new Recorder(cp.getRecordDirectory(),
        recordingFile, recordingType, myMediaInfo);

            cp.setToRecordingFile(recordingFile);
            cp.setToRecordingType(recordingType);
    }
    }

    public void setDtmfKeyToSend(String dtmfKeyToSend) {
    if (telephoneEventPayload == 0) {
        String treatment;

        if (dtmfKeyToSend.equals("*")) {
        treatment = "dtmfStar.au";
        } else if (dtmfKeyToSend.equals("#")) {
        treatment = "dtmfPound.au";
        } else {
        treatment = "dtmf" + dtmfKeyToSend + ".au";
        }

        try {
            TreatmentManager tm = new TreatmentManager(treatment,
            0, conferenceMediaInfo.getSampleRate(),
            conferenceMediaInfo.getChannels());


            callHandler.getMember().addTreatment(tm);
        } catch (IOException e) {
        Logger.println("Unable to play dtmf key " + dtmfKeyToSend
            + " " + e.getMessage());
        }
        return;
    }

        if (dtmfKeyToSend != null) {
        if (this.dtmfKeyToSend != null) {
            this.dtmfKeyToSend += dtmfKeyToSend;
            return;
        }
    }

    this.dtmfKeyToSend = dtmfKeyToSend;
    dtmfSendSequence = 0;
    }

    public void speexEncode(int[] intData, byte[] byteData)
        throws SpeexException {

    if (speexEncoder == null) {
            speexEncoder = new SpeexEncoder(myMediaInfo.getSampleRate(),
        myMediaInfo.getChannels());

        Logger.println("Call " + cp + " created SpeexEncoder");
    }

        speexEncoder.encode(intData, byteData, RtpPacket.HEADER_SIZE);
    }

    private long encryptCount;
    private long encryptTime;

    public boolean needToEncrypt() {
        return encryptCipher != null;
    }

    public void encrypt(byte[] data, int length) {

    try {
        encryptCount++;
        long start = System.currentTimeMillis();
        byte[] cipherText = encryptCipher.doFinal(data, 0, length);
        encryptTime += (System.currentTimeMillis() - start);
        senderPacket.setBuffer(cipherText);
    } catch (Exception e) {
            Logger.println("Call " + cp + " Encryption failed, length "
        + data.length + ": " + e.getMessage());
            callHandler.cancelRequest("Encryption failed: " +
        e.getMessage());
    }
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
