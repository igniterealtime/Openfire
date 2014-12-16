/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.caps.*;
import net.java.sip.communicator.util.*;

import org.jitsi.service.configuration.*;

import org.jitsi.videobridge.log.*;
import org.jitsi.videobridge.osgi.*;
import org.osgi.framework.*;

import java.util.concurrent.*;

/**
 * Activator of the Jitsi Meet Focus bundle.
 *
 * @author Pawel Domas
 */
public class FocusBundleActivator
    implements BundleActivator
{
    /**
     * The number of threads available in the thread pool shared through OSGi.
     */
    private static final int SHARED_POOL_SIZE = 20;

    /**
     * OSGi bundle context held by this activator.
     */
    public static BundleContext bundleContext;

    /**
     * {@link ConfigurationService} instance cached by the activator.
     */
    private static ConfigurationService configService;

    /**
     * {@link org.jitsi.jicofo.FocusManager} instance created by this activator.
     */
    private FocusManager focusManager;

    /**
     * Shared thread pool available through OSGi for other components that do
     * not like to manage their own pool.
     */
    private static ExecutorService sharedThreadPool;

    @Override
    public void start(BundleContext context)
        throws Exception
    {
        bundleContext = context;

        EntityCapsManager.setBundleContext(context);

        sharedThreadPool = Executors.newFixedThreadPool(SHARED_POOL_SIZE);

        context.registerService(ExecutorService.class, sharedThreadPool, null);

        this.focusManager = new FocusManager();

        context.registerService(FocusManager.class, focusManager, null);
    }

    @Override
    public void stop(BundleContext context)
        throws Exception
    {
        sharedThreadPool.shutdownNow();
        sharedThreadPool = null;

        configService = null;

        EntityCapsManager.setBundleContext(null);
    }

    /**
     * Returns the instance of <tt>ConfigurationService</tt>.
     */
    public static ConfigurationService getConfigService()
    {
        if (configService == null)
        {
            configService = ServiceUtils.getService(
                bundleContext, ConfigurationService.class);
        }
        return configService;
    }

    /**
     * Returns the <tt>LoggingService</tt> instance, if any.
     * @return the <tt>LoggingService</tt> instance, if any.
     */
    public static LoggingService getLoggingService()
    {
        if (bundleContext != null)
        {
            return ServiceUtils2.getService(bundleContext,
                                            LoggingService.class);
        }
        return null;
    }

    /**
     * Returns shared thread pool service.
     */
    public static ExecutorService getSharedThreadPool()
    {
        return sharedThreadPool;
    }

}
