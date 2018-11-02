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
package org.igniterealtime.openfire.plugins.certificatemanager;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;

import java.io.File;
import java.util.Map;

/**
 * The Certificate Manager plugin adds additional administrative functionality to the certificate stores that are used
 * by Openfire.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateManagerPlugin implements Plugin, PropertyEventListener
{
    final DirectoryWatcher directoryWatcher = new DirectoryWatcher();

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        directoryWatcher.start();
        PropertyEventDispatcher.addListener( this );
    }

    @Override
    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener( this );
        directoryWatcher.stop();
    }

    protected void applyPropertyChange( String property, Map<String, Object> params )
    {
        switch ( property )
        {
            case DirectoryWatcher.PROPERTY_ENABLED: // intended fall-through.
            case DirectoryWatcher.PROPERTY_WATCHED_PATH:
                directoryWatcher.stop();
                directoryWatcher.start();
                break;
        }
    }
    @Override
    public void propertySet( String property, Map<String, Object> params )
    {
        applyPropertyChange( property, params );
    }

    @Override
    public void propertyDeleted( String property, Map<String, Object> params )
    {
        applyPropertyChange( property, params );
    }

    @Override
    public void xmlPropertySet( String property, Map<String, Object> params )
    {
        applyPropertyChange( property, params );
    }

    @Override
    public void xmlPropertyDeleted( String property, Map<String, Object> params )
    {
        applyPropertyChange( property, params );
    }
}
