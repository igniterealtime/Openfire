package org.jivesoftware.wildfire.gateway;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.Packet;

/**
 * The <code>JabberEndpoint</code> implements the <code>Endpoint</code> for an
 * XMPP server.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public class JabberEndpoint implements Endpoint {

    /**
     * The componentManager
     *
     * @see ComponentManager
     */
    private final ComponentManager componentManager;
    /**
     * The component
     *
     * @see Component
     */
    private final Component component;
    
    
    /** 
     * The value. 
     * @see EndpointValve 
     */
    private final EndpointValve valve;
    
    /**
     * Construct a new <code>JabberEndpoint</code>.
     * @param componentManager The componentManager.
     * @param component The component.
     */
    public JabberEndpoint(ComponentManager componentManager, Component component) {
        this(componentManager, component, new EndpointValve());
    }
    
    /**
     * Construct a new <code>JabberEndpoint</code>.
     * @param componentManager 
     * @param component 
     * @param valve
     */
    public JabberEndpoint(ComponentManager componentManager, Component component, EndpointValve valve) {
        this.componentManager = componentManager;
        this.component = component;
        this.valve= valve;
    }
//    
//    
//    /**
//     * @param jid
//     * @param string
//     * @throws Exception
//     */
//    public void sendMessage(JID jid, String string) throws Exception {
//        Message message = new Message();
//        message.setBody(string);
//        message.setTo(jid);
//        this.componentManager.sendPacket(this.component, message);
//    }

    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#sendPacket(Packet)
     */
    public void sendPacket(Packet packet) throws ComponentException {
        if(valve.isOpen()) {
            /**
             * Push all pending packets to the XMPP Server.
             */
            while(!queue.isEmpty()) {
                this.componentManager.sendPacket(this.component, queue.poll());
            }
            this.componentManager.sendPacket(this.component, packet);
        } else {
            queue.add(packet);
            logger.log(Level.FINE, "jabberendpoint.sendpacketenqueue", packet.getFrom());
        }
    }
    
    /** The backlog queue. */
    private final ConcurrentLinkedQueue<Packet> queue = new ConcurrentLinkedQueue<Packet>();

    
    /** The logger. */
    final static private Logger logger = Logger.getLogger("JabberEndpoint", "gateway_i18n");
    
    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#getValve()
     */
    public EndpointValve getValve() {
        return this.valve;
    }

}
