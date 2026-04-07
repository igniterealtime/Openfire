/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Utility class to perform DNS lookups for XMPP services.
 *
 * @author Matt Tucker
 */
public class DNSUtil {

    private static DirContext context;

    private static final Logger logger = LoggerFactory.getLogger(DNSUtil.class);

    private static Cache<String, CacheableOptional<SrvRecord[]>> LOOKUP_CACHE;

    /**
     * Internal DNS overrides for XMPP domain resolution.
     *
     * Resolution precedence is exact domain match, then most-specific wildcard pattern, then global fallback
     * {@code *}. Wildcard patterns are expected in the form {@code *.example.org}.
     */
    private static volatile Map<String, SrvRecord> dnsOverride;

    static {
        try {
            Hashtable<String,String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            context = new InitialDirContext(env);

            String property = JiveGlobals.getProperty("dnsutil.dnsOverride");
            if (property != null) {
                dnsOverride = new ConcurrentHashMap<>(decode(property));
                dnsOverride.forEach((domain, override) -> logger.debug("Detected DNS override configuration for {} to {}", domain, override));
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
     * Returns a collection of host names and ports that the specified XMPP
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
     * specified default port.
     *
     * As an example, a lookup for "example.com" may return "im.example.com:5269".
     *
     * DNS overrides are evaluated before DNS lookups. Resolution first checks for an exact key match in
     * {@code dnsOverride}. If none exists, wildcard entries in the form {@code *.example.org} are considered.
     * When multiple wildcard entries match, the most-specific match is selected. If no wildcard entry matches,
     * the special key {@code *} is used as a global fallback.
     *
     * The returned collection is a list of sets of host names. The 'inner'
     * collection, the sets of host names, are grouping host names that have an
     * equal 'priority' SRV value. The 'outer' collection, the list, defines the
     * order that's defined by the 'priority' value (lowest first). Generally
     * speaking, all values from the first set of the outer list should be
     * processed, before elements from the next set of that list are to be processed.
     *
     * The Sets that are returned as elements of the list have a predictable
     * iteration order. This order is based on a randomization based on the
     * SRV 'weight' value of each host.
     *
     * @param domain the domain.
     * @param defaultPort default port to return if the DNS look up fails.
     * @return SRV records (grouped by priority) which encompasses the hostname and port
     *         that the XMPP server can be reached at for the specified domain.
     * @see <a href="https://tools.ietf.org/html/rfc6120#section-3.2">XMPP CORE</a>
     * @see <a href="https://xmpp.org/extensions/xep-0368.html">XEP-0368</a>
     */
    public static List<Set<SrvRecord>> resolveXMPPDomain(String domain, int defaultPort) {
        // Check if there is an entry in the internal DNS for the specified domain
        List<Set<SrvRecord>> results = new LinkedList<>();
        if (dnsOverride != null) {
            // DNS names are case-insensitive, normalize domain for exact match lookup
            SrvRecord serviceRecord = dnsOverride.get(domain.toLowerCase());
            if (serviceRecord == null) {
                serviceRecord = findMostSpecificWildcardOverride(domain);
            }
            if (serviceRecord == null) {
                serviceRecord = dnsOverride.get("*");
            }
            if (serviceRecord != null) {
                logger.debug("Answering lookup for domain '{}' from DNS override property. Returning: {}", domain, serviceRecord);
                results.add(Set.of(serviceRecord));
                return results;
            }
        }

        // Attempt the SRV lookup.
        final List<SrvRecord> srvLookups = new LinkedList<>(srvLookup("xmpp-server", "tcp", domain));

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

        if (srvLookups.isEmpty()) {
            srvLookups.addAll(srvLookup( "jabber", "tcp", domain ));
        }

        if (!srvLookups.isEmpty()) {
            // we have to re-prioritize the combination of both lookups.
            results.addAll( SrvRecord.prioritize(srvLookups) );
        }

        // Use domain and default port as fallback, if that's not already in the list.
        if (results.stream().flatMap(Set::stream).noneMatch(h -> h.getHostname().equals(domain) && h.getPort() == defaultPort && !h.isDirectTLS())) {
            results.add(Set.of(new SrvRecord(domain, defaultPort, false, Integer.MAX_VALUE, 0)));
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Answering lookup for domain '{}' from DNS responses. Returning:", domain);
            for (final Set<SrvRecord> srvLookup : results) {
                for (final SrvRecord hostAddress : srvLookup) {
                    if (hostAddress.getPriority() != Integer.MAX_VALUE) {
                        logger.debug(" - {} (based on a DNS lookup)", hostAddress);
                    } else {
                        logger.debug(" - {} (a fallback, based on the XMPP domain and default port)", hostAddress);
                    }
                }
            }
        }
        return results;
    }

    /**
     * Returns DNS override entries that are checked before DNS SRV lookups.
     *
     * Resolution precedence is exact key match, then most-specific wildcard key in the form
     * {@code *.example.org}, then global fallback key {@code *}.
     *
     * @return configured DNS override entries, or null when no overrides are configured.
     */
    public static Map<String, SrvRecord> getDnsOverride() {
        return dnsOverride;
    }

    /**
     * Sets DNS override entries that are checked before DNS SRV lookups.
     *
     * Exact keys are matched first. Wildcard keys in the form {@code *.example.org} are matched by suffix,
     * preferring the most-specific wildcard when multiple entries match. The key {@code *} is a global fallback.
     *
     * @param dnsOverride DNS override entries keyed by domain or wildcard pattern.
     */
    public static void setDnsOverride(Map<String, SrvRecord> dnsOverride) {
        if (dnsOverride == null) {
            DNSUtil.dnsOverride = null;
            JiveGlobals.setProperty("dnsutil.dnsOverride", encode(null));
        } else {
            // Normalize keys to lowercase for case-insensitive DNS name handling
            Map<String, SrvRecord> normalizedOverrides = new ConcurrentHashMap<>();
            for (Map.Entry<String, SrvRecord> entry : dnsOverride.entrySet()) {
                normalizedOverrides.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            DNSUtil.dnsOverride = normalizedOverrides;
            JiveGlobals.setProperty("dnsutil.dnsOverride", encode(normalizedOverrides));
        }
    }

    /**
     * Finds the most-specific wildcard DNS override that matches a domain.
     *
     * Wildcard patterns are expected in the form {@code *.example.org}. When multiple patterns match,
     * the longest pattern key is selected.
     *
     * @param domain The domain being resolved.
     * @return A matching wildcard override, or null when no wildcard pattern matches.
     */
    private static SrvRecord findMostSpecificWildcardOverride(final String domain)
    {
        // Get a snapshot reference to avoid ConcurrentModificationException
        final Map<String, SrvRecord> currentOverrides = dnsOverride;
        if (currentOverrides == null) {
            return null;
        }

        SrvRecord bestMatch = null;
        int bestKeyLength = -1;

        for (final Map.Entry<String, SrvRecord> entry : currentOverrides.entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith("*.")) {
                continue;
            }

            if (!isNameCoveredByPattern(domain, key)) {
                continue;
            }

            if (key.length() > bestKeyLength) {
                bestMatch = entry.getValue();
                bestKeyLength = key.length();
            }
        }

        return bestMatch;
    }

    private static String encode(Map<String, SrvRecord> internalDNS) {
        if (internalDNS == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : internalDNS.keySet()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append('{').append(key).append(',');
            sb.append(internalDNS.get(key).getHostname()).append(':');
            sb.append(internalDNS.get(key).getPort()).append('}');
        }
        return sb.toString();
    }

    private static Map<String, SrvRecord> decode(String encodedValue) {
        Map<String, SrvRecord> answer = new HashMap<>();
        StringTokenizer st = new StringTokenizer(encodedValue, "{},:");
        while (st.hasMoreElements()) {
            String key = st.nextToken();
            String host = st.nextToken();
            // Host entries in DNS should end with a ".".
            if (host.endsWith(".")) {
                host = host.substring(0, host.length()-1);
            }
            int port = Integer.parseInt(st.nextToken());
            // Normalize keys to lowercase for case-insensitive DNS name handling
            answer.put(key.toLowerCase(), new SrvRecord(host, port, false));
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
     * @return An ordered list of results (possibly empty, never null).
     */
    public static List<SrvRecord> srvLookup(@Nonnull final String service, @Nonnull final String proto, @Nonnull final String name) {
        logger.debug("DNS SRV Lookup for service '{}', protocol '{}' and name '{}'", service, proto, name);

        final String lookup = constructLookup(service, proto, name);

        final CacheableOptional<SrvRecord[]> optional = LOOKUP_CACHE.get(lookup);
        SrvRecord[] result;
        if (optional != null) {
            // Return a cached result.
            if (optional.isAbsent()) {
                logger.debug("DNS SRV lookup previously failed for '{}' (negative cache result)", lookup);
                result = new SrvRecord[0];
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
                    logger.trace("No SRV record found for '{}'", lookup);
                    result = new SrvRecord[0];
                } else {
                    result = new SrvRecord[srvRecords.size()];
                    final boolean directTLS = lookup.startsWith("_xmpps-"); // XEP-0368
                    for (int i = 0; i < srvRecords.size(); i++) {
                        result[i] = SrvRecord.from(((String) srvRecords.get(i)).split(" "), directTLS);
                    }
                    logger.trace("{} SRV record(s) found for '{}':", result.length, lookup);
                    for (SrvRecord srvRecord : result) {
                        logger.trace(" - {}", srvRecord);
                    }
                }
                LOOKUP_CACHE.put(lookup, CacheableOptional.of(result));
            } catch (NameNotFoundException e) {
                logger.trace("No SRV record found for '{}'", lookup);
                LOOKUP_CACHE.put(lookup, CacheableOptional.of(new SrvRecord[0])); // Empty result (different from negative result!)
                result = new SrvRecord[0];
            } catch (NamingException e) {
                logger.debug("DNS SRV lookup was unsuccessful for '{}': {}", lookup, e);
                LOOKUP_CACHE.put(lookup, CacheableOptional.of(null)); // Negative result cache (different from empty result!)
                result = new SrvRecord[0];
            }
        }

        // Do not store _prioritized_ results in the cache, as there is a random element to the prioritization that needs to happen every time.
        return SrvRecord.prioritize(result).stream().flatMap(Set::stream).collect(Collectors.toList());
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
     * return <em>false</em> for name: {@code notexample.org}, pattern: {@code *.example.org}
     * return <em>true</em>  for name: {@code xmpp.example.org}, pattern: {@code *.xmpp.example.org} (certificate edge case)
     *
     * Note: This method includes special handling for certificate compatibility where a domain name
     * can match a wildcard pattern for that same domain (e.g., {@code xmpp.example.org} matches
     * {@code *.xmpp.example.org}). While this behavior is non-standard for DNS wildcards, it is
     * required for Openfire's certificate management functionality.
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
            final String suffix = hayStack.substring( 2 ); // Remove "*."
            if ( needle.endsWith( suffix ) ) {
                // Check that the match is at a proper dot boundary or exact match
                final int suffixStart = needle.length() - suffix.length();
                if ( suffixStart == 0 ) {
                    // Exact match: needle equals the suffix (certificate edge case)
                    return true;
                }
                // Subdomain match: must have a dot before the suffix
                return needle.charAt( suffixStart - 1 ) == '.';
            }
        }
        return false;
    }
}
