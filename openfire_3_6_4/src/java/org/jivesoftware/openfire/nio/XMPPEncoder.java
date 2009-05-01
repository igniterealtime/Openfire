/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * Encoder that does nothing. We are already writing ByteBuffers so there is no need
 * to encode them.<p>
 *
 * This class exists as a counterpart of {@link XMPPDecoder}. Unlike that class this class does nothing.
 *
 * @author Gaston Dombiak
 */
public class XMPPEncoder extends ProtocolEncoderAdapter {

    public void encode(IoSession session, Object message, ProtocolEncoderOutput out)
            throws Exception {
        // Ignore. Do nothing. Content being sent is already a bytebuffer (of strings) 
    }
}
