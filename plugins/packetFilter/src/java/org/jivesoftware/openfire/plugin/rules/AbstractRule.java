package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.xmpp.packet.Packet;


public abstract class AbstractRule implements Rule {

    private Action packetAction;
    private PacketType packetType;
    private Boolean disabled;
    private String source;
    private String destination;
    private Boolean log;
    private String description;
    private String ruleId;
    private String displayName;
    private SourceDestType sourceType;
    private SourceDestType destType;


    public SourceDestType getDestType() {
        return destType;
    }

    public void setDestType(SourceDestType destType) {
        this.destType = destType;
    }

    public SourceDestType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceDestType sourceType) {
        this.sourceType = sourceType;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    private int order;


    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleType() {
        return this.getClass().getName();
    }


    public Action getPacketAction() {
        return packetAction;
    }

    public void setPacketAction(Action packetAction) {
        this.packetAction = packetAction;
    }

    public PacketType getPackeType() {
        return packetType;
    }

    public void setPacketType(PacketType packetType) {
        this.packetType = packetType;
    }

    public Boolean isDisabled() {
        return disabled;
    }

    public void isDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Boolean doLog() {
        return log;
    }

    public void doLog(Boolean log) {
        this.log = log;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Packet doAction(Packet packet) throws PacketRejectedException {
        return null;
    }

    public boolean sourceMustMatch() {
        return true;
    }

    public boolean destMustMatch() {
        return true;
    }


    @Override
	public String toString() {
        StringBuffer sb = new StringBuffer();
        if (packetAction != null)
        sb.append("Type : "+packetAction.toString()+'\n');
        sb.append("Order : "+getOrder()+'\n');
        sb.append("Diplay Name : "+getDisplayName()+'\n');
        sb.append("Packet Type : "+packetType+'\n');
        sb.append("ID : "+ruleId+'\n');
        sb.append("Soruce Type : "+sourceType+'\n');
        sb.append("Source : "+source+'\n');
        sb.append("Dest Type : "+destType+'\n');
        sb.append("Destination : "+destination+'\n');
        sb.append("Loging : "+log+'\n');
        sb.append("Disabled : "+disabled+'\n');
        return sb.toString();
    }
}
