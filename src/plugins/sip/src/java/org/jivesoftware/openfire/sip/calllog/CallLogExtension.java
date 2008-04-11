/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.calllog;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.sip.sipaccount.SipComponent;
import org.xmpp.packet.PacketExtension;

/**
 *
 * CallLog packet extension Class.
 *
 * @author Thiago Rocha Camargo
 */
public class CallLogExtension extends PacketExtension {

	public final static String ELEMENT_NAME="callLog";
	public final static String NAMESPACE= SipComponent.NAMESPACE+"/log";

	static{
    registeredExtensions.put(QName.get(ELEMENT_NAME, NAMESPACE), CallLogExtension.class);
	}

	public CallLogExtension(Element e){
		super(e);
	}

    public CallLogExtension createCopy() {
        return new CallLogExtension(this.getElement().createCopy());
    }

}
