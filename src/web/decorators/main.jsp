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

<%@ page import="org.jivesoftware.util.StringUtils,
                 org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.openfire.cluster.ClusterManager"
    errorPage="../error.jsp"
%><%@ page import="org.xmpp.packet.JID"%>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.opensymphony.com/sitemesh/decorator" prefix="decorator" %>
<%@ taglib uri="http://www.opensymphony.com/sitemesh/page" prefix="page" %>

<jsp:useBean id="info" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out); %>

<decorator:usePage id="decoratedPage" />

<%
    String path = request.getContextPath();
    // Decorated pages will typically must set a pageID and optionally set a subPageID
    // and extraParams. Store these values as request attributes so that the tab and sidebar
    // handling tags can get at the data.
    request.setAttribute("pageID", decoratedPage.getProperty("meta.pageID"));
    request.setAttribute("subPageID", decoratedPage.getProperty("meta.subPageID"));
    request.setAttribute("extraParams", decoratedPage.getProperty("meta.extraParams"));

    // Message HTML can be passed in:
    String message = decoratedPage.getProperty("page.message");

    pageContext.setAttribute( "usernameHtmlEscaped", StringUtils.escapeHTMLTags(JID.unescapeNode(webManager.getUser().getUsername())) );
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<fmt:setBundle basename="openfire_i18n"/>
<html>
<head>
    <title><%= AdminConsole.getAppName() %> <fmt:message key="login.title" />: <decorator:title /></title>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
    <script language="JavaScript" type="text/javascript" src="<%= path %>/js/prototype.js"></script>
    <script language="JavaScript" type="text/javascript" src="<%= path %>/js/scriptaculous.js"></script>
    <script language="JavaScript" type="text/javascript" src="<%= path %>/js/cookies.js"></script>
    <script language="JavaScript" type="text/javascript">

    </script>
    <script type="text/javascript" src="<%= path %>/js/behaviour.js"></script>
    <script type="text/javascript">
    // Add a nice little rollover effect to any row in a jive-table object. This will help
    // visually link left and right columns.
    /*
    var myrules = {
        '.jive-table TBODY TR' : function(el) {
            el.onmouseover = function() {
                this.style.backgroundColor = '#ffffee';
            }
            el.onmouseout = function() {
                this.style.backgroundColor = '#ffffff';
            }
        }
    };
    Behaviour.register(myrules);
    */
    </script>
    <decorator:head />
</head>

<body id="jive-body">

<!-- BEGIN main -->
<div id="main">

    <div id="jive-header">
        <div id="jive-logo">
            <a href="/index.jsp"><img src="/images/login_logo.gif" alt="Openfire" width="179" height="53" /></a>
        </div>
        <div id="jive-userstatus">
            <%= AdminConsole.getAppName() %> <%= AdminConsole.getVersionString() %><br/>
            <fmt:message key="admin.logged_in_as"><fmt:param value="<strong>${usernameHtmlEscaped}</strong>"/></fmt:message> - <a href="<%= path %>/index.jsp?logout=true"><%= LocaleUtils.getLocalizedString("global.logout") %></a><br/>
            <fmt:message key="admin.clustering.status"/> -
                <% if (ClusterManager.isClusteringEnabled()) { %>
                    <% if (ClusterManager.isClusteringStarted()) { %>
                        <% if (ClusterManager.isSeniorClusterMember()) { %>
                                <fmt:message key="admin.clustering.senior"/>
                        <% } else { %>
                                <fmt:message key="admin.clustering.junior"/>
                        <% }  %>
                    <% } else { %>
                        <fmt:message key="admin.clustering.starting"/>
                    <% } %>
                <% } else { %>
                    <fmt:message key="admin.clustering.disabled"/>
                <% } %>
        </div>
        <div id="jive-nav">
            <div id="jive-nav-left"></div>
            <admin:tabs css="" currentcss="currentlink">
            <a href="[url]" title="[description]" onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;">[name]</a>
            </admin:tabs>
            <div id="jive-nav-right"></div>
        </div>
        <div id="jive-subnav">
            <admin:subnavbar css="" currentcss="current">
                <a href="[url]" title="[description]"
                  onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                  >[name]</a>
            </admin:subnavbar>
        </div>
    </div>

    <div id="jive-main">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top">
            <td width="1%">
                <div id="jive-sidebar-container">
                    <div id="jive-sidebar-box">
                        <div id="jive-sidebar">
                            <admin:sidebar css="" currentcss="currentlink" headercss="category">
                                <a href="[url]" title="[description]"
                                  onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                                  >[name]</a>
                                 <admin:subsidebar css="" currentcss="currentlink">
                                    <a href="[url]" title="[description]"
                                     onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                                     >[name]</a>
                                 </admin:subsidebar>
                            </admin:sidebar>
                            <br>
                            <img src="<%= path %>/images/blank.gif" width="150" height="1" border="0" alt="">
                        </div>
                    </div>
                </div>
            </td>
            <td width="99%" id="jive-content">


                <%  if (message != null) { %>

                    <%= message %>

                <%  } %>

                <h1>
                    <decorator:title default="&nbsp;"/>
                </h1>

                <div id="jive-main-content">
                    <decorator:body/>
                </div>
            </td>
        </tr>
    </tbody>
    </table>
    </div>

</div>
<!-- END main -->

<!-- BEGIN footer -->
    <div id="jive-footer">
        <div class="jive-footer-nav">
            <admin:tabs css="" currentcss="currentlink" justlinks="true">
            <a href="[url]" title="[description]" onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;">[name]</a>
            </admin:tabs>
        </div>
        <div class="jive-footer-copyright">
            Built by <a href="http://www.jivesoftware.com">Jive Software</a> and the <a href="http://www.igniterealtime.org">IgniteRealtime.org</a> community
        </div>
    </div>
<!-- END footer -->

</body>
</html>
