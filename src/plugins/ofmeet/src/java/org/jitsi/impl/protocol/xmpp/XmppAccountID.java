/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.service.protocol.jabber.*;

import java.util.*;

/**
 * Account ID used by Jitsi Meet focus XMPP accounts.
 *
 * FIXME: move code related to account properties initialization here,
 * protocol provider or factory. Eventually remove this class.
 *
 * @author Pawel Domas
 */
public class XmppAccountID
    extends JabberAccountID
{
    public XmppAccountID(String id, Map<String, String> accountProperties)
    {
        super(id, accountProperties);
    }
}
