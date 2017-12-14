package org.jivesoftware.openfire.session;

/**
 * Holds a (possibly authenticated) domain pair.
 */
public class DomainPair implements java.io.Serializable {
    private final String local;
    private final String remote;
    private static final long serialVersionUID = 1L;

    public DomainPair(String local, String remote) {
        this.local = local;
        this.remote = remote;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DomainPair that = (DomainPair) o;

        if (!local.equals(that.local)) return false;
        return remote.equals(that.remote);
    }

    @Override
    public int hashCode() {
        int result = local.hashCode();
        result = 31 * result + remote.hashCode();
        return result;
    }
}
