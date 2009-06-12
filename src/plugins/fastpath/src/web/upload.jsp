<%@ page import="org.jivesoftware.util.Log,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.xmpp.component.ComponentManagerFactory,
                 org.xmpp.packet.JID,
                 java.util.Iterator,
                 java.util.List,
                 org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager, org.jivesoftware.openfire.user.UserNotFoundException, org.jivesoftware.openfire.fastpath.settings.chat.ChatSetting, org.jivesoftware.util.StringUtils, org.apache.commons.fileupload.DiskFileUpload, org.apache.commons.fileupload.FileItem, org.apache.commons.fileupload.FileUploadException"
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


    String workgroup = "";
    Iterator formItems = items.iterator();
    while (formItems.hasNext()) {
        FileItem fileItem = (FileItem)formItems.next();
        if (fileItem.isFormField()) {
            String fieldName = fileItem.getFieldName();
            String fieldValue = fileItem.getString();
            if ("wgID".equals(fieldName)) {
                workgroup = fieldValue;
                break;
            }
        }
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

    // Go back to workgroup-image-settings.jsp
    try {
        response.sendRedirect("workgroup-image-settings.jsp?wgID=" + workgroup + "&updated=true");
    }
    catch (Exception ex) {
        Log.error(ex);
    }
%>

 <%!
     private String encode(byte[] data) {
         try {
             final String encodedFile = StringUtils.encodeBase64(data);
             return encodedFile;
         }
         catch (Exception ex) {
             Log.error(ex);
         }
         return null;
     }

     private void saveSetting(String encodedFile, String setting, String workgroupName) {
         final ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();
         final JID workgroupJID = new JID(workgroupName);
         Workgroup workgroup = null;
         try {
             workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
         }
         catch (UserNotFoundException e) {
             Log.error(e);
             return;
         }
         ChatSetting set = chatSettingsManager.getChatSetting(workgroup, setting);
         set.setValue(encodedFile);
         chatSettingsManager.updateChatSetting(set);

         workgroup.imagesChanged();
     }

 %>