<%--
  -	$RCSfile: muc-room-create.jsp,v $
  -	$Revision: 1.2 $
  -	$Date: 2004/12/15 03:57:36 $
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%  // Redirect to muc-room-edit-form and set that a room will be created
    response.sendRedirect("muc-room-edit-form.jsp?&create=true");
    return;
%>