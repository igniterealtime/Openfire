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
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that changes the nickname of an existing room occupant in the cluster node. When
 * a room occupant changes his nickname the other cluster nodes, that hold a
 * {@link org.jivesoftware.openfire.muc.spi.RemoteMUCRole} will need to update their local
 * information with the new nickname.
 *
 * @author Gaston Dombiak
 */
public class ChangeNickname extends MUCRoomTask {
    private String oldNick;
    private String newNick;
    private Presence presence;

    public ChangeNickname() {
    }

    public ChangeNickname(LocalMUCRoom room, String oldNick, String newNick, Presence presence) {
        super(room);
        this.oldNick = oldNick;
        this.newNick = newNick;
        this.presence = presence;
    }

    public String getOldNick() {
        return oldNick;
    }

    public String getNewNick() {
        return newNick;
    }

    public Presence getPresence() {
        return presence;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            public void run() {
                getRoom().nicknameChanged(ChangeNickname.this);
            }
        });
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeSafeUTF(out, oldNick);
        ExternalizableUtil.getInstance().writeSafeUTF(out, newNick);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        oldNick = ExternalizableUtil.getInstance().readSafeUTF(in);
        newNick = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
