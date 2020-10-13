/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.muc.spi;

/**
 * Configuration mode for Federated Multi-User Chat.
 *
 * @author Guus der kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0289.html>XEP-0289: Federated MUC for Constrained Environments</a>
 */
public enum FMUCMode
{
    /**
     * Two FMUC nodes operating such that both will continue to work when a network fails between them. This mode has
     * the properties of reduced network traffic and of not having a guarantee of consistent message ordering between
     * nodes.
     */
    MasterMaster,

    /**
     * Two FMUC nodes operating where one, the master, will continue working during a network outage while the slave
     * will cease to work while it cannot communicate with the master. This mode has increased network traffic and a
     * consistent message delivery order across both nodes.
     */
    MasterSlave
}
