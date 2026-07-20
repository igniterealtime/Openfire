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

public class CsiModule extends BasicModule {
    static class Bind2CSIHandler implements Bind2InlineHandler {

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
    private static final Bind2CSIHandler handler = new Bind2CSIHandler();
    /**
     * <p>Create a basic module with the given name.</p>
     */
    public CsiModule() {
        super("Client State Indication");
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
        Bind2Request.registerElementHandler(handler);
    }

    @Override
    public void stop() {
        Bind2Request.unregisterElementHandler(handler.getNamespace());
        super.stop();
    }
}
