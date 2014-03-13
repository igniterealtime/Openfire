package com.rayo.core.verb;

import org.xmpp.packet.*;

public class UnInviteCommand extends AbstractVerbCommand {

    private JID muc;
    private String callId;

    public UnInviteCommand(JID muc, String callId)
    {
		this.muc = muc;
        this.callId = callId;
	}

    public JID getMuc() {
        return muc;
    }

    public void setMuc(JID muc) {
        this.muc = muc;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }
}
