package org.jivesoftware.util;

import static org.junit.Assert.assertEquals;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;

public class XMPPDateTimeFormatTest {
    private final String TEST_DATE = "2013-01-25T18:07:22.768Z";
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private final XMPPDateTimeFormat xmppDateTimeFormat = new XMPPDateTimeFormat();
    
    @Test
    public void failTest() {
        Date parsedDate = null;
        try {
            parsedDate = xmppDateTimeFormat.parseString(TEST_DATE);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        df.setTimeZone(TimeZone.getTimeZone("utc"));
        String date = df.format(parsedDate);
        assertEquals(date, "2013-01-25T18:07:22.768+0000");
    }

    @Test
    public void testFormatNoSecondFractions() throws Exception
    {
        // Setup fixture
        final String testValue = "2015-03-19T22:54:15+00:00"; // Thu, 19 Mar 2015 22:54:15 GMT

        // Execute system under test
        final Date result = xmppDateTimeFormat.parseString(testValue);

        // Verify results
        long expected = 1426805655000L; // Epoch value of Thu, 19 Mar 2015 22:54:15 GMT
        assertEquals( expected, result.getTime() );
    }

    @Test
    public void testFormatThreeSecondFractions() throws Exception
    {
        // Setup fixture
        final String testValue = "2015-03-19T22:54:15.841+00:00"; // Thu, 19 Mar 2015 22:54:15.841 GMT

        // Execute system under test
        final Date result = xmppDateTimeFormat.parseString(testValue);

        // Verify results
        long expected = 1426805655841L; // Epoch value of Thu, 19 Mar 2015 22:54:15.841 GMT
        assertEquals( expected, result.getTime() );
    }

    @Test
    public void testFormatManySecondFractions() throws Exception
    {
        // Setup fixture
        final String testValue = "2015-03-19T22:54:15.841473+00:00"; // Thu, 19 Mar 2015 22:54:15.841473 GMT

        // Execute system under test
        final Date result = xmppDateTimeFormat.parseString(testValue);

        // Verify results
        long expected = 1426805655841L; // Epoch value of Thu, 19 Mar 2015 22:54:15 GMT
        assertEquals( expected, result.getTime() );
    }
}
