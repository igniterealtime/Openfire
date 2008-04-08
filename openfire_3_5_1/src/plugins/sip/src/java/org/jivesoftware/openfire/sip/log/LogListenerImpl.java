/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.log;

import org.dom4j.Element;
import org.jivesoftware.openfire.sip.calllog.CallLog;
import org.jivesoftware.openfire.sip.calllog.CallLogDAO;
import org.jivesoftware.openfire.sip.calllog.CallLogExtension;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;

import java.sql.SQLException;
import java.util.Date;

/**
 * Implements a LogListener.
 * Log every call events of SIP users that are using Spark SIP plugin
 *
 * @author Thiago Rocha Camargo
 */
public class LogListenerImpl implements LogListener {

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
                    componentManager.getLog().error(e);
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
