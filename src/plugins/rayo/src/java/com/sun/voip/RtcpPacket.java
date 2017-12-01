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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RtcpPacket {
    public static final int SENDER_INTERVAL = 30;	// 30 seconds

    private static final int SENDER_REPORT_LENGTH = 52;
    private static final int RECEIVER_REPORT_LENGTH = 32;

    private DatagramPacket packet;

    protected byte[] rtcpData;
    protected SocketAddress from;

    /*
     * This constructor is used for a packet which has been received.
     */
    public RtcpPacket(DatagramPacket packet) {
    rtcpData = packet.getData();
    from = packet.getSocketAddress();
    }

    /*
     * This constructor is used to create a packet to send
     */
    public RtcpPacket(int SSRC, boolean isSenderReport) {
    if (isSenderReport) {
        rtcpData = new byte[SENDER_REPORT_LENGTH];
        rtcpData[0] = (byte) 0x81;
        rtcpData[1] = (byte) 200;
        rtcpData[2] = (byte) 0;
        rtcpData[3] = (byte) SENDER_REPORT_LENGTH;
    } else {
        rtcpData = new byte[RECEIVER_REPORT_LENGTH];
        rtcpData[0] = (byte) 0x81;
        rtcpData[1] = (byte) 201;
        rtcpData[2] = (byte) 0;
        rtcpData[3] = (byte) RECEIVER_REPORT_LENGTH;
    }

    rtcpData[4] = (byte) ((SSRC >> 24) & 0xff);
    rtcpData[5] = (byte) ((SSRC >> 16) & 0xff);
    rtcpData[6] = (byte) ((SSRC >> 8) & 0xff);
    rtcpData[7] = (byte) (SSRC & 0xff);

    packet = new DatagramPacket(rtcpData, rtcpData.length);
    }

    public byte[] getData() {
    return rtcpData;
    }

    public int getSynchronizationSource() {
        return ((rtcpData[4] << 24) & 0xff000000) | 
       ((rtcpData[5] << 16) & 0x00ff0000) | 
       ((rtcpData[6] << 8)  & 0x0000ff00) | 
       (rtcpData[7] & 0xff);
    }

    public void setSynchronizationSource(int synchronizationSource) {
        rtcpData[4] = (byte) ((synchronizationSource >> 24) & 0xff);
        rtcpData[5] = (byte) ((synchronizationSource >> 16) & 0xff);
        rtcpData[6] = (byte) ((synchronizationSource >> 8) & 0xff);
        rtcpData[7] = (byte) (synchronizationSource & 0xff);
    }

    public DatagramPacket getDatagramPacket() {
    return packet;
    }

    /**
     * Set the SocketAddress of where to send this packet.
     */
    public void setSocketAddress(InetSocketAddress isa) {
        packet.setSocketAddress(isa);
    }

    public SocketAddress getSocketAddress() {
    return packet.getSocketAddress();
    }

}
