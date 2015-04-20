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

import org.apache.mina.core.buffer.IoBuffer;

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
    private IoBuffer ioBuffer;


    public ByteBufferWriter(IoBuffer byteBuffer, CharsetEncoder encoder) {
        this.encoder = encoder;
        this.ioBuffer = byteBuffer;
    }

    @Override
	public void write(char cbuf[], int off, int len) throws IOException {
        ioBuffer.putString(new String(cbuf, off, len), encoder);
    }

    @Override
	public void flush() throws IOException {
        // Ignore
    }

    @Override
	public void close() throws IOException {
        // Ignore
    }
}
