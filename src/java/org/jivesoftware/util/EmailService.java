/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2003-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.mail.Address;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * A service to send email.<p>
 *
 * This class has a few factory methods you can use to return message objects
 * or to add messages into a queue to be sent. Using these methods, you can
 * send emails in the following couple of ways:<p>
 * <pre>
 *   EmailTask emailTask = new EmailTask();
 *   emailTask.addMessage(
 *     "Joe Bloe", "jbloe@place.org",
 *     "Jane Doe", "jane@doe.com",
 *     "Hello...",
 *     "This is the body of the email..."
 *   );
 *   emailTask.run();
 * </pre>
 * or
 * <pre>
 *   EmailTask emailTask = new EmailTask();
 *   Message message = emailTask.createMimeMessage();
 *   // call setters on the message object
 *   // .
 *   // .
 *   // .
 *   emailTask.sendMessage(message);
 *   emailTask.run();
 * </pre><p>
 *
 * This class is configured with a set of Jive properties:<ul>
 *      <li><tt>mail.smtp.host</tt> -- the host name of your mail server, i.e.
 *          mail.yourhost.com
 *      <li><tt>mail.smtp.port</tt> -- an optional property to change the smtp
 *          port used from the default of 25.
 *      <li><tt>mail.smtp.username</tt> -- an optional property to change the
 *          username used to connect to the smtp server. Default is no username.
 *      <li><tt>mail.smtp.password</tt> -- an optional property to change the
 *          password used to connect to the smtp server. Default is no password.
 *      <li><tt>mail.smtp.ssl</tt> -- an optional property to set whether to use
 *          SSL to connect to the smtp server or not. Default is false.
 * </ul>
 */
public class EmailService {

    private static final String SSL_FACTORY = "org.jivesoftware.util.SimpleSSLSocketFactory";

    private static EmailService instance = new EmailService();

    public static EmailService getInstance() {
        return instance;
    }

    private String host;
    private int port;
    private String username;
    private String password;
    private boolean sslEnabled;
    private boolean debugEnabled;

    private ThreadPoolExecutor executor;
    private Session session = null;

    /**
     * Constructs a new EmailService instance.
     */
    private EmailService() {
        executor = new ThreadPoolExecutor(0, 5, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

        host = JiveGlobals.getProperty("mail.smtp.host");
        port = JiveGlobals.getIntProperty("mail.smtp.port", 25);
        username = JiveGlobals.getProperty("mail.smtp.username");
        password = JiveGlobals.getProperty("mail.smtp.password");
        sslEnabled = JiveGlobals.getBooleanProperty("mail.smtp.ssl");
        debugEnabled = JiveGlobals.getBooleanProperty("mail.debug");

        createSession();
    }

    /**
     * Factory method to return a blank JavaMail message. You should use the
     * object returned and set desired message properties. When done, pass the
     * object to the addMessage(Message) method.
     *
     * @return A new JavaMail message.
     */
    public MimeMessage createMimeMessage() {
        return new MimeMessage(session);
    }

    /**
     * Sends a JavaMail message. To create a message, use the
     * {@link #createMimeMessage()} method.
     *
     * @param message the message to send.
     */
    public void sendMessage(MimeMessage message) {
        if (message != null) {
            sendMessages(Collections.singletonList(message));
        }
        else {
            Log.error("Cannot add null email message to queue.");
        }
    }

    /**
     * Send a collection of messages. To create a message, use the
     * {@link #createMimeMessage()} method.
     *
     * @param messages a collection of the messages to send.
     */
    public void sendMessages(Collection<MimeMessage> messages) {
        // If there are no messages, do nothing.
        if (messages.size() == 0) {
            return;
        }
        executor.submit(new EmailTask(messages));
    }

    /**
     * Sends a message, specifying all of its fields.<p>
     *
     * To have more advanced control over the message sent, use the
     * {@link #sendMessage(MimeMessage)} method.<p>
     *
     * Both a plain text and html body can be specified. If one of the values is null,
     * only the other body type is sent. If both body values are set, a multi-part
     * message will be sent. If parts of the message are invalid (ie, the toEmail is null)
     * the message won't be sent.
     *
     * @param toName the name of the recipient of this email.
     * @param toEmail the email address of the recipient of this email.
     * @param fromName the name of the sender of this email.
     * @param fromEmail the email address of the sender of this email.
     * @param subject the subject of the email.
     * @param textBody plain text body of the email, which can be <tt>null</tt> if the
     *      html body is not null.
     * @param htmlBody html body of the email, which can be <tt>null</tt> if the text body
     *      is not null.
     */
    public void sendMessage(String toName, String toEmail, String fromName,
            String fromEmail, String subject, String textBody, String htmlBody) 
    {
        // Check for errors in the given fields:
        if (toEmail == null || fromEmail == null || subject == null ||
                (textBody == null && htmlBody == null))
        {
            Log.error("Error sending email: Invalid fields: "
                    + ((toEmail == null) ? "toEmail " : "")
                    + ((fromEmail == null) ? "fromEmail " : "")
                    + ((subject == null) ? "subject " : "")
                    + ((textBody == null && htmlBody == null) ? "textBody or htmlBody " : "")
            );
        }
        else {
            try {
                String encoding = MimeUtility.mimeCharset("iso-8859-1");
                MimeMessage message = createMimeMessage();
                Address to   = null;
                Address from = null;

                if (toName != null) {
                    to = new InternetAddress(toEmail, toName, encoding);
                }
                else {
                    to = new InternetAddress(toEmail, "", encoding);
                }

                if (fromName != null) {
                    from = new InternetAddress(fromEmail, fromName, encoding);
                }
                else {
                    from = new InternetAddress(fromEmail, "", encoding);
                }

                // Set the date of the message to be the current date
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",
                        java.util.Locale.US);
                format.setTimeZone(JiveGlobals.getTimeZone());
                message.setHeader("Date", format.format(new Date()));
                message.setHeader("Content-Transfer-Encoding", "8bit");
                message.setRecipient(Message.RecipientType.TO, to);
                message.setFrom(from);
                message.setSubject(StringUtils.replace(subject, "\n", ""), encoding);
                // Create HTML, plain-text, or combination message
                if (textBody != null && htmlBody != null) {
                    MimeMultipart content = new MimeMultipart("alternative");
                    // Plain-text
                    MimeBodyPart text = new MimeBodyPart();
                    text.setText(textBody, encoding);
                    text.setDisposition(Part.INLINE);
                    content.addBodyPart(text);
                    // HTML
                    MimeBodyPart html = new MimeBodyPart();
                    html.setContent(htmlBody, "text/html");
                    html.setDisposition(Part.INLINE);
                    content.addBodyPart(html);
                    // Add multipart to message.
                    message.setContent(content);
                    message.setDisposition(Part.INLINE);
                    sendMessage(message);
                }
                else if (textBody != null) {
                    MimeBodyPart bPart = new MimeBodyPart();
                    bPart.setText(textBody, encoding);
                    bPart.setDisposition(Part.INLINE);
                    MimeMultipart mPart = new MimeMultipart();
                    mPart.addBodyPart(bPart);
                    message.setContent(mPart);
                    message.setDisposition(Part.INLINE);
                    // Add the message to the send list
                    sendMessage(message);
                }
                else if (htmlBody != null) {
                    MimeBodyPart bPart = new MimeBodyPart();
                    bPart.setContent(htmlBody, "text/html");
                    bPart.setDisposition(Part.INLINE);
                    MimeMultipart mPart = new MimeMultipart();
                    mPart.addBodyPart(bPart);
                    message.setContent(mPart);
                    message.setDisposition(Part.INLINE);
                    // Add the message to the send list
                    sendMessage(message);
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * Sets the SMTP host (eg mail.example.com). The host is null by
     * default, but must be set before gateway exports can execute.
     *
     * @param host The SMTP host.
     */
    public void setHost(String host) {
        this.host = host;
        createSession();
    }

    /**
     * Sets the port number that will be used when connecting to the SMTP
     * server. The default is 25, the standard SMTP port number.
     *
     * @param port The SMTP port number.
     */
    public void setPort(int port) {
        this.port = port;
        createSession();
    }

    /**
     * Sets the username that will be used when connecting to the SMTP
     * server. The default is null, or no username.
     *
     * @param username The SMTP username.
     */
    public void setUsername(String username) {
        this.username = username;
        createSession();
    }

    /**
     * Sets the username that will be used when connecting to the SMTP
     * server. The default is null, or no username.
     *
     * @param password The SMTP password.
     */
    public void setPassword(String password) {
        this.password = password;
        createSession();
    }

    /**
     * Toggles SMTP transport layer debugging on or off. Debug information is
     * written to <tt>System.out</tt> by the underlying JavaMail provider.
     *
     * @param debugEnabled true if SMTP debugging should be enabled.
     */
    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        createSession();
    }

    /**
     * Sets whether this gateway is configured for SSL connections
     * to the SMTP server or not.
     *
     * @param sslEnabled true if ssl should be enabled, false otherwise.
     */
    public void setSSLEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
        createSession();
    }

    /**
     * Creates a Javamail session.
     */
    private void createSession() {
        Properties mailProps = new Properties();
        mailProps.setProperty("mail.smtp.host", host);
        mailProps.setProperty("mail.smtp.port", String.valueOf(port));
        // Allow messages with a mix of valid and invalid recipients to still be sent.
        mailProps.setProperty("mail.smtp.sendpartial", "true");
        mailProps.setProperty("mail.debug", String.valueOf(debugEnabled));

        // Methology from an article on www.javaworld.com (Java Tip 115)
        // We will attempt to failback to an insecure connection
        // if the secure one cannot be made
        if (sslEnabled) {
            // Register with security provider.
            Security.setProperty("ssl.SocketFactory.provider", SSL_FACTORY);

            mailProps.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
            mailProps.setProperty("mail.smtp.socketFactory.fallback", "true");
        }

        // If a username is defined, use SMTP authentication.
        if (username != null) {
            mailProps.put("mail.smtp.auth", "true");
        }
        session = Session.getInstance(mailProps, null);
    }

    /**
     * Task to send one or more emails via the SMTP server.
     */
    private class EmailTask implements Runnable {

        private Collection<MimeMessage> messages;

        public EmailTask(Collection<MimeMessage> messages) {
            this.messages = messages;
        }

        public void run() {
            Transport transport = null;
            try {
                URLName url = new URLName("smtp", host, port, "", username, password);
                transport = new com.sun.mail.smtp.SMTPTransport(session, url);
                transport.connect(host, port, username, password);
                for (MimeMessage message : messages) {
                    // Attempt to send message, but catch exceptions caused by invalid
                    // addresses so that other messages can continue to be sent.
                    try {
                        transport.sendMessage(message,
                            message.getRecipients(MimeMessage.RecipientType.TO));
                    }
                    catch (AddressException ae) {
                        Log.error(ae);
                    }
                    catch (SendFailedException sfe) {
                        Log.error(sfe);
                    }
                }
            }
            catch (MessagingException me) {
                Log.error(me);
            }
            finally {
                if (transport != null) {
                    try {
                        transport.close();
                    }
                    catch (MessagingException e) { /* ignore */ }
                }
            }
        }
    }
}