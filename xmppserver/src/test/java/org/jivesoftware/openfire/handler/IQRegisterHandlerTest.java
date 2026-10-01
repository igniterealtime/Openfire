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

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.xmpp.packet.*;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private RosterManager rosterManager;
    private SessionManager sessionManager;

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
        rosterManager = mock(RosterManager.class, withSettings().lenient());
        doReturn(rosterManager).when(xmppServer).getRosterManager();

        session = mock(ClientSession.class, withSettings().lenient());
        doReturn(true).when(session).isAuthenticated();
        doReturn("alice").when(session).getUsername();
        doReturn(new JID("alice", Fixtures.XMPP_DOMAIN, "res")).when(session).getAddress();
        sessionManager = xmppServer.getSessionManager();
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
        verify(session, description("A reply must be delivered by passing it to the session, exactly once (OF-3382)")).process(captor.capture());
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
        verify(userManager, never().description("A registration without a usable password must not create an account (XEP-0077 3.1)")).createUser(anyString(), anyString(), any(), any());
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
        verify(userManager, never().description("An account must not be created when in-band registration is disabled (OF-3388)")).createUser(anyString(), anyString(), any(), any());
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
        verify(userManager, never().description("A registration without a username must not create an account (OF-3387)")).createUser(anyString(), anyString(), any(), any());
    }

    /** OF-3387 */
    @Test
    public void testCreateWithFormWithEmptyUsernameField() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + form("<field var='username'/><field var='password'><value>secret</value></field>") + SUFFIX));
        verify(userManager, never().description("A registration with an empty username must not create an account (OF-3387)")).createUser(anyString(), anyString(), any(), any());
    }

    // ----- Account creation on behalf of others (authenticated) -----

    /** OF-3381 */
    @Test
    public void testAuthenticatedCannotCreateAnotherAccount() throws Exception
    {
        assertError(PacketError.Condition.forbidden, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
        verify(userManager, never().description("An authenticated user must not be able to create an account for someone else (OF-3381)")).createUser(anyString(), anyString(), any(), any());
    }

    // ----- Account deletion -----

    /** OF-3383 */
    @Test
    public void testRemoveWithOtherChildElements() throws Exception
    {
        assertError(PacketError.Condition.bad_request, process(PREFIX + "<remove/><username>alice</username><password>secret</password>" + SUFFIX));
        verify(userManager, never().description("A <remove/> that has other child elements is ambiguous, and must not delete the account (OF-3383)")).deleteUser(any());
    }

    /** OF-3384 */
    @Test
    public void testRemoveByUnregisteredEntity() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.registration_required, process(PREFIX + "<remove/>" + SUFFIX));
        verify(userManager, never().description("An entity that is not registered has no account to delete (OF-3384)")).deleteUser(any());
    }

    /** OF-3388 */
    @Test
    public void testRemoveWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<remove/>" + SUFFIX));
        verify(userManager, never().description("An account must not be deleted when in-band registration is disabled (OF-3388)")).deleteUser(any());
    }

    // ----- Self-update -----

    /** OF-3385 */
    @Test
    public void testPasswordChangeWithoutUsername() throws Exception
    {
        assertError(PacketError.Condition.bad_request, process(PREFIX + "<password>newpass456</password>" + SUFFIX));
        verify(user, never().description("A password change that does not say which account it is for must not be applied (OF-3385)")).setPassword(anyString());
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
        verify(user, never().description("An update that omits the email address must leave the stored one as it is (OF-3386)")).setEmail(any());
        verify(user, never().description("An update that omits the password must not change it (OF-3386)")).setPassword(anyString());
    }

    /** OF-3386 */
    @Test
    public void testFormPasswordChangeLeavesEmailAndNameUnchanged() throws Exception
    {
        assertResult(process(PREFIX + form("<field var='username'><value>alice</value></field><field var='password'><value>newpass456</value></field>") + SUFFIX));
        verify(user).setPassword("newpass456");
        verify(user, never().description("A password change in a data form must not wipe the email address (OF-3386)")).setEmail(any());
        verify(user, never().description("A password change in a data form must not wipe the name (OF-3386)")).setName(any());
    }

    /** OF-3386 */
    @Test
    public void testEmptyEmailClearsEmail() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><email/>" + SUFFIX));
        verify(user, description("An email address that is explicitly empty must clear the stored one (OF-3386)")).setEmail(isNull());
        verify(user, never().description("Only the field that was submitted may change: the name must be left as it is (OF-3386)")).setName(any());
    }

    /** OF-3386 */
    @Test
    public void testEmptyNameClearsName() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><name/>" + SUFFIX));
        verify(user, description("A name that is explicitly empty must clear the stored one (OF-3386)")).setName(isNull());
        verify(user, never().description("Only the field that was submitted may change: the email address must be left as it is (OF-3386)")).setEmail(any());
    }

    /** OF-3386 */
    @Test
    public void testEmptyPasswordLeavesPasswordUnchanged() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><password/><name>Alice Liddell</name>" + SUFFIX));
        verify(user, never().description("An empty password must be treated as not provided, and must not be applied (OF-3386)")).setPassword(anyString());
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
        verify(user, never().description("A request with an emptied required name must be rejected before anything changes, including the password (OF-3386)")).setPassword(anyString());
        verify(user, never().description("A required name must not be cleared (OF-3386)")).setName(any());
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
        verify(target, description("A username that is equivalent to the user's own after nodeprep names their own account, so this is a self-update (OF-3390)")).setPassword("newpass456");
        verify(userManager, never().description("A self-update must not be treated as the creation of an account (OF-3390)")).createUser(anyString(), anyString(), any(), any());
    }

    // ----- Error replies -----

    private static void assertDoesNotEchoPassword(final IQ reply)
    {
        assertEquals(IQ.Type.error, reply.getType(), "Expected an error, but got: " + reply);
        assertNull(reply.getChildElement(), "Expected the error not to include the request payload, but got: " + reply);
        assertFalse(reply.toXML().contains("newpass456"), "The password was echoed in: " + reply);
    }

    /** OF-3393: XEP-0077 3.3, the original XML SHOULD NOT be returned in errors for password changes. */
    @Test
    public void testRefusedPasswordChangeDoesNotEchoPassword() throws Exception
    {
        handler.setCanChangePassword(false);
        assertDoesNotEchoPassword(process(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
    }

    /** OF-3393 */
    @Test
    public void testRefusedFormPasswordChangeDoesNotEchoPassword() throws Exception
    {
        handler.setCanChangePassword(false);
        assertDoesNotEchoPassword(process(PREFIX + form("<field var='username'><value>alice</value></field><field var='password'><value>newpass456</value></field>") + SUFFIX));
    }

    /** OF-3393: registration errors echoed the chosen password too. */
    @Test
    public void testRegistrationConflictDoesNotEchoPassword() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        doThrow(new UserAlreadyExistsException()).when(userManager).createUser(anyString(), anyString(), any(), any());
        final IQ reply = process(PREFIX + "<username>bob</username><password>newpass456</password>" + SUFFIX);
        assertError(PacketError.Condition.conflict, reply);
        assertDoesNotEchoPassword(reply);
    }

    /** OF-3393 */
    @Test
    public void testRefusedRegistrationForAnotherUserDoesNotEchoPassword() throws Exception
    {
        assertDoesNotEchoPassword(process(PREFIX + "<username>bob</username><password>newpass456</password>" + SUFFIX));
    }

    /** OF-3393: only requests that contain a password are kept out of the error reply. */
    @Test
    public void testErrorWithoutPasswordIncludesPayload() throws Exception
    {
        handler.setCanChangePassword(false);
        final IQ reply = process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX);
        assertError(PacketError.Condition.not_allowed, reply);
        assertNotNull(reply.getChildElement(), "Expected the request's payload to be included, but got: " + reply);
        assertEquals("Alice Liddell", reply.getChildElement().elementText("name"));
    }

    // ----- Additional coverage -----

    // A session that has not authenticated yet has a placeholder address.
    private static final String UNAUTHENTICATED_GET = "<iq type='get' id='test' from='" + Fixtures.XMPP_DOMAIN + "/stream-id' to='" + Fixtures.XMPP_DOMAIN + "'><query xmlns='jabber:iq:register'/></iq>";

    /** OF-3381: the refusal applies whoever the caller is, including an administrator. */
    @Test
    public void testAdministratorCannotCreateAnotherAccountEither() throws Exception
    {
        AdminManager.getInstance().addAdminAccount(new JID("alice", Fixtures.XMPP_DOMAIN, null));
        try {
            assertError(PacketError.Condition.forbidden, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
            verify(userManager, never().description("An administrator must not be able to create an account for someone else through in-band registration either (OF-3381)")).createUser(anyString(), anyString(), any(), any());
        } finally {
            AdminManager.getInstance().clearAdminUsers();
        }
    }

    @Test
    public void testAnotherAccountWithoutPasswordIsRefused() throws Exception
    {
        assertError(PacketError.Condition.forbidden, process(PREFIX + "<username>bob</username>" + SUFFIX));
        verify(userManager, never().description("An authenticated user must not be able to create an account for someone else, with or without a password (OF-3381)")).createUser(anyString(), anyString(), any(), any());
    }

    @Test
    public void testAnotherAccountIsRefusedWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.forbidden, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
        verify(userManager, never().description("An authenticated user must not be able to create an account for someone else, whatever the settings (OF-3381)")).createUser(anyString(), anyString(), any(), any());
    }

    // Creation

    @Test
    public void testCreateWithName() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertResult(process(PREFIX + "<username>bob</username><password>secret</password><name>Bob Smith</name>" + SUFFIX));
        verify(userManager).createUser("bob", "secret", "Bob Smith", null);
    }

    @Test
    public void testCreateWithForm() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertResult(process(PREFIX + form("<field var='username'><value>bob</value></field><field var='password'><value>secret</value></field><field var='email'><value>bob@example.com</value></field><field var='name'><value>Bob Smith</value></field>") + SUFFIX));
        verify(userManager).createUser("bob", "secret", "Bob Smith", "bob@example.com");
    }

    @Test
    public void testCreateWithBlankUsername() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username/><password>secret</password>" + SUFFIX));
        verify(userManager, never().description("A registration with a blank username must not create an account (OF-3387)")).createUser(anyString(), anyString(), any(), any());
    }

    /** XEP-0077 3.1: a username that is not valid after nodeprep. */
    @Test
    public void testCreateWithInvalidUsername() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        assertError(PacketError.Condition.jid_malformed, process(PREFIX + "<username>b o b</username><password>secret</password>" + SUFFIX));
        verify(userManager, never().description("A username that is not valid after nodeprep must not create an account")).createUser(anyString(), anyString(), any(), any());
    }

    @Test
    public void testCreateWhenUserProviderIsReadOnly() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        doThrow(new UnsupportedOperationException()).when(userManager).createUser(anyString(), anyString(), any(), any());
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
    }

    @Test
    public void testCreateWithInvalidValues() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        doThrow(new IllegalArgumentException("Invalid email")).when(userManager).createUser(anyString(), anyString(), any(), any());
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username>bob</username><password>secret</password><email>nope</email>" + SUFFIX));
    }

    @Test
    public void testCreateWithUnexpectedFailure() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        doThrow(new IllegalStateException("Unexpected")).when(userManager).createUser(anyString(), anyString(), any(), any());
        assertError(PacketError.Condition.internal_server_error, process(PREFIX + "<username>bob</username><password>secret</password>" + SUFFIX));
    }

    // Self-update

    @Test
    public void testEmailIsUpdated() throws Exception
    {
        assertResult(process(PREFIX + "<username>alice</username><email>alice.liddell@example.com</email>" + SUFFIX));
        verify(user).setEmail("alice.liddell@example.com");
        verify(user, never().description("Only the field that was submitted may change: the name must be left as it is (OF-3386)")).setName(any());
        verify(user, never().description("Only the field that was submitted may change: the password must be left as it is (OF-3386)")).setPassword(anyString());
    }

    @Test
    public void testFormEmailIsUpdated() throws Exception
    {
        assertResult(process(PREFIX + form("<field var='username'><value>alice</value></field><field var='email'><value>alice.liddell@example.com</value></field>") + SUFFIX));
        verify(user).setEmail("alice.liddell@example.com");
        verify(user, never().description("Only the field that was submitted may change: the name must be left as it is (OF-3386)")).setName(any());
    }

    @Test
    public void testPasswordChangeWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertResult(process(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
        verify(user, description("Changing your own password is governed by register.password, not register.inband (OF-3388)")).setPassword("newpass456");
    }

    @Test
    public void testFormWithoutUsernameWhenAuthenticated() throws Exception
    {
        assertError(PacketError.Condition.bad_request, process(PREFIX + form("<field var='password'><value>newpass456</value></field>") + SUFFIX));
        verify(user, never().description("A form that does not say which account it is for must not change a password (OF-3385)")).setPassword(anyString());
    }

    @Test
    public void testEmptyRequiredEmailIsRejectedBeforeAnythingChanges() throws Exception
    {
        UserManager.setProvider(new Fixtures.StubUserProvider() {
            @Override
            public boolean isEmailRequired()
            {
                return true;
            }
        });

        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username>alice</username><password>newpass456</password><email/>" + SUFFIX));
        verify(user, never().description("A request with an emptied required email address must be rejected before anything changes, including the password (OF-3386)")).setPassword(anyString());
        verify(user, never().description("A required email address must not be cleared (OF-3386)")).setEmail(any());
    }

    @Test
    public void testSelfUpdateWhenUserProviderIsReadOnly() throws Exception
    {
        doThrow(new UnsupportedOperationException()).when(user).setName(any());
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
    }

    @Test
    public void testSelfUpdateWithInvalidValue() throws Exception
    {
        doThrow(new IllegalArgumentException("Invalid email")).when(user).setEmail(any());
        assertError(PacketError.Condition.not_acceptable, process(PREFIX + "<username>alice</username><email>nope</email>" + SUFFIX));
    }

    @Test
    public void testSelfUpdateOfAnAccountThatNoLongerExists() throws Exception
    {
        doThrow(new UserNotFoundException()).when(userManager).getUser("alice");
        assertError(PacketError.Condition.bad_request, process(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
    }

    @Test
    public void testRequestWithoutSession() throws Exception
    {
        doReturn(null).when(sessionManager).getSession(any(JID.class));
        final IQ reply = handler.handleIQ(Fixtures.iqFrom(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
        assertNotNull(reply, "Without a session there is nothing to deliver to, so the reply is returned.");
        assertError(PacketError.Condition.internal_server_error, reply);
        verify(user, never().description("Without a session the request can't be attributed to an account, so nothing may change")).setPassword(anyString());
    }

    // Retrieving the registration

    @Test
    public void testUnauthenticatedGetReturnsRegistrationForm() throws Exception
    {
        doReturn(false).when(session).isAuthenticated();
        final IQ reply = process(UNAUTHENTICATED_GET);
        assertResult(reply);
        final Element query = reply.getChildElement();
        assertEquals("jabber:iq:register", query.getNamespaceURI());
        assertNotNull(query.element("username"));
        assertNotNull(query.element("password"));
        assertNotNull(query.element("email"));
        assertNotNull(query.element("name"));
        assertNull(query.element("registered"), "An entity that is not registered is not 'registered'.");
        assertNotNull(query.element(QName.get("x", "jabber:x:data")), "Expected a data form.");
    }

    @Test
    public void testAuthenticatedGetReturnsCurrentRegistration() throws Exception
    {
        final IQ reply = process(AUTHENTICATED_GET);
        assertResult(reply);
        final Element query = reply.getChildElement();
        assertNotNull(query.element("registered"));
        assertEquals("alice", query.elementText("username"));
        assertEquals("alice@example.com", query.elementText("email"));
        assertEquals("Alice", query.elementText("name"));
        assertEquals("", query.elementText("password"), "The password must not be returned.");

        final Element form = query.element(QName.get("x", "jabber:x:data"));
        assertNotNull(form);
        for (final Element field : form.elements("field")) {
            switch (field.attributeValue("var")) {
                case "username": assertEquals("alice", field.elementText("value")); break;
                case "email": assertEquals("alice@example.com", field.elementText("value")); break;
                case "name": assertEquals("Alice", field.elementText("value")); break;
                case "password": assertNull(field.element("value"), "The password must not be returned."); break;
                default: break;
            }
        }
    }

    // Removal

    /** XEP-0077 3.2: the account is removed, together with its roster and group memberships, and its sessions are closed. */
    @Test
    public void testRemove() throws Exception
    {
        final ClientSession otherSession = mock(ClientSession.class, withSettings().lenient());
        doReturn(List.of(session, otherSession)).when(sessionManager).getSessions(any(JID.class));
        final GroupManager groupManager = mock(GroupManager.class, withSettings().lenient());

        try (MockedStatic<GroupManager> groups = mockStatic(GroupManager.class)) {
            groups.when(GroupManager::getInstance).thenReturn(groupManager);

            assertResult(process(PREFIX + "<remove/>" + SUFFIX));

            verify(userManager).deleteUser(user);
            verify(rosterManager).deleteRoster(session.getAddress());
            verify(groupManager).deleteUser(user);
        }
        final ArgumentCaptor<StreamError> error = ArgumentCaptor.forClass(StreamError.class);
        verify(session, description("The sessions of a removed account must be closed, as they would otherwise outlive the account")).close(error.capture());
        verify(otherSession, description("This includes the sessions of the account's other resources")).close(any(StreamError.class));
        assertEquals(StreamError.Condition.not_authorized, error.getValue().getCondition());
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
        verify(discoInfoHandler, never().description("jabber:iq:register must stay advertised while one of the settings is enabled (OF-3391)")).removeServerFeature("jabber:iq:register");
        handler.setCanChangePassword(false);
        verify(discoInfoHandler, description("jabber:iq:register must stop being advertised once neither setting is enabled (OF-3391)")).removeServerFeature("jabber:iq:register");
    }

    /** OF-3391 */
    @Test
    public void testFeatureIsAddedWhenASettingIsEnabled()
    {
        handler.setInbandRegEnabled(false);
        handler.setCanChangePassword(false);
        clearInvocations(discoInfoHandler);

        handler.setCanChangePassword(true);

        verify(discoInfoHandler, description("jabber:iq:register must be advertised again once a setting is enabled (OF-3391)")).addServerFeature("jabber:iq:register");
        verify(discoInfoHandler, never().description("Enabling a setting must not remove the feature (OF-3391)")).removeServerFeature(anyString());
    }

    // ----- System properties -----

    /** OF-3394: the settings are declared as system properties, so that the admin console can describe them. */
    @ParameterizedTest
    @ValueSource(strings = {"register.inband", "register.password"})
    public void testSettingIsASystemProperty(final String key)
    {
        final SystemProperty<?> property = SystemProperty.getProperty(key).orElseThrow(() -> new AssertionError("Not a system property: " + key));
        assertEquals(Boolean.TRUE, property.getDefaultValue());
        assertTrue(property.isDynamic());
        assertNotNull(property.getDescription());
        assertFalse(property.getDescription().isBlank(), "Expected a description for " + key);
        assertFalse(property.getDescription().contains("system_property."), "Expected a description for " + key + ", but got: " + property.getDescription());
    }

    /** OF-3394: a change to the property itself is acted on, not just a change made through the setters. */
    @Test
    public void testSettingsFollowPropertyChanges() throws Exception
    {
        JiveGlobals.setProperty("register.password", "false");
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
    }

    /** OF-3394: the advertised feature follows a change to the properties (OF-3391). */
    @Test
    public void testAdvertisedFeatureFollowsPropertyChanges()
    {
        JiveGlobals.setProperty("register.inband", "false");
        JiveGlobals.setProperty("register.password", "false");
        verify(discoInfoHandler, description("The advertised feature must follow a change to the property itself, not only a change made through the setters (OF-3394)")).removeServerFeature("jabber:iq:register");

        clearInvocations(discoInfoHandler);
        JiveGlobals.setProperty("register.password", "true");
        verify(discoInfoHandler, description("The advertised feature must follow a change to the property itself, not only a change made through the setters (OF-3394)")).addServerFeature("jabber:iq:register");
    }

    // ----- Settings -----

    /** OF-3388 (a): self-updates are not governed by 'register.inband'. */
    @Test
    public void testSelfUpdateWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertResult(process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
        verify(user, description("Updating your own name is governed by register.password, not register.inband (OF-3388)")).setName("Alice Liddell");
    }

    /** OF-3388 (a) */
    @Test
    public void testFormPasswordChangeWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        assertResult(process(PREFIX + form("<field var='username'><value>alice</value></field><field var='password'><value>newpass456</value></field>") + SUFFIX));
        verify(user, description("Changing your own password is governed by register.password, not register.inband (OF-3388)")).setPassword("newpass456");
    }

    /** OF-3388 (b): name/email updates are governed by 'register.password'. OF-3392: refused with not-allowed. */
    @Test
    public void testNameUpdateWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>alice</username><name>Alice Liddell</name>" + SUFFIX));
        verify(user, never().description("A name update must be refused when register.password is disabled (OF-3388)")).setName(any());
    }

    /** OF-3392: XEP-0077 3.3, the server does not allow password changes. */
    @Test
    public void testPasswordChangeWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.not_allowed, process(PREFIX + "<username>alice</username><password>newpass456</password>" + SUFFIX));
        verify(user, never().description("A password change must be refused when register.password is disabled (OF-3388)")).setPassword(anyString());
    }

    private static final String AUTHENTICATED_GET = "<iq type='get' id='test' from='alice@" + Fixtures.XMPP_DOMAIN + "/res' to='" + Fixtures.XMPP_DOMAIN + "'><query xmlns='jabber:iq:register'/></iq>";

    /** OF-3388: reading your own registration is allowed when either setting is enabled. */
    @Test
    public void testAuthenticatedGetWhenInbandRegistrationDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        final IQ reply = process(AUTHENTICATED_GET);
        assertResult(reply);
        assertNotNull(reply.getChildElement().element("registered"));
    }

    /** OF-3388 */
    @Test
    public void testAuthenticatedGetWhenPasswordChangeDisabled() throws Exception
    {
        handler.setCanChangePassword(false);
        final IQ reply = process(AUTHENTICATED_GET);
        assertResult(reply);
        assertNotNull(reply.getChildElement().element("registered"));
    }

    /** OF-3388: when both settings are disabled the feature is not supported, see XEP-0077 3.1. */
    @Test
    public void testAuthenticatedGetWhenBothDisabled() throws Exception
    {
        handler.setInbandRegEnabled(false);
        handler.setCanChangePassword(false);
        assertError(PacketError.Condition.service_unavailable, process(AUTHENTICATED_GET));
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
