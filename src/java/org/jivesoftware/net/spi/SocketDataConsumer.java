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
package org.jivesoftware.net.spi;

import org.jivesoftware.net.Connection;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.jivesoftware.net.spi.BasicDataConsumer;


/**
 * <p>Socket data consumer implementation designed to work intimately with
 * the SocketConnection class.</p>
 *
 * @author Iain Shigeoka
 */
public class SocketDataConsumer extends BasicDataConsumer {

    private Connection conn;
    private OutputStream out;

    /**
     * <p>Create a new socket consumer for the given connection using
     * the provided output stream.</p>
     *
     * @param out The output stream to write to
     * @param connection The connection the data consumer belongs to
     */
    SocketDataConsumer(OutputStream out, Connection connection){
        super();
        this.conn = connection;
        this.out = out;
    }

    /**
     * <p>Consume the data by writing it to the underlying socket output stream.</p>
     *
     * <p>Any IOExceptions during the write will return a false result.</p>
     *
     * @param data The data to write to the connection's output stream
     */
    protected void doConsume(ByteBuffer data){
        try {
            if (!conn.isClosed()){
                // TODO: implement write
                //out.write(data);
                throw new IOException();
            }
        } catch (IOException e) {
            // TODO: implement log
            // log
        }
    }
}
