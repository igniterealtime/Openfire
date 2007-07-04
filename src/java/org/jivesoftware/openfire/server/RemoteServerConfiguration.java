package org.jivesoftware.openfire.server;

import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Holds the configuration when connecting to/from a remote server. The configuration specifies
 * if incoming or outgoing connections are allowed to the remote server and the port to use
 * when creating an outgoing connection.
 *
 * @author Gaston Dombiak
 */
public class RemoteServerConfiguration implements Cacheable, Externalizable {

    private String domain;

    private Permission permission;

    private int remotePort;

    public RemoteServerConfiguration(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();            // overhead of object
        size += CacheSizes.sizeOfString(domain);      // domain
        size += CacheSizes.sizeOfInt();               // remotePort
        return size;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, domain);
        ExternalizableUtil.getInstance().writeBoolean(out, permission != null);
        if (permission != null) {
            ExternalizableUtil.getInstance().writeInt(out, permission.ordinal());
        }
        ExternalizableUtil.getInstance().writeInt(out, remotePort);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        domain = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            permission = Permission.values()[ExternalizableUtil.getInstance().readInt(in)];
        }
        remotePort = ExternalizableUtil.getInstance().readInt(in);
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
