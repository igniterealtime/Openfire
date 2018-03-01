/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
package org.igniterealtime.openfire.plugin.inverse;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * An Openfire plugin that integrates the Inverse web client.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class InversePlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger( InversePlugin.class );
    private final String[] publicResources = new String[]
    {
        "inverse/inverse",
        "inverse/index.html",
        "inverse/config.json"
    };

    private WebAppContext context = null;

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.addExclude( publicResource );
        }

        // Add the Webchat sources to the same context as the one that's providing the BOSH interface.
        context = new WebAppContext( null, pluginDirectory.getPath() + File.separator + "classes/", "/inverse" );
        context.setClassLoader( this.getClass().getClassLoader() );

        // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add( new ContainerInitializer( new JettyJasperInitializer(), null ) );
        context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        context.setAttribute( InstanceManager.class.getName(), new SimpleInstanceManager());

        HttpBindManager.getInstance().addJettyHandler( context );
    }

    @Override
    public void destroyPlugin()
    {
        if ( context != null )
        {
            HttpBindManager.getInstance().removeJettyHandler( context );
            context.destroy();
            context = null;
        }

        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.removeExclude( publicResource );
        }
    }

    public static Language getLanguage()
    {
        final String inverseLanguage = JiveGlobals.getProperty( "inverse.config.language" );
        if ( inverseLanguage != null )
        {
            final Language result = Language.byConverseCode( inverseLanguage );
            if ( result != null )
            {
                return result;
            }
            else
            {
                Log.warn( "The value '{}' of property 'inverse.config.language' cannot be mapped to a language code that is understood by Inverse.", inverseLanguage );
            }
        }

        final Language result = Language.byLocale( JiveGlobals.getLocale() );
        if ( result != null )
        {
            return result;
        }
        else
        {
            Log.warn( "The Openfire locale '{}' cannot be mapped to a language code that is understood by Inverse.", JiveGlobals.getLocale() );
        }

        return Language.English;
    }
}
