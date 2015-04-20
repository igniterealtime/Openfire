package org.ifsoft.websockets;

import org.jivesoftware.openfire.StreamID;

public class BasicStreamID implements StreamID {
    String id;

    public BasicStreamID(String id) {
        this.id = id;
    }

    public String getID() {
        return id;
    }

    public String toString() {
        return id;
    }

    public int hashCode() {
        return id.hashCode();
    }
}
