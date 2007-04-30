/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.openfire.test.throttle;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.packet.Time;

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
 * java ThrottleTestWriter [server] [username] [password]
 *
 * @author Matt Tucker
 */
public class ThrottleTestWriter {

    private static boolean done = false;
    private static AtomicInteger packetCount = new AtomicInteger(0);

    /**
     * Starts the throttle test write client.
     *
     * @param args application arguments.
     */
    public static void main(String [] args) {
        if (args.length != 3) {
            System.out.println("Usage: java ThrottleTestWriter [server] [username] [password]");
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

            con.login(username, password, "writer");
            System.out.print("success.");
            System.out.println("");

            // Get the "real" server address.
            server = con.getServiceName();

            String writerAddress = username + "@" + server + "/writer";
            final String readerAddress = username + "@" + server + "/reader";

            System.out.println("Registered as " + writerAddress);

            // Look for the reader process.
            System.out.print("Looking for " + readerAddress + "...");
            while (true) {
                IQ testIQ = new Time();
                testIQ.setType(IQ.Type.GET);
                testIQ.setTo(readerAddress);
                PacketCollector collector = con.createPacketCollector(new PacketIDFilter(testIQ.getPacketID()));
                con.sendPacket(testIQ);
                // Wait 5 seconds.
                long start = System.currentTimeMillis();
                Packet result = collector.nextResult(5000);
                collector.cancel();
                // If we got a result, continue.
                if (result != null && result.getError() == null) {
                    System.out.println(" found reader. Starting packet flood.");
                    break;
                }
                System.out.print(".");
                long end = System.currentTimeMillis();
                if (end - start < 5000) {
                    try {
                        Thread.sleep(5000 - (end-start));
                    }
                    catch (Exception e) {
                        // ignore.
                    }
                }
            }

            // Create a process to log how many packets we're writing out.
            Runnable statsRunnable = new Runnable() {

                public void run() {
                    while (!done) {
                        try {
                            Thread.sleep(5000);
                        }
                        catch (Exception e) { /* ignore */ }
                        int count = packetCount.getAndSet(0);
                        System.out.println("Packets per second: " + (count/5));
                    }
                }
            };
            Thread statsThread = new Thread(statsRunnable);
            statsThread.setDaemon(true);
            statsThread.start();

            // Now start flooding packets.
            Message testMessage = new Message(readerAddress);
            testMessage.setBody("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            while (!done) {
                con.sendPacket(testMessage);
                packetCount.getAndIncrement();
            }
        }
        catch (Exception e) {
            System.out.println("\nError: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
