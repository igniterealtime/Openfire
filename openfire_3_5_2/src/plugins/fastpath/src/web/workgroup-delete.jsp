<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%--
  -	$RCSfile$
  -	$Revision: 28091 $
  -	$Date: 2006-03-06 12:38:00 -0800 (Mon, 06 Mar 2006) $
--%>
<%@ page import="org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID"
%>
<!-- Define Administration Bean -->
<%   // Get parameters //
    String wgID = ParamUtils.getParameter(request,"wgID");
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;

    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    JID workgroupJID = new JID(wgID);

    // Load the workgroup object
    Workgroup workgroup = workgroupManager.getWorkgroup(workgroupJID);

    // handle a delete request
    if (cancel) {
        response.sendRedirect("workgroup-properties.jsp?wgID=" + wgID);
    }

    // handle a delete request
    if (delete) {
        workgroupManager.deleteWorkgroup(workgroup);
        response.sendRedirect("workgroup-summary.jsp?wgID="+wgID+"&deleted=true");
        return;
    }
%>
<html>
    <head>
        <title><%= "Delete Workgroup: "+wgID%></title>
        <meta name="subPageID" content="workgroup-properties"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="delete_a_workgroup.html"/>-->
    </head>
    <body>
<p>Are you sure you want to delete this workgroup?</p>
<form action="workgroup-delete.jsp">
  <input type="hidden" name="wgID" value="<%= wgID %>"/>
  <input type="submit" name="delete" value="Yes"/>
  <input type="submit" name="cancel" value="No"/>
</form>
</body>
</html>
