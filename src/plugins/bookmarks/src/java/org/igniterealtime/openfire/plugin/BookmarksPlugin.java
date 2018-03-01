package org.igniterealtime.openfire.plugin;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.plugin.spark.BookmarkInterceptor;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * A plugin that implements XEP-0048 "Bookmarks".
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="http://xmpp.org/extensions/xep-0048.html">XEP-0048 Bookmarks</a>
 */
public class BookmarksPlugin implements Plugin
{
    private final static Logger Log = LoggerFactory.getLogger( BookmarksPlugin.class );

    private BookmarkInterceptor bookmarkInterceptor;

    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        boolean foundIncompatiblePlugin = false;
        try
        {
            // Check if we Enterprise is installed and stop loading this plugin if found
            if ( checkForEnterprisePlugin() )
            {
                System.out.println( "Enterprise plugin found. Stopping Bookmarks Plugin." );
                foundIncompatiblePlugin = true;
            }

            // Check if we ClientControl (version <= 1.3.1) is installed and stop loading this plugin if found
            if ( checkForIncompatibleClientControlPlugin() )
            {
                System.out.println( "ClientControl plugin v1.3.1 or earlier found. Stopping Bookmarks Plugin." );
                foundIncompatiblePlugin = true;
            }
        }
        catch ( Exception ex )
        {
            Log.warn( "An exception occurred while determining if there are incompatible plugins. Assuming everything is OK.", ex );
        }

        if ( foundIncompatiblePlugin )
        {
            throw new IllegalStateException( "This plugin cannot run next to the Enterprise plugin (any version) or the ClientControl plugin v1.3.1 or earlier." );
        }

        // Create and start the bookmark interceptor, which adds server-managed bookmarks when
        // a user requests their bookmark list.
        bookmarkInterceptor = new BookmarkInterceptor();
        bookmarkInterceptor.start();
    }

    public void destroyPlugin()
    {
        if ( bookmarkInterceptor != null )
        {
            bookmarkInterceptor.stop();
            bookmarkInterceptor = null;
        }
    }

    /**
     * Checks if there's a plugin named "enterprise" in the Openfire plugin directory.
     *
     * @return true if the enterprise plugin is found, otherwise false.
     */
    private static boolean checkForEnterprisePlugin() throws IOException
    {
        return getPluginJar( "enterprise" ) != null;
    }

    /**
     * Checks if there's a plugin named "clientControl" in the Openfire plugin directory of which the version is equal
     * to or earlier than 1.3.1.
     *
     * @return true if the clientControl plugin (<= 1.3.1) is found, otherwise false.
     */
    private static boolean checkForIncompatibleClientControlPlugin() throws IOException, DocumentException
    {
        final JarFile jar = getPluginJar( "clientControl" );

        if ( jar == null )
        {
            return false;
        }

        final ZipEntry pluginXml = jar.getEntry( "plugin.xml" );
        if ( pluginXml == null )
        {
            // Odd - not a plugin?
            Log.warn( "Found a clientControl.jar file that does not appear to include a plugin.xml.", jar.getName() );
            return false;
        }

        final File tempFile = File.createTempFile( "plugin-xml", "xml" );
        try ( final InputStream is = jar.getInputStream( pluginXml );
              final FileOutputStream os = new FileOutputStream( tempFile ) )
        {
            while ( is.available() > 0 )
            {
                os.write( is.read() );
            }

            final SAXReader saxReader = new SAXReader();
            saxReader.setEncoding( "UTF-8" );
            final Document pluginXML = saxReader.read( tempFile );
            Element element = (Element) pluginXML.selectSingleNode( "/plugin/version" );
            if ( element != null )
            {
                final Version version = new Version( element.getTextTrim() );
                return !version.isNewerThan( new Version( "1.3.1" ) );
            }
        }
        return false;
    }

    /**
     * Returns the plugin JAR for the plugin of the provided name.
     *
     * @param pluginName the name of the plugin (cannot be null or empty).
     * @return The plugin JAR file, or null when not found.
     */
    private static JarFile getPluginJar( final String pluginName ) throws IOException
    {
        File pluginDir = new File( JiveGlobals.getHomeDirectory(), "plugins" );
        File[] jars = pluginDir.listFiles( new FileFilter()
        {
            public boolean accept( File pathname )
            {
                return pathname.getName().equalsIgnoreCase( pluginName + ".jar" );
            }
        } );

        final File jar;
        if ( jars.length > 0 )
        {
            return new JarFile( jars[ 0 ] );
        }
        else
        {
            return null;
        }
    }
}
