/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.handler;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests that verify the functionality as implemented in {@link IQRegisterHandler}.
 */
public class IQRegisterHandlerTest
{
    private static final String PREFIX = "<iq type='set' id='test' from='alice@" + Fixtures.XMPP_DOMAIN + "/res' to='" + Fixtures.XMPP_DOMAIN + "'><query xmlns='jabber:iq:register'>";
    private static final String SUFFIX = "</query></iq>";

    private UserProvider originalUserProvider;
    private UserManager userManager;
    private User user;
    private ClientSession session;
    private IQRegisterHandler handler;
    private IQDiscoInfoHandler discoInfoHandler;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("provider.user.className", Fixtures.StubUserProvider.class.getName());

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);
        originalUserProvider = UserManager.getUserProvider();

        user = mock(User.class, withSettings().lenient());
        doReturn("alice").when(user).getUsername();
        doReturn("alice@example.com").when(user).getEmail();
        doReturn("Alice").when(user).getName();
        // Like a real user, reflect a name change in subsequent reads.
        doAnswer(invocation -> {
            doReturn(invocation.getArgument(0)).when(user).getName();
            return null;
        }).when(user).setName(any());

        userManager = mock(UserManager.class, withSettings().lenient());
        doReturn(user).when(userManager).getUser("alice");
        doReturn(user).when(userManager).createUser(anyString(), anyString(), any(), any());
        doReturn(userManager).when(xmppServer).getUserManager();
        doReturn(mock(RosterManager.class, withSettings().lenient())).when(xmppServer).getRosterManager();

        session = mock(ClientSession.class, withSettings().lenient());
        doReturn(true).when(session).isAuthenticated();
        doReturn("alice").when(session).getUsername();
        doReturn(new JID("alice", Fixtures.XMPP_DOMAIN, "res")).when(session).getAddress();
        final SessionManager sessionManager = xmppServer.getSessionManager();
        doReturn(session).when(sessionManager).getSession(any(JID.class));

        discoInfoHandler = mock(IQDiscoInfoHandler.class, withSettings().lenient());
        doReturn(discoInfoHandler).when(xmppServer).getIQDiscoInfoHandler();

        handler = new IQRegisterHandler();
        handler.initialize(xmppServer);
        handler.setInbandRegEnabled(true);
        handler.setCanChangePassword(true);
    }

    @AfterEach
    public void tearDown()
    {
        UserManager.setProvider(originalUserProvider);
        Fixtures.clearExistingProperties();
    }

    /**
     * Passes the stanza to the handler, and returns the reply that was delivered to the session. Replies must be
     * delivered directly to the session, as a session that has not yet authenticated can't be reached by routing.
     */
    private IQ process(final String stanza) throws Exception
    {
        assertNull(handler.handleIQ(Fixtures.iqFrom(stanza)), "Expected the reply to be delivered directly to the session.");
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(session).process(captor.capture());
        return (IQ) captor.getValue();
    }

    private static void assertError(final PacketError.Condition expected, final IQ reply)
    {
        assertEquals(IQ.Type.error, reply.getType(), "Expected an error, but got: " + reply);
        assertEquals(expected, reply.getError().getCondition());
    }

    private static void assertResult(final IQ reply)
    {
        assertEquals(IQ.Type.result, reply.getType(), "Expected a result, but got: " + reply);
    }

    private static String form(final String fields)
    {
        return "<x xmlns='jabber:x:data' type='submit'><field var='FORM_TYPE' type='hidden'><value>jabber:iq:register</value></field>" + fields + "</x>";
    }

    // ----- Account creation (unauthenticated) -----

    /** OF-3382: a refused registration must get a response. */
    @Test
    public void testCreateWithEmptyPassword() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username>bob</username><password/>" + SUFFIX));
        verify(userManager, never()).createUser(anyString(), anyString(), any(), any());
    }

    /** OF-3382 */
    @Test
    public void testCreateWithMissingPassword() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username>bob</username>" + SUFFIX));
    }

    /** OF-3382 */
    @Test
    public void testCreateWhenInbandRegistrationDisabled() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        handler.setInbandRegEnabled(false);
        assertError(PacketError.Condition.service_unavailable, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
        verify(userManager, never()).createUser(anyString(), anyString(), any(), any());
    }

    @Test
    public void testCreate() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertResult(process(PREFIX + "<username>bob</username><password>secret</password><email>bob@example.com</email>" + SUFFIX));
        verify(userManager).createUser("bob", "secret", null, "bob@example.com");
    }

    /** OF-3387 */
    @Test
    public void testCreateWithFormWithoutUsernameField() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + form("<field var='password'><value>secret</value></field>") + SUFFIX));
        verify(userManager, never()).createUser(anyString(), anyString(), any(), any());
    }

    /** OF-3387 */
    @Test
    public void testCreateWithFormWithEmptyUsernameField() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + form("<field var='username'/><field var='password'><value>secret</value></field>") + SUFFIX));
        verify(userManager, never()).createUser(anyString(), anyString(), any(), any());
    }

    // ----- Account creation on behalf of others (authenticated) -----

    /** OF-3381 */
    @Test
    public void testAuthenticatedCannotCreateAnotherAccount() throws Exception
    {
        assertError(PacketError.Condition.forbidden, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
        verify(userManager, never()).createUser(anyString(), anyString(), any(), any());
    }

    // ----- Account deletion -----

    /** OF-3383 */
    @Test
    public void testRemoveWithOtherChildElements() throws Exception
    {
        assertError(PacketError.Condition.bad_request, process(PREFIX + "<remove/><username>alice</username><password>secret</password>" + SUFFIX));
        verify(userManager, never()).deleteUser(any());
    }

    /** OF-3384 */
    @Test
    public void testRemoveByUnregisteredEntity() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.registration_required, process(PREFIX + "<remove/>" + SUFFIX));
        verify(userManager, never()).deleteUser(any());
    }

    /** OF-3388 */
    @Test
    public void testRemoveWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<remove/>" + SUFFIX));
        verify(userManager, never()).deleteUser(any());
    }

    // ----- Self-update -----

    /** OF-3385 */
    @Test
    public void testPasswordChangeWithoutUsername() throws Exception
    {
        assertError(PacketError.Condition.bad_request, process(PREFIX + "<password>newpass456</password>" + SUFFIX));
        verify(user, never()).setPassword(anyString());
    }

    @Test
    public void testPasswordChange() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
        verify(user).setPassword("newpass456");
    }

    /** OF-3386 */
    @Test
    public void testNameOnlyUpdateLeavesEmailUnchanged() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
        verify(user).setName("Alice Liddell");
        verify(user, never()).setEmail(any());
        verify(user, never()).setPassword(anyString());
    }

    /** OF-3386 */
    @Test
    public void testFormPasswordChangeLeavesEmailAndNameUnchanged() throws Exception
    {
        assertResult(process(PREFIX + form("<field var='username'><value>alice</value></field><field var='password'><value>newpass456</value></field>") + SUFFIX));
        verify(user).setPassword("newpass456");
        verify(user, never()).setEmail(any());
        verify(user, never()).setName(any());
    }

    /** OF-3386 */
    @Test
    public void testEmptyEmailClearsEmail() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><email/>" + SUFFIX));
        verify(user).setEmail(isNull());
        verify(user, never()).setName(any());
    }

    /** OF-3386 */
    @Test
    public void testEmptyNameClearsName() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><name/>" + SUFFIX));
        verify(user).setName(isNull());
        verify(user, never()).setEmail(any());
    }

    /** OF-3386 */
    @Test
    public void testEmptyPasswordLeavesPasswordUnchanged() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><password/><name>Alice Liddell</name>" + SUFFIX));
        verify(user, never()).setPassword(anyString());
        verify(user).setName("Alice Liddell");
    }

    /** OF-3386 */
    @Test
    public void testEmptyRequiredNameIsRejectedBeforeAnythingChanges() throws Exception
    {
        UserManager.setProvider(new Fixtures.StubUserProvider() {
            @Override
            public boolean isNameRequired()
            {
                return true;
            }
        });

        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username>alice</username><password>newpass456</password><name/>" + SUFFIX));
        verify(user, never()).setPassword(anyString());
        verify(user, never()).setName(any());
    }

    /**
     * OF-3390: naming the session's own account in a form that is equivalent after nodeprep is a self-update.
     */
    @ParameterizedTest
    @CsvSource({
        "strasse, straße",
        "alice, \uFF21\uFF2C\uFF29\uFF23\uFF25", // fullwidth
        "alice, alice\u00AD",                        // soft hyphen
        "alice, ALICE"
    })
    public void testSelfUpdateWithEquivalentUsername(final String account, final String requested) throws Exception
    {
        final User target = mock(User.class, withSettings().lenient());
        doReturn(account).when(target).getUsername();
        doReturn(account).when(session).getUsername();
        doReturn(target).when(userManager).getUser(account);

        assertResult(process(PREFIX + "<username>" + requested + "</username><password>newpass456</password>" + SUFFIX));
        verify(target).setPassword("newpass456");
        verify(userManager, never()).createUser(anyString(), anyString(), any(), any());
    }

    // ----- Service discovery -----

    private boolean advertisesRegistration()
    {
        final Iterator<String> features = handler.getFeatures();
        while (features.hasNext()) {
            if ("jabber:iq:register".equals(features.next())) {
                return true;
            }
        }
        return false;
    }

    /** OF-3391 */
    @Test
    public void testAdvertisedWhenEnabled()
    {
        assertTrue(advertisesRegistration());
    }

    /** OF-3391: every jabber:iq:register request is refused when both settings are off. */
    @Test
    public void testNotAdvertisedWhenBothDisabled()
    {
        handler.setInbandRegEnabled(false);
        handler.setCanChangePassword(false);
        assertFalse(advertisesRegistration());
    }

    /** OF-3391 */
    @Test
    public void testAdvertisedWhenOnlyInbandRegistrationEnabled()
    {
        handler.setCanChangePassword(false);
        assertTrue(advertisesRegistration());
    }

    /** OF-3391: clients use the feature to determine if password changes are possible. */
    @Test
    public void testAdvertisedWhenOnlyPasswordChangeEnabled()
    {
        handler.setInbandRegEnabled(false);
        assertTrue(advertisesRegistration());
    }

    /** OF-3391: a read-only provider refuses every set, so neither operation is available. */
    @Test
    public void testNotAdvertisedWhenUserProviderIsReadOnly()
    {
        UserManager.setProvider(new Fixtures.StubUserProvider() {
            @Override
            public boolean isReadOnly()
            {
                return true;
            }
        });
        assertFalse(advertisesRegistration());
    }

    /** OF-3391: the feature follows runtime changes of the settings. */
    @Test
    public void testFeatureIsRemovedWhenBothSettingsAreDisabled()
    {
        handler.setInbandRegEnabled(false);
        verify(discoInfoHandler, never()).removeServerFeature("jabber:iq:register");
        handler.setCanChangePassword(false);
        verify(discoInfoHandler).removeServerFeature("jabber:iq:register");
    }

    /** OF-3391 */
    @Test
    public void testFeatureIsAddedWhenASettingIsEnabled()
    {
        handler.setInbandRegEnabled(false);
        handler.setCanChangePassword(false);
        clearInvocations(discoInfoHandler);

        handler.setCanChangePassword(true);

        verify(discoInfoHandler).addServerFeature("jabber:iq:register");
        verify(discoInfoHandler, never()).removeServerFeature(anyString());
    }

    // ----- Settings -----

    /** OF-3388 (a): self-updates are not governed by 'register.inband'. */
    @Test
    public void testSelfUpdateWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertResult(process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
        verify(user).setName("Alice Liddell");
    }

    /** OF-3388 (a) */
    @Test
    public void testFormPasswordChangeWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertResult(process(PREFIX + form("<field var='username'><value>alice</value></field><field var='password'><value>newpass456</value></field>") + SUFFIX));
        verify(user).setPassword("newpass456");
    }

    /** OF-3388 (b): name/email updates are governed by 'register.password'. OF-3392: refused with not-allowed. */
    @Test
    public void testNameUpdateWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
        verify(user, never()).setName(any());
    }

    /** OF-3392: XEP-0077 3.3, the server does not allow password changes. */
    @Test
    public void testPasswordChangeWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
        verify(user, never()).setPassword(anyString());
    }

    /** OF-3388: reading your own registration is governed by 'register.password'. */
    @Test
    public void testAuthenticatedGetWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        final IQ reply = process("<iq type='get' id='test' from='alice@" + Fixtures.XMPP_DOMAIN + "/res' to='" + Fixtures.XMPP_DOMAIN + "'><query xmlns='jabber:iq:register'/></iq>");
        assertResult(reply);
        assertNotNull(reply.getChildElement().element("registered"));
    }

    /** OF-3388 */
    @Test
    public void testAuthenticatedGetWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.forbidden, process("<iq type='get' id='test' from='alice@" + Fixtures.XMPP_DOMAIN + "/res' to='" + Fixtures.XMPP_DOMAIN + "'><query xmlns='jabber:iq:register'/></iq>"));
    }

    /** OF-3388: the registration form is governed by 'register.inband'. OF-3389: refused with service-unavailable. */
    @Test
    public void testUnauthenticatedGetWhenInbandRegistrationDisabled() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        handler.setInbandRegEnabled(false);
        assertError(PacketError.Condition.service_unavailable, process("<iq type='get' id='test' from='alice@" + Fixtures.XMPP_DOMAIN + "/res' to='" + Fixtures.XMPP_DOMAIN + "'><query xmlns='jabber:iq:register'/></iq>"));
    }
}
