package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager;
import org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

public class GojaraAdminProcessor extends AbstractRemoteRosterProcessor {
    private TransportSessionManager transportSessionManager = TransportSessionManager.getInstance();
    private GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();

    public GojaraAdminProcessor() {
        Log.info("Created GojaraAdminProcessor");
    }

    /**
     * Here we process the response of the remote command sent to Spectrum. We have to identify what kind of response it
     * is, as no tag for the command being responded is being sent. Currently these commands are used in Gojara
     * TransportSessionManager: online_users ( Chatmsg of online users for specific transport), usernames seperated by
     * newlines
     */
    @Override
    public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
        Message message = (Message) packet;

        // handle different commands
        Log.debug("Intercepted spectrum message: " + message.toString());
        String command = packet.getID();
        if (command.equals("online_users")) {
            handleOnlineUsers(message, subdomain);
        } else if (command.equals("unregister")) {
            handleUnregister(message, subdomain);
        } else if (command.equals("config_check")) {
            handleConfigCheck(subdomain);
        } else if (command.equals("uptime")) {
            handleStatistic(message, subdomain, "uptime");
        } else if (command.equals("messages_from_xmpp")) {
            handleStatistic(message, subdomain, "messages_from_xmpp");
        } else if (command.equals("messages_to_xmpp")) {
            handleStatistic(message, subdomain, "messages_to_xmpp");
        } else if (command.equals("used_memory")) {
            handleStatistic(message, subdomain, "used_memory");
        } else if (command.equals("average_memory_per_user")) {
            handleStatistic(message, subdomain, "average_memory_per_user");
        }
    }

    private void handleOnlineUsers(Message message, String subdomain) {
        Log.debug("Found online_users command!");
        String body = message.getBody();
        if (body.equals("0") || body.startsWith("Unknown command."))
            return;
        String[] content = message.getBody().split("\\r?\\n");
        for (String user : content) {
            JID userjid = new JID(user);
            transportSessionManager.connectUserTo(subdomain, userjid.getNode());
        }
    }

    private void handleUnregister(Message message, String subdomain) {
        Log.debug("Found unregister command! ");
        String body = message.getBody();

        Pattern p = Pattern.compile("^User '(.+)' unregistered.");
        Matcher m = p.matcher(body);
        if (m.matches()) {
            String user = m.group(1);
            JID userJid = new JID(user);
            transportSessionManager.removeRegistrationOfUserFromDB(subdomain, userJid.getNode());
            Log.debug("unregister command was successfull for user: " + userJid.getNode());
        }
    }

    private void handleConfigCheck(String subdomain) {
        gojaraAdminManager.confirmGatewayConfig(subdomain);
        Log.info("Confirm config_check for " + subdomain);
    }

    private void handleStatistic(Message message, String subdomain, String statistic) {
        String body = message.getBody();
        // we dont catch this with exception so we can see what might go wrong. Sometimes S2 responded not knowing the
        // command but i dont really know why
        if (body.startsWith("Unknown command."))
            return;

        int value;
        try {
            value = Integer.parseInt(body);
            gojaraAdminManager.putStatisticValue(subdomain, statistic, value);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
