/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.calllog;

/**
 *
 * CallLog instance. This class handle all Log information for a call
 *
 * @author Thiago Rocha Camargo
 *
 */
public class CallLog {

	private String username;

	private String numA;

	private String numB;

	private long dateTime;

	private int duration;

	private Type type = Type.dialed;

	public CallLog(String username) {
		this.username = username;
	}

    @SuppressWarnings({"UnnecessarySemicolon"}) // Fix for QDox Source inspector
    public enum Type {
		dialed, received, missed;
    }

	public long getDateTime() {
		return dateTime;
	}

	public void setDateTime(long dateTime) {
		this.dateTime = dateTime;
	}

	public String getNumA() {
		return numA;
	}

	public void setNumA(String numA) {
		this.numA = numA;
	}

	public String getNumB() {
		return numB;
	}

	public void setNumB(String numB) {
		this.numB = numB;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public int getDuration() {
		return duration;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	public String getUsername() {
		return username;
	}

}
