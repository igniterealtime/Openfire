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
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.io.IOException;
import java.security.Security;
import java.util.Properties;

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
                folder = openFolder();
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

    /**
     * Returns true if a connection to the IMAP server was successful.
     *
     * @return true if a connection to the IMAP server was successful.
     */
    public boolean testConnection() {
        Folder folder = openFolder();
        boolean success = folder != null && folder.isOpen();
        closeFolder(folder, null);
        return success;
    }

    private void listenMessages() {
        try {
            // Add messageCountListener to listen for new messages
            messageListener = new MessageCountAdapter() {
                public void messagesAdded(MessageCountEvent ev) {
                    Message[] msgs = ev.getMessages();
                    System.out.println("Got " + msgs.length + " new messages");

                    // Just dump out the new messages
                    for (int i = 0; i < msgs.length; i++) {
                        try {
                            System.out.println("-----");
                            System.out.println("Message " +
                                    msgs[i].getMessageNumber() + ":");
                            msgs[i].writeTo(System.out);
                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        } catch (MessagingException mex) {
                            mex.printStackTrace();
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
            } catch (FolderClosedException fex) {
                throw fex;
            } catch (MessagingException mex) {
                supportsIdle = false;
            }
            while (messageListener != null) {
                if (supportsIdle && folder instanceof IMAPFolder) {
                    IMAPFolder f = (IMAPFolder) folder;
                    f.idle();
                } else {
                    Thread.sleep(freq); // sleep for freq milliseconds

                    // This is to force the IMAP server to send us
                    // EXISTS notifications.
                    if (folder != null && folder.isOpen()) {
                        folder.getMessageCount();
                    }
                }
            }

        } catch (Exception ex) {
            Log.error("Error listening new email messages", ex);
        }
    }

    private Folder openFolder(){
        try {
            Properties props = System.getProperties();

            props.setProperty("mail.imap.host", getHost());
            props.setProperty("mail.imap.port", String.valueOf(getPort()));
//            props.setProperty("mail.imap.user", getUser());
            props.setProperty("mail.imap.connectiontimeout", String.valueOf(10 * 1000));
            // Allow messages with a mix of valid and invalid recipients to still be sent.
            props.setProperty("mail.debug",  JiveGlobals.getProperty("plugin.email.listener.debug", "false"));

            // Methology from an article on www.javaworld.com (Java Tip 115)
            // We will attempt to failback to an insecure connection
            // if the secure one cannot be made
            if (isSSLEnabled()) {
                // Register with security provider.
                Security.setProperty("ssl.SocketFactory.provider", SSL_FACTORY);

                //props.setProperty("mail.imap.starttls.enable", "true");
                props.setProperty("mail.imap.socketFactory.class", SSL_FACTORY);
                props.setProperty("mail.imap.socketFactory.fallback", "true");
            }

            // Get a Session object
            Session session = Session.getInstance(props, null);

            // Get a Store object
            Store store = session.getStore(isSSLEnabled() ? "imaps" : "imap");

            // Connect
            store.connect(getHost(), getUser(), getPassword());

            // Open a Folder
            Folder newFolder = store.getFolder(getFolder());
            if (newFolder == null || !newFolder.exists()) {
                Log.error("Invalid email folder: " + getFolder());
                return null;
            }

            newFolder.open(Folder.READ_WRITE);
            return newFolder;
        } catch (MessagingException e) {
            Log.error("Error while initializing email listener", e);
        }
        return null;
    }

    private void closeFolder(Folder folder, MessageCountAdapter messageListener) {
        if (folder != null) {
            if (messageListener != null) {
                folder.removeMessageCountListener(messageListener);
            }
            try {
                folder.close(false);
            } catch (MessagingException e) {
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
     * @param  port port where the IMAP server is listening.
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
     * Returns the password to use to connect to the IMAP server. A null value means that
     * this property needs to be configured to be used.
     *
     * @return the password to use to connect to the IMAP server.
     */
    public String getPassword() {
        return JiveGlobals.getProperty("plugin.email.listener.password");
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
     * Returns the milliseconds to wait to check for new emails. This frequency
     * is used if the IMAP server does not support idle.
     *
     * @return the milliseconds to wait to check for new emails.
     */
    public int getFrequency() {
        return JiveGlobals.getIntProperty("plugin.email.listener.frequency", 5 * 60 * 1000);
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

    /**
     * Sets the host where the IMAP server is running or <tt>null</tt> if none was defined.
     *
     * @param host the host where the IMAP server is running or null if none was defined.
     */
    public void setHost(String host) {
        JiveGlobals.setProperty("plugin.email.listener.host", host);
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
     * Sets the password to use to connect to the IMAP server. A null value means that
     * this property needs to be configured to be used.
     *
     * @param password the password to use to connect to the IMAP server.
     */
    public void setPassword(String password) {
        JiveGlobals.setProperty("plugin.email.listener.password", password);
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
     * Sets the milliseconds to wait to check for new emails. This frequency
     * is used if the IMAP server does not support idle.
     *
     * @param frequency the milliseconds to wait to check for new emails.
     */
    public void setFrequency(int frequency) {
        JiveGlobals.setProperty("plugin.email.listener.frequency", Integer.toString(frequency));
    }

}
