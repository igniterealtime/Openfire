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

/**
 * See: http://code.google.com/apis/talk/jep_extensions/gmail.html
 *
 * @author Daniel Henninger
 */
public class GoogleMailSender {

    private String address;
    private String name;
    private Boolean originator;
    private Boolean unread;

    public GoogleMailSender(String address, String name, Boolean originator, Boolean unread) {
        this.setAddress(address);
        this.setName(name);
        this.setOriginator(originator);
        this.setUnread(unread);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getOriginator() {
        return originator;
    }

    public void setOriginator(Boolean originator) {
        this.originator = originator;
    }

    public Boolean getUnread() {
        return unread;
    }

    public void setUnread(Boolean unread) {
        this.unread = unread;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<sender");
        if (address != null) {
            buf.append(" address=\"").append(address).append("\"");
        }
        if (name != null) {
            buf.append(" name=\"").append(name).append("\"");
        }
        if (originator != null && originator) {
            buf.append(" originator=\"1\"");
        }
        if (unread != null && unread) {
            buf.append(" unread=\"1\"");
        }
        buf.append("/>");
        return buf.toString();
    }

}
