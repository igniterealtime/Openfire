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
     * Receiver Report
     * 
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |V=2|P|    RC   |   PT=RR=201   |             length            | header
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                     SSRC of packet sender                     |
     * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
     * |                 SSRC_1 (SSRC of first source)                 | report
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ block
     * | fraction lost |       cumulative number of packets lost       |   1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
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

public class RtcpReceiverPacket extends RtcpPacket {

    /*
     * This constructor is used for a packet which has been received.
     */
    public RtcpReceiverPacket(DatagramPacket packet) {
	super(packet);
    }

    /*
     * This constructor is used to create a packet to send
     */
    public RtcpReceiverPacket(int SSRC) {
	super(SSRC, false);
	setSSRC_1(SSRC);
    }

    public void setSSRC_1(int SSRC) {
        rtcpData[8] = (byte) ((SSRC >> 24) & 0xff);
        rtcpData[9] = (byte) ((SSRC >> 16) & 0xff);
        rtcpData[10] = (byte) ((SSRC >> 8) & 0xff);
        rtcpData[11] = (byte) (SSRC & 0xff);
    }
 
    public void setFractionLost(byte fractionLost) {
	rtcpData[12] = fractionLost;
    }

    public byte getFractionLost() {
	return rtcpData[12];
    }

    public void setCumulativeLost(int cumulativeLost) {
        rtcpData[13] = (byte) ((cumulativeLost >> 16) & 0xff);
        rtcpData[14] = (byte) ((cumulativeLost >> 8) & 0xff);
        rtcpData[15] = (byte) (cumulativeLost & 0xff);
    }

    public int getCumulativeLost() {
	int cumulativeLost = ((((int)rtcpData[13]) << 16) & 0x00ff0000) |
                             ((((int)rtcpData[14]) << 8) & 0x0000ff00) |
                             (rtcpData[15] & 0xff);

        if ((rtcpData[13] & 0x80) != 0) {
            cumulativeLost |= 0xff000000;       // it's negative
        }

	return cumulativeLost;
    }

    public void setHighestSeqReceived(int seq) {
        rtcpData[16] = (byte) ((seq >> 24) & 0xff);
        rtcpData[17] = (byte) ((seq >> 16) & 0xff);
        rtcpData[18] = (byte) ((seq >> 8) & 0xff);
        rtcpData[19] = (byte) (seq & 0xff);
    }

    public int getHighestSeqReceived() {
	return ((((int)rtcpData[16]) << 24) & 0xff000000) |
               ((((int)rtcpData[17]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[18]) << 8)  & 0x0000ff00) |
               (rtcpData[19] & 0xff);
    }

    public void setInterArrivalJitter(int interArrivalJitter) {
        rtcpData[20] = (byte) ((interArrivalJitter >> 24) & 0xff);
        rtcpData[21] = (byte) ((interArrivalJitter >> 16) & 0xff);
        rtcpData[22] = (byte) ((interArrivalJitter >> 8) & 0xff);
        rtcpData[23] = (byte) (interArrivalJitter  & 0xff);
    }

    public int getInterArrivalJitter() {
	return ((((int)rtcpData[20]) << 24) & 0xff000000) |
               ((((int)rtcpData[21]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[22]) << 8)  & 0x0000ff00) |
               (rtcpData[23] & 0xff);
    }

    public void setLSR(int LSR) {
        rtcpData[24] = (byte) ((LSR >> 24) & 0xff);
        rtcpData[25] = (byte) ((LSR >> 16) & 0xff);
        rtcpData[26] = (byte) ((LSR >> 8) & 0xff);
        rtcpData[27] = (byte) (LSR & 0xff);
    }

    public int getLSR() {
	return ((((int)rtcpData[24]) << 24) & 0xff000000) |
               ((((int)rtcpData[25]) << 16) & 0x00ff0000) |
               ((((int)rtcpData[26]) << 8)  & 0x0000ff00) |
               (rtcpData[27] & 0xff);
    }

    public void setDLSR(int DLSR) {
        rtcpData[28] = (byte) ((DLSR >> 24) & 0xff);
        rtcpData[29] = (byte) ((DLSR >> 16) & 0xff);
        rtcpData[30] = (byte) ((DLSR >> 8) & 0xff);
        rtcpData[31] = (byte) (DLSR & 0xff);
    }

    public void printReport() {
	if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.writeFile(" RTCP Receiver report:  "
	        + " from " + from
                + ", fractionLost " + getFractionLost()
                + ", highest sequence "
                + Integer.toHexString(getHighestSeqReceived())
                + ", cumulativeLost " + getCumulativeLost()
                + ", jitter " + getInterArrivalJitter()
                + " LSR " + getLSR());
	}
    }

}
