package com.reucon.openfire.plugin.archive.xep0313;

import java.util.ArrayList;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQHandler;

import com.reucon.openfire.plugin.archive.xep.AbstractXepSupport;

/**
 * Encapsulates support for <a
 * href="http://www.xmpp.org/extensions/xep-0313.html">XEP-0313</a>.
 */
public class Xep0313Support extends AbstractXepSupport {

    private static final String NAMESPACE = "urn:xmpp:mam:0";

    public Xep0313Support(XMPPServer server) {
        super(server, NAMESPACE,NAMESPACE, "XEP-0313 IQ Dispatcher", true);

        this.iqHandlers = new ArrayList<>();
        iqHandlers.add(new IQQueryHandler0());
    }

}
