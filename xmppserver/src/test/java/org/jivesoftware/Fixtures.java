/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.jivesoftware.database.ConnectionProvider;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.spi.*;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.JiveProperties;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@SuppressWarnings({"WeakerAccess"})
public final class Fixtures {

    public static final String XMPP_DOMAIN = "test.xmpp.domain";
    public static final String HOSTNAME = XMPP_DOMAIN; // Make hostname be XMPP Domain name, to avoid the need for DNS SRV lookups

    private Fixtures() {
    }

    /**
     * Disables the persistence (of properties) to the database, setting a dummy database connection provider, unless
     * another provider has explicitly been set.
     */
    public static void disableDatabasePersistence() {
        JiveProperties.disableDatabasePersistence();
        // The persistence of JiveProperties implicitly initializes the DbConnectionManager. Do that here, to avoid issues.
        if (DbConnectionManager.getConnectionProvider() == null) {
            DbConnectionManager.setConnectionProvider(new DummyConnectionProvider());
        }
        // The following allows JiveGlobals to persist
        JiveGlobals.setXMLProperty("setup", "true");
        // The following speeds up tests by avoiding DB retries
        JiveGlobals.setXMLProperty("database.maxRetries", "0");
        JiveGlobals.setXMLProperty("database.retryDelay", "0");
        clearExistingProperties();
    }

    /**
     * Reconfigures the Openfire home directory to the blank test one. This allows {@link JiveGlobals#setProperty(String, String)} etc.
     * to work (and persist) in test classes without errors being displayed to stderr. Ideally should be called in a
     * {@link org.junit.BeforeClass} method.
     */
    public static void reconfigureOpenfireHome() throws Exception {
        final URL configFile = ClassLoader.getSystemResource("conf/openfire.xml");
        if (configFile == null) {
            throw new IllegalStateException("Unable to read openfire.xml file; does conf/openfire.xml exist in the test classpath, i.e. test/resources?");
        }
        final Path openfireHome = Paths.get(configFile.toURI()).getParent().getParent();
        JiveGlobals.setHomePath(openfireHome);
    }

    /**
     * As {@link #reconfigureOpenfireHome()} allows properties to persist, this method clears all existing properties
     * (both XML and 'database') to ensure clean test output. Ideally should be called in a {@link org.junit.Before} method.
     */
    public static void clearExistingProperties() {
        JiveGlobals.getXMLPropertyNames().stream()
            .filter(name -> !"setup".equals(name))
            .filter(name -> !"database.maxRetries".equals(name))
            .filter(name -> !"database.retryDelay".equals(name))
            .forEach(JiveGlobals::deleteXMLProperty);
        JiveGlobals.getPropertyNames()
            .forEach(JiveGlobals::deleteProperty);
    }

    /**
     * As {@link #reconfigureOpenfireHome()} allows properties to persist, this method clears all existing properties
     * (both XML and 'database') to ensure clean test output. Ideally should be called in a {@link org.junit.Before} method.
     *
     * @param except properties that are not cleared if they are set.
     */
    public static void clearExistingProperties(Set<String> except) {
        JiveGlobals.getXMLPropertyNames().stream()
            .filter(name -> !"setup".equals(name))
            .filter(name -> !"database.maxRetries".equals(name))
            .filter(name -> !"database.retryDelay".equals(name))
            .filter(name -> !except.contains(name))
            .forEach(JiveGlobals::deleteXMLProperty);
        JiveGlobals.getPropertyNames().stream()
            .filter(name -> !except.contains(name))
            .forEach(JiveGlobals::deleteProperty);
    }

    public static XMPPServer mockXMPPServer() {
        final XMPPServer xmppServer = mock(XMPPServer.class, withSettings().lenient());
        doAnswer(invocationOnMock -> {
            final JID jid = invocationOnMock.getArgument(0);
            return jid.getDomain().equals(XMPP_DOMAIN);
        }).when(xmppServer).isLocal(any(JID.class));

        doAnswer(invocationOnMock -> new JID(invocationOnMock.getArgument(0), XMPP_DOMAIN, invocationOnMock.getArgument(1)))
            .when(xmppServer).createJID(any(String.class), nullable(String.class));
        doAnswer(invocationOnMock -> new JID(invocationOnMock.getArgument(0), XMPP_DOMAIN, invocationOnMock.getArgument(1), invocationOnMock.getArgument(2)))
            .when(xmppServer).createJID(any(String.class), nullable(String.class), any(Boolean.class));
        doReturn(mockXMPPServerInfo()).when(xmppServer).getServerInfo();
        doReturn(mockIQRouter()).when(xmppServer).getIQRouter();
        doReturn(mockConnectionManager()).when(xmppServer).getConnectionManager();
        doReturn(mockSessionManager()).when(xmppServer).getSessionManager();

        return xmppServer;
    }

    public static XMPPServerInfo mockXMPPServerInfo() {
        final XMPPServerInfo xmppServerInfo = mock(XMPPServerInfo.class, withSettings().lenient());
        doReturn(XMPP_DOMAIN).when(xmppServerInfo).getXMPPDomain();
        doReturn(HOSTNAME).when(xmppServerInfo).getHostname();
        return xmppServerInfo;
    }

    public static IQRouter mockIQRouter() {
        final IQRouter iqRouter = mock(IQRouter.class, withSettings().lenient());
        return iqRouter;
    }

    public static SessionManager mockSessionManager() {
        final SessionManager sessionManager = mock(SessionManager.class, withSettings().lenient());
        when(sessionManager.nextStreamID()).thenReturn(new BasicStreamIDFactory().createStreamID());
        return sessionManager;
    }

    public static ConnectionManager mockConnectionManager() {
        final ConnectionManager connectionManager = mock(ConnectionManagerImpl.class, withSettings().lenient());
        doReturn(mockConnectionListener()).when(connectionManager).getListener(any(ConnectionType.class), anyBoolean());
        return connectionManager;
    }

    public static ConnectionListener mockConnectionListener() {
        final ConnectionListener connectionListener = mock(ConnectionListener.class, withSettings().lenient());
        doReturn(mockConnectionConfiguration()).when(connectionListener).generateConnectionConfiguration();
        return connectionListener;
    }

    public static ConnectionConfiguration mockConnectionConfiguration() {
        final ConnectionConfiguration connectionListener = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(connectionListener).getTlsPolicy();
        return connectionListener;
    }

    public static RoutingTable mockRoutingTable() {
        return  mock(RoutingTable.class, withSettings().lenient());
    }

    public static class StubUserProvider implements UserProvider {
        final Map<String, User> users = new HashMap<>();

        @Override
        public User loadUser(final String username) throws UserNotFoundException {
            return Optional.ofNullable(users.get(username)).orElseThrow(UserNotFoundException::new);
        }

        @Override
        public User createUser(final String username, final String password, final String name, final String email) throws UserAlreadyExistsException {
            if (users.containsKey(username)) {
                throw new UserAlreadyExistsException();
            }
            final User user = mock(User.class, withSettings().lenient());
            doReturn(username).when(user).getUsername();
            doReturn(name).when(user).getName();
            doReturn(email).when(user).getEmail();
            doReturn(new Date()).when(user).getCreationDate();
            doReturn(new Date()).when(user).getModificationDate();
            users.put(username, user);
            return user;
        }

        @Override
        public void deleteUser(final String username) {
            users.remove(username);
        }

        @Override
        public int getUserCount() {
            return users.size();
        }

        @Override
        public Collection<User> getUsers() {
            return users.values();
        }

        @Override
        public Collection<String> getUsernames() {
            return users.keySet();
        }

        @Override
        public Collection<User> getUsers(final int startIndex, final int numResults) {
            return null;
        }

        @Override
        public void setName(final String username, final String name) throws UserNotFoundException {
            final User user = loadUser(username);
            doReturn(name).when(user).getName();
        }

        @Override
        public void setEmail(final String username, final String email) throws UserNotFoundException {
            final User user = loadUser(username);
            doReturn(email).when(user).getEmail();
        }

        @Override
        public void setCreationDate(final String username, final Date creationDate) throws UserNotFoundException {
            final User user = loadUser(username);
            doReturn(new Date()).when(user).getCreationDate();
        }

        @Override
        public void setModificationDate(final String username, final Date modificationDate) throws UserNotFoundException {
            final User user = loadUser(username);
            doReturn(new Date()).when(user).getModificationDate();
        }

        @Override
        public Set<String> getSearchFields() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<User> findUsers(final Set<String> fields, final String query) throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<User> findUsers(final Set<String> fields, final String query, final int startIndex, final int numResults) throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public boolean isNameRequired() {
            return false;
        }

        @Override
        public boolean isEmailRequired() {
            return false;
        }
    }

    public static IQ iqFrom(final String stanza) {
        try {
            final SAXReader reader = new SAXReader();
            final Document document = reader.read(new ByteArrayInputStream(stanza.getBytes(StandardCharsets.UTF_8)));
            return new IQ(document.getRootElement());
        } catch (final DocumentException e) {
            throw new IllegalArgumentException("The supplied input did not contain a well-formed XML document", e);
        }
    }

    public static class DummyConnectionProvider implements ConnectionProvider {

        @Override
        public boolean isPooled()
        {
            return false;
        }

        @Override
        public java.sql.Connection getConnection() throws SQLException
        {
            return null;
        }

        @Override
        public void start()
        {}

        @Override
        public void restart()
        {}

        @Override
        public void destroy()
        {}
    }

    /**
     * Self-Signed expired X509 certificate for testing purposes (expired 17th June 2023)
     */
    public static final String expiredX509Certificate
        = "-----BEGIN CERTIFICATE-----\n" +
        "MIICsjCCAZoCCQDsFzeWUN/PbjANBgkqhkiG9w0BAQsFADAbMRkwFwYDVQQDDBB0\n" +
        "ZXN0LnhtcHAuZG9tYWluMB4XDTIzMDYxNjEzMzE0OFoXDTIzMDYxNzEzMzE0OFow\n" +
        "GzEZMBcGA1UEAwwQdGVzdC54bXBwLmRvbWFpbjCCASIwDQYJKoZIhvcNAQEBBQAD\n" +
        "ggEPADCCAQoCggEBAKxaCt4vSjqzXwCfui+S0KjnQrxVagDKJOHbyhkxTbRROJYz\n" +
        "7SmdAUVHCcFlugOk7UhxTBZ7hHeC3DQTvqilwISRsgyOM8MSAXKV6lvWu2WDRI7s\n" +
        "LRg5o6r23Me/kiSMXGpzaWitxMOgZWxYJlLb7CfIJwN16F6UvKsX6npN5ETnvzkV\n" +
        "PZNgoWvy/TX2QlPJTiukX0a4FbyX6REkAgtI6WfLJm7lqtJZFw7KoX2g8GO+wk3v\n" +
        "akeuuA8OIrtRg5eP5K82a//sF1VoCh9vOryr4mT2BTa8L6x+bF27WMc8+QzXf0JL\n" +
        "s+Iy8J5dneQWEZPK13Eh5doE59EBx33fadT90CsCAwEAATANBgkqhkiG9w0BAQsF\n" +
        "AAOCAQEAQn05d+0QjKH5osqz7ZKm7wle/pF1KkOmCD9lfU1+Iu02Adz2nUx+WTaH\n" +
        "dtB8MtkLQFMcqz6oYquD0ruE3KUvj/A5fmir8wz0m9MG/i3QNrytWepv4vlrcmMr\n" +
        "yfZLwSrR3UNcNYk9W01/xjQVgH2JsF1B1nbn7eJt0mzr0arHp7VjtjdDJIfkZEEh\n" +
        "5ZpOERffIINoEptoKMCfjbcp2+PLZ1TjL6MxtrVs+TQmX4i9wL03uNgItbFqYeYP\n" +
        "RVeb9OaNj4NRMZB49D/jIaqmQWZi6u2ooOQv6AlyzeKInWm+aDmxCAX4IZAod18/\n" +
        "1hI2qIN8Xj9GUT7wldL368Dq1fUI5g==\n" +
        "-----END CERTIFICATE-----\n";

    /**
     * Private key for {@link #expiredX509Certificate} for testing purposes
     */
    public static final String privateKeyForExpiredCert
        = "-----BEGIN PRIVATE KEY-----\n" +
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCsWgreL0o6s18A\n" +
        "n7ovktCo50K8VWoAyiTh28oZMU20UTiWM+0pnQFFRwnBZboDpO1IcUwWe4R3gtw0\n" +
        "E76opcCEkbIMjjPDEgFylepb1rtlg0SO7C0YOaOq9tzHv5IkjFxqc2lorcTDoGVs\n" +
        "WCZS2+wnyCcDdehelLyrF+p6TeRE5785FT2TYKFr8v019kJTyU4rpF9GuBW8l+kR\n" +
        "JAILSOlnyyZu5arSWRcOyqF9oPBjvsJN72pHrrgPDiK7UYOXj+SvNmv/7BdVaAof\n" +
        "bzq8q+Jk9gU2vC+sfmxdu1jHPPkM139CS7PiMvCeXZ3kFhGTytdxIeXaBOfRAcd9\n" +
        "32nU/dArAgMBAAECggEAfE6lMAMjoprklmqdutpFLM0/UN66Cb/CQjRn2yu4Q6mh\n" +
        "CpSBJVZcKD9IRfi85Qv7KBivLDgCHsB/WgAzryd9ZyA+LtgRdUxzRtXhpkOF/X1j\n" +
        "4UFudN59sT1Dl74QBdRGg3CiQiGynPX+sNoTKgf8l+TAXrqX0j+spConr/amARuF\n" +
        "xpvg+OSmZXCwzzLQbUEsigEoeF6ePBLjhbwFwtwbyuMqivLEZWGp/CudhNWIsuwq\n" +
        "aCUQ9Pz8VtcfJOabvRUykeLxJBglhjhIASYqR1OysLsoqSLzEC1cBaWqD3aRr4xL\n" +
        "ZGlgnAHOuU9DwO5qLdrGpLRd/acCzNkR0ojoqLFIAQKBgQDUcOS0dy5kWIpz6ECY\n" +
        "HIJyipjDGcy6VtTXmh0uKDmxJPWCAwPl6xYmF/pLQDzCw2knHrF/bSx0uTw7jUh/\n" +
        "RGnHcqorcUyH+J3JVuoMyoWMuI7o7lIygepJXNqd9dNiGgYvhumCM4Y6YQJ7AsFE\n" +
        "FilHBNBteMDl4OM3dG7S60ldAQKBgQDPsNlMUU6OkDfH4/4nWFk9nlQEt1r52lmW\n" +
        "/yRmwFKJRGDYcjjndMeBQZEMTJUsWH4s0/QwmXxMXrR5FbKIK/nQVdfG8TrAAZ6R\n" +
        "Jdt9o3pmjBINHd5dgnHBXtiK3sD/GJwtFn83yLOK9drhoMiI94yc1tgS6vn1ZTvs\n" +
        "lhvunt4xKwKBgDYWoERKa+dkm6uzIG8aIyRioU5bTULMRNi4BmHwH/A4RsHZXq61\n" +
        "UihUxodOTaoQ8r7hE7Qr6bu2Rd2rtR+iHYSIb0csS5368MGIfYLQNXyEqO4pb4go\n" +
        "h6wyFf9NzYoWsih7owxhbfWDKYyEQQzCz7OjSCX3LrXYskE2RdkxyrYBAoGBAMwY\n" +
        "24G/CPbaTKa3q2PY02HVPHWiBdowtAfJ1WjQKIvSUWWC4d66iO/BkhvHCnUYxW2i\n" +
        "IH695kNacfnn05kztfwAz9ol5vkW3k9/J3IQ+9DYZ0jSiFnWPZmsbhoSCxDki11X\n" +
        "lU8pgR7WufEuQsMumdTq4E2+8kIv6LJ3VR2qq2kfAoGBAM1bz/DiXYawNWaE1/tW\n" +
        "2ljpPehXVrTe7IZDPk/L0NnMn+NfxxzHLRtqqrHNGFPwOky/Xncg6yBS46SjyTZR\n" +
        "A7+RhRBDDhV5yY7y/0FYJLKtF0K88s976Z58/3pzD6UIPP2AX3PaQS5aToGGh55Z\n" +
        "7IkCfxQK6EycXiKlfAXQOPdy\n" +
        "-----END PRIVATE KEY-----";
}
