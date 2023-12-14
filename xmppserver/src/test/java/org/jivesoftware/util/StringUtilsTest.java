/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class StringUtilsTest {
    
    @BeforeEach
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

    private void assertValidDomainName(String domain) {
        assertValidDomainName(domain, domain);
    }

    private void assertValidDomainName(String domain, String expected) {
        assertEquals(expected, StringUtils.validateDomainName(domain), "Domain should be valid: " + domain);
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
        assertThat(StringUtils.getFullElapsedTime(Duration.ofSeconds(1)), is("1 second"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofMillis(1001)), is("1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofSeconds(30).plus(Duration.ofMillis(30))), is("30 seconds, 30 ms"));
    }

    @Test
    public void testElapsedTimeInMinutes() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(Duration.ofMinutes(1)), is("1 minute"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofMinutes(1).plus(Duration.ofSeconds(1).plus(Duration.ofMillis(1)))), is("1 minute, 1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofMinutes(30).plus(Duration.ofSeconds(30))), is("30 minutes, 30 seconds"));
    }

    @Test
    public void testElapsedTimeInHours() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(Duration.ofHours(1)), is("1 hour"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofHours(1).plus(Duration.ofMinutes(1)).plus(Duration.ofSeconds(1)).plus(Duration.ofMillis(1))), is("1 hour, 1 minute, 1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofHours(10).plus(Duration.ofMinutes(30))), is("10 hours, 30 minutes"));
    }

    @Test
    public void testElapsedTimeInDays() throws Exception {
        assertThat(StringUtils.getFullElapsedTime(Duration.ofDays(1)), is("1 day"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofDays(1).plus(Duration.ofHours(1)).plus(Duration.ofMinutes(1)).plus(Duration.ofSeconds(1)).plus(Duration.ofMillis(1))), is("1 day, 1 hour, 1 minute, 1 second, 1 ms"));
        assertThat(StringUtils.getFullElapsedTime(Duration.ofDays(10).plus(Duration.ofHours(10))), is("10 days, 10 hours"));
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
        assertEquals(List.of("hello world"), StringUtils.shellSplit("\"hello world\""));
    }

    @Test
    public void singleQuotes() {
        assertEquals(List.of("hello world"), StringUtils.shellSplit("'hello world'"));
    }


    @Test
    public void escapedDoubleQuotes() {
        assertEquals(List.of("\"hello world\""), StringUtils.shellSplit("\"\\\"hello world\\\""));
    }

    @Test
    public void noEscapeWithinSingleQuotes() {
        assertEquals(List.of("hello \\\" world"), StringUtils.shellSplit("'hello \\\" world'"));
    }

    @Test
    public void backToBackQuotedStringsShouldFormSingleToken() {
        assertEquals(List.of("foobarbaz"), StringUtils.shellSplit("\"foo\"'bar'baz"));
        assertEquals(List.of("three four"), StringUtils.shellSplit("\"three\"' 'four"));
    }

    @Test
    public void escapedSpacesDoNotBreakUpTokens() {
        assertEquals(List.of("three four"), StringUtils.shellSplit("three\\ four"));
    }
}
