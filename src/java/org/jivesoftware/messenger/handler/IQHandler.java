/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.ChannelHandler;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.IQRouter;
import org.jivesoftware.messenger.PacketDeliverer;
import org.jivesoftware.messenger.PacketException;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * <p>Base class whose main responsibility is to handle IQ packets. Subclasses may only need to
 * specify the IQHandlerInfo (i.e. name and namespace of the packets to handle) and actually handle
 * the IQ packet.</p>
 * <p/>
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

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        IQ iq = (IQ) packet;
        try {
            iq = handleIQ(iq);
            if (iq != null) {
                deliverer.deliver(iq);
            }
        }
        catch (org.jivesoftware.messenger.auth.UnauthorizedException e) {
            if (iq != null) {
                try {
                    IQ response = IQ.createResultIQ(iq);
                    response.setError(PacketError.Condition.not_authorized);
                    Session session = SessionManager.getInstance().getSession(iq.getFrom());
                    if (!session.getConnection().isClosed()) {
                        session.getConnection().deliver(response);
                    }
                }
                catch (Exception de) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), de);
                    SessionManager.getInstance().getSession(iq.getFrom()).getConnection().close();
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
     *                               the given operation.
     * @throws org.xmlpull.v1.XmlPullParserException
     *                               If there was trouble reading the stream.
     */
    public abstract IQ handleIQ(IQ packet) throws UnauthorizedException, XmlPullParserException;

    /**
     * <p>Obtain the handler information to help generically handle IQ packets.</p>
     * <p/>
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