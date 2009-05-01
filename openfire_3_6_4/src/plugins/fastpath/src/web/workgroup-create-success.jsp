<%--
  -	$RCSfile$
  -	$Revision: 22570 $
  -	$Date: 2005-10-10 21:07:40 -0700 (Mon, 10 Oct 2005) $
--%>
<%@ page import="org.jivesoftware.util.*"
%>
<%   // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");

    // Handle button clicks:
    /*if (request.getParameter("details") != null) {
        response.sendRedirect("workgroup-properties.jsp?wgID=" + wgID);
        return;
    }

    if (request.getParameter("new") != null) {
        response.sendRedirect("workgroup-create.jsp");
        return;
    }*/

    response.sendRedirect("workgroup-queues.jsp?wgID="+wgID+"&created=true");
%>
