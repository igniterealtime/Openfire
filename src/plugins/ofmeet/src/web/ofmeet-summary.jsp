<%--
  -	$Revision$
  -	$Date$
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

<%@ page import="org.jivesoftware.util.*,
                 org.jitsi.videobridge.*,
                 org.jitsi.jigasi.openfire.*,
                 org.jitsi.videobridge.openfire.PluginImpl,
                 java.util.*,
                 java.net.URLEncoder"                 
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% 
	Videobridge videobridge = PluginImpl.component.getVideobridge();
    	int confCount = videobridge.getConferenceCount();
%>
<html>
    <head>
        <title><fmt:message key="config.page.summary.title"/></title>
        <meta name="pageID" content="ofmeet-summary"/>
    </head>
    <body>

<p>
<fmt:message key="ofmeet.conference.summary" />
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ofmeet.conference.expired" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="ofmeet.summary.conferences" />: <%= confCount %>,
</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="ofmeet.summary.conference" /></th>
        <th nowrap><fmt:message key="ofmeet.summary.focus" /></th>
        <th nowrap><fmt:message key="ofmeet.summary.room" /></th>
        <th nowrap><fmt:message key="ofmeet.summary.last.activity" /></th>
        <th nowrap><fmt:message key="ofmeet.summary.endpoints" /></th>           
        <th nowrap><fmt:message key="ofmeet.summary.dominant.speaker" /></th>
        <th nowrap><fmt:message key="ofmeet.summary.is.expired" /></th>
        <th nowrap><fmt:message key="ofmeet.summary.is.recording" /></th>     
        <th nowrap><fmt:message key="ofmeet.summary.expire" /></th>          
    </tr>
</thead>
<tbody>

<% 
    if (confCount == 0) {
%>
    <tr>
        <td align="center" colspan="10">
            <fmt:message key="ofmeet.summary.no.conferences" />
        </td>
    </tr>

<%
    }
    int i = 0;
    for (Conference conference : videobridge.getConferences())
    {
    	i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="10%" valign="middle">
		<%= conference.getID() %>
        </td>
        <td width="15%" align="center">
            <% if (!"".equals(conference.getFocus())) { %>
                <%= (new JID(conference.getFocus())).getNode() %>
            <% }
               else { %>
                &nbsp;
            <% } %>
        </td>
        <td width="25%" align="center">
            <%
            String room = "&nbsp;";
            
            for (Map.Entry<String, String> entry  : CallControlComponent.self.conferences.entrySet())
            {
            	if (entry.getValue().equals(conference.getID()))
            	{
            		room = "<a href='/muc-room-edit-form.jsp?roomJID=" + entry.getKey() + "'>" + entry.getKey() + "</a>";            		
            	}
            }            
            %><%= room %>
        </td>
        <td width="15%" align="center">
        	<%
        		long lastActivity = conference.getLastActivityTime();
        		String elapsed = lastActivity == 0 ? "&nbsp;" : StringUtils.getElapsedTime(System.currentTimeMillis() - lastActivity);
        	%>
		<%= elapsed %>
        </td>  
        <td width="10%" align="center">
		<%= conference.getEndpoints().size() %>
        </td>         
        <td width="10%" align="center">
            <% if (conference.getSpeechActivity() != null && conference.getSpeechActivity().getDominantEndpoint() != null && conference.getSpeechActivity().getDominantEndpoint().getID() != null) { %>
                <%= conference.getSpeechActivity().getDominantEndpoint().getID() %>
            <% }
               else { %>
                &nbsp;
            <% } %>
        </td>
        <td width="4%" align="center">		
            <% if (conference.isExpired()) { %>
                <img src="images/success-16x16.gif" width="16" height="16" border="0" alt="">
            <% }
               else { %>
                &nbsp;
            <% } %>		
        </td>
        <td width="4%" align="center">
            <% if (conference.isRecording()) { %>
                <img src="images/success-16x16.gif" width="16" height="16" border="0" alt="">
            <% }
               else { %>
                &nbsp;
            <% } %>		
        </td>        
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="ofmeet-expire.jsp?confid=<%= URLEncoder.encode(conference.getID(), "UTF-8") %>&focus=<%= URLEncoder.encode(conference.getFocus(), "UTF-8") %>"
             title="<fmt:message key="ofmeet.summary.expire" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
        </td>
    </tr>

<%
    }
%>
</tbody>
</table>
</div>
</body>
</html>
