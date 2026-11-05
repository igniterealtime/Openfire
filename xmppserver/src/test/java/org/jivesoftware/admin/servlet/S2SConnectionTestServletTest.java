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
package org.jivesoftware.admin.servlet;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Authentication;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.s2stest.CheckStatus;
import org.jivesoftware.openfire.server.s2stest.DomainValidator;
import org.jivesoftware.openfire.server.s2stest.LogSummary;
import org.jivesoftware.openfire.server.s2stest.PingOutcome;
import org.jivesoftware.openfire.server.s2stest.S2SCheck;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.S2STestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link S2SConnectionTestServlet}: CSRF protection, domain validation before any test runs, and the
 * checklist it hands to the page.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
public class S2SConnectionTestServletTest
{
    private static final String CSRF = "csrf-token";
    private static final String DOMAIN_PARAMETER = "server2server-testing-domain";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private RequestDispatcher dispatcher;

    private final List<JID> testedDomains = new ArrayList<>();
    private S2STestService.Result nextResult;
    private S2SConnectionTestServlet servlet;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        final DomainValidator validator = new DomainValidator(
            jid -> "example.com".equals(jid.getDomain()),
            jid -> false,
            domain -> !"blocked.example.net".equals(domain));
        servlet = new S2SConnectionTestServlet(validator, domain -> {
            testedDomains.add(domain);
            return nextResult;
        });
        lenient().when(request.getRequestDispatcher(S2SConnectionTestServlet.VIEW)).thenReturn(dispatcher);
        lenient().when(request.getServerName()).thenReturn("localhost");
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
    }

    private void post(final String domain, final String csrfParameter)
    {
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("csrf", CSRF) });
        when(request.getParameter("csrf")).thenReturn(csrfParameter);
        lenient().when(request.getParameter(DOMAIN_PARAMETER)).thenReturn(domain);
    }

    @SuppressWarnings("unchecked")
    private List<S2SConnectionTestServlet.Row> rows()
    {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("rows"), captor.capture());
        return (List<S2SConnectionTestServlet.Row>) captor.getValue();
    }

    private String attribute(final String name)
    {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq(name), captor.capture());
        return Objects.toString(captor.getValue());
    }

    @Test
    public void testGetShowsTheFormWithoutRunningATest() throws Exception
    {
        // Execute system under test.
        servlet.doGet(request, response);

        // Verify results.
        assertTrue(testedDomains.isEmpty(), "opening the page must not run a test");
        verify(request, never().description("no checklist must be shown before a test")).setAttribute(eq("rows"), any());
        verify(dispatcher, times(1).description("the page must be rendered")).forward(request, response);
    }

    /**
     * The failed-session details page links here with the domain in the address ("Retry now"). The domain fills the
     * form, and the test runs only when the administrator submits it, as a GET carries no CSRF token.
     */
    @Test
    public void testGetWithADomainFillsTheFormWithoutRunningATest() throws Exception
    {
        // Setup test fixture.
        when(request.getParameter(DOMAIN_PARAMETER)).thenReturn("remote.example.org");

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify results.
        assertEquals("remote.example.org", attribute("s2sDomain"), "the domain in the address must fill the form");
        assertTrue(testedDomains.isEmpty(), "opening the page with a domain must not run a test");
        verify(request, never().description("no checklist must be shown before a test")).setAttribute(eq("rows"), any());
    }

    @Test
    public void testCsrfMismatchRunsNoTest() throws Exception
    {
        // Setup test fixture.
        post("remote.example.org", "forged");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertTrue(testedDomains.isEmpty(), "a request failing the CSRF check must not run a test");
        assertFalse(attribute("errorMessage").startsWith("???"), "the CSRF error must be explained with a real message");
        verify(dispatcher, times(1).description("the page must still be rendered")).forward(request, response);
    }

    @Test
    public void testInvalidDomainFailsCheckOneWithoutRunningATest() throws Exception
    {
        // Setup test fixture.
        post("not a domain", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertTrue(testedDomains.isEmpty(), "an invalid domain must not be tested");
        final List<S2SConnectionTestServlet.Row> rows = rows();
        assertEquals(11, rows.size(), "the checklist must show all 11 checks");
        assertEquals(CheckStatus.FAIL, rows.get(0).getStatus(), "check 1 must fail for an invalid domain");
        assertTrue(rows.get(0).getDetail().contains("not a domain"), "the failure must show the value that was entered");
        assertTrue(rows.stream().skip(1).allMatch(row -> row.getStatus() == CheckStatus.NOT_REACHED), "no other check is reached when the domain is rejected");
    }

    @Test
    public void testLocalDomainFailsCheckOne() throws Exception
    {
        // Setup test fixture.
        post("example.com", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertTrue(testedDomains.isEmpty(), "a local domain must not be tested");
        assertEquals(CheckStatus.FAIL, rows().get(0).getStatus(), "check 1 must fail for a local domain");
    }

    @Test
    public void testBlockedDomainFailsCheckOne() throws Exception
    {
        // Setup test fixture.
        post("blocked.example.net", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertTrue(testedDomains.isEmpty(), "a domain blocked by the server-to-server policy must not be tested");
        assertEquals(CheckStatus.FAIL, rows().get(0).getStatus(), "check 1 must fail for a blocked domain");
    }

    @Test
    public void testValidDomainIsTestedAndTheChecklistBuiltFromTheResult() throws Exception
    {
        // Setup test fixture: the attempt recorded only how it authenticated.
        final AttemptMilestones milestones = AttemptMilestones.builder().authentication(Authentication.saslExternal()).build();
        nextResult = new S2STestService.Result(PingOutcome.result(), true, milestones, new LogSummary(0, 0, 0), "<iq/>", "certs", "logs");
        post("https://remote.example.org/", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertEquals(List.of(new JID("remote.example.org")), testedDomains, "the normalised domain must be tested, once");
        final List<S2SConnectionTestServlet.Row> rows = rows();
        assertEquals(CheckStatus.PASS, rows.get(0).getStatus(), "check 1 must pass");
        assertEquals(CheckStatus.NOT_OBSERVED, rows.get(1).getStatus(), "steps the attempt did not record must show as not observed");
        assertEquals(CheckStatus.PASS, rows.get(6).getStatus(), "check 7 must pass for a recorded certificate login");
        assertEquals(CheckStatus.PASS, rows.get(7).getStatus(), "check 8 must pass for a registered session");
        assertEquals(CheckStatus.PASS, rows.get(8).getStatus(), "check 9 must pass for a reply from the remote");
        assertEquals(CheckStatus.PASS, rows.get(9).getStatus(), "check 10 must pass for a result");
        assertEquals(CheckStatus.PASS, rows.get(10).getStatus(), "check 11 must be judged from the log of the attempt");
        assertEquals("<iq/>", attribute("stanzas"), "the raw XMPP output must be passed to the page");
        assertFalse(attribute("summary").startsWith("???"), "the summary must be a real message");
        assertEquals("info", attribute("summaryType"), "with steps not observed and nothing failed, the summary must be informational");
        for (final S2SConnectionTestServlet.Row row : rows) {
            assertFalse(row.getTitle().contains("???") || row.getDetail().contains("???") || row.getStatusLabel().contains("???"), "every row must use messages that exist: " + row);
        }
    }

    @Test
    public void testFailedTestSummaryNamesTheFailedCheck() throws Exception
    {
        // Setup test fixture: no session, and only this server answered.
        nextResult = new S2STestService.Result(PingOutcome.localError("remote-server-not-found"), false, null, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertEquals("error", attribute("summaryType"), "a failed test must show an error summary");
        assertTrue(attribute("summary").contains("8"), "the summary must name the first failed check, the session");
        assertFalse(attribute("summary").contains(LocaleUtils.getLocalizedString(S2SCheck.SESSION.titleKey())),
            "the summary must not quote the check title, which states the passing outcome");
        assertTrue(rows().get(7).isFirstFailure(), "the first failed row must be marked for highlighting");
    }

    @Test
    public void testRecordedMilestonesFillTheirChecks() throws Exception
    {
        // Setup test fixture: the attempt recorded its TCP connection, but not how it authenticated (as when a session
        // was reused, or another cluster node holds the route), while an authenticated session is registered.
        final AttemptMilestones milestones = AttemptMilestones.builder().tcp(Tcp.connected("192.0.2.10:5269")).build();
        nextResult = new S2STestService.Result(PingOutcome.result(), true, milestones, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        final List<S2SConnectionTestServlet.Row> rows = rows();
        assertEquals(CheckStatus.PASS, rows.get(2).getStatus(), "the recorded TCP connection must fill check 3");
        assertTrue(rows.get(2).getDetail().contains("192.0.2.10:5269"), "check 3 must show the endpoint the attempt recorded");
        assertEquals(CheckStatus.NOT_OBSERVED, rows.get(1).getStatus(), "a step the attempt did not record must stay not observed");
        assertEquals(CheckStatus.NOT_OBSERVED, rows.get(6).getStatus(), "a registered session must not fill check 7: only what this attempt recorded can pass a check");
    }

    @SuppressWarnings("unchecked")
    private List<S2SConnectionTestServlet.AdviceItem> advice()
    {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("advice"), captor.capture());
        return (List<S2SConnectionTestServlet.AdviceItem>) captor.getValue();
    }

    @Test
    public void testAFailureComesWithAdviceOnWhatToDoNext() throws Exception
    {
        // Setup test fixture: the domain does not resolve.
        final AttemptMilestones milestones = AttemptMilestones.builder().dns(AttemptMilestones.Dns.noAddress()).build();
        nextResult = new S2STestService.Result(PingOutcome.localError("remote-server-not-found"), false, milestones, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        final List<S2SConnectionTestServlet.AdviceItem> advice = advice();
        assertEquals(1, advice.size(), "a failed test must come with advice for its first failed check only");
        final S2SConnectionTestServlet.AdviceItem item = advice.get(0);
        assertEquals(LocaleUtils.getLocalizedString("server2server.settings.testing.advice.check", List.of(2, LocaleUtils.getLocalizedString(S2SCheck.DNS.titleKey()))), item.getHeading(), "the advice must name the check it is about");
        assertEquals(LocaleUtils.getLocalizedString("server2server.settings.testing.advice.dns.fail.local"), item.getLocal(), "the advice must say what to look at on this server");
        assertEquals(List.of("dns-overrides.jsp"), item.getLinks().stream().map(S2SConnectionTestServlet.Link::getUrl).toList(), "the advice must link to the admin console page that holds the setting");
        assertEquals(LocaleUtils.getLocalizedString("server2server.settings.testing.advice.dns.fail.remote"), item.getRemote(), "the advice must say what could be wrong on the remote server");
    }

    @Test
    public void testNameMismatchAdviceNamesTheDomainAndTheCertificateNames() throws Exception
    {
        // Setup test fixture: the encrypted connection worked, but the certificate is issued for another name.
        final AttemptMilestones milestones = AttemptMilestones.builder()
            .tls(AttemptMilestones.Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"))
            .certificate(AttemptMilestones.Certificate.nameMismatch(List.of("other.example.org"))).build();
        nextResult = new S2STestService.Result(PingOutcome.localError("remote-server-not-found"), false, milestones, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        final S2SConnectionTestServlet.AdviceItem item = advice().get(0);
        assertTrue(item.getLocal().contains("other.example.org"), "the advice must name what the certificate is issued for: " + item.getLocal());
        assertTrue(item.getLocal().contains("remote.example.org"), "the advice must name the domain that was tested: " + item.getLocal());
        assertNotNull(item.getNote(), "certificate advice must point to the server-wide settings that let such a connection go ahead");
    }

    @Test
    public void testExpiredCertificateAdviceHasNothingToDoOnThisServer() throws Exception
    {
        // Setup test fixture: this server stopped the handshake, as the remote certificate has expired.
        final AttemptMilestones milestones = AttemptMilestones.builder()
            .tls(AttemptMilestones.Tls.certificateRejected())
            .certificate(AttemptMilestones.Certificate.expired(java.time.Instant.parse("2026-10-06T09:17:19Z"))).build();
        nextResult = new S2STestService.Result(PingOutcome.localError("remote-server-not-found"), false, milestones, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        final S2SConnectionTestServlet.AdviceItem item = advice().get(0);
        assertTrue(item.getHeading().startsWith(LocaleUtils.getLocalizedString("server2server.settings.testing.advice.check", List.of(6, LocaleUtils.getLocalizedString(S2SCheck.CERTIFICATE.titleKey())))), "a certificate rejected during the handshake must get the advice of check 6, which says why: " + item.getHeading());
        assertNull(item.getLocal(), "nothing on this server can make an expired certificate valid, so the advice must not suggest anything here");
        assertNotNull(item.getRemote(), "the advice must say what could be wrong on the remote server");
    }

    @Test
    public void testADnsOverrideThatWasUsedIsNamedInTheAdvice() throws Exception
    {
        // Setup test fixture: a DNS override on this server sent the connection to a host that closed it without a stream.
        final AttemptMilestones milestones = AttemptMilestones.builder()
            .dns(AttemptMilestones.Dns.override(List.of(AttemptMilestones.Dns.Target.plain("xmpp.example.org:5269"))))
            .tcp(Tcp.connected("192.0.2.10:5269"))
            .stream(AttemptMilestones.Stream.closedByRemote()).build();
        nextResult = new S2STestService.Result(PingOutcome.localError("remote-server-not-found"), false, milestones, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        final S2SConnectionTestServlet.AdviceItem item = advice().get(0);
        assertNotNull(item.getDnsOverride(), "the advice must point to the DNS override that decided where this server connected");
        assertTrue(item.getDnsOverride().contains("xmpp.example.org:5269"), "the advice must name where the override sent the connection: " + item.getDnsOverride());
        assertTrue(item.getLinks().stream().anyMatch(link -> link.getUrl().equals("dns-overrides.jsp")), "the advice must link to the DNS Overrides page");
    }

    @Test
    public void testAPassingTestComesWithNoAdvice() throws Exception
    {
        // Setup test fixture.
        nextResult = new S2STestService.Result(PingOutcome.result(), true, AttemptMilestones.builder().build(), null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertTrue(advice().isEmpty(), "a test with no failure or warning needs no advice");
    }

    @Test
    public void testRecordedAuthenticationFillsCheckSeven() throws Exception
    {
        // Setup test fixture: the attempt recorded why dialback was used.
        final AttemptMilestones milestones = AttemptMilestones.builder().authentication(Authentication.dialbackAfterExternalRefused()).build();
        nextResult = new S2STestService.Result(PingOutcome.result(), true, milestones, null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertEquals(CheckStatus.WARNING, rows().get(6).getStatus(), "a recorded fallback to Server Dialback must make check 7 a warning");
        assertTrue(rows().get(6).getDetail().contains("refused"), "check 7 must give the reason the attempt recorded");
    }

    @Test
    public void testComposedTextOfADetailIsTranslatedFromTheResourceBundle() throws Exception
    {
        // Setup test fixture: the connection to every address failed, each in its own way.
        final Tcp tcp = Tcp.allFailed(List.of(Tcp.AddressFailure.refused("192.0.2.10:5269"), Tcp.AddressFailure.failed("192.0.2.11:5269", "No route to host")));
        nextResult = new S2STestService.Result(PingOutcome.localError("remote-server-not-found"), false, AttemptMilestones.builder().tcp(tcp).build(), null, "", "", "");
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertEquals("The connection to 192.0.2.10:5269, 192.0.2.11:5269 failed: 192.0.2.10:5269 refused; 192.0.2.11:5269 No route to host", rows().get(2).getDetail(),
            "the list of addresses and what happened at each must be translated from the resource bundle, like the rest of the detail");
    }

    @Test
    public void testInterruptionDuringTheTestIsPassedOn() throws Exception
    {
        // Setup test fixture: the test is interrupted while it waits, as when the server shuts down.
        servlet = new S2SConnectionTestServlet(new DomainValidator(jid -> false, jid -> false, domain -> true), domain -> {
            throw new InterruptedException("shutting down");
        });
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        final boolean interrupted = Thread.interrupted(); // Also clears the flag, so that it does not affect other tests.
        assertTrue(interrupted, "an interruption must be passed on to the request thread, not swallowed");
        assertFalse(attribute("errorMessage").startsWith("???"), "the page must still say that the test could not complete");
    }

    @Test
    public void testErrorDuringTheTestIsReported() throws Exception
    {
        // Setup test fixture.
        servlet = new S2SConnectionTestServlet(new DomainValidator(jid -> false, jid -> false, domain -> true), domain -> {
            throw new IllegalStateException("boom");
        });
        post("remote.example.org", CSRF);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify results.
        assertFalse(attribute("errorMessage").startsWith("???"), "an unexpected error must be reported with a real message");
        verify(request, never().description("no checklist must be shown when the test could not run")).setAttribute(eq("rows"), any());
    }
}
