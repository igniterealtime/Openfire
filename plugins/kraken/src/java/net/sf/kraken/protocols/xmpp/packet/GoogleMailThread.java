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

import net.sf.kraken.util.StringUtils;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * See: http://code.google.com/apis/talk/jep_extensions/gmail.html
 * 
 * @author mecevit
 * @author Daniel Henninger
 */
public class GoogleMailThread {

    private Long threadId;
    private Integer numParticipants;
    private Integer numMessages;
    private Date date;
    private String url;
    private List<GoogleMailSender> senders;
    private List<String> labels;
    private String subject;
    private String snippit;

    public GoogleMailThread(Long threadId, Integer numParticipants, Integer numMessages, Date date, String url, ArrayList<GoogleMailSender> senders, ArrayList<String> labels, String subject, String snippit) {
        this.setThreadId(threadId);
        this.setNumParticipants(numParticipants);
        this.setNumMessages(numMessages);
        this.setDate(date);
        this.setUrl(url);
        this.setSenders(senders);
        this.setLabels(labels);
        this.setSubject(subject);
        this.setSnippit(snippit);
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public Integer getNumParticipants() {
        return numParticipants;
    }

    public void setNumParticipants(Integer numParticipants) {
        this.numParticipants = numParticipants;
    }

    public Integer getNumMessages() {
        return numMessages;
    }

    public void setNumMessages(Integer numMessages) {
        this.numMessages = numMessages;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<GoogleMailSender> getSenders() {
        return senders;
    }

    public void setSenders(List<GoogleMailSender> senders) {
        this.senders = senders;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSnippit() {
        return snippit;
    }

    public void setSnippit(String snippit) {
        this.snippit = snippit;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<mail-thread-info");
        if (threadId != null) {
            buf.append(" tid=\"").append(threadId).append("\"");
        }
        if (numParticipants != null) {
            buf.append(" participation=\"").append(numParticipants).append("\"");
        }
        if (numMessages != null) {
            buf.append(" messages=\"").append(numMessages).append("\"");
        }
        if (date != null) {
            buf.append(" date=\"").append(date.getTime()).append("\"");
        }
        if (url != null) {
            buf.append(" url=\"").append(url).append("\"");
        }
        buf.append(">");
        buf.append("<senders>");
        for (GoogleMailSender sender : senders) {
            buf.append(sender.toXML());
        }
        buf.append("</senders>");
        buf.append("<labels>").append(StringUtils.join(labels, "|")).append("</labels>");
        buf.append("<subject>").append(subject).append("</subject>");
        buf.append("<snippit>").append(snippit).append("</snippit>");
        buf.append("</mail-thread-info>");
        return buf.toString();
    }

}
