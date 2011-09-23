/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.xmpp.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.apache.log4j.Logger;

import java.util.Vector;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

/**
 * See: http://code.google.com/apis/talk/jep_extensions/gmail.html
 * 
 * @author mecevit
 * @author Daniel Henninger
 */
public class GoogleMailBoxPacket extends IQ {

    static Logger Log = Logger.getLogger(GoogleMailBoxPacket.class);

    public static String MAILBOX_ELEMENT = "mailbox";
    public static String MAILBOX_NAMESPACE = "google:mail:notify";

    private Date resultTime;
    private Integer totalMatched;
    private Boolean totalIsEstimate;
    private String url;
    private Vector<GoogleMailThread> mailThreads = new Vector<GoogleMailThread>();
    
    public GoogleMailBoxPacket() {
    }

    @Override
    public void addExtension(PacketExtension extension) {
    }

    public void addMailThread(GoogleMailThread thread) {
        mailThreads.add(thread);
    }

    public Vector<GoogleMailThread> getMailThreads() {
        return mailThreads;
    }

    public Date getResultTime() {
        return resultTime;
    }

    public void setResultTime(Date resultTime) {
        this.resultTime = resultTime;
    }

    public Integer getTotalMatched() {
        return totalMatched;
    }

    public void setTotalMatched(Integer totalMatched) {
        this.totalMatched = totalMatched;
    }

    public Boolean getTotalIsEstimate() {
        return totalIsEstimate;
    }

    public void setTotalIsEstimate(Boolean totalIsEstimate) {
        this.totalIsEstimate = totalIsEstimate;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getChildElementXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<mailbox xmlns=\"").append(MAILBOX_NAMESPACE).append("\"");
        if (resultTime != null) {
            buf.append(" result-time=\"").append(resultTime.getTime()).append("\"");
        }
        if (totalMatched != null) {
            buf.append(" total-matched=\"").append(totalMatched).append("\"");
        }
        if (totalIsEstimate != null && totalIsEstimate) {
            buf.append(" total-estimate=\"1\"");
        }
        if (url != null) {
            buf.append(" url=\"").append(url).append("\"");
        }
        buf.append(">");
        for (GoogleMailThread thread : mailThreads) {
            buf.append(thread.toXML());
        }
        buf.append("</mailbox>");
        return buf.toString();
    }

    public static class Provider implements IQProvider {

        public Provider() {
            super();
        }

        public IQ parseIQ(XmlPullParser parser) throws Exception {
            GoogleMailBoxPacket mailPacket = new GoogleMailBoxPacket();
            try {
                GoogleMailThread thread = null;

                boolean done = false;
                int eventType = parser.getEventType();
                while (!done) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (parser.getName().equals("mailbox")) {
                            String dateString = parser.getAttributeValue("", "result-time");
                            try {
                                mailPacket.setResultTime(new Date(Long.valueOf(dateString)));
                            }
                            catch (Exception ex) {
                                // Well crap, ok then, ignore it.
                            }
                            try {
                                mailPacket.setTotalMatched(Integer.valueOf(parser.getAttributeValue("", "total-matched"), 0));
                            }
                            catch (NumberFormatException ex) {
                                // Well crap, ok then, ignore it.
                            }
                            String estimateString = parser.getAttributeValue("", "total-estimate");
                            mailPacket.setTotalIsEstimate(estimateString != null && estimateString.equals("1"));
                            mailPacket.setUrl(parser.getAttributeValue("", "url"));
                        }
                        else if (parser.getName().equals("mail-thread-info")) {
                            Long tid = Long.valueOf(parser.getAttributeValue("", "tid"));
                            Integer numParts = null;
                            try {
                                numParts = Integer.valueOf(parser.getAttributeValue("", "participation"), 0);
                            }
                            catch (NumberFormatException ex) {
                                // Well crap, ok then, ignore it.
                            }
                            Integer numMsgs = null;
                            try {
                                numMsgs = Integer.valueOf(parser.getAttributeValue("", "messages"), 0);
                            }
                            catch (NumberFormatException ex) {
                                // Well crap, ok then, ignore it.
                            }
                            String url = parser.getAttributeValue("", "url");
                            String dateString = parser.getAttributeValue("", "date");
                            Date date = null;
                            try {
                                date = new Date(Long.valueOf(dateString));
                            }
                            catch (Exception ex) {
                                // Well crap, ok then, ignore it.
                            }
                            thread = new GoogleMailThread(
                                tid,
                                numParts,
                                numMsgs,
                                date,
                                url,
                                new ArrayList<GoogleMailSender>(),
                                null,
                                null,
                                null
                            );
                        }
                        else if (parser.getName().equals("sender")) {
                            String address = parser.getAttributeValue("", "address");
                            String name = parser.getAttributeValue("", "name");
                            String origString = parser.getAttributeValue("", "originator");
                            String unreadString = parser.getAttributeValue("", "unread");
                            thread.getSenders().add(new GoogleMailSender(
                                address,
                                name,
                                origString != null && origString.equals("1"),
                                unreadString != null && unreadString.equals("1")
                            ));
                        }
                        else if (parser.getName().equals("labels")) {
                            thread.setLabels(Arrays.asList(parser.nextText().split("|")));
                        }
                        else if (parser.getName().equals("subject")) {
                            thread.setSubject(parser.nextText());
                        }
                        else if (parser.getName().equals("snippet")) {
                            thread.setSnippit(parser.nextText());
                        }

                    }
                    else if (eventType == XmlPullParser.END_TAG) {
                        if (parser.getName().equals("mail-thread-info")) {
                            mailPacket.addMailThread(thread);
                            thread = null;
                        }
                        else if (parser.getName().equals("mailbox")) {
                            done = true;
                            break;
                        }
                    }
                    eventType = parser.next();
                }
            }
            catch (IOException ex) {
                Log.debug("XMPP: IO exception while parsing mailbox packet:", ex);
            }
            catch (XmlPullParserException ex) {
                Log.debug("XMPP: XML pull exception while parsing mailbox packet:", ex);
            }
            catch (Exception ex) {
                Log.debug("XMPP: Unknown exception while parsing mailbox packet:", ex);
            }

            return mailPacket;
        }
    }

}

