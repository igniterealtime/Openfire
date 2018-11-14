<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.container.Plugin" %>
<%@ page import="org.jivesoftware.openfire.container.PluginManager" %>
<%@ page import="org.jivesoftware.openfire.container.PluginMetadataHelper" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.io.*" %><%--
  - Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<%
    webManager.init(request, response, session, application, out );

    final boolean showReadme = ParamUtils.getBooleanParameter( request, "showReadme", false);
    final boolean showChangelog = ParamUtils.getBooleanParameter(request, "showChangelog", false);
    if (showReadme || showChangelog )
    {
        final PluginManager pluginManager = webManager.getXMPPServer().getPluginManager();

        final String pluginName = ParamUtils.getParameter(request, "plugin" );
        final Plugin plugin = pluginManager.getPlugin( pluginName );
        if ( plugin != null )
        {
            final Path filePath;
            final Path pluginPath = pluginManager.getPluginPath( plugin );
            if ( showChangelog )
            {
                filePath = pluginPath.resolve( "changelog.html" );
            }
            else
            {
                filePath = pluginPath.resolve( "readme.html" );
            }

            if ( filePath.toFile().exists() )
            {
                try ( final BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream( filePath.toFile() ), "UTF8") ) )
                {
                    String line;
                    while ((line = in.readLine()) != null)
                    {
                        response.getWriter().println( line );
                    }
                }
                catch ( IOException ioe )
                {
                    ioe.printStackTrace();
                }
            }
        }
    }
%>
