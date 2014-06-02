package org.jivesoftware.openfire.session;

public final class ConnectionSettings {

    private ConnectionSettings() {
    }

    public static final class Client {

        public static final String TLS_POLICY = "xmpp.client.tls.policy";

        public static final String COMPRESSION_SETTINGS = "xmpp.client.compression.policy";
        public static final String LOGIN_ALLOWED = "xmpp.client.login.allowed";
        public static final String LOGIN_ANONYM_ALLOWED = "xmpp.client.login.allowedAnonym";

        private Client() {
        }
    }

    public static final class Server {

        public static final String SOCKET_ACTIVE = "xmpp.server.socket.active";
        public static final String PORT = "xmpp.server.socket.remotePort";
        public static final String SOCKET_READ_TIMEOUT = "xmpp.server.read.timeout";

        public static final String QUEUE_MAX_THREADS = "xmpp.server.outgoing.max.threads";
        public static final String QUEUE_SIZE = "xmpp.server.outgoing.queue";

        public static final String DIALBACK_ENABLED = "xmpp.server.dialback.enabled";
        public static final String TLS_ENABLED = "xmpp.server.tls.enabled";
        public static final String TLS_ACCEPT_SELFSIGNED_CERTS = "xmpp.server.certificate.accept-selfsigned";
        public static final String TLS_CERTIFICATE_VERIFY = "xmpp.server.certificate.verify";
        public static final String TLS_CERTIFICATE_VERIFY_VALIDITY = "xmpp.server.certificate.verify.validity";
        public static final String TLS_CERTIFICATE_ROOT_VERIFY = "xmpp.server.certificate.verify.root";
        public static final String TLS_CERTIFICATE_CHAIN_VERIFY = "xmpp.server.certificate.verify.chain";

        public static final String COMPRESSION_SETTINGS = "xmpp.server.compression.policy";

        public static final String PERMISSION_SETTINGS = "xmpp.server.permission";

        private Server() {
        }
    }

    public static final class Multiplex {
        public static final String TLS_POLICY = "xmpp.multiplex.tls.policy";
        public static final String COMPRESSION_SETTINGS = "xmpp.multiplex.compression.policy";

        private Multiplex() {
        }
    }
}
