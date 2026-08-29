/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class Sasl2InlineFeaturesTest
{
    @Test
    void advertisesStreamManagementResumption()
    {
        final Element authentication = SASLAuthentication.asSASLMechanismsElementForClientSessions(Set.of("PLAIN"), true);

        assertNotNull(authentication);
        final Element inline = authentication.element("inline");
        assertNotNull(inline);
        assertNotNull(inline.element(QName.get("sm", StreamManager.NAMESPACE_V3)),
            "SASL2 inline features must advertise XEP-0198 resumption.");
    }
}
