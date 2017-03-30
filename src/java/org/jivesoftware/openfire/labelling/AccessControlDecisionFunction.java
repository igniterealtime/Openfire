package org.jivesoftware.openfire.labelling;

import org.xmpp.packet.JID;

import java.util.Set;

/**
 * Created by dwd on 15/03/17.
 */
public interface AccessControlDecisionFunction {
    public SecurityLabel check(String clearances, SecurityLabel label, JID rewrite) throws SecurityLabelException;
    public SecurityLabel valid(SecurityLabel label, boolean rewrite) throws SecurityLabelException;
    public String getClearance(JID entity);
}
