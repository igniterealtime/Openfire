/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that destroys the local room in the cluster node. Local room occupants
 * hosted in the cluster node will get the notification of the room being
 * destroyed.
 *
 * @author Gaston Dombiak
 */
public class DestroyRoomRequest extends MUCRoomTask {
    private String alternateJID;
    private String reason;

    public DestroyRoomRequest() {
    }

    public DestroyRoomRequest(LocalMUCRoom room, String alternateJID, String reason) {
        super(room);
        this.alternateJID = alternateJID;
        this.reason = reason;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        getRoom().destroyRoom(this);
    }

    public String getAlternateJID() {
        return alternateJID;
    }

    public String getReason() {
        return reason;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeBoolean(out, alternateJID != null);
        if (alternateJID != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, alternateJID);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, reason != null);
        if (reason != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, reason);
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            alternateJID = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            reason = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }
}
