/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util.cert;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Periodically evaluates the TLS certificates in all identity stores of Openfire. Sends out a warning (in the form of
 * an XMPP message) to all server administrators when a certificate is detected that is expired, or is about to expire.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CertificateExpiryChecker extends BasicModule
{
    private static final Logger Log = LoggerFactory.getLogger(CertificateExpiryChecker.class);

    /**
     * Enables or disabled the period check for expiry of Openfire's TLS certificates.
     */
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("ssl.certificates.expirycheck.service-enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * Determines if the administrators receive a message when an (almost) expired TLS certificate is detected.
     */
    public static final SystemProperty<Boolean> NOTIFY_ADMINS = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("ssl.certificates.expirycheck.notify-admins")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * How often to check for (nearly) expired TLS certificates.
     */
    public static final SystemProperty<Duration> FREQUENCY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("ssl.certificates.expirycheck.frequency")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.HOURS)
        .setDefaultValue(Duration.ofHours(6))
        .setMinValue(Duration.ofHours(1))
        .addListener((duration) -> {
            XMPPServer.getInstance().getCertificateExpiryChecker().stop();
            XMPPServer.getInstance().getCertificateExpiryChecker().start();
        })
        .build();

    /**
     * How long before a TLS certificate will expire a warning is to be sent out to admins.
     */
    public static final SystemProperty<Duration> WARNING_PERIOD = SystemProperty.Builder.ofType(Duration.class)
        .setKey("ssl.certificates.expirycheck.warning-period")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.HOURS)
        .setMinValue(Duration.ofHours(1))
        .setDefaultValue(Duration.ofDays(7))
        .build();

    private ExpiryCheckerTask expiryCheckerTask;

    public CertificateExpiryChecker()
    {
        super("Certificate Expiry Checker");
    }

    @Override
    public void start() throws IllegalStateException
    {
        expiryCheckerTask = new ExpiryCheckerTask();
        TaskEngine.getInstance().schedule(expiryCheckerTask, Duration.ofSeconds(20), FREQUENCY.getValue());
    }

    @Override
    public void stop()
    {
        if (expiryCheckerTask != null) {
            TaskEngine.getInstance().cancelScheduledTask(expiryCheckerTask);
            expiryCheckerTask = null;
        }
    }

    public static class ExpiryCheckerTask extends TimerTask
    {
        @Override
        public void run()
        {
            if (!ENABLED.getValue()) {
                Log.debug("Skipping TLS certificate expiry check, as it has been disabled by configuration.");
                return;
            }
            Log.debug("Starting TLS certificate expiry check.");

            try {
                final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();

                // Openfire can use different identity stores for different types of connection. We'll iterate over all of them.
                final Set<IdentityStore> identityStores = new HashSet<>();
                for (final ConnectionType connectionType : ConnectionType.values()) {
                    identityStores.add(certificateStoreManager.getIdentityStore(connectionType));
                }

                // Time until expiry of the certificate with the lowest 'notAfter' value.
                Instant lowestNotAfter = Instant.MAX;

                for (final IdentityStore identityStore : identityStores) {
                    final Optional<Instant> oldest;
                        oldest = identityStore.getAllCertificates().values().stream()
                            .map(X509Certificate::getNotAfter)
                            .filter(Objects::nonNull)
                            .map(Date::toInstant)
                            .sorted()
                            .findFirst();

                    if (oldest.isPresent()) {
                        final Instant oldestDate = oldest.get();
                        if (oldestDate.isBefore(lowestNotAfter)) {
                            lowestNotAfter = oldestDate;
                        }
                    }
                }

                // Check if there's expired or nearly expired certificates.
                final boolean hasExpired = lowestNotAfter.isBefore(Instant.now());
                final boolean hasNearlyExpired = lowestNotAfter.minus(WARNING_PERIOD.getValue()).isBefore(Instant.now());
                if (hasExpired) {
                    Log.warn("One or more TLS certificates used by Openfire have expired. This can cause connectivity issues. Please use the Openfire Admin Console to review the state of all certificates in each of Openfire's \"identity\" certificate stores. Replace certificates where need.");
                    if (NOTIFY_ADMINS.getValue()) {
                        XMPPServer.getInstance().sendMessageToAdmins(LocaleUtils.getLocalizedString("ssl.certificates.expirycheck.notification-message.expired"));
                    }
                } else if (hasNearlyExpired) {
                    Log.info("One or more TLS certificates used by Openfire will expire soon. This can cause connectivity issues. Please use the Openfire Admin Console to review the state of all certificates in each of Openfire's \"identity\" certificate stores. Replace certificates where need.");
                    if (NOTIFY_ADMINS.getValue()) {
                        XMPPServer.getInstance().sendMessageToAdmins(LocaleUtils.getLocalizedString("ssl.certificates.expirycheck.notification-message.nearly-expired"));
                    }
                } else {
                    Log.debug("None of the TLS certificates used by Openfire have expired or will expire soon.");
                }
            } catch (Throwable e) {
                Log.warn("An unexpected exception prevented the period check for expired TLS certificates from executing successfully.", e);
            }
        }
    }
}
