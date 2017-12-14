package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.Timer;
import java.util.TimerTask;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.tree.DefaultAttribute;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * This implements a special IQ package for Spark client. Spark checks the
 * disco#info package send from external component for a <registered> tag.
 * Because spectrum does not support this feature we have to modify the disco
 * package from spectrum with the registered tag if the user is registered with
 * this gateway. Part of command pattern used in {@link MainInterceptor}
 * 
 * @author Holger Bergunde
 * 
 */
public class DiscoIQRegisteredProcessor extends AbstractRemoteRosterProcessor {
    private boolean _isRegistered = false;

    public DiscoIQRegisteredProcessor() {
        Log.info("Created DiscoIQRegisteredProcessor");
    }

    @Override
    public void process(Packet packet, final String subdomain, String to, String from) throws PacketRejectedException {
        Log.debug("Processing packet in DiscoIQRegisteredProcessor for " + subdomain);
        // Check if the jabber:iq:register is enabled in admin panel
        boolean isFeatureEnabled = JiveGlobals.getBooleanProperty("plugin.remoteroster.sparkDiscoInfo", false);
        if (!isFeatureEnabled) {
            Log.debug("Spark extension is deactivated. Won't change the disco#info");
            return;
        }

        final InterceptorManager interceptorManager = InterceptorManager.getInstance();
        final PacketInterceptor interceptor = new PacketInterceptor() {

            public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
                if (!processed && incoming) {
                    if (packet instanceof IQ) {
                        IQ iqPacket = (IQ) packet;
                        Element packetElement = iqPacket.getChildElement();
                        if (packetElement == null)
                            return;
                        String ns = iqPacket.getChildElement().getNamespace().getURI();
                        
                        if (iqPacket.getType().equals(IQ.Type.result) && ns.equals("jabber:iq:register")
                                && iqPacket.getFrom().toString().equals(subdomain)) {
                            // Check if we are already registered
                            setRegistered(iqPacket.toString().contains("<registered/>"));
                            throw new PacketRejectedException();
                        } else if (iqPacket.getType().equals(IQ.Type.result) && ns.equals("http://jabber.org/protocol/disco#info")
                                && iqPacket.getFrom().toString().equals(subdomain)) {

                            /*
                             * This is the answer of the disco#info from spark
                             * to our component. add the jabber:iq:register
                             * feature if we are registered
                             */
                            if (isRegistered()) {
                                Log.debug("Modifying disco#info packge to send registered iq feature to Spark user "
                                        + iqPacket.getTo().toString());
                                Attribute attribut = new DefaultAttribute("var", "jabber:iq:registered");
                                iqPacket.getChildElement().addElement("feature").add(attribut);
                            }
                        }
                    }
                }
            }
        };

        Log.debug("Creating my own listener for jabber:iq:register result to external component " + subdomain);
        interceptorManager.addInterceptor(interceptor);

        IQ askComponent = new IQ();
        askComponent.setTo(to);
        askComponent.setFrom(from);
        askComponent.setType(IQ.Type.get);
        Element query = new DefaultElement( QName.get( "query", "jabber:iq:register" ) );
        askComponent.setChildElement(query);

        // Remove the package intercepter in 1sec
        TimerTask removeInterceptorTask = new TimerTask() {

            @Override
            public void run() {
                Log.debug("Removing my created listener for jabber:iq:register. Component " + subdomain);
                interceptorManager.removeInterceptor(interceptor);
            }
        };

        Timer timer = new Timer();
        timer.schedule(removeInterceptorTask, 1000);

        // Send the register query to component
        dispatchPacket(askComponent);

    }

    private boolean isRegistered() {
        return _isRegistered;
    }

    private void setRegistered(boolean bool) {
        _isRegistered = bool;
    }
}
