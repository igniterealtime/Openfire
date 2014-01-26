package com.rayo.core.verb;

import org.xmpp.packet.*;
import org.dom4j.*;

public class JoinBridgeEvent extends AbstractVerbEvent {

    private String mixer;
    private String nickname;
    private JID participant;

    public JoinBridgeEvent(String mixer, JID participant, String nickname)
    {
		this.mixer = mixer;
		this.participant = participant;
		this.nickname = nickname;
	}

    public String getMixer() {
        return mixer;
    }

    public String getNickname() {
        return nickname;
    }

    public JID getParticipant() {
        return participant;
    }
}
