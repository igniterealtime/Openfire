package org.jivesoftware.xmpp.workgroup.interceptor;

import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.EmailService;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * An interceptor to ban users by preventing them from sending packets to the workgroup.
 *
 * @author Gaston Dombiak
 */
public class UserInterceptor implements PacketInterceptor {

    /**
     * A Map of banned bare JIDs.
     */
    private Map<String,String> jidBanMap = new HashMap<String,String>();
    /**
     * A Map of banned domains.
     */
    private Map<String,String> domainBanMap = new HashMap<String,String>();
    private String fromEmail;
    private String fromName;
    private String emailSubject = "";
    private String emailBody = "";
    private List<String> emailNotifyList;
    private String rejectionMessage;

    /**
     * Returns the subject of the notification emails.
     *
     * @return the subject of the notification emails.
     */
    public String getEmailSubject() {
        return emailSubject;
    }

    /**
     * Sets the subject of the nofitication emails.
     *
     * @param emailSubject the subject of notification emails.
     */
    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    /**
     * Returns the body of the nofication emails.
     *
     * @return the body of the notification emails.
     */
    public String getEmailBody() {
        return emailBody;
    }

    /**
     * Sets the body of the nofication emails.
     *
     * @param emailBody the body of the notification emails.
     */
    public void setEmailBody(String emailBody) {
        this.emailBody = emailBody;
    }

    /**
     * Returns the email address that notification emails will appear to be from.
     *
     * @return the email address that notification emails will appear from.
     */
    public String getFromEmail() {
        return fromEmail;
    }

    /**
     * Sets the email address that notification emails will appear to be from.
     *
     * @param fromEmail the email address that notification emails will appear from.
     */
    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    /**
     * Returns the name that notification emails will appear to be from.
     *
     * @return the name that notification emails will appear from.
     */
    public String getFromName() {
        return fromName;
    }

    /**
     * Sets the name that notification emails will appear to be from.
     *
     * @param fromName the name that notification emails will appear from.
     */
    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    /**
     * Returns the list of email addresses that will be notified when
     * a user is banned.
     *
     * @return the comma-delimited list of notification email addresses.
     */
    public String getEmailNotifyList() {
        if (emailNotifyList == null || emailNotifyList.isEmpty()) {
            return "";
        }
        StringBuilder buf = new StringBuilder();
        buf.append(emailNotifyList.get(0));
        for (int i=1; i<emailNotifyList.size(); i++) {
            buf.append(", ");
            buf.append(emailNotifyList.get(i));
        }
        return buf.toString();
    }

    /**
     * Sets the list of email addresses that will be notified when a user is banned.
     *
     * @param notifyList the comma-delimited list of notification email addresses.
     */
    public void setEmailNotifyList(String notifyList) {
        if (notifyList == null || notifyList.equals("")) {
            emailNotifyList = null;
        }
        else {
            emailNotifyList = new ArrayList<String>();
            StringTokenizer tokenizer = new StringTokenizer(notifyList, ",");
            while (tokenizer.hasMoreTokens()) {
                String emailAddress = tokenizer.nextToken().trim();
                emailNotifyList.add(emailAddress);
            }
        }
    }

    /**
     * Returns the list of banned users as a list of comma-delimited usernames or
     * the empty string if no banned users exist.
     *
     * @return the list of banned users as a list of comma-delimited usernames.
     */
    public String getJidBanList() {
        return getString(jidBanMap);
    }

    /**
     * Sets the list of banned usernames. The list should be a list of comma-delimited usernames.
     *
     * @param usernames the list of banned usernames.
     */
    public void setJidBanList(String usernames) {
        jidBanMap = getMap(usernames);
    }

    /**
     * Returns the list of banned domains as a list of comma-delimited domains or
     * the empty string if no banned domains exist.
     *
     * @return the list of banned domains as a list of comma-delimited usernames.
     */
    public String getDomainBanList() {
        return getString(domainBanMap);
    }

    /**
     * Sets the list of banned domains. The list should be a list of comma-delimited usernames.
     *
     * @param domains the list of banned usernames.
     */
    public void setDomainBanList(String domains) {
        domainBanMap = getMap(domains);
    }

    public String getRejectionMessage() {
        return rejectionMessage;
    }

    public void setRejectionMessage(String rejectionMessage) {
        this.rejectionMessage = rejectionMessage;
    }

    /**
     * Checks to see if the sender of the packet is a banned user. If they are, a
     * PacketRejectedException is thrown and email notifications may be sent.
     */
    public void interceptPacket(String workgroup, Packet packet, boolean read, boolean processed)
        throws PacketRejectedException {
        if (!read || processed) {
            // Ignore packets that are being sent or have been processed
            return;
        }
        JID jid = packet.getFrom();
        if (jidBanMap.containsKey(jid.toBareJID()) || domainBanMap.containsKey(jid.getDomain())) {
            sendNotifications(packet, jid.toString());
            PacketRejectedException exception = new PacketRejectedException("User '" +
                    packet.getFrom().toBareJID() +
                    "' not allowed to join queue.");
            if (rejectionMessage != null) {
                exception.setRejectionMessage(rejectionMessage);
            }
            throw exception;
        }
    }

    private void sendNotifications(Packet packet, String packetSender) {
        EmailService emailService = EmailService.getInstance();
        String body;
        if (fromEmail == null) {
            return;
        }
        for (String toEmail : emailNotifyList) {
            body = StringUtils.replace(emailBody, "{packet}", packet.toXML());
            body = StringUtils.replace(body, "{sender}", packetSender);
            emailService.sendMessage(null, toEmail, fromName, fromEmail, emailSubject, body, null);
        }
    }

    private static Map<String,String> getMap(String iPStr) {
        Map<String,String> newMap = new HashMap<String,String>();
        if (iPStr == null) {
            return newMap;
        }
        StringTokenizer tokens = new StringTokenizer(iPStr, ",");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            newMap.put(address, "");
        }
        return newMap;
    }


    private static String getString(Map<String,String> map) {
        if (map == null || map.size() == 0) {
            return "";
        }
        // Iterate through the elements in the map.
        StringBuilder buf = new StringBuilder();
        Iterator<String> iter = map.keySet().iterator();
        if (iter.hasNext()) {
            buf.append(iter.next());
        }
        while (iter.hasNext()) {
            buf.append(", ").append(iter.next());
        }
        return buf.toString();
    }
}
