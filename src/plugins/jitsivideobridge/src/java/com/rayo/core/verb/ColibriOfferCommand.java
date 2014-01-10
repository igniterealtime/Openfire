package com.rayo.core.verb;

import org.xmpp.packet.*;

public class ColibriOfferCommand extends AbstractVerbCommand {

    private JID muc;

    public ColibriOfferCommand(JID muc)
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
