package com.rayo.core.verb;

import org.xmpp.packet.*;

public class RemoveSourceEvent extends AbstractVerbEvent {

   	private JID muc;
    private String nickname;
    private JID participant;
    private boolean active;

    public RemoveSourceEvent() {}

    public RemoveSourceEvent(Verb verb) {
        super(verb);
    }

    public JID getParticipant() {
        return participant;
    }

    public void setParticipant(JID participant) {
        this.participant = participant;
    }

    public JID getMuc() {
        return muc;
    }

    public void setMuc(JID muc) {
        this.muc = muc;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    public void setActive(boolean active) {
		this.active = active;
	}

    public boolean isActive() {
		return active;
	}
}
