package org.jivesoftware.openfire.plugin.gojara.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.dom4j.XPath;

public class XpathHelper {

	/**
	 * Search the specified document for Nodes corresponding to the xpath Keep
	 * in mind that you have to use xmpp namespace for searching
	 * Predefined namespaces: 
	 * 	jabber:iq:roster		//roster:*
	 * 	jabber:iq:register		//register:*
	 * 	http://jabber.org/protocol/disco#info			//disco/*
	 * e.g
	 * '//roster:features'
	 * 
	 * @param doc
	 *            document
	 * @param xpath
	 *            with roster namespace for searching in query nodes
	 * @return list of nodes found by xpath expression
	 */
	@SuppressWarnings("unchecked")
	public static List<Node> findNodesInDocument(Document doc, String xpath)
	{
		Map<String, String> namespaceUris = new HashMap<String, String>();
		namespaceUris.put("roster", "jabber:iq:roster");
		namespaceUris.put("discoitems", "http://jabber.org/protocol/disco#items");
		namespaceUris.put("register", "jabber:iq:register");
		namespaceUris.put("disco", "http://jabber.org/protocol/disco#info");
		XPath xPath = DocumentHelper.createXPath(xpath);
		xPath.setNamespaceURIs(namespaceUris);
		return xPath.selectNodes(doc);
	}


	/**
	 * Returns the username from the given jid. user.name@jabber.server.org
	 * returns "user.name"
	 * 
	 * @param jid
	 * @return the extracted username as string
	 */
	public static String getUsernameFromJid(String jid)
	{
		int firstAtPos = jid.indexOf("@");
		return firstAtPos != -1 ? jid.substring(0, firstAtPos) : jid;
	}

}
