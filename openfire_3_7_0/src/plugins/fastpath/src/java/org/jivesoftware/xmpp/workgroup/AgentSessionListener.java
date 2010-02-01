/**
 * $RCSfile$
 * $Revision: 18406 $
 * $Date: 2005-02-07 14:32:46 -0800 (Mon, 07 Feb 2005) $
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

package org.jivesoftware.xmpp.workgroup;

/**
 * <p>Implement to listen for changes in the contents of an agent session list.</p>
 * <p>Many entities will need to monitor agent session lists for membership changes.
 * Implementing this interface allows an object to register for callbacks
 * when agent lists change.</p>
 *
 * @author Derek DeMoro
 */
public interface AgentSessionListener {
    /**
     * <p>Called after the given agent session is added to the list.</p>
     *
     * @param session The session that was added
     */
    void notifySessionAdded(AgentSession session);

    /**
     * <p>Called after the given agent session is removed from the list.</p>
     *
     * @param session The session that was removed
     */
    void notifySessionRemoved(AgentSession session);
}
