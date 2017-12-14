package org.igniterealtime.openfire.plugins.httpfileupload;

import nl.goodbytes.xmpp.xep0363.Component;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by guus on 18-11-17.
 */
public class HttpFileUploadPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger( HttpFileUploadPlugin.class );
    private Component component;
    private WebAppContext context;

    private final String[] publicResources = new String[]
        {
            "httpfileupload/*",
            "httpFileUpload/*"
        };

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        try
        {
            final URL endpoint = new URL( "https", XMPPServer.getInstance().getServerInfo().getHostname(), HttpBindManager.getInstance().getHttpBindSecurePort(), "/httpfileupload");
            component = new Component( XMPPServer.getInstance().getServerInfo().getXMPPDomain(), endpoint );

            // Add the Webchat sources to the same context as the one that's providing the BOSH interface.
            context = new WebAppContext( null, pluginDirectory.getPath() + File.separator + "classes", "/httpfileupload" );
            context.setClassLoader( this.getClass().getClassLoader() );

            // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
            final List<ContainerInitializer> initializers = new ArrayList<>();
            initializers.add( new ContainerInitializer( new JettyJasperInitializer(), null ) );
            context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
            context.setAttribute( InstanceManager.class.getName(), new SimpleInstanceManager());

            HttpBindManager.getInstance().addJettyHandler( context );

            InternalComponentManager.getInstance().addComponent( "httpfileupload", component );

            for ( final String publicResource : publicResources )
            {
                AuthCheckFilter.addExclude( publicResource );
            }
        }
        catch ( MalformedURLException e )
        {
            Log.error( "Unable to initialize endpoint URL!", e );
        }
        catch ( ComponentException e )
        {
            Log.error( "Unable to register component!", e );
        }

    }

    @Override
    public void destroyPlugin()
    {
        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.removeExclude( publicResource );
        }

        if ( context != null )
        {
            HttpBindManager.getInstance().removeJettyHandler( context );
            context.destroy();
            context = null;
        }

        if ( component != null )
        {
            InternalComponentManager.getInstance().removeComponent( "httpfileupload" );
        }
    }
}
