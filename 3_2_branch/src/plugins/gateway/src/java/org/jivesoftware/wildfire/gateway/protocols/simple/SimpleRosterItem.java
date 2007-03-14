/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.simple;

import javax.sip.Dialog;

/**
 * This class represents a roster item of SIP transport.
 */
public class SimpleRosterItem {
	private String         userid;
	private String         nickname;
	private SimplePresence presence;
	private Dialog         outgoingDialog;
	private long           seqNum;
	
	public SimpleRosterItem(String userid, String nickname, long seqNum) {
		this.userid   = userid;
		this.nickname = nickname;
		this.seqNum   = seqNum;
		
		presence = new SimplePresence();
		presence.setTupleStatus(SimplePresence.TupleStatus.CLOSED);
		
		outgoingDialog = null;
	}
	
	public void incrementSeqNum() {
		seqNum++;
	}
	
	public long getSeqNum() {
		return seqNum;
	}
	
	public void updatePresence(String newPresence) throws Exception {
		presence.parse(newPresence);
	}
	
	public void setOutgoingDialog(Dialog outgoingDialog) {
		this.outgoingDialog = outgoingDialog;
	}
	
	public Dialog getOutgoingDialog() {
		return outgoingDialog;
	}
}