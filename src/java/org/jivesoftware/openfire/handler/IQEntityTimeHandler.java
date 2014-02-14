/**
 * $RCSfile$
 * $Revision: 1747 $
 * $Date: 2014-02-10 22:37:12 -0100 (Thu, 10 Feb 2014) $
 *
 * Copyright (C) 2004-2014 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.handler;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.xmpp.packet.IQ;

import javax.xml.bind.DatatypeConverter;
import java.util.*;

/**
 * This IQ handler implements XEP-0202: Entity Time.
 */
public final class IQEntityTimeHandler extends IQHandler implements ServerFeaturesProvider {

    private final IQHandlerInfo info;

    public IQEntityTimeHandler() {
        super("XEP-0202: Entity Time");
        info = new IQHandlerInfo("time", "urn:xmpp:time");
    }

    @Override
    public IQ handleIQ(IQ packet) {
        IQ response = IQ.createResultIQ(packet);
        Element timeElement = DocumentHelper.createElement(QName.get("time", "urn:xmpp:time"));
        timeElement.addElement("tzo").setText(formatsTimeZone(TimeZone.getDefault()));
        timeElement.addElement("utc").setText(getUtcDate(new Date()));
        response.setChildElement(timeElement);
        return response;
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton("urn:xmpp:time").iterator();
    }

    /**
     * Formats a {@link TimeZone} as specified in XEP-0082: XMPP Date and Time Profiles.
     *
     * @param tz The time zone.
     * @return The formatted time zone.
     */
    private String formatsTimeZone(TimeZone tz) {
        int seconds = Math.abs(tz.getRawOffset()) / 1000;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        return (tz.getRawOffset() < 0 ? "-" : "+") + String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Gets the ISO 8601 formatted date (UTC) as specified in XEP-0082: XMPP Date and Time Profiles.
     *
     * @param date The date.
     * @return The UTC formatted date.
     */
    private String getUtcDate(Date date) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        // This makes sure the date is formatted as the xs:dateTime type.
        return DatatypeConverter.printDateTime(calendar);
    }
}