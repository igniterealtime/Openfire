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
package org.jivesoftware.openfire.server.diagnostics;

import org.jivesoftware.openfire.keystore.TrustStore;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Certificate;
import org.jivesoftware.util.CertificateManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Says which property of a remote server's certificate made this server reject it, for the connection test.
 *
 * These methods only explain a rejection; they never decide whether a certificate is accepted. That decision stays
 * with the trust manager during the TLS handshake, and with {@link CertificateManager#verifyCertificates} after it.
 *
 * @author Matthew Vivian
 */
public final class CertificateVerdicts
{
    private CertificateVerdicts()
    {
    }

    /**
     * @param handshakeFailure why a TLS handshake failed.
     * @return whether it failed because this server's trust manager rejected the remote certificate.
     */
    public static boolean isCertificateRejection(@Nonnull final Throwable handshakeFailure)
    {
        return findCause(handshakeFailure, CertificateException.class).isPresent();
    }

    /**
     * Explains why this server's trust manager rejected the remote certificate during the TLS handshake.
     *
     * Expiry, a validity period that has not started and revocation are named; any other failure to build a trusted
     * certification path (an unknown issuer, a self-signed certificate, a broken chain) is reported as untrusted.
     *
     * @param handshakeFailure why the handshake failed; {@link #isCertificateRejection} is true for it.
     * @return the property that failed.
     */
    @Nonnull
    public static Certificate ofHandshakeRejection(@Nonnull final Throwable handshakeFailure)
    {
        final Optional<CertPathValidatorException> validation = findCause(handshakeFailure, CertPathValidatorException.class);
        final CertPathValidatorException.Reason reason = validation.map(CertPathValidatorException::getReason).orElse(null);

        if (reason == CertPathValidatorException.BasicReason.REVOKED || findCause(handshakeFailure, CertificateRevokedException.class).isPresent()) {
            return Certificate.revoked();
        }
        // Only the remote server's own certificate is named: the messages for expiry and a validity period that has not
        // started say "the remote certificate". A failure on an intermediate is reported as untrusted, without its date.
        final Optional<X509Certificate> failed = validation.flatMap(CertificateVerdicts::failedEndEntityCertificate);
        if (failed.isPresent()) {
            if (reason == CertPathValidatorException.BasicReason.EXPIRED || findCause(handshakeFailure, CertificateExpiredException.class).isPresent()) {
                return Certificate.expired(failed.get().getNotAfter().toInstant());
            }
            if (reason == CertPathValidatorException.BasicReason.NOT_YET_VALID || findCause(handshakeFailure, CertificateNotYetValidException.class).isPresent()) {
                return Certificate.notYetValid(failed.get().getNotBefore().toInstant());
            }
        }
        return Certificate.untrusted();
    }

    /**
     * Explains why a certificate chain that the TLS handshake accepted was rejected by the identity check that
     * follows it ({@link CertificateManager#verifyCertificates}), which checks the same things in the same order.
     *
     * @param chain the certificates the remote presented; null or empty when it presented none.
     * @param domain the domain the remote is expected to be.
     * @param trustStore the trust store used for server-to-server connections.
     * @return the property that failed; never valid, as the chain was rejected.
     */
    @Nonnull
    public static Certificate ofRejectedChain(@Nullable final java.security.cert.Certificate[] chain, @Nonnull final String domain, @Nonnull final TrustStore trustStore)
    {
        if (chain == null || chain.length == 0) {
            return Certificate.notReceived();
        }
        if (chain[0] instanceof X509Certificate endEntity) {
            try {
                endEntity.checkValidity();
            } catch (final CertificateExpiredException e) {
                return Certificate.expired(endEntity.getNotAfter().toInstant());
            } catch (final CertificateNotYetValidException e) {
                return Certificate.notYetValid(endEntity.getNotBefore().toInstant());
            }
        }
        final X509Certificate trusted = trustStore.getEndEntityCertificateWithoutLogging(chain);
        if (trusted == null) {
            return Certificate.untrusted();
        }
        return Certificate.nameMismatch(CertificateManager.getServerIdentities(trusted));
    }

    /**
     * @return the certificate at which validation of the path failed, when the validator says which one it was and it
     *         is the end-entity certificate (the first in the path).
     */
    private static Optional<X509Certificate> failedEndEntityCertificate(final CertPathValidatorException validation)
    {
        final CertPath path = validation.getCertPath();
        final int index = validation.getIndex();
        if (path == null || index != 0 || path.getCertificates().isEmpty()) {
            return Optional.empty();
        }
        return path.getCertificates().get(index) instanceof X509Certificate certificate ? Optional.of(certificate) : Optional.empty();
    }

    private static <T extends Throwable> Optional<T> findCause(final Throwable throwable, final Class<T> type)
    {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (type.isInstance(cause)) {
                return Optional.of(type.cast(cause));
            }
        }
        return Optional.empty();
    }
}
