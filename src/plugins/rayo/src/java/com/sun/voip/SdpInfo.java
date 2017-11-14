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

import java.util.Vector;


public class SdpInfo {

    private String sdp;
    private String remoteHost;
    private int remotePort;
    private byte telephoneEventPayload;
    private boolean preferredMediaSpecified;
    private String userName;
    private String callId;
    private String conferenceId;
    private boolean isDistributedBridge;
    private int synchronizationSource;
    private InetSocketAddress rtcpAddress;

    private Vector supportedMedia;
    private MediaInfo mediaInfo;
    private MediaInfo transmitMediaInfo;
    private boolean transmitMediaInfoOk;

    public SdpInfo(String remoteHost, int remotePort,
        byte telephoneEventPayload, Vector supportedMedia,
        MediaInfo mediaInfo, boolean preferredMediaSpecified) {

    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
    this.telephoneEventPayload = telephoneEventPayload;
    this.supportedMedia = supportedMedia;
    this.mediaInfo = mediaInfo;
    this.preferredMediaSpecified = preferredMediaSpecified;
    }

    public void setSdp(String sdp) {
    this.sdp = sdp;
    }

    public String getSdp() {
    return sdp;
    }

    public void setRemoteHost(String remoteHost) {
    this.remoteHost = remoteHost;
    }

    public String getRemoteHost() {
    return remoteHost;
    }

    public void setRemotePort(int remotePort) {
    this.remotePort = remotePort;
    }

    public int getRemotePort() {
    return remotePort;
    }

    public byte getTelephoneEventPayload() {
    return telephoneEventPayload;
    }

    public void setUserName(String userName) {
        this.userName= userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setCallId(String callId) {
    this.callId = callId;
    }

    public String getCallId() {
    return callId;
    }

    public void setConferenceId(String conferenceId) {
    this.conferenceId = conferenceId;
    }

    public String getConferenceId() {
    return conferenceId;
    }

    public void setDistributedBridge() {
    isDistributedBridge = true;
    }

    public boolean isDistributedBridge() {
    return isDistributedBridge;
    }

    public void setRtcpAddress(InetSocketAddress rtcpAddress) {
    this.rtcpAddress = rtcpAddress;
    }

    public InetSocketAddress getRtcpAddress() {
    return rtcpAddress;
    }

    public void setMediaInfo(MediaInfo mediaInfo) {
    this.mediaInfo = mediaInfo;
    }

    public MediaInfo getMediaInfo() {
    return mediaInfo;
    }

    public void setTransmitMediaInfoOk(boolean transmitMediaInfoOk) {
    this.transmitMediaInfoOk = transmitMediaInfoOk;
    }

    public boolean getTransmitMediaInfoOk() {
    return transmitMediaInfoOk;
    }

    public void setTransmitMediaInfo(MediaInfo transmitMediaInfo) {
    this.transmitMediaInfo = transmitMediaInfo;
    }

    public MediaInfo getTransmitMediaInfo() {
    if (transmitMediaInfo == null || mediaInfo.getPayload() ==
        RtpPacket.PCMU_PAYLOAD) {

        return mediaInfo;
    }

    int transmitSampleRate = transmitMediaInfo.getSampleRate();

    if (transmitSampleRate > mediaInfo.getSampleRate()) {
        transmitSampleRate = mediaInfo.getSampleRate();
    }

    int transmitChannels = transmitMediaInfo.getChannels();

    if (transmitChannels > mediaInfo.getChannels()) {
        transmitChannels = mediaInfo.getChannels();
    }

    try {
        transmitMediaInfo = MediaInfo.findMediaInfo(
        transmitMediaInfo.getEncoding(),
        transmitSampleRate, transmitChannels);
    } catch (IOException e) {
        Logger.println(e.getMessage());
        Logger.println("Using transmit media info " + transmitMediaInfo);
    }

    return transmitMediaInfo;
    }

    public boolean preferredMediaSpecified() {
    return preferredMediaSpecified;
    }

    public void setSynchronizationSource(int synchronizationSource) {
    Logger.println("Setting sync to " + synchronizationSource);
    this.synchronizationSource = synchronizationSource;
    }

    public int getSynchronizationSource() {
    return synchronizationSource;
    }

    public boolean isSupported(MediaInfo mediaInfo) {
    try {
        getMediaInfo(mediaInfo.getSampleRate(), mediaInfo.getChannels(),
        mediaInfo.getEncoding());
    } catch (IOException e) {
        return false;
    }

    return true;
    }

    public MediaInfo getMediaInfo(int sampleRate, int channels, int encoding)
        throws IOException {

    if (supportedMedia != null) {
        for (int i = 0; i < supportedMedia.size(); i++) {
            MediaInfo mediaInfo = (MediaInfo) supportedMedia.elementAt(i);

            if (mediaInfo.getSampleRate() == sampleRate &&
                mediaInfo.getChannels() == channels &&
                mediaInfo.getEncoding() == encoding) {

            return mediaInfo;
            }
        }
    }

    throw new IOException("No Suitable media for "
        + encoding + "/" + sampleRate + "/" + channels);
    }

    public MediaInfo findBestMediaInfo(Vector otherSupportedMedia,  MediaInfo otherMediaPreference) throws IOException {

    MediaInfo best = null;

        for (int i = 0; i < otherSupportedMedia.size(); i++) {
        MediaInfo m = (MediaInfo) otherSupportedMedia.elementAt(i);

        if (!isSupported(m)) {
        continue;
        }

        if (otherMediaPreference != null) {
        if (m.getSampleRate() > otherMediaPreference.getSampleRate() ||
                m.getChannels() > otherMediaPreference.getChannels()) {

            continue;
        }
        }

        if (best == null || isBetter(m, best)) {
        best = m;
        }
    }

    if (best == null) {
       throw new IOException("No supported Media!");
    }

    return best;
    }

    private boolean isBetter(MediaInfo m1, MediaInfo m2) {
    if (m1.getSampleRate() > m2.getSampleRate() &&
            m1.getChannels() >= m2.getChannels()) {

        return true;
    }

    if (m1.getSampleRate() == m2.getSampleRate() &&
        m1.getChannels() > m2.getChannels()) {

        return true;
    }

    return false;
    }

}
