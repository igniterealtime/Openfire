package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.packet.IQ;

public class JingleChannelIQ extends IQ {

    public static final String NAME = "channel";
    public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes#channel";

    public static final String UDP= "udp";
    public static final String TCP= "tcp";

    private String protocol = UDP;
    private String host;
    private int localport = -1;
    private int remoteport = -1;
    private String id;

    public JingleChannelIQ() {
        this.setType(Type.GET);
        this.setPacketID(IQ.nextID());
    }

    public String getChildElementXML() {
        final StringBuilder str = new StringBuilder();

        str.append("<").append(NAME).append(" xmlns='").append(NAMESPACE).append("' protocol='").append(protocol).append("' ");
        if (localport > 0 && remoteport > 0 && host != null) {
            str.append("host='").append(host).append("' ");
            str.append("localport='").append(localport).append("' ");
            str.append("remoteport='").append(remoteport).append("' ");
        }
        str.append("/>");

        return str.toString();
    }

    public boolean isRequest() {
        return Type.GET.equals(this.getType());
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public int getRemoteport() {
        return remoteport;
    }

    public void setRemoteport(int remoteport) {
        this.remoteport = remoteport;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getLocalport() {
        return localport;
    }

    public void setLocalport(int localport) {
        this.localport = localport;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public static IQ createEmptyResult(IQ iq) {
        return createIQ(iq.getPacketID(), iq.getFrom(), iq.getTo(), IQ.Type.RESULT);
    }

    public static IQ createEmptyError(IQ iq) {
        return createIQ(iq.getPacketID(), iq.getFrom(), iq.getTo(), IQ.Type.ERROR);
    }

    public static IQ createEmptyError() {
        return createIQ(null, null, null, IQ.Type.ERROR);
    }

    public static IQ createIQ(String ID, String to, String from, IQ.Type type) {
        IQ iqPacket = new IQ() {
            public String getChildElementXML() {
                return null;
            }
        };

        iqPacket.setPacketID(ID);
        iqPacket.setTo(to);
        iqPacket.setFrom(from);
        iqPacket.setType(type);

        return iqPacket;
    }
}
