/**
 * $Revision$
 * $Date$
 *
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

package org.jivesoftware.openfire.mediaproxy;

import java.net.DatagramPacket;

/**
 * Listener for datagram packets received.
 *
 * @author Thiago Camargo
 */
public interface DatagramListener {

    /**
     * Called when a datagram is received. If the method returns false, the
     * packet MUST NOT be resent from the received Channel.
     *
     * @param datagramPacket the datagram packet received.
     * @return ?
     */
    public boolean datagramReceived(DatagramPacket datagramPacket);
}