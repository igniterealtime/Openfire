/**
 * $RCSfile: DNSUtil.java,v $
 * $Revision: 2867 $
 * $Date: 2005-09-22 03:40:04 -0300 (Thu, 22 Sep 2005) $
 *
 * Copyright (C) 2004-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Utilty class to perform DNS lookups for XMPP services.
 *
 * @author Matt Tucker
 */
public class DNSUtil {

    private static DirContext context;

    /**
     * Internal DNS that allows to specify target IP addresses and ports to use for domains.
     * The internal DNS will be checked up before performing an actual DNS SRV lookup.
     */
    private static Map<String, HostAddress> dnsOverride;

    static {
        try {
            Hashtable<String,String> env = new Hashtable<String,String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            context = new InitialDirContext(env);

            String property = JiveGlobals.getProperty("dnsutil.dnsOverride");
            if (property != null) {
                dnsOverride = decode(property);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the host name and port that the specified XMPP server can be
     * reached at for server-to-server communication. A DNS lookup for a SRV
     * record in the form "_xmpp-server._tcp.example.com" is attempted, according
     * to section 14.4 of RFC 3920. If that lookup fails, a lookup in the older form
     * of "_jabber._tcp.example.com" is attempted since servers that implement an
     * older version of the protocol may be listed using that notation. If that
     * lookup fails as well, it's assumed that the XMPP server lives at the
     * host resolved by a DNS lookup at the specified domain on the specified default port.<p>
     *
     * As an example, a lookup for "example.com" may return "im.example.com:5269".
     *
     * @param domain the domain.
     * @param defaultPort default port to return if the DNS look up fails.
     * @return a HostAddress, which encompasses the hostname and port that the XMPP
     *      server can be reached at for the specified domain.
     */
    public static HostAddress resolveXMPPServerDomain(String domain, int defaultPort) {
        // Check if there is an entry in the internal DNS for the specified domain
        if (dnsOverride != null) {
            HostAddress hostAddress = dnsOverride.get(domain);
            if (hostAddress != null) {
                return hostAddress;
            }
        }
        if (context == null) {
            return new HostAddress(domain, defaultPort);
        }
        String host = domain;
        int port = defaultPort;
        try {
            Attributes dnsLookup =
                    context.getAttributes("_xmpp-server._tcp." + domain, new String[]{"SRV"});
            String srvRecord = (String)dnsLookup.get("SRV").get();
            String [] srvRecordEntries = srvRecord.split(" ");
            port = Integer.parseInt(srvRecordEntries[srvRecordEntries.length-2]);
            host = srvRecordEntries[srvRecordEntries.length-1];
        }
        catch (Exception e) {
            // Attempt lookup with older "jabber" name.
            try {
                Attributes dnsLookup =
                        context.getAttributes("_jabber._tcp." + domain, new String[]{"SRV"});
                String srvRecord = (String)dnsLookup.get("SRV").get();
                String [] srvRecordEntries = srvRecord.split(" ");
                port = Integer.parseInt(srvRecordEntries[srvRecordEntries.length-2]);
                host = srvRecordEntries[srvRecordEntries.length-1];
            }
            catch (Exception e2) {
                // Do nothing
            }
        }
        // Host entries in DNS should end with a ".".
        if (host.endsWith(".")) {
            host = host.substring(0, host.length()-1);
        }
        return new HostAddress(host, port);
    }

    /**
     * Returns the internal DNS that allows to specify target IP addresses and ports
     * to use for domains. The internal DNS will be checked up before performing an
     * actual DNS SRV lookup.
     *
     * @return the internal DNS that allows to specify target IP addresses and ports
     *         to use for domains.
     */
    public static Map<String, HostAddress> getDnsOverride() {
        return dnsOverride;
    }

    /**
     * Sets the internal DNS that allows to specify target IP addresses and ports
     * to use for domains. The internal DNS will be checked up before performing an
     * actual DNS SRV lookup.
     *
     * @param dnsOverride the internal DNS that allows to specify target IP addresses and ports
     *        to use for domains.
     */
    public static void setDnsOverride(Map<String, HostAddress> dnsOverride) {
        DNSUtil.dnsOverride = dnsOverride;
        JiveGlobals.setProperty("dnsutil.dnsOverride", encode(dnsOverride));
    }

    private static String encode(Map<String, HostAddress> internalDNS) {
        if (internalDNS == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(100);
        for (String key : internalDNS.keySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append("{").append(key).append(",");
            sb.append(internalDNS.get(key).getHost()).append(":");
            sb.append(internalDNS.get(key).getPort()).append("}");
        }
        return sb.toString();
    }

    private static Map<String, HostAddress> decode(String encodedValue) {
        Map<String, HostAddress> answer = new HashMap<String, HostAddress>();
        StringTokenizer st = new StringTokenizer(encodedValue, "{},:");
        while (st.hasMoreElements()) {
            String key = st.nextToken();
            answer.put(key, new HostAddress(st.nextToken(), Integer.parseInt(st.nextToken())));
        }
        return answer;
    }

    /**
     * Encapsulates a hostname and port.
     */
    public static class HostAddress {

        private String host;
        private int port;

        private HostAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Returns the hostname.
         *
         * @return the hostname.
         */
        public String getHost() {
            return host;
        }

        /**
         * Returns the port.
         *
         * @return the port.
         */
        public int getPort() {
            return port;
        }

        public String toString() {
            return host + ":" + port;
        }
    }
}