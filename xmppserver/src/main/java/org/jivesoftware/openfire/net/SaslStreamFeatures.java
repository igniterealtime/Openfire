/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.fast.FastSessionState;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.sasl.MechanismName;
import org.jivesoftware.openfire.sasl.SaslMechanismEligibility;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the SASL-related stream features that a session is offered, and records what was rendered.
 *
 * Advertisement and recording are deliberately performed together. The SCRAM implementations use the mechanism names
 * that a peer was shown to detect channel-binding downgrades, and both the mechanism names and the XEP-0440
 * channel-binding types to compute the XEP-0474 downgrade-protection hash. A peer computes its own hash from what it
 * actually received, so a recorded set that differs from the rendered one fails authentication for every user, with
 * nothing to indicate why.
 *
 * What may be offered to a session at all is decided by {@link SaslMechanismEligibility}; this class turns that
 * decision into XML.
 */
public class SaslStreamFeatures
{
    private static final Logger Log = LoggerFactory.getLogger(SaslStreamFeatures.class);

    private SaslStreamFeatures() {
    }

    /**
     * Adds the SASL-related stream features for the given session, and records what was advertised.
     *
     * This method is the single place where the SASL mechanisms and the XEP-0440 channel-binding types that a
     * session is offered are determined. Both are recorded on the session as they are rendered, because SASL
     * mechanism implementations need to know exactly what the peer was shown: the SCRAM implementations use the
     * mechanism names to detect channel-binding downgrades, and use both sets to compute the XEP-0474 downgrade
     * protection hash. A hash taken over anything other than what the peer actually received will not match the
     * one the peer computes, and authentication will fail for every user.
     *
     * @param session  the session for which to advertise SASL features (cannot be null).
     * @param features the collection of stream features to add to (cannot be null).
     */
    public static void appendSASLFeatures(@Nonnull final LocalSession session, @Nonnull final List<Element> features)
    {
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Set<String> fastMechanisms = advertisableSASLMechanisms.stream()
            .filter(MechanismName::isFast).collect(Collectors.toUnmodifiableSet());
        final Set<String> standardMechanisms = advertisableSASLMechanisms.stream()
            .filter(mechanism -> !MechanismName.isFast(mechanism)).collect(Collectors.toUnmodifiableSet());
        SASLAuthentication.setAdvertisedSASLMechanisms(session, standardMechanisms);
        final boolean fastFeatureIsAdvertised = session instanceof ClientSession
            && SASLAuthentication.checkSASL2Permitted(session).isEmpty() && FastTokenManager.ENABLE_FAST.getValue();
        FastSessionState.setAdvertisedMechanisms(session, fastFeatureIsAdvertised ? fastMechanisms : Collections.emptySet());

        final Set<String> advertisableChannelBindingTypes = SaslMechanismEligibility.getAdvertisableChannelBindingTypes(session, advertisableSASLMechanisms);
        SASLAuthentication.setAdvertisedChannelBindingTypes(session, advertisableChannelBindingTypes);

        features.addAll(asSASLMechanisms(session, advertisableSASLMechanisms, advertisableChannelBindingTypes));
    }

    /**
     * Returns a list of XML elements representing the SASL mechanism features that are applicable to the given session.
     * The returned elements are suitable for inclusion in the stream features element sent to the peer.
     * Both SASL (RFC 6120) and SASL2 (XEP-0388) feature elements may be included, depending on configuration.
     * An empty list is returned if the session is already authenticated or if the session type is not recognized.
     *
     * @param session                         the local session for which to determine applicable SASL mechanism feature elements (cannot be null)
     * @param advertisableMechanismNames      The set of SASL mechanism names that are to be advertised.
     * @param advertisableChannelBindingTypes The set of channel binding types that are to be advertised.
     * @return a list of XML elements representing SASL mechanism features; never null, possibly empty
     */
    public static List<Element> asSASLMechanisms(@Nonnull final LocalSession session, @Nonnull final Set<String> advertisableMechanismNames, @Nonnull final Set<String> advertisableChannelBindingTypes)
    {
        final List<Element> features = new LinkedList<>();
        // Never list these if the session is already authenticated.
        if (session.isAuthenticated()) return features;

        if (session instanceof ClientSession) {
            final Element sasl1Mechs = asSASLMechanismsElementForClientSessions(advertisableMechanismNames, false);
            if (sasl1Mechs != null) {
                features.add(sasl1Mechs);
            }
            if (SASLAuthentication.checkSASL2Permitted(session).isEmpty()) {
                final Element sasl2Mechs = asSASLMechanismsElementForClientSessions(advertisableMechanismNames, true);
                if (sasl2Mechs != null) {
                    features.add(sasl2Mechs);
                }
            }
        } else if (session instanceof LocalIncomingServerSession) {
            final Element sasl1Mechs = asSASLMechanismsElementForServerSessions(advertisableMechanismNames, false);
            if (sasl1Mechs != null) {
                features.add(sasl1Mechs);
            }
            if (SASLAuthentication.checkSASL2Permitted(session).isEmpty()) {
                final Element sasl2Mechs = asSASLMechanismsElementForServerSessions(advertisableMechanismNames, true);
                if (sasl2Mechs != null) {
                    features.add(sasl2Mechs);
                }
            }
        } else {
            Log.debug("Unable to determine SASL mechanisms that are applicable to session '{}'. Unrecognized session type.", session);
            return features;
        }

        if (!advertisableChannelBindingTypes.isEmpty()) {
            final Element channelBindingTypesEl = DocumentHelper.createElement(new QName("sasl-channel-binding", new Namespace("", SASLAuthentication.SASL_CHANNEL_BINDING_NAMESPACE)));
            for (final String channelBindingType : advertisableChannelBindingTypes) {
                channelBindingTypesEl.addElement("channel-binding").addAttribute("type", channelBindingType);
            }
            features.add(channelBindingTypesEl);
        }

        return features;
    }

    /**
     * Returns an XML element advertising the SASL mechanisms available to a client session.
     *
     * The element will be in either the SASL (RFC 6120) or SASL2 (XEP-0388) namespace depending on
     * the {@code usingSASL2} parameter.
     *
     * May return {@code null} if the resulting element would be empty and the {@code sasl.client.suppressEmpty}
     * property is set to {@code true}.
     *
     * @param advertisableMechanismNames The set of SASL mechanism names that are to be advertised.
     * @param usingSASL2 {@code true} to generate a SASL2 {@code <authentication>} element;
     *                   {@code false} to generate a SASL1 {@code <mechanisms>} element
     * @return an XML element listing the available SASL mechanisms, or {@code null} if the element
     *         would be empty and suppression of empty elements is configured
     */
    @VisibleForTesting
    static Element asSASLMechanismsElementForClientSessions(@Nonnull final Set<String> advertisableMechanismNames, final boolean usingSASL2)
    {
        final Namespace namespace = new Namespace("", usingSASL2 ? SASLAuthentication.SASL2_NAMESPACE : SASLAuthentication.SASL_NAMESPACE );
        final QName qName = new QName(usingSASL2 ? "authentication" : "mechanisms", namespace);
        final Element result = DocumentHelper.createElement( qName );

        for (final String mech : advertisableMechanismNames) {
            if (MechanismName.isFast(mech)) continue; // FAST mechanisms live in the inline FAST feature.
            final Element mechanism = result.addElement("mechanism");
            mechanism.setText(mech);
        }
        if ( usingSASL2 )
        {
            Element inlineElement = result.addElement("inline");
            inlineElement.add(Bind2Request.featureElement());
            // Element sm = inlineElement.addElement(...);
            if (FastTokenManager.ENABLE_FAST.getValue()) {
                final Set<String> fastMechanisms = advertisableMechanismNames.stream()
                    .filter(MechanismName::isFast).collect(Collectors.toSet());
                if (!fastMechanisms.isEmpty()) inlineElement.add(FastTokenManager.featureElement(fastMechanisms));
            }
        }

        // OF-2072: Return null instead of an empty element, if so configured.
        if ( (usingSASL2 || JiveGlobals.getBooleanProperty("sasl.client.suppressEmpty", false)) && advertisableMechanismNames.isEmpty() ) {
            return null;
        }

        return result;
    }

    /**
     * Returns an XML element advertising the SASL mechanisms available to an incoming server session.
     *
     * The element will be in either the SASL (RFC 6120) or SASL2 (XEP-0388) namespace depending on the
     * {@code usingSASL2} parameter.
     *
     * May return {@code null} if the resulting element would be empty and the {@code sasl.server.suppressEmpty} property
     * is set to {@code true}.
     *
     * @param advertisableMechanismNames The set of SASL mechanism names that are to be advertised.
     * @param usingSASL2 {@code true} to generate a SASL2 {@code <authentication>} element in the SASL2 namespace;
     *                   {@code false} to generate a SASL1 {@code <mechanisms>} element
     * @return an XML element listing the available SASL mechanisms, or {@code null} if the element
     *         would be empty and suppression of empty elements is configured
     */
    @VisibleForTesting
    static Element asSASLMechanismsElementForServerSessions(Set<String> advertisableMechanismNames, boolean usingSASL2)
    {
        // OF-2072: Return null instead of an empty element, if so configured.
        // For SASL2, always null.
        if ((usingSASL2 || JiveGlobals.getBooleanProperty("sasl.server.suppressEmpty", false)) && advertisableMechanismNames.isEmpty()) {
            return null;
        }

        final Namespace namespace = new Namespace("", usingSASL2 ? SASLAuthentication.SASL2_NAMESPACE : SASLAuthentication.SASL_NAMESPACE );
        final QName qName = new QName(usingSASL2 ? "authentication" : "mechanisms", namespace);
        final Element result = DocumentHelper.createElement( qName );
        for (final String mech : advertisableMechanismNames) {
            final Element mechanism = result.addElement("mechanism");
            mechanism.setText(mech);
        }

        return result;
    }
}
