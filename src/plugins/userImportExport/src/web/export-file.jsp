<%@ page import="java.io.OutputStream,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.plugin.ImportExportPlugin"
         contentType="application/x-download"%><%
    String fileName = request.getParameter("fileName");
    boolean xep227Support = ParamUtils.getBooleanParameter(request, "xep227support", false);

    response.setContentType("application/x-download");
    response.setHeader("Content-Disposition","attachment;filename="+fileName+".xml");
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
    byte[] content = plugin.exportUsersToByteArray(xep227Support);
    OutputStream os = response.getOutputStream();
    os.write(content);
    os.flush();
    os.close();
%>
