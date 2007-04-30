/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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

    protected boolean doDecode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out)
            throws Exception {
        if (in.remaining() < 4) {
            return false;
        }
        // Get the XML light parser from the IoSession
        XMLLightweightParser parser =
                (XMLLightweightParser) session.getAttribute(ConnectionHandler.XML_PARSER);
        // Parse as many stanzas as possible from the received data
        parser.read(in);

        if (parser.areThereMsgs()) {
            for (String stanza : parser.getMsgs()) {
                out.write(stanza);
            }
        }
        return true;
    }
}
