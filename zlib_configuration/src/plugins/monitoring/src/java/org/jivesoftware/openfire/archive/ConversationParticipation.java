/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.archive;

import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

/**
 * Participation of a user, connected from a specific resource, in a conversation. If
 * a user joins and leaves the conversation many times then we will have many instances
 * of this class.
 *
 * @author Gaston Dombiak
 */
public class ConversationParticipation implements Externalizable {
    private Date joined = new Date();
    private Date left;
    private String nickname;

    public ConversationParticipation() {
    }

    public ConversationParticipation(Date joined) {
        this.joined = joined;
    }

    public ConversationParticipation(Date joined, String nickname) {
        this.joined = joined;
        this.nickname = nickname;
    }

    public void participationEnded(Date left) {
        this.left = left;
    }
    
    /**
     * Returns the date when the user joined the conversation.
     *
     * @return the date when the user joined the conversation.
     */
    public Date getJoined() {
        return joined;
    }

    /**
     * Returns the date when the user left the conversation.
     *
     * @return the date when the user left the conversation.
     */
    public Date getLeft() {
        return left;
    }

    /**
     * Returns the nickname of the user used in the group conversation or
     * <tt>null</tt> if participation is in a one-to-one chat.
     *
     * @return the nickname of the user used in the group conversation.
     */
    public String getNickname() {
        return nickname;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeLong(out, joined.getTime());
        ExternalizableUtil.getInstance().writeBoolean(out, nickname != null);
        if (nickname != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, left != null);
        if (left != null) {
            ExternalizableUtil.getInstance().writeLong(out, left.getTime());
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        joined = new Date(ExternalizableUtil.getInstance().readLong(in));
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            left = new Date(ExternalizableUtil.getInstance().readLong(in));
        }
    }
}
