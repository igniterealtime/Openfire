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

import org.jivesoftware.net.DataConsumer;
import org.jivesoftware.net.DataProducer;
import org.jivesoftware.net.Monitor;

import java.nio.ByteBuffer;
import java.util.Date;

import org.jivesoftware.net.DataConsumer;
import org.jivesoftware.net.DataProducer;

/**
 * <p>A basic implementation of the data consumer .</p>
 *
 * @author Iain Shigeoka
 */
public class BasicDataConsumer implements DataConsumer {

    protected DataProducer source;
    protected DataConsumer sink;
    protected Monitor monitor;

    BasicDataConsumer(DataProducer source, DataConsumer sink, Monitor monitor){
        this.source = source;
        this.sink = sink;
        this.monitor = monitor;
    }

    BasicDataConsumer(Monitor monitor){
        this.monitor = monitor;
    }

    BasicDataConsumer(){
    }

    public void setSource(DataProducer source) {
        this.source = source;
    }

    public void setSink(DataConsumer sink) {
        this.sink = sink;
    }

    public void consumeAll() {
        if (source != null){
            ByteBuffer data = source.produce(0);
            while (data != null){
                consume(data);
                data = source.produce(0);
            }
        }
    }

    public void consume(ByteBuffer data) {
        Date start = new Date();
        int size = data.remaining();
        preConsume(data);
        if (data.hasRemaining()){
            doConsume(data);
        }
        if (data.hasRemaining()){
            postConsume(data);
        }
        if (monitor != null){
            monitor.addSample(size,start,new Date());
        }

        if (sink != null && data.hasRemaining()){
            sink.consume(data);
        }
    }

    protected void preConsume(ByteBuffer data){
    }

    protected void doConsume(ByteBuffer data){
    }

    protected void postConsume(ByteBuffer data){
    }
}
