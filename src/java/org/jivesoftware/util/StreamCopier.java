/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Fast way to copy data from one stream to another.
 * From ORA Java I/O by Elliotte Rusty Harold.
 *
 * @author Iain Shigeoka
 */
public class StreamCopier {
    /**
     * Copies data from an input stream to an output stream
     *
     * @param in  The stream to copy data from
     * @param out The stream to copy data to
     * @throws IOException if there's trouble during the copy
     */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        // do not allow other threads to whack on in or out during copy
        synchronized (in) {
            synchronized (out) {
                byte[] buffer = new byte[256];
                while (true) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) break;
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}
