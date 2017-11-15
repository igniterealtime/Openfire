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

import java.util.ArrayList;

public class MediaInfo {

    private byte payload;
    private int encoding;
    private int sampleRate;
    private int channels;

    private boolean isTelephoneEventPayload;

    private int samplesPerPacket;

    private static ArrayList supportedMedia = new ArrayList();

    static {
    supportedMedia.add(new MediaInfo(
            (byte)0, RtpPacket.PCMU_ENCODING, 8000, 1, true));

        //supportedMedia.add(new MediaInfo(
        //    (byte)101, RtpPacket.PCM_ENCODING, 8000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)102, RtpPacket.PCM_ENCODING, 8000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)103, RtpPacket.PCM_ENCODING, 16000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)104, RtpPacket.PCM_ENCODING, 16000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)105, RtpPacket.PCM_ENCODING, 32000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)106, RtpPacket.PCM_ENCODING, 32000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)107, RtpPacket.PCM_ENCODING, 44100, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)108, RtpPacket.PCM_ENCODING, 44100, 2, false));

if (false) {
        supportedMedia.add(new MediaInfo(
            (byte)109, RtpPacket.PCM_ENCODING, 48000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)110, RtpPacket.PCM_ENCODING, 48000, 2, false));
}

        supportedMedia.add(new MediaInfo(
            (byte)111, RtpPacket.PCM_ENCODING, 48000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)112, RtpPacket.PCMU_ENCODING, 16000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)113, RtpPacket.PCMU_ENCODING, 16000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)114, RtpPacket.PCMU_ENCODING, 32000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)115, RtpPacket.PCMU_ENCODING, 32000, 2, false));

if (false) {
        supportedMedia.add(new MediaInfo(
            (byte)116, RtpPacket.PCMU_ENCODING, 44100, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)117, RtpPacket.PCMU_ENCODING, 44100, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)118, RtpPacket.PCMU_ENCODING, 48000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)119, RtpPacket.PCMU_ENCODING, 48000, 2, false));
}

        supportedMedia.add(new MediaInfo(
            (byte)120, RtpPacket.SPEEX_ENCODING, 8000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)121, RtpPacket.SPEEX_ENCODING, 8000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)122, RtpPacket.SPEEX_ENCODING, 16000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)123, RtpPacket.SPEEX_ENCODING, 16000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)124, RtpPacket.SPEEX_ENCODING, 32000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)125, RtpPacket.SPEEX_ENCODING, 32000, 2, false));
    }

    public MediaInfo(byte payload , int encoding, int sampleRate, int channels, boolean isTelephoneEventPayload)
    {
        this.payload = payload;
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.isTelephoneEventPayload = isTelephoneEventPayload;

        samplesPerPacket = sampleRate * channels / (1000 / RtpPacket.PACKET_PERIOD);
    }

    public static MediaInfo findMediaInfo(int encoding, int sampleRate,
    int channels) throws IOException {

    for (int i = 0; i < supportedMedia.size(); i++) {
        MediaInfo mediaInfo = (MediaInfo) supportedMedia.get(i);

        if (mediaInfo.getEncoding() == encoding &&
            mediaInfo.getSampleRate() == sampleRate &&
            mediaInfo.getChannels() == channels) {

        return mediaInfo;
        }
    }

    throw new IOException("Unsupported media " + encoding
        + "/" + sampleRate + "/" + channels);
    }

    public static MediaInfo findMediaInfo(byte payload) throws IOException {
    for (int i = 0; i < supportedMedia.size(); i++) {
        MediaInfo mediaInfo = (MediaInfo) supportedMedia.get(i);

        if (mediaInfo.getPayload() == payload) {
        return mediaInfo;
        }
    }

    throw new IOException("Unsupported payload " + payload);
    }

    public byte getPayload() {
    return payload;
    }

    public int getEncoding() {
    return encoding;
    }

    public String getEncodingString() {
    if (encoding == RtpPacket.PCMU_ENCODING) {
        return "PCMU";
    }

    if (encoding == RtpPacket.PCM_ENCODING) {
        return "PCM";
    }

    return "SPEEX";
    }

    public int getSampleRate() {
    return sampleRate;
    }

    public int getChannels() {
    return channels;
    }

    public int getSamplesPerPacket() {
    return samplesPerPacket;
    }

    public boolean isTelephoneEventPayload() {
    return isTelephoneEventPayload;
    }

    public String toString() {
    String s = "PCMU";

    if (encoding == RtpPacket.PCM_ENCODING) {
        s = "PCM";
    } else if (encoding == RtpPacket.SPEEX_ENCODING) {
        s = "SPEEX";
    }

    return payload + ":" + s + "/" + sampleRate + "/" + channels;
    }

}
