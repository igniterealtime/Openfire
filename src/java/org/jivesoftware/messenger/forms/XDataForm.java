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
