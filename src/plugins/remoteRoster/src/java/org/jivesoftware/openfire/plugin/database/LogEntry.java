package org.jivesoftware.openfire.plugin.database;

/**
 * 
 * This class represents a log entry for the gojara plugin
 * 
 * 
 * @author holger.bergunde
 * 
 */
public class LogEntry {

	private String _from;
	private String _to;
	private String _type;
	private long _date;

	public LogEntry(String from, String to, String type, long date) {
		_from = from;
		_to = to;
		_type = type;
		_date = date;
	}

	public String getFrom() {
		return _from;
	}

	public String getTo() {
		return _to;
	}

	public String getType() {
		return _type;
	}

	/**
	 * Date of logentry
	 * 
	 * @return date in unixtimestamp milliseconds
	 */
	public long getDate() {
		return _date;
	}

}
