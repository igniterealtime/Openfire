/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin;

import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.Packet;

/**
 * Packet interceptor that prints to the stdout XML packets (i.e. XML after
 * it was parsed).<p>
 * <p>
 * If you find in the logs an entry for raw XML, an entry that a session was closed and
 * never find the corresponding interpreted XML for the raw XML then there was an error
 * while parsing the XML that closed the session.
 *
 * @author Gaston Dombiak.
 */
public class InterpretedXMLPrinter implements PacketInterceptor {

    private static final Logger LOGGER = LogManager.getLogger();
    static final String PROPERTY_ENABLED = DebuggerPlugin.PROPERTY_PREFIX + "interpretedAllowed";
    private DebuggerPlugin plugin;

    InterpretedXMLPrinter(final DebuggerPlugin plugin) {

        this.plugin = plugin;
    }

    @Override
    public void interceptPacket(final Packet packet, final Session session, final boolean incoming, final boolean processed) {
        if (session != null && !processed) {
            String hostAddress;
            try {
                hostAddress = "/" + session.getHostAddress() + ":?????";
            } catch (final UnknownHostException ignored) {
                hostAddress = "";
            }
            // Pad this out so it aligns with the RawPrintFilter output
            plugin.log(String.format("INT %-16s - %s - (%11s): %s", hostAddress, incoming ? "RECV" : "SENT", session.getStreamID(), packet.toXML()));
        }
    }

    public boolean isEnabled() {
        return JiveGlobals.getBooleanProperty(PROPERTY_ENABLED);
    }

    public void setEnabled(final boolean enabled) {
        JiveGlobals.setProperty(PROPERTY_ENABLED, Boolean.toString(enabled));
    }

    void wasEnabled(final boolean enabled) {
        if (enabled) {
            LOGGER.debug("Interpreted XML logger enabled");
            InterceptorManager.getInstance().addInterceptor(this);
        } else {
            LOGGER.debug("Interpreted XML logger disabled");
            InterceptorManager.getInstance().removeInterceptor(this);
        }
    }

}
