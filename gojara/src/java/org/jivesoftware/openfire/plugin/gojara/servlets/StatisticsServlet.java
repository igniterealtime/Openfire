package org.jivesoftware.openfire.plugin.gojara.servlets;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager;
import org.jivesoftware.openfire.plugin.gojara.database.LogEntry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet for live statistics using ajax. Sending last messages back for
 * statistics using json notation.
 * 
 * @author Holger Bergunde
 * 
 */
public class StatisticsServlet extends HttpServlet {

	private static final long serialVersionUID = -6872070494892162304L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		DatabaseManager db = DatabaseManager.getInstance();
		String component = req.getParameter("component");
		String fromString = req.getParameter("date");
		/*
		 * { "packets": [ { "type": "IQ", "from": "holger" }, { "type":
		 * "Message", "from": "babett", "to": "holger", "date": "1243235344" } ]
		 * }
		 */

		int msgCnt = 0;
		int iqCnt = 0;
		int presenceCnt = 0;
		int rosterCnt = 0;

		JSONObject root = new JSONObject();
		if (component != null && fromString != null) {
			JSONArray packetArray = new JSONArray();
			try {
				root.put("packets", packetArray);

				int limit = 40;
				long from = Long.valueOf(fromString);

				Collection<LogEntry> queryResult = db.getLogsByDateAndLimit(from, limit, component);
				for (LogEntry entry : queryResult) {
					JSONObject packet = new JSONObject();
					packet.put("type", entry.getType()).put("to", entry.getTo()).put("from", entry.getFrom())
							.put("date", entry.getDate());
					packetArray.put(packet);

					if (entry.getType().contains("IQ")) {
						iqCnt++;
					} else if (entry.getType().contains("Message")) {
						msgCnt++;
					} else if (entry.getType().contains("Roster")) {
						rosterCnt++;
					} else if (entry.getType().contains("Presence")) {
						presenceCnt++;
					}
				}
				JSONObject numbers = new JSONObject();
				numbers.put("msg", msgCnt);
				numbers.put("iq", iqCnt);
				numbers.put("presence", presenceCnt);
				numbers.put("roster", rosterCnt);
				root.put("numbers", numbers);

			} catch (JSONException e1) {
				e1.printStackTrace();
			}

		}

		resp.getOutputStream().write(root.toString().getBytes());
		resp.getOutputStream().close();
	}

}
