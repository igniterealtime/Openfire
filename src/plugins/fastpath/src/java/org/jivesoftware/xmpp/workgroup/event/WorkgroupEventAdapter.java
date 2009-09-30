/**
 * $RCSfile$
 * $Revision: 19238 $
 * $Date: 2005-07-07 09:53:37 -0700 (Thu, 07 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup.event;

import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;

/**
 * An abstract adapter class for receiving workgroup events. The methods in this class
 * are empty -- in other words, this class exists as a convenience for creating listener
 * objects.<p>
 *
 * Workgroup Events let you track when a workgroup is destroyed, created, modified, or when a
 * support chat has been created and ended.<p>
 *
 * Extend this class to create a  listener and override the methods for the events of interest.
 * Create a listener object using the extended class and then register it with
 * a WorkgroupEventDispatcher using the
 * {@link WorkgroupEventDispatcher#addListener(WorkgroupEventListener) addListener} method.
 *
 * @author Derek DeMoro
 * @see WorkgroupEventListener
 * @see WorkgroupEventDispatcher
 */
public abstract class WorkgroupEventAdapter implements WorkgroupEventListener {

    public void workgroupCreated(Workgroup workgroup) {

    }

    public void workgroupDeleting(Workgroup workgroup) {

    }

    public void workgroupDeleted(Workgroup workgroup) {

    }

    public void workgroupOpened(Workgroup workgroup) {

    }

    public void workgroupClosed(Workgroup workgroup) {

    }

    public void agentJoined(Workgroup workgroup, AgentSession agentSession) {

    }

    public void agentDeparted(Workgroup workgroup, AgentSession agentSession) {

    }

    public void chatSupportStarted(Workgroup workgroup, String sessionID) {

    }

    public void chatSupportFinished(Workgroup workgroup, String sessionID) {

    }

    public void agentJoinedChatSupport(Workgroup workgroup, String sessionID, AgentSession agentSession) {

    }

    public void agentLeftChatSupport(Workgroup workgroup, String sessionID, AgentSession agentSession) {

    }
}
