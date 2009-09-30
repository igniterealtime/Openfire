/**
 * $RCSfile$
 * $Revision$
 * $Date: 2005-07-26 19:10:33 +0200 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;

/**
 * Interface to listen for vCard changes. Use the
 * {@link org.jivesoftware.openfire.vcard.VCardEventDispatcher#addListener(VCardListener)}
 * method to register for events.
 *
 * @author Remko Tron&ccedil;on
 */
public interface VCardListener {
    /**
     * A vCard was created.
     *
     * @param username the username for which the vCard was created.
     * @param vCard the vcard created.
     */
    public void vCardCreated(String username, Element vCard);

    /**
     * A vCard was updated.
     *
     * @param username the user for which the vCard was updated.
     * @param vCard the vcard updated.
     */
    public void vCardUpdated(String username, Element vCard);

    /**
     * A vCard was deleted.
     *
     * @param username the user for which the vCard was deleted.
     * @param vCard the vcard deleted.
     */
    public void vCardDeleted(String username, Element vCard);
}
