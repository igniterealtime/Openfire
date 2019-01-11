package org.jivesoftware.util;

import org.dom4j.Document;
import org.junit.Test;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );

        // Execute system under test.
        final List<String> results = WebXmlUtils.getServletNames( webXml );

        // Verify result.
        assertNotNull( results );
        final Iterator iterator = results.iterator(); // Names should be reported in order.
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );

        // Execute system under test.
        final List<String> results = WebXmlUtils.getFilterNames( webXml );

        // Verify result.
        assertNotNull( results );
        final Iterator iterator = results.iterator(); // Names should be reported in order.
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
        final String filterName = "AuthCheck";

        // Execute system under test.
        final Map<String, String> result = WebXmlUtils.getFilterInitParams( webXml, filterName );

        // Verify result.
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertEquals( "login.jsp,index.jsp?logout=true,setup/index.jsp,setup/setup-*,.gif,.png,error-serverdown.jsp,loginToken.jsp", result.get("excludes") );
    }

    @Test
    public void testGetFilterInitParamsForFilterWithoutParams() throws Exception
    {
        // Setup fixture.
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
        final Document webXml = WebXmlUtils.asDocument( new File( WebXmlUtilsTest.class.getResource( "/org/jivesoftware/util/test-web.xml" ).toURI() ) );
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
