package com.ifsoft.websockets.servlet;

import java.io.IOException;
import java.util.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.util.JiveGlobals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WebSocketsAdmin extends HttpServlet
{
	private static Logger Log = LoggerFactory.getLogger("WebSocketsAdmin");

	private String action                   = "edit";

	private String webAppName	            = "ws";


	private String errorMessage = null;

    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
    }


	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		response.setHeader("Expires", "Sat, 6 May 1995 12:00:00 GMT");
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
		response.addHeader("Cache-Control", "post-check=0, pre-check=0");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Content-Type", "text/html");
		response.setHeader("Connection", "close");

		ServletOutputStream out = response.getOutputStream();
		Map<String, String> errors = new HashMap<String, String>();

		action = request.getParameter("action");

		if (action == null) {
			action = "edit";
		}

		if(action.equals("edit")) {

			webAppName		= JiveGlobals.getProperty("websockets.webapp.name", 					webAppName);

			displayPage(out, errors.size());
		}

		else if(action.equals("update")) {

			webAppName				= request.getParameter("webAppName");

			validateFields(errors);

			if(errors.isEmpty()) {

				try {

					JiveGlobals.setProperty("websockets.webapp.name", 				webAppName);

					Log.info("WebSockets Properties updated");
				}
				catch (Exception e) {
					Log.error(e.getMessage(), e);
				}

				response.sendRedirect("websockets-properties");

			}
			else {
				displayPage(out, errors.size());
			}

		}
		else {

			displayPage(out, errors.size());
		}
	}

	private void displayPage(ServletOutputStream out, int errorSize) {

		try {
			out.println("");
			out.println("<html>");
			out.println("    <head>");
			out.println("        <title>WebSockets Properties</title>");
			out.println("        <meta name=\"pageID\" content=\"WEBSOCKETS-PROPERTIES\"/>");
			out.println("    </head>");
			out.println("    <body>");

			if (errorSize > 0) {
				out.println("<div class=\"error\">");
				out.println(errorMessage);
				out.println("</div>");
			}
			out.println("");
			out.println("Use the form below to edit WebSockets Properties.<br>");
			out.println("</p>");
			out.println("<form action=\"websockets-properties\" method=\"get\">");

			if(action.equals("edit")) {
				out.println("<input type='hidden' name='action' value='update'>");

			} else {
				out.println("<input type='hidden' name='action' value='edit'>");
			}
			out.println("");

			out.println("<div class=\"jive-contentBoxHeader\">General</div>");
			out.println("<div class=\"jive-contentBox\">");
			out.println("	 <table>");
			out.println("	 	<tr><td>Web Application Name</td><td><input size='20' type='text' name='webAppName' value='" + webAppName + "'></td>");
			out.println("	 		<td>Name of WebSockets web application.</td></tr>");
			out.println("	 </table>");
			out.println("</div>");
			out.println("");

			out.println("&nbsp;<p/>&nbsp;<p/><input type=\"submit\" value=\"Save Properties\">");
			out.println("</form>");
			out.println("");
			out.println("</body>");
			out.println("</html>");
        }
        catch (Exception e) {
        	Log.error("displayPage exception", e);
        }
	}


	private void validateFields(Map<String, String> errors)
	{
		if(webAppName.length() < 1 ) {
			errors.put("webAppName", "");
			errorMessage = "Please specify a web application name";
		}
	}

}

