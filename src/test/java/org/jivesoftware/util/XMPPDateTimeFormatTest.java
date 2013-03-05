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
}
