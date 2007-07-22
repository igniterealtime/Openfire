/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.HistoryStrategy;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServerImpl;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Cluster task that will update the history strategy used by the MultiUserChatServer
 * service. It is currently not possible to edit the history strategy of a given room
 * but only of the service. Therefore, this task will only update the service's strategy.
 *
 * @author Gaston Dombiak
 */
public class UpdateHistoryStrategy implements ClusterTask {
    private int type;
    private int maxNumber;

    public UpdateHistoryStrategy() {
    }

    public UpdateHistoryStrategy(HistoryStrategy historyStrategy) {
        type = historyStrategy.getType().ordinal();
        maxNumber = historyStrategy.getMaxNumber();
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        MultiUserChatServerImpl mucServer = (MultiUserChatServerImpl) XMPPServer.getInstance().getMultiUserChatServer();
        HistoryStrategy strategy = mucServer.getHistoryStrategy();
        strategy.setType(HistoryStrategy.Type.values()[type]);
        strategy.setMaxNumber(maxNumber);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, type);
        ExternalizableUtil.getInstance().writeInt(out, maxNumber);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = ExternalizableUtil.getInstance().readInt(in);
        maxNumber = ExternalizableUtil.getInstance().readInt(in);
    }
}
