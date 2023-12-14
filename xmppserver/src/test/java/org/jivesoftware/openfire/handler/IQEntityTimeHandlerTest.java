/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;

import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author csh
 */
public class IQEntityTimeHandlerTest {

    @Test
    public void testIQInfo() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        assertTrue(iqEntityTimeHandler.getFeatures().hasNext());
        assertEquals(iqEntityTimeHandler.getFeatures().next(), "urn:xmpp:time");
        assertEquals(iqEntityTimeHandler.getInfo().getNamespace(), "urn:xmpp:time");
        assertEquals(iqEntityTimeHandler.getInfo().getName(), "time");
    }

    @Test
    public void testTimeZone() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        assertEquals(iqEntityTimeHandler.formatsTimeZone(TimeZone.getTimeZone("GMT-8:00")), "-08:00");
    }

    @Test
    public void testUtcDate() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        Date date = new Date();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        assertEquals(iqEntityTimeHandler.getUtcDate(date), DatatypeConverter.printDateTime(calendar));
    }

    @Test @Disabled
    public void testPerformanceDatatypeConvertVsXMPPDateFormat() {

        Date date = new Date();
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        calendar.setTime(date);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {

            DatatypeConverter.printDateTime(calendar);
        }
        long durationA = System.currentTimeMillis() - start;
        assertThat(durationA, is(lessThan(2000L)));

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            XMPPDateTimeFormat.format(date);
        }
        long durationB = System.currentTimeMillis() - start;
        assertThat(durationB, is(lessThan(4000L)));

        assertThat(durationA, is(lessThan(durationB)));
    }

    @Test
    public void testIQ() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        IQ input = new IQ(IQ.Type.get, "1");
        IQ result = iqEntityTimeHandler.handleIQ(input);
        assertEquals(result.getChildElement().getName(), "time");
        assertEquals(result.getChildElement().getNamespace().getText(), "urn:xmpp:time");
        assertEquals(result.getChildElement().content().size(), 2);
        assertTrue(result.getChildElement().content().get(0) instanceof Element);
        assertTrue(result.getChildElement().content().get(1) instanceof Element);
        assertEquals(result.getChildElement().content().get(0).getName(), "tzo");
        assertEquals(result.getChildElement().content().get(1).getName(), "utc");
    }
}
