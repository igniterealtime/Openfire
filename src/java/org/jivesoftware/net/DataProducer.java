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

package org.jivesoftware.net;

import org.jivesoftware.net.DataConsumer;

import java.nio.ByteBuffer;

/**
 * <p>DataProducers create data to be processed, optionally passing the
 * data along to downstream consumers.</p>
 *
 * <p>Produces can either obtain their data from an upstream producer
 * or can be generated (the normal mode). They may also be chained
 * by setting a data sink allowing consumers to be chained.</p>
 *
 * @author Iain Shigeoka
 */
public interface DataProducer {
    /**
     * <p>Set a data producer as a source of default source of data.</p>
     *
     * <p>Producers with a source often act as filters for incoming data.</p>
     *
     * @param source The producer that will source data to this producer
     * or null for no upstream producer
     */
    void setSource(DataProducer source);

    /**
     * <p>Set a downstream data consumer as a sink for data.</p>
     *
     * @param sink The consumer that will consume the data from this producer
     * or null if there is no downstream consumer
     */
    void setSink(DataConsumer sink);

    /**
     * <p>Produce data from the producer until the produce() returns null (no data)
     * or the sink.consume() returns false.</p>
     */
    void produceAll();

    /**
     * <p>Produce data up to the given limit.</p>
     *
     * @param limit The limit on the number of bytes to produce or zero for no limit
     * @return The bytes produced or an empty buffer no bytes are available
     */
    ByteBuffer produce(long limit);
}
