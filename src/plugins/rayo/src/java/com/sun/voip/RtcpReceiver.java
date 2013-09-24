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
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import java.util.HashMap;

import com.sun.stun.StunServerImpl;

public class RtcpReceiver extends Thread {
    private boolean done;
    private DatagramSocket rtcpSocket;
    private byte[] rtcpData;

    private StunServerImpl stunServerImpl;

    /*
     * The rtcpSocket needs to be passed in here because
     * it is obtained the same the the rtpSocket is obtained
     * and ports for the two socket must be an even/odd pair.
     */
    public RtcpReceiver(DatagramSocket rtcpSocket, boolean loneChannel) {
	this.rtcpSocket = rtcpSocket;

	if (loneChannel) {
	    timeLastReceivedMap = new HashMap();
	}

        stunServerImpl = new StunServerImpl();

	setName("RtcpReceiver-" + rtcpSocket.getLocalPort());
	setPriority(Thread.NORM_PRIORITY);
        start();
    }

    public void end() {
	done = true;
	rtcpSocket.close();
    }

    private HashMap<String, Long> timeLastReceivedMap;

    private long timeLastReceived;

    /*
     * Receive both sender and receiver reports.
     */
    public void run() {
        rtcpData = new byte[1500];

        DatagramPacket packet = new DatagramPacket(rtcpData, rtcpData.length);

        while (!done) {
            try {
                rtcpSocket.receive(packet);

		if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Got RTCP Packet from " + packet.getSocketAddress());
		}

		byte[] data = packet.getData();

		RtcpPacket rtcpPacket = null;

		if (isStunBindingRequest(data) == true) {
                    stunServerImpl.processStunRequest(rtcpSocket, packet);
                    continue;
                }

		if ((data[1] & 0xff) == 200) {
	            rtcpPacket = new RtcpSenderPacket(packet);
		    ((RtcpSenderPacket)rtcpPacket).printReport();
		} else if ((data[1] & 0xff) == 201) {
	    	    rtcpPacket = new RtcpReceiverPacket(packet);
		    ((RtcpReceiverPacket)rtcpPacket).printReport();
		} else {
	    	    Util.dump("unknown RTCP packet", data, 0, 16);
		}

		if (rtcpPacket != null) {
		    timeLastReceived = System.currentTimeMillis();

		    if (timeLastReceivedMap != null) {
			if (Logger.logLevel >= Logger.LOG_INFO) {
			    Logger.println("Updated map for " + packet.getSocketAddress() 
			        + " " + timeLastReceived);
			}

		        synchronized (timeLastReceivedMap) {
			    timeLastReceivedMap.put(packet.getSocketAddress().toString(),
			        new Long(timeLastReceived));
		        }
		    }
		}
            } catch (Exception e) {
                if (!done) {
                    Logger.error("RtcpReceiver:  receive failed! " 
			+ e.getMessage());
		    end();
		}
            }
        }
    }

    private boolean isStunBindingRequest(byte[] data) {
        /*
         * If this is an RTP packet, the first byte
         * must have bit 7 set indicating RTP v2.
         * If byte 0 is 0 and byte 1 is 1, then we
         * assume this packet is a STUN Binding request.
         */
        return data[0] == 0 && data[1] == 1;
    }

    public long secondsSinceLastReport(InetSocketAddress isa) {
	if (timeLastReceivedMap != null && isa == null) {
	    return 0;
	}

	long now = System.currentTimeMillis();

	if (timeLastReceivedMap == null) {
	    if (timeLastReceived == 0) {
		timeLastReceived = now;
	    }

	    long elapsed = now - timeLastReceived;

	    timeLastReceived = now;
	    return elapsed;
	}

	Long t;

	synchronized (timeLastReceivedMap) {
            t = timeLastReceivedMap.get(isa.toString());
	}

	if (t == null) {
	    synchronized (timeLastReceivedMap) {
                if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Putting " + isa);
		}

	        timeLastReceivedMap.put(isa.toString(), new Long(now));
	    }
	    
	    return 0;
	}

	return (now - t.longValue()) / 1000;
    }

}
