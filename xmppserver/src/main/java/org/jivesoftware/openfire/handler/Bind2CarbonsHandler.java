package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.openfire.net.Bind2InlineHandler;
import org.jivesoftware.openfire.session.LocalClientSession;

public class Bind2CarbonsHandler implements Bind2InlineHandler {
    @Override
    public String getNamespace() {
        return "urn:xmpp:carbons:2";
    }

    @Override
    public boolean handleElement(LocalClientSession session, Element bound, Element element) {
        session.setMessageCarbonsEnabled(element.getName().equals("active"));
        return true;
    }
}
