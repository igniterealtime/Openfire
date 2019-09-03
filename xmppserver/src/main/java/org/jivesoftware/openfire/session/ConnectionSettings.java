package org.jivesoftware.openfire.session;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.jivesoftware.util.SystemProperty;

public final class ConnectionSettings {

    private ConnectionSettings() {
    }

    public static final class Client {

        public static final String SOCKET_ACTIVE = "xmpp.socket.plain.active";
        public static final String PORT = "xmpp.socket.plain.port";
        public static final SystemProperty<Duration> IDLE_TIMEOUT_PROPERTY = SystemProperty.Builder.ofType(Duration.class)
            .setKey("xmpp.client.idle")
            .setDefaultValue(Duration.ofMinutes(6))
            .setMinValue(Duration.ofMillis(-1))
            .setChronoUnit(ChronoUnit.MILLIS)
            .setDynamic(Boolean.TRUE)
            .build();
        /**
         * @deprecated in favour of {@link #IDLE_TIMEOUT_PROPERTY}
         */
        @Deprecated
        public static final String IDLE_TIMEOUT = IDLE_TIMEOUT_PROPERTY.getKey();
        public static final SystemProperty<Boolean> KEEP_ALIVE_PING_PROPERTY = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.client.idle.ping")
            .setDefaultValue(Boolean.TRUE)
            .setDynamic(Boolean.TRUE)
            .build();
        /**
         * @deprecated in favour of {@link #KEEP_ALIVE_PING_PROPERTY}
         */
        @Deprecated
        public static final String KEEP_ALIVE_PING = KEEP_ALIVE_PING_PROPERTY.getKey();

        public static final String TLS_POLICY = "xmpp.client.tls.policy";
        public static final String OLD_SSLPORT = "xmpp.socket.ssl.port";
        public static final SystemProperty<Boolean> ENABLE_OLD_SSLPORT_PROPERTY = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.socket.ssl.active")
            .setDefaultValue(Boolean.TRUE)
            .setDynamic(Boolean.FALSE)
            .build();
        /**
         * @deprecated in favour of {@link #ENABLE_OLD_SSLPORT_PROPERTY}
         */
        @Deprecated
        public static final String ENABLE_OLD_SSLPORT = ENABLE_OLD_SSLPORT_PROPERTY.getKey();
        public static final String AUTH_PER_CLIENTCERT_POLICY = "xmpp.client.cert.policy";

        public static final String COMPRESSION_SETTINGS = "xmpp.client.compression.policy";
        public static final String LOGIN_ALLOWED = "xmpp.client.login.allowed";
        public static final String LOGIN_BLOCKED = "xmpp.client.login.blocked";
        public static final String LOGIN_ANONYM_ALLOWED = "xmpp.client.login.allowedAnonym";

        public static final String MAX_THREADS = "xmpp.client.processing.threads";
        public static final String MAX_READ_BUFFER = "xmpp.client.maxReadBufferSize";

        public static final String MAX_THREADS_SSL = "xmpp.client_ssl.processing.threads";
        public static final String MAX_READ_BUFFER_SSL = "xmpp.client_ssl.maxReadBufferSize";
        public static final String TLS_ALGORITHM = "xmpp.socket.ssl.algorithm";
    }

    public static final class Server {

        public static final String SOCKET_ACTIVE = "xmpp.server.socket.active";
        public static final String PORT = "xmpp.server.socket.port";
        public static final String OLD_SSLPORT = "xmpp.server.socket.ssl.port";
        public static final String ENABLE_OLD_SSLPORT = "xmpp.server.socket.ssl.active";
        public static final String REMOTE_SERVER_PORT = "xmpp.server.socket.remotePort";
        public static final String SOCKET_READ_TIMEOUT = "xmpp.server.read.timeout";

        public static final String QUEUE_MAX_THREADS = "xmpp.server.outgoing.max.threads";
        public static final String QUEUE_SIZE = "xmpp.server.outgoing.queue";

        public static final String DIALBACK_ENABLED = "xmpp.server.dialback.enabled";
        public static final String TLS_POLICY = "xmpp.server.tls.policy";

        /**
         * @deprecated Replaced by #TLS_POLICY
         */
        @Deprecated
        public static final String TLS_ENABLED = "xmpp.server.tls.enabled";

        public static final String TLS_ACCEPT_SELFSIGNED_CERTS = "xmpp.server.certificate.accept-selfsigned";
        public static final String TLS_CERTIFICATE_VERIFY = "xmpp.server.certificate.verify";
        public static final String TLS_CERTIFICATE_VERIFY_VALIDITY = "xmpp.server.certificate.verify.validity";
        public static final String TLS_CERTIFICATE_ROOT_VERIFY = "xmpp.server.certificate.verify.root";
        public static final String TLS_CERTIFICATE_CHAIN_VERIFY = "xmpp.server.certificate.verify.chain";
        public static final String TLS_ON_PLAIN_DETECTION_ALLOW_NONDIRECTTLS_FALLBACK = "xmpp.server.tls.on-plain-detection-allow-nondirecttls-fallback";

        public static final String COMPRESSION_SETTINGS = "xmpp.server.compression.policy";

        public static final String PERMISSION_SETTINGS = "xmpp.server.permission";
        public static final String AUTH_PER_CLIENTCERT_POLICY = "xmpp.server.cert.policy";
        public static final String ALLOW_ANONYMOUS_OUTBOUND_DATA = "xmpp.server.allow-anonymous-outbound-data";
    }

    public static final class Multiplex {
        public static final String SOCKET_ACTIVE = "xmpp.multiplex.socket.active";
        public static final String PORT = "xmpp.multiplex.socket.port";

        public static final String TLS_POLICY = "xmpp.multiplex.tls.policy";
        public static final String COMPRESSION_SETTINGS = "xmpp.multiplex.compression.policy";

        public static final String OLD_SSLPORT = "xmpp.multiplex.ssl.port";
        public static final String ENABLE_OLD_SSLPORT = "xmpp.multiplex.ssl.active";
        public static final String MAX_THREADS ="xmpp.multiplex.processing.threads";
        public static final String MAX_THREADS_SSL = "xmpp.multiplex.ssl.processing.threads";
        public static final String AUTH_PER_CLIENTCERT_POLICY = "xmpp.multiplex.cert.policy" ;
    }

    public static final class Component {
        public static final String SOCKET_ACTIVE = "xmpp.component.socket.active";
        public static final String PORT = "xmpp.component.socket.port";
        public static final String OLD_SSLPORT = "xmpp.component.ssl.port";
        public static final SystemProperty<Boolean> ENABLE_OLD_SSLPORT_PROPERTY = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.component.ssl.active")
            .setDefaultValue(Boolean.TRUE)
            .setDynamic(Boolean.FALSE)
            .build();
        /**
         * @deprecated in favour of {@link #ENABLE_OLD_SSLPORT_PROPERTY}
         */
        @Deprecated
        public static final String ENABLE_OLD_SSLPORT = ENABLE_OLD_SSLPORT_PROPERTY.getKey();
        public static final String MAX_THREADS = "xmpp.component.processing.threads";
        public static final String MAX_THREADS_SSL = "xmpp.component.ssl.processing.threads";
        public static final String AUTH_PER_CLIENTCERT_POLICY = "xmpp.component.cert.policy";
        public static final String TLS_POLICY = "xmpp.component.tls.policy";
        public static final String COMPRESSION_SETTINGS = "xmpp.component.compression.policy";
    }
}
