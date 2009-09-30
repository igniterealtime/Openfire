/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008-2008 Jive Software. All rights reserved.
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
