package org.jivesoftware.openfire.user;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

@RunWith(MockitoJUnitRunner.class)
public class UserManagerTest {

    private static final String REMOTE_XMPP_DOMAIN = "remote.xmpp.domain";
    private static final String USER_ID = "test-user-id";
    private static final String USER_ID_2 = "test-user-id-2";
    private static final JID REMOTE_USER_JID = new JID(USER_ID, REMOTE_XMPP_DOMAIN, null);

    private static final String USER_FOUND_RESULT = "<iq type='result' from='" + REMOTE_USER_JID + "' to='"  + Fixtures.XMPP_DOMAIN + "' id='info1'>\n" +
        "  <query xmlns='http://jabber.org/protocol/disco#info'>\n" +
        "    <identity\n" +
        "        category='account'\n" +
        "        type='registered'/>\n" +
        "  </query>\n" +
        "</iq>";

    private UserManager userManager;
    private IQRouter iqRouter;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
    }

    @Before
    public void setUp() throws Exception {

        Fixtures.clearExistingProperties();

        // Ensure the cache's are cleared
        CacheFactory.createCache("User").clear();
        CacheFactory.createCache("Remote Users Existence").clear();

        // Use the stub user provider, and a very short timeout
        JiveGlobals.setProperty("provider.user.className", Fixtures.StubUserProvider.class.getName());
        JiveGlobals.setProperty("usermanager.remote-disco-info-timeout-seconds", "0");

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        iqRouter = xmppServer.getIQRouter();

        userManager = new UserManager(xmppServer);
        userManager.createUser(USER_ID, "change me", "Test User Name", "test-email@example.com");
        userManager.createUser(USER_ID_2, "change me", "Test User Name 2", "test-email-2@example.com");
        
    }
    
    @Test
    public void canGetUserByUserNameForExistingUsers()  throws Exception{
    	final User result = userManager.getUser(USER_ID);
    	assertThat(result.getUsername(), is(USER_ID));
        assertThat(result.getEmail(), is("test-email@example.com"));
        assertThat(result.getName(), is("Test User Name"));
    }
    
    @Test
    public void getUserNamesWillGetAListOfUserNames()  throws Exception{
    	final Collection<String> result = userManager.getUsernames();
    	assertThat(result.contains(USER_ID), is(true));
        assertThat(result.contains(USER_ID_2), is(true));
        assertThat(result.contains("not exists name"), is(false));
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void deleteInvalidUserWillGetError()  throws Exception{
    	User user = new User("!@#ED",null,null,null,null);	
    	userManager.deleteUser(user);
    }
    
    @Test(expected=UserAlreadyExistsException.class)
    public void createExistingUserWillGetError()  throws Exception{
    	userManager.createUser(USER_ID, "change me", "Test User Name", "test-email@example.com");
    	
    }
    
    @Test
    public void createThenDeleteUserAndVerifyDeletionResults()  throws Exception{
    	userManager.createUser("toBeDeleted", "change me", "toBeDeleted User Name", "toBeDeleted@example.com");
    	int count = userManager.getUserCount();
    	assertThat(count, is(3));
    	Collection<String> usernames = userManager.getUsernames();
    	assertThat(usernames.contains("tobedeleted"), is(true));
    	User user = userManager.getUser("tobedeleted");
    	userManager.deleteUser(user);
    	count = userManager.getUserCount();
    	assertThat(count, is(2));
    	usernames = userManager.getUsernames();
    	assertThat(usernames.contains("tobedeleted"), is(false));
    }
    
    
    
    @Test
    public void verifyUserCountIsTwo()  throws Exception{
    	final int result = userManager.getUserCount();
        assertThat(result, is(2));
    }
    
    
    

    @Test
    public void isRegisteredUserWillReturnTrueForLocalUsers() {
        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, Fixtures.XMPP_DOMAIN, null));
        assertThat(result, is(true));
    }

    @Test
    public void isRegisteredUserFalseWillReturnTrueForLocalUsers() {
        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, Fixtures.XMPP_DOMAIN, null), false);
        assertThat(result, is(true));
    }

    @Test
    public void isRegisteredUserTrueWillReturnTrueForLocalUsers() {
        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, Fixtures.XMPP_DOMAIN, null), true);
        assertThat(result, is(true));
    }

    @Test
    public void isRegisteredUserWillReturnFalseForLocalNonUsers() {
        final boolean result = userManager.isRegisteredUser(new JID("unknown-user", Fixtures.XMPP_DOMAIN, null));
        assertThat(result, is(false));
    }

    @Test
    public void isRegisteredUserFalseWillReturnFalseForLocalNonUsers() {
        final boolean result = userManager.isRegisteredUser(new JID("unknown-user", Fixtures.XMPP_DOMAIN, null), false);
        assertThat(result, is(false));
    }

    @Test
    public void isRegisteredUserTrueWillReturnFalseForLocalNonUsers() {
        final boolean result = userManager.isRegisteredUser(new JID("unknown-user", Fixtures.XMPP_DOMAIN, null), true);
        assertThat(result, is(false));
    }

    @Test
    public void isRegisteredUserWillReturnTrueForRemoteUsers() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            iqListener.get().receivedAnswer(Fixtures.iqFrom(USER_FOUND_RESULT));
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null));

        assertThat(result, is(true));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserFalseWillReturnFalseForRemoteUsers() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            iqListener.get().receivedAnswer(Fixtures.iqFrom(USER_FOUND_RESULT));
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null), false);

        assertThat(result, is(false));
    }

    @Test
    public void isRegisteredUserTrueWillReturnTrueForRemoteUsers() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            iqListener.get().receivedAnswer(Fixtures.iqFrom(USER_FOUND_RESULT));
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null), true);

        assertThat(result, is(true));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserWillReturnFalseForUnknownRemoteUsers() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            final IQ iq = invocationOnMock.getArgument(0);
            final Element childElement = iq.getChildElement();
            final IQ response = IQ.createResultIQ(iq);
            response.setChildElement(childElement.createCopy());
            response.setError(new PacketError(PacketError.Condition.item_not_found, PacketError.Condition.item_not_found.getDefaultType()));
            iqListener.get().receivedAnswer(response);
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null));

        assertThat(result, is(false));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserFalseWillReturnFalseForUnknownRemoteUsers() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            final IQ iq = invocationOnMock.getArgument(0);
            final Element childElement = iq.getChildElement();
            final IQ response = IQ.createResultIQ(iq);
            response.setChildElement(childElement.createCopy());
            response.setError(new PacketError(PacketError.Condition.item_not_found, PacketError.Condition.item_not_found.getDefaultType()));
            iqListener.get().receivedAnswer(response);
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null), false);

        assertThat(result, is(false));
    }

    @Test
    public void isRegisteredUserTrueWillReturnFalseForUnknownRemoteUsers() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            final IQ iq = invocationOnMock.getArgument(0);
            final Element childElement = iq.getChildElement();
            final IQ response = IQ.createResultIQ(iq);
            response.setChildElement(childElement.createCopy());
            response.setError(new PacketError(PacketError.Condition.item_not_found, PacketError.Condition.item_not_found.getDefaultType()));
            iqListener.get().receivedAnswer(response);
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null), true);

        assertThat(result, is(false));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserWillReturnFalseForATimeout() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            final IQ iq = invocationOnMock.getArgument(0);
            iqListener.get().answerTimeout(iq.getID());
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null));

        assertThat(result, is(false));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserTrueWillReturnFalseForATimeout() {

        final AtomicReference<IQResultListener> iqListener = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            final IQResultListener listener = invocationOnMock.getArgument(1);
            iqListener.set(listener);
            return null;
        }).when(iqRouter).addIQResultListener(any(), any(), anyLong());

        doAnswer(invocationOnMock -> {
            final IQ iq = invocationOnMock.getArgument(0);
            iqListener.get().answerTimeout(iq.getID());
            return null;
        }).when(iqRouter).route(any());

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null), true);

        assertThat(result, is(false));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserWillReturnFalseNoAnswer() {

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null));

        assertThat(result, is(false));
        verify(iqRouter).route(any());
    }

    @Test
    public void isRegisteredUserTrueWillReturnFalseNoAnswer() {

        final boolean result = userManager.isRegisteredUser(new JID(USER_ID, REMOTE_XMPP_DOMAIN, null), true);

        assertThat(result, is(false));
        verify(iqRouter).route(any());
    }
}
