/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log;



/**
 * A Log target which will do filtering and then pass it
 * onto targets further along in chain.
 * <p/>
 * <p>Filtering can mena that not all LogEvents get passed
 * along chain or that the LogEvents passed alongare modified
 * in some manner.</p>
 *
 * @author <a href="mailto:donaldp@apache.org">Peter Donald</a>
 */
public interface FilterTarget extends LogTarget {

    /**
     * Add a target to output chain.
     *
     * @param target the log target
     */
    void addTarget(LogTarget target);
}
