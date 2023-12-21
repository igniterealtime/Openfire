/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.cluster;


import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClusterMonitorTest {

    private static final String THIS_HOST_NAME = "test-host-name";
    private static final byte[] OTHER_NODE_ID = new byte[]{0, 1, 2, 3};
    private ClusterMonitor clusterMonitor;
    @Mock private XMPPServer xmppServer;
    @Mock private XMPPServerInfo xmppServerInfo;

    @BeforeAll
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() {
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        lenient().doReturn(xmppServerInfo).when(xmppServer).getServerInfo();

        lenient().doReturn(THIS_HOST_NAME).when(xmppServerInfo).getHostname();

        clusterMonitor = new ClusterMonitor();
        clusterMonitor.initialize(xmppServer);
        clusterMonitor.start();
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    @Test
    public void whenAnotherNodeJoinsTheClusterNothingWillBeSent() {

        clusterMonitor.joinedCluster(OTHER_NODE_ID);

        verify(xmppServer, never()).sendMessageToAdmins(any());
    }

    @Test
    public void whenAnotherNodeLeavesTheClusterAMessageWillBeSent() {

        clusterMonitor.joinedCluster(OTHER_NODE_ID);
        clusterMonitor.leftCluster(OTHER_NODE_ID);

        verify(xmppServer).sendMessageToAdmins(any());
    }

    @Test
    public void whenAnotherNodeJoinsTheClusterAfterANodeHasLeftTheClusterAMessageWillBeSent() {

        clusterMonitor.joinedCluster(OTHER_NODE_ID);
        clusterMonitor.leftCluster(OTHER_NODE_ID);
        reset(xmppServer);

        clusterMonitor.joinedCluster(OTHER_NODE_ID);

        verify(xmppServer).sendMessageToAdmins(any());
    }

    @Test
    public void whenThisNodeLeavesTheClusterAMessageWillBeSent() {

        clusterMonitor.leftCluster();

        verify(xmppServer).sendMessageToAdmins(any());
    }
}
