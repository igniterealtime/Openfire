package org.jivesoftware.openfire.session;

/**
 * Holds a (possibly authenticated) domain pair.
 */
public class DomainPair {
    private final String local;
    private final String remote;

    public DomainPair(String local, String remote) {
        this.local = local;
        this.remote = remote;
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object other) {
        if (other instanceof DomainPair) {
            DomainPair domainPair = (DomainPair)other;
            return domainPair.local.equals(this.local) && domainPair.remote.equals(this.remote);
        }
        return false;
    }

    public String toString() {
        return "{" + local + " -> " + remote + "}";
    }

    public String getLocal() {
        return local;
    }

    public String getRemote() {
        return remote;
    }
}
