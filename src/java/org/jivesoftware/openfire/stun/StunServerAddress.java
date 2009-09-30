/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
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
package org.jivesoftware.openfire.stun;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Provides easy abstract to store STUN Server Addresses and Ports
 */
public class StunServerAddress {
    private String server;
    private String port;

    public StunServerAddress(String server, String port) {
        this.server = server;
        this.port = port;
    }

    /**
     * Get the Host Address
     *
     * @return Host Address
     */
    public String getServer() {
        return server;
    }

    /**
     * Get STUN port
     *
     * @return the Server Port
     */
    public String getPort() {
        return port;
    }

    public boolean equals(Object obj) {

        if (this == obj) return true;

        if (obj instanceof StunServerAddress) {

            StunServerAddress other = (StunServerAddress) obj;

            if (this.getPort().equals(other.getPort())) {

                if (this.getServer().equals(other.getServer())) {
                    return true;
                }

                try {
                    InetAddress addr0 = InetAddress.getByName(this.getServer());
                    InetAddress addr1 = InetAddress.getByName(other.getServer());

                    return addr0.getHostAddress().equals(addr1.getHostAddress());

                } catch (UnknownHostException e) {
                    return false;
                }

            }

        }
        return false;
    }
}