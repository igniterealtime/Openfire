package org.jivesoftware.openfire.handler;

import org.dom4j.tree.BaseElement;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class IQBindHandlerTest {
    private final String TEST_XMPP_DOMAIN = "test-domain.com";
    XMPPServer mockServer;
    XMPPServerInfo mockXmppServerInfo;
    LocalClientSession mockLocalClientSession;
    RoutingTableImpl routingTable;
    SessionManager mockSessionManager;
    IQ packet;
    IQBindHandler iQBindHandler;

    @Before
    public void wireMocks() {
        mockServer = mock(XMPPServer.class);
        when(mockServer.getPacketDeliverer()).thenReturn(null);
        when(mockServer.getNodeID()).thenReturn(NodeID.getInstance(new byte[]{0}));

        mockXmppServerInfo = mock(XMPPServerInfo.class);
        when(mockXmppServerInfo.getXMPPDomain()).thenReturn(TEST_XMPP_DOMAIN);
        when(mockServer.getServerInfo()).thenReturn(mockXmppServerInfo);

        mockLocalClientSession = mock(LocalClientSession.class);
        Presence mockPresence = mock(Presence.class);
        when(mockPresence.isAvailable()).thenReturn(true);
        when(mockLocalClientSession.getPresence()).thenReturn(mockPresence);

        routingTable = new RoutingTableImpl();
        routingTable.initialize(mockServer);

        mockSessionManager = mock(SessionManager.class);
        when(mockServer.getSessionManager()).thenReturn(mockSessionManager);
        when(mockServer.getRoutingTable()).thenReturn(routingTable);

        packet = new IQ();
        String resource = "resource";

        BaseElement baseElement = new BaseElement(resource);
        baseElement.add(new BaseElement("resource"));
        packet.setChildElement(resource, resource);
        packet.getChildElement().add(baseElement);

        iQBindHandler = new IQBindHandler();
        iQBindHandler.initialize(mockServer);
    }

    private JID wireJIDTestCase(final String node, final String resource, int sessionConflictLimit) {
        JID jid = new JID(node, TEST_XMPP_DOMAIN, resource, true);

        AuthToken authToken = AuthToken.generateUserToken(jid.toString());

        when(mockLocalClientSession.getAuthToken()).thenReturn(authToken);

        when(mockLocalClientSession.getAddress()).thenReturn(jid);
        routingTable.addClientRoute(jid, mockLocalClientSession);
        when(mockSessionManager.getConflictKickLimit()).thenReturn(sessionConflictLimit);
        when(mockSessionManager.getSession(jid)).thenReturn(mockLocalClientSession);
        return jid;
    }

    @Test
    public void handleIQBind_sessionJidDoesNotMatch_sessionNotFound() throws UnauthorizedException {
        this.wireJIDTestCase("ALL-CAPS-JID", "CAPS-RESOURCE", 1);

        packet.setFrom("Non-Matching-User@test-domain.com/caps-resource");

        IQ handledIQ = iQBindHandler.handleIQ(packet);
        IQ expectedReply = IQ.createResultIQ(packet);

        expectedReply.setError(PacketError.Condition.internal_server_error);
        assertEquals(handledIQ.getError().getCondition(), expectedReply.getError().getCondition());
    }

    @Test
    public void handleIQBind_usernameIsAllCaps_shouldConflictWithMatchingSession() throws UnauthorizedException {
        JID jid = this.wireJIDTestCase("ALL-CAPS-JID", "CAPS-RESOURCE", 1);

        packet.setFrom(jid);

        assertNull(iQBindHandler.handleIQ(packet));

        ArgumentCaptor<IQ> iqArgumentCaptor = ArgumentCaptor.forClass(IQ.class);
        verify(mockLocalClientSession, times(1)).process(iqArgumentCaptor.capture());
        assertEquals(PacketError.Condition.conflict, iqArgumentCaptor.getValue().getError().getCondition());
    }


    @Test
    public void handleIQBind_usernameIsLowercase_shouldConflictWithMatchingSession() throws UnauthorizedException {
        JID jid = this.wireJIDTestCase("all-lowercase-jid", "resource", 1);

        packet.setFrom(jid);

        assertNull(iQBindHandler.handleIQ(packet));

        ArgumentCaptor<IQ> iqArgumentCaptor = ArgumentCaptor.forClass(IQ.class);
        verify(mockLocalClientSession, times(1)).process(iqArgumentCaptor.capture());
        assertEquals(PacketError.Condition.conflict, iqArgumentCaptor.getValue().getError().getCondition());
    }

    @Test
    public void handleIQBind_usernameMixedCase_shouldConflictWithMatchingSession() throws UnauthorizedException {
        JID jid = this.wireJIDTestCase("mIxEd-CaSe-UsErNaMe", "rEsOuRcE", 1);

        packet.setFrom(jid);

        assertNull(iQBindHandler.handleIQ(packet));

        ArgumentCaptor<IQ> iqArgumentCaptor = ArgumentCaptor.forClass(IQ.class);
        verify(mockLocalClientSession, times(1)).process(iqArgumentCaptor.capture());
        assertEquals(PacketError.Condition.conflict, iqArgumentCaptor.getValue().getError().getCondition());
    }

    @Test
    public void handleIQBind_sessionMatchesNeverKickEnabled_shouldConflictWithMatchingSession() throws UnauthorizedException {
        JID jid = this.wireJIDTestCase("mIxEd-CaSe-UsErNaMe", "rEsOuRcE", SessionManager.NEVER_KICK);

        packet.setFrom(jid);

        assertNull(iQBindHandler.handleIQ(packet));

        ArgumentCaptor<IQ> iqArgumentCaptor = ArgumentCaptor.forClass(IQ.class);
        verify(mockLocalClientSession, times(1)).process(iqArgumentCaptor.capture());
        assertEquals(PacketError.Condition.conflict, iqArgumentCaptor.getValue().getError().getCondition());
    }

    @Test
    public void handleIQBind_sessionMatchesHighIncrementConflictCount_shouldConflictWithMatchingSession() throws UnauthorizedException {
        JID jid = this.wireJIDTestCase("mIxEd-CaSe-UsErNaMe", "rEsOuRcE", 0);

        packet.setFrom(jid);
        when(mockLocalClientSession.incrementConflictCount()).thenReturn(10);
        assertNull(iQBindHandler.handleIQ(packet));


        ArgumentCaptor<LocalClientSession> clientSessionArgumentCaptor = ArgumentCaptor.forClass(LocalClientSession.class);
        verify(mockSessionManager, times(1)).removeDetached(clientSessionArgumentCaptor.capture());
        assertEquals(mockLocalClientSession, clientSessionArgumentCaptor.getValue());
        verify(mockLocalClientSession, times(1)).close();

        verify(mockLocalClientSession, times(1)).setAuthToken(any(), any());
    }

    @Test
    public void handleIQBind_exceptionThrownWhileHandlingOldSession_shouldProcessEmptyReply() throws UnauthorizedException {
        JID jid = this.wireJIDTestCase("mIxEd-CaSe-UsErNaMe", "rEsOuRcE", SessionManager.NEVER_KICK);
        when(mockSessionManager.getConflictKickLimit()).thenThrow(RuntimeException.class);
        packet.setFrom(jid);

        assertNull(iQBindHandler.handleIQ(packet));

        verify(mockLocalClientSession, times(1)).process(any(IQ.class));
        verify(mockLocalClientSession, times(1)).setAuthToken(any(), any());
    }

    @Test
    public void handleIQBind_getInfo_shouldReturnInfo() {
        IQHandlerInfo iqHandlerInfo = iQBindHandler.getInfo();
        assertNotNull(iqHandlerInfo);
        assertEquals(iqHandlerInfo.getClass(), IQHandlerInfo.class);
        assertEquals(iqHandlerInfo.getName(), "bind");
        assertEquals(iqHandlerInfo.getNamespace(), "urn:ietf:params:xml:ns:xmpp-bind");
    }

}
