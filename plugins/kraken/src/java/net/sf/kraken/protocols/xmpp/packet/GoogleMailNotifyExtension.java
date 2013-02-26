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

import org.jivesoftware.smack.packet.PacketExtension;

import java.util.Date;

/**
 * See: http://code.google.com/apis/talk/jep_extensions/gmail.html
 * 
 * @author Daniel Henninger
 */
public class GoogleMailNotifyExtension implements PacketExtension {

    public static String ELEMENT_NAME = "query";
    public static String NAMESPACE = "google:mail:notify";

    public String getElementName() {
        return ELEMENT_NAME;
    }

    public String getNamespace() {
        return NAMESPACE;
    }

    private String query;
    private Date newerThanTime;
    private Long newerThanTid;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Date getNewerThanTime() {
        return newerThanTime;
    }

    public void setNewerThanTime(Date newerThanTime) {
        this.newerThanTime = newerThanTime;
    }

    public Long getNewerThanTid() {
        return newerThanTid;
    }

    public void setNewerThanTid(Long newerThanTid) {
        this.newerThanTid = newerThanTid;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace()).append("\"");
        if (query != null) {
            buf.append(" q=\"").append(query).append("\"");
        }
        if (newerThanTime != null) {
            buf.append(" newer-than-time=\"").append(newerThanTime.getTime()).append("\"");
        }
        if (newerThanTid != null) {
            buf.append(" newer-than-tid=\"").append(newerThanTid).append("\"");
        }
        buf.append("/>");
        return buf.toString();
    }

}
