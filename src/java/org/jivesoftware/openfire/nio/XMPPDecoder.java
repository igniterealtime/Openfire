/**
 * $Revision: $
 * $Date: $
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

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

/**
 * Decoder class that parses ByteBuffers and generates XML stanzas. Generated
 * stanzas are then passed to the next filters.
 *
 * @author Gaston Dombiak
 */
public class XMPPDecoder extends CumulativeProtocolDecoder {

    @Override
	protected boolean doDecode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out)
            throws Exception {
        // Get the XML light parser from the IoSession
        XMLLightweightParser parser = (XMLLightweightParser) session.getAttribute(ConnectionHandler.XML_PARSER);
        // Parse as many stanzas as possible from the received data
        parser.read(in);

        if (parser.areThereMsgs()) {
            for (String stanza : parser.getMsgs()) {
                out.write(stanza);
            }
        }
        return !in.hasRemaining();
    }
}
