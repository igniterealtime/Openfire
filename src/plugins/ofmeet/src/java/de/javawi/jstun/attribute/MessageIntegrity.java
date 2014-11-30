/*
 * This file is part of JSTUN. 
 * 
 * Copyright (c) 2005 Thomas King <king@t-king.de> - All rights
 * reserved.
 * 
 * This software is licensed under either the GNU Public License (GPL),
 * or the Apache 2.0 license. Copies of both license agreements are
 * included in this distribution.
 */

package de.javawi.jstun.attribute;

public class MessageIntegrity extends MessageAttribute {
	// incomplete message integrity implementation
	public MessageIntegrity() {
		super(MessageAttribute.MessageAttributeType.MessageIntegrity);
	}
	
	public byte[] getBytes() {
		return new byte[0];
	}
	
	public static MessageIntegrity parse(byte[] data) {
		return new MessageIntegrity();
	}
}
