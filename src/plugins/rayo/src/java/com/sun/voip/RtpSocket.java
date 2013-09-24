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

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.SocketException;

import java.io.IOException;

/**
 * RtpSocket has one DatagramSocket for RTP and one for RTCP.
 * RTP and RTCP sockets come in pairs.  RTP must use an even port number
 * and RTCP uses the RTP port number plus 1.
 *
 * It would be possible to extend Datagram socket if we had a contructor
 * requiring the caller to specify an even port number.  Instead we find
 * two available DatagramSockets with an even and odd port number.
 *
 * RTCP is automatically started and received information is optionally printed.
 */
public class RtpSocket {
    /*
     * The default limit on Solaris is 256kb.  To change this limit, use 
     * ndd /dev/udp and set udp_max_buf to what you want.
     */
    public static final int MAX_SEND_BUFFER = (10 * 1024 * 1024);  // 10mb

    public static final int MAX_RECEIVE_BUFFER = (10 * 1024 * 1024);  // 10mb

    DatagramSocket rtpDatagramSocket;		// Socket for RTP
    DatagramSocket rtcpDatagramSocket;		// Socket for RTCP

    RtcpReceiver rtcpReceiver;			// handle RTCP reports

    private static int rtpTimeout = 330;	// 330 seconds (5 1/2 minutes)

    static {
	String s = System.getProperty("com.sun.voip.RTP_TIMEOUT");

	if (s != null) {
	    int timeout = rtpTimeout;

	    try {
	        timeout = Integer.parseInt(s);

		if (timeout < 0) {
		    Logger.println("Invalid RTP Timeout, using "
			+ rtpTimeout);
		} else {
		    rtpTimeout = timeout;
		}
	    } catch (NumberFormatException e) {
		Logger.println("Invalid RTP Timeout, using "
		    + rtpTimeout);
	    }
	}
    }
    
    public RtpSocket(InetAddress ia, int port) throws SocketException {
	int p = port;

	if ((p & 1) != 0) {
	    p++;
	    Logger.println("Port number must be even, using " + p);
	}

        while (true) {
            try {
		rtpDatagramSocket = new DatagramSocket(p, ia);

                /*
                 * RTP/RTCP ports come in pairs and we need an even port number
		 * for RTP.
                 */
                if ((rtpDatagramSocket.getLocalPort() & 1) != 0) {
                    continue;       // got an odd port number, try again
                }

                try {
                    rtcpDatagramSocket = new DatagramSocket(
			rtpDatagramSocket.getLocalPort() + 1, ia);

		    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                        Logger.println(
		    	    "RtpSocket:  listening for RTP data at local port " 
		            + rtpDatagramSocket.getLocalPort());
		    }
                    break;
                } catch (SocketException e) {
		    /*
		     * odd port number is in use, try again.
		     */
		    if (p != 0) {
			p += 2;
		    }
                } catch (Exception e) {
                    rtpDatagramSocket.close();
                    Logger.error(
			"RtpSocket:  Unable to create control socket! " 
			+ e.getMessage());
                    throw e;
                }
	    } catch (SocketException e) {
	        /*
	         * port number is in use, try again.
	         */
		if (p != 0) {
		    p += 2;
		}
		continue;
            } catch (Exception e) {
                Logger.error("RtpSocket:  Unable to create RTP/RTCP sockets! " 
	            + e.getMessage());
                throw new SocketException(
		    "RtpSocket:  Unable to create RTP/RTCP sockets!");
            }
	}

	if (p != port) {
	    System.out.println("RtpSocket:  Desired port " + port 
		+ " is in use.  Using " + p + " instead.");
	}

	try {
	    rtpDatagramSocket.setReceiveBufferSize(MAX_RECEIVE_BUFFER);
        } catch (SocketException e) {
            Logger.error("RtpSocket:  Unable to set receive buffer size! "
	        + e.getMessage());

	    throw e;
	}

	try {
	    rtpDatagramSocket.setSendBufferSize(MAX_SEND_BUFFER);
        } catch (SocketException e) {
            Logger.error("RtpSocket:  Unable to set send buffer size! "
	        + e.getMessage());

	    throw e;
	}

	try {
	    rtpDatagramSocket.setSoTimeout(0);
        } catch (SocketException e) {
            Logger.error("RtpSocket:  Unable to set socket timeout! "
	        + e.getMessage());

	    throw e;
	}
    }

    public void startRtcpReceiver() {
	/*
	 * Start the RTCP receiver
	 */
	rtcpReceiver = new RtcpReceiver(rtcpDatagramSocket, true);
    }

    /**
     * Return the local address for the RTP socket
     *
     * @return InetSocketAddress local address and port for the socket
     */
    public InetSocketAddress getInetSocketAddress() {
	return new InetSocketAddress(rtpDatagramSocket.getLocalAddress(),
	    rtpDatagramSocket.getLocalPort());
    }

    public DatagramSocket getDatagramSocket() {
	return rtpDatagramSocket;
    }

    /**
     * Receives an RtpPacket from this socket. When this method returns, 
     * the RtpPacket's buffer is filled with the data received. 
     * The packet also contains the sender's IP address, and the port number 
     * on the sender's machine.
     *
     * This method blocks until data is received. The length field 
     * of the RtpPacket object contains the length of the received message. 
     * If the message is longer than the packet's length, 
     * the message is truncated.
     *
     * @param p the RtpPacket into which to place the incoming data.
     */
    public int receive(RtpPacket p) throws IOException {
	if (rtpDatagramSocket == null) {
	    throw new IOException("RtpSocket receive failed, socket closed");
	}

	rtpDatagramSocket.receive(p.getDatagramPacket());
	return p.getDatagramPacket().getLength();
    }

    /**
     * Sends an RtpPacket from this socket. The RtpPacket includes 
     * information indicating the data to be sent, its length, 
     * the IP address of the remote host, and the port number 
     * on the remote host.
     *
     * @param RtpPacket the packet to be sent
     */
    public void send(RtpPacket p) throws IOException {
	if (rtpDatagramSocket == null) {
	    throw new IOException("RtpSocket send failed, socket closed");
	}

	rtpDatagramSocket.send(p.getDatagramPacket());
    }

    /**
     * Send an Rtcp packet
     */
    public void send(RtcpPacket p) throws IOException {
	if (rtcpDatagramSocket == null) {
	    throw new IOException("RtcpSocket send failed, socket closed");
	}

	rtcpDatagramSocket.send(p.getDatagramPacket());
    }

    /**
     * Set socket timeout
     *
     * @param timeout millisecond timeout value
     */
    public void setSoTimeout(int timeout) throws SocketException {
        if (rtpDatagramSocket == null) {
	    throw new SocketException("rtpDatagramSocket is null");
	}

	rtpDatagramSocket.setSoTimeout(timeout);
    }

    public boolean isClosed() {
        if (rtpDatagramSocket == null) {
            return true;
        }

        return rtpDatagramSocket.isClosed();
    }

    public void flushSocket() {
	if (rtpDatagramSocket == null) {
	    return;
	}

	flushSocket(rtpDatagramSocket);
    }

    public static void flushSocket(DatagramSocket socket) {
	/*
	 * Flush the socket
	 */
	int len = RtpPacket.getDataSize(
	    RtpPacket.PCM_ENCODING, RtpPacket.MAX_SAMPLE_RATE, 2);

	len += RtpPacket.HEADER_SIZE;

        byte[] data = new byte[len];

	DatagramPacket packet = new DatagramPacket(data, len);

        int count = 0;

	try {
	    socket.setSoTimeout(1);

	    while (true) {
	        try {
                    socket.receive(packet);
		    count++;
	        } catch (SocketTimeoutException e) {
		    break;
	        } catch (IOException e) {
		    Logger.println("Error flushing socket " + e.getMessage());
		    break;
		}
            }
	} catch (SocketException e) {
	    Logger.println("Can't flush receiver socket!");
	}

	if (count > 0) {
	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("Packets flushed:  " + count);
	    }
	}

	try {
	    socket.setSoTimeout(0);
	} catch (SocketException e) {
	    Logger.println("Can't set socket timeout to 0!");
	}
    }

    public static int getRtpTimeout() {
	return rtpTimeout;
    }

    public static void setRtpTimeout(int rtpTimeout) {
	RtpSocket.rtpTimeout = rtpTimeout;
    }

    public DatagramSocket getRtcpDatagramSocket() {
	return rtcpDatagramSocket;
    }
	
    /**
     * Close the RTP socket, stop the RtcpReceiver and close the RTCP socket.
     */
    public void close() {
	if (rtpDatagramSocket != null) {
	    rtpDatagramSocket.close();
	    rtpDatagramSocket = null;
	}

	if (rtcpReceiver != null) {
	    rtcpReceiver.end();
	    rtcpDatagramSocket.close();
	    rtcpReceiver = null;
	}
    }

}
