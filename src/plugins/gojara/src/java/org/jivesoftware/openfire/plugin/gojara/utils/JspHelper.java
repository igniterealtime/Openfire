package org.jivesoftware.openfire.plugin.gojara.utils;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;

/**
 * @author axel.frederik.brand Helper for some functions we call from gojara registration / session jsps
 */
public class JspHelper {

	/**
	 * Create a link that leads to the same page, sorted by the attribute clicked on. The sorting Order depends on the
	 * current sorting order. If element is not the one currently sorted by, its always ASC, else DESC
	 * 
	 * @param column
	 * @param sortParams
	 * @return String with html in it
	 */
	public static String sortingHelperRegistrations(String column, Map<String, String> sortParams) {
		String link_beginning = "<a href=\"gojara-RegistrationsOverview.jsp?sortby=";
		return helpMe(column, sortParams, link_beginning);
	}

	public static String sortingHelperSessions(String column, Map<String, String> sortParams) {
		String link_beginning = "<a href=\"gojara-activeSessions.jsp?sortby=";
		return helpMe(column, sortParams, link_beginning);
	}

	private static String helpMe(String column, Map<String, String> sortParams, String link_beginning) {
		String image_asc = "<img alt=\"sorted ASC\" src=\"/images/sort_ascending.gif\">";
		String image_desc = "<img alt=\"sorted DESC\" src=\"/images/sort_descending.gif\">";

		String ending = "";
		if (column.equals("username"))
			ending = "User Name:";
		else if (column.equals("transport"))
			ending = "Resource:";
		else if (column.equals("lastActivity"))
			ending = "Last Login was at:";
		else if (column.equals("loginTime"))
			ending = "Login Time:";
		else
			ending = "i dont want to be here";
		ending += "</a>";

		String sortinglink = "";
		if (sortParams.containsValue(column)) {
			if (sortParams.containsValue("ASC")) {
				sortinglink = image_asc + link_beginning + column + "&sortorder=DESC\">" + ending;
			} else if (sortParams.containsValue("DESC")) {
				sortinglink = image_desc + link_beginning + column + "&sortorder=ASC\">" + ending;
			}
		} else {
			// This is not the currently sorted colum so we want to sort with it, Ascending.
			sortinglink = link_beginning + column + "&sortorder=ASC\">" + ending;
		}
		return sortinglink;
	}

	/**
	 * Compares specified date to current date and returns String explaining how much Minutes / Hours / Days has passed
	 * since then
	 * 
	 * @param oldDate
	 * @return
	 */
	public static String dateDifferenceHelper(Date oldDate) {
		Timestamp stamp = new Timestamp(System.currentTimeMillis());
		Date currentDate = new Date(stamp.getTime());
		long diff = currentDate.getTime() - oldDate.getTime();

		long diffSeconds = diff / 1000 % 60;
		long diffMinutes = diff / (60 * 1000) % 60;
		long diffHours = diff / (60 * 60 * 1000) % 24;
		long diffDays = diff / (24 * 60 * 60 * 1000);

		return "" + diffSeconds + " Seconds " + diffMinutes + " Minutes " + diffHours + " Hours " + diffDays + " Days ago";
	}
}
