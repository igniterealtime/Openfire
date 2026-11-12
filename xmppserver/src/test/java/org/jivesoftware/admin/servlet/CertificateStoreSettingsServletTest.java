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
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.junit.jupiter.api.AfterAll;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the admin console page that holds the settings of the certificate stores: it shows the cert.stores.readonly
 * setting, sets it to what the checkbox shows on a form submission with a valid CSRF token, and writes an admin event
 * only when the value changed.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
public class CertificateStoreSettingsServletTest
{
    private static final String CSRF = "csrf-token";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private RequestDispatcher requestDispatcher;

    /**
     * The admin events the servlet writes.
     */
    private final List<String> events = new ArrayList<>();

    private CertificateStoreSettingsServlet servlet;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();
        events.clear();
        servlet = new CertificateStoreSettingsServlet()
        {
            @Override
            protected void logEvent( final HttpServletRequest request, final HttpServletResponse response, final String summary, final String details )
            {
                events.add( summary + ": " + details );
            }
        };
        lenient().when( request.getSession() ).thenReturn( session );
        lenient().when( request.getServerName() ).thenReturn( "localhost" );
        lenient().when( request.getRequestDispatcher( CertificateStoreSettingsServlet.PAGE ) ).thenReturn( requestDispatcher );
    }

    /**
     * Verifies that the page shows the current value of the setting, with a CSRF token for its form.
     */
    @Test
    public void testDoGetShowsSetting() throws Exception
    {
        // Setup test fixture.
        CertificateStoreManager.READ_ONLY.setValue( true );

        // Execute system under test.
        servlet.doGet( request, response );

        // Verify result.
        verify( request, description( "the page must show the value of the setting" ) ).setAttribute( "readOnly", true );
        verify( request, description( "the form must carry a CSRF token" ) ).setAttribute( eq( "csrf" ), anyString() );
        verify( requestDispatcher, description( "the page must be rendered" ) ).forward( request, response );
    }

    /**
     * Verifies that a GET request never changes the setting, even when it carries the parameters of the form.
     */
    @Test
    public void testDoGetWithFormParametersDoesNotChangeSetting() throws Exception
    {
        // Setup test fixture.
        lenient().when( request.getParameter( CertificateStoreSettingsServlet.CHECKBOX_PARAMETER ) ).thenReturn( "true" );
        lenient().when( request.getParameter( "csrf" ) ).thenReturn( CSRF );
        lenient().when( request.getCookies() ).thenReturn( new Cookie[] { new Cookie( "csrf", CSRF ) } );

        // Execute system under test.
        servlet.doGet( request, response );

        // Verify result.
        assertFalse( CertificateStoreManager.READ_ONLY.getValue(), "a GET request must not change the setting" );
        assertTrue( events.isEmpty(), "a GET request must not write an admin event" );
    }

    /**
     * Verifies that saving a ticked checkbox while the setting is off turns the setting on, writes an admin event, and
     * redirects to the page with its success message.
     */
    @Test
    public void testDoPostTickedWhenOffTurnsSettingOn() throws Exception
    {
        // Setup test fixture.
        submit( true );

        // Execute system under test.
        servlet.doPost( request, response );

        // Verify result.
        assertTrue( CertificateStoreManager.READ_ONLY.getValue(), "saving a ticked checkbox must turn the setting on" );
        assertEquals( List.of( "Updated the read-only setting of the certificate stores: cert.stores.readonly = true" ), events, "turning the setting on must write one admin event" );
        verify( response, description( "a successful save must redirect to the page with its success message" ) ).sendRedirect( CertificateStoreSettingsServlet.SAVED_REDIRECT );
    }

    /**
     * Verifies that saving an unticked checkbox while the setting is on turns the setting off and writes an admin
     * event.
     */
    @Test
    public void testDoPostUntickedWhenOnTurnsSettingOff() throws Exception
    {
        // Setup test fixture.
        CertificateStoreManager.READ_ONLY.setValue( true );
        submit( false );

        // Execute system under test.
        servlet.doPost( request, response );

        // Verify result.
        assertFalse( CertificateStoreManager.READ_ONLY.getValue(), "saving an unticked checkbox must turn the setting off" );
        assertEquals( List.of( "Updated the read-only setting of the certificate stores: cert.stores.readonly = false" ), events, "turning the setting off must write one admin event" );
        verify( response, description( "a successful save must redirect to the page with its success message" ) ).sendRedirect( CertificateStoreSettingsServlet.SAVED_REDIRECT );
    }

    /**
     * Verifies that saving the value the setting already has writes no admin event, as nothing changed.
     */
    @Test
    public void testDoPostUnchangedValueWritesNoEvent() throws Exception
    {
        // Setup test fixture.
        CertificateStoreManager.READ_ONLY.setValue( true );
        submit( true );

        // Execute system under test.
        servlet.doPost( request, response );

        // Verify result.
        assertTrue( CertificateStoreManager.READ_ONLY.getValue(), "saving a ticked checkbox must leave the setting on" );
        assertTrue( events.isEmpty(), "saving the value the setting already has must not write an admin event" );
        verify( response, description( "the save must still redirect to the page with its success message" ) ).sendRedirect( CertificateStoreSettingsServlet.SAVED_REDIRECT );
    }

    /**
     * Verifies that a form submission without a valid CSRF token is refused: the setting is unchanged, no admin event is
     * written, and the page is shown with the error.
     */
    @SuppressWarnings( "unchecked" )
    @Test
    public void testDoPostWithInvalidCsrfIsRefused() throws Exception
    {
        // Setup test fixture.
        lenient().when( request.getParameter( CertificateStoreSettingsServlet.CHECKBOX_PARAMETER ) ).thenReturn( "true" );
        lenient().when( request.getParameter( "csrf" ) ).thenReturn( "another-token" );
        lenient().when( request.getCookies() ).thenReturn( new Cookie[] { new Cookie( "csrf", CSRF ) } );

        // Execute system under test.
        servlet.doPost( request, response );

        // Verify result.
        assertFalse( CertificateStoreManager.READ_ONLY.getValue(), "a submission without a valid CSRF token must not change the setting" );
        assertTrue( events.isEmpty(), "a refused submission must not write an admin event" );
        verify( request, description( "the page must show the CSRF error" ) ).setAttribute( eq( "errors" ), argThat( errors -> ( (Map<String, String>) errors ).containsKey( "csrf" ) ) );
        verify( requestDispatcher, description( "the page must be rendered with the error" ) ).forward( request, response );
        verify( response, never().description( "a refused submission must not redirect to the success message" ) ).sendRedirect( anyString() );
    }

    private void submit( final boolean ticked )
    {
        lenient().when( request.getParameter( CertificateStoreSettingsServlet.CHECKBOX_PARAMETER ) ).thenReturn( ticked ? "true" : null );
        lenient().when( request.getParameter( "csrf" ) ).thenReturn( CSRF );
        lenient().when( request.getCookies() ).thenReturn( new Cookie[] { new Cookie( "csrf", CSRF ) } );
    }
}
