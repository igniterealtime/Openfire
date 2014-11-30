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

import java.util.logging.*;

import de.javawi.jstun.util.*;


public abstract class MessageAttribute implements MessageAttributeInterface {
	private static Logger logger = Logger.getLogger("de.javawi.stun.util.MessageAttribute");
	MessageAttributeType type;
	
	public MessageAttribute() {
	}
	
	public MessageAttribute(MessageAttributeType type) {
		setType(type);
	}
	
	public void setType(MessageAttributeType type) {
		this.type = type;
	}
	
	public MessageAttribute.MessageAttributeType getType() {
		return type;
	}
	
	public static int typeToInteger(MessageAttributeType type) {
		if (type == MessageAttributeType.MappedAddress) return MAPPEDADDRESS;
		if (type == MessageAttributeType.ResponseAddress) return RESPONSEADDRESS;
		if (type == MessageAttributeType.ChangeRequest) return CHANGEREQUEST;
		if (type == MessageAttributeType.SourceAddress) return SOURCEADDRESS;
		if (type == MessageAttributeType.ChangedAddress) return CHANGEDADDRESS;
		if (type == MessageAttributeType.Username) return USERNAME;
		if (type == MessageAttributeType.Password) return PASSWORD;
		if (type == MessageAttributeType.MessageIntegrity) return MESSAGEINTEGRITY;
		if (type == MessageAttributeType.ErrorCode) return ERRORCODE;
		if (type == MessageAttributeType.UnknownAttribute) return UNKNOWNATTRIBUTE;
		if (type == MessageAttributeType.ReflectedFrom) return REFLECTEDFROM;
		if (type == MessageAttributeType.Dummy) return DUMMY;
		return -1;
	}
	
	public static MessageAttributeType intToType(long type) {
		if (type == MAPPEDADDRESS) return MessageAttributeType.MappedAddress;
		if (type == RESPONSEADDRESS) return MessageAttributeType.ResponseAddress;
		if (type == CHANGEREQUEST) return MessageAttributeType.ChangeRequest;
		if (type == SOURCEADDRESS) return MessageAttributeType.SourceAddress;
		if (type == CHANGEDADDRESS) return MessageAttributeType.ChangedAddress;
		if (type == USERNAME) return MessageAttributeType.Username;
		if (type == PASSWORD) return MessageAttributeType.Password;
		if (type == MESSAGEINTEGRITY) return MessageAttributeType.MessageIntegrity;
		if (type == ERRORCODE) return MessageAttributeType.ErrorCode;
		if (type == UNKNOWNATTRIBUTE) return MessageAttributeType.UnknownAttribute;
		if (type == REFLECTEDFROM) return MessageAttributeType.ReflectedFrom;
		if (type == DUMMY) return MessageAttributeType.Dummy;
		return null;
	}
	
	abstract public byte[] getBytes() throws UtilityException;
	//abstract public MessageAttribute parse(byte[] data) throws MessageAttributeParsingException;
	
	public int getLength() throws UtilityException {
		int length = getBytes().length;
		return length;
	}
	
	public static MessageAttribute parseCommonHeader(byte[] data) throws MessageAttributeParsingException {
		try {			
			byte[] typeArray = new byte[2];
			System.arraycopy(data, 0, typeArray, 0, 2);
			int type = Utility.twoBytesToInteger(typeArray);
			byte[] lengthArray = new byte[2];
			System.arraycopy(data, 2, lengthArray, 0, 2);
			int lengthValue = Utility.twoBytesToInteger(lengthArray);
			byte[] valueArray = new byte[lengthValue];
			System.arraycopy(data, 4, valueArray, 0, lengthValue);
			MessageAttribute ma;
			switch (type) {
			case MAPPEDADDRESS: ma = MappedAddress.parse(valueArray); break;
			case RESPONSEADDRESS: ma = ResponseAddress.parse(valueArray); break;
			case CHANGEREQUEST: ma = ChangeRequest.parse(valueArray); break;
			case SOURCEADDRESS: ma = SourceAddress.parse(valueArray); break;
			case CHANGEDADDRESS: ma = ChangedAddress.parse(valueArray); break;
			case USERNAME: ma = Username.parse(valueArray); break;
			case PASSWORD: ma = Password.parse(valueArray); break;
			case MESSAGEINTEGRITY: ma = MessageIntegrity.parse(valueArray); break;
			case ERRORCODE: ma = ErrorCode.parse(valueArray); break;
			case UNKNOWNATTRIBUTE: ma = UnknownAttribute.parse(valueArray); break;
			case REFLECTEDFROM: ma = ReflectedFrom.parse(valueArray); break;
			default:
				if (type <= 0x7fff) {
					throw new UnknownMessageAttributeException("Unkown mandatory message attribute", intToType(type));
				} else {
					logger.finer("MessageAttribute with type " + type + " unkown.");
					ma = Dummy.parse(valueArray);
					break;
				}
			}
			return ma;
		} catch (UtilityException ue) {
			throw new MessageAttributeParsingException("Parsing error");
		}
	}
}
