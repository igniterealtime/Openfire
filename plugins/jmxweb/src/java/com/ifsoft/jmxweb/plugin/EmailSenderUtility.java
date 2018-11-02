package com.ifsoft.jmxweb.plugin;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PdfPTable;
import com.owlike.genson.Genson;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.security.Security;

import org.jivesoftware.openfire.admin.DefaultAdminProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.*;

/**
 * This class creates a pdf attachment to be sent via email.
 * **/

public class EmailSenderUtility{
    private static Logger Log = LoggerFactory.getLogger("JmxWebPlugin:EmailSenderUtility");
    private static final String SSL_FACTORY = "org.jivesoftware.util.SimpleSSLSocketFactory";


    public void sendEmail() {
        ByteArrayOutputStream outputStream = null;
        try {
            String host = JiveGlobals.getProperty("mail.smtp.host", "localhost");
            String port = JiveGlobals.getProperty("mail.smtp.port", "25");
            String username = JiveGlobals.getProperty("mail.smtp.username");
            String password = JiveGlobals.getProperty("mail.smtp.password");
            String debugEnabled = JiveGlobals.getProperty("mail.debug");
            boolean sslEnabled = JiveGlobals.getBooleanProperty("mail.smtp.ssl", true);

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.auth", port);
            props.setProperty("mail.smtp.sendpartial", "true");
            props.setProperty("mail.debug", debugEnabled);

            if (sslEnabled) {
                // Register with security provider.
                Security.setProperty("ssl.SocketFactory.provider", SSL_FACTORY);
                props.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
                props.setProperty("mail.smtp.socketFactory.fallback", "true");
            }

            if (username != null) {
                props.put("mail.smtp.auth", "true");
            }

            Session session = Session.getInstance(props);
            outputStream = new ByteArrayOutputStream();
            createPdfAttachment(outputStream);
            byte[] bytes = outputStream.toByteArray();
            ByteArrayDataSource dataSource = new ByteArrayDataSource(bytes, "application/pdf");
            MimeBodyPart pdfBodyPart = new MimeBodyPart();
            pdfBodyPart.setDataHandler(new DataHandler(dataSource));
            pdfBodyPart.setFileName("ResultSummary.pdf");
            MimeMultipart multipart= new MimeMultipart();
            multipart.addBodyPart(pdfBodyPart);
            MimeMessage msg = new MimeMessage(session);

            DefaultAdminProvider defaultAdminProvider= new DefaultAdminProvider();
            java.util.List<JID> adminList=defaultAdminProvider.getAdmins();
            java.util.List<String> adminListEmails=new ArrayList<String>();

            UserManager manager = UserManager.getInstance();
            Log.info("Number of Admins " +adminList.size());
            for(int i = 0; i < adminList.size(); i++) {
                User user;
                try {
                    user = manager.getUser(adminList.get(i).getNode().toString());
                    Log.info("Admin Emails: " +user.getEmail());
                    adminListEmails.add(user.getEmail());
                }
                catch (Exception ex) {
                    continue;
                }
            }

           // java.util.List<String> recipientsList=Arrays.asList("", "", "");
            InternetAddress[] recipients = new InternetAddress[adminListEmails.size()];
            for (int i = 0; i < adminListEmails.size(); i++) {
                recipients[i] = new InternetAddress(adminListEmails.get(i).toString());
            }
            msg.setFrom(new InternetAddress("no-reply@openfire.org", "Openfire Admin"));
            msg.setRecipients(javax.mail.Message.RecipientType.TO,recipients);
            msg.setSubject("MONITORING REPORT - " + new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date()));
            msg.setContent(multipart);

            if (username != null)
            {
                URLName url = new URLName("smtp", host, Integer.parseInt(port), "", username, password);
                Transport transport = new com.sun.mail.smtp.SMTPTransport(session, url);
                transport.connect(host, Integer.parseInt(port), username, password);
                transport.sendMessage(msg, msg.getRecipients(MimeMessage.RecipientType.TO));

           } else Transport.send(msg);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not send email");
        }
    }
    public void createPdfAttachment(OutputStream outputStream) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, outputStream);
        document.open();
        document.addTitle("Monitoring Report");
        document.addSubject("PDF Document");
        document.addKeywords("iText, email");
        document.addAuthor("JMX");
        document.addCreator("JMX");
        Timestamp stamp = new Timestamp(System.currentTimeMillis());
        Date date = new Date(stamp.getTime());
        //Make the Get call to get the data from Jolokia endpoint.
        HttpClient httpClient = new HttpClient();
        String monData = httpClient.getMemoryData();
        Log.info("Monitoring Data JSON:"+monData);
        //Converting json string to java object for easy manipulation.
        Map outNode = new Genson().deserialize(monData, Map.class);
        Map requestNode = (Map) outNode.get("request");
        Map valueNode = (Map) outNode.get("value");

        HashMap<String,String> monitoringData = new HashMap<String,String>();
        monitoringData.put("Current Date",date.toString());
        monitoringData.put("Report Date",outNode.get("timestamp").toString());
        monitoringData.put("Maximum Heap Memory",valueNode.get("max").toString());
        monitoringData.put("Committed Heap Memory",valueNode.get("committed").toString());
        monitoringData.put("Init Heap Memory",valueNode.get("init").toString());
        monitoringData.put("Used Heap Memory",valueNode.get("used").toString());
        Font boldFont = new Font(Font.FontFamily.TIMES_ROMAN, 18, Font.BOLD);
        PdfPTable table = new PdfPTable(2);
        table.setSpacingBefore(5);
        table.addCell(new Phrase("Monitor", boldFont ));
        table.addCell(new Phrase("Value", boldFont ));

        for (Map.Entry<String, String> entry : monitoringData.entrySet()) {
            table.addCell(entry.getKey());
            System.out.println(entry.getKey());

            if (entry.getValue()!="" && entry.getValue()!=null)
            {
                table.addCell(entry.getValue());
                System.out.println(entry.getValue());
            }
            else{
                table.addCell("NOT AVAILABLE");
            }
        }
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        document.add(table);

        document.close();
    }
}
