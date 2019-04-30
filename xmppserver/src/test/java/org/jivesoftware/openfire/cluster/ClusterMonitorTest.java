package org.jivesoftware.openfire.cluster;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClusterMonitorTest {

    private static final String THIS_HOST_NAME = "test-host-name";
    private static final byte[] OTHER_NODE_ID = new byte[]{0, 1, 2, 3};
    private ClusterMonitor clusterMonitor;
    @Mock private XMPPServer xmppServer;
    @Mock private XMPPServerInfo xmppServerInfo;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
    }

    @Before
    public void setUp() {

        Fixtures.clearExistingProperties();

        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        doReturn(xmppServerInfo).when(xmppServer).getServerInfo();

        doReturn(THIS_HOST_NAME).when(xmppServerInfo).getHostname();

        clusterMonitor = new ClusterMonitor();
        clusterMonitor.initialize(xmppServer);
        clusterMonitor.start();
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
