<%@ page errorPage="error.jsp" import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.io.FilenameFilter"%>
<%@ page import="org.apache.commons.fileupload.DiskFileUpload"%>
<%@ page import="java.util.List"%>
<%@ page import="org.apache.commons.fileupload.FileUploadException"%>
<%@ page import="org.jivesoftware.util.Log"%>
<%@ page import="org.apache.commons.fileupload.FileItem"%>
<%@ page import="java.util.Iterator"%>
<%@ page import="java.io.FileOutputStream"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
<head>
    <title><fmt:message key="spark.version.title" /></title>
    <meta name="pageID" content="spark-version"/>
    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>

<%
    String submitted = ParamUtils.getParameter(request, "submit");


    String windowClient = "";
    String macClient = "";
    String linuxClient = "";
    String optionalMessage = "";

    boolean updated = false;
    if (submitted != null && submitted.trim().length() > 0) {
        windowClient = ParamUtils.getParameter(request, "windowsClient");
        macClient = ParamUtils.getParameter(request, "macClient");
        linuxClient = ParamUtils.getParameter(request, "linuxClient");

        // Persist.
        if(windowClient != null){
            JiveGlobals.setProperty("spark.windows.client", windowClient);
            updated = true;
        }

        if(macClient != null){
            JiveGlobals.setProperty("spark.mac.client", macClient);
            updated = true;
        }

        if(linuxClient != null){
            JiveGlobals.setProperty("spark.linux.client", linuxClient);
            updated = true;
        }

        String url = request.getRequestURL().toString();
        String server = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        url = url.replace("localhost", server);
        url = url.replace("spark-form.jsp", "getspark");
        JiveGlobals.setProperty("spark.client.downloadURL", url);

        optionalMessage = ParamUtils.getParameter(request, "optionalMessage");
        if(optionalMessage != null){
            JiveGlobals.setProperty("spark.client.displayMessage", optionalMessage);
        }
        else {
            JiveGlobals.deleteProperty("spark.client.displayMessage");
        }
    }
    else {
        windowClient = JiveGlobals.getProperty("spark.windows.client");
        macClient = JiveGlobals.getProperty("spark.mac.client");
        linuxClient = JiveGlobals.getProperty("spark.linux.client");
        optionalMessage = JiveGlobals.getProperty("spark.client.displayMessage");
    }

    if (optionalMessage == null){
        optionalMessage = "";
    }

    File buildDir = new File(JiveGlobals.getHomeDirectory(), "enterprise" + File.separator + "spark");
    // Create the directory if it doesn't exist.
    if (!buildDir.exists()) {
        buildDir.mkdirs();
    }

    DiskFileUpload upload = new DiskFileUpload();
    List items = null;
    try {
        items = upload.parseRequest(request);
    }
    catch (Exception e) {
      // I'm going to ignore this Greg. That's right.
    }


    boolean uploaded = false;
    if (items != null) {
        for (Object item : items) {
            FileItem fileItem = (FileItem) item;
            if (!fileItem.isFormField()) {
                String fieldName = fileItem.getFieldName();

                if ("file".equals(fieldName)) {
                    String filename = fileItem.getName();
                    filename = new File(filename).getName();
                    byte[] data = fileItem.get();

                    if (filename != null && filename.trim().length() > 0) {
                        uploaded = true;

                        // Write out Client to dir.
                        FileOutputStream faos = new FileOutputStream(new File(buildDir, filename));
                        faos.write(data);
                        faos.flush();
                        faos.close();
                    }

                }
            }
        }
    }


%>


<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>

<fmt:message key="spark.version.instructions" />
<br/><br/>

<form name="f" action="spark-form.jsp" enctype="multipart/form-data" method="post">
    <table>
        <tr>
            <td colspan="3"><fmt:message key="spark.version.form.builds" /><tt><%= buildDir.getAbsolutePath()%></tt></td>
        </tr>

        <tr>
            <td><tt><fmt:message key="spark.version.form.upload" /></tt></td>
            <td><input type="file" name="file" size="40" /></td>
            <td><input type="submit" name="upload" value="<fmt:message key="spark.version.form.button" />" /></td>
        </tr>

    </table>
</form>

<% if(updated){%>
<div class="success">
     <fmt:message key="spark.version.form.confirmation.build" />
    </div><br>
<% } %>

<% if(uploaded){ %>
<div class="success">
    <fmt:message key="spark.version.form.confirmation.upload" />
</div><br/>
<% } %>

<form action="spark-form.jsp" method="GET">
<table><tr><td><img src="images/win.gif" alt=""></td><td><b><fmt:message key="spark.version.form.clients.windows" /></b></td></tr></table>

    <%
        File[] list = buildDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".exe");
            }
        });
        if (list != null && list.length > 0) {
    %>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th nowrap><fmt:message key="spark.version.form.clients.active" /></th>
                    <th nowrap><fmt:message key="spark.version.form.clients.name" /></th>
                    <th nowrap><fmt:message key="spark.version.form.clients.date" /></th>
                </tr>

            </thead>
            <tbody>
                <%
                    for (File clientFile : list) {
                        Date buildDate = new Date(clientFile.lastModified());
                        String date = JiveGlobals.formatDateTime(buildDate);
                        String selected = "";
                        if (windowClient != null && clientFile.getName().equals(windowClient)) {
                            selected = "checked";
                        }
                %>
                <tr>
                    <td width="1%" align="center"><input type="radio" name="windowsClient"
                                                         value="<%= clientFile.getName() %>" <%= selected %>></td>
                    <td width="1%"><b><%= clientFile.getName()%>
                    </b></td>
                    <td><%=date %>
                    </td>

                </tr>
                <%
                    }
                %>

            </tbody>

        </table>
    </div>

    <% } else { %>

    <ul><i><fmt:message key="spark.version.form.clients.nobuilds" /></i></ul>
    <% } %>

    <br/>
<table><tr><td><img src="images/mac.gif" alt=""></td><td><b><fmt:message key="spark.version.form.clients.mac" /></b></td></tr></table>

    <%
        list = buildDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".dmg");
            }
        });
        if (list != null && list.length > 0) {
    %>
    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th nowrap><fmt:message key="spark.version.form.clients.active" /></th>
                    <th nowrap><fmt:message key="spark.version.form.clients.name" /></th>
                    <th nowrap><fmt:message key="spark.version.form.clients.date" /></th>
                </tr>

            </thead>
            <tbody>
                <%
                    for (File clientFile : list) {
                        Date buildDate = new Date(clientFile.lastModified());
                        String date = JiveGlobals.formatDateTime(buildDate);
                        String selected = "";
                        if (macClient != null && clientFile.getName().equals(macClient)) {
                            selected = "checked";
                        }
                %>
                <tr>
                    <td width="1%" align="center"><input type="radio" name="macClient"
                                                         value="<%= clientFile.getName()%>" <%= selected%>></td>
                    <td width="1%"><b><%= clientFile.getName()%>
                    </b></td>
                    <td><%=date %>
                    </td>

                </tr>
                <%
                    }
                %>

            </tbody>
        </table>
    </div>
    <% } else { %>

    <ul><i><fmt:message key="spark.version.form.clients.nobuilds" /></i></ul>
    <% } %>
<br/>
<table><tr><td><img src="images/zip.gif" alt=""></td><td><b><fmt:message key="spark.version.form.clients.nix" /></b></td></tr></table>
     <%
        list = buildDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".tar.gz");
            }
        });
        if (list != null && list.length > 0) {
    %>
    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th nowrap><fmt:message key="spark.version.form.clients.active" /></th>
                    <th nowrap><fmt:message key="spark.version.form.clients.name" /></th>
                    <th nowrap><fmt:message key="spark.version.form.clients.date" /></th>
                </tr>

            </thead>
            <tbody>
                <%
                    for (File clientFile : list) {
                        Date buildDate = new Date(clientFile.lastModified());
                        String date = JiveGlobals.formatDateTime(buildDate);
                        String selected = "";
                        if (linuxClient != null && clientFile.getName().equals(linuxClient)) {
                            selected = "checked";
                        }
                %>
                <tr>
                    <td width="1%" align="center"><input type="radio" name="linuxClient"
                                                         value="<%= clientFile.getName()%>" <%= selected%>></td>
                    <td width="1%"><b><%= clientFile.getName()%>
                    </b></td>
                    <td><%=date %>
                    </td>

                </tr>
                <%
                    }
                %>

            </tbody>
        </table>
    </div>
    <% } else { %>

    <ul><i><fmt:message key="spark.version.form.clients.nobuilds" /></i></ul>
    <% } %>


<br/><br/>
    <fieldset>
    <legend><fmt:message key="spark.version.form.optional" /></legend>

    <div>
    <p>
    <fmt:message key="spark.version.form.optional.instructions" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td>
                <textarea name="optionalMessage" cols="40" rows="3" wrap="virtual"><%= optionalMessage%></textarea>

            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br/><br/>


    <input type="submit" name="submit" value="<fmt:message key="spark.version.form.update" />">
</form>

</body>
</html>
