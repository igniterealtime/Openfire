/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.osgi;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.impl.neomedia.transform.srtp.*;
import org.jitsi.impl.osgi.framework.*;
import org.jitsi.service.configuration.*;

import org.osgi.framework.*;

import java.util.*;

/**
 * Represents the entry point of the OSGi environment of the Jitsi Meet
 * conference focus application.
 *
 * @author Pawel Domas
 */
public class OSGi
{
    /**
     * Indicates whether 'mock' protocol providers should be used instead of
     * original Jitsi protocol providers. For the purpose of unit testing.
     */
    private static boolean useMockProtocols = false;

    /**
     * The <tt>OSGiLauncher</tt> instance which
     * represents the launched OSGi instance.
     */
    private static OSGiLauncher launcher;

    /**
     * <tt>BundleActivator</tt> bundle activator launched on startup/shutdown of
     * the OSGi system.
     */
    private static BundleActivator activator;

    /**
     * The locations of the OSGi bundles (or rather of the class files of their
     * <tt>BundleActivator</tt> implementations) comprising Jitsi Meet Focus.
     * An element of the <tt>BUNDLES</tt> array is an array of <tt>String</tt>s
     * and represents an OSGi start level.
     */
    private static String[][] getBUNDLES()
    {

        String[] protocols =
            {
                "org/jitsi/impl/protocol/xmpp/XmppProtocolActivator"
            };

        String[] mockProtocols =
            {
                "mock/MockActivator"
            };

        String[][] bundles = {
            {
                "net/java/sip/communicator/impl/libjitsi/LibJitsiActivator"
            },
            {
                "net/java/sip/communicator/util/UtilActivator",
                //"net/java/sip/communicator/impl/fileaccess/FileAccessActivator"
            },
            {
                "net/java/sip/communicator/impl/configuration/ConfigurationActivator"
            },
            {
                //"net/java/sip/communicator/impl/resources/ResourceManagementActivator"
            },
            {
                //"net/java/sip/communicator/impl/dns/DnsUtilActivator"
            },
            {
                "net/java/sip/communicator/impl/credentialsstorage/CredentialsStorageActivator"
            },
            {
                "net/java/sip/communicator/impl/netaddr/NetaddrActivator"
            },
            {
                //"net/java/sip/communicator/impl/packetlogging/PacketLoggingActivator"
            },
            {
                //"net/java/sip/communicator/service/gui/internal/GuiServiceActivator"
            },
            {
                "net/java/sip/communicator/service/protocol/media/ProtocolMediaActivator"
            },
            {
                //"net/java/sip/communicator/service/notification/NotificationServiceActivator",
                //"net/java/sip/communicator/impl/globaldisplaydetails/GlobalDisplayDetailsActivator"
            },
            useMockProtocols
                ? new String[]
                    {
                        "mock/media/MockMediaActivator"
                    }
                : new String[]
                    {
                        //"net/java/sip/communicator/impl/neomedia/NeomediaActivator"
                    },
            {
                //"net/java/sip/communicator/impl/certificate/CertificateVerificationActivator"
            },
            {
                //"net/java/sip/communicator/impl/version/VersionActivator"
            },
            {
                "net/java/sip/communicator/service/protocol/ProtocolProviderActivator"
            },
            {
                "net/java/sip/communicator/plugin/reconnectplugin/ReconnectPluginActivator"
            },
            // Shall we use mock protocol providers ?
            useMockProtocols ? mockProtocols : protocols,
            {
                "org/jitsi/jicofo/FocusBundleActivator"
            },
            {
                "org/jitsi/videobridge/log/LoggingBundleActivator"
            },
            useMockProtocols
                ? new String[] { "mock/MockMainMethodActivator" }
                : new String[] { }
        };

        return bundles;
    }

    static
    {
        /*
         * Before we start OSGi and, more specifically, the very Jitsi
         * Videobridge application, set the default values of the System
         * properties which affect the (optional) behavior of the application.
         */
        setSystemPropertyDefaults();
    }

    /**
     * Sets default values on <tt>System</tt> properties which affect the
     * (optional) behavior of the Jitsi Videobridge application and the
     * libraries that it utilizes. Because <tt>ConfigurationServiceImpl</tt>
     * will override <tt>System</tt> property values, the set default
     * <tt>System</tt> property values will not prevent the user from overriding
     * them. 
     */
    private static void setSystemPropertyDefaults()
    {
        /*
         * XXX A default System property value specified bellow will eventually
         * be set only if the System property in question does not have a value
         * set yet.
         */

        Map<String,String> defaults = new HashMap<String,String>();
        String true_ = Boolean.toString(true);
        String false_ = Boolean.toString(false);

        /*
         * The design at the time of this writing considers the configuration
         * file read-only (in a read-only directory) and provides only manual
         * editing for it.
         */
        defaults.put(
                ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
                true_);

        defaults.put(
                DeviceConfiguration.PROP_AUDIO_SYSTEM,
                AudioSystem.LOCATOR_PROTOCOL_AUDIOSILENCE);

        defaults.put(
                MediaServiceImpl.DISABLE_VIDEO_SUPPORT_PNAME,
                true_);

        // It makes no sense for Jitsi Videobridge to pace its RTP output.
        defaults.put(
                DeviceConfiguration.PROP_VIDEO_RTP_PACING_THRESHOLD,
                Integer.toString(Integer.MAX_VALUE));

        /*
         * XXX Explicitly support JitMeet by default because is is the primary
         * use case of Jitsi Videobridge right now.
         */
        defaults.put(
                SsrcTransformEngine
                    .DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
                true_);
        defaults.put(SRTPCryptoContext.CHECK_REPLAY_PNAME, false_);

        for (Map.Entry<String,String> e : defaults.entrySet())
        {
            String key = e.getKey();

            if (System.getProperty(key) == null)
                System.setProperty(key, e.getValue());
        }
    }

    /**
     * Indicates whether mock protocol providers should be used instead of
     * original Jitsi protocol providers.
     */
    public static boolean isUseMockProtocols()
    {
        return useMockProtocols;
    }

    /**
     * Make OSGi use mock protocol providers instead of original Jitsi protocols
     * implementation.
     * @param useMockProtocols <tt>true</tt> if Jitsi protocol providers should
     *                         be replaced with mock version.
     */
    public static void setUseMockProtocols(boolean useMockProtocols)
    {
        OSGi.useMockProtocols = useMockProtocols;
    }

    /**
     * Starts the OSGi infrastructure.
     *
     * @param activator the <tt>BundleActivator</tt> that will be launched after
     *                  OSGi starts.
     */
    public static synchronized void start(BundleActivator activator)
    {
        if (OSGi.activator != null)
            throw new IllegalStateException("activator");

        OSGi.activator = activator;

        if (launcher == null)
        {
            launcher = new OSGiLauncher(getBUNDLES());
        }

        launcher.start(activator);
    }

    /**
     * Stops the Jitsi Meet Focus bundles and the OSGi implementation.
     *
     * The <tt>BundleActivator</tt> that has been passed to
     * {@link #start(BundleActivator)} will be launched after shutdown.
     */
    public static synchronized void stop()
    {
        if (launcher != null && activator != null)
        {
            launcher.stop(activator);

            activator = null;
        }
    }
}
