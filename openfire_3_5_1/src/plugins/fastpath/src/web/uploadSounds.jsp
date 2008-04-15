<%@ page import="org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 org.apache.commons.fileupload.DiskFileUpload,
                 org.apache.commons.fileupload.FileItem,
                 org.apache.commons.fileupload.FileUploadException,
                 org.xmpp.component.ComponentManagerFactory,
                 org.xmpp.packet.JID,
                 java.util.Iterator,
                 java.util.List,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.openfire.user.UserNotFoundException,
                 org.jivesoftware.xmpp.workgroup.UnauthorizedException,
                 org.jivesoftware.xmpp.workgroup.DbProperties"
%>
<%
    DiskFileUpload upload = new DiskFileUpload();
    List items = null;
    try {
        items = upload.parseRequest(request);
    }
    catch (FileUploadException e) {
        e.printStackTrace();
    }

    String workgroup = request.getParameter("wgID");
    if (!ModelUtil.hasLength(workgroup)) {
        workgroup = (String)request.getSession().getAttribute("workgroup");
    }

    Iterator iter = items.iterator();
    byte[] data = null;
    while (iter.hasNext()) {
        FileItem item = (FileItem)iter.next();

        if (!item.isFormField()) {
            String setting = item.getFieldName();
            String filename = item.getName();
            data = item.get();

            if (filename != null && filename.trim().length() > 0) {
                // Know let's encode the file.
                final String encodedFile = encode(data);
                saveSetting(encodedFile, setting, workgroup);
            }
        }
    }

    // Go back to workgroup-sound-settings.jsp
    try {
        response.sendRedirect("workgroup-sound-settings.jsp?wgID=" + workgroup + "&updated=true");
    }
    catch (Exception ex) {
        ComponentManagerFactory.getComponentManager().getLog().error(ex);
    }
%>

 <%!
     private String encode(byte[] data) {
         try {
             final String encodedFile = StringUtils.encodeBase64(data);
             return encodedFile;
         }
         catch (Exception ex) {
             ComponentManagerFactory.getComponentManager().getLog().error(ex);
         }
         return null;
     }

     private void saveSetting(String encodedFile, String setting, String workgroupName) {
         final JID workgroupJID = new JID(workgroupName);
         Workgroup workgroup = null;
         try {
             workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
         }
         catch (UserNotFoundException e) {
             ComponentManagerFactory.getComponentManager().getLog().error(e);
             return;
         }

         DbProperties props = workgroup.getProperties();
         try {
             props.setProperty(setting, encodedFile);
         }
         catch (UnauthorizedException e) {
             e.printStackTrace();
         }
         workgroup.imagesChanged();
     }

 %>