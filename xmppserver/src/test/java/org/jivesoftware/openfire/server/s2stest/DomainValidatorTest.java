/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.server.s2stest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmpp.packet.JID;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies how {@link DomainValidator} judges the domain an administrator asks to test, which decides check 1.
 *
 * @author Matthew Vivian
 */
public class DomainValidatorTest
{
    private static final String LOCAL = "example.com";
    private static final String COMPONENT = "conference.example.com";
    private static final String BLOCKED = "blocked.example.net";

    private final DomainValidator validator = new DomainValidator(
        jid -> LOCAL.equals(jid.getDomain()),
        jid -> COMPONENT.equals(jid.getDomain()),
        domain -> !BLOCKED.equals(domain));

    @Test
    public void testRemoteDomainIsValid()
    {
        // Execute system under test.
        final DomainValidation result = validator.validate("remote.example.org");

        // Verify results.
        assertEquals(DomainCheck.VALID, result.check(), "a remote domain allowed by the policy must be valid");
        assertEquals("remote.example.org", result.domain(), "the domain to test must be the one entered");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://remote.example.org/", "http://remote.example.org", "  remote.example.org  ", "remote.example.org/"})
    public void testCommonCopyAndPasteMistakesAreForgiven(final String input)
    {
        // Execute system under test.
        final DomainValidation result = validator.validate(input);

        // Verify results.
        assertEquals(DomainCheck.VALID, result.check(), "a URL scheme, surrounding spaces or a trailing slash must not make the domain invalid");
        assertEquals("remote.example.org", result.domain(), "the domain must be extracted from the pasted value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a domain", "", "   ", "remote..example.org"})
    public void testMalformedDomainIsInvalid(final String input)
    {
        // Execute system under test.
        final DomainValidation result = validator.validate(input);

        // Verify results.
        assertEquals(DomainCheck.INVALID, result.check(), "a value that is not an XMPP domain must be rejected");
        assertEquals(input.trim(), result.domain(), "an invalid value must be reported as entered, for the page to show");
    }

    @Test
    public void testNullIsInvalid()
    {
        assertEquals(DomainCheck.INVALID, validator.validate(null).check(), "a missing value must be rejected");
    }

    @Test
    public void testLocalDomainIsRejected()
    {
        assertEquals(DomainCheck.LOCAL, validator.validate(LOCAL).check(), "a domain this server serves has no remote server to test");
    }

    @Test
    public void testComponentIsRejected()
    {
        assertEquals(DomainCheck.COMPONENT, validator.validate(COMPONENT).check(), "a component of this server has no remote server to test");
    }

    @Test
    public void testDomainBlockedByPolicyIsRejected()
    {
        assertEquals(DomainCheck.BLOCKED_BY_POLICY, validator.validate(BLOCKED).check(), "a domain the server-to-server policy blocks must not be tested");
    }

    @Test
    public void testPolicyIsNotConsultedForInvalidInput()
    {
        // Setup test fixture.
        final AtomicBoolean consulted = new AtomicBoolean();
        final Predicate<String> policy = domain -> {
            consulted.set(true);
            return true;
        };
        final DomainValidator validatorWithSpy = new DomainValidator(jid -> false, jid -> false, policy);

        // Execute system under test.
        final DomainValidation result = validatorWithSpy.validate("not a domain");

        // Verify results.
        assertEquals(DomainCheck.INVALID, result.check(), "the value must be rejected as invalid");
        assertFalse(consulted.get(), "the policy must not be asked about a value that is not a domain");
    }

    @Test
    public void testAddressWithUserPartTestsItsDomain()
    {
        // Execute system under test.
        final DomainValidation result = validator.validate("user@remote.example.org");

        // Verify results.
        assertEquals(DomainCheck.VALID, result.check(), "an address must be accepted, as the page always has");
        assertEquals("remote.example.org", result.domain(), "only the domain part of an address is tested");
        assertEquals(new JID("remote.example.org").getDomain(), result.domain(), "the domain must be in its normalised form");
    }
}
