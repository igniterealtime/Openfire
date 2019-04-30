/*
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

import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import java.util.*;

/**
 * Utility class to perform DNS lookups for XMPP services.
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
            Hashtable<String,String> env = new Hashtable<>();
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
     * Returns a sorted list of host names and ports that the specified XMPP
     * domain can be reached at for server-to-server communication.
     *
     * DNS lookups for a SRV records in the form "_xmpp-server._tcp.example.com"
     * and "_xmpps-server._tcp.example.com" are attempted, in line with section
     * 3.2 of XMPP Core and XEP-0368.
     *
     * If those lookup fail to provide any records, a lookup in the older form
     * of "_jabber._tcp.example.com" is attempted since servers that implement
     * an older version of the protocol may be listed using that notation.
     *
     * If that lookup fails as well, it's assumed that the XMPP server lives at
     * the host resolved by a DNS A lookup at the specified domain on the
     * specified default port.<p>
     *
     * As an example, a lookup for "example.com" may return "im.example.com:5269".
     *
     * @param domain the domain.
     * @param defaultPort default port to return if the DNS look up fails.
     * @return a list of  HostAddresses, which encompasses the hostname and port
     *         that the XMPP server can be reached at for the specified domain.
     * @see <a href="https://tools.ietf.org/html/rfc6120#section-3.2">XMPP CORE</a>
     * @see <a href="https://xmpp.org/extensions/xep-0368.html">XEP-0368</a>
     */
    public static List<HostAddress> resolveXMPPDomain(String domain, int defaultPort) {
        // Check if there is an entry in the internal DNS for the specified domain
        List<HostAddress> results = new LinkedList<>();
        if (dnsOverride != null) {
            HostAddress hostAddress = dnsOverride.get(domain);
            if (hostAddress == null) {
                hostAddress = dnsOverride.get("*");
            }
            if (hostAddress != null) {
                results.add(hostAddress);
                return results;
            }
        }

        // Attempt the SRV lookup.
        final List<WeightedHostAddress> srvLookups = new LinkedList<>();
        srvLookups.addAll(srvLookup("xmpp-server", "tcp", domain ) );

        final boolean allowTLS = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ENABLED, true);
        if (allowTLS) {
            srvLookups.addAll(srvLookup("xmpps-server", "tcp", domain));
        }
        if (!srvLookups.isEmpty()) {
            // we have to re-prioritize the combination of both lookups.
            results.addAll( prioritize( srvLookups.toArray( new WeightedHostAddress[0] ) ) );
        }

        if (results.isEmpty()) {
            results.addAll(srvLookup( "jabber", "tcp", domain ) );
        }

        // Use domain and default port as fallback.
        if (results.isEmpty()) {
            results.add(new HostAddress(domain, defaultPort, false));
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
                sb.append(',');
            }
            sb.append('{').append(key).append(',');
            sb.append(internalDNS.get(key).getHost()).append(':');
            sb.append(internalDNS.get(key).getPort()).append('}');
        }
        return sb.toString();
    }

    private static Map<String, HostAddress> decode(String encodedValue) {
        Map<String, HostAddress> answer = new HashMap<>();
        StringTokenizer st = new StringTokenizer(encodedValue, "{},:");
        while (st.hasMoreElements()) {
            String key = st.nextToken();
            answer.put(key, new HostAddress(st.nextToken(), Integer.parseInt(st.nextToken()), false));
        }
        return answer;
    }

    /**
     * Performs a DNS SRV lookup. Does not take into account any DNS overrides configured in this class.
     *
     * The results returned by this method are ordered by priority (ascending), and order of equal priority entries is
     * randomized by weight, as defined in the DNS SRV specification.
     *
     * @param service the symbolic name of the desired service (cannot be null).
     * @param proto the transport protocol of the desired service; this is usually either TCP or UDP (cannot be null).
     * @param name the domain name for which this record is valid (cannot be null).
     * @return An ordered of results (possibly empty, never null).
     */
    public static List<WeightedHostAddress> srvLookup(String service, String proto, String name) {
        if (service == null || proto == null || name == null) {
            throw new NullPointerException("DNS lookup can't be null");
        }

        if ( !service.startsWith( "_" ) )
        {
            service = "_" + service;
        }
        if ( !service.endsWith( "." ) )
        {
            service = service + ".";
        }

        if ( !proto.startsWith( "_" ) )
        {
            proto = "_" + proto;
        }
        if ( !proto.endsWith( "." ) )
        {
            proto = proto+ ".";
        }

        if ( !name.endsWith( "." ) ) {
            name = name + ".";
        }

        // _service._proto.name.
        final String lookup = (service + proto + name).toLowerCase();
        try {
            Attributes dnsLookup =
                    context.getAttributes(lookup, new String[]{"SRV"});
            Attribute srvRecords = dnsLookup.get("SRV");
            if (srvRecords == null) {
                logger.debug("No SRV record found for domain: " + lookup);
                return Collections.emptyList();
            }
            WeightedHostAddress[] hosts = new WeightedHostAddress[srvRecords.size()];
            final boolean directTLS = lookup.startsWith( "_xmpps-" ); // XEP-0368
            for (int i = 0; i < srvRecords.size(); i++) {
                hosts[i] = new WeightedHostAddress(((String)srvRecords.get(i)).split(" "), directTLS);
            }

            return prioritize(hosts);
        }
        catch (NameNotFoundException e) {
            logger.debug("No SRV record found for: " + lookup, e);
        }
        catch (NamingException e) {
            logger.error("Can't process DNS lookup!", e);
        }
        return Collections.emptyList();
    }

    /**
     * Checks if the provided DNS pattern matches the provided name. For example, this method will:
     * return <em>true</em>  for name: {@code xmpp.example.org}, pattern: {@code *.example.org}
     * return <em>false</em> for name: {@code xmpp.example.org}, pattern: {@code example.org}
     *
     * This method is not case sensitive.
     *
     * @param name The name to check against a pattern (cannot be null or empty).
     * @param pattern the pattern (cannot be null or empty).
     * @return true when the name is covered by the pattern, otherwise false.
     */
    public static boolean isNameCoveredByPattern( String name, String pattern )
    {
        if ( name == null || name.isEmpty() || pattern == null || pattern.isEmpty() )
        {
            throw new IllegalArgumentException( "Arguments cannot be null or empty." );
        }

        final String needle = name.toLowerCase();
        final String hayStack = pattern.toLowerCase();

        if ( needle.equals( hayStack )) {
            return true;
        }

        if ( hayStack.startsWith( "*." ) ) {
            return needle.endsWith( hayStack.substring( 2 ) );
        }
        return false;
    }

    /**
     * Encapsulates a hostname and port.
     */
    public static class HostAddress {

        private final String host;
        private final int port;
        private boolean directTLS;

        private HostAddress(String host, int port, boolean directTLS) {
            // Host entries in DNS should end with a ".".
            if (host.endsWith(".")) {
                this.host = host.substring(0, host.length()-1);
            }
            else {
                this.host = host;
            }
            this.port = port;
            this.directTLS = directTLS;
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

        public boolean isDirectTLS()
        {
            return directTLS;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public static List<WeightedHostAddress> prioritize(WeightedHostAddress[] records) {
        final List<WeightedHostAddress> result = new LinkedList<>();

        // sort by priority (ascending)
        SortedMap<Integer, Set<WeightedHostAddress>> byPriority = new TreeMap<>();
        for(final WeightedHostAddress record : records) {
            if (byPriority.containsKey(record.getPriority())) {
                byPriority.get(record.getPriority()).add(record);
            } else {
                final Set<WeightedHostAddress> set = new HashSet<>();
                set.add(record);
                byPriority.put(record.getPriority(), set);
            }
        }

        // now, randomize each priority set by weight.
        for(Map.Entry<Integer, Set<WeightedHostAddress>> weights : byPriority.entrySet()) {

            List<WeightedHostAddress> zeroWeights = new LinkedList<>();

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

        private WeightedHostAddress(String[] srvRecordEntries, boolean directTLS) {
            super(srvRecordEntries[srvRecordEntries.length-1],
                  Integer.parseInt(srvRecordEntries[srvRecordEntries.length-2]),
                  directTLS
            );
            weight = Integer.parseInt(srvRecordEntries[srvRecordEntries.length-3]);
            priority = Integer.parseInt(srvRecordEntries[srvRecordEntries.length-4]);
        }

        WeightedHostAddress(String host, int port, boolean directTLS, int priority, int weight) {
            super(host, port, directTLS);
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
