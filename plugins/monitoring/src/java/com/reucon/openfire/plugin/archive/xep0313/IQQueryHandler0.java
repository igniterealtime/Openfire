package com.reucon.openfire.plugin.archive.xep0313;

import org.dom4j.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.JID;

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
    protected void sendMidQuery(IQ packet) {
        sendAcknowledgementResult(packet);
    }

    @Override
    protected void sendEndQuery(IQ packet, JID from, QueryRequest queryRequest) {
        sendFinalMessage(from, queryRequest);
    }

    /**
     * Send result packet to client acknowledging query.
     * @param packet Received query packet
     */
    private void sendAcknowledgementResult(IQ packet) {
        IQ result = IQ.createResultIQ(packet);
        router.route(result);
    }

    /**
     * Send final message back to client following query.
     * @param JID to respond to
     * @param queryRequest Received query request
     */
    private void sendFinalMessage(JID from, final QueryRequest queryRequest) {

        Message finalMessage = new Message();
        finalMessage.setTo(from);
        if ( XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService( queryRequest.getArchive() ) != null )
        {
            finalMessage.setFrom( queryRequest.getArchive().asBareJID() );
        }
        Element fin = finalMessage.addChildElement("fin", NAMESPACE);
        completeFinElement(queryRequest, fin);

        router.route(finalMessage);
    }

}
