<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.muc.MUCRole,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 org.jivesoftware.openfire.muc.spi.FMUCHandler,
                 org.jivesoftware.openfire.muc.spi.FMUCMode,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.slf4j.LoggerFactory"
         errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    final JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    final String roomName = roomJID.getNode();
    final String outboundJoinPeerString = ParamUtils.getParameter(request, "roomconfig_fmuc_outbound_jid", true );

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    boolean save = ParamUtils.getBooleanParameter(request,"save");

    Map<String, String> errors = new HashMap<>();

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);
    if (room == null) {
        // The requested room name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
    }

    if (save)
    {
        // Validate input
        JID outboundJoinPeer = null;
        if (outboundJoinPeerString != null && !outboundJoinPeerString.isEmpty()) {
            try {
                outboundJoinPeer = new JID( outboundJoinPeerString );
                if ( outboundJoinPeer.getNode() == null ) {
                    errors.put("outboundJoinPeer", "no_node");
                }
                if ( outboundJoinPeer.getResource() != null ) {
                    errors.put("outboundJoinPeer", "no_bare_jid");
                }
            } catch (IllegalArgumentException e) {
                errors.put("outboundJoinPeer", "invalid_jid");
                outboundJoinPeer = null;
            }
        }

        // Apply changes (if there are no errors)
        if ( errors.isEmpty() ) {
            final FMUCHandler.OutboundJoinConfiguration outboundJoinConfiguration;
            if ( outboundJoinPeer == null ) {
                outboundJoinConfiguration = null;
            } else {
                outboundJoinConfiguration = new FMUCHandler.OutboundJoinConfiguration(outboundJoinPeer, FMUCMode.MasterMaster ); // We currently do not support another mode than master-master.
            }
            try {
                room.getFmucHandler().setOutboundJoinConfiguration(outboundJoinConfiguration);
            } catch ( Exception e ) {
                LoggerFactory.getLogger("muc-room-federation.jsp").warn("An exception occurred while trying to apply an FMUC config change to room {}", roomJID, e );
                errors.put( "fmuchandler", e.getMessage() );
            }

            // Log the event
            webManager.logEvent("updated FMUC settings for MUC room "+roomName, "outbound peer = "+(outboundJoinConfiguration == null ? "(none)" :outboundJoinConfiguration.getPeer())+"\n outbound mode = "+(outboundJoinConfiguration == null ? "(none)" :outboundJoinConfiguration.getMode()));
            response.sendRedirect("muc-room-federation.jsp?success=true&roomJID="+URLEncoder.encode(room.getJID().toBareJID(), "UTF-8"));
            return;
        }
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "room", room );
    pageContext.setAttribute( "roomJIDBare", roomJID.toBareJID() );
    pageContext.setAttribute( "fmucOutboundJID", room.getFmucHandler().getOutboundJoinConfiguration() == null ? "" : room.getFmucHandler().getOutboundJoinConfiguration().getPeer().toString());

%>

<html>
<head>
    <title><fmt:message key="muc.room.federation.title"/></title>
    <meta name="subPageID" content="muc-room-federation"/>
    <meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>"/>
</head>
<body>

<p>
    <fmt:message key="muc.room.federation.info" />
</p>

<!-- Display success report, but only if there were no errors. -->
<c:if test="${param.success and empty errors}">
    <admin:infoBox type="success">
        <fmt:message key="muc.room.federation.saved_successfully"/>
    </admin:infoBox>
</c:if>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
            <c:when test="${err.key eq 'outboundJoinPeer'}"><fmt:message key="muc.room.federation.invalid_outbound_join_peer" /></c:when>
            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<div class="jive-table">
    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th scope="col"><fmt:message key="muc.room.edit.form.room_id" /></th>
                <th scope="col"><fmt:message key="muc.room.edit.form.users" /></th>
                <th scope="col"><fmt:message key="muc.room.edit.form.on" /></th>
                <th scope="col"><fmt:message key="muc.room.edit.form.modified" /></th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>
                    <c:out value="${room.name}"/>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${room.occupantsCount eq 0}">
                            <c:out value="${room.occupantsCount}"/>
                            <c:if test="${room.maxUsers gt 0}">
                                / <c:out value="${room.maxUsers}"/>
                            </c:if>
                        </c:when>
                        <c:otherwise>
                            <c:url var="mucroomoccupantslink" value="muc-room-occupants.jsp">
                                <c:param name="roomJID" value="${roomJIDBare}"/>
                            </c:url>
                            <a href="${mucroomoccupantslink}">
                                <c:out value="${room.occupantsCount}"/>
                                <c:if test="${room.maxUsers gt 0}">
                                    / <c:out value="${room.maxUsers}"/>
                                </c:if>
                            </a>
                        </c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <fmt:formatDate value="${room.creationDate}" dateStyle="medium" timeStyle="short"/>
                </td>
                <td>
                    <fmt:formatDate value="${room.modificationDate}" dateStyle="medium" timeStyle="short"/>
                </td>
            </tr>
            </tbody>
        </table>
    </div>
</div>

<br>

<p><fmt:message key="muc.room.federation.form.descr" /></p>
<br>

<form action="muc-room-federation.jsp">
    <input type="hidden" name="roomJID" value="${fn:escapeXml(roomJIDBare)}">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="save" value="true">

    <table width="100%" border="0">
        <tbody>
            <tr>
                <td><label for="roomconfig_fmuc_outbound_jid"><fmt:message key="muc.room.federation.form.outbound_jid" /></label>:</td>
                <td><input name="roomconfig_fmuc_outbound_jid" id="roomconfig_fmuc_outbound_jid" value="${empty fmucOutboundJID ? "" : fn:escapeXml(fmucOutboundJID)}" type="text" size="40"></td>
            </tr>
            <tr align="center">
                <td colspan="2">
                    <input type="submit" name="Submit" value="<fmt:message key="global.save_changes" />">
                </td>
            </tr>
        </tbody>
    </table>

</form>
<br>

<p>
    <fmt:message key="muc.room.occupants.detail.info" />
</p>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th scope="col"><fmt:message key="muc.room.occupants.user" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.nickname" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.role" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.affiliation" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.kick" /></th>
        </tr>
        </thead>
        <tbody>
        <% for (MUCRole role : room.getOccupants()) { %>
        <tr>
            <td><%= StringUtils.escapeHTMLTags(role.getUserAddress().toString()) %></td>
            <td><%= StringUtils.escapeHTMLTags(role.getNickname().toString()) %></td>
            <td><%= StringUtils.escapeHTMLTags(role.getRole().toString()) %></td>
            <td><%= StringUtils.escapeHTMLTags(role.getAffiliation().toString()) %></td>
            <td><a href="muc-room-occupants.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>&nickName=<%= URLEncoder.encode(role.getNickname(), "UTF-8") %>&kick=1&csrf=${csrf}" title="<fmt:message key="muc.room.occupants.kick"/>"><img src="images/delete-16x16.gif" alt="<fmt:message key="muc.room.occupants.kick"/>" border="0" width="16" height="16"/></a></td>
        </tr>
        <% } %>
        </tbody>
    </table>
</div>

</body>
</html>
