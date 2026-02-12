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

package org.jivesoftware.openfire.disco;

import org.xmpp.forms.DataForm;
import org.xmpp.packet.JID;

import java.util.Set;

/**
 * Provides extended service discovery information using XEP-0128 data form extensions.
 * <p>
 * Implementations of this interface can contribute additional XEP-0004 data forms to disco#info responses,
 * enabling plugin-controlled additions to service discovery as defined by XEP-0128.
 * This supports use cases such as:
 * <ul>
 *   <li>Adding custom fields to existing forms (e.g. what the MUC Extended Info plugin does)</li>
 *   <li>Providing entirely new data forms with custom FORM_TYPE values (e.g. XEP-0232: Software Information, XEP-0504: Data Policy)</li>
 * </ul>
 * </p>
 *
 * When multiple providers return forms with the same FORM_TYPE, the forms are merged by combining
 * their fields. However, <b>each field must be unique</b> within a form type. If two providers contribute
 * fields with the same field name to the same FORM_TYPE, this is treated as a configuration error. The duplicate
 * provider's entire contribution is skipped and a warning is logged. Other providers continue to be processed
 * normally.
 *
 * Implementations should use 'JiveGlobals.getBooleanProperty( "admin.disable-exposure" )' and consider appropriate
 * adjustments where needed.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0004.html">XEP-0004: Data Forms</a>
 * @see <a href="https://xmpp.org/extensions/xep-0128.html">XEP-0128: Service Discovery Extensions</a>
 * @see <a href="https://xmpp.org/extensions/xep-0504.html">XEP-0504: Data Policy</a>
 */
public interface ExtendedDiscoInfoProvider {

    /**
     * Returns a collection of data forms with extended information about the entity.
     * <p>
     * The returned Set may be empty but should not be null. It may be immutable.
     * Forms should include a FORM_TYPE field to enable proper merging.
     * </p>
     *
     * @param name the recipient's JID.
     * @param node the requested disco node.
     * @param senderJID the XMPPAddress of user that sent the disco info request.
     * @return A Set of data forms (possibly empty, never null). May be immutable.
     */
    Set<DataForm> getExtendedInfos(String name, String node, JID senderJID );

}
