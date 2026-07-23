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

package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.openfire.net.Bind2InlineHandler;
import org.jivesoftware.openfire.session.LocalClientSession;

public class Bind2CarbonsHandler implements Bind2InlineHandler {
    @Override
    public String getNamespace() {
        return "urn:xmpp:carbons:2";
    }

    @Override
    public boolean handleElement(LocalClientSession session, Element bound, Element element) {
        session.setMessageCarbonsEnabled(element.getName().equals("enable"));
        return true;
    }
}
