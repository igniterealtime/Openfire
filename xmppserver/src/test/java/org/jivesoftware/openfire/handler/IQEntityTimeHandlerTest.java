package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.Ignore;
import org.junit.Test;
import org.xmpp.packet.IQ;

import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test @Ignore
    public void testPerformanceDatatypeConvertVsXMPPDateFormat() {

        Date date = new Date();
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        calendar.setTime(date);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {

            DatatypeConverter.printDateTime(calendar);
        }
        assertThat(System.currentTimeMillis() - start, is(lessThan(2000L)));

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            XMPPDateTimeFormat.format(date);
        }
        assertThat(System.currentTimeMillis() - start, is(lessThan(4000L)));
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
        assertEquals(((Element) result.getChildElement().content().get(0)).getName(), "tzo");
        assertEquals(((Element) result.getChildElement().content().get(1)).getName(), "utc");
    }
}
