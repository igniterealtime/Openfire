/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.TaskEngine;

import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Queue tasks while this JVM is joining the cluster and the requested room was still not loaded.
 *
 * @author Gaston Dombiak
 */
public class QueuedTasksManager {

    private static QueuedTasksManager instance = new QueuedTasksManager();

    private Queue<MUCRoomTask> taskQueue = new ConcurrentLinkedQueue<MUCRoomTask>();

    public static QueuedTasksManager getInstance() {
        return instance;
    }

    /**
     * Hide the constructor so no one can create other instances
     */
    private QueuedTasksManager() {
        // Register a periodic task that will execute queued tasks
        TaskEngine.getInstance().scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (!ClusterManager.isClusteringStarting()) {
                    MUCRoomTask mucRoomTask;
                    while ((mucRoomTask = taskQueue.poll()) != null) {
                        mucRoomTask.run();
                    }
                }
            }
        }, 1000, 30000);
    }

    /**
     * Queues a task. The queued task will be executed once this JVM completed joining the cluster.
     * Moreover, if joining the cluster failed then the queue will also be consumed.
     *
     * @param task the task to queue.
     */
    public void addTask(MUCRoomTask task) {
        taskQueue.add(task);
    }
}
