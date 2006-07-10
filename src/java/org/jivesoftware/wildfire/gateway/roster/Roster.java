package org.jivesoftware.wildfire.gateway.roster;


import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.wildfire.gateway.Gateway;

/**
 * Roster maintains the list of <code>ForeignContact</code>s that are related
 * to a particular <code>JID</code>
 * 
 * @author Noah Campbell
 * @version 1.0
 * 
 * @see AbstractForeignContact
 * @see org.xmpp.packet.JID
 * @deprecated
 */
public class Roster implements Serializable {
    
    
    /**
     * The serialVersionUID.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * The foreignContacts.
     *
     * @see java.util.Map
     * @see AbstractForeignContact
     */
    private Map<String, AbstractForeignContact> foreignContacts = new HashMap<String, AbstractForeignContact>();

    /**
     * Return the foreign contact based on the legacy id.  A ForeignContact will always
     * be returned.  If the foreignId does not exist in the Roster, than a new one will be 
     * created.
     * 
     * @param foreignId The legacy contact id.
     * @param gateway The gateway that this foreign contact came from.
     * @return ForeignContact
     */
    public ForeignContact getForeignContact(String foreignId, Gateway gateway) {
        
        AbstractForeignContact fc = this.foreignContacts.get(foreignId);
        if(fc == null)  {
//            fc = new ForeignContact(foreignId, new Status(), gateway);
            this.foreignContacts.put(foreignId, fc);
        }
        return fc;
    }

    /**
     * @return allForeignContacts a <code>Collection</code> of <code>ForeignContact</code>s.
     */
    public Collection<AbstractForeignContact> getAll() {
        return Collections.unmodifiableCollection(this.foreignContacts.values());
    }
    
}
