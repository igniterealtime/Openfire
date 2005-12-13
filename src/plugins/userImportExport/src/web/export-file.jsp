<%@ page import="java.io.OutputStream,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.plugin.ImportExportPlugin"
         contentType="application/x-download"%><%
    String fileName = request.getParameter("fileName");
    response.setContentType("application/x-download");
    response.setHeader("Content-Disposition","attachment;filename="+fileName+".xml");
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
    byte[] content = plugin.exportUsersToFile();
    OutputStream os = response.getOutputStream();
    os.write(content);
    os.flush();
    os.close();
%>