package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.xmpp.packet.Packet;

import java.util.List;

public interface Rule {
    enum Action {
        Pass,
        Drop,
        Reject
    }

    ;

    enum PacketType {
        Message("Message"),
        MessageChat("MUC Private Message"),
        MessageGroupChat("MUC"),
        Presence("Presence"),
        Iq("Iq"),
        Any("Any");

        private String display;

        PacketType(String display) {
            this.display = display;
        }

        public String getDisplayName() {
            return display;
        }
    }

    ;

    enum SourceDestType {
        Any,
        User,
        Group,
        Component,
        Other
    }

    ;


    public Action getPacketAction();

    public void setPacketAction(Action action);

    public PacketType getPackeType();

    public void setPacketType(PacketType packetType);

    public Boolean isDisabled();

    public void isDisabled(Boolean disabled);

    public String getSource();

    public void setSource(String source);

    public String getDestination();

    public void setDestination(String destination);

    public Boolean doLog();

    public void doLog(Boolean log);

    public String getDescription();

    public void setDescription(String description);

    public String getRuleId();

    public void setRuleId(String id);

    public int getOrder();

    public void setOrder(int order);

    public String getRuleType();

    public String getDisplayName();

    public void setDisplayName(String displayName);

    public SourceDestType getSourceType();

    public void setSourceType(SourceDestType sourceType);

    public SourceDestType getDestType();

    public void setDestType(SourceDestType destType);
    

    public Packet doAction(Packet packet) throws PacketRejectedException;
}
