package org.jivesoftware.openfire.muc;

import java.util.Date;

/**
 * Represents information about a retired MUC room.
 */
public class MUCRoomRetiree {
    private final String name;
    private final String alternateJID;
    private final String reason;
    private final Date retiredAt;

    public MUCRoomRetiree(String name, String alternateJID, String reason, Date retiredAt) {
        this.name = name;
        this.alternateJID = alternateJID;
        this.reason = reason;
        this.retiredAt = retiredAt;
    }

    public String getName() {
        return name;
    }

    public String getAlternateJID() {
        return alternateJID;
    }

    public String getReason() {
        return reason;
    }

    public Date getRetiredAt() {
        return retiredAt;
    }
}
