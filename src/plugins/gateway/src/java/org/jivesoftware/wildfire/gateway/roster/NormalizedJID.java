package org.jivesoftware.wildfire.gateway.roster;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.xmpp.packet.JID;

/**
 * @author Noah Campbell
 *
 */
/**
 * Ignore the resource portion of the JID.
 * 
 * @author Noah Campbell
 *
 */
public final class NormalizedJID implements Serializable {

	/**
	 * The JID.
	 *
	 * @see JID
	 */
	public final String JID;
    
    /**
     * The toStringValue.  Stored to increase efficiency.
     *
     * @see NormalizedJID
     */
    private final String toStringValue;
    
	/**
	 * The serialVersionUID.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a new <code>NormalizedJID</code>
	 * @param jid
	 */
	private NormalizedJID(JID jid) {
		JID = jid.toBareJID();
        toStringValue = JID + " (normalized)";
	}
	

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof NormalizedJID) {
			NormalizedJID jid = (NormalizedJID)obj;
			return JID.equalsIgnoreCase(jid.JID);
		} else if(obj instanceof JID) {
			JID jid = new JID( ((JID)obj).toBareJID() );
			return JID.equalsIgnoreCase(jid.toBareJID());
		} else if( obj instanceof String) {
			JID jid = new JID((String)obj);
			jid = new JID(jid.toBareJID());
			return JID.equalsIgnoreCase(jid.toBareJID());
		} else {
			return super.equals(obj);
		}
	}
    
	/**
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return JID.hashCode();
	}
	
	/**
     * Wrap a <code>JID</code> and return a <code>NormalizedJID</code>.  If the
     * <code>JID</code> has already been wrapped, then the cached version is returned.
     * 
	 * @param jid
	 * @return normalizedJID
	 */
	public static NormalizedJID wrap(JID jid) {
		if(cache.containsKey(jid)) {
			return cache.get(jid);
		} else {
			NormalizedJID nJID = new NormalizedJID(jid);
			cache.put(jid, nJID);
			return nJID;
		}
	}
    
    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return toStringValue;
    }
	
	/**
	 * The cache of <code>NormailzedJID</code>s.
	 *
	 * @see java.util.Map
	 */
	private transient static final Map<JID, NormalizedJID> cache = new ConcurrentHashMap<JID, NormalizedJID>();
	
}
