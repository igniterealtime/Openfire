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
package org.igniterealtime.openfire.plugin.candy;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates a JSON object that contains configuration for the Candy web application.
 *
 * @author Guus der Kinderen, guus@gmail.com
 */
public class ConfigServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger( ConfigServlet.class );

    public void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException
    {
        Log.trace( "Processing doGet()" );
        final String endpoint = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/http-bind/";
        final boolean debug = JiveGlobals.getBooleanProperty( "candy.config.debug", false );

        final Set<JID> autojoinRooms = new HashSet<>();
        for ( final String autojoinRoom : JiveGlobals.getProperties( "candy.config.autojoin" ) )
        {
            try
            {
                autojoinRooms.add( new JID( autojoinRoom ) );
            }
            catch ( IllegalArgumentException ex )
            {
                Log.error( "Unable to add this value as an auto-join room to the configuration of Candy: ", autojoinRoom, ex );
            }
        }

        // Generating a resource string that's sufficiently random for security purposes.
        final String resource = "openfire-candy-" + StringUtils.randomString( 8 );

        // The language of the Candy UI.
        final Language language = CandyPlugin.getLanguage();

        final JSONObject config = new JSONObject();
        config.put( "endpoint", endpoint );


        final JSONObject core = new JSONObject();
        config.put( "core", core );
        core.put( "debug", debug );
        if ( autojoinRooms.isEmpty() )
        {
            core.put( "autojoin", true );
        }
        else
        {
            final JSONArray roomlist = new JSONArray();
            for ( final JID autojoinRoom : autojoinRooms )
            {
                roomlist.put( autojoinRoom.toBareJID() );
            }
            core.put( "autojoin", roomlist );
        }

        core.put( "resource", resource );

        final JSONObject view = new JSONObject();
        config.put( "view", view );

        view.put( "language", language.getCode() );
        view.put( "assets", "candy/res/" );

        try ( final Writer writer = response.getWriter() )
        {
            writer.write( config.toString( 2 ) );
            writer.flush();
        }
    }
}
