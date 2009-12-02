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

package org.jivesoftware.openfire.sip.log;

import java.sql.SQLException;
import java.util.Date;

import org.dom4j.Element;
import org.jivesoftware.openfire.sip.calllog.CallLog;
import org.jivesoftware.openfire.sip.calllog.CallLogDAO;
import org.jivesoftware.openfire.sip.calllog.CallLogExtension;
import org.jivesoftware.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;

/**
 * Implements a LogListener.
 * Log every call events of SIP users that are using Spark SIP plugin
 *
 * @author Thiago Rocha Camargo
 */
public class LogListenerImpl implements LogListener {

	private static final Logger Log = LoggerFactory.getLogger(LogListenerImpl.class);
	
    ComponentManager componentManager = null;

    public LogListenerImpl(ComponentManager componentmanager) {
        this.componentManager = componentmanager;
    }

    public IQ logReceived(IQ iq) {

        String username = iq.getTo().toBareJID().split("@")[0];

        if (username != null) {

            CallLog callLog = new CallLog(username);
            Element pe = iq.getChildElement().element("callLog");

            if (pe != null) {

                Element numA = pe.element("numA");
                Element numB = pe.element("numB");
                Element duration = pe.element("duration");
                Element type = pe.element("type");

                callLog.setNumA((numA != null) ? numA.getTextTrim() : "");
                callLog.setNumB((numB != null) ? numB.getTextTrim() : "");
                callLog.setDateTime(new Date().getTime());
                callLog.setDuration((duration != null) ? Integer.parseInt(duration.getText()) : 0);
                if (type != null && "loss".equals(type.getTextTrim())) {
                    // Backwards compatibility change
                    type.setText("missed");
                }
                callLog.setType((type != null) ? CallLog.Type.valueOf(type.getTextTrim()) : CallLog.Type.dialed);

                try {
                    CallLogDAO.insert(callLog);
                } catch (SQLException e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }
        iq.setType(IQ.Type.result);

        iq.deleteExtension(CallLogExtension.ELEMENT_NAME, CallLogExtension.NAMESPACE);

        Element childElement = iq.getChildElement();
        if (childElement != null) {
            Element childElementCopy = childElement.createCopy();
            iq.setChildElement(childElementCopy);
        }
        return iq;
    }

    public ComponentManager getComponentManager() {
        return this.componentManager;
    }

}
