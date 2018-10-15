package org.jivesoftware.openfire.group;

import java.nio.charset.StandardCharsets;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * This class is designed to identify and manage custom JIDs
 * that represent Groups (rather than Users or Components). 
 * 
 * The node for a GroupJID is the group name encoded as base32hex. 
 * This allows us to preserve special characters and upper/lower casing
 * within the group name. The encoded group name is valid according to
 * the RFC6122 rules for a valid node and does not require further
 * JID escaping.
 * 
 * We use an MD5 hash of the group name as the resource value to help 
 * distinguish Group JIDs from regular JIDs in the local domain when 
 * they are persisted in the DB or over the network.
 * 
 * @author Tom Evans
 *
 */
public class GroupJID extends JID {
    
    private static final Logger Log = LoggerFactory.getLogger(GroupJID.class);
    private static final long serialVersionUID = 5681300465012974014L;
    
    private transient String groupName;

    /**
     * Construct a JID representing a Group.
     * 
     * @param name A group name for the local domain
     */
    public GroupJID(String name) {
        super(encodeNode(name), 
                XMPPServer.getInstance().getServerInfo().getXMPPDomain(), 
                StringUtils.hash(name), 
                true);
        groupName = name;
    }

    /**
     * Construct a JID representing a Group from a regular JID. This constructor is
     * private because it is used only from within this class after the source JID
     * has been validated.
     * 
     * @param source A full JID representing a group
     * @see GroupJID#fromString
     */
    private GroupJID(JID source) {
        // skip stringprep for the new group JID, since it has already been parsed
        super(source.getNode(), source.getDomain(), source.getResource(), true);
    }

    /**
     * Returns the group name corresponding to this JID.
     *
     * @return The name for the corresponding group
     */
    public String getGroupName() {
        // lazy instantiation
        if (groupName == null) {
            groupName = decodeNode(getNode());
        }
        return groupName;
    }

    /**
     * Override the base class implementation to retain the resource
     * identifier for group JIDs.
     *
     * @return This JID, as a group JID
     */
    @Override
    public JID asBareJID() {
        return this;
    }

    /**
     * Override the base class implementation to retain the resource
     * identifier for group JIDs.
     *
     * @return The full JID rendered as a string
     */
    @Override
    public String toBareJID() {
        return this.toString();
    }
    
    @Override
    public int compareTo(JID jid) {
        // Comparison order is domain, node, resource.
        int compare = getDomain().compareTo(jid.getDomain());
        if (compare == 0) {
            String otherNode = jid.getNode();
            compare = otherNode == null ? 1 : getGroupName().compareTo(otherNode);
        }
        if (compare == 0) {
            compare = jid.getResource() == null ? 0 : -1;
        }
        return compare;
    }

    
    /**
     * Encode the given group name in base32hex (UTF-8). This encoding
     * is valid according to the nodeprep profile of stringprep
     * (RFC6122, Appendix A) and needs no further escaping.
     * 
     * @param name A group name
     * @return The encoded group name
     */
    private static String encodeNode(String name) {
        return StringUtils.encodeBase32(name);
    }

    /**
     * Decode the given group name from base32hex (UTF-8). 
     * 
     * @param name A group name, encoded as base32hex
     * @return The group name
     */
    private static String decodeNode(String node) {
        return new String(StringUtils.decodeBase32(node), StandardCharsets.UTF_8);
    }

    /**
     * Check a JID to determine whether it represents a group. If the given
     * JID is an instance of this class, it is a group JID. Otherwise,
     * calculate the hash to determine whether the JID can be resolved to
     * a group.
     *
     * @param jid A JID, possibly representing a group
     * @return true if the given jid represents a group in the local domain
     */
    public static boolean isGroup(JID jid) {
        try {
            return isGroup(jid, false);
        } catch (GroupNotFoundException gnfe) {
            // should not happen because we do not validate the group exists
            Log.error("Unexpected group validation", gnfe);
            return false;
        }
    }

    /**
     * Check a JID to determine whether it represents a group. If the given
     * JID is an instance of this class, it is a group JID. Otherwise,
     * calculate the hash to determine whether the JID can be resolved to
     * a group. This method also optionally validates that the corresponding 
     * group actually exists in the local domain.
     *
     * @param jid A JID, possibly representing a group
     * @param groupMustExist If true, validate that the corresponding group actually exists
     * @return true if the given jid represents a group in the local domain
     * @throws GroupNotFoundException The JID represents a group, but the group does not exist
     */
    public static boolean isGroup(JID jid, boolean groupMustExist) throws GroupNotFoundException {
        boolean isGroup = false;
        String groupName = null, node = jid.getNode();
        if (node != null) {
            
            isGroup = (jid instanceof GroupJID) ? true : 
                jid.getResource() != null &&
                StringUtils.isBase32(node) &&
                StringUtils.hash(groupName = decodeNode(node)).equals(jid.getResource());
            
            if (isGroup && groupMustExist) {
                Log.debug("Validating group: " + jid);
                if (XMPPServer.getInstance().isLocal(jid)) {
                    GroupManager.getInstance().getGroup(groupName);
                } else {
                    isGroup = false;  // not in the local domain
                }
            }
        }
        return isGroup;
    }

    /**
     * Returns a JID from the given JID. If the JID represents a group,
     * returns an instance of this class. Otherwise returns the given JID.
     *
     * @param jid A JID, possibly representing a group
     * @return A new GroupJID if the given JID represents a group, or the given JID
     */
    public static JID fromJID(JID jid) {
        if (jid instanceof GroupJID || jid.getResource() == null || jid.getNode() == null) {
            return jid;
        } else {
            return (isGroup(jid)) ? new GroupJID(jid) : jid;
        }
    }

    /**
     * Creates a JID from the given string. If the string represents a group,
     * return an instance of this class. Otherwise returns a regular JID.
     *
     * @param jid A JID, possibly representing a group
     * @return A JID with a type appropriate to its content
     * @throws IllegalArgumentException the given string is not a valid JID
     */
    public static JID fromString(String jid) {
        Log.debug("Parsing JID from string: " + jid);
        return fromJID(new JID(jid));
    }
    
}
