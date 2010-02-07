/**
 * $RCSfile: $
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

package org.jivesoftware.openfire.plugin;

import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.management.MINAStatCollector;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.TaskEngine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Collector of raw data that is print to a log file every minute.
 *
 * @author Gaston Dombiak
 */
public class StatCollector extends TimerTask {
    private boolean headerPrinter = false;
    private List<String> content = new ArrayList<String>();
    private SocketAcceptor socketAcceptor;
    // Take a sample every X seconds
    private int frequency;
    private boolean started = false;
    private MINAStatCollector statCollector;

    public StatCollector(int frequency) {
        this.frequency = frequency;
        ConnectionManagerImpl connectionManager =
                ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
        if (JiveGlobals.getBooleanProperty("statistic.connectionmanager", false)) {
            socketAcceptor = connectionManager.getMultiplexerSocketAcceptor();
        }
        else {
            socketAcceptor = connectionManager.getSocketAcceptor();
        }
        statCollector = new MINAStatCollector(socketAcceptor, frequency - 1000);
    }

    @Override
	public void run() {
        try {
            // Collect content
            StringBuilder sb = new StringBuilder();
            // Add current timestamp
            sb.append(System.currentTimeMillis());
            sb.append(',');
            // Add info about the db connection pool
            sb.append(DbConnectionManager.getConnectionProvider().toString());
            sb.append(',');
            // Add info about the thread pool that process incoming requests
            ExecutorThreadModel threadModel = (ExecutorThreadModel) socketAcceptor.getDefaultConfig().getThreadModel();
            ThreadPoolExecutor executor = (ThreadPoolExecutor) threadModel.getExecutor();
            sb.append(executor.getCorePoolSize());
            sb.append(',');
            sb.append(executor.getActiveCount());
            sb.append(',');
            sb.append(executor.getQueue().size());
            sb.append(',');
            sb.append(executor.getCompletedTaskCount());
            // Add info about number of connected sessions
            sb.append(',');
            sb.append(SessionManager.getInstance().getConnectionsCount(false));
            // Add info about MINA statistics
            sb.append(',');
            sb.append(statCollector.getMsgRead());
            sb.append(',');
            sb.append(statCollector.getMsgWritten());
            sb.append(',');
            sb.append(statCollector.getQueuedEvents());
            sb.append(',');
            sb.append(statCollector.getScheduledWrites());

            // Add new line of content with current stats
            content.add(sb.toString());

            // Check if we need to print content to file (print content every minute)
            if (content.size() > (60f / frequency * 1000)) {
                try {
                    File file = new File(JiveGlobals.getHomeDirectory() + File.separator + "logs", JiveGlobals.getProperty("statistic.filename", "stats.txt"));
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    BufferedWriter out = new BufferedWriter(new FileWriter(file, true));
                    if (!headerPrinter) {
                        out.write(new Date().toString());
                        out.write('\n');
                        out.write(
                                "Timestamp, DB min, DB max, DB current, DB used, Core Threads, Active Threads, Queue Tasks, Completed Tasks, Sessions, NIO Read, NIO Written, Queued NIO events, Queues NIO writes");
                        out.write('\n');
                        headerPrinter = true;
                    }
                    for (String line : content) {
                        out.write(line);
                        out.write('\n');
                    }
                    out.close();
                } catch (IOException e) {
                    Log.error("Error creating statistics log file", e);
                }
                content.clear();
            }
        } catch (Exception e) {
            Log.error("Error collecting and logging server statistics", e);
        }
    }

    public synchronized void start() {
        if (!started) {
            started = true;
            statCollector.start();
            TaskEngine.getInstance().scheduleAtFixedRate(this, 1000, frequency);
        }
    }

    public void stop() {
        if (started) {
            statCollector.stop();
            TaskEngine.getInstance().cancelScheduledTask(this);
        }
    }
}
