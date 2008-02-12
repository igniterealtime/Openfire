package org.jivesoftware.openfire.component;

/**
 * Holds the configuration for external components that want to connect to this server. The
 * configuration specifies if the external component is allowed to connect to the server as well
 * as the shared secret between the server and the component. If no secret or configuration was
 * defined then the default shared secret will be used.
 *
 * @author Gaston Dombiak
 */
public class ExternalComponentConfiguration {

    private String subdomain;

    private Permission permission;

    private String secret;

    public ExternalComponentConfiguration(String subdomain, Permission permission, String secret) {
        this.subdomain = subdomain;
        this.permission = permission;
        this.secret = secret;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public Permission getPermission() {
        return permission;
    }

    void setPermission(Permission permission) {
        this.permission = permission;
    }

    public String getSecret() {
        return secret;
    }

    void setSecret(String secret) {
        this.secret = secret;
    }

    public enum Permission {
        /**
         * The XMPP entity is allowed to connect to the server.
         */
        allowed,

        /**
         * The XMPP entity is NOT allowed to connect to the server.
         */
        blocked;
    }
}
