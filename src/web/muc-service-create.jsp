<%--
  -	$RCSfile:$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%  // Redirect to muc-service-edit-form and set that a service will be created
    response.sendRedirect("muc-service-edit-form.jsp?create=true");
    return;
%>