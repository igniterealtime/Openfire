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

package de.javawi.jstun.header;

public class MessageHeaderException extends Exception {
	private static final long serialVersionUID = 3689066248944103737L;

	public MessageHeaderException(String mesg) {
		super(mesg);
	}
}