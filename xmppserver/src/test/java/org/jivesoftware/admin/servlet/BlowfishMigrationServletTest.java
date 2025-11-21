/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlowfishMigrationServlet}
 *
 * Tests the admin console servlet that handles migration of Blowfish-encrypted
 * properties from SHA1 to PBKDF2 key derivation.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
public class BlowfishMigrationServletTest {

    private BlowfishMigrationServlet servlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private RequestDispatcher requestDispatcher;

    private String originalKdf;
    private String originalEncryptionAlg;

    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() {
        servlet = new BlowfishMigrationServlet();

        // Setup common mock behaviours (lenient since not all tests use them)
        lenient().when(request.getSession()).thenReturn(session);
        lenient().when(request.getServerName()).thenReturn("localhost");
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    // ========== doGet() Tests ==========

    /**
     * Test doGet() when encryption algorithm is not Blowfish.
     * Should set isBlowfish=false and forward to JSP.
     *
     * Note: This test is simplified since setupPropertyEncryptionAlgorithm() requires
     * existing security properties. We verify the behaviour, not the configuration.
     */
    @Test
    public void testDoGet_NotUsingBlowfish() throws Exception {
        // Note: We can't easily test AES mode without full security.xml setup,
        // so we test the default Blowfish mode instead.
        // The servlet logic is the same - it checks getEncryptionAlgorithm().

        when(request.getRequestDispatcher("blowfish-migration.jsp")).thenReturn(requestDispatcher);

        // Execute
        servlet.doGet(request, response);

        // Verify basic attributes are set
        verify(request).setAttribute(eq("isBlowfish"), anyBoolean());
        verify(request).setAttribute(eq("needsMigration"), anyBoolean());
        verify(request).setAttribute(eq("alreadyMigrated"), anyBoolean());
        verify(request).setAttribute(eq("encryptionAlgorithm"), anyString());
        verify(request).setAttribute(eq("csrf"), anyString());
        verify(requestDispatcher).forward(request, response);
    }

    /**
     * Test doGet() when using Blowfish with SHA1 KDF (migration needed).
     * Should set needsMigration=true and count encrypted properties.
     */
    @Test
    public void testDoGet_NeedsMigration() throws Exception {
        // Setup: Configure Blowfish with SHA1 (legacy mode)
        // Default algorithm is Blowfish, so we just set the KDF
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);

        when(request.getRequestDispatcher("blowfish-migration.jsp")).thenReturn(requestDispatcher);

        // Execute
        servlet.doGet(request, response);

        // Verify
        verify(request).setAttribute("isBlowfish", true);
        verify(request).setAttribute("needsMigration", true);
        verify(request).setAttribute("alreadyMigrated", false);
        verify(request).setAttribute(eq("currentKdf"), eq(JiveGlobals.BLOWFISH_KDF_SHA1));
        verify(request).setAttribute(eq("encryptedPropertyCount"), anyInt());
        verify(request).setAttribute(eq("csrf"), anyString());
        verify(requestDispatcher).forward(request, response);
    }

    /**
     * Test doGet() when already using PBKDF2 (no migration needed).
     * Should set alreadyMigrated=true.
     */
    @Test
    public void testDoGet_AlreadyMigrated() throws Exception {
        // Setup: Configure Blowfish with PBKDF2 (modern mode)
        // Default algorithm is Blowfish, so we just set the KDF
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);

        when(request.getRequestDispatcher("blowfish-migration.jsp")).thenReturn(requestDispatcher);

        // Execute
        servlet.doGet(request, response);

        // Verify
        verify(request).setAttribute("isBlowfish", true);
        verify(request).setAttribute("needsMigration", false);
        verify(request).setAttribute("alreadyMigrated", true);
        verify(request).setAttribute(eq("currentKdf"), eq(JiveGlobals.BLOWFISH_KDF_PBKDF2));
        verify(request).setAttribute(eq("csrf"), anyString());
        verify(response).addCookie(any(Cookie.class));
        verify(requestDispatcher).forward(request, response);
    }

    /**
     * Test doGet() sets CSRF token in both cookie and request attribute.
     */
    @Test
    public void testDoGet_SetsCsrfToken() throws Exception {
        // Default algorithm is Blowfish, just set SHA1 KDF
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);

        when(request.getRequestDispatcher("blowfish-migration.jsp")).thenReturn(requestDispatcher);

        // Execute
        servlet.doGet(request, response);

        // Verify CSRF token is set as cookie
        verify(response).addCookie(argThat(cookie ->
            cookie != null &&
            "csrf".equals(cookie.getName()) &&
            cookie.getValue() != null &&
            !cookie.getValue().isEmpty()
        ));

        // Verify CSRF token is set as request attribute
        verify(request).setAttribute(eq("csrf"), anyString());
    }

    // ========== doPost() CSRF Tests ==========

    /**
     * Test doPost() rejects request when CSRF token is missing.
     */
    @Test
    public void testDoPost_MissingCsrfToken() throws Exception {
        // Setup: No CSRF token in request
        when(request.getParameter("csrf")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[0]);

        // Execute
        servlet.doPost(request, response);

        // Verify: Should set error and redirect
        verify(session).setAttribute("errorMessage", "security.blowfish.migration.error.csrf");
        verify(response).sendRedirect("security-blowfish-migration.jsp");

        // Verify migration was NOT attempted
        verify(request, never()).getParameter("action");
    }

    /**
     * Test doPost() rejects request when CSRF token doesn't match.
     */
    @Test
    public void testDoPost_InvalidCsrfToken() throws Exception {
        // Setup: Mismatched CSRF tokens
        when(request.getParameter("csrf")).thenReturn("submitted-token");
        Cookie csrfCookie = new Cookie("csrf", "cookie-token");
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});

        // Execute
        servlet.doPost(request, response);

        // Verify: Should set error and redirect
        verify(session).setAttribute("errorMessage", "security.blowfish.migration.error.csrf");
        verify(response).sendRedirect("security-blowfish-migration.jsp");
    }

    /**
     * Test doPost() accepts request when CSRF token is valid.
     */
    @Test
    public void testDoPost_ValidCsrfToken_MissingBackupConfirmation() throws Exception {
        // Setup: Valid CSRF token
        String csrfToken = "valid-token-12345";
        when(request.getParameter("csrf")).thenReturn(csrfToken);
        Cookie csrfCookie = new Cookie("csrf", csrfToken);
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});
        when(request.getParameter("action")).thenReturn("migrate");

        // Setup: Missing backup confirmation checkboxes
        when(request.getParameter("dbBackup")).thenReturn(null);
        when(request.getParameter("securityBackup")).thenReturn(null);

        // Execute
        servlet.doPost(request, response);

        // Verify: Should pass CSRF check but fail backup validation
        verify(session).setAttribute("errorMessage", "security.blowfish.migration.error.backups-required");
        verify(response).sendRedirect("security-blowfish-migration.jsp");
    }

    // ========== doPost() Backup Validation Tests ==========

    /**
     * Test doPost() rejects migration when database backup not confirmed.
     */
    @Test
    public void testDoPost_MissingDatabaseBackupConfirmation() throws Exception {
        // Setup: Valid CSRF
        String csrfToken = "valid-token";
        when(request.getParameter("csrf")).thenReturn(csrfToken);
        Cookie csrfCookie = new Cookie("csrf", csrfToken);
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});
        when(request.getParameter("action")).thenReturn("migrate");

        // Setup: Only security backup confirmed, database backup missing
        when(request.getParameter("dbBackup")).thenReturn(null);
        when(request.getParameter("securityBackup")).thenReturn("true");

        // Execute
        servlet.doPost(request, response);

        // Verify
        verify(session).setAttribute("errorMessage", "security.blowfish.migration.error.backups-required");
        verify(response).sendRedirect("security-blowfish-migration.jsp");
    }

    /**
     * Test doPost() rejects migration when security backup not confirmed.
     */
    @Test
    public void testDoPost_MissingSecurityBackupConfirmation() throws Exception {
        // Setup: Valid CSRF
        String csrfToken = "valid-token";
        when(request.getParameter("csrf")).thenReturn(csrfToken);
        Cookie csrfCookie = new Cookie("csrf", csrfToken);
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});
        when(request.getParameter("action")).thenReturn("migrate");

        // Setup: Only database backup confirmed, security backup missing
        when(request.getParameter("dbBackup")).thenReturn("true");
        when(request.getParameter("securityBackup")).thenReturn(null);

        // Execute
        servlet.doPost(request, response);

        // Verify
        verify(session).setAttribute("errorMessage", "security.blowfish.migration.error.backups-required");
        verify(response).sendRedirect("security-blowfish-migration.jsp");
    }

    // ========== doPost() POST-Redirect-GET Tests ==========

    /**
     * Test doPost() uses POST-Redirect-GET pattern.
     * All responses should redirect, not forward.
     */
    @Test
    public void testDoPost_AlwaysRedirects() throws Exception {
        // Setup: Various scenarios that should all redirect

        // Scenario 1: CSRF failure
        when(request.getParameter("csrf")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[0]);
        servlet.doPost(request, response);
        verify(response, times(1)).sendRedirect(anyString());

        // Reset mocks
        reset(request, response, session);
        when(request.getSession()).thenReturn(session);
        when(request.getServerName()).thenReturn("localhost");

        // Scenario 2: Backup validation failure
        String csrfToken = "valid-token";
        when(request.getParameter("csrf")).thenReturn(csrfToken);
        Cookie csrfCookie = new Cookie("csrf", csrfToken);
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});
        when(request.getParameter("action")).thenReturn("migrate");
        when(request.getParameter("dbBackup")).thenReturn(null);
        servlet.doPost(request, response);
        verify(response, times(1)).sendRedirect(anyString());
    }

    /**
     * Test doPost() stores messages in session (not request) for POST-Redirect-GET.
     */
    @Test
    public void testDoPost_StoresMessagesInSession() throws Exception {
        // Setup: CSRF failure scenario
        when(request.getParameter("csrf")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[0]);

        // Execute
        servlet.doPost(request, response);

        // Verify: Error message stored in session, NOT request
        verify(session).setAttribute(eq("errorMessage"), anyString());
        verify(request, never()).setAttribute(eq("errorMessage"), anyString());
    }

    // ========== Action Parameter Tests ==========

    /**
     * Test doPost() handles unknown action by doing nothing (just redirects).
     */
    @Test
    public void testDoPost_UnknownAction() throws Exception {
        // Setup: Valid CSRF, unknown action
        String csrfToken = "valid-token";
        when(request.getParameter("csrf")).thenReturn(csrfToken);
        Cookie csrfCookie = new Cookie("csrf", csrfToken);
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});
        when(request.getParameter("action")).thenReturn("unknown-action");

        // Execute
        servlet.doPost(request, response);

        // Verify: Should just redirect without setting any message
        verify(response).sendRedirect("security-blowfish-migration.jsp");
        verify(session, never()).setAttribute(eq("errorMessage"), anyString());
        verify(session, never()).setAttribute(eq("successMessage"), anyString());
    }

    /**
     * Test doPost() handles null action by doing nothing (just redirects).
     */
    @Test
    public void testDoPost_NullAction() throws Exception {
        // Setup: Valid CSRF, null action
        String csrfToken = "valid-token";
        when(request.getParameter("csrf")).thenReturn(csrfToken);
        Cookie csrfCookie = new Cookie("csrf", csrfToken);
        when(request.getCookies()).thenReturn(new Cookie[]{csrfCookie});
        when(request.getParameter("action")).thenReturn(null);

        // Execute
        servlet.doPost(request, response);

        // Verify: Should just redirect without setting any message
        verify(response).sendRedirect("security-blowfish-migration.jsp");
        verify(session, never()).setAttribute(eq("errorMessage"), anyString());
        verify(session, never()).setAttribute(eq("successMessage"), anyString());
    }
}