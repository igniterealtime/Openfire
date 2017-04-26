/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire;

/**
 * Interface that let observers be notified when the server has been started or is
 * about to be stopped. Use {@link XMPPServer#addServerListener(XMPPServerListener)} to
 * add new listeners.
 *
 * @author Gaston Dombiak
 */
public interface XMPPServerListener {

    /**
     * Notification message indicating that the server has been started. At this point
     * all server modules have been initialized and started. Message sending and receiving
     * is now possible. However, some plugins may still be pending to be loaded.
     */
    void serverStarted();

    /**
     * Notification message indication that the server is about to be stopped. At this point
     * all modules are still running so all services are still available.
     */
    void serverStopping();
}
