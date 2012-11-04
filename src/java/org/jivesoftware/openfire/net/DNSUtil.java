/**
 * $RCSfile: DNSUtil.java,v $
 * $Revision: 2867 $
 * $Date: 2005-09-22 03:40:04 -0300 (Thu, 22 Sep 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.net;

import org.eclipse.jetty.util.MultiMap;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import java.io.Serializable;
import java.util.*;

/**
 * Utilty class to perform DNS lookups for XMPP services.
 *
 * @author Matt Tucker
 */
public class DNSUtil {

    private static DirContext context;

    private static final Logger logger = LoggerFactory.getLogger(DNSUtil.class);

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
            logger.error("Can't initialize DNS context!", e);
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
     * @deprecated replaced with support for multiple srv records, see 
     *      {@link #resolveXMPPDomain(String, int)}
     */
    @Deprecated
    public static HostAddress resolveXMPPServerDomain(String domain, int defaultPort) {
        return resolveXMPPDomain(domain, defaultPort).get(0);
    }

    /**
     * Returns a sorted list of host names and ports that the specified XMPP domain
     * can be reached at for server-to-server communication. A DNS lookup for a SRV
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
     * @return a list of  HostAddresses, which encompasses the hostname and port that the XMPP
     *      server can be reached at for the specified domain.
     */
    public static List<HostAddress> resolveXMPPDomain(String domain, int defaultPort) {
        // Check if there is an entry in the internal DNS for the specified domain
        List<HostAddress> results = new LinkedList<HostAddress>();
        if (dnsOverride != null) {
            HostAddress hostAddress = dnsOverride.get(domain);
            if (hostAddress != null) {
                results.add(hostAddress);
                return results;
            }
        }

        // Attempt the SRV lookup.
        results.addAll(srvLookup("_xmpp-server._tcp." + domain));
        if (results.isEmpty()) {
            results.addAll(srvLookup("_jabber._tcp." + domain));
        }

        // Use domain and default port as fallback.
        if (results.isEmpty()) {
            results.add(new HostAddress(domain, defaultPort));
        }
        return results;
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

    private static List<? extends HostAddress> srvLookup(String lookup) {
        if (lookup == null) {
            throw new NullPointerException("DNS lookup can't be null");
        }
        try {
            Attributes dnsLookup =
                    context.getAttributes(lookup, new String[]{"SRV"});
            Attribute srvRecords = dnsLookup.get("SRV");
            if (srvRecords == null) {
                logger.debug("No SRV record found for domain: " + lookup);
                return new ArrayList<HostAddress>();
            }
            WeightedHostAddress[] hosts = new WeightedHostAddress[srvRecords.size()];
            for (int i = 0; i < srvRecords.size(); i++) {
                hosts[i] = new WeightedHostAddress(((String)srvRecords.get(i)).split(" "));
            }

            return prioritize(hosts);
        }
        catch (NameNotFoundException e) {
            logger.debug("No SRV record found for: " + lookup, e);
        }
        catch (NamingException e) {
            logger.error("Can't process DNS lookup!", e);
        }
        return new ArrayList<HostAddress>();
    }

    /**
     * Encapsulates a hostname and port.
     */
    public static class HostAddress {

        private final String host;
        private final int port;

        private HostAddress(String host, int port) {
            // Host entries in DNS should end with a ".".
            if (host.endsWith(".")) {
                this.host = host.substring(0, host.length()-1);
            }
            else {
                this.host = host;
            }
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

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public static List<WeightedHostAddress> prioritize(WeightedHostAddress[] records) {
        final List<WeightedHostAddress> result = new LinkedList<WeightedHostAddress>();

        // sort by priority (ascending)
        SortedMap<Integer, Set<WeightedHostAddress>> byPriority = new TreeMap<Integer, Set<WeightedHostAddress>>();
        for(final WeightedHostAddress record : records) {
            if (byPriority.containsKey(record.getPriority())) {
                byPriority.get(record.getPriority()).add(record);
            } else {
                final Set<WeightedHostAddress> set = new HashSet<WeightedHostAddress>();
                set.add(record);
                byPriority.put(record.getPriority(), set);
            }
        }

        // now, randomize each priority set by weight.
        for(Map.Entry<Integer, Set<WeightedHostAddress>> weights : byPriority.entrySet()) {

            List<WeightedHostAddress> zeroWeights = new LinkedList<WeightedHostAddress>();

            int totalWeight = 0;
            final Iterator<WeightedHostAddress> i = weights.getValue().iterator();
            while (i.hasNext()) {
                final WeightedHostAddress next = i.next();
                if (next.weight == 0) {
                    // set aside, as these should be considered last according to the RFC.
                    zeroWeights.add(next);
                    i.remove();
                    continue;
                }

                totalWeight += next.getWeight();
            }

            int iterationWeight = totalWeight;
            Iterator<WeightedHostAddress> iter = weights.getValue().iterator();
            while (iter.hasNext()) {
                int needle = new Random().nextInt(iterationWeight);

                while (true) {
                    final WeightedHostAddress record = iter.next();
                    needle -= record.getWeight();
                    if (needle <= 0) {
                        result.add(record);
                        iter.remove();
                        iterationWeight -= record.getWeight();
                        break;
                    }
                }
                iter = weights.getValue().iterator();
            }

            // finally, append the hosts with zero priority (shuffled)
            Collections.shuffle(zeroWeights);
            for(WeightedHostAddress zero : zeroWeights) {
                result.add(zero);
            }
        }

        return result;
    }
    /**
     * The representation of weighted address.
     */
    public static class WeightedHostAddress extends HostAddress {

        private final int priority;
        private final int weight;

        private WeightedHostAddress(String [] srvRecordEntries) {
            super(srvRecordEntries[srvRecordEntries.length-1],
                    Integer.parseInt(srvRecordEntries[srvRecordEntries.length-2]));
            weight = Integer.parseInt(srvRecordEntries[srvRecordEntries.length-3]);
            priority = Integer.parseInt(srvRecordEntries[srvRecordEntries.length-4]);
        }

        WeightedHostAddress(String host, int port, int priority, int weight) {
            super(host, port);
            this.priority = priority;
            this.weight = weight;
        }

        /**
         * Returns the priority.
         *
         * @return the priority.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Returns the weight.
         *
         * @return the weight.
         */
        public int getWeight() {
            return weight;
        }
    }
}