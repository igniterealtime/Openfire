package org.jivesoftware.xmpp.workgroup.interceptor;

import org.xmpp.packet.Packet;

/**
 * Packet interceptors that will print to the stdout the intercepted packets. This monitor
 * may be useful to debug problems or just monitor the activity in certain parts of the
 * application. Howerver, notice that printing to the stdout is an expensive operation which
 * will block other threads until the print has been finished.
 *
 * @author Gaston Dombiak
 */
public class TrafficMonitor implements PacketInterceptor {

    private boolean readEnabled = true;
    private boolean sentEnabled = true;
    private boolean onlyNotProcessedEnabled = true;

    public void interceptPacket(String workgroup, Packet packet, boolean read, boolean processed) {
        if ((readEnabled && read) || (sentEnabled && !read)) {
            if (onlyNotProcessedEnabled && processed) {
                return;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("Workgroup: ").append(workgroup);
            builder.append(" Read: ").append(read);
            builder.append(" Processed: ").append(processed);
            builder.append(" Packet: ").append(packet.toXML());
            System.out.println(builder.toString());
        }
    }

    /**
     * Returns true if packets that were received will be printed to the console.
     *
     * @return true if packets that were received will be printed to the console.
     */
    public boolean isReadEnabled() {
        return readEnabled;
    }

    /**
     * Sets if packets that were received will be printed to the console.
     *
     * @param readEnabled true if packets that were received will be printed to the console.
     */
    public void setReadEnabled(boolean readEnabled) {
        this.readEnabled = readEnabled;
    }

    /**
     * Returns true if packets that were sent will be printed to the console.
     *
     * @return true if packets that were sent will be printed to the console.
     */
    public boolean isSentEnabled() {
        return sentEnabled;
    }

    /**
     * Sets if packets that were sent will be printed to the console.
     *
     * @param sentEnabled true if packets that were sent will be printed to the console.
     */
    public void setSentEnabled(boolean sentEnabled) {
        this.sentEnabled = sentEnabled;
    }

    /**
     * Returns true if only the packets that were not processed will be printed to the console.
     *
     * @return true if only the packets that were not processed will be printed to the console.
     */
    public boolean isOnlyNotProcessedEnabled() {
        return onlyNotProcessedEnabled;
    }

    /**
     * Sets if only the packets that were not processed will be printed to the console.
     *
     * @param onlyNotProcessedEnabled true if only the packets that were not processed will be
     *        printed to the console.
     */
    public void setOnlyNotProcessedEnabled(boolean onlyNotProcessedEnabled) {
        this.onlyNotProcessedEnabled = onlyNotProcessedEnabled;
    }
}
