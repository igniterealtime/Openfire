package com.fotsum;

import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.concurrent.Future;

public class CallbackOnOffline implements Plugin, PacketInterceptor {

    private static final Logger Log = LoggerFactory.getLogger(CallbackOnOffline.class);

    private boolean debug;
    private String url;
    private InterceptorManager interceptorManager;
    private UserManager userManager;
    private PresenceManager presenceManager;
    private Client client;

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        debug = JiveGlobals.getBooleanProperty("plugin.callback_on_offline.debug", false);
        if (debug) {
            Log.debug("initialize CallbackOnOffline plugin. Start.");
        }

        interceptorManager = InterceptorManager.getInstance();
        presenceManager = XMPPServer.getInstance().getPresenceManager();
        userManager = XMPPServer.getInstance().getUserManager();
        client = ClientBuilder.newClient();

        String code = "plugin.callback_on_offline.url";
        url = JiveGlobals.getProperty(code, null);
        if (url == null || url.length() == 0) {
            String url = "http://localhost:8080/user/offline/callback/url";  //default url
            JiveGlobals.setProperty(code, url);
        }

        // register with interceptor manager
        interceptorManager.addInterceptor(this);

        if (debug) {
            Log.debug("initialize CallbackOnOffline plugin. Finish.");
        }
    }

    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
        if (debug) {
            Log.debug("destroy CallbackOnOffline plugin.");
        }
    }


    public void interceptPacket(Packet packet, Session session, boolean incoming,
                                boolean processed) throws PacketRejectedException {
        if (processed
                && incoming
                && packet instanceof Message
                && packet.getTo() != null) {

            Message msg = (Message) packet;

            if (msg.getType() != Message.Type.chat) {
                return;
            }

            try {
                JID to = packet.getTo();
                User userTo = userManager.getUser(to.getNode());
                boolean available = presenceManager.isAvailable(userTo);

                if (debug) {
                    Log.debug("intercepted message from {} to {}, recipient is available {}", packet.getFrom().toBareJID(), to.toBareJID(), available);
                }

                if (!available) {
                    JID from = packet.getFrom();

                    WebTarget target = client.target(url)
                            .queryParam("from", from.toBareJID())
                            .queryParam("to", to.toBareJID());

                    if (debug) {
                        Log.debug("sending request to url='{}'", target);
                    }

                    Future<Response> responseFuture = target.request().async().get();
                    if (debug) {
                        try {
                            Response response = responseFuture.get();
                            Log.debug("got response status url='{}' status='{}'", target, response.getStatus());
                        } catch (Exception e) {
                            Log.debug("can't get response status url='{}'", target, e);
                        }
                    }
                }
            } catch (UserNotFoundException e) {
            }
        }
    }


    public static void main(String... args) throws PacketRejectedException {
        CallbackOnOffline c = new CallbackOnOffline();
        c.initializePlugin(null, null);
        c.interceptPacket(null, null, false, false);
    }

}
