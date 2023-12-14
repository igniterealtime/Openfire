/*
 * Copyright (C) 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Document;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebXmlUtils}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class WebXmlUtilsTest
{
    @Test
    public void testGetServletNames() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );

        // Execute system under test.
        final List<String> results = WebXmlUtils.getServletNames( webXml );

        // Verify result.
        assertNotNull( results );
        final Iterator<String> iterator = results.iterator(); // Names should be reported in order.
        assertEquals( "PluginServlet", iterator.next() );
        assertEquals( "FaviconServlet", iterator.next() );
        assertEquals( "dwr-invoker", iterator.next() );
        assertEquals( "PluginIconServlet", iterator.next() );
        assertFalse( iterator.hasNext() );
    }

    @Test
    public void testGetFilterNames() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );

        // Execute system under test.
        final List<String> results = WebXmlUtils.getFilterNames( webXml );

        // Verify result.
        assertNotNull( results );
        final Iterator<String> iterator = results.iterator(); // Names should be reported in order.
        assertEquals( "AuthCheck", iterator.next() );
        assertEquals( "PluginFilter", iterator.next() );
        assertEquals( "Set Character Encoding", iterator.next() );
        assertEquals( "LocaleFilter", iterator.next() );
        assertEquals( "sitemesh", iterator.next() );
        assertFalse( iterator.hasNext() );
    }

    @Test
    public void testGetServletClassName() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "dwr-invoker";

        // Execute system under test.
        final String result = WebXmlUtils.getServletClassName( webXml, servletName );

        // Verify result.
        assertEquals( "uk.ltd.getahead.dwr.DWRServlet", result );
    }

    @Test
    public void testGetServletClassNameForNonExistingServlet() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "This does not exist";

        // Execute system under test.
        final String result = WebXmlUtils.getServletClassName( webXml, servletName );

        // Verify result.
        assertNull( result );
    }

    @Test
    public void testGetFilterClassName() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "Set Character Encoding";

        // Execute system under test.
        final String result = WebXmlUtils.getFilterClassName( webXml, filterName );

        // Verify result.
        assertEquals( "org.jivesoftware.util.SetCharacterEncodingFilter", result );
    }

    @Test
    public void testGetFilterClassNameForNonExistingFilter() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "This does not exist";

        // Execute system under test.
        final String result = WebXmlUtils.getFilterClassName( webXml, filterName );

        // Verify result.
        assertNull( result );
    }

    @Test
    public void testGetServletInitParams() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "FaviconServlet";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getServletInitParams( webXml, servletName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 2, result.size() );
        assertEquals( "42", result.get("answer") );
        assertEquals( "fishes", result.get("thanks") );
    }

    @Test
    public void testGetServletInitParamsForServletWithoutParams() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "PluginServlet";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getServletInitParams( webXml, servletName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 0, result.size() );
    }


    @Test
    public void testGetServletInitParamsForNonExistingServlet() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "This does not exist";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getServletInitParams( webXml, servletName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 0, result.size() );
    }

    @Test
    public void testGetFilterInitParams() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "AuthCheck";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getFilterInitParams( webXml, filterName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertEquals( "login.jsp,index.jsp?logout=true,setup/index.jsp,setup/setup-admin-settings.jsp,setup/setup-completed.jsp,setup/setup-datasource-jndi.jsp,setup/setup-datasource-settings.jsp,setup/setup-datasource-standard.jsp,setup/setup-finished.jsp,setup/setup-host-settings.jsp,setup/setup-ldap-group.jsp,setup/setup-ldap-server.jsp,setup/setup-ldap-user.jsp,setup/setup-profile-settings.jsp,.gif,.png,error-serverdown.jsp,loginToken.jsp", result.get("excludes") );
    }

    @Test
    public void testGetFilterInitParamsForFilterWithoutParams() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "PluginFilter";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getFilterInitParams( webXml, filterName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 0, result.size() );
    }


    @Test
    public void testGetFilterInitParamsForNonExistingFilter() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "This does not exist";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getFilterInitParams( webXml, filterName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 0, result.size() );
    }

    @Test
    public void testGetServletUrlPatterns() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "dwr-invoker";

        // Execute system under test.
        final Set<String> results = WebXmlUtils.getServletUrlPatterns( webXml, servletName );

        // Verify result.
        assertNotNull( results );
        assertEquals( 2, results.size() );
        assertTrue( results.contains( "/dwr/*" ));
        assertTrue( results.contains( "/more-dwr/*" ));
    }

    @Test
    public void testGetServletUrlPatternsForNonExistingServlet() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String servletName = "This does not exist";

        // Execute system under test.
        final Set<String> results = WebXmlUtils.getServletUrlPatterns( webXml, servletName );

        // Verify result.
        assertNotNull( results );
        assertEquals( 0, results.size() );
    }

    @Test
    public void testGetFilterUrlPatterns() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "LocaleFilter";

        // Execute system under test.
        final Set<String> results = WebXmlUtils.getFilterUrlPatterns( webXml, filterName );

        // Verify result.
        assertNotNull( results );
        assertEquals( 2, results.size() );
        assertTrue( results.contains( "*.jsp" ));
        assertTrue( results.contains( "foo.bar" ));
    }

    @Test
    public void testGetFilterUrlPatternsForNonExistingFilter() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "This does not exist";

        // Execute system under test.
        final Set<String> results = WebXmlUtils.getFilterUrlPatterns( webXml, filterName );

        // Verify result.
        assertNotNull( results );
        assertEquals( 0, results.size() );
    }

    @Test
    public void testGetFilterUrlPatternsForFilterThatUsesServletMapping() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( Objects.requireNonNull(WebXmlUtilsTest.class.getResource("/org/jivesoftware/util/test-web.xml")).toURI() ) );
        final String filterName = "AuthCheck";

        // Execute system under test.
        final Set<String> results = WebXmlUtils.getFilterUrlPatterns( webXml, filterName );

        // Verify result.
        assertNotNull( results );
        assertEquals( 3, results.size() );
        assertTrue( results.contains( "test/*.jsp" )); // from url pattern
        assertTrue( results.contains( "/dwr/*" ));     // from servlet-mapping
        assertTrue( results.contains( "/more-dwr/*" ));// from servlet-mapping
    }
}
