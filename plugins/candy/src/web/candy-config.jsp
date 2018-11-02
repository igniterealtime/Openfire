<!--
  - Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  - http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->
<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="java.util.List" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRoom" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="org.igniterealtime.openfire.plugin.candy.Language" %>
<%@ page import="org.igniterealtime.openfire.plugin.candy.CandyPlugin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    String deleteRoom = request.getParameter( "deleteRoom" );
    if ( deleteRoom != null )
    {
        deleteRoom = URLDecoder.decode( deleteRoom, "UTF-8" );
    }
    String newRoom = request.getParameter( "newRoom" );
    if ( newRoom != null )
    {
        newRoom = URLDecoder.decode( newRoom, "UTF-8" );
    }
    boolean update = request.getParameter("update") != null;
    String success = request.getParameter("success");
    String error = null;

    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter( request, "csrf");

    if (update || deleteRoom != null || newRoom != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            deleteRoom = null;
            newRoom = null;
            error = "csrf";
        }
    }
    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    HttpBindManager httpBindManager = HttpBindManager.getInstance();

    if ( error == null )
    {
        if ( deleteRoom != null )
        {
            for ( final String propertyName : JiveGlobals.getPropertyNames( "candy.config.autojoin" ) )
            {
                final String propertyValue = JiveGlobals.getProperty( propertyName );
                if ( deleteRoom.equals( propertyValue ) )
                {
                    JiveGlobals.deleteProperty( propertyName );
                    response.sendRedirect("candy-config.jsp?success=deleteRoom");
                    return;
                }
            }
        }

        if ( newRoom != null )
        {
            // When just the room name was provided, append with the service domain.
            if ( !newRoom.contains( "@" ) )
            {
                final String mucDomain = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices().get( 0 ).getServiceDomain();
                newRoom = newRoom + "@" + mucDomain;
            }
            JiveGlobals.setProperty( "candy.config.autojoin." + StringUtils.randomString( 10 ), new JID( newRoom ).toBareJID() );
            response.sendRedirect("candy-config.jsp?success=addRoom");
            return;
        }

        if ( update )
        {
            if ( ParamUtils.getParameter( request, "debugEnabled" ) != null )
            {
                JiveGlobals.setProperty( "candy.config.debug", Boolean.toString( ParamUtils.getBooleanParameter( request, "debugEnabled" ) ) );
            }

            if ( ParamUtils.getParameter( request, "language" ) != null )
            {
                JiveGlobals.setProperty( "candy.config.language", URLEncoder.encode( ParamUtils.getParameter( request, "language" ) , "UTF-8" ) );
            }
            response.sendRedirect("candy-config.jsp?success=update");
            return;
        }
    }

    // Read all updated values from the properties.
    final boolean debugEnabled = JiveGlobals.getBooleanProperty( "candy.config.debug", false );
    final List<String> autojoinRooms = JiveGlobals.getProperties( "candy.config.autojoin" );
%>
<html>
<head>
    <title><fmt:message key="config.page.title"/></title>
    <meta name="pageID" content="candy-config"/>
</head>
<body>

<% if ( autojoinRooms.isEmpty() ) { %>
<div class="jive-warning">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/warning-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <fmt:message key="warning.autojoin.empty"/>
            </td></tr>
        </tbody>
    </table>
</div><br>

<% } %>

<% if ( !httpBindManager.isHttpBindEnabled() ) { %>

<div class="jive-warning">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/warning-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <fmt:message key="warning.httpbinding.disabled">
                    <fmt:param value="<a href=\"../../http-bind.jsp\">"/>
                    <fmt:param value="</a>"/>
                </fmt:message>
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>

<% if (error != null) { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <% if ( "csrf".equalsIgnoreCase( error )  ) { %>
                <fmt:message key="global.csrf.failed" />
                <% } else { %>
                <fmt:message key="admin.error" />: <c:out value="error"></c:out>
                <% } %>
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>


<%  if (success != null) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <% if ( "addRoom".equalsIgnoreCase( success )  ) { %>
                <fmt:message key="config.page.autojoin.addroom.success" />
                <% } else if ( "deleteRoom".equalsIgnoreCase( success )  ) { %>
                <fmt:message key="config.page.autojoin.deleteroom.success" />
                <% } else { %>
                <fmt:message key="properties.save.success" />
                <% } %>
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>

<p>
    <fmt:message key="config.page.description">
        <fmt:param value=""/>
    </fmt:message>
    <% if ( httpBindManager.isHttpBindActive() ) {
        final String unsecuredAddress = "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + httpBindManager.getHttpBindUnsecurePort() + "/candy/";
    %>
        <fmt:message key="config.page.link.unsecure">
            <fmt:param value="<%=unsecuredAddress%>"/>
        </fmt:message>
    <% } %>
    <% if ( httpBindManager.isHttpsBindActive() ) {
        final String securedAddress = "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + httpBindManager.getHttpBindSecurePort() + "/candy/";
    %>
        <fmt:message key="config.page.link.secure">
            <fmt:param value="<%=securedAddress%>"/>
        </fmt:message>
    <% } %>
</p>

<div class="jive-contentBoxHeader"><fmt:message key="config.page.autojoin.header" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="config.page.autojoin.description" /></p>

    <form action="candy-config.jsp">
        <input type="hidden" name="csrf" value="${csrf}">

        <table class="jive-table" cellpadding="3" cellspacing="0" border="0">
            <thead>
            <tr>
                <th width="1%">&nbsp;</th>
                <th width="35%" nowrap><fmt:message key="config.page.autojoin.header.room" /></th>
                <th width="55%" nowrap><fmt:message key="config.page.autojoin.header.description" /></th>
                <th width="1%" nowrap><fmt:message key="config.page.autojoin.header.users" /></th>
                <th width="1%" nowrap><fmt:message key="config.page.autojoin.header.delete" /></th>
            </tr>
            </thead>
            <tbody>
                <%
                    int i=0;
                    if ( autojoinRooms.isEmpty() ) {
                %>
                <tr>
                    <td align="center" colspan="5"><fmt:message key="config.page.autojoin.no-rooms" /></td>
                </tr>
                <%
                    } else {
                        for ( final String autojoinRoom : autojoinRooms )
                        {
                            i++;
                            final JID roomJID = new JID( autojoinRoom );
                            final MultiUserChatService service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService( roomJID );
                            MUCRoom room = null;
                            if ( service != null )
                            {
                                room = service.getChatRoom( roomJID.getNode() );
                            }
                %>
                <tr class="jive-<%= i%2==0 ? "even":"odd" %>">
                <%
                    if ( room != null ) {
                %>
                    <td></td>
                    <td>
                        <a href="../../muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"title="<fmt:message key="global.click_edit" />">
                        <%=  StringUtils.escapeHTMLTags(room.getName()) %>
                        </a>
                    </td>
                    <td>
                        <% if (!"".equals(room.getDescription())) { %>
                        <%= StringUtils.escapeHTMLTags(room.getDescription()) %>
                        <% }
                        else { %>
                        &nbsp;
                        <% } %>
                    </td>
                    <td style="text-align: center;"><nobr><%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %></nobr></td>
                    <td style="text-align: center;"><a href="candy-config.jsp?deleteRoom=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>&csrf=${csrf}" title="<fmt:message key="config.page.autojoin.button.delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a></td>
                <% } else { %>
                    <td></td>
                    <td colspan="3">
                        <img src="images/warning-16x16.gif" width="16" height="16" border="0" alt="">&nbsp;<%=StringUtils.escapeHTMLTags(autojoinRoom)%>
                        <i><fmt:message key="warning.nonexisting.room">
                            <fmt:param value="<a href=\"../../muc-room-edit-form.jsp?create=true\">"/>
                            <fmt:param value="</a>"/>
                        </fmt:message>
                        </i>
                    </td>
                    <td style="text-align: center;"><a href="candy-config.jsp?deleteRoom=<%= URLEncoder.encode(autojoinRoom, "UTF-8") %>&csrf=${csrf}" title="<fmt:message key="config.page.autojoin.button.delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a></td>

                <% } %>
                </tr>
                <%
                        }
                    }
                %>
            </tbody>
        </table>

        <br>

        <table cellpadding="3" cellspacing="1" border="0">
            <tbody>
                <tr>
                    <td nowrap="" width="1%">
                        <label for="newRoom"><fmt:message key="config.page.autojoin.label.newroom" /></label>
                    </td>
                    <td>
                        <input type="text" size="40" name="newRoom" id="newRoom">&nbsp;<input type="submit" name="update" value="<fmt:message key="config.page.autojoin.label.addroom" />">
                    </td>
                </tr>
            </tbody>
        </table>
    </form>

</div>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="config.page.language.header" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="config.page.language.description" /></p>

    <form action="candy-config.jsp">
        <input type="hidden" name="csrf" value="${csrf}">

        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <%
                final Language currentLanguage = CandyPlugin.getLanguage();
                for ( final Language language : Language.values() )
                {
            %>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="language" value="<%=language.getCode()%>" id="<%=language.getCode()%>" <%= (currentLanguage == language ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="<%=language.getCode()%>">
                        <%= language %>
                    </label>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>

        <br>

        <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

    </form>

</div>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="config.page.debug.header" /></div>
<div class="jive-contentBox">

    <form action="candy-config.jsp">
        <input type="hidden" name="csrf" value="${csrf}">

        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="debugEnabled" value="true" id="rb01" <%= (debugEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb01">
                        <b><fmt:message key="config.page.debug.enabled" /></b> - <fmt:message key="config.page.debug.enabled_info" />
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="debugEnabled" value="false" id="rb02" <%= (!debugEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb02">
                        <b><fmt:message key="config.page.debug.disabled" /></b> - <fmt:message key="config.page.debug.disabled_info" />
                    </label>
                </td>
            </tr>
            </tbody>
        </table>

        <br>

        <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

    </form>

</div>

</body>
</html>
