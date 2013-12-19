package com.rayo.core.verb;

import org.xmpp.packet.*;
import org.dom4j.*;

public class ColibriOfferEvent extends AbstractVerbEvent {

    private JID muc;
    private String nickname;
    private JID participant;
    private Element conference;

    public ColibriOfferEvent() {}

    public ColibriOfferEvent(Verb verb) {
        super(verb);
    }

    public Element getConference() {
        return conference;
    }

    public void setConference(Element conference) {
        this.conference = conference;
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
}
