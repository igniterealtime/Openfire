/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.archive;

import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: gato
 * Date: Oct 9, 2007
 * Time: 11:59:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class UserParticipations implements Externalizable {
    /**
     * Flag that indicates if the participations of the user were in a group chat conversation or a one-to-one
     * chat.
     */
    private boolean roomParticipation;
    /**
     * Participations of the same user in a groupchat or one-to-one chat. In a group chat conversation
     * a user may leave the conversation and return later so for each time the user joined the room a new
     * participation is going to be created. Moreover, each time the user changes his nickname in the room
     * a new participation is created.
     */
    private List<ConversationParticipation> participations;

    public UserParticipations() {
    }

    public UserParticipations(boolean roomParticipation) {
        this.roomParticipation = roomParticipation;
        if (roomParticipation) {
            participations = new ArrayList<ConversationParticipation>();
        }
        else {
            participations = new CopyOnWriteArrayList<ConversationParticipation>();
        }
    }

    public List<ConversationParticipation> getParticipations() {
        return participations;
    }

    public ConversationParticipation getRecentParticipation() {
        return participations.get(0);
    }

    public void addParticipation(ConversationParticipation participation) {
        participations.add(0, participation);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeBoolean(out, roomParticipation);
        ExternalizableUtil.getInstance().writeExternalizableCollection(out, participations);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        roomParticipation = ExternalizableUtil.getInstance().readBoolean(in);
        if (roomParticipation) {
            participations = new ArrayList<ConversationParticipation>();
        }
        else {
            participations = new CopyOnWriteArrayList<ConversationParticipation>();
        }
        ExternalizableUtil.getInstance().readExternalizableCollection(in, participations, getClass().getClassLoader());
    }
}
