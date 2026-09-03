/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.csi;

import org.dom4j.Element;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.net.Bind2InlineHandler;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.session.LocalClientSession;


/**
 * The CsiModule provides functionality for managing Client State Indication (CSI) within an XMPP server.
 *
 * This module interacts with incoming bind2 requests and processes inline elements related to client state activation
 * and deactivation. It registers a handler for handling CSI-specific operations during the start of the module and
 * unregisters it during the module's stop operation.
 */
public class CsiModule extends BasicModule
{
    private Bind2CSIHandler bind2CSIHandler;

    /**
     * Create a basic module with the given name.
     */
    public CsiModule() {
        super("Client State Indication");
    }

    @Override
    public synchronized void start() throws IllegalStateException {
        super.start();
        final Bind2CSIHandler localHandler = new Bind2CSIHandler();
        Bind2Request.registerElementHandler(localHandler);
        bind2CSIHandler = localHandler; // Only dereference any previous handler after registration succeeds, otherwise that previous handler can never be removed again.
    }

    @Override
    public synchronized void stop() {
        super.stop();
        if (bind2CSIHandler != null) {
            Bind2Request.unregisterElementHandler(bind2CSIHandler);
            bind2CSIHandler = null;
        }
    }

    /**
     * Handles inline elements related to Client State Indication (CSI) during bind2 requests.
     *
     * Implements the {@link Bind2InlineHandler} interface to integrate with the bind2 inline element handling
     * mechanism. Acts as a bridge between the bind2 processing flow and the specific functionalities of the
     * Client State Indication module.
     */
    static class Bind2CSIHandler implements Bind2InlineHandler
    {
        @Override
        public String getNamespace() {
            return CsiManager.NAMESPACE;
        }

        @Override
        public boolean handleElement(LocalClientSession session, Element bound, Element element) {
            if (element.getName().equals("active")) {
                session.getCsiManager().activate();
            } else if (element.getName().equals("inactive")) {
                session.getCsiManager().deactivate();
            }
            return true;
        }
    }
}
