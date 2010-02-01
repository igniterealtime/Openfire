<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="java.io.File" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
<head>
    <title><fmt:message key="spark.download.title" /></title>
    <meta name="pageID" content="spark-download"/>
    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>

<fmt:message key="spark.download.instructions" />
<br/><br/>


<%
    String url = request.getRequestURL().toString();
    String server = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    url = url.replace("localhost", server);
    url = url.replace("spark-download.jsp", "getspark");

    boolean windowsClientExists = false;
    boolean macClientExists = false;
    boolean linuxClientExists = false;
    File buildDir = new File(JiveGlobals.getHomeDirectory(), "enterprise" + File.separator + "spark");
    if (buildDir.exists()) {
        File[] list = buildDir.listFiles();
        int no = list != null ? list.length : 0;
        for (int i = 0; i < no; i++) {
            File clientFile = list[i];
            if (clientFile.getName().endsWith(".exe")) {
                windowsClientExists = true;
            }
            else if (clientFile.getName().endsWith(".dmg")) {
                macClientExists = true;
            }
            else if(clientFile.getName().endsWith(".tar.gz")){
                linuxClientExists = true;
            }
        }
    }

    boolean showEmailTemplate = windowsClientExists || macClientExists || linuxClientExists;
%>
<table><tr><td><img src="images/win.gif" alt=""></td><td><b><fmt:message key="spark.download.windows" /></b></td>
    <% if(windowsClientExists){ %>
    <td><a href="getspark?os=windows"><%= url %>?os=windows</a></td>
    <% } else { %>
    <td><fmt:message key="spark.download.nobuild" /></td>
    <% } %>
    </tr>
    <tr>
        <td><img src="images/mac.gif" alt=""></td><td><b><fmt:message key="spark.download.mac" /></b></td>
    <% if(macClientExists){ %>
        <td><a href="getspark?os=mac"><%= url %>?os=mac</a></td>
    <% } else { %>
        <td><fmt:message key="spark.download.nobuild" /></td>
    <% } %>
    </tr>
      <tr>
        <td><img src="images/zip.gif" alt=""></td><td><b><fmt:message key="spark.download.nix" /></b></td>
    <% if(linuxClientExists){ %>
        <td><a href="getspark?os=linux"><%= url %>?os=linux</a></td>
    <% } else { %>
        <td><fmt:message key="spark.download.nobuild" /></td>
    <% } %>
    </tr>

</table>
<br/><br/>
<% if(showEmailTemplate){%>
<p><b><fmt:message key="spark.download.emailtemplate" /></b></p>
<p>
<fmt:message key="spark.download.emailtemplate.instructions" />
</p>
<div style="padding:4px;background-color:#eee;width:500px;">
<p>
    <fmt:message key="spark.download.emailtemplate.template.part1" />
    <br/><br/>
    <fmt:message key="spark.download.windows" />
    <% if(windowsClientExists) { %>
        <%= url + "?os=windows" %>
    <% } else { %>
        <fmt:message key="spark.download.nobuild" />
    <% } %>
    <br/>
    <fmt:message key="spark.download.mac" />
    <% if(macClientExists) { %>
        <%= url + "?os=mac" %>
    <% } else { %>
        <fmt:message key="spark.download.nobuild" />
    <% } %>
    <br/>
    <fmt:message key="spark.download.nix" />
    <% if(linuxClientExists) { %>
        <%= url + "?os=linux" %>
    <% } else { %>
        <fmt:message key="spark.download.nobuild" />
    <% } %>
    <br/><br/>
    <fmt:message key="spark.download.emailtemplate.template.part2"/>
    <fmt:message key="spark.download.emailtemplate.template.part3">
        <fmt:param value="<%= "<b>" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + "</b>" %>" />
    </fmt:message>
    <fmt:message key="spark.download.emailtemplate.template.part4"/>
</p>
</div>
<% } %>


</body>
</html>
