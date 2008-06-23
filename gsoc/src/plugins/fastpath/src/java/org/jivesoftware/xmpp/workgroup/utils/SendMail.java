/**
 * $RCSfile$
 * $Revision: 18406 $
 * $Date: 2005-02-07 14:32:46 -0800 (Mon, 07 Feb 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.utils;

import org.jivesoftware.util.Log;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.rmi.server.UID;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

public class SendMail {
    private String toField;
    private String subjectField;
    private String messageText;
    private String myAddress;
    private String attachmentFile;
    private String customerName;
    private boolean isHTML = false;

    public boolean sendMessage(String message, String host, String port, String username, String password) {
        boolean ok = false;

        String uidString = "";
        try {
            // Set the email properties necessary to send email
            final Properties props = System.getProperties();
            props.put("mail.smtp.host", host);
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.server", host);
            if (ModelUtil.hasLength(port)) {
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", port);
            }

            Session sess;

            if (ModelUtil.hasLength(password) && ModelUtil.hasLength(username)) {
                sess = Session.getInstance(props, new MailAuthentication(username, password));
            }
            else {
                sess = Session.getDefaultInstance(props, null);
            }


            Message msg = new MimeMessage(sess);

            StringTokenizer toST = new StringTokenizer(toField, ",");
            if (toST.countTokens() > 1) {
                InternetAddress[] address = new InternetAddress[toST.countTokens()];
                int addrIndex = 0;
                String addrString = "";
                while (toST.hasMoreTokens()) {
                    addrString = toST.nextToken();
                    address[addrIndex] = (new InternetAddress(addrString));
                    addrIndex = addrIndex + 1;
                }
                msg.setRecipients(Message.RecipientType.TO, address);
            }
            else {
                InternetAddress[] address = {new InternetAddress(toField)};
                msg.setRecipients(Message.RecipientType.TO, address);
            }

            InternetAddress from = new InternetAddress(myAddress);

            msg.setFrom(from);
            msg.setSubject(subjectField);

            UID msgUID = new UID();

            uidString = msgUID.toString();

            msg.setHeader("X-Mailer", uidString);

            msg.setSentDate(new Date());

            MimeMultipart mp = new MimeMultipart();

            // create body part for textarea
            MimeBodyPart mbp1 = new MimeBodyPart();

            if (getCustomerName() != null) {
                messageText = "From: " + getCustomerName() + "\n" + messageText;
            }

            if (isHTML) {
                mbp1.setContent(messageText, "text/html");
            }
            else {
                mbp1.setContent(messageText, "text/plain");
            }
            mp.addBodyPart(mbp1);

            try {
                if (!isHTML) {
                    msg.setContent(messageText, "text/plain");
                }
                else {
                    msg.setContent(messageText, "text/html");
                }
                Transport.send(msg);
                ok = true;
            }
            catch (SendFailedException sfe) {
                Log.warn("Could not connect to SMTP server.");
            }

        }
        catch (Exception eq) {
            Log.warn("Could not connect to SMTP server.");
        }
        return ok;
    }

    class MailAuthentication extends Authenticator {
        String smtpUsername = null;
        String smtpPassword = null;

        public MailAuthentication(String username, String password) {
            smtpUsername = username;
            smtpPassword = password;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(smtpUsername, smtpPassword);
        }
    }


    public void setToField(String toField) {
        this.toField = toField;
    }


    public String getToField() {
        return toField;
    }


    public void setSubjectField(String subjectField) {
        this.subjectField = subjectField;
    }


    public String getSubjectField() {
        return subjectField;
    }


    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }


    public String getMessageText() {
        return messageText;
    }


    public void setMyAddress(String myAddress) {
        this.myAddress = myAddress;
    }


    public String getMyAddress() {
        return myAddress;
    }


    public void setAttachmentFile(String attachmentFile) {
        this.attachmentFile = attachmentFile;
    }


    public String getAttachmentFile() {
        return attachmentFile;
    }


    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }


    public String getCustomerName() {
        return customerName;
    }

    public void setHTML(boolean isHTML) {
        this.isHTML = isHTML;
    }

    public boolean getHTML() {
        return isHTML;
    }
}
