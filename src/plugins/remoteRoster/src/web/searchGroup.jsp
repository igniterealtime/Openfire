<?xml version="1.0" encoding="UTF-8" ?>
<%@page import="org.dom4j.tree.DefaultElement"%>
<%@page import="org.dom4j.*"%>
<%@ page language="java" contentType="text/xml" pageEncoding="ISO-8859-1"%>
<%@ page import="org.jivesoftware.openfire.group.GroupManager"%>
<%@ page import="org.jivesoftware.openfire.group.Group"%>
<%@ page import="java.util.Collection"%>
<%
String param = request.getParameter("search");
Element root = new DefaultElement("result");
if (param != null && param.length() > 0)
{
	GroupManager manager = GroupManager.getInstance();
	Collection<Group> groups = manager.getGroups();
	for (Group gr : groups)
	{
		if (gr.getName().startsWith(param))
		{
			root.addElement("item").addText(gr.getName());
		}
	}
	
}
	out.write(root.asXML());
%>
