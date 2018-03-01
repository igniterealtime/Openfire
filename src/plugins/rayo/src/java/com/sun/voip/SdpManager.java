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

package com.sun.voip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.ParseException;

import java.util.Vector;

public class SdpManager {

    private static Vector supportedMedia = new Vector();

    private MediaInfo localMediaPreference;

    private MediaInfo transmitMediaInfo;

    private boolean isMacOS = false;

    private int maxSampleRate;
    private int maxChannels;

    private static boolean useTelephoneEvent = true;

    public SdpManager() {
        String s = System.getProperty("os.name");

        if (s.equals("Mac OS X")) {
        isMacOS = true;

        maxSampleRate = RtpPacket.MAC_SAMPLE_RATE;
        maxChannels = RtpPacket.MAC_CHANNELS;
    } else {
        maxSampleRate = RtpPacket.MAX_SAMPLE_RATE;
        maxChannels = RtpPacket.MAX_CHANNELS;
    }
    }

    public static void useTelephoneEvent(boolean useTelephoneEvent) {
    SdpManager.useTelephoneEvent = useTelephoneEvent;
    }

    public static boolean useTelephoneEvent() {
    return useTelephoneEvent;
    }

    public SdpManager(Vector supportedMedia) {
    this.supportedMedia = supportedMedia;
    }

    public static void setSupportedMedia(Vector supportedMedia) {
    SdpManager.supportedMedia = supportedMedia;
    }

    public void setPreferredMedia(int encoding, int sampleRate, int channels)
        throws ParseException {

    if (sampleRate == 8000 && channels == 1) {
        encoding = RtpPacket.PCMU_ENCODING;
    }

    setPreferredMediaInfo(findMediaInfo(encoding, sampleRate, channels));
    }

    public void setPreferredMediaInfo(MediaInfo preferredMediaInfo) {
    localMediaPreference = preferredMediaInfo;

        maxSampleRate = localMediaPreference.getSampleRate();
        maxChannels = localMediaPreference.getChannels();
    }

    public MediaInfo getPreferredMediaInfo() {
    return localMediaPreference;
    }

    public void setTransmitMediaInfo(int encoding, int sampleRate,
        int channels) throws ParseException {

    transmitMediaInfo = findMediaInfo(encoding, sampleRate, channels);
    }

    public void setTransmitMediaInfo(MediaInfo transmitMediaInfo) {
    this.transmitMediaInfo = transmitMediaInfo;
    }

    public MediaInfo getTransmitMediaInfo() {
        return transmitMediaInfo;
    }

    public static SdpInfo parseSdp(String sdpData) throws ParseException {
    return new SdpParser().parseSdp(sdpData);
    }

    /*
     * Get supported media
     */
    private String getSupportedMedia() {
    String s = "";

    int n = 0;

    for (int i = 0; i < supportedMedia.size(); i++) {
        MediaInfo mediaInfo = (MediaInfo) supportedMedia.elementAt(i);

        if (mediaInfo.getSampleRate() > maxSampleRate || mediaInfo.getChannels() > maxChannels) {
            continue;
        }

        if (useTelephoneEvent == false && mediaInfo.isTelephoneEventPayload())
        {
            continue;
        }

        if (n > 0) {
        s += " ";
        }

        s += mediaInfo.getPayload();
        n++;
    }

    return s;
    }

    /*
     * Get supported rtpmaps
     */
    private String getRtpmaps() {
    String rtpmaps = "";

    for (int i = 0; i < supportedMedia.size(); i++)
    {
        MediaInfo mediaInfo = (MediaInfo) supportedMedia.elementAt(i);

        if (mediaInfo.getSampleRate() > maxSampleRate || mediaInfo.getChannels() > maxChannels)
        {
            continue;
        }

        rtpmaps += generateRtpmap(mediaInfo) + "\r\n";
    }

    return rtpmaps;
    }

    /*
     * Get the rtpmap for a specific payload
     */
    private String getRtpmap(byte payload) throws ParseException {
        return generateRtpmap(findMediaInfo(payload));
    }

    /*
     * Generate an rtpmap entry for a speicifed MediaInfo.
     */
    private String generateRtpmap(MediaInfo mediaInfo) {
    if (mediaInfo.isTelephoneEventPayload()) {
    //    if (useTelephoneEvent == false) {
    //	return "";
    //    }

        return "a=rtpmap:" + mediaInfo.getPayload()
        + " telephone-event/8000";
    }

    return "a=rtpmap:" + mediaInfo.getPayload() + " "
        + mediaInfo.getEncodingString() + "/"
        + mediaInfo.getSampleRate() + "/"
        + mediaInfo.getChannels();
    }

    /*
     * Find the MediaInfo for a specified payload
     */
    public static MediaInfo findMediaInfo(byte payload)
        throws ParseException {

    for (int i = 0; i < supportedMedia.size(); i++) {
        MediaInfo mediaInfo = (MediaInfo)
        supportedMedia.elementAt(i);

            if (mediaInfo.getPayload() == payload) {
                return mediaInfo;
            }
    }

    throw new ParseException("Unsupported payload " + payload, 0);
    }

    /*
     * Find the MediaInfo for specified parameters
     */
    public static MediaInfo findMediaInfo(int encoding, int sampleRate,
    int channels) throws ParseException {

    for (int i = 0; i < supportedMedia.size(); i++) {
        MediaInfo mediaInfo = (MediaInfo)
        supportedMedia.elementAt(i);

        if (mediaInfo.isTelephoneEventPayload()) {
        continue;	// skip this one
        }

        if (mediaInfo.getEncoding() == encoding &&
            mediaInfo.getSampleRate() == sampleRate &&
            mediaInfo.getChannels() == channels) {

        return mediaInfo;
        }
    }

    throw new ParseException("Unsupported media " +
        "encoding " + encoding + " sample rate " + sampleRate
        + " channels " + channels, 0);
    }

    public String generateSdp(CallParticipant cp, String name, InetSocketAddress isa)
    {
        String toNumber = cp.getPhoneNumber();

            String sdp = "v=0\r\n"
                + "o=" + name + " 1 1 IN IP4 "
                + isa.getAddress().getHostAddress() + "\r\n"
                + "s=SIP Call\r\n"
                + "c=IN IP4 "
                + isa.getAddress().getHostAddress() + "\r\n"
                + "t=0 0 \r\n"
                + "m=audio " + isa.getPort();

            if (toNumber.indexOf("sip:") == 0)	// TODO hack for Lync DTMF
            {
                sdp += " RTP/AVP " + "13 " + getSupportedMedia() + "\r\n"
                        + "a=rtpmap:13 CN/8000" + "\r\n";

            } else {	// Lync, add DTMF support

                sdp += " RTP/AVP " + "13 101 " + getSupportedMedia() + "\r\n"
                        + "a=rtpmap:13 CN/8000" + "\r\n"
                        + "a=rtpmap:101 telephone-event/8000" + "\r\n"
                        + "a=fmtp:101 0-16" + "\r\n";
            }

            sdp += getRtpmaps();

            if (localMediaPreference != null)
            {
                sdp += "a=PreferredPayload:" + localMediaPreference.getPayload() + "\r\n";
            }

    if (transmitMediaInfo != null) {
        sdp += "a=transmitPayload:"
        + transmitMediaInfo.getPayload() + "\r\n";
    }

        return sdp;
    }

    public String generateSdp(String name, InetSocketAddress isa, SdpInfo remoteSdpInfo) throws IOException
    {
        MediaInfo mediaInfo = null;

        if (localMediaPreference != null)
        {
            if (remoteSdpInfo.isSupported(localMediaPreference)) {
                mediaInfo = localMediaPreference;
                Logger.println("Using local media preference:  " + mediaInfo);
            }
        }

        /*
         * Try remote media preference
         */
        if (remoteSdpInfo.preferredMediaSpecified())
        {
            MediaInfo remoteMediaPreference = remoteSdpInfo.getMediaInfo();

            if (remoteMediaPreference.getSampleRate() <= maxSampleRate &&
                remoteMediaPreference.getChannels() <= maxChannels) {

                /*
                 * See if remote media preference is supported
                 */
                try {
                    mediaInfo = findMediaInfo(remoteMediaPreference.getPayload());
                    Logger.println("Using remote media preference:  " + mediaInfo);
                } catch (ParseException e) {
                }
            }
        }

        if (mediaInfo == null) {
            /*
             * default to 8000/1 ulaw
             */
            mediaInfo = remoteSdpInfo.findBestMediaInfo(supportedMedia,	localMediaPreference);

            Logger.println("Using best media " + mediaInfo);
        }

        remoteSdpInfo.setMediaInfo(mediaInfo);

        String payloads = "13 101 " + mediaInfo.getPayload();

        byte telephoneEventPayload = remoteSdpInfo.getTelephoneEventPayload();

        String telephoneEvent = "";

        if (useTelephoneEvent == true && telephoneEventPayload != 0) {
            try {
                MediaInfo m = findMediaInfo(telephoneEventPayload);
            payloads += " " + telephoneEventPayload;
                telephoneEvent += generateRtpmap(m) + "\r\n";
            } catch (ParseException e) {
            Logger.println("Failed to add rtpmap for telephone event " + telephoneEventPayload);
            }
        }

        String transmitMap = "";

        if (transmitMediaInfo != null) {
            transmitMap = generateRtpmap(transmitMediaInfo) + "\r\n";
        }

        String sdp =
            "v=0\r\n"
                + "o=" + name + " 1 1 IN IP4 "
                + isa.getAddress().getHostAddress() + "\r\n"
                + "s=SIP Call\r\n"
                + "c=IN IP4 "
                + isa.getAddress().getHostAddress() + "\r\n"
                + "t=0 0 \r\n"
                + "m=audio " + isa.getPort()
                + " RTP/AVP " + payloads + "\r\n"
                + "a=rtpmap:13 CN/8000" + "\r\n"
                + "a=rtpmap:101 telephone-event/8000" + "\r\n"
                + "a=fmtp:101 0-16" + "\r\n"
                + generateRtpmap(mediaInfo) + "\r\n"
            + transmitMap
            + telephoneEvent;

        if (transmitMediaInfo != null) {
            sdp += "a=transmitPayload:"
                + transmitMediaInfo.getPayload() + "\r\n";
        }

        return sdp;
    }

}
