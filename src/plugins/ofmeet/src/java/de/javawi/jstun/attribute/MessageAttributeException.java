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

public class MessageAttributeException extends Exception {
	private static final long serialVersionUID = 3258131345099404850L;

	public MessageAttributeException(String mesg) {
		super(mesg);
	}
}