package org.jivesoftware.openfire.plugin.gojara.servlets;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;

/**
 * Searching for groups via ajax from javascript. If there are groups matiching
 * the search string it will send back to javascript using xml
 * 
 * @author Holger Bergunde
 * 
 */
public class SearchGroupServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String param = req.getParameter("search");
		Element root = new DefaultElement("result");
		if (param != null && param.length() > 0) {
			GroupManager manager = GroupManager.getInstance();
			Collection<Group> groups = manager.getGroups();
			for (Group gr : groups) {
				if (gr.getName().startsWith(param)) {
					root.addElement("item").addText(gr.getName());
				}
			}
		}
		resp.getOutputStream().write(root.asXML().getBytes());
		resp.getOutputStream().close();
	}

}
