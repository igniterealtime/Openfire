/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.HistoryStrategy;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Cluster task that will update the history strategy used by a MultiUserChatService
 * service. It is currently not possible to edit the history strategy of a given room
 * but only of the service. Therefore, this task will only update the service's strategy.
 *
 * @author Gaston Dombiak
 */
public class UpdateHistoryStrategy implements ClusterTask {
    private String serviceName;
    private int type;
    private int maxNumber;

    public UpdateHistoryStrategy() {
    }

    public UpdateHistoryStrategy(String serviceName, HistoryStrategy historyStrategy) {
        this.serviceName = serviceName;
        type = historyStrategy.getType().ordinal();
        maxNumber = historyStrategy.getMaxNumber();
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        MultiUserChatServiceImpl mucServer = (MultiUserChatServiceImpl) XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
        if (mucServer == null) throw new IllegalArgumentException("MUC service not found for subdomain: "+serviceName);
        HistoryStrategy strategy = mucServer.getHistoryStrategy();
        strategy.setType(HistoryStrategy.Type.values()[type]);
        strategy.setMaxNumber(maxNumber);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, serviceName);
        ExternalizableUtil.getInstance().writeInt(out, type);
        ExternalizableUtil.getInstance().writeInt(out, maxNumber);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        serviceName = ExternalizableUtil.getInstance().readSafeUTF(in);
        type = ExternalizableUtil.getInstance().readInt(in);
        maxNumber = ExternalizableUtil.getInstance().readInt(in);
    }
}
