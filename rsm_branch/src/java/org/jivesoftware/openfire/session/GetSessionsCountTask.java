/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
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
public class GetSessionsCountTask implements ClusterTask {
    private Boolean authenticated;
    private Integer count;

    public GetSessionsCountTask() {
    }

    public GetSessionsCountTask(Boolean authenticated) {
        this.authenticated = authenticated;
    }

    public Object getResult() {
        return count;
    }

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

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeBoolean(out, authenticated);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        authenticated = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
