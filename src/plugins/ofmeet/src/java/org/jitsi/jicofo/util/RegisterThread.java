/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.util;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

/**
 * Thread does the job of registering given <tt>ProtocolProviderService</tt>.
 *
 * @author Pawel Domas
 */
public class RegisterThread
    extends Thread
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(RegisterThread.class);

    private final ProtocolProviderService pps;

    public RegisterThread(ProtocolProviderService pps)
    {
        this.pps = pps;
    }

    @Override
    public void run()
    {
        try
        {
            pps.register(new ServerSecurityAuthority());
        }
        catch (OperationFailedException e)
        {
            logger.error(e, e);
        }
    }
}
