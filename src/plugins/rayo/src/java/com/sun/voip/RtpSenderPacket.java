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

/*
 * Packet for sending RTP data
 */
public class RtpSenderPacket extends RtpPacket {
    private static boolean comfortNoise = true;

    private long packetsSent = 0;
    private long comfortPayloadSentCount = 0;
    private long timeComfortPayloadSent;

    /**
     * Initialize an Rtp sender packet.
     */
    public RtpSenderPacket(int encoding, int sampleRate, int channels) {
    super(encoding, sampleRate, channels);

        //
        // Initialize RTP header
        //
        buffer[0]  = (byte)0x80;   		  // version 2
        buffer[1]  = (byte)MARK_BIT | PCMU_PAYLOAD; // MARK bit and payload
        buffer[2]  = (byte)0;      		  // sequence #
        buffer[3]  = (byte)1;
        buffer[4]  = (byte)0;      		  // rtpTimestamp
        buffer[5]  = (byte)0;
        buffer[6]  = (byte)0;
        buffer[7]  = (byte)0;

        long now = System.currentTimeMillis();

    buffer[8]  = (byte)((now >> 24) & 0xff); // Synchronization Source SSRC id
    buffer[9]  = (byte)((now >> 16) & 0xff);
    buffer[10] = (byte)((now >> 8) & 0xff);
    buffer[11] = (byte)(now & 0xff);
    }

    /**
     * set the header fields for a comfort payload change
     *
     * After sending a comfort noise payload, 
     * we keep track of how much time has passed until we start
     * sending data again.  We then update the rtpTimestamp
     * with the number of PACKET_PERIOD samples (rounded up).
     */
    public void setComfortPayload() {
    buffer[1] = COMFORT_PAYLOAD;

    setLength(RtpPacket.HEADER_SIZE + 1);

    comfortPayloadSentCount++;

    timeComfortPayloadSent = System.currentTimeMillis();
    }

    public void setComfortNoiseLevel(byte comfortNoiseLevel) {
    buffer[RtpPacket.DATA] = comfortNoiseLevel;
    }

    /**
     * increment the RTP sequence number
     */
    public void incrementRtpSequenceNumber() {
        rtpSequenceNumber++;
        buffer[2] = (byte)((rtpSequenceNumber >> 8) & 0xff);
        buffer[3] = (byte)(rtpSequenceNumber & 0xff);
    }
    
    /**
     * Update the RTP sequence number and timestamp.  This method is called
     * after sending a packet of data.  The reason this isn't done automatically
     * is because the mixer sends packets with the same sequence # and
     * time stamp to multiple members.
     */
    public void updateRtpHeader(int size) {
    rtpSequenceNumber++;
        buffer[0] = (byte)0x80;           // version 2
        buffer[1] &= ~MARK_BIT;   	  // clear MARK bit
        buffer[2] = (byte)((rtpSequenceNumber >> 8) & 0xff);
        buffer[3] = (byte)(rtpSequenceNumber & 0xff);

        /*
         * adjust RTP header time stamp
         */
    adjustRtpTimestamp(size - HEADER_SIZE);
    }

    /**
     * Adjust the RTP Timestamp.  This method is called when we are about
     * to resume sending data after sending a COMFORT_PAYLOAD.
     *
     * Calulate the number of samples that would have been
     * sent during the time between when the comfort payload
     * was sent and now.
     *
     * The timestamp is adjusted by the amount of time we stopped sending data.
     *
     * @param adjustment long to add to the RTP timestamp.
     */
    public void adjustRtpTimestamp() {
        /*
         * Elapsed time since comfort payload sent multiplied
         * by the number of samples per second gives the
         * number of samples by which the RTP timestamp must
         * be adjusted.
         */
    if (timeComfortPayloadSent == 0) {
        Logger.error("RtpSenderPacket:  timeComfortPayloadSent is 0!");
        return;
    }

    adjustRtpTimestamp(System.currentTimeMillis() - timeComfortPayloadSent);
    timeComfortPayloadSent = 0;
    }

    /*
     * Adjust the RTP timestamp by the specified amount.
     * This adjustment is necessary after a comfort payload change
     * or when there is a long pause.
     */
    public void adjustRtpTimestamp(long elapsed) {
        int adjustment = (int)
        (elapsed * (getDataSize() / RtpPacket.PACKET_PERIOD));

        adjustment = ((adjustment + getDataSize() - 1) / getDataSize()) *
            getDataSize();

        buffer[1] |= MARK_BIT; 
    adjustRtpTimestamp(adjustment);
    }

    private void adjustRtpTimestamp(int adjustment) {
    rtpTimestamp += adjustment;

        buffer[4] = (byte)((rtpTimestamp >> 24) & 0xff);
        buffer[5] = (byte)((rtpTimestamp >> 16) & 0xff);
        buffer[6] = (byte)((rtpTimestamp >> 8) & 0xff);
        buffer[7] = (byte)(rtpTimestamp & 0xff);
    }

    public void incrementPacketsSent() {
    packetsSent++;
    }

    public long getPacketsSent() {
    return packetsSent;
    }

    public long getComfortPayloadSentCount() {
    return comfortPayloadSentCount;
    }

    /*
     * Make sure sequence number wraps properly.
     */
    public static void main(String[] args) {
    RtpSenderPacket packet = new RtpSenderPacket(
        PCM_ENCODING, 8000, 1);

    Logger.logLevel = 5;

    short expected = packet.rtpSequenceNumber;

    while (true) {
        if (packet.rtpSequenceNumber != expected) {
        Logger.println("expected " + expected
            + " got " + packet.rtpSequenceNumber);
        }

        packet.rtpSequenceNumber++;
        expected = packet.rtpSequenceNumber;
    }
    }

}
