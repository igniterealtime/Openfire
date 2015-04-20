package com.javamonitor.openfire.mbeans;

import org.jivesoftware.openfire.ServerPort;
import org.jivesoftware.openfire.XMPPServer;

/**
 * An MBean representing the Openfire server itself.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class Openfire implements OpenfireMBean {
    /**
     * @see com.javamonitor.openfire.mbeans.OpenfireMBean#getVersion()
     */
    public String getVersion() {
        return XMPPServer.getInstance().getServerInfo().getVersion()
                .getVersionString();
    }

    private static final Integer XMPP_CLIENT_PORT = 5222;

    /**
     * @see com.javamonitor.openfire.mbeans.OpenfireMBean#getLowestPort()
     */
    public Integer getLowestPort() {
        int slept = 0;
        Integer lowestPort = null;
        do {
            lowestPort = findLowestPort();
            if (XMPP_CLIENT_PORT.equals(lowestPort)) {
                // this is extremely likely to be correct, so we are done
                return lowestPort;
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                // won't happen...
            }
        } while (slept++ < 30);

        return lowestPort;
    }

    /**
     * Find the lowest port number, as the server stands now.
     * 
     * @return The lowest known port number, or <code>null</code> if no ports
     *         were opened yet.
     */
    private Integer findLowestPort() {
        int lowestClientPort = Integer.MAX_VALUE;
        int lowestPort = Integer.MAX_VALUE;
        for (final ServerPort port : XMPPServer.getInstance()
                .getConnectionManager().getPorts()) {
            final int number = port.getPort();
            if (number < lowestPort) {
                lowestPort = number;
            }

            if (ServerPort.Type.client.equals(port.getType())
                    && number < lowestClientPort) {
                lowestClientPort = number;
            }
        }

        if (lowestClientPort != Integer.MAX_VALUE) {
            return lowestClientPort;
        } else if (lowestPort != Integer.MAX_VALUE) {
            return lowestPort;
        }

        return null;
    }

    /**
     * Start collecting data.
     */
    public void start() {
        // nothing to do...
    }

    /**
     * Stop collecting data.
     */
    public void stop() {
        // nothing to do...
    }
}
