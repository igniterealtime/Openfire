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

package org.jivesoftware.messenger.transport;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.stream.XMLStreamException;

/**
 * Routes packets to the appropriate transport gateway or drops the packet.
 *
 * @author Iain Shigeoka
 */
public class TransportHandler extends BasicModule implements ChannelHandler {

    private Map<String, Channel> transports = new ConcurrentHashMap<String, Channel>();

    public TransportHandler() {
        super("Transport handler");
    }

    public void addTransport(Channel transport) {
        transports.put(transport.getName(), transport);
    }

    public void process(XMPPPacket packet) throws UnauthorizedException, PacketException {
        boolean handled = false;
        String host = packet.getRecipient().getHost();
        for (Channel channel : transports.values()) {
            if (channel.getName().equalsIgnoreCase(host)) {
                channel.add(packet);
                handled = true;
            }
        }
        if (!handled) {
            XMPPAddress recipient = packet.getRecipient();
            XMPPAddress sender = packet.getSender();
            packet.setError(XMPPError.Code.REMOTE_SERVER_TIMEOUT);
            packet.setSender(recipient);
            packet.setRecipient(sender);
            try {
                deliverer.deliver(packet);
            }
            catch (UnauthorizedException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            catch (XMLStreamException xse) {
                Log.error(xse);
            }
        }
    }

    public PacketDeliverer deliverer;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        return trackInfo;
    }
}