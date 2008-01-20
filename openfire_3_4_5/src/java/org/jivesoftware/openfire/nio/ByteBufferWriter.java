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

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.CharsetEncoder;

/**
 * Wrapper on a MINA {@link ByteBuffer} that extends the Writer class.
 *
 * @author Gaston Dombia
 */
public class ByteBufferWriter extends Writer {
    private CharsetEncoder encoder;
    private ByteBuffer byteBuffer;


    public ByteBufferWriter(ByteBuffer byteBuffer, CharsetEncoder encoder) {
        this.encoder = encoder;
        this.byteBuffer = byteBuffer;
    }

    public void write(char cbuf[], int off, int len) throws IOException {
        byteBuffer.putString(new String(cbuf, off, len), encoder);
    }

    public void flush() throws IOException {
        // Ignore
    }

    public void close() throws IOException {
        // Ignore
    }
}
