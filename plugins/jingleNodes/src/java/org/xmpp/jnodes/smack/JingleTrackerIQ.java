package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.packet.IQ;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class JingleTrackerIQ extends IQ {

    public static final String NAME = "services";
    public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes";

    private final ConcurrentHashMap<String, TrackerEntry> entries = new ConcurrentHashMap<String, TrackerEntry>();

    public JingleTrackerIQ() {
        this.setType(Type.GET);
        this.setPacketID(IQ.nextID());
    }

    public boolean isRequest() {
        return Type.GET.equals(this.getType());
    }

    public void addEntry(final TrackerEntry entry) {
        entries.put(entry.getJid(), entry);
    }

    public void removeEntry(final TrackerEntry entry) {
        entries.remove(entry.getJid());
    }

    public String getChildElementXML() {
        final StringBuilder str = new StringBuilder();

        str.append("<").append(NAME).append(" xmlns='").append(NAMESPACE).append("'>");
        for (final TrackerEntry entry : entries.values()) {
            str.append("<").append(entry.getType().toString());
            str.append(" policy='").append(entry.getPolicy().toString()).append("'");
            str.append(" address='").append(entry.getJid()).append("'");
            str.append(" protocol='").append(entry.getProtocol()).append("'");
            if (entry.isVerified()) {
                str.append(" verified='").append(entry.isVerified()).append("'");
            }
            str.append("/>");
        }
        str.append("</").append(NAME).append(">");

        return str.toString();
    }

    public Collection<TrackerEntry> getEntries() {
        return entries.values();
    }
}
