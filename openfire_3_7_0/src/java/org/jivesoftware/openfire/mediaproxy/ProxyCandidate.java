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

import java.net.InetAddress;

/**
 * Basic interface to access a Candidate provided by a Session
 *
 * @author Thiago Camargo
 */
public interface ProxyCandidate {

    public String getSID();

    public String getPass();

    public InetAddress getLocalhost();

    public InetAddress getHostA();

    public InetAddress getHostB();

    public void setHostA(InetAddress hostA);

    public void setHostB(InetAddress hostB);

    public void sendFromPortA(String host, int port);

    public void sendFromPortB(String host, int port);

    public int getPortA();

    public int getPortB();

    public void setPortA(int portA);

    public void setPortB(int portB);

    public int getLocalPortA();

    public int getLocalPortB();

    public void start();

    public void stopAgent();
}
