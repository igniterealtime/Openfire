/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
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

package org.jivesoftware.openfire.privacy;

/**
 * /**
 * Interface to listen for privacy list events. Use the
 * {@link PrivacyListManager#addListener(PrivacyListEventListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface PrivacyListEventListener {

    /**
     * A privacy list was created.
     *
     * @param list the privacy list.
     */
    public void privacyListCreated(PrivacyList list);

    /**
     * A privacy list is being deleted.
     *
     * @param listName name of the the privacy list that has been deleted.
     */
    public void privacyListDeleting(String listName);

    /**
     * Properties of the privacy list were changed.
     *
     * @param list the privacy list.
     */
    public void privacyListModified(PrivacyList list);
}
