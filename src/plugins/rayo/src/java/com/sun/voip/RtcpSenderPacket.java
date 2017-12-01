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

import java.net.DatagramPacket;

    /*
     * From RFC1889
     *
     * SR: Sender report RTCP packet
     * 
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |V=2|P|    RC   |   PT=SR=200   |             length            | header
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                         SSRC of sender                        |
     * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
     * |              NTP timestamp, most significant word             | sender
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ info
     * |             NTP timestamp, least significant word             |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                         RTP timestamp                         |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                     sender's packet count                     |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                      sender's octet count                     |
     * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
     * |                 SSRC_1 (SSRC of first source)                 | report
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ block
     * | fraction lost |       cumulative number of packets lost       |   1
     * -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           extended highest sequence number received           |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                      interarrival jitter                      |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                         last SR (LSR)                         |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                   delay since last SR (DLSR)                  |
     * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
     * |                 SSRC_2 (SSRC of second source)                | report
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ block
     * :                               ...                             :   2
     * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
     * |                  profile-specific extensions                  |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

public class RtcpSenderPacket extends RtcpPacket {

    /*
     * This constructor is used for a packet which has been received.
     */
    public RtcpSenderPacket(DatagramPacket packet) {
    super(packet);
    }

    /*
     * This constructor is used to create a packet to send
     */
    public RtcpSenderPacket(int SSRC) {
    super(SSRC, true);
    setSSRC_1(SSRC);
    }

    public void setNTPTimestamp(long ts) {
    rtcpData[8] = (byte)  ((ts >> 56) & 0xff);
    rtcpData[9] = (byte)  ((ts >> 48) & 0xff);
    rtcpData[10] = (byte) ((ts >> 40) & 0xff);
    rtcpData[11] = (byte) ((ts >> 32) & 0xff);
    rtcpData[12] = (byte) ((ts >> 24) & 0xff);
    rtcpData[13] = (byte) ((ts >> 16) & 0xff);
    rtcpData[14] = (byte) ((ts >> 8) & 0xff);
    rtcpData[15] = (byte) (ts & 0xff);
    }

    public void setRTPTimestamp(int ts) {
    rtcpData[16] = (byte) ((ts >> 24) & 0xff);
    rtcpData[17] = (byte) ((ts >> 16) & 0xff);
    rtcpData[18] = (byte) ((ts >> 8) & 0xff);
    rtcpData[19] = (byte) (ts & 0xff);
    }

    public int getRTPTimestamp() {
        return ((((int)rtcpData[16]) << 24) & 0xff000000) |
               ((((int)rtcpData[17]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[18]) << 8)  & 0x0000ff00) |
               (rtcpData[19] & 0xff);
    }

    public void setPacketCount(int packetCount) {
    rtcpData[20] = (byte) ((packetCount >> 24) & 0xff);
    rtcpData[21] = (byte) ((packetCount >> 16) & 0xff);
    rtcpData[22] = (byte) ((packetCount >> 8) & 0xff);
    rtcpData[23] = (byte) (packetCount & 0xff);
    }

    public int getPacketCount() {
        return ((((int)rtcpData[20]) << 24) & 0xff000000) |
               ((((int)rtcpData[21]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[22]) << 8)  & 0x0000ff00) |
               (rtcpData[23] & 0xff);
    }

    public void setOctetCount(int octetCount) {
    rtcpData[24] = (byte) ((octetCount >> 24) & 0xff);
    rtcpData[25] = (byte) ((octetCount >> 16) & 0xff);
    rtcpData[26] = (byte) ((octetCount >> 8) & 0xff);
    rtcpData[27] = (byte) (octetCount >> 0xff);
    }

    public int getOctetCount() {
    return ((((int)rtcpData[24]) << 24) & 0xff000000) |
               ((((int)rtcpData[25]) << 16) & 0x00ff0000) |
           ((((int)rtcpData[26]) << 8)  & 0x0000ff00) |
               (rtcpData[27] & 0xff);
    }

    public void setSSRC_1(int SSRC) {
    rtcpData[28] = (byte) ((SSRC >> 24) & 0xff);
    rtcpData[29] = (byte) ((SSRC >> 16) & 0xff);
    rtcpData[30] = (byte) ((SSRC >> 8) & 0xff);
    rtcpData[31] = (byte) (SSRC & 0xff);
    }

    public void setFractionLost(byte fractionLost) {
    rtcpData[32] = fractionLost;
    }

    public byte getFractionLost() {
    return rtcpData[32];
    }

    public void setCumulativeLost(int cumulativeLost) {
    rtcpData[33] = (byte) ((cumulativeLost >> 16) & 0xff);
    rtcpData[34] = (byte) ((cumulativeLost >> 8) & 0xff);
    rtcpData[35] = (byte) (cumulativeLost & 0xff);
    }

    public int getCumulativeLost() {
    int cumulativeLost = ((((int)rtcpData[33]) << 16) & 0x00ff0000) |
                             ((((int)rtcpData[34]) << 8) & 0x0000ff00) |
                             (rtcpData[35] & 0xff);

        if ((rtcpData[33] & 0x80) != 0) {
            cumulativeLost |= 0xff000000;       // it's negative
        }

    return cumulativeLost;
    }

    public void setHighestSeqReceived(int seq) {
    rtcpData[36] = (byte) ((seq >> 24) & 0xff);
    rtcpData[37] = (byte) ((seq >> 16) & 0xff);
    rtcpData[38] = (byte) ((seq >> 8) & 0xff);
    rtcpData[39] = (byte) (seq & 0xff);
    }

    public int getHighestSeqReceived() {
        return ((((int)rtcpData[36]) << 24) & 0xff000000) |
               ((((int)rtcpData[37]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[38]) << 8)  & 0x0000ff00) |
               (rtcpData[39] & 0xff);
    }

    public void setInterArrivalJitter(int interArrivalJitter) {
    rtcpData[40] = (byte) ((interArrivalJitter >> 24) & 0xff);
    rtcpData[41] = (byte) ((interArrivalJitter >> 16) & 0xff);
    rtcpData[42] = (byte) ((interArrivalJitter >> 8) & 0xff);
    rtcpData[43] = (byte) (interArrivalJitter  & 0xff);
    }

    public int getInterArrivalJitter() {
        return ((((int)rtcpData[40]) << 24) & 0xff000000) |
               ((((int)rtcpData[41]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[42]) << 8)  & 0x0000ff00) |
               (rtcpData[43] & 0xff);
    }

    public void setLSR(int LSR) {
    rtcpData[44] = (byte) ((LSR >> 24) & 0xff);
    rtcpData[45] = (byte) ((LSR >> 16) & 0xff);
    rtcpData[46] = (byte) ((LSR >> 8) & 0xff);
    rtcpData[47] = (byte) (LSR & 0xff);
    }

    public int getLSR() {
        return ((((int)rtcpData[44]) << 24) & 0xff000000) |
               ((((int)rtcpData[45]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[46]) << 8)  & 0x0000ff00) |
               (rtcpData[47] & 0xff);

    }
    public void setDLSR(int DLSR) {
    rtcpData[48] = (byte) ((DLSR >> 24) & 0xff);
    rtcpData[49] = (byte) ((DLSR >> 16) & 0xff);
    rtcpData[50] = (byte) ((DLSR >> 8) & 0xff);
    rtcpData[51] = (byte) (DLSR & 0xff);
    }

    public void printReport() {
    if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.writeFile(" RTCP Sender report:  from " + from
                + ", RTP Timestamp " + Integer.toHexString(getRTPTimestamp())
                + ", packets " + getPacketCount()
                + ", octets " + getOctetCount()
                + ", fractionLost " + getFractionLost()
                + ", cumulativeLost " + getCumulativeLost()
                + ", highest seq "
                + Integer.toHexString(getHighestSeqReceived())
                + ", jitter " + getInterArrivalJitter());
    }
    }

}
