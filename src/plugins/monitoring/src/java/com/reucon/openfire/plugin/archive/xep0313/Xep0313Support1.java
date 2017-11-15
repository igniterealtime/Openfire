package com.reucon.openfire.plugin.archive.xep0313;

import com.reucon.openfire.plugin.archive.xep.AbstractXepSupport;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQHandler;

import java.util.ArrayList;

/**
 * Encapsulates support for <a
 * href="http://www.xmpp.org/extensions/xep-0313.html">XEP-0313</a>.
 */
public class Xep0313Support1 extends AbstractXepSupport {

    private static final String NAMESPACE = "urn:xmpp:mam:1";

    public Xep0313Support1(XMPPServer server) {
        super(server, NAMESPACE,NAMESPACE, "XEP-0313 IQ Dispatcher", true);

        this.iqHandlers = new ArrayList<>();
        iqHandlers.add(new IQQueryHandler1());
    }

}
