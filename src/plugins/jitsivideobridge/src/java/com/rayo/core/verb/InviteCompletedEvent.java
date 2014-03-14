package com.rayo.core.verb;

import org.xmpp.packet.*;
import org.dom4j.*;

public class InviteCompletedEvent extends AbstractVerbEvent {

    private String callId;

    public InviteCompletedEvent(String callId)
    {
		this.callId = callId;
	}

    public String getCallId() {
        return callId;
    }
}
