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
class IQQueryHandler1 extends IQQueryHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQQueryHandler1.class);
    private static final String MODULE_NAME = "Message Archive Management Query Handler v1";
    private static final String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

    IQQueryHandler1() {
        super(MODULE_NAME, "urn:xmpp:mam:1");
    }

    @Override
    protected void sendEndQuery(IQ packet, JID jid, QueryRequest queryRequest) {
        sendAcknowledgementResult(packet, jid, queryRequest);
    }

    /**
     * Send result packet to client acknowledging query.
     * @param packet Received query packet
     * @param JID to respond to
     */
    private void sendAcknowledgementResult(IQ packet, JID from, QueryRequest queryRequest) {
        if (packet.getTo() == null) packet.setTo(domain);

        IQ result = IQ.createResultIQ(packet);
        Element fin = result.setChildElement("fin", NAMESPACE);
        completeFinElement(queryRequest, fin);
        router.route(result);
    }
}