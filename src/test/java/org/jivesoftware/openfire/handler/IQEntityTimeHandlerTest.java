package org.jivesoftware.openfire.handler;

import junit.framework.Assert;
import org.dom4j.Element;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.Test;
import org.xmpp.packet.IQ;

import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * @author csh
 */
public class IQEntityTimeHandlerTest {

    @Test
    public void testIQInfo() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        Assert.assertTrue(iqEntityTimeHandler.getFeatures().hasNext());
        Assert.assertEquals(iqEntityTimeHandler.getFeatures().next(), "urn:xmpp:time");
        Assert.assertEquals(iqEntityTimeHandler.getInfo().getNamespace(), "urn:xmpp:time");
        Assert.assertEquals(iqEntityTimeHandler.getInfo().getName(), "time");
    }

    @Test
    public void testTimeZone() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        Assert.assertEquals(iqEntityTimeHandler.formatsTimeZone(TimeZone.getTimeZone("GMT-8:00")), "-08:00");
    }

    @Test
    public void testUtcDate() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        Date date = new Date();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        Assert.assertEquals(iqEntityTimeHandler.getUtcDate(date), DatatypeConverter.printDateTime(calendar));
    }

    @Test
    public void testPerformanceDatatypeConvertVsXMPPDateFormat() {

        Date date = new Date();
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        calendar.setTime(date);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {

            DatatypeConverter.printDateTime(calendar);
        }
        System.out.println(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            XMPPDateTimeFormat.format(date);
        }
        System.out.println(System.currentTimeMillis() - start);
    }

    @Test
    public void testIQ() {
        IQEntityTimeHandler iqEntityTimeHandler = new IQEntityTimeHandler();
        IQ input = new IQ(IQ.Type.get, "1");
        IQ result = iqEntityTimeHandler.handleIQ(input);
        Assert.assertEquals(result.getChildElement().getName(), "time");
        Assert.assertEquals(result.getChildElement().getNamespace().getText(), "urn:xmpp:time");
        Assert.assertEquals(result.getChildElement().content().size(), 2);
        Assert.assertTrue(result.getChildElement().content().get(0) instanceof Element);
        Assert.assertTrue(result.getChildElement().content().get(1) instanceof Element);
        Assert.assertEquals(((Element) result.getChildElement().content().get(0)).getName(), "tzo");
        Assert.assertEquals(((Element) result.getChildElement().content().get(1)).getName(), "utc");
    }
}
