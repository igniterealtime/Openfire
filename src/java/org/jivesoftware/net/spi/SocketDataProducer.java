/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.net.spi;

import org.jivesoftware.net.Connection;

import java.io.InputStream;
import java.nio.ByteBuffer;

import org.jivesoftware.net.spi.BasicDataProducer;
import org.jivesoftware.net.Connection;

/**
 * Produces data from the input stream of a socket connection.
 *
 * @author Iain Shigeoka
 */
public class SocketDataProducer extends BasicDataProducer {

    private Connection conn;
    private InputStream in;

    /**
     * <p>Create a new socket producer for the given connection using
     * the provided input stream.</p>
     *
     * @param in The input stream to read from
     * @param connection The connection the data producer belongs to
     */
    SocketDataProducer(InputStream in, Connection connection){
        super();
        this.conn = connection;
        this.in = in;
    }

    protected ByteBuffer doProduction(long limit) {
        return super.doProduction(limit);
    }
}