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

import org.dom4j.Element;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that requests the cluster node hosting a room occupant to change his
 * role and/or affiliation. If the occupant was not found or the change is
 * not allowed then a <tt>null</tt> value is returned. Otherwise the DOM
 * object representing the new presence of the room occupant is returned.
 *
 * @author Gaston Dombiak
 */
public class UpdateOccupantRequest extends MUCRoomTask {
    private Element answer;
    private String nickname;
    private int role;
    private MUCRole.Affiliation affiliation;


    public UpdateOccupantRequest() {
    }

    public UpdateOccupantRequest(LocalMUCRoom room, String nickname, MUCRole.Affiliation newAffiliation,
                                 MUCRole.Role newRole) {
        super(room);
        this.nickname = nickname;
        this.role = newRole.ordinal();
        this.affiliation = newAffiliation;
    }

    public String getNickname() {
        return nickname;
    }

    public MUCRole.Role getRole() {
        return MUCRole.Role.values()[role];
    }

    public MUCRole.Affiliation getAffiliation() {
        return affiliation;
    }

    public boolean isAffiliationChanged() {
        return affiliation != null;
    }

    public Object getResult() {
        return answer;
    }

    public void run() {
        try {
            Presence presence = getRoom().updateOccupant(this);
            if (presence != null) {
                answer = presence.getElement();
            }
        } catch (NotAllowedException e) {
            // Do nothing. A null return value means that the operation failed
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        ExternalizableUtil.getInstance().writeInt(out, role);
        ExternalizableUtil.getInstance().writeBoolean(out, affiliation != null);
        if (affiliation != null) {
            ExternalizableUtil.getInstance().writeInt(out, affiliation.ordinal());
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        role = ExternalizableUtil.getInstance().readInt(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            affiliation = MUCRole.Affiliation.values()[ExternalizableUtil.getInstance().readInt(in)];
        }
    }
}