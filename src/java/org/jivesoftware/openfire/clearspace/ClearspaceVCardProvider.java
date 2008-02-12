/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.vcard.VCardProvider;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;
import org.dom4j.Element;

/**
 * @author Daniel Henninger
 */
public class ClearspaceVCardProvider implements VCardProvider {
    public Element loadVCard(String username) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Element createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Element updateVCard(String username, Element vCardElement) throws NotFoundException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void deleteVCard(String username) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isReadOnly() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
