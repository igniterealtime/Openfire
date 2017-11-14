/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will be executed in cluster nodes to get the number of sessions hosted by the
 * cluster node.
 *
 * @author Gaston Dombiak
 */
public class GetSessionsCountTask implements ClusterTask<Integer> {
    private Boolean authenticated;
    private Integer count;

    public GetSessionsCountTask() {
    }

    public GetSessionsCountTask(Boolean authenticated) {
        this.authenticated = authenticated;
    }

    @Override
    public Integer getResult() {
        return count;
    }

    @Override
    public void run() {
        if (authenticated) {
            // Get count of authenticated sessions
            count = SessionManager.getInstance().getUserSessionsCount(true);
        }
        else {
            // Get count of connected sessions (authenticated or not)
            count = SessionManager.getInstance().getConnectionsCount(true);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeBoolean(out, authenticated);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        authenticated = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
