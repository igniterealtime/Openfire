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

import de.javawi.jstun.attribute.*;
import de.javawi.jstun.util.*;

import java.util.*;
import java.util.logging.*;

public class MessageHeader implements MessageHeaderInterface {
	/*
	 *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |      STUN Message Type        |         Message Length        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *                          Transaction ID
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *                                                                 |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 */
	private static Logger logger = Logger.getLogger("de.javawi.stun.header.MessageHeader");
	MessageHeaderType type;
	byte[] id = new byte[16];
	
	TreeMap<MessageAttribute.MessageAttributeType, MessageAttribute> ma = new TreeMap<MessageAttribute.MessageAttributeType, MessageAttribute>();
	
	public MessageHeader() {
		super();
	}
	
	public MessageHeader(MessageHeaderType type) {
		super();
		setType(type);
	}
		
    public void setType(MessageHeaderType type) {
		this.type = type;
    }
    
    public MessageHeaderType getType() {
    	return type;
    }
	
	public static int typeToInteger(MessageHeaderType type) {
		if (type == MessageHeaderType.BindingRequest) return BINDINGREQUEST;
		if (type == MessageHeaderType.BindingResponse) return BINDINGRESPONSE;
		if (type == MessageHeaderType.BindingErrorResponse) return BINDINGERRORRESPONSE;
		if (type == MessageHeaderType.SharedSecretRequest) return SHAREDSECRETREQUEST;
		if (type == MessageHeaderType.SharedSecretResponse) return SHAREDSECRETRESPONSE;
		if (type == MessageHeaderType.SharedSecretErrorResponse) return SHAREDSECRETERRORRESPONSE;
		return -1;
	}
	
	public void setTransactionID(byte[] id) {
		System.arraycopy(id, 0, this.id, 0, 16);
	}
	
	public void generateTransactionID() throws UtilityException {
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 0, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 2, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 4, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 6, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 8, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 10, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 12, 2);
		System.arraycopy(Utility.integerToTwoBytes((int)(Math.random() * 65536)), 0, id, 14, 2);
	}
	
	public byte[] getTransactionID() {
		byte[] idCopy = new byte[id.length];
		System.arraycopy(id, 0, idCopy, 0, id.length);
		return idCopy;
	}
	
	public boolean equalTransactionID(MessageHeader header) {
		byte[] idHeader = header.getTransactionID();
		if (idHeader.length != 16) return false;
		if ((idHeader[0] == id[0]) && (idHeader[1] == id[1]) && (idHeader[2] == id[2]) && (idHeader[3] == id[3]) && 
			(idHeader[4] == id[4]) && (idHeader[5] == id[5]) && (idHeader[6] == id[6]) && (idHeader[7] == id[7]) && 
			(idHeader[8] == id[8]) && (idHeader[9] == id[9]) && (idHeader[10] == id[10]) && (idHeader[11] == id[11]) &&
			(idHeader[12] == id[12]) && (idHeader[13] == id[13]) && (idHeader[14] == id[14]) && (idHeader[15] == id[15])) {
			return true;
		} else {
			return false;
		}
	}
	
	public void addMessageAttribute(MessageAttribute attri) {
		ma.put(attri.getType(), attri);
	}
	
	public MessageAttribute getMessageAttribute(MessageAttribute.MessageAttributeType type) {
		return ma.get(type);
	}
	
	public byte[] getBytes() throws UtilityException {
		int length = 20;
		Iterator<MessageAttribute.MessageAttributeType> it = ma.keySet().iterator();
		while (it.hasNext()) {
			MessageAttribute attri = ma.get(it.next());
			length += attri.getLength();
		}
		// add attribute size + attributes.getSize();
		byte[] result = new byte[length];
		System.arraycopy(Utility.integerToTwoBytes(typeToInteger(type)), 0, result, 0, 2);
		System.arraycopy(Utility.integerToTwoBytes(length-20), 0, result, 2, 2);
		System.arraycopy(id, 0, result, 4, 16);
		
		// arraycopy of attributes
		int offset = 20;
		it = ma.keySet().iterator();
		while (it.hasNext()) {
			MessageAttribute attri = ma.get(it.next());
			System.arraycopy(attri.getBytes(), 0, result, offset, attri.getLength());
			offset += attri.getLength();
		}
		return result;
	}
	
	public int getLength() throws UtilityException {
		return getBytes().length;
	}
	
	public void parseAttributes(byte[] data) throws MessageAttributeParsingException {
		try {
			byte[] lengthArray = new byte[2];
			System.arraycopy(data, 2, lengthArray, 0, 2);
			int length = Utility.twoBytesToInteger(lengthArray);
			System.arraycopy(data, 4, id, 0, 16);
			byte[] cuttedData;
			int offset = 20;
			while (length > 0) {
				cuttedData = new byte[length];
				System.arraycopy(data, offset, cuttedData, 0, length);
				MessageAttribute ma = MessageAttribute.parseCommonHeader(cuttedData); 
				addMessageAttribute(ma);
				length -= ma.getLength();
				offset += ma.getLength();
			}
		} catch (UtilityException ue) {
			throw new MessageAttributeParsingException("Parsing error");
		}
	}
	
	public static MessageHeader parseHeader(byte[] data) throws MessageHeaderParsingException {
		try {
			MessageHeader mh = new MessageHeader();
			byte[] typeArray = new byte[2];
			System.arraycopy(data, 0, typeArray, 0, 2);
			int type = Utility.twoBytesToInteger(typeArray);
			switch (type) {
			case BINDINGREQUEST: mh.setType(MessageHeaderType.BindingRequest); logger.finer("Binding Request received."); break;
			case BINDINGRESPONSE: mh.setType(MessageHeaderType.BindingResponse); logger.finer("Binding Response received."); break;
			case BINDINGERRORRESPONSE: mh.setType(MessageHeaderType.BindingErrorResponse); logger.finer("Binding Error Response received."); break;
			case SHAREDSECRETREQUEST: mh.setType(MessageHeaderType.SharedSecretRequest); logger.finer("Shared Secret Request received."); break;
			case SHAREDSECRETRESPONSE: mh.setType(MessageHeaderType.SharedSecretResponse); logger.finer("Shared Secret Response received."); break;
			case SHAREDSECRETERRORRESPONSE: mh.setType(MessageHeaderType.SharedSecretErrorResponse); logger.finer("Shared Secret Error Response received.");break;
			default: throw new MessageHeaderParsingException("Message type " + type + "is not supported"); 
			}
			return mh;
		} catch (UtilityException ue) {
			throw new MessageHeaderParsingException("Parsing error");
		}
	}
}