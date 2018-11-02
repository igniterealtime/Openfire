package com.javamonitor.mbeans;

import static com.javamonitor.JmxHelper.objectNameBase;
import static java.lang.System.getProperty;

/**
 * The application server helper mbean. This mbean is responsible for
 * aggregating the various server's mbeans into a single view.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class Server implements ServerMBean {
    private Throwable lastException = null;

    private final ServerMBean actualServer;

    /**
     * The object name for the application server helper mbean.
     */
    public static final String serverObjectName = objectNameBase + "Server";

    /**
     * The attribute name of the lowest HTTP port attribute.
     */
    public static final String httpPortAttribute = "HttpPort";

    /**
     * The attribute name of the application server name attribute.
     */
    public static final String nameAttribute = "Name";

    /**
     * The attribute name of the application server version attribute.
     */
    public static final String versionAttribute = "Version";

    /**
     * Create a new server info aggregator bean. Here we try to find out in what
     * server we're running and instantiate the correct server-specific MBean.
     *
     * Note that we have to test for Tomcat after the others, because Tomcat is
     * used here and there as an embedded server. In such a case, we want to
     * detect the outer server, not the embedded Tomcat.
     */
    public Server()
    {
        if (ServerOpenfire.runningInOpenfire())
        {
            actualServer = new ServerOpenfire();

        } else {
            actualServer = null;
        }
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getName()
     */
    public String getName() throws Exception {
        if (actualServer == null) {
            return "Java VM";
        }

        return actualServer.getName();
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getVersion()
     */
    public String getVersion() throws Exception {
        if (actualServer == null) {
            return getProperty("java.version");
        }

        return actualServer.getVersion();
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getHttpPort()
     */
    public Integer getHttpPort() throws Exception {
        if (actualServer == null) {
            return null;
        }

        return actualServer.getHttpPort();
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getLastException()
     */
    public Throwable getLastException() {
        return lastException;
    }

    /**
     * Set the last exception that the probe saw.
     *
     * @param lastException
     *            The last exception to set.
     */
    public void setLastException(final Throwable lastException) {
        this.lastException = lastException;
    }
}
