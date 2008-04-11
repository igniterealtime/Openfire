/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.openfire.test.throttle;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.FromMatchesFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.packet.Time;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple client to test XMPP server throttling. When server throttling is working
 * properly, a server should slow down incoming packets to match the speed of outgoing
 * packets (otherwise, memory would fill up until a server crash).<p/>
 *
 * This client should be deployed as follows:
 * <pre>
 * [ writer ] -- fast connection --> [ xmpp server ] -- slow connection --> reader
 * </pre>
 *
 * A good way to simulate fast and slow connections is to use virtual machines where
 * the network interface speed can be set (to simulate a modem, etc).
 *
 * java ThrottleTestReader [server] [username] [password]
 *
 * @author Matt Tucker
 */
public class ThrottleTestReader {

    private static AtomicInteger packetCount = new AtomicInteger(0);
    private static boolean done = false;

    /**
     * Starts the throttle test reader client.
     *
     * @param args application arguments.
     */
    public static void main(String [] args) {
        if (args.length != 3) {
            System.out.println("Usage: java ThrottleTestReader [server] [username] [password]");
            System.exit(0);
        }
        String server = args[0];
        String username = args[1];
        String password = args[2];
        try {
            // Connect to the server, without TLS encryption.
            ConnectionConfiguration config = new ConnectionConfiguration(server);
            config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
            final XMPPConnection con = new XMPPConnection(config);
            System.out.print("Connecting to " + server + "... ");
            con.connect();

            con.login(username, password, "reader");
            System.out.print("success.");
            System.out.println("");

            // Get the "real" server address.
            server = con.getServiceName();

            final String writerAddress = username + "@" + server + "/writer";
            String readerAddress = username + "@" + server + "/reader";

            System.out.println("Registered as " + readerAddress);

            // Look for the reader process.
            System.out.print("Waiting for " + writerAddress + "...");
            PacketCollector collector = con.createPacketCollector(new AndFilter(
                    new FromMatchesFilter(writerAddress), new PacketTypeFilter(Time.class)));
            Time timeRequest = (Time)collector.nextResult();
            Time timeReply = new Time(Calendar.getInstance());
            timeReply.setPacketID(timeRequest.getPacketID());
            timeReply.setType(IQ.Type.RESULT);
            timeReply.setTo(timeRequest.getFrom());
            con.sendPacket(timeReply);
            System.out.println(" found writer. Now in reading mode.");

            // Track how many packets we've read.
            con.addPacketListener(new PacketListener() {

                public void processPacket(Packet packet) {
                    packetCount.getAndIncrement();
                }
            }, new PacketTypeFilter(Message.class));

            while (!done) {
                Thread.sleep(5000);
                int count = packetCount.getAndSet(0);
                System.out.println("Packets per second: " + (count/5));
            }

            // Sleep while we're reading packets.
            Thread.sleep(Integer.MAX_VALUE);
        }
        catch (Exception e) {
            System.out.println("\nError: " + e.getMessage());
        }
    }
}
