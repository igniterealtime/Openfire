/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
