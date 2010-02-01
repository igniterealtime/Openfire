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
package org.jivesoftware.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

/**
 * Callable which will read from an input stream and write to an output stream.
 *
 * @author Alexander Wenckus
 */
public class InputOutputStreamWrapper implements Callable {
    private static final int DEFAULT_BUFFER_SIZE = 8000;

    private long amountWritten = 0;
    private int bufferSize;
    private InputStream in;
    private OutputStream out;

    public InputOutputStreamWrapper(InputStream in, OutputStream out, int bufferSize) {
        if(bufferSize <= 0) {
            bufferSize = DEFAULT_BUFFER_SIZE;
        }

        this.bufferSize = bufferSize;
        this.in = in;
        this.out = out;
    }

    public InputOutputStreamWrapper(InputStream in, OutputStream out) {
        this(in, out, DEFAULT_BUFFER_SIZE);
    }

    public Object call() throws Exception {
        final byte[] b = new byte[bufferSize];
        int count = 0;
        amountWritten = 0;

        do {
            // write to the output stream
            out.write(b, 0, count);

            amountWritten += count;

            // read more bytes from the input stream
            count = in.read(b);
        } while (count >= 0);

        return amountWritten;
    }

    public long getAmountWritten() {
        return amountWritten;
    }
}
