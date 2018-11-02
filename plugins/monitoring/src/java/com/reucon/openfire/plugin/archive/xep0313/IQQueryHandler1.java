package com.reucon.openfire.plugin.archive.xep0313;

import org.dom4j.*;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

/**
 * XEP-0313 IQ Query Handler
 */
class IQQueryHandler1 extends IQQueryHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQQueryHandler1.class);
    private static final String MODULE_NAME = "Message Archive Management Query Handler v1";

    IQQueryHandler1() {
        super(MODULE_NAME, "urn:xmpp:mam:1");
    }

    @Override
    protected void sendEndQuery(IQ packet, Session session, QueryRequest queryRequest) {
        sendAcknowledgementResult(packet, session, queryRequest);
    }

    /**
     * Send result packet to client acknowledging query.
     * @param packet Received query packet
     * @param session Client session to respond to
     */
    private void sendAcknowledgementResult(IQ packet, Session session, QueryRequest queryRequest) {
        IQ result = IQ.createResultIQ(packet);
        Element fin = result.setChildElement("fin", NAMESPACE);
        completeFinElement(queryRequest, fin);
        session.process(result);
    }
}
