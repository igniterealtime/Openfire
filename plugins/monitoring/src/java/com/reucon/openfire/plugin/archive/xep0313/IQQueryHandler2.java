package com.reucon.openfire.plugin.archive.xep0313;

import org.dom4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import org.jivesoftware.openfire.XMPPServer;

/**
 * XEP-0313 IQ Query Handler
 */
class IQQueryHandler2 extends IQQueryHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQQueryHandler2.class);
    private static final String MODULE_NAME = "Message Archive Management Query Handler v2";
    private static final String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

    IQQueryHandler2() {
        super(MODULE_NAME, "urn:xmpp:mam:2");
    }

    @Override
    protected void sendEndQuery(IQ packet, JID from, QueryRequest queryRequest) {
        sendAcknowledgementResult(packet, from, queryRequest);
    }

    /**
     * Send result packet to client acknowledging query.
     * @param packet Received query packet
     * @param JID to respond to
     */
    private void sendAcknowledgementResult(IQ packet, JID from, QueryRequest queryRequest) {
        IQ result = IQ.createResultIQ(packet);
        result.setFrom(domain);
        Element fin = result.setChildElement("fin", NAMESPACE);
        completeFinElement(queryRequest, fin);
        router.route(result);
    }
}
