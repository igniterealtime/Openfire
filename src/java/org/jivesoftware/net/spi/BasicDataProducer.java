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
 * <p>A basic data producer that produces data from it's
 * source and adds samples to the monitor.</p>
 *
 * @author Iain Shigeoka
 */
public class BasicDataProducer implements DataProducer {

    /** The source of the data producer, or null if none */
    protected DataProducer source;
    /** The sink for the data producer data, or null if none */
    protected DataConsumer sink;
    /** The mnitor of the data producer data, or null if none */
    protected Monitor monitor;

    /**
     * <p>Create a data producer with the given source, sink, and monitor.</p>
     *
     * @param source The data source or null for none
     * @param sink The data sink or null for none
     * @param monitor The monitor to use or null for none
     */
    public BasicDataProducer(DataProducer source, DataConsumer sink, Monitor monitor){
        this.source = source;
        this.sink = sink;
        this.monitor = monitor;
    }

    /**
     * <p>Create a data producer with the given monitor.</p>
     *
     * @param monitor The monitor to use or null for none
     */
    public BasicDataProducer(Monitor monitor){
        this(null,null,monitor);
    }

    /**
     * <p>Create a data producer without any source, sink, and monitor.</p>
     */
    public BasicDataProducer(){
        this(null,null,null);
    }

    public void setSource(DataProducer source) {
        this.source = source;
    }

    public void setSink(DataConsumer sink) {
        this.sink = sink;
    }

    public void produceAll() {
        if (sink != null){
            ByteBuffer data = produce(0);
            while(data.hasRemaining()){
                sink.consume(data);
                data = produce(0);
            }
        }
    }

    public ByteBuffer produce(long limit) {
        Date start = new Date();

        ByteBuffer data = null;
        boolean success = preProduction(limit);
        if (success){
            data = doProduction(limit);
        }
        if (success && data != null){
            success = postProduction(data,limit);
        }

        if (monitor != null){
            monitor.addSample(data.remaining(),start,new Date());
        }

        if (data != null && sink != null){
            sink.consume(data);
        }

        return data;
    }

    /**
     * <p>Called before production begins to verify production should be done.</p>
     *
     * <p>Override to check the limit size before allowing actual production.</p>
     *
     * @param limit The limit to check before data production or zero for no limit
     * @return True if the production should continue
     */
    protected boolean preProduction(long limit){
        return true;
    }

    /**
     * <p>Conduct the actual production for at most <code>limit</code> bytes.</p>
     *
     * @param limit The limit in bytes to produce
     * @return The bytes read or null for no bytes
     */
    protected ByteBuffer doProduction(long limit){
        ByteBuffer data = null;
        if (source != null){
            data = source.produce(limit);
        }
        return data;
    }

    /**
     * <p>Called after production if verification of the read bytes should
     * be made before returning.</p>
     *
     * @param data The data that was read or null if no data read
     * @param limit The limit in bytes to produce
     * @return True if the production was successful
     */
    protected boolean postProduction(ByteBuffer data, long limit){
        return true;
    }
}
