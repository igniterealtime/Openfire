/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.spi.IQImpl;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * Implements the TYPE_IQ jabber:iq:time protocol (time info). Allows
 * Jabber entities to query each other's local time.  The server
 * will respond with its local time.
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

    private Element responseElement;
    private IQHandlerInfo info;

    public IQTimeHandler() {
        super("XMPP Server Time Handler");
        info = new IQHandlerInfo("query", "jabber:iq:time", IQImpl.class);
        responseElement = DocumentHelper.createElement(QName.get("query", "jabber:iq:time"));
        responseElement.addElement("utc");
        responseElement.addElement("tz").setText(TIME_FORMAT.getTimeZone().getDisplayName());
        responseElement.addElement("display");
    }

    public IQ handleIQ(IQ packet) {
        IQ response = null;

        buildResponse();

        response = packet.createResult(responseElement);
        return response;
    }

    /**
     * Build the responseElement packet
     */
    private void buildResponse() {
        Date current = new Date();
        responseElement.element("utc").setText(UTC_FORMAT.format(current));
        StringBuffer display = new StringBuffer(DATE_FORMAT.format(current));
        display.append(' ');
        display.append(TIME_FORMAT.format(current));
        responseElement.element("display").setText(display.toString());
    }


    // todo Make display text match the locale of user (xml:lang support)
    // #################################################################
    // Standard formatting according to locale and Jabber specs
    // #################################################################
    private static final DateFormat DATE_FORMAT = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private static final DateFormat TIME_FORMAT = DateFormat.getTimeInstance(DateFormat.LONG);
    private static final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");

    static {
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:time");
        return features.iterator();
    }
}
