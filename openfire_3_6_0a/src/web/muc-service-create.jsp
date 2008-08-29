<%--
  -	$Revision: 10204 $
  -	$Date: 2008-04-11 18:44:25 -0400 (Fri, 11 Apr 2008) $
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%  // Redirect to muc-service-edit-form and set that a service will be created
    response.sendRedirect("muc-service-edit-form.jsp?create=true");
    return;
%>