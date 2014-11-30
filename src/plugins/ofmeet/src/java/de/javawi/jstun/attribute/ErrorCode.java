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

import de.javawi.jstun.util.Utility;
import de.javawi.jstun.util.UtilityException;

public class ErrorCode extends MessageAttribute {
   /* 
    *  0                   1                   2                   3
    *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    * |                   0                     |Class|     Number    |
    * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    * |      Reason Phrase (variable)                                ..
    * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    */
	
	int responseCode;
	String reason;
	
	public ErrorCode() {
		super(MessageAttribute.MessageAttributeType.ErrorCode);
	}
	
	public void setResponseCode(int responseCode) throws MessageAttributeException {
		switch (responseCode) {
		case 400: reason = "Bad Request"; break;
		case 401: reason = "Unauthorized"; break;
		case 420: reason = "Unkown Attribute"; break;
		case 430: reason = "Stale Credentials"; break;
		case 431: reason = "Integrity Check Failure"; break;
		case 432: reason = "Missing Username"; break;
		case 433: reason = "Use TLS"; break;
		case 500: reason = "Server Error"; break;
		case 600: reason = "Global Failure"; break;
		default: throw new MessageAttributeException("Response Code is not valid");
		}
		this.responseCode = responseCode;
	}
	
	public int getResponseCode() {
		return responseCode;
	}
	
	public String getReason() {
		return reason;
	}

	public byte[] getBytes() throws UtilityException {
		int length = reason.length();
		// length adjustment
		if ((length % 4) != 0) {
			length += 4 - (length % 4);
		}
		// message attribute header
		length += 4;
		byte[] result = new byte[length];
		// message attribute header
		// type
		System.arraycopy(Utility.integerToTwoBytes(typeToInteger(type)), 0, result, 0, 2);
		// length
		System.arraycopy(Utility.integerToTwoBytes(length-4), 0, result, 2, 2);
		
		// error code header
		int classHeader = (int) Math.floor(((double)responseCode)/100);
		result[6] = Utility.integerToOneByte(classHeader);
		result[7] = Utility.integerToOneByte(responseCode%100);
		byte[] reasonArray = reason.getBytes();
		System.arraycopy(reasonArray, 0, result, 8, reasonArray.length);		
		return result;
	}
	
	public static ErrorCode parse(byte[] data) throws MessageAttributeParsingException {
		try {
			if (data.length < 4) {
				throw new MessageAttributeParsingException("Data array too short");
			}
			byte classHeaderByte = data[3];
			int classHeader = Utility.oneByteToInteger(classHeaderByte);
			if ((classHeader < 1) || (classHeader > 6)) throw new MessageAttributeParsingException("Class parsing error");
			byte numberByte = data[4];
			int number = Utility.oneByteToInteger(numberByte);
			if ((number < 0) || (number > 99)) throw new MessageAttributeParsingException("Number parsing error");
			int responseCode = (classHeader * 100) + number;
			ErrorCode result = new ErrorCode();
			result.setResponseCode(responseCode);
			return result;
		} catch (UtilityException ue) {
			throw new MessageAttributeParsingException("Parsing error");
		} catch (MessageAttributeException mae) {
			throw new MessageAttributeParsingException("Parsing error");
		}		
	}
}
