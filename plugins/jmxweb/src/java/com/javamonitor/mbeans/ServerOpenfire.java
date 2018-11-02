package com.javamonitor.mbeans;

import static  com.ifsoft.jmxweb.plugin.JmxWebPlugin.OBJECTNAME_OPENFIRE;

import com.javamonitor.JmxHelper;

/**
 * The tricky bits for Openfire servers.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class ServerOpenfire implements ServerMBean {
    /**
     * See if we are running in an Openfire server.
     *
     * @return <code>true</code> if we are running inside Openfire, or
     *         <code>false</code> otherwise.
     */
    public static boolean runningInOpenfire() {
        return JmxHelper.mbeanExists(OBJECTNAME_OPENFIRE);
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getName()
     */
    public String getName() throws Exception {
        return "Openfire";
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getVersion()
     */
    public String getVersion() throws Exception {
        return JmxHelper.queryString(OBJECTNAME_OPENFIRE, "Version");
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getHttpPort()
     */
    public Integer getHttpPort() throws Exception {
        return JmxHelper.queryInt(OBJECTNAME_OPENFIRE, "LowestPort");
    }

    /**
     * @see com.javamonitor.mbeans.ServerMBean#getLastException()
     */
    public Throwable getLastException() {
        // not used, the Server class resolves this for us
        throw new Error("Not implemented...");
    }
}
