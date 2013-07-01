package org.jivesoftware.openfire.plugin.gojara.database;

/**
 * This class represents a log entry for the GoJara plugin
 * 
 * @author Holger Bergunde
 */
public class LogEntry {

	private String _from;
	private String _to;
	private String _type;
	private long _date;
	private String _component;

	/**
	 * Constructs a log entry
	 * 
	 * @param from
	 *            full qualified JID as String
	 * @param to
	 *            full qualified JID as String
	 * @param type
	 *            class name of packet as String
	 * @param date
	 *            date of the packet in unixtimestamp miliseconds
	 */
	public LogEntry(String from, String to, String type, long date, String component) {
		_from = from;
		_to = to;
		_type = type;
		_date = date;
		_component = component;
	}

	/**
	 * Returns the sender of this packet represented by this log entry
	 * 
	 * @return full qualified jid as string
	 */
	public String getFrom() {
		return _from;
	}

	/**
	 * Returns the receiver of this packet represented by this log entry
	 * 
	 * @return full qualified jid as string
	 */
	public String getTo() {
		return _to;
	}

	/**
	 * Returns the packet type as class name
	 * 
	 * @return class name as string
	 */
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

	public String getComponent() {
		return _component;
	}
}
