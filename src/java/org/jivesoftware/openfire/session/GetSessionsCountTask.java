/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
