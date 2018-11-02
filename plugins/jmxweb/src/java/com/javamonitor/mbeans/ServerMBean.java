package com.javamonitor.mbeans;

/**
 * The interface to the server mbean.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public interface ServerMBean {
    /**
     * The name of the application server that we run in.
     * 
     * @return The name of the application server.
     * @throws Exception
     *             When there was a problem querying JMX.
     */
    String getName() throws Exception;

    /**
     * The version of the application server that we run in.
     * 
     * @return The version of the application server.
     * @throws Exception
     *             When there was a problem querying JMX.
     */
    String getVersion() throws Exception;

    /**
     * The lowest HTTP port in use in this application server that we run in.
     * Actually, this is a bit of a misnomer. Not all servers are HTTP servers.
     * For example, we support the Openfire XMPP server, which only uses HTTP
     * for configuration and not for actual services to clients.
     * 
     * @return The lowest service port in use in this application server, or -1
     *         if no port is known.
     * @throws Exception
     *             When there was a problem querying JMX.
     */
    Integer getHttpPort() throws Exception;

    /**
     * Find the last exception that this probe saw. We use it to send to the
     * collector server, once the connection is restored, and we use it to avoid
     * logging an exception every minute while the connection to the collector
     * servers is down.
     * 
     * @return The last exception we saw.
     */
    Throwable getLastException();
}
