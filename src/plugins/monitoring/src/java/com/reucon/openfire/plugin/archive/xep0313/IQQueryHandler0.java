package com.reucon.openfire.plugin.archive.xep0313;

import org.dom4j.*;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;

/**
 * XEP-0313 IQ Query Handler
 */
class IQQueryHandler0 extends IQQueryHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQHandler.class);
    private static final String MODULE_NAME = "Message Archive Management Query Handler v0";

    IQQueryHandler0() {
        super(MODULE_NAME, "urn:xmpp:mam:0");
    }

    @Override
    protected void sendMidQuery(IQ packet, Session session) {
        sendAcknowledgementResult(packet, session);
    }

    @Override
    protected void sendEndQuery(IQ packet, Session session, QueryRequest queryRequest) {
        sendFinalMessage(session, queryRequest);
    }

    /**
     * Send result packet to client acknowledging query.
     * @param packet Received query packet
     * @param session Client session to respond to
     */
    private void sendAcknowledgementResult(IQ packet, Session session) {
        IQ result = IQ.createResultIQ(packet);
        session.process(result);
    }

    /**
     * Send final message back to client following query.
     * @param session Client session to respond to
     * @param queryRequest Received query request
     */
    private void sendFinalMessage(Session session,
            final QueryRequest queryRequest) {

        Message finalMessage = new Message();
        finalMessage.setTo(session.getAddress());
        Element fin = finalMessage.addChildElement("fin", NAMESPACE);
        completeFinElement(queryRequest, fin);

        session.process(finalMessage);
    }

}
