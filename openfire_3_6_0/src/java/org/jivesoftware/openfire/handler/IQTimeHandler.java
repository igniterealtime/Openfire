/**
 * $RCSfile$
 * $Revision: 1747 $
 * $Date: 2005-08-04 18:36:36 -0300 (Thu, 04 Aug 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.handler;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.JiveConstants;
import org.xmpp.packet.IQ;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Implements the TYPE_IQ jabber:iq:time protocol (time info) as
 * as defined by JEP-0090. Allows Jabber entities to query each
 * other's local time.  The server will respond with its local time.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the time request is addressed to itself.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ time requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQTimeHandler extends IQHandler implements ServerFeaturesProvider {

    // todo: Make display text match the locale of user (xml:lang support)
    private static final DateFormat DATE_FORMAT = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private static final DateFormat TIME_FORMAT = DateFormat.getTimeInstance(DateFormat.LONG);
    // UTC and not JEP-0082 time format is used as per the JEP-0090 specification.
    private static final FastDateFormat UTC_FORMAT =
            FastDateFormat.getInstance(JiveConstants.XMPP_DELAY_DATETIME_FORMAT,
            TimeZone.getTimeZone("UTC"));

    private Element responseElement;
    private IQHandlerInfo info;

    public IQTimeHandler() {
        super("XMPP Server Time Handler");
        info = new IQHandlerInfo("query", "jabber:iq:time");
        responseElement = DocumentHelper.createElement(QName.get("query", "jabber:iq:time"));
        responseElement.addElement("utc");
        responseElement.addElement("tz").setText(TIME_FORMAT.getTimeZone().getDisplayName());
        responseElement.addElement("display");
    }

    public IQ handleIQ(IQ packet) {
        IQ response = null;
        response = IQ.createResultIQ(packet);
        response.setChildElement(buildResponse());
        return response;
    }

    /**
     * Build the responseElement packet
     */
    private Element buildResponse() {
        Element response = responseElement.createCopy();
        Date current = new Date();
        response.element("utc").setText(UTC_FORMAT.format(current));
        StringBuilder display = new StringBuilder(DATE_FORMAT.format(current));
        display.append(' ');
        display.append(TIME_FORMAT.format(current));
        response.element("display").setText(display.toString());
        return response;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator<String> getFeatures() {
        return Collections.singleton("jabber:iq:time").iterator();
    }
}