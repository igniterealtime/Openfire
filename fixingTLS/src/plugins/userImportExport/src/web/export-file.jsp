<%@ page import="java.io.OutputStream,
                 org.jivesoftware.messenger.plugin.ImportExportPlugin"
%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />

<%
    admin.init(request, response, session, application, out);

    String fileName = request.getParameter("fileName");
	
    response.setContentType("application/x-download");
    response.setHeader("Content-Disposition","attachment;filename="+fileName+".xml");
	
    ImportExportPlugin plugin = (ImportExportPlugin) admin.getXMPPServer().getPluginManager().getPlugin("userimportexport");
    byte[] content = plugin.exportUsersToFile();
    
    OutputStream os = response.getOutputStream();
    os.write(content);
    os.flush();
    os.close();
%>
