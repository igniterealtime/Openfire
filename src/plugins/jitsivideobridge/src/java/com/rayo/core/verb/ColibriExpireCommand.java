package com.rayo.core.verb;

import org.xmpp.packet.*;

public class ColibriExpireCommand extends AbstractVerbCommand {

    private JID muc;

    public ColibriExpireCommand(JID muc)
    {
		this.muc = muc;
	}

    public JID getMuc() {
        return muc;
    }

    public void setMuc(JID muc) {
        this.muc = muc;
    }
}
