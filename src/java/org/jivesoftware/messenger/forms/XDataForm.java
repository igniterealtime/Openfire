/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.forms;

import org.jivesoftware.messenger.XMPPFragment;
import org.dom4j.Element;

/**
 * The standard XMPP DataForm packet.
 *
 * @author Gaston Dombiak
 */
public interface XDataForm extends DataForm, XMPPFragment {

    /**
     * Obtain the data form as an XML DOM element.
     *
     * @return The data form as an XML DOM element.
     */
    public Element asXMLElement();
}
