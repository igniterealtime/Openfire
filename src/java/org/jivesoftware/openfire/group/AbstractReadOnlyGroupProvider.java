package org.jivesoftware.openfire.group;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jivesoftware.util.Immutable;
import org.xmpp.packet.JID;

/**
 * Common base class for immutable (read-only) GroupProvider implementations.
 *
 * @author Tom Evans
 */
public abstract class AbstractReadOnlyGroupProvider implements GroupProvider {
	
	// Mutator methods are marked final for read-only group providers

	/**
	 * @throws UnsupportedOperationException
	 */
    public final void addMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Cannot add members to read-only groups");
    }

	/**
	 * @throws UnsupportedOperationException
	 */
    public final void updateMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Cannot update members for read-only groups");
    }

	/**
	 * @throws UnsupportedOperationException
	 */
    public final void deleteMember(String groupName, JID user) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Cannot remove members from read-only groups");
    }

    /**
     * Always true for a read-only provider
     */
    public final boolean isReadOnly() {
        return true;
    }

	/**
	 * @throws UnsupportedOperationException
	 */
    public final Group createGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot create groups via read-only provider");
    }

	/**
	 * @throws UnsupportedOperationException
	 */
    public final void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot remove groups via read-only provider");
    }

	/**
	 * @throws UnsupportedOperationException
	 */
    public final void setName(String oldName, String newName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot modify read-only groups");
    }

	/**
	 * @throws UnsupportedOperationException
	 */
    public final void setDescription(String name, String description) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot modify read-only groups");
    }

    // Search methods may be overridden by read-only group providers
    
    /**
     * Returns true if the provider supports group search capability. This implementation
     * always returns false.
     */
    public boolean isSearchSupported() {
        return false;
    }

    /**
     * Returns a collection of group search results. This implementation
     * returns an empty collection.
     */
    public Collection<String> search(String query) {
    	return Collections.emptyList();
    }

    /**
     * Returns a collection of group search results. This implementation
     * returns an empty collection.
     */
    public Collection<String> search(String query, int startIndex, int numResults) {
    	return Collections.emptyList();
    }

    /**
     * Returns a collection of group search results. This implementation
     * returns an empty collection.
     */
	public Collection<String> search(String key, String value) {
		return Collections.emptyList();
	}

	// Shared group methods may be overridden by read-only group providers

    /**
     * Returns true if the provider supports group sharing. This implementation
     * always returns false.
     */
    public boolean isSharingSupported() {
        return false;
    }

    /**
     * Returns a collection of shared group names. This implementation
     * returns an empty collection.
     */
    public Collection<String> getSharedGroupNames() {
    	return Collections.emptyList();
	}

    /**
     * Returns a collection of shared group names for the given user. This 
     * implementation returns an empty collection.
     */
    public Collection<String> getSharedGroupNames(JID user) {
    	return Collections.emptyList();
	}

    /**
     * Returns a collection of shared public group names. This 
     * implementation returns an empty collection.
     */
	public Collection<String> getPublicSharedGroupNames() {
		return Collections.emptyList();
	}

    /**
     * Returns a collection of groups shared with the given group. This 
     * implementation returns an empty collection.
     */
	public Collection<String> getVisibleGroupNames(String userGroup) {
		return Collections.emptyList();
	}
	
    /**
     * Returns a map of properties for the given group. This 
     * implementation returns an empty immutable map.
     */
	public Map<String, String> loadProperties(Group group) {
		return new Immutable.Map<String,String>();
	}
	
}
