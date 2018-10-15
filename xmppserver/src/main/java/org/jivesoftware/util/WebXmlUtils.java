package org.jivesoftware.util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.File;
import java.util.*;

/**
 * Utilities to extract data from a web.xml file.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class WebXmlUtils
{
    private final static Logger Log = LoggerFactory.getLogger( WebXmlUtils.class );

    public static Document asDocument( File webXML ) throws DocumentException
    {
        // Make the reader non-validating so that it doesn't try to resolve external DTD's. Trying to resolve
        // external DTD's can break on some firewall configurations.
        SAXReader saxReader = new SAXReader( false);
        try {
            saxReader.setFeature( "http://apache.org/xml/features/nonvalidating/load-external-dtd", false );
        }
        catch (SAXException e)
        {
            Log.warn("Error setting SAXReader feature", e);
        }
        return saxReader.read( webXML );
    }

    /**
     * Retrieves the names of all servlets from a web.xml document.
     *
     * Returns a list that contains 'sessioncreate' and 'sessiondestroy' from this web.xml document:
     * <servlet>
     *   <servlet-name>sessioncreate</servlet-name>
     *   <servlet-class>SessionCreateServlet</servlet-class>
     * </servlet>
     * <servlet>
     *   <servlet-name>sessiondestroy</servlet-name>
     *   <servlet-class>SessionDestroyServlet</servlet-class>
     * </servlet>
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @return The name of the filter class, or null when no such class was defined.
     */
    public static List<String> getServletNames( Document webXml )
    {
        return getNames( "servlet", webXml );
    }

    /**
     * Retrieves the names of all filters from a web.xml document.
     *
     * Returns a list that contains 'message' from this web.xml document:
     * <filter>
     *   <filter-name>message</filter-name>
     *   <filter-class>com.acme.filter.MessageFilter</filter-class>
     * </filter>
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @return The name of the filter class, or null when no such class was defined.
     */
    public static List<String> getFilterNames( Document webXml )
    {
        return getNames( "filter", webXml );
    }

    private static List<String> getNames( String type, Document webXml )
    {
        final List<String> result = new ArrayList<>();
        final List<Element> elements = webXml.getRootElement().elements( type ); // all elements of 'type' (filter or servlet).
        for ( final Element element : elements )
        {
            final String name = element.elementTextTrim( type + "-name" );
            if ( name != null && !name.isEmpty() )
            {
                result.add( name );
            }
        }

        return result;
    }

    /**
     * Retrieves the class name for a particular servlet from a web.xml document.
     *
     * Returns 'SessionCreateServlet' for 'sessioncreate' of this web.xml document:
     * <servlet>
     *   <servlet-name>sessioncreate</servlet-name>
     *   <servlet-class>SessionCreateServlet</servlet-class>
     * </servlet>
     * <servlet>
     *   <servlet-name>sessiondestroy</servlet-name>
     *   <servlet-class>SessionDestroyServlet</servlet-class>
     * </servlet>
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @param servletName The name of the servlet (cannot be null or empty).
     * @return The name of the filter class, or null when no such class was defined.
     */
    public static String getServletClassName( Document webXml, String servletName )
    {
        return getClassName( "servlet", webXml, servletName );
    }

    /**
     * Retrieves the class name for a particular filter from a web.xml document.
     *
     * Returns 'com.acme.filter.MessageFilter' for 'message' of this web.xml document:
     * <filter>
     *   <filter-name>message</filter-name>
     *   <filter-class>com.acme.filter.MessageFilter</filter-class>
     * </filter>
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @param filterName The name of the filter (cannot be null or empty).
     * @return The name of the filter class, or null when no such class was defined.
     */
    public static String getFilterClassName( Document webXml, String filterName )
    {
        return getClassName( "filter", webXml, filterName );
    }

    private static String getClassName( String type, Document webXml, String typeName )
    {
        String className = null;
        final List<Element> elements = webXml.getRootElement().elements( type ); // all elements of 'type' (filter or servlet).
        for ( final Element element : elements )
        {
            final String name = element.elementTextTrim( type + "-name" );
            if ( typeName.equals( name ) )
            {
                className = element.elementTextTrim( type + "-class" );
                break;
            }
        }

        if (className == null || className.isEmpty() )
        {
            return null;
        }

        return className;
    }

    /**
     * Retrieves a map of init param/values for a particular servlet from a web.xml document.
     *
     * For filter 'message' of this web.xml document, returns a map with two entries: foo-> bar, test->value.
     * <servlet>
     *   <servlet-name>sessioncreate</servlet-name>
     *   <servlet-class>SessionCreateServlet</servlet-class>
     *   <init-param>
     *     <param-name>foo</param-name>
     *     <param-value>bar</param-value>
     *   </init-param>
     *   <init-param>
     *     <param-name>test</param-name>
     *     <param-value>value</param-value>
     *   </init-param>
     * </servlet>
     *
     * Parameters with no or empty name are ignored. When multiple parameters have the same name, only one of them is
     * returned.
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @param servletName The name of the servlet (cannot be null or empty).
     * @return A map (possibly empty, but never null).
     */
    public static Map<String, String> getServletInitParams( Document webXml, String servletName )
    {
        return getInitParams( "servlet", webXml, servletName );
    }

    /**
     * Retrieves a map of init param/values for a particular filter from a web.xml document.
     *
     * For filter 'message' of this web.xml document, returns a map with two entries: foo-> bar, test->value.
     * <filter>
     *   <filter-name>message</filter-name>
     *   <filter-class>com.acme.filter.MessageFilter</filter-class>
     *   <init-param>
     *     <param-name>foo</param-name>
     *     <param-value>bar</param-value>
     *   </init-param>
     *   <init-param>
     *     <param-name>test</param-name>
     *     <param-value>value</param-value>
     *   </init-param>
     * </filter>
     *
     * Parameters with no or empty name are ignored. When multiple parameters have the same name, only one of them is
     * returned.
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @param filterName The name of the filter (cannot be null or empty).
     * @return A map (possibly empty, but never null).
     */
    public static Map<String, String> getFilterInitParams( Document webXml, String filterName )
    {
        return getInitParams( "filter", webXml, filterName );
    }

    private static Map<String, String> getInitParams( String type, Document webXml, String typeName )
    {
        final Map<String, String> result = new HashMap<>();
        final List<Element> elements = webXml.getRootElement().elements( type ); // all elements of 'type' (filter or servlet).
        for ( final Element element : elements )
        {
            final String name = element.elementTextTrim( type + "-name" );
            if ( typeName.equals( name ) )
            {
                final List<Element> initParamElements = element.elements( "init-param" );
                for ( final Element initParamElement : initParamElements )
                {
                    final String pName  = initParamElement.elementTextTrim( "param-name" );
                    final String pValue = initParamElement.elementTextTrim( "param-value" );
                    if ( pName == null || pName.isEmpty() ) {
                        Log.warn( "Unable to add init-param that has no name" );
                    }
                    else
                    {
                        result.put( pName, pValue );
                    }
                }
            }
        }

        return result;
    }

    /**
     * Retrieves all URL patterns that apply to a specific servlet.
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @param servletName The name of the servlet (cannot be null or empty).
     * @return A collection (possibly empty, but never null).
     */
    public static Set<String> getServletUrlPatterns( Document webXml, String servletName )
    {
        return getUrlPatterns( "servlet", webXml, servletName );
    }

    /**
     * Retrieves all URL patterns that apply to a specific filter.
     *
     * @param webXml web.xml document, parsed as XML (cannot be null)
     * @param filterName The name of the filter (cannot be null or empty).
     * @return A collection (possibly empty, but never null).
     */
    public static Set<String> getFilterUrlPatterns( Document webXml, String filterName )
    {
        return getUrlPatterns( "filter", webXml, filterName );
    }

    private static Set<String> getUrlPatterns( String type, Document webXml, String typeName )
    {
        final Set<String> result = new HashSet<>();
        final List<Element> elements = webXml.getRootElement().elements( type + "-mapping" ); // all elements of 'type'-mapping (filter-mapping or servlet-mapping).
        for ( final Element element : elements )
        {
            final String name = element.elementTextTrim( type + "-name" );
            if ( typeName.equals( name ) )
            {
                final List<Element> urlPatternElements = element.elements( "url-pattern" );
                for ( final Element urlPatternElement : urlPatternElements )
                {
                    final String urlPattern = urlPatternElement.getTextTrim();
                    if ( urlPattern != null )
                    {
                        result.add( urlPattern );
                    }
                }

                // A filter can also be mapped to a servlet (by name). In that case, all url-patterns of the corresponding servlet-mapping should be used.
                if ( "filter".equals( type ) )
                {
                    final List<Element> servletNameElements = element.elements( "servlet-name" );
                    for ( final Element servletNameElement : servletNameElements )
                    {
                        final String servletName = servletNameElement.getTextTrim();
                        if ( servletName != null )
                        {
                            result.addAll( getUrlPatterns( "servlet", webXml, servletName ) );
                        }
                    }
                }
                break;
            }
        }

        return result;
    }
}
