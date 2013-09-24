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

import com.sun.voip.CallParticipant;
import com.sun.voip.Logger;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSocket;

import java.io.IOException;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import java.nio.ByteBuffer;

import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import com.sun.stun.StunServerImpl;

/**
 * Receive data from each member in a conference and dispatch it to
 * the appropriate ConferenceMember so the data can be given to the mixer.
 */
public class ConferenceReceiver extends Thread {
    /*
     * For debugging
     */
    private static int receiverPause = 0;   // ms to pause	

    private String conferenceId;

    private Selector selector;

    private StunServerImpl stunServerImpl;

    private boolean done;

    private static int loneReceiverPort = 0;

    private static DatagramChannel loneReceiverChannel;

    private int memberCount = 0;

    ConferenceReceiver(String conferenceId, int loneReceiverPort) throws SocketException {
	if (loneReceiverPort != 0) {
	    conferenceId = "TheLoneReceiver";
	    setName(conferenceId);
	} else {
	    setName("Receiver-" + conferenceId);
	}

	this.conferenceId = conferenceId;

	initLoneReceiverChannel(loneReceiverPort);

	stunServerImpl = new StunServerImpl();

	start();
    }

    private void initLoneReceiverChannel(int loneReceiverPort) {
	if (this.loneReceiverPort != loneReceiverPort && loneReceiverChannel != null) {
	    close();
	}

	this.loneReceiverPort = loneReceiverPort;

	try {
	    selector = Selector.open();
	} catch (IOException e) {
	    Logger.println("Conference receiver failed to open selector "
		+ e.getMessage());

	    return;    
	}

	if (loneReceiverPort == 0) {
	    return;
	}

	Logger.println("Init lone channel using port " + loneReceiverPort);

	try {
	    loneReceiverChannel = DatagramChannel.open();

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Opened lone receiver channel " + loneReceiverChannel);
	    }
	} catch (IOException e) {
	    Logger.println(
		"Conference receiver failed to open DatagramChannel "
		+ " " + e.getMessage());

	    return;
	}

	try {
	    loneReceiverChannel.configureBlocking(false);
	} catch (IOException e) {
	    Logger.println(
		"Conference receiver failed to configureBlocking to false "
		+ e.getMessage());
	    return;
	}

        DatagramSocket socket = loneReceiverChannel.socket();

	try {
            socket.setReceiveBufferSize(RtpSocket.MAX_RECEIVE_BUFFER);
	} catch (SocketException e) {
	    Logger.println("ConferenceReceiver failed to set receive buffer size "
		+ e.getMessage());
	    return;
	}

	try {
            socket.setSoTimeout(0);
	} catch (SocketException e) {
	    Logger.println("ConferenceReceiver failed to set timeout "
		+ e.getMessage());
	    return;
	}

	InetSocketAddress bridgeAddress = Bridge.getLocalBridgeAddress();

	InetSocketAddress isa = new InetSocketAddress(bridgeAddress.getAddress(), 
	    loneReceiverPort);

	try {
	   socket.bind(isa);
	} catch (IOException e) {
	    Logger.println(
		"Conference receiver unable to bind to " + loneReceiverPort + " "
		+ e.getMessage());
	    return;
	}

	try {
	    SelectionKey selectionKey = 
		loneReceiverChannel.register(selector, SelectionKey.OP_READ);
	} catch (Exception e) {
	    Logger.println(
		"Conference receiver unable to register:  " 
		+ e.getMessage());
	    return;
	}

	memberCount++;

	Logger.println("Lone Channel uses port " + loneReceiverPort);
    }

    public static DatagramChannel getChannel(CallParticipant cp) {
	if (loneReceiverChannel == null || cp.getPhoneNumber().indexOf("@") < 0) {
	    return null;
	}

	return loneReceiverChannel;
    }

    /*
     * We're not sure of the selector synchronization issues so we add
     * members to register to a vector and have the thread below actually
     * do the register.
     */
    private Vector<ConferenceMember> membersToRegister = new Vector();

    private Vector<ConferenceMember> membersToUnregister = new Vector();

    private HashMap<InetSocketAddress, MemberReceiver> members = new HashMap();

    /*
     * Find the MemberReceiver associated with the InetSocketAddress of the sender.
     * RTP header.
     */
    private MemberReceiver findMemberReceiver(InetSocketAddress isa) {
	synchronized (members) {
	    return members.get(isa);
	}
    }

    public void addMember(MemberReceiver memberReceiver) {
	synchronized (members) {
	    Logger.println("addMember " + memberReceiver + " "
		+ memberReceiver.getMember().getMemberSender().getSendAddress());

	    members.put(memberReceiver.getMember().getMemberSender().getSendAddress(),
		memberReceiver);
	}
    }

    public void addMember(ConferenceMember member) throws IOException {
	CallParticipant cp = member.getCallParticipant();

	if (loneReceiverChannel != null && cp.getPhoneNumber().indexOf("@") >= 0) {
	    return;
	}

	synchronized(membersToRegister) {
	    if (selector == null) {
		return;
	    }

	    membersToRegister.add(member);
	    Logger.writeFile("ConferenceReceiver Adding member to register " 
		+ member + " size " + membersToRegister.size());
	    selector.wakeup();
	}
    }

    public void removeMember(ConferenceMember member) {
	CallParticipant cp = member.getCallParticipant();

	if (loneReceiverChannel != null) {
	    synchronized (members) {
	        if (members.remove(member.getMemberSender().getSendAddress()) != null) {
		    return;
	        }
	    }
	}

	synchronized(membersToRegister) {
	    if (selector == null) {
		return;
	    }

	    membersToUnregister.add(member);
	    Logger.writeFile("ConferenceReceiver adding member to unregister " 
		+ member + " size " + membersToUnregister.size());
	    selector.wakeup();
	}
    }

    private void registerMembers() {
	synchronized(membersToRegister) {
	    for (int i = 0; i < membersToRegister.size(); i++) {
		ConferenceMember member = 
		    (ConferenceMember) membersToRegister.get(i);

	    	Logger.writeFile("ConferenceReceiver registering " + member);

		try {
		    member.getMemberReceiver().register(selector);
		    memberCount++;
		} catch (Exception e) {
		    Logger.println(
			"ConferenceReceiver failed to register member "
			+ member + " " + e.getMessage());

		    membersToRegister.remove(member);

		    if (member.getCallHandler() != null) {
		        member.getCallHandler().cancelRequest(
			    "ConferenceReceiver failed to register member ");
		    }
		}
	    }

	    membersToRegister.clear();

            for (int i = 0; i < membersToUnregister.size(); i++) {
                ConferenceMember member =
                    (ConferenceMember) membersToUnregister.get(i);

                Logger.writeFile("ConferenceReceiver unregistering " + member);

                member.getMemberReceiver().unregister();
		memberCount--;
            }

	    membersToUnregister.clear();
        }
    }

    /**
     * Receive data and dispatch the data to the appropriate member.
     */
    public void run() {
        while (!done) {
            try {
		registerMembers();

		/* 
		 * Wait for packets to arrive
		 */
		int n;

		if ((n = selector.select()) <= 0) {
		    if (Logger.logLevel == -1) {
		        Logger.println("select returned " + n
			    + " isOpen " + selector.isOpen());

			Logger.println("membersToRegister size " 
			    + membersToRegister.size()
			    + " membersToUnregister size " 
			    + membersToUnregister.size());

			Logger.println("keys size " + selector.keys().size()
			    + " member count " + memberCount);
		    }
		    continue;
		}

		if (Logger.logLevel == -1) {
	            if (memberCount != selector.keys().size()) {
		        Logger.println("memberCount " + memberCount
			    + " not equal to selector key count " 
			    + selector.keys().size());
		    }
		}

                Iterator it = selector.selectedKeys().iterator();

        	byte[] data = new byte[RtpPacket.getMaxDataSize()];
		int dataLength;
    		InetSocketAddress isa;
    		MemberReceiver memberReceiver;

                while (it.hasNext()) {
		    try {
                        SelectionKey sk = (SelectionKey)it.next();

                        it.remove();

                        DatagramChannel datagramChannel = (DatagramChannel)sk.channel();

	    		ByteBuffer byteBuffer = ByteBuffer.wrap(data);

	    		isa = (InetSocketAddress) datagramChannel.receive(byteBuffer);

			dataLength = byteBuffer.position();

	    	    	if (isStunBindingRequest(data) == true) {
			    stunServerImpl.processStunRequest(datagramChannel, isa, data);
			    continue;
	    	    	}

                        memberReceiver = (MemberReceiver) sk.attachment();

                        if (memberReceiver == null) {
			    memberReceiver = findMemberReceiver(isa);

			    if (memberReceiver == null) {
			        if (Logger.logLevel > Logger.LOG_DETAILINFO) {
			            Logger.println("ConferenceReceiver couldn't find "
				        + "member associated with packet! " + isa);
				}
			        continue;
			    }
			}

		        if (memberReceiver.readyToReceiveData() == false) {
			    if (memberReceiver.traceCall() || Logger.logLevel == -11) {
			        Logger.println("receiver not ready, conference "
				    + conferenceId + " " + memberReceiver
				    + " address " + memberReceiver.getReceiveAddress());
	    		    }
			    continue;
		        }
		    } catch (NullPointerException e) {
			e.printStackTrace();
                	/*
                 	 * It's possible to get a null pointer exception when
                 	 * end is called.  The way to avoid this non-fatal error
                 	 * is to synchronize on selector.  
			 * Catching the exception eliminates the overhead 
			 * of synchonization in the main receiver loop.
                         */
                        if (!done) {
                            Logger.println(
				"ConferenceReceiver:  non-fatal NPE.");
                	}
			System.exit(1);
			continue;
            	    }

		    if (memberReceiver.traceCall()) {
			Logger.println("Received data for " + memberReceiver);
		    }

		    long start = 0;

		    if (memberReceiver.traceCall()) {
		        start = System.nanoTime();
		    }

		    /*
		     * Dispatch to member
		     */
		    memberReceiver.receive(isa, data, dataLength);

		    if (memberReceiver.traceCall()) {
			memberReceiver.traceCall(false);

			Logger.println("Call " + memberReceiver + " receive time "
			    + ((System.nanoTime() - start) / 1000000000.) 
			    + " seconds");
		    }
		}

                /*  
                 * XXX For debugging
                 */
                if (receiverPause != 0) {
		    if (receiverPause >= 20) {
		        Logger.println("pause Receiving " 
			    + receiverPause + "ms");
		    }

                    long start = System.currentTimeMillis();

                    while (System.currentTimeMillis() - start < receiverPause)
                        ;

		    if (receiverPause >= 20) {
		        receiverPause = 0;
		    }
		}
            } catch (IOException e) {
		if (!done) {
		    /*
		     * We're not sure why this happens but there appears to be
		     * a timing problem with selectors when a call ends.
		     */
                    Logger.error("ConferenceReceiver:  receive failed! " + 
		        e.getMessage());
		    e.printStackTrace();
		}
            } catch (Exception e) {
		if (!done) {
                    Logger.error("ConferenceReceiver:  unexpected exception " 
			+ e.getMessage());
		    e.printStackTrace();
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

    public static void setReceiverPause(int receiverPause) {
	ConferenceReceiver.receiverPause = receiverPause;	
    }

    /*
     * finished
     */
    public void end() {
	Logger.writeFile("Conference receiver done " + conferenceId);

        done = true;

	close();
    }

    private void close() {
        synchronized(membersToRegister) {
	    if (selector != null) {
	        try {
	            selector.close();
	        } catch (IOException e) {
		    Logger.println(
			"Conference receiver failed to close selector "
			+ conferenceId + " " + e.getMessage());
	    	}
	        selector = null;
	    }
	}

	if (loneReceiverChannel != null) {
	    try {
		loneReceiverChannel.close();
	        if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Closed lone receiver channel " 
			+ loneReceiverChannel);
		}
	    } catch (Exception e) {
		Logger.println("Exception closing lone receiver channel:  " 
		    + e.getMessage());
	    }
	}
    }

}
