package com.rayo.core.verb;

import org.xmpp.packet.*;
import org.dom4j.*;

public class InviteAcceptedEvent extends AbstractVerbEvent {

    private String callId;

    public InviteAcceptedEvent(String callId)
    {
		this.callId = callId;
	}

    public String getCallId() {
        return callId;
    }
}
