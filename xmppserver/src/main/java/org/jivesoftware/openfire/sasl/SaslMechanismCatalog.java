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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServerFactory;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The SASL mechanisms that this server can offer, and the configuration that governs them.
 *
 * Three distinct sets are involved, and they narrow in this order:
 * <ol>
 *   <li>The <em>enabled</em> mechanisms, from the {@code sasl.mechs} property. This is what an administrator has
 *       chosen to allow, and is the only one of the three that is stored.</li>
 *   <li>The <em>implemented</em> mechanisms, for which some registered JCA provider supplies a
 *       {@link javax.security.sasl.SaslServer}.</li>
 *   <li>The <em>supported</em> mechanisms: those that are both enabled and implemented, and whose additional
 *       requirements this deployment meets, such as an AuthProvider that can supply the necessary credentials, or an
 *       available channel-binding implementation.</li>
 * </ol>
 *
 * A mechanism being supported does not mean it will be offered to a given peer. That is a further narrowing, made per
 * session, and it lives elsewhere.
 *
 * This class also owns the registration of Openfire's own SASL provider and the reloading of the mechanism list when
 * its property changes, both of which happen when the class is first loaded.
 */
public class SaslMechanismCatalog
{
    private static final Logger Log = LoggerFactory.getLogger(SaslMechanismCatalog.class);

    /**
     * The mechanism names that configuration allows. Not all of them are necessarily available; see
     * {@link #getSupportedMechanisms()}.
     */
    private static Set<String> mechanisms = new HashSet<>();

    static
    {
        // Add (proprietary) Providers of SASL implementation to the Java security context.
        if (Security.getProvider( "JiveSoftware" ) == null) {
            Security.addProvider(new SaslProvider());
        }

        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("sasl.mechs");
        JiveGlobals.migrateProperty("sasl.gssapi.debug");
        JiveGlobals.migrateProperty("sasl.gssapi.config");
        JiveGlobals.migrateProperty("sasl.gssapi.useSubjectCredsOnly");

        initMechanisms();

        PropertyEventDispatcher.addListener( new PropertyEventListener()
        {
            @Override
            public void propertySet( String property, Map<String, Object> params )
            {
                if ("sasl.mechs".equals( property ) )
                {
                    initMechanisms();
                }
            }

            @Override
            public void propertyDeleted( String property, Map<String, Object> params )
            {
                if ("sasl.mechs".equals( property ) )
                {
                    initMechanisms();
                }
            }

            @Override
            public void xmlPropertySet( String property, Map<String, Object> params )
            {}

            @Override
            public void xmlPropertyDeleted( String property, Map<String, Object> params )
            {}
        } );
    }

    private SaslMechanismCatalog() {
    }

    /**
     * Forces this class to be initialized, which registers Openfire's SASL provider with the Java security context and
     * loads the configured mechanism names.
     *
     * Callers that go on to use this class need not invoke this method; class initialization takes care of it. It
     * exists for the benefit of code that depends on the provider being registered without otherwise consulting the
     * catalog.
     */
    public static void initialize() {
        // Intentionally empty: invoking it is enough to trigger class initialization.
    }

    /**
     * Returns {@code true} if configuration allows the given mechanism to be used.
     *
     * This says nothing about whether the mechanism is implemented, or whether this deployment meets its
     * requirements; see {@link #getSupportedMechanisms()} for that.
     *
     * @param mechanismName the SASL mechanism name to check, upper-cased (cannot be null)
     * @return {@code true} if the mechanism is enabled by configuration
     */
    public static boolean isEnabled(@Nonnull final String mechanismName)
    {
        return mechanisms.contains(mechanismName);
    }

    /**
     * Adds a new SASL mechanism to the list of supported SASL mechanisms by the server. The
     * new mechanism will be offered to clients and connection managers as stream features.<p>
     *
     * Note: this method simply registers the SASL mechanism to be advertised as a supported
     * mechanism by Openfire. Actual SASL handling is done by Java itself, so you must add
     * the provider to Java.
     *
     * @param mechanismName the name of the new SASL mechanism (cannot be null or an empty String).
     */
    public static void addSupportedMechanism(String mechanismName) {
        if ( mechanismName == null || mechanismName.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'mechanism' must cannot be null or an empty string." );
        }
        mechanisms.add( mechanismName.toUpperCase() );
        Log.info( "Support added for the '{}' SASL mechanism.", mechanismName.toUpperCase() );
    }

    /**
     * Removes a SASL mechanism from the list of supported SASL mechanisms by the server.
     *
     * @param mechanismName the name of the SASL mechanism to remove (cannot be null or empty, not case-sensitive).
     */
    public static void removeSupportedMechanism(String mechanismName) {
        if ( mechanismName == null || mechanismName.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'mechanism' must cannot be null or an empty string." );
        }

        if ( mechanisms.remove( mechanismName.toUpperCase() ) )
        {
            Log.info( "Support removed for the '{}' SASL mechanism.", mechanismName.toUpperCase() );
        }
    }

    /**
     * Returns the list of supported SASL mechanisms by the server. Note that Java may have
     * support for more mechanisms but some of them may not be returned since a special setup
     * is required that might be missing. Use {@link #addSupportedMechanism(String)} to add
     * new SASL mechanisms.
     *
     * @return the set of supported SASL mechanisms by the server.
     */
    public static Set<String> getSupportedMechanisms()
    {
        // List all mechanism names for which there's an implementation.
        final Set<String> implementedMechanisms = getImplementedMechanisms();

        // Start off with all mechanisms that we intend to support.
        final Set<String> answer = new HashSet<>( mechanisms );
        if (FastTokenManager.ENABLE_FAST.getValue()) {
            answer.addAll(FastTokenManager.MECHANISMS);
        }

        // Clean up not-available mechanisms.
        for ( final Iterator<String> it = answer.iterator(); it.hasNext(); )
        {
            final String mechanism = it.next();

            if ( !implementedMechanisms.contains( mechanism ) )
            {
                Log.trace( "Cannot support '{}' as there's no implementation available.", mechanism );
                it.remove();
                continue;
            }

            if (MechanismName.requiresChannelBinding(mechanism)) {
                final String requiredCbType = MechanismName.requiredChannelBindingType(mechanism);
                final ChannelBindingProviderManager cbManager = ChannelBindingProviderManager.getInstance();
                if (requiredCbType != null) {
                    // Mechanism encodes a specific CB type (e.g. HT-*-UNIQ): only offer it when
                    // that exact type is supported.
                    if (!cbManager.supportsChannelBinding(requiredCbType)) {
                        Log.trace( "Cannot support '{}' as channel binding type '{}' is not available.", mechanism, requiredCbType );
                        it.remove();
                        continue;
                    }
                } else {
                    // Mechanism uses runtime-negotiated CB (e.g. SCRAM-SHA-1-PLUS): require at
                    // least one CB type to be available.
                    if (cbManager.getSupportedChannelBindingTypes().isEmpty()) {
                        Log.trace( "Cannot support '{}' as there's no implementation available for channel binding.", mechanism );
                        it.remove();
                        continue;
                    }
                }
            }

            switch ( mechanism )
            {
                case "CRAM-MD5": // intended fall-through
                case "DIGEST-MD5":
                    // Check if the user provider in use supports passwords retrieval. Access to the users passwords will be required by the CallbackHandler.
                    if ( !AuthFactory.supportsPasswordRetrieval() )
                    {
                        Log.trace( "Cannot support '{}' as the AuthProvider that's in use does not support password retrieval.", mechanism );
                        it.remove();
                    }
                    break;

                case ScramSha1SaslServer.MECHANISM_NAME: // intended fall-through
                case ScramSha1SaslServer.MECHANISM_NAME+"-PLUS": // intended fall-through
                case ScramSha256SaslServer.MECHANISM_NAME: // intended fall-through
                case ScramSha256SaslServer.MECHANISM_NAME+"-PLUS": // intended fall-through
                case ScramSha512SaslServer.MECHANISM_NAME: // intended fall-through
                case ScramSha512SaslServer.MECHANISM_NAME+"-PLUS":
                    if ( !AuthFactory.supportsScram() )
                    {
                        Log.trace( "Cannot support '{}' as the AuthProvider that's in use does not support SCRAM.", mechanism );
                        it.remove();
                    }
                    break;

                case "ANONYMOUS":
                    if (!AnonymousSaslServer.ENABLED.getValue()) {
                        Log.trace( "Cannot support '{}' as it has been disabled by configuration.", mechanism );
                        it.remove();
                    }
                    break;

                case "JIVE-SHAREDSECRET":
                    if ( !JiveSharedSecretSaslServer.isSharedSecretAllowed() )
                    {
                        Log.trace( "Cannot support '{}' as it has been disabled by configuration.", mechanism );
                        it.remove();
                    }
                    break;

                case "GSSAPI":
                    final String gssapiConfig = JiveGlobals.getProperty( "sasl.gssapi.config" );
                    if ( gssapiConfig != null )
                    {
                        System.setProperty( "java.security.krb5.debug", JiveGlobals.getProperty( "sasl.gssapi.debug", "false" ) );
                        System.setProperty( "java.security.auth.login.config", gssapiConfig );
                        System.setProperty( "javax.security.auth.useSubjectCredsOnly", JiveGlobals.getProperty( "sasl.gssapi.useSubjectCredsOnly", "false" ) );
                    }
                    else
                    {
                        Log.trace( "Cannot support '{}' as the 'sasl.gssapi.config' property has not been defined.", mechanism );
                        it.remove();
                    }
                    break;
            }
        }
        return answer;
    }

    /**
     * Returns a collection of mechanism names for which the JVM has an implementation available.
     * <p>
     * Note that this need not (and likely will not) correspond with the list of mechanisms that is offered to XMPP
     * peer entities, which is provided by #getSupportedMechanisms.
     *
     * @return a collection of SASL mechanism names (never null, possibly empty)
     */
    public static Set<String> getImplementedMechanisms()
    {
        final Set<String> result = new HashSet<>();
        final Enumeration<SaslServerFactory> saslServerFactories = Sasl.getSaslServerFactories();
        while ( saslServerFactories.hasMoreElements() )
        {
            final SaslServerFactory saslServerFactory = saslServerFactories.nextElement();
            Collections.addAll( result, saslServerFactory.getMechanismNames( null ) );
        }
        return result;
    }

    /**
     * Returns a collection of SASL mechanism names that forms the source pool from which the mechanisms that are
     * eventually being offered to peers are obtained.
     **
     * When a mechanism is not returned by this method, it will never be offered, but when a mechanism is returned
     * by this method, there is no guarantee that it will be offered.
     *
     * Apart from being returned in this method, an implementation must be available (see {@link #getImplementedMechanisms()}
     * and configuration or other characteristics of this server must not prevent a particular mechanism from being
     * used (see @{link {@link #getSupportedMechanisms()}}.
     *
     * @return A collection of mechanisms that are considered for use in this instance of Openfire.
     */
    public static List<String> getEnabledMechanisms()
    {
        return JiveGlobals.getListProperty("sasl.mechs",
            Arrays.asList(
                "ANONYMOUS",
                "PLAIN",
                "DIGEST-MD5",
                "CRAM-MD5",
                ScramSha1SaslServer.MECHANISM_NAME,
                ScramSha1SaslServer.MECHANISM_NAME+"-PLUS",
                ScramSha256SaslServer.MECHANISM_NAME,
                ScramSha256SaslServer.MECHANISM_NAME+"-PLUS",
                ScramSha512SaslServer.MECHANISM_NAME,
                ScramSha512SaslServer.MECHANISM_NAME+"-PLUS",
                "JIVE-SHAREDSECRET",
                "GSSAPI",
                "EXTERNAL"
            )
        );
    }

    /**
     * Sets the collection of mechanism names that the system administrator allows to be used.
     *
     * @param mechanisms A collection of mechanisms that are considered for use in this instance of Openfire. Null to reset the default setting.
     * @see #getEnabledMechanisms()
     */
    public static void setEnabledMechanisms( List<String> mechanisms )
    {
        JiveGlobals.setProperty( "sasl.mechs", mechanisms );
        initMechanisms();
    }

    private static void initMechanisms()
    {
        final List<String> propertyValues = getEnabledMechanisms();
        mechanisms = new HashSet<>();
        for ( final String propertyValue : propertyValues )
        {
            try
            {
                addSupportedMechanism( propertyValue );
            }
            catch ( Exception ex )
            {
                Log.warn( "An exception occurred while trying to add support for SASL Mechanism '{}':", propertyValue, ex );
            }
        }
    }
}
