/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;


/**
 * Represents a single XMPP delivery node, identified by
 * a XMPPAddress.<p>
 *
 * A normal full address is of the form: user@server/resource. However
 * a 'bare address' is only composed of user@server and omits or ignores
 * the resource. The bare address is useful for referring to a generic
 * delivery node, without specifying the resource (allowing the normal
 * resource priority routing to determine where it is being delivered).
 *
 * @author Iain Shigeoka
 */
public class XMPPAddress implements Cacheable {

    private transient String cached;
    private transient String cachedBare;
    private transient String cachedPrep;
    private transient String cachedBarePrep;

    /**
     * The user name associated with this address.
     */
    private String name;
    private transient String namePrep;

    /**
     * The name of the XMPP host domain (server host name).
     */
    private String host;
    private transient String hostPrep;

    /**
     * The address resource attribute typically identifying
     * specific delivery points associated with a user account
     */
    private String resource;
    private transient String resourcePrep;

    /**
     * Dirty flag indicating that the cached address string must
     * be regenerated because a set*() method
     * was called.
     */
    private transient boolean dirty = true;

    /**
     * Create a local XMPPAddress.
     *
     * @param username the user name for the id.
     * @param resource the resource for the id.
     */
    public XMPPAddress(String username, String host, String resource) {
        setHost(host);
        setName(username);
        setResource(resource);
    }

    /**
     * Create a XMPP ID by parsing the given address string.
     *
     * @param address the address string to parse.
     */
    public static XMPPAddress parseJID(String address) {
        String name;
        String host;
        String resource;
        if (address == null) {
            return null;
        }
        else {
            address = address.trim();
            int atPos = address.indexOf('@');
            int slashPos = address.indexOf('/');
            if (atPos != -1) {
                name = address.substring(0, atPos);
            }
            else {
                name = "";
            }
            if (slashPos == -1 || slashPos == address.length() - 1) {
                slashPos = address.length();
                resource = "";
            }
            else {
                resource = address.substring(slashPos + 1);
            }
            host = address.substring(atPos + 1, slashPos);
        }
        return new XMPPAddress(name, host, resource);
    }

    /**
     * Returns the XMPP address with any resource information removed. For example,
     * for the address "matt@jivesoftware.com/Smack", "matt@jivesoftware.com" would
     * be returned.
     *
     * @param XMPPAddress the XMPP address.
     * @return the bare XMPP address without resource information.
     */
    public static String parseBareAddress(String XMPPAddress) {
        if (XMPPAddress == null) {
            return null;
        }
        int slashIndex = XMPPAddress.indexOf("/");
        if (slashIndex < 0) {
            return XMPPAddress;
        }
        else if (slashIndex == 0) {
            return "";
        }
        else {
            return XMPPAddress.substring(0, slashIndex);
        }
    }

    /**
     * Obtain the XMPP name for this address.
     *
     * @return the name or null if no id has been set or no name is defined (server ID).
     */
    public String getName() {
        return name;
    }

    /**
     * Obtain the XMPP nameprep name for this address.
     *
     * @return The name or null if no id has been set or no name is defined (server ID).
     */
    public String getNamePrep() {
        if (name != null) {
            if (namePrep == null) {
                namePrep = NodePrep.prep(name);
            }
        }
        return namePrep;
    }

    /**
     * Set the XMPP name for this address.
     *
     * @param name the name.
     */
    public void setName(String name) {
        dirty = true;
        if (name == null) {
            this.name = null;
        }
        else {
            this.name = name.trim();
        }
        namePrep = null;
    }

    /**
     * Obtain the XMPP host domain for this address.
     *
     * @return the name of the XMPP domain (server host name) or null if undefined.
     */
    public String getHost() {
        return host;
    }

    /**
     * Obtain the XMPP host domain for this address.
     *
     * @return the name of the XMPP domain (server host name) or null if undefined
     */
    public String getHostPrep() {
        if (host != null) {
            if (hostPrep == null) {
                hostPrep = NamePrep.prep(host);
            }
        }
        return hostPrep;
    }

    /**
     * Set the XMPP host domain for this address.
     *
     * @param host the name of the XMPP domain (server host name) or null if undefined.
     */
    public void setHost(String host) {
        dirty = true;
        if (host == null) {
            this.host = null;
        }
        else {
            this.host = host.trim();
        }
        hostPrep = null;
    }

    /**
     * Obtain the resource identified by this address.
     *
     * @return The name of the resource or null if none defined.
     */
    public String getResource() {
        return resource;
    }

    /**
     * Obtain the resource identified by this address.
     *
     * @return The name of the resource or null if none defined.
     */
    public String getResourcePrep() {
        if (resource != null) {
            if (resourcePrep == null) {
                resourcePrep = ResourcePrep.prep(resource);
            }
        }
        return resourcePrep;
    }

    /**
     * Set the resource identified by this address.
     *
     * @param resource the name of the resource or null if none defined.
     */
    public void setResource(String resource) {
        dirty = true;
        if (resource == null) {
            this.resource = null;
        }
        else {
            this.resource = resource.trim();
        }
        resourcePrep = null;
    }

    private synchronized void generateCachedJID() {
        dirty = false;
        StringBuffer buf = new StringBuffer();
        StringBuffer bufprep = new StringBuffer();
        if (name != null && !"".equals(name)) {
            bufprep.append(NodePrep.prep(name));
            bufprep.append('@');
            buf.append(name);
            buf.append('@');
        }
        if (host != null) {
            bufprep.append(NamePrep.prep(host));
            buf.append(host);
        }
        if (resource != null && !"".equals(resource)) {
            cachedBarePrep = bufprep.toString();
            cachedBare = buf.toString();
            buf.append('/');
            buf.append(resource);
            bufprep.append('/');
            bufprep.append(ResourcePrep.prep(resource));
            cached = buf.toString();
            cachedPrep = bufprep.toString();
        }
        else {
            if (buf.length() == 0) {
                cached = Integer.toHexString(hashCode());
                cachedBare = cached;
                cachedPrep = cached;
                cachedBarePrep = cached;
            }
            else {
                cached = buf.toString();
                cachedBare = cached;
                cachedPrep = bufprep.toString();
                cachedBarePrep = cachedPrep;
            }
        }
    }

    /**
     * Obtain an easer to read string for this address.
     *
     * @return The XMPP ID as a URI or a simple "XMPPAddress" string if no URI has been set
     */
    public String toString() {
        if (dirty) {
            generateCachedJID();
        }
        return cached;
    }

    /**
     * Obtain the XMPP ID in normal XMPP format.
     */
    public String toStringPrep() {
        if (dirty) {
            generateCachedJID();
        }
        return cachedPrep;
    }

    /**
     * Obtain the XMPP ID in normal XMPP format excluding any resource information.
     *
     * @return the bare jid as a string (no resource)
     */
    public String toBareString() {
        if (dirty) {
            generateCachedJID();
        }
        return cachedBare;
    }

    /**
     * Obtain the XMPP ID in normal XMPP format excluding any resource information.
     *
     * @return the bare jid processed by stringprep and returned as a string (no resource)
     */
    public String toBareStringPrep() {
        if (dirty) {
            generateCachedJID();
        }
        return cachedBarePrep;
    }

    public int getCachedSize() {
        int size = CacheSizes.sizeOfString(name);
        size += CacheSizes.sizeOfString(host);
        size += CacheSizes.sizeOfString(resource);
        return size;
    }

    public boolean equalsBare(XMPPAddress sender) {
        return equal(getHostPrep(), sender.getHostPrep())
                && equal(getNamePrep(), sender.getNamePrep());
    }

    private boolean equal(String lhs, String rhs) {
        boolean equals = true;
        if (lhs == null) {
            if (rhs != null) {
                equals = false;
            }
        }
        else {
            if (!lhs.equals(rhs)) {
                equals = false;
            }
        }
        return equals;
    }

    public boolean equals(Object address) {
        if (address instanceof XMPPAddress) {
            XMPPAddress addr = (XMPPAddress)address;
            if (equalsBare(addr) && equal(resource, addr.resource)) {
                return true;
            }
        }
        return false;
    }

    public int hashCode() {
        if (dirty) {
            generateCachedJID();
        }
        if (cachedPrep == null) {
            return 0;
        }
        else {
            return cachedPrep.hashCode();
        }
    }
}
