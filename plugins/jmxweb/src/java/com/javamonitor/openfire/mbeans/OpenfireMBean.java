package com.javamonitor.openfire.mbeans;

/**
 * The MBean interface to the Openfire MBean.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public interface OpenfireMBean {
    /**
     * Find the version of this XMPP server.
     * 
     * @return The version number of this Openfire server.
     */
    String getVersion();

    /**
     * Find the lowest port number for this Openfire server. We prefer to use a
     * client port, but we will happily use any other port of no client XMPP
     * port can be found.
     * <p>
     * Also, it seems that the start-up sequence of Openfire is such that it
     * takes quite a while to open the correct ports. This method sleeps a
     * little, waiting for the standard XMPP port to show up, since that is the
     * lowest port in 99% of the cases. Just in case that port is not used, we
     * default to another port. This makes it possible to monitor multiple
     * Openfire servers that have different port numbers, running on the same
     * machine.
     * 
     * @return The lowest port for this Openfire server.
     */
    Integer getLowestPort();
}
