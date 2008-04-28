/**
 * $RCSfile$
 * $Revision: 19158 $
 * $Date: 2005-06-27 15:15:06 -0700 (Mon, 27 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.dataforms;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.dom4j.Element;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class WorkgroupFormProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        if ("workgroup-form".equals(name)) {
            return true;
        }
        return false;
    }

    public boolean handleSet(IQ packet) {
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);
        FormManager formManager = FormManager.getInstance();
        DataForm dataForm = formManager.getDataForm(workgroup);
        if (dataForm == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }



        Element iq = packet.getChildElement();

        if (iq.elements().isEmpty()) {
            reply.setChildElement(iq.createCopy());
            // Send the data form to the requestor

            reply.addExtension(dataForm.createCopy());
            workgroup.send(reply);
        }
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }
}
