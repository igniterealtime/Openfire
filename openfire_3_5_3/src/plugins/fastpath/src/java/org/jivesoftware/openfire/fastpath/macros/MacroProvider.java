/**
 * $RCSfile$
 * $Revision: 19255 $
 * $Date: 2005-07-07 18:49:41 -0700 (Thu, 07 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.macros;

import org.jivesoftware.xmpp.workgroup.*;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import com.thoughtworks.xstream.XStream;
import org.dom4j.Element;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class MacroProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "macros".equals(name);
    }

    public boolean handleSet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "macros".equals(name);
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);
        Element iq = packet.getChildElement();
        String name = iq.getName();

        boolean isPersonal = iq.element("personal") != null;
        Agent agent;
        try {
            agent = workgroup.getAgentManager().getAgent(packet.getFrom());
        }
        catch (AgentNotFoundException e) {
            sendItemNotFound(packet, workgroup);
            return;
        }


        if ("macros".equals(name) && !isPersonal) {
            Element globalMacros = reply.setChildElement("macros", "http://jivesoftware.com/protocol/workgroup");
            DbProperties props = workgroup.getProperties();
            String macroModel = props.getProperty("jive.macro" + workgroup.getID());
            if (ModelUtil.hasLength(macroModel)) {
                globalMacros.addElement("model").setText(macroModel);
            }
            else {
                sendItemNotFound(packet, workgroup);
                return;
            }
        }
        else if (isPersonal) {
            Element personalMacros = reply.setChildElement("macros", "http://jivesoftware.com/protocol/workgroup");
            DbProperties props = agent.getProperties();
            String macroModel = props.getProperty("personal.macro");
            if (ModelUtil.hasLength(macroModel)) {
                personalMacros.addElement("model").setText(macroModel);
            }
            else {
                sendItemNotFound(packet, workgroup);
                return;
            }
        }
        else {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        workgroup.send(reply);
    }

    private void sendItemNotFound(IQ packet, Workgroup workgroup) {
        IQ reply;
        reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        reply.setError(new PacketError(PacketError.Condition.item_not_found));
        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {
        IQ reply;
        Element iq = packet.getChildElement();

        String personalMacro = iq.element("personalMacro").getTextTrim();
        try {
            // Verify that an agent is requesting this information.
            Agent agent = workgroup.getAgentManager().getAgent(packet.getFrom());

            DbProperties props = agent.getProperties();
            XStream xstream = new XStream();
            xstream.alias("macro", Macro.class);
            xstream.alias("macrogroup", MacroGroup.class);
            MacroGroup group = (MacroGroup)xstream.fromXML(personalMacro);

            String saveString = xstream.toXML(group);

            try {
                props.deleteProperty("personal.macro");
                props.setProperty("personal.macro", saveString);
            }
            catch (UnauthorizedException e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }

            reply = IQ.createResultIQ(packet);
        }
        catch (AgentNotFoundException e) {
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
        }
        workgroup.send(reply);
    }
}
