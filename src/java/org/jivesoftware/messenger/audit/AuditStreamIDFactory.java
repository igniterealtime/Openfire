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
package org.jivesoftware.messenger.audit;

import org.jivesoftware.messenger.StreamID;
import org.jivesoftware.messenger.StreamIDFactory;
import org.jivesoftware.messenger.spi.BasicStreamIDFactory;

/**
 * Factory for producing audit stream IDs. We use a factory so that
 * audit information can be identified using an appropriate storage
 * key (typically a long for RDBMS).
 *
 * @author Iain Shigeoka
 */
public class AuditStreamIDFactory implements StreamIDFactory {

    private BasicStreamIDFactory factory = new BasicStreamIDFactory();

    public AuditStreamIDFactory() {
    }

    public StreamID createStreamID() {
        return factory.createStreamID();
    }
}
