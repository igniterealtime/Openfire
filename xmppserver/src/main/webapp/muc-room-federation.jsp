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

<%@ page import="org.jivesoftware.openfire.muc.MUCRoom,
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
    final String outboundJoinPeerString = ParamUtils.getParameter(request, "roomconfig_fmuc_outbound_jid", true);
    final boolean fmucEnabled = ParamUtils.getBooleanParameter(request, "fmuc-enabled");
    String stopSessionString = ParamUtils.getParameter(request,"stopSession", true);
    JID stopSession = stopSessionString != null && !stopSessionString.isEmpty() ? new JID(stopSessionString) : null;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    boolean save = ParamUtils.getBooleanParameter(request,"save");

    Map<String, String> errors = new HashMap<>();

    if (save || stopSession != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            stopSession = null;
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
    final FMUCHandler fmucHandler = room.getFmucHandler();

    if (stopSession != null)
    {
        boolean stoppedSomething = false;
        final FMUCHandler.OutboundJoin outboundJoin = fmucHandler.getOutboundJoin();
        if ( outboundJoin != null && outboundJoin.getPeer().equals( stopSession )) {
            fmucHandler.stopOutbound();
            stoppedSomething = true;
            webManager.logEvent("closed FMUC outbound join to " + stopSession, null);
        }
        final FMUCHandler.OutboundJoinProgress outboundJoinProgress = fmucHandler.getOutboundJoinProgress();
        if ( outboundJoinProgress != null && outboundJoinProgress.getPeer().equals(stopSession )) {
            fmucHandler.abortOutboundJoinProgress();
            stoppedSomething = true;
            webManager.logEvent("closed FMUC outbound join attempt (in progress) to " + stopSession, null);
        }
        final JID finalStopSession = stopSession;
        if (fmucHandler.getInboundJoins().stream().anyMatch(j -> j.getPeer().equals(finalStopSession))) {
            fmucHandler.stopInbound(stopSession);
            stoppedSomething = true;
            webManager.logEvent("closed FMUC inbound join from " + stopSession, null);
        }

        if (stoppedSomething) {
            response.sendRedirect("muc-room-federation.jsp?closeSuccess=true&roomJID=" + URLEncoder.encode(room.getJID().toBareJID(), "UTF-8"));
        } else {
            response.sendRedirect("muc-room-federation.jsp?closeError=true&roomJID=" + URLEncoder.encode(room.getJID().toBareJID(), "UTF-8"));
        }
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
            try {
                room.setFmucEnabled(fmucEnabled);
                room.setFmucOutboundNode(outboundJoinPeer);
                room.setFmucOutboundMode(FMUCMode.MasterMaster); // We currently do not support another mode than master-master.
                room.getFmucHandler().applyConfigurationChanges();
                room.saveToDB();
            } catch ( Exception e ) {
                LoggerFactory.getLogger("muc-room-federation.jsp").warn("An exception occurred while trying to apply an FMUC config change to room {}", roomJID, e );
                errors.put( "fmuchandler", e.getMessage() );
            }

            // Log the event
            webManager.logEvent("Updated FMUC settings for MUC room "+roomName, "FMUC enabled = " + room.isFmucEnabled() + ",\noutbound peer = "+(room.getFmucOutboundNode() == null ? "(none)" :room.getFmucOutboundNode())+",\noutbound mode = "+(room.getFmucOutboundMode() == null ? "(none)" :room.getFmucOutboundMode()));
            response.sendRedirect("muc-room-federation.jsp?success=true&roomJID="+URLEncoder.encode(room.getJID().toBareJID(), "UTF-8"));
            return;
        }
    }

    pageContext.setAttribute( "killSwitchEnabled", !FMUCHandler.FMUC_ENABLED.getValue() );
    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "room", room );
    pageContext.setAttribute( "roomJIDBare", roomJID.toBareJID() );
    pageContext.setAttribute( "fmucOutboundJID", room.getFmucOutboundNode() == null ? "" : room.getFmucOutboundNode().toString());
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
<c:if test="${param.closeSuccess and empty errors}">
    <admin:infoBox type="success">
        Successfully closed a session.
    </admin:infoBox>
</c:if>

<c:if test="${killSwitchEnabled}">
    <admin:infoBox type="warning">
        <fmt:message key="muc.room.federation.experimental_warning" />
    </admin:infoBox>
</c:if>

<c:if test="${param.closeError}">
    <admin:infoBox type="warning">
        Unable to close session.
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

<form action="muc-room-federation.jsp">
    <input type="hidden" name="roomJID" value="${fn:escapeXml(roomJIDBare)}">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="save" value="true">

<fmt:message key="muc.room.federation.form.boxtitle" var="formboxtitle"/>
<admin:contentBox title="${formboxtitle}">

    <p><fmt:message key="muc.room.federation.form.descr" /></p>

    <table cellpadding="3" cellspacing="0" border="0">
        <tr valign="middle">
            <td colspan="2"><input type="checkbox" name="fmuc-enabled" id="fmuc-enabled" ${room.fmucEnabled ? 'checked' : ''}/> <label for="fmuc-enabled"><fmt:message key="muc.room.federation.form.enabled"/></label></td>
        </tr>
        <tr valign="middle">
            <td><label for="roomconfig_fmuc_outbound_jid"><fmt:message key="muc.room.federation.form.outbound_jid" /></label>:</td>
            <td><input name="roomconfig_fmuc_outbound_jid" id="roomconfig_fmuc_outbound_jid" value="${empty fmucOutboundJID ? "" : fn:escapeXml(fmucOutboundJID)}" type="text" size="40"></td>
        </tr>
    </table>

</admin:contentBox>

<input type="submit" name="Submit" value="<fmt:message key="global.save_changes" />">

</form>

<br>

<p>
    Currently, federation with these MUCs has been established.
</p>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th scope="col">Remote MUC address</th>
            <th scope="col">Federation direction</th>
            <th scope="col">Occupants on node</th>
            <th scope="col">Close</th>
        </tr>
        </thead>
        <tbody>
        <c:if test="${empty room.fmucHandler.outboundJoin and empty room.fmucHandler.outboundJoinProgress and empty room.fmucHandler.inboundJoins}">
        <tr>
            <td colspan="4">(Currently, there is no ongoing federation)</td>
        </tr>
        </c:if>
        <c:if test="${not empty room.fmucHandler.outboundJoinProgress}">
        <tr>
            <td>
                <c:out value="${room.fmucHandler.outboundJoinProgress.peer}"/>
            </td>
            <td colspan="2">
                Outbound, federation being established...
            </td>
            <td width="1%" align="center" style="border-right:1px #ccc solid;">
                <a href="muc-room-federation.jsp?roomJID=${admin:urlEncode(roomJIDBare)}&stopSession=${admin:urlEncode(room.fmucHandler.outboundJoinProgress.peer)}&csrf=${csrf}" title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
            </td>
        </tr>
        </c:if>
        <c:if test="${not empty room.fmucHandler.outboundJoin}">
            <!-- Always have at least one row, containing the first occupant if the remote room has occupants. -->
            <tr>
                <td>
                    <c:out value="${room.fmucHandler.outboundJoin.peer}"/>
                </td>
                <td>
                    Outbound (mode: <c:out value="${room.fmucHandler.outboundJoin.mode}"/>)
                </td>
                <td>
                    <!-- Add the first occupant, if there's one -->
                    <c:choose>
                        <c:when test="${not empty room.fmucHandler.outboundJoin.occupants}">
                            <c:forEach var="occupant" items="${room.fmucHandler.outboundJoin.occupants}" end="0">
                                <c:out value="${occupant}"/>
                            </c:forEach>
                        </c:when>
                        <c:otherwise>&nbsp;</c:otherwise>
                    </c:choose>
                </td>
                <td width="1%" align="center" style="border-right:1px #ccc solid;">
                    <a href="muc-room-federation.jsp?roomJID=${admin:urlEncode(roomJIDBare)}&stopSession=${admin:urlEncode(room.fmucHandler.outboundJoin.peer)}&csrf=${csrf}" title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                </td>
            </tr>
            <!-- Add rows for all occupants beyond the first one. -->
            <c:forEach var="occupant" items="${room.fmucHandler.outboundJoin.occupants}" varStatus="status">
                <c:if test="${not status.first}">
                    <tr>
                        <td>&nbsp;</td>
                        <td>&nbsp;</td>
                        <td>
                            <c:out value="${occupant}"/>
                        </td>
                        <td>&nbsp;</td>
                    </tr>
                </c:if>
            </c:forEach>
        </c:if>
        <c:forEach var="inboundJoin" items="${room.fmucHandler.inboundJoins}">
            <!-- Always have at least one row, containing the first occupant if the remote room has occupants. -->
            <tr>
                <td>
                    <c:out value="${inboundJoin.peer}"/>
                </td>
                <td>
                    Inbound
                </td>
                <td>
                    <!-- Add the first occupant, if there's one -->
                    <c:choose>
                        <c:when test="${not empty inboundJoin.occupants}">
                            <c:forEach var="occupant" items="${inboundJoin.occupants}" end="0">
                                <c:out value="${occupant}"/>
                            </c:forEach>
                        </c:when>
                        <c:otherwise>&nbsp;</c:otherwise>
                    </c:choose>

                    <c:out value="${occupant}"/>
                </td>
                <td width="1%" align="center" style="border-right:1px #ccc solid;">
                    <a href="muc-room-federation.jsp?roomJID=${admin:urlEncode(roomJIDBare)}&stopSession=${admin:urlEncode(inboundJoin.peer)}&csrf=${csrf}" title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                </td>
            </tr>
            <!-- Add rows for all occupants beyond the first one. -->
            <c:forEach var="occupant" items="${inboundJoin.occupants}" varStatus="status">
                <c:if test="${not status.first}">
                <tr>
                    <td>&nbsp;</td>
                    <td>&nbsp;</td>
                    <td>
                        <c:out value="${occupant}"/>
                    </td>
                    <td>&nbsp;</td>
                </tr>
                </c:if>
            </c:forEach>
        </c:forEach>
    </table>
</div>

</body>
</html>
