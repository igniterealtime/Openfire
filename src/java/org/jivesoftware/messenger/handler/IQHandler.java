/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import javax.xml.stream.XMLStreamException;

/**
 * <p>Base class whose main responsibility is to handle IQ packets. Subclasses may only need to
 * specify the IQHandlerInfo (i.e. name and namespace of the packets to handle) and actually handle
 * the IQ packet.</p>
 *
 * Simplifies creation of simple TYPE_IQ message handlers.
 *
 * @author Gaston Dombiak
 */
public abstract class IQHandler extends BasicModule implements ChannelHandler {

    public PacketDeliverer deliverer;

    protected IQRouter router;

    /**
     * Create a basic module with the given name.
     *
     * @param moduleName The name for the module or null to use the default
     */
    public IQHandler(String moduleName) {
        super(moduleName);
    }

    public void setRouter(IQRouter router) {
        this.router = router;
    }

    public void process(XMPPPacket xmppPacket) throws UnauthorizedException, PacketException {
        IQ iq = (IQ)xmppPacket;
        try {
            iq = handleIQ(iq);
            if (iq != null) {
                deliverer.deliver(iq);
            }
        }
        catch (org.jivesoftware.messenger.auth.UnauthorizedException e) {
            if (iq != null) {
                try {
                    XMPPPacket response = iq.createResult();
                    response.setError(XMPPError.Code.UNAUTHORIZED);
                    Session session = iq.getOriginatingSession();
                    if (!session.getConnection().isClosed()) {
                        session.getConnection().deliver(response);
                    }
                }
                catch (Exception de) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), de);
                    try {
                        iq.getOriginatingSession().getConnection().close();
                    }
                    catch (UnauthorizedException e1) {
                        // do nothing
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * Handles the received IQ packet.
     *
     * @param packet the IQ packet to handle.
     * @return the response to send back.
     * @throws UnauthorizedException If the user that sent the packet is not authorized to request
     * the given operation.
     * @throws XMLStreamException If there was trouble reading the stream.
     */
    public abstract IQ handleIQ(IQ packet) throws UnauthorizedException, XMLStreamException;

    /**
     * <p>Obtain the handler information to help generically handle IQ packets.</p>
     *
     * <p>IQHandlers that aren't local server iq handlers (e.g. chatbots, transports, etc)
     * return a null.</p>
     *
     * @return The IQHandlerInfo for this handler
     */
    public abstract IQHandlerInfo getInfo();

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        return trackInfo;
    }
}