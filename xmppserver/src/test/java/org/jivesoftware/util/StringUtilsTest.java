package org.jivesoftware.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

public class StringUtilsTest {
    
    @Before
    public void setUp() {
        JiveGlobals.setLocale(Locale.ENGLISH);
    }
    
    @Test
    public void testValidDomainNames() {
        
        assertValidDomainName("www.mycompany.com");
        assertValidDomainName("www.my-company.com");
        assertValidDomainName("abc.de");
        assertValidDomainName("tronçon.be", "xn--tronon-zua.be");
        assertValidDomainName("öbb.at", "xn--bb-eka.at");

    }
    
    @Test
    public void testInvalidDomainNames() {
        
        assertInvalidDomainName("www.my_company.com", "Contains non-LDH characters");
        assertInvalidDomainName("www.-dash.com", "Has leading or trailing hyphen");
        assertInvalidDomainName("www.dash-.com", "Has leading or trailing hyphen");
        assertInvalidDomainName("abc.<test>.de", "Contains non-LDH characters");

    }

    @Test
    public void testStringReplace() {
        assertEquals(StringUtils.replace("Hello Foo Foo", "Foo", "World"), "Hello World World");
        assertEquals(StringUtils.replace("Hello Foo foo", "Foo", "World"), "Hello World foo");
        assertEquals(StringUtils.replaceIgnoreCase("Hello Foo foo", "Foo", "World"), "Hello World World");
        int[] count = new int[1];
        assertEquals(StringUtils.replaceIgnoreCase("Hello Foo foo", "Foo", "World", count), "Hello World World");
        assertEquals(count[0], 2);
    }

    private void assertValidDomainName(String domain) {
        assertValidDomainName(domain, domain);
    }

    private void assertValidDomainName(String domain, String expected) {
        assertEquals("Domain should be valid: " + domain, expected, StringUtils.validateDomainName(domain));
    }

    private void assertInvalidDomainName(String domain, String expectedCause) {
        try {
            StringUtils.validateDomainName(domain);
            fail("Domain should not be valid: " + domain);
        } catch (IllegalArgumentException iae) {
            // this is not part of the official API, so leave off for now
            //assertEquals("Unexpected cause: " + iae.getMessage(), expectedCause, iae.getMessage());
        }
    }

    @Test
    public void testElapsedTimeInMilliseconds() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(0), is("0 ms"));
        assertThat(StringUtils.getFullElapsedTime(1), is("1 ms"));
        assertThat(StringUtils.getFullElapsedTime(250), is("250 ms"));
    }

    @Test
    public void testElapsedTimeInSeconds() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.SECOND), is("1 second"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.SECOND + 1), is("1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.SECOND * 30 + 30), is("30 seconds, 30 ms"));
    }

    @Test
    public void testElapsedTimeInMinutes() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.MINUTE), is("1 minute"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.MINUTE + JiveConstants.SECOND + 1), is("1 minute, 1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.MINUTE * 30 + JiveConstants.SECOND * 30), is("30 minutes, 30 seconds"));
    }

    @Test
    public void testElapsedTimeInHours() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.HOUR), is("1 hour"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.HOUR + JiveConstants.MINUTE + JiveConstants.SECOND + 1), is("1 hour, 1 minute, 1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.HOUR * 10 + JiveConstants.MINUTE * 30), is("10 hours, 30 minutes"));
    }

    @Test
    public void testElapsedTimeInDays() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.DAY), is("1 day"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.DAY + JiveConstants.HOUR + JiveConstants.MINUTE + JiveConstants.SECOND + 1), is("1 day, 1 hour, 1 minute, 1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(JiveConstants.DAY * 10 + JiveConstants.HOUR * 10), is("10 days, 10 hours"));
    }

    // shellSplit tests, from https://gist.github.com/raymyers/8077031

    @Test
    public void blankYieldsEmptyArgs() {
        assertTrue(StringUtils.shellSplit("").isEmpty());
    }

    @Test
    public void whitespacesOnlyYeildsEmptyArgs() {
        assertTrue(StringUtils.shellSplit("  \t \n").isEmpty());
    }

    @Test
    public void normalTokens() {
        assertEquals(Arrays.asList("a", "bee", "cee"), StringUtils.shellSplit("a\tbee  cee"));
    }

    @Test
    public void doubleQuotes() {
        assertEquals(Arrays.asList("hello world"), StringUtils.shellSplit("\"hello world\""));
    }

    @Test
    public void singleQuotes() {
        assertEquals(Arrays.asList("hello world"), StringUtils.shellSplit("'hello world'"));
    }


    @Test
    public void escapedDoubleQuotes() {
        assertEquals(Arrays.asList("\"hello world\""), StringUtils.shellSplit("\"\\\"hello world\\\""));
    }

    @Test
    public void noEscapeWithinSingleQuotes() {
        assertEquals(Arrays.asList("hello \\\" world"), StringUtils.shellSplit("'hello \\\" world'"));
    }

    @Test
    public void backToBackQuotedStringsShouldFormSingleToken() {
        assertEquals(Arrays.asList("foobarbaz"), StringUtils.shellSplit("\"foo\"'bar'baz"));
        assertEquals(Arrays.asList("three four"), StringUtils.shellSplit("\"three\"' 'four"));
    }

    @Test
    public void escapedSpacesDoNotBreakUpTokens() {
        assertEquals(Arrays.asList("three four"), StringUtils.shellSplit("three\\ four"));
    }
}
