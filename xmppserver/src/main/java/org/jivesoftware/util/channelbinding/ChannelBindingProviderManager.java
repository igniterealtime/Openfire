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
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLEngine;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a set of providers that can extract channel binding data of various types from SSL engines.
 *
 * This class offers a best-effort mechanism to obtain channel binding values as defined in RFC 5705, RFC 5929, RFC 9266, etc.
 * It dynamically detects, at runtime, whether the underlying TLS implementation supports exporting keying material for the
 * requested channel binding type, without requiring a hard dependency on any particular provider or JDK version. Providers
 * are tried in order of registration until one succeeds or all fail.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5929">RFC 5929: Channel Bindings for TLS</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5705">RFC 5705: Keying Material Exporters for Transport Layer Security (TLS)</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9266">RFC 9266: Channel Bindings for TLS 1.3</a>
 */
public class ChannelBindingProviderManager
{
    private static final Logger Log = LoggerFactory.getLogger(ChannelBindingProviderManager.class);

    private static final ChannelBindingProviderManager INSTANCE = new ChannelBindingProviderManager();

    static {
        INSTANCE.addProvider(new TlsServerEndPointChannelBindingProvider());
    }

    // Map from RFC-defined unique prefix to ordered list of providers for that type.
    private final Map<String, List<ChannelBindingProvider>> providers = new ConcurrentHashMap<>();

    /**
     * Returns the singleton instance of the manager.
     *
     * @return the singleton ChannelBindingProviderManager instance
     */
    public static ChannelBindingProviderManager getInstance()
    {
        return INSTANCE;
    }

    /**
     * Constructs a new manager instance. Intended primarily for testing; typical usage should prefer the singleton
     * returned by getInstance().
     */
    @VisibleForTesting
    ChannelBindingProviderManager()
    {
    }

    /**
     * Registers a provider for its declared channel binding type (RFC-defined unique prefix) at the tail (end) of the
     * list. Multiple providers can be registered for the same type; they are tried in registration order (head to tail).
     *
     * @param provider the provider to register
     */
    public void addProvider(@Nonnull final ChannelBindingProvider provider)
    {
        Objects.requireNonNull(provider, "provider must not be null");
        final String prefix = provider.getType();
        Objects.requireNonNull(prefix, "provider's type must not be null");
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("provider's type must not be empty");
        }

        Log.trace("Registering channel binding provider at tail: {} for prefix '{}'", provider.getClass().getName(), prefix);
        providers.computeIfAbsent(prefix, k -> new CopyOnWriteArrayList<>()).add(provider);
    }

    /**
     * Registers a provider for its declared channel binding type (RFC-defined unique prefix) at the head (start) of the
     * list. Multiple providers can be registered for the same type; they are tried in registration order (head to tail).
     *
     * @param provider the provider to register
     */
    public void addProviderToHead(@Nonnull final ChannelBindingProvider provider)
    {
        Objects.requireNonNull(provider, "provider must not be null");
        final String prefix = provider.getType();
        Objects.requireNonNull(prefix, "provider's type must not be null");
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("provider's type must not be empty");
        }

        Log.trace("Registering channel binding provider at head: {} for prefix '{}'", provider.getClass().getName(), prefix);
        providers.computeIfAbsent(prefix, k -> new CopyOnWriteArrayList<>()).add(0, provider);
    }

    /**
     * Removes a specific provider instance for the given channel binding type prefix, if present.
     *
     * When multiple instances are registered, only the first instance is removed.
     *
     * @param provider the provider instance to remove
     * @return if this manager contained the specified provider
     */
    public boolean removeProvider(@Nonnull final ChannelBindingProvider provider)
    {
        Objects.requireNonNull(provider, "provider must not be null");
        final String prefix = provider.getType();
        Objects.requireNonNull(prefix, "provider's type must not be null");
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("provider's type must not be empty");
        }

        Log.trace("Removing channel binding provider {} for prefix '{}'", provider.getClass().getName(), prefix);
        final List<ChannelBindingProvider> list = providers.get(prefix);
        boolean removed = false;
        if (list != null) {
            removed = list.remove(provider);
            if (list.isEmpty()) {
                providers.remove(prefix);
            }
        }
        return removed;
    }

    /**
     * Attempts to obtain the channel binding data for the given type prefix and SSL engine by delegating to registered
     * providers in order. Returns the first successful result, or an empty Optional if none succeed.
     *
     * @param cbPrefix the RFC-defined unique prefix for the channel binding type (must not be null or empty)
     * @param engine the SSL engine from which to extract channel binding data
     * @return an Optional containing the channel binding data, or empty if unavailable
     */
    public Optional<byte[]> getChannelBinding(@Nonnull final String cbPrefix, @Nonnull final SSLEngine engine)
    {
        Objects.requireNonNull(cbPrefix, "cbPrefix must not be null");
        if (cbPrefix.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        Objects.requireNonNull(engine, "engine must not be null");

        Log.trace("Getting channel binding '{}' for engine: {}", cbPrefix, engine);
        final List<ChannelBindingProvider> list = providers.get(cbPrefix);
        if (list == null || list.isEmpty())
        {
            Log.debug("No channel binding provider registered for prefix '{}'", cbPrefix);
            return Optional.empty();
        }
        for (final ChannelBindingProvider provider : list) {
            try {
                Log.trace("Trying provider: {}", provider.getClass().getName());
                final Optional<byte[]> channelBindingData = provider.getChannelBinding(engine);
                if (channelBindingData.isPresent()) {
                    Log.debug("Channel binding '{}' found for engine: {} by provider {}", cbPrefix, engine, provider.getClass().getName());
                    return channelBindingData;
                }
            } catch (Exception t) {
                Log.warn("Provider '{}' failed to obtain channel binding '{}' for engine: {}", provider.getClass().getName(), cbPrefix, engine, t);
            }
        }
        Log.debug("No channel binding '{}' found for engine: {}", cbPrefix, engine);
        return Optional.empty();
    }

    /**
     * Checks if there is at least one provider registered for the given channel binding type prefix.
     *
     * @param cbPrefix the RFC-defined unique prefix for the channel binding type (must not be null or empty)
     * @return true if at least one provider is registered for the prefix, false otherwise
     */
    public boolean supportsChannelBinding(@Nonnull final String cbPrefix)
    {
        Objects.requireNonNull(cbPrefix, "cbPrefix must not be null");
        if (cbPrefix.isEmpty()) {
            throw new IllegalArgumentException("cbPrefix must not be empty");
        }

        return !providers.getOrDefault(cbPrefix, List.of()).isEmpty();
    }

    /**
     * Returns an unmodifiable set of all supported channel binding type prefixes for which at least one provider is registered.
     *
     * @return a set of RFC-defined unique prefixes for supported channel binding types
     */
    public Set<String> getSupportedChannelBindingTypes()
    {
        return Collections.unmodifiableSet(providers.keySet());
    }

    /**
     * Returns an XML element that describes the supported SASL channel binding types, if applicable.
     *
     * This method inspects the provided SASL mechanisms element. If at least one mechanism ends with "-PLUS"
     * and the server supports one or more channel binding types, it returns an element that advertises these types.
     * Otherwise, it returns an empty Optional.
     *
     * @param saslMechanisms The XML element containing SASL mechanisms to inspect.
     * @return An Optional containing the capability element if channel binding types should be advertised, or empty otherwise.
     * @see <a href="https://xmpp.org/extensions/xep-0440.html">XEP-0440: SASL Channel-Binding Type Capability</a>
     */
    public Optional<Element> getSASLChannelBindingTypeCapabilityElement(@Nonnull final Element saslMechanisms)
    {
        if (saslMechanisms.elements("mechanism").stream().noneMatch(mech -> mech.getText().endsWith("-PLUS"))) {
            return Optional.empty();
        }

        final Set<String> supportedChannelBindingTypes = this.getSupportedChannelBindingTypes();
        if (supportedChannelBindingTypes.isEmpty()) {
            return Optional.empty();
        }

        final Element result = DocumentHelper.createElement(new QName("sasl-channel-binding", new Namespace("", "urn:xmpp:sasl-cb:0")));
        for (final String channelBindingType : supportedChannelBindingTypes) {
            result.addElement("channel-binding").addAttribute("type", channelBindingType);
        }
        return Optional.of(result);
    }
}
