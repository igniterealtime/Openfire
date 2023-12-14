/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.Serializable;
import java.util.*;

/**
 * Utility class to perform DNS lookups for XMPP services.
 *
 * @author Matt Tucker
 */
public class DNSUtil {

    private static DirContext context;

    private static final Logger logger = LoggerFactory.getLogger(DNSUtil.class);

    private static Cache<String, CacheableOptional<WeightedHostAddress[]>> LOOKUP_CACHE;

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
                dnsOverride.forEach((domain, override) -> logger.info("Detected DNS override configuration for {} to {}", domain, override));
            }
        }
        catch (Exception e) {
            logger.error("Can't initialize DNS context!", e);
        }
        try {
            LOOKUP_CACHE = CacheFactory.createCache("DNS Records");
        } catch (Exception e) {
            logger.error("Can't initialize DNS cache!", e);
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
                logger.debug("Answering lookup for domain '{}' from DNS override property. Returning: {}", domain, hostAddress);
                results.add(hostAddress);
                return results;
            }
        }

        // Attempt the SRV lookup.
        final List<WeightedHostAddress> srvLookups = new LinkedList<>(srvLookup("xmpp-server", "tcp", domain));

        final String propertyValue = JiveGlobals.getProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.optional.toString());
        Connection.TLSPolicy configuredPolicy;
        try {
            configuredPolicy = Connection.TLSPolicy.valueOf(propertyValue);
        } catch (RuntimeException e) {
            // In case of misconfiguration, default to the highest level of security.
            logger.warn("Unexpected value for '{}': '{}'. Defaulting to '{}'", ConnectionSettings.Server.TLS_POLICY, propertyValue, Connection.TLSPolicy.required);
            configuredPolicy = Connection.TLSPolicy.required;
        }
        final boolean allowTLS = configuredPolicy == Connection.TLSPolicy.required || configuredPolicy == Connection.TLSPolicy.optional;
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
        StringBuilder sb = new StringBuilder();
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
     * @param service the symbolic name of the desired service.
     * @param proto the transport protocol of the desired service; this is usually either TCP or UDP.
     * @param name the domain name for which this record is valid.
     * @return An ordered of results (possibly empty, never null).
     */
    public static List<WeightedHostAddress> srvLookup(@Nonnull final String service, @Nonnull final String proto, @Nonnull final String name) {
        logger.trace("DNS SRV Lookup for service '{}', protocol '{}' and name '{}'", service, proto, name);

        final String lookup = constructLookup(service, proto, name);

        final CacheableOptional<WeightedHostAddress[]> optional = LOOKUP_CACHE.get(lookup);
        WeightedHostAddress[] result;
        if (optional != null) {
            // Return a cached result.
            if (optional.isAbsent()) {
                logger.warn("DNS SRV lookup previously failed for '{}' (negative cache result)", lookup);
                result = new WeightedHostAddress[0];
            } else {
                result = optional.get();
                if ( result.length == 0 ) {
                    logger.debug("No SRV record found for '{}' (cached result)", lookup);
                } else {
                    logger.trace("{} SRV record(s) found for '{}' (cached result)", result.length, lookup);
                }
            }
        } else {
            // No result in cache. Query DNS and cache result.
            try {
                final Attributes dnsLookup = context.getAttributes(lookup, new String[]{"SRV"});
                final Attribute srvRecords = dnsLookup.get("SRV");
                if (srvRecords == null) {
                    logger.debug("No SRV record found for '{}'", lookup);
                    result = new WeightedHostAddress[0];
                } else {
                    result = new WeightedHostAddress[srvRecords.size()];
                    final boolean directTLS = lookup.startsWith("_xmpps-"); // XEP-0368
                    for (int i = 0; i < srvRecords.size(); i++) {
                        result[i] = new WeightedHostAddress(((String) srvRecords.get(i)).split(" "), directTLS);
                    }
                    logger.trace("{} SRV record(s) found for '{}'", result.length, lookup);
                }
                LOOKUP_CACHE.put(lookup, CacheableOptional.of(result));
            } catch (NameNotFoundException e) {
                logger.debug("No SRV record found for '{}'", lookup, e);
                LOOKUP_CACHE.put(lookup, CacheableOptional.of(new WeightedHostAddress[0])); // Empty result (different from negative result!)
                result = new WeightedHostAddress[0];
            } catch (NamingException e) {
                logger.info("DNS SRV lookup was unsuccessful for '{}': {}", lookup, e.getMessage());
                LOOKUP_CACHE.put(lookup, CacheableOptional.of(null)); // Negative result cache (different from empty result!)
                result = new WeightedHostAddress[0];
            }
        }

        // Do not store _prioritized_ results in the cache, as there is a random element to the prioritization that needs to happen every time.
        return prioritize(result);
    }

    /**
     * Constructs a DNS SRV lookup query (eg: <tt>_service._proto.name.</tt>)
     *
     * @param service the symbolic name of the desired service.
     * @param proto the transport protocol of the desired service; this is usually either TCP or UDP.
     * @param name the domain name for which this record is valid.
     * @return A DNS lookup query
     */
    // Package protected to be able to unit test this method.
    @Nonnull
    static String constructLookup(@Nonnull final String service, @Nonnull final String proto, @Nonnull final String name)
    {
        String lookup = "";
        if ( !service.startsWith( "_" ) )
        {
            lookup += "_";
        }
        lookup += service;

        if ( !lookup.endsWith( "." ) )
        {
            lookup += ".";
        }

        if ( !proto.startsWith( "_" ) )
        {
            lookup += "_";
        }
        lookup += proto;

        if ( !lookup.endsWith( "." ) )
        {
            lookup += ".";
        }

        lookup += name;
        if ( !lookup.endsWith( "." ) ) {
            lookup += ".";
        }

        return lookup.toLowerCase();
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
    public static class HostAddress implements Serializable {

        private final String host;
        private final int port;
        private final boolean directTLS;

        public HostAddress(String host, int port, boolean directTLS) {
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
            result.addAll(zeroWeights);
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
