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

import org.jivesoftware.openfire.XMPPServer;
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
 * Generates a JSON object that contains configuration for the inVerse web application.
 *
 * @author Guus der Kinderen, guus@gmail.com
 */
public class ConfigServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger( ConfigServlet.class );

    public void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException
    {
        Log.trace( "Processing doGet()" );

        final boolean inbandRegEnabled = XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled();
        final String defaultDomain = JiveGlobals.getProperty( "inverse.config.default_domain", XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
        final boolean lockedDomain = JiveGlobals.getBooleanProperty( "inverse.config.locked_domain", false );
        final String endpoint = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/http-bind/";
        final boolean debug = JiveGlobals.getBooleanProperty( "inverse.config.debug", false );
        //final String viewMode = JiveGlobals.getProperty( "inverse.config.view_mode" );

        // The language of the inVerse UI.
        final Language language = InversePlugin.getLanguage();

        final JSONObject config = new JSONObject();
        config.put( "auto_away", 300); // TODO make configurable.
        config.put( "notify_all_room_messages", new JSONArray() ); // TODO make configurable.
        config.put( "i18n", language.getCode() );
        config.put( lockedDomain ? "locked_domain" : "default_domain", defaultDomain );

        if ( inbandRegEnabled )
        {
            config.put( "registration_domain", defaultDomain );
        }

        // When the domain that inVerse is locked to does not support IBB, there is no point in showing the
        // 'registration' taballowing registration. Disallowing registration explicitly will suppress the tab.
        if ( !inbandRegEnabled && lockedDomain )
        {
            config.put( "allow_registration", false );
        }

        config.put( "domain_placeholder", defaultDomain );
        config.put( "bosh_service_url", endpoint );
        config.put( "debug", debug );
        config.put( "view_mode", "fullscreen" );
//        if ( viewMode != null && !viewMode.isEmpty() )
//        {
//            config.put( "view_mode", viewMode );
//        }

        final JSONArray whitelistedPlugins = new JSONArray(); // TODO make configurable.
        whitelistedPlugins.put( "converse-singleton" );
        whitelistedPlugins.put( "converse-inverse" );
        config.put( "whitelisted_plugins", whitelistedPlugins );

        final JSONArray blacklistedPlugins = new JSONArray(); // TODO make configurable.
        blacklistedPlugins.put( "converse-minimize" );
        blacklistedPlugins.put( "converse-dragresize" );
        config.put( "blacklisted_plugins", blacklistedPlugins );

        config.put( "auto_reconnect", true ); // TODO make configurable.
        config.put( "message_carbons", true ); // TODO make configurable.
        config.put( "message_archiving", "always" ); // TODO make configurable.
        config.put( "roster_groups", true ); // TODO make configurable.
        config.put( "show_message_load_animation", false ); // TODO make configurable

        // inVerse.js requires some hard-coded converse.js configuration options (look in the upstream source of
        // src/converse-inverse.js at the settings in passed into `updateSettings`). We should not allow overrides of
        // these configu options (if only, because inVerse will override them anyways):
        // chatview_avatar_height: 44,
        // chatview_avatar_width: 44,
        // hide_open_bookmarks: true,
        // show_controlbox_by_default: true,
        // sticky_controlbox: true,
        try ( final Writer writer = response.getWriter() )
        {
            writer.write( config.toString( 2 ) );
            writer.flush();
        }
    }
}
