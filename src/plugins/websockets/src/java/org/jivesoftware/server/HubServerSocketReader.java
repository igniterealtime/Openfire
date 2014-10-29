/**
 * $RCSfile$
 * $Revision: 1530 $
 * $Date: 2005-06-17 18:38:27 -0300 (Fri, 17 Jun 2005) $
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

package org.jivesoftware.openfire.server;

import org.dom4j.io.XMPPPacketReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HubServerSocketReader extends OutgoingServerSocketReader {

	private static final Logger Log = LoggerFactory.getLogger(OutgoingServerSocketReader.class);

    public HubServerSocketReader() {
		super(new XMPPPacketReader());
    }

}
