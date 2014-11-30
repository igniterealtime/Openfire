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

public class MessageAttributeParsingException extends MessageAttributeException { 
	private static final long serialVersionUID = 3258409534426263605L;

	public MessageAttributeParsingException(String mesg) {
		super(mesg);
	}
}