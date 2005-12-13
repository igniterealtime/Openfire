<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.wildfire.container.Plugin"%>
<%@ page import="java.io.*"%>
<%@ page import="org.jivesoftware.wildfire.container.PluginManager"%>
<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>
<%
   String pluginName = ParamUtils.getParameter(request, "plugin");
   PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
   Plugin plugin = pluginManager.getPlugin(pluginName);
   if (plugin != null) {
       // Try looking for PNG file first then default to GIF.
       File icon = new File(pluginManager.getPluginDirectory(plugin), "logo_small.png");
       boolean isPng = true;
       if (!icon.exists()) {
           icon = new File(pluginManager.getPluginDirectory(plugin), "logo_small.gif");
           isPng = false;
       }
       if (icon.exists()) {
           // Clear any empty lines added by the JSP declaration. This is required to show
           // the image in resin!
           response.reset();
           if (isPng) {
               response.setContentType("image/png");
           }
           else {
               response.setContentType("image/gif");
           }
           InputStream in = null;
           OutputStream ost = null;
           try {
               in = new FileInputStream(icon);
               ost = response.getOutputStream();

               byte[] buf = new byte[1024];
               int len;
               while ((len = in.read(buf)) >= 0) {
                  ost.write(buf,0,len);
               }
               ost.flush();
           }
           catch (IOException ioe) {

           }
           finally {
               if (in != null) {
                   try { in.close(); } catch (Exception e) { }
               }
               if (ost != null) {
                   try { ost.close(); } catch (Exception e) { }
               }
           }
       }
   }
%>