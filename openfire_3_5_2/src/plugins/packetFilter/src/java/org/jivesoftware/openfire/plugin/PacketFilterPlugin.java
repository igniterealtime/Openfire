package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.rules.Rule;
import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.jivesoftware.openfire.plugin.rules.RuleManagerProxy;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

import java.io.File;

public class PacketFilterPlugin implements Plugin, PacketInterceptor {

    private static PluginManager pluginManager;

    public PacketFilterPlugin() {
        XMPPServer server = XMPPServer.getInstance();
        interceptorManager = InterceptorManager.getInstance();

    }

    //Packet Filter
    private PacketFilter pf;

    //Hook for intercpetorn
    private InterceptorManager interceptorManager;


    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        // register with interceptor manager
        Log.info("Packet Filter loaded...");
        interceptorManager.addInterceptor(this);
        this.pluginManager = manager;
        pf = PacketFilter.getInstance();
        RuleManager ruleManager = new RuleManagerProxy();
        pf.setRuleManager(ruleManager);
        
    }

    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
    }


    public String getName() {
        return "packetFilter";
        
    }
    public static PluginManager getPluginManager() {
        return pluginManager;
    }
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {

        Rule rule = pf.findMatch(packet);

        if (rule != null) {
            rule.doAction(packet);
        }
    }
}       
