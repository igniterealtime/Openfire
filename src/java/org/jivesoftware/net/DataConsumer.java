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

import java.nio.ByteBuffer;

/**
 * <p>DataConsumers consume data, optionally passing it along to downstream
 * consumers.</p>
 *
 * <p>Consumers can either obtain their data from a producer using consumeAll()
 * or can be pushed data using their consume method. They may also be chained
 * by setting a data sink allowing consumers to be chained.</p>
 *
 * @author Iain Shigeoka
 */
public interface DataConsumer {

    /**
     * <p>Set a data producer as a source of data when using the consumeAll()
     * method.</p>
     *
     * @param source The producer that will source data to this consumer
     * or null for no producer
     */
    void setSource(DataProducer source);

    /**
     * Set a downstream data consumer as a sink for data creating a consumer
     * chain.
     *
     * @param sink The consumer that will consume the data after this consumer
     * or null if there is no downstream consumer
     */
    void setSink(DataConsumer sink);

    /**
     * Consume data from the producer until the producer returns null (no data).
     */
    void consumeAll();

    /**
     * Consume the given data.
     *
     * @param data The data to consume
     */
    void consume(ByteBuffer data);
}