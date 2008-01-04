/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
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
