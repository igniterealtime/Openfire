/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.StreamError;

import javax.annotation.Nullable;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract implementation of the Connection interface that models abstract connections. Abstract
 * connections are connections that don't have a physical connection counterpart. Instead they
 * can be seen as conceptual or just 'abstract' connections.<p>
 *
 * Default values and common behavior of virtual connections are modeled in this class. Subclasses
 * should just need to specify how packets are delivered and what means closing the connection.
 *
 * @author Gaston Dombiak
 */
public abstract class VirtualConnection extends AbstractConnection
{
    private static final Logger Log = LoggerFactory.getLogger(VirtualConnection.class);

    private final AtomicReference<State> state = new AtomicReference<State>(State.OPEN);

    @Override
    public Certificate[] getLocalCertificates() {
        // Ignore
        return new Certificate[0];
    }

    @Override
    public Certificate[] getPeerCertificates() {
        // Ignore
        return new Certificate[0];
    }

    @Override
    public void setUsingSelfSignedCertificate(boolean isSelfSigned) {
    }

    @Override
    public boolean isUsingSelfSignedCertificate() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    @Override
    public boolean isCompressed() {
        // Return false since compression is not used for virtual connections
        return false;
    }

    @Override
    @Nullable
    public PacketDeliverer getPacketDeliverer() {
        //Ignore
        return null;
    }

    public void startTLS(boolean clientMode, boolean directTLS) throws Exception {
        //Ignore
    }

    public void addCompression() {
        //Ignore
    }

    @Override
    public void startCompression() {
        //Ignore
    }

    @Override
    @Deprecated // Remove in Openfire 4.9 or later.
    public boolean isSecure() {
        return isEncrypted();
    }

    @Override
    public boolean isEncrypted() {
        // Return false since TLS is not used for virtual connections
        return false;
    }

    @Override
    public boolean validate() {
        // Return true since the virtual connection is valid until it no longer exists
        return true;
    }

    @Override
    public boolean isInitialized() {
        return session != null && !isClosed();
    }

    /**
     * Closes the session, the virtual connection and notifies listeners that the connection
     * has been closed.
     *
     * @param error If non-null, the end-stream tag will be preceded with this error.
     */
    @Override
    public void close(@Nullable final StreamError error, final boolean networkInterruption) {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {
            
            if (session != null) {
                if (!networkInterruption) {
                    // A 'clean' closure should never be resumed (see #onRemoteDisconnect for handling of unclean disconnects). OF-2752
                    session.getStreamManager().formalClose();
                }
                session.setStatus(Session.Status.CLOSED);
            }

            // See OF-1596
            // The notification will trigger some shutdown procedures that, amongst other things,
            // check what type of session (eg: anonymous) is being closed. This check depends on the
            // session still being available.
            //
            // For that reason, it's important to first notify the listeners, and then close the
            // session - not the other way around.
            //
            // This fixes a very visible bug where MUC users would remain in the MUC room long after
            // their session was closed. Effectively, the bug prevents the MUC room from getting a
            // presence update to notify it that the user logged off.
            notifyCloseListeners();
            closeListeners.clear();

            try {
                closeVirtualConnection(error);
            } catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error.close") + "\n" + toString(), e);
            }
        }
    }

    /**
     * Closes the virtual connection. Subclasses should indicate what closing a virtual
     * connection means. At this point the session has a CLOSED state.
     *
     * @param error If non-null, this error will be sent to the peer before the connection is disconnected.
     */
    public abstract void closeVirtualConnection(@Nullable final StreamError error);
}
