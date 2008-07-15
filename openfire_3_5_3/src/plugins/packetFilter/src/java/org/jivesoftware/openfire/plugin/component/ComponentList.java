package org.jivesoftware.openfire.plugin.component;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQResultListener;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ComponentList implements IQResultListener {

    private static ComponentList instance = new ComponentList();
    private XMPPServer server = XMPPServer.getInstance();
    private RoutingTable routingTable = server.getRoutingTable();

    Map<String, String> componentMap = new ConcurrentHashMap<String, String>();

    private ComponentList() {
        getComponentInfo();
    }

    public Map<String, String> getComponentMap() {
        return componentMap;
    }

    public static ComponentList getInstance() {
        return instance;
    }

    public String getComponentName(JID jid) {
        return componentMap.get(jid.toString());
    }

    public String getComponentName(String jid) {
        return componentMap.get(jid);
    }


    public void receivedAnswer(IQ packet) {
        if (IQ.Type.result == packet.getType()) {

            Element child = packet.getChildElement();
            if (child != null) {
                for (Iterator it = child.elementIterator("identity"); it.hasNext();) {
                    Element identity = (Element) it.next();
                    String name = identity.attributeValue("name");
                    componentMap.put(packet.getFrom().toString(), name);
                }
            }
        }
    }

    public void answerTimeout(String packetId) {
       Log.warn("An answer to a previously sent IQ stanza was never received. Packet id: " + packetId);
    }


    public Collection<String> getComponentDomains() {
        return routingTable.getComponentsDomains();
    }

    public Collection<String> getComponentNames() {
        return componentMap.values();
    }

    private void getComponentInfo() {
        IQRouter iqRouter;
        Collection<String> components = routingTable.getComponentsDomains();
        iqRouter = server.getIQRouter();
        for (String componentDomain : components) {
            IQ iq = new IQ(IQ.Type.get);
            iq.setFrom(server.getServerInfo().getXMPPDomain());
            iq.setTo(componentDomain);
            iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
            iqRouter.addIQResultListener(iq.getID(), this);
            iqRouter.route(iq);
        }
    }
}
