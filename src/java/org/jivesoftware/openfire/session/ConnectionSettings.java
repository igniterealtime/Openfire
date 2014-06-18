package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.util.property.BooleanProperty;
import org.jivesoftware.util.property.EnumProperty;
import org.jivesoftware.util.property.IntegerProperty;
import org.jivesoftware.util.property.StringProperty;

import static org.jivesoftware.util.property.Property.of;

public final class ConnectionSettings {

    private ConnectionSettings() {
    }

    public static final class Client {

        public static final BooleanProperty SOCKET_ACTIVE = of("xmpp.socket.plain.active", true);
        public static final IntegerProperty PORT = of("xmpp.socket.plain.port", ConnectionManager.DEFAULT_PORT);
        public static final IntegerProperty IDLE_TIMEOUT = of("xmpp.client.idle", 6*60*1000);
        public static final BooleanProperty KEEP_ALIVE_PING = of("xmpp.client.idle.ping", true);

        public static final EnumProperty<Connection.TLSPolicy> TLS_POLICY =
                of("xmpp.client.tls.policy", Connection.TLSPolicy.optional);
        public static final IntegerProperty OLD_SSLPORT = of("xmpp.socket.ssl.port", ConnectionManager.DEFAULT_SSL_PORT);
        public static final BooleanProperty ENABLE_OLD_SSLPORT = of("xmpp.socket.ssl.active", false);
        public static final StringProperty AUTH_PER_CLIENTCERT_POLICY = of("xmpp.client.cert.policy", "disabled");

        public static final EnumProperty<Connection.CompressionPolicy> COMPRESSION_SETTINGS =
                of("xmpp.client.compression.policy", Connection.CompressionPolicy.optional);
        public static final StringProperty LOGIN_ALLOWED = of("xmpp.client.login.allowed", "");
        public static final StringProperty LOGIN_ANONYM_ALLOWED = of("xmpp.client.login.allowedAnonym", "");

        private Client() {
        }
    }

    public static final class Server {

        public static final BooleanProperty SOCKET_ACTIVE = of("xmpp.server.socket.active", true);
        public static final IntegerProperty PORT = of("xmpp.server.socket.port", ConnectionManager.DEFAULT_SERVER_PORT);
        public static final IntegerProperty REMOTE_SERVER_PORT = of("xmpp.server.socket.remotePort", ConnectionManager.DEFAULT_SERVER_PORT);
        public static final IntegerProperty SOCKET_READ_TIMEOUT = of("xmpp.server.read.timeout", 120000);

        public static final IntegerProperty QUEUE_MAX_THREADS = of("xmpp.server.outgoing.max.threads", 20);
        public static final IntegerProperty QUEUE_SIZE = of("xmpp.server.outgoing.queue", 50);

        public static final BooleanProperty DIALBACK_ENABLED = of("xmpp.server.dialback.enabled", true);
        public static final BooleanProperty TLS_ENABLED = of("xmpp.server.tls.enabled", true);
        public static final BooleanProperty TLS_ACCEPT_SELFSIGNED_CERTS = of("xmpp.server.certificate.accept-selfsigned", false);
        public static final BooleanProperty TLS_CERTIFICATE_VERIFY = of("xmpp.server.certificate.verify", true);
        public static final BooleanProperty TLS_CERTIFICATE_VERIFY_VALIDITY = of("xmpp.server.certificate.verify.validity", true);
        public static final BooleanProperty TLS_CERTIFICATE_ROOT_VERIFY = of("xmpp.server.certificate.verify.root", true);
        public static final BooleanProperty TLS_CERTIFICATE_CHAIN_VERIFY = of("xmpp.server.certificate.verify.chain", true);

        public static final EnumProperty<Connection.CompressionPolicy> COMPRESSION_SETTINGS =
                of("xmpp.server.compression.policy", Connection.CompressionPolicy.disabled);

        public static final EnumProperty<RemoteServerManager.PermissionPolicy> PERMISSION_SETTINGS = of("xmpp.server.permission", RemoteServerManager.PermissionPolicy.blacklist);

        private Server() {
        }
    }

    public static final class Multiplex {
        public static final BooleanProperty SOCKET_ACTIVE = of("xmpp.multiplex.socket.active", false);
        public static final IntegerProperty PORT = of("xmpp.multiplex.socket.port", ConnectionManager.DEFAULT_MULTIPLEX_PORT);

        public static final EnumProperty<Connection.TLSPolicy> TLS_POLICY =
                of("xmpp.multiplex.tls.policy", Connection.TLSPolicy.disabled);
        public static final EnumProperty<Connection.CompressionPolicy> COMPRESSION_SETTINGS =
                of("xmpp.multiplex.compression.policy", Connection.CompressionPolicy.disabled);

        private Multiplex() {
        }
    }

    public static final class Component {
        public static final BooleanProperty SOCKET_ACTIVE = of("xmpp.component.socket.active", false);
        public static final IntegerProperty PORT = of("xmpp.component.socket.port", ConnectionManager.DEFAULT_COMPONENT_PORT);
    }
}
