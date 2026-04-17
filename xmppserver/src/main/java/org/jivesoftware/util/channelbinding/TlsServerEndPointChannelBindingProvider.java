/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.util.channelbinding;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PSSParameterSpec;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Implementation of {@link ChannelBindingProvider} for the <code>tls-server-end-point</code> channel binding type (RFC 5929).
 *
 * This provider extracts channel binding data from a {@link SSLEngine}, using the hash of the server's certificate
 * as specified by RFC 5929. The hash algorithm is chosen based on the certificate's signature algorithm.
 *
 * The channel binding data is always derived from the server certificate, regardless of which side computes it.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5929">RFC 5929: Channel Bindings for TLS</a>
 */
public class TlsServerEndPointChannelBindingProvider implements ChannelBindingProvider
{
    private static final Logger Log = LoggerFactory.getLogger(TlsServerEndPointChannelBindingProvider.class);
    private static final Set<String> SERVER_END_POINT_WEAK_HASH_ALGORITHMS = Set.of("MD5", "SHA1", "SHA-1");
    private static final String SERVER_END_POINT_FALLBACK_HASH_ALGORITHM = "SHA-256";
    private static final Pattern ALGORITHM_NAME_PATTERN = Pattern.compile("^SHA(?!\\d+-)(\\d)");

    @Override
    public String getType()
    {
        return ChannelBindingType.TLS_SERVER_END_POINT.getPrefix();
    }

    /**
     * Attempts to extract the channel binding data from the provided SSLEngine. This is typically the hash of the
     * server's certificate. The hash algorithm is chosen based on the certificate's signature algorithm per
     * RFC 5929 §4.1.
     *
     * The tls-server-end-point binding is always derived from the server certificate, regardless of which side computes
     * it. To determine if the local entity is acting in server or client mode, the engine's #getUseClientMode() method
     * is evaluated.
     *
     * @param engine the SSLEngine from which to extract channel binding data (must not be null)
     * @return an Optional containing the channel binding data, or empty if unavailable or unsupported
     */
    @Override
    public Optional<byte[]> getChannelBinding(@Nonnull final SSLEngine engine)
    {
        Objects.requireNonNull(engine, "engine must not be null");
        final SSLSession session = engine.getSession();
        try
        {
            // This binding requires the server certificate to be used, which is the local certificate when we're the
            // server, but the peer's certificate when we're establishing a connection to a remote server (e.g. s2s).
            final Certificate[] certs;
            try {
                certs = engine.getUseClientMode() ? session.getPeerCertificates() : session.getLocalCertificates();
            } catch (SSLPeerUnverifiedException e) {
                return Optional.empty();
            }

            // RFC 5929 specifies the end-entity certificate (first in chain)
            if (certs == null || certs.length == 0 || !(certs[0] instanceof X509Certificate cert)) {
                return Optional.empty();
            }

            final String hashAlg;
            if ("1.2.840.113549.1.1.10".equals(cert.getSigAlgOID())) { // Use OID instead of name for PSS detection (more robust)
                // RSASSA-PSS is effectively the only commonly used signature algorithm where the hash function is not
                // encoded in the algorithm name; all other mainstream algorithms either include the hash in the name or
                // (like Ed25519) use no hash at all, in which case returning "undefined" is correct per RFC 5929.
                hashAlg = resolveServerEndPointHashAlgorithmPSS(cert);
            } else {
                hashAlg = resolveServerEndPointHashAlgorithm(cert.getSigAlgName());
            }
            if (hashAlg == null) {
                Log.debug("TLS server end point channel binding is undefined for signature algorithm '{}' for session: {}", cert.getSigAlgName(), session);
                return Optional.empty();
            }
            final MessageDigest md = MessageDigest.getInstance(hashAlg);
            return Optional.of(md.digest(cert.getEncoded()));
        } catch (final Exception e) {
            Log.trace("Failed to compute TLS server end point channel binding for session: {}", session, e);
        }
        return Optional.empty();
    }

    /**
     * Resolves the hash algorithm for a certificate using RSASSA-PSS signature algorithm.
     *
     * Extracts the hash algorithm from the PSS parameters of the certificate and normalizes it for use in TLS server
     * end point channel binding. Returns null if the hash algorithm cannot be determined.
     *
     * @param cert the X509Certificate with RSASSA-PSS signature algorithm
     * @return the normalized hash algorithm name, or null if unavailable
     */
    @VisibleForTesting
    String resolveServerEndPointHashAlgorithmPSS(final X509Certificate cert)
    {
        final String pssHash = extractPssHashAlgorithm(cert);
        if (pssHash == null) {
            return null;
        }
        final String normalized = normalizeServerEndPointHashAlgorithmName(pssHash);
        return substituteWeakServerEndPointHashAlgorithm(normalized);
    }

    /**
     * Extracts the hash algorithm name from the RSASSA-PSS parameters of the given X509 certificate.
     *
     * @param cert the X509Certificate with RSASSA-PSS signature algorithm
     * @return the hash algorithm name, or null if unavailable or parsing fails
     */
    @VisibleForTesting
    String extractPssHashAlgorithm(X509Certificate cert)
    {
        try {
            final byte[] params = cert.getSigAlgParams();
            if (params == null) {
                return null;
            }
            final AlgorithmParameters ap = AlgorithmParameters.getInstance("RSASSA-PSS");
            ap.init(params);
            final PSSParameterSpec spec = ap.getParameterSpec(PSSParameterSpec.class);
            return spec.getDigestAlgorithm();
        } catch (Exception e) {
            Log.trace("Failed to parse RSASSA-PSS parameters", e);
            return null;
        }
    }

    /**
     * Resolves the hash algorithm to use for TLS server end point channel binding per RFC 5929 §4.1:
     *
     * <ul>
     *   <li>MD5 or SHA-1 -> SHA-256</li>
     *   <li>Any other single hash function -> that hash function</li>
     *   <li>No hash function or multiple hash functions -> undefined (returns null)</li>
     * </ul>
     *
     * @param sigAlgName the certificate's signature algorithm name (e.g. {@code "SHA256withRSA"})
     * @return the JCA hash algorithm name to use, or {@code null} if channel binding is undefined
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc5929">RFC 5929: Channel Bindings for TLS</a>
     */
    @VisibleForTesting
    String resolveServerEndPointHashAlgorithm(final String sigAlgName)
    {
        if (sigAlgName == null) {
            return null;
        }
        final String[] parts = sigAlgName.split("(?i)with", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            Log.trace("No hash function for signature algorithm '{}'", sigAlgName);
            return null;
        }
        final String normalizedHash = normalizeServerEndPointHashAlgorithmName(parts[0].trim());
        final String result = substituteWeakServerEndPointHashAlgorithm(normalizedHash);
        Log.trace("Hash function for signature algorithm '{}': '{}'", sigAlgName, result);
        return result;
    }

    /**
     * Normalizes a hash algorithm name to its canonical JCA form for TLS server end point channel binding by inserting the dash that
     * {@link java.security.cert.X509Certificate#getSigAlgName()} omits in SHA-2 family names. For example:
     * {@code "SHA256"} becomes {@code "SHA-256"}, {@code "SHA512"} becomes {@code "SHA-512"}. Already-correct names
     * ({@code "SHA-256"}, {@code "SHA3-256"}, {@code "MD5"}) are unchanged.
     */
    @VisibleForTesting
    String normalizeServerEndPointHashAlgorithmName(final String name)
    {
        return ALGORITHM_NAME_PATTERN
            .matcher(name.toUpperCase(Locale.ROOT))
            .replaceAll("SHA-$1");
    }

    /**
     * Substitutes weak hash algorithms (MD5, SHA-1) with SHA-256 for TLS server end point channel binding, per RFC 5929 §4.1.
     *
     * @param hashAlg the hash algorithm name
     * @return SHA-256 if the input is a weak hash, otherwise the input hash algorithm
     */
    @VisibleForTesting
    String substituteWeakServerEndPointHashAlgorithm(final String hashAlg)
    {
        if (SERVER_END_POINT_WEAK_HASH_ALGORITHMS.contains(hashAlg)) {
            return SERVER_END_POINT_FALLBACK_HASH_ALGORITHM;
        }
        return hashAlg;
    }
}


