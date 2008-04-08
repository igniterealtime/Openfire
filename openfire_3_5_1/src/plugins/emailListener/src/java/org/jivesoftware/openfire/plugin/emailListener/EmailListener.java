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

package org.jivesoftware.openfire.plugin.emailListener;

import com.sun.mail.imap.IMAPFolder;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.security.Security;
import java.util.Date;
import java.util.Properties;
import java.util.Collection;
import java.util.ArrayList;

/**
 * Email listener service that will send an instant message to specified users
 * when new email messages are found.
 *
 * @author Gaston Dombiak
 */
public class EmailListener {

    private static final String SSL_FACTORY = "org.jivesoftware.util.SimpleSSLSocketFactory";

    private static final EmailListener instance = new EmailListener();

    /**
     * Message listener that will process new emails found in the IMAP server.
     */
    private MessageCountAdapter messageListener;
    private Folder folder;
    private boolean started = false;

    public static EmailListener getInstance() {
        return instance;
    }

    private EmailListener() {
    }

    /**
     * Returns true if a connection to the IMAP server was successful.
     *
     * @param host Host to connect to.
     * @param port Port to connect over.
     * @param isSSLEnabled True if an SSL connection will be attempted.
     * @param user Username to use for authentication.
     * @param password Password to use for authentication.
     * @param folderName Folder to check.
     * @return true if a connection to the IMAP server was successful.
     */
    public static boolean testConnection(String host, int port, boolean isSSLEnabled, String user, String password,
                                     String folderName) {
        Folder folder = openFolder(host, port, isSSLEnabled, user, password, folderName);
        boolean success = folder != null && folder.isOpen();
        closeFolder(folder, null);
        return success;
    }

    /**
     * Opens a connection to the IMAP server and listen for new messages.
     */
    public void start() {
        // Check that the listner service is not running
        if (started) {
            return;
        }
        Thread thread = new Thread("Email Listener Thread") {
            public void run() {
                // Open the email folder and keep it
                folder = openFolder(getHost(), getPort(), isSSLEnabled(), getUser(), getPassword(), getFolder());
                if (folder != null) {
                    // Listen for new email messages until #stop is requested
                    listenMessages();
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
        started = true;
    }

    /**
     * Closes the active connection to the IMAP server.
     */
    public void stop() {
        closeFolder(folder, messageListener);
        started = false;
        folder = null;
        messageListener = null;
    }

    private void listenMessages() {
        try {
            // Add messageCountListener to listen for new messages
            messageListener = new MessageCountAdapter() {
                public void messagesAdded(MessageCountEvent ev) {
                    Message[] msgs = ev.getMessages();

                    // Send new messages to specified users
                    for (Message msg : msgs) {
                        try {
                            sendMessage(msg);
                        }
                        catch (Exception e) {
                            Log.error("Error while sending new email message", e);
                        }
                    }
                }


            };
            folder.addMessageCountListener(messageListener);

            // Check mail once in "freq" MILLIseconds
            int freq = getFrequency();
            boolean supportsIdle = false;
            try {
                if (folder instanceof IMAPFolder) {
                    IMAPFolder f = (IMAPFolder) folder;
                    f.idle();
                    supportsIdle = true;
                }
            }
            catch (FolderClosedException fex) {
                throw fex;
            }
            catch (MessagingException mex) {
                supportsIdle = false;
            }
            while (messageListener != null) {
                if (supportsIdle && folder instanceof IMAPFolder) {
                    IMAPFolder f = (IMAPFolder) folder;
                    f.idle();
                }
                else {
                    Thread.sleep(freq); // sleep for freq milliseconds

                    // This is to force the IMAP server to send us
                    // EXISTS notifications.
                    if (folder != null && folder.isOpen()) {
                        folder.getMessageCount();
                    }
                }
            }

        }
        catch (Exception ex) {
            Log.error("Error listening new email messages", ex);
        }
    }

    private void sendMessage(Message message) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("New email has been received\n");
        // FROM
        sb.append("From: ");
        for (Address address : message.getFrom()) {
            sb.append(address.toString()).append(" ");
        }
        sb.append("\n");
        // DATE
        Date date = message.getSentDate();
        sb.append("Received: ").append(date != null ? date.toString() : "UNKNOWN").append("\n");
        // SUBJECT
        sb.append("Subject: ").append(message.getSubject()).append("\n");
        // Apend body
        appendMessagePart(message, sb);

        // Send notifications to specified users
        for (String user : getUsers()) {
            // Create notification message
            org.xmpp.packet.Message notification = new org.xmpp.packet.Message();
            notification.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
            notification.setTo(user);
            notification.setSubject("New email has been received");
            notification.setBody(sb.toString());
            // Send notification message
            XMPPServer.getInstance().getMessageRouter().route(notification);
        }
    }

    private void appendMessagePart(Part part, StringBuilder sb) throws Exception {
        /*
         * Using isMimeType to determine the content type avoids
         * fetching the actual content data until we need it.
         */
        if (part.isMimeType("text/plain")) {
            // This is plain text"
            sb.append((String) part.getContent()).append("\n");
        }
        else if (part.isMimeType("multipart/*")) {
            // This is a Multipart
            Multipart mp = (Multipart) part.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                appendMessagePart(mp.getBodyPart(i), sb);
            }
        }
        else if (part.isMimeType("message/rfc822")) {
            // This is a Nested Message
            appendMessagePart((Part) part.getContent(), sb);
        }
        else {
            /*
            * If we actually want to see the data, and it's not a
            * MIME type we know, fetch it and check its Java type.
            */
            /*Object o = part.getContent();
            if (o instanceof String) {
                // This is a string
                System.out.println((String) o);
            }
            else if (o instanceof InputStream) {
                // This is just an input stream
                InputStream is = (InputStream) o;
                int c;
                while ((c = is.read()) != -1) {
                    System.out.write(c);
                }
            }
            else {
                // This is an unknown type
                System.out.println(o.toString());
            }*/
        }
    }

    private static Folder openFolder(String host, Integer port, Boolean isSSLEnabled, String user, String password,
                                     String folder) {
        if (host == null || port == null || isSSLEnabled == null || user == null || password == null || folder == null) {
            return null;
        }
        try {
            Properties props = System.getProperties();

            props.setProperty("mail.imap.host", host);
            props.setProperty("mail.imap.port", String.valueOf(port));
            props.setProperty("mail.imap.connectiontimeout", String.valueOf(10 * 1000));
            // Allow messages with a mix of valid and invalid recipients to still be sent.
            props.setProperty("mail.debug", JiveGlobals.getProperty("plugin.email.listener.debug", "false"));

            // Methology from an article on www.javaworld.com (Java Tip 115)
            // We will attempt to failback to an insecure connection
            // if the secure one cannot be made
            if (isSSLEnabled) {
                // Register with security provider.
                Security.setProperty("ssl.SocketFactory.provider", SSL_FACTORY);

                //props.setProperty("mail.imap.starttls.enable", "true");
                props.setProperty("mail.imap.socketFactory.class", SSL_FACTORY);
                props.setProperty("mail.imap.socketFactory.fallback", "true");
            }

            // Get a Session object
            Session session = Session.getInstance(props, null);

            // Get a Store object
            Store store = session.getStore(isSSLEnabled ? "imaps" : "imap");

            // Connect
            store.connect(host, user, password);

            // Open a Folder
            Folder newFolder = store.getFolder(folder);
            if (newFolder == null || !newFolder.exists()) {
                Log.error("Invalid email folder: " + folder);
                return null;
            }

            newFolder.open(Folder.READ_WRITE);
            return newFolder;
        }
        catch (Exception e) {
            Log.error("Error while initializing email listener", e);
        }
        return null;
    }

    private static void closeFolder(Folder folder, MessageCountAdapter messageListener) {
        if (folder != null) {
            if (messageListener != null) {
                folder.removeMessageCountListener(messageListener);
            }
            try {
                folder.close(false);
            }
            catch (MessagingException e) {
                Log.error("Error closing folder", e);
            }
        }
    }

    /**
     * Returns the host where the IMAP server is running or <tt>null</tt> if none was defined.
     *
     * @return the host where the IMAP server is running or null if none was defined.
     */
    public String getHost() {
        return JiveGlobals.getProperty("plugin.email.listener.host");
    }

    /**
     * Sets the host where the IMAP server is running or <tt>null</tt> if none was defined.
     *
     * @param host the host where the IMAP server is running or null if none was defined.
     */
    public void setHost(String host) {
        JiveGlobals.setProperty("plugin.email.listener.host", host);
    }

    /**
     * Returns the port where the IMAP server is listening. By default unsecured connections
     * use port 143 and secured ones use 993.
     *
     * @return port where the IMAP server is listening.
     */
    public int getPort() {
        return JiveGlobals.getIntProperty("plugin.email.listener.port", isSSLEnabled() ? 993 : 143);
    }

    /**
     * Sets the port where the IMAP server is listening. By default unsecured connections
     * use port 143 and secured ones use 993.
     *
     * @param port port where the IMAP server is listening.
     */
    public void setPort(int port) {
        JiveGlobals.setProperty("plugin.email.listener.port", Integer.toString(port));
    }

    /**
     * Returns the user to use to connect to the IMAP server. A null value means that
     * this property needs to be configured to be used.
     *
     * @return the user to use to connect to the IMAP server.
     */
    public String getUser() {
        return JiveGlobals.getProperty("plugin.email.listener.user");
    }

    /**
     * Sets the user to use to connect to the IMAP server. A null value means that
     * this property needs to be configured to be used.
     *
     * @param user the user to use to connect to the IMAP server.
     */
    public void setUser(String user) {
        JiveGlobals.setProperty("plugin.email.listener.user", user);
    }

    /**
     * Returns the password to use to connect to the IMAP server. A null value means that
     * this property needs to be configured to be used.
     *
     * @return the password to use to connect to the IMAP server.
     */
    public String getPassword() {
        return JiveGlobals.getProperty("plugin.email.listener.password");
    }

    /**
     * Sets the password to use to connect to the IMAP server. A null value means that
     * this property needs to be configured to be used.
     *
     * @param password the password to use to connect to the IMAP server.
     */
    public void setPassword(String password) {
        JiveGlobals.setProperty("plugin.email.listener.password", password);
    }

    /**
     * Returns the name of the folder. In some Stores, name can be an absolute path if
     * it starts with the hierarchy delimiter. Else it is interpreted relative to the
     * 'root' of this namespace.
     *
     * @return the name of the folder.
     */
    public String getFolder() {
        return JiveGlobals.getProperty("plugin.email.listener.folder");
    }

    /**
     * Sets the name of the folder. In some Stores, name can be an absolute path if
     * it starts with the hierarchy delimiter. Else it is interpreted relative to the
     * 'root' of this namespace.
     *
     * @param folder the name of the folder.
     */
    public void setFolder(String folder) {
        JiveGlobals.setProperty("plugin.email.listener.folder", folder);
    }

    /**
     * Returns the milliseconds to wait to check for new emails. This frequency
     * is used if the IMAP server does not support idle.
     *
     * @return the milliseconds to wait to check for new emails.
     */
    public int getFrequency() {
        return JiveGlobals.getIntProperty("plugin.email.listener.frequency", 5 * 60 * 1000);
    }

    /**
     * Sets the milliseconds to wait to check for new emails. This frequency
     * is used if the IMAP server does not support idle.
     *
     * @param frequency the milliseconds to wait to check for new emails.
     */
    public void setFrequency(int frequency) {
        JiveGlobals.setProperty("plugin.email.listener.frequency", Integer.toString(frequency));
    }
    /**
     * Returns true if SSL is enabled to connect to the server.
     *
     * @return true if SSL is enabled to connect to the server.
     */
    public boolean isSSLEnabled() {
        return JiveGlobals.getBooleanProperty("plugin.email.listener.ssl", false);
    }

    /**
     * Sets if SSL is enabled to connect to the server.
     *
     * @param enabled true if SSL is enabled to connect to the server.
     */
    public void setSSLEnabled(boolean enabled) {
        JiveGlobals.setProperty("plugin.email.listener.ssl", Boolean.toString(enabled));
    }

    public Collection<String> getUsers() {
        String users = JiveGlobals.getProperty("plugin.email.listener.users");
        if (users == null || users.trim().length() == 0) {
            Collection<String> admins = new ArrayList<String>();
            for (JID jid : XMPPServer.getInstance().getAdmins()) {
                admins.add(jid.toString());
            }
            return admins;
        }
        return StringUtils.stringToCollection(users);
    }

    public void setUsers(Collection<String> users) {
        JiveGlobals.setProperty("plugin.email.listener.users", StringUtils.collectionToString(users));
    }
}
