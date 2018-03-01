package org.jivesoftware.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Basic tests for code used by CertificateManager.
 *
 * @author Gaston Dombiak
 */
public class CertificateTest {

    /**
     * Verify that all CN elements are found.
     */
    @Test
    public void testCN() {
        Pattern cnPattern = Pattern.compile("(?i)(cn=)([^,]*)");
        String text = "EMAILADDRESS=XXXXX@scifi.com, CN=scifi.com, CN=jabber.scifi.com, OU=Domain validated only, O=XX, L=Skx, C=SE";
        List<String> found = new ArrayList<>();
        Matcher matcher = cnPattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(2));
        }
        assertEquals("Incorrect number of CNs were found", 2, found.size());
        assertEquals("Incorrect CN found", "scifi.com" , found.get(0));
        assertEquals("Incorrect CN found", "jabber.scifi.com" , found.get(1));
    }
}
