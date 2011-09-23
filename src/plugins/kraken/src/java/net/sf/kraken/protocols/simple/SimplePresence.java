/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.protocols.simple;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class performs conversions between presence packets of XMPP and SIMPLE formats.
 * <br>
 * By now, SIP presence comforms with <a href="http://www.ietf.org/rfc/rfc4480.txt">RFC4480</a>.
 * @author Patrick Siu
 * @author Daniel Henninger
 */
public class SimplePresence {

    static Logger Log = Logger.getLogger(SimplePresence.class);

    /**
	 * Tuple status of the presence packet.
	 * @author Patrick Siu
	 * @version 0.0.1
	 */
	public enum TupleStatus {
		OPEN  ("open"),
		CLOSED("closed");
		
		private String status;
		
		public static TupleStatus getTupleStatus(String tupleStatusString) throws IllegalArgumentException {
			for (TupleStatus t : values()) {
				if (t.toString().equalsIgnoreCase(tupleStatusString)) return t;
			}
			throw new IllegalArgumentException("There is no matching TupleStatus for this String.");
		}
		
		private TupleStatus(String status) {
			this.status = status;
		}
		
		public boolean isOpen() {
			return status.equals("open");
		}
		
		@Override
        public String toString() {
			return status;
		}
	}
	
	/**
	 * Represents the rpid of the status packet.
	 * <br><br>
	 * Refer to <a href="http://www.ietf.org/rfc/rfc4480.txt">RFC4480</a> for details of these statuses.
	 * @author Patrick Siu
	 * @version 0.0.1
	 */
	public enum Rpid {
		APPOINTMENT      ("appointment"),
		AWAY             ("away"),
		BREAKFAST        ("breakfast"),
		BUSY             ("busy"),
		DINNER           ("dinner"),
		HOLIDAY          ("holiday"),
		IN_TRANSIT       ("in-transit"),
		LOOKING_FOR_WORK ("looking-for-work"),
		LUNCH            ("lunch"),
		MEAL             ("meal"),
		MEETING          ("meeting"),
		ON_THE_PHONE     ("on-the-phone"),
		OTHER            ("other"),
		PERFORMANCE      ("performance"),
		PERMANENT_ABSENCE("permanent-absence"),
		PLAYING          ("playing"),
		PRESENTATION     ("presentation"),
		SHOPPING         ("shopping"),
		SLEEPING         ("sleeping"),
		SPECTATOR        ("spectator"),
		STEERING         ("steering"),
		TRAVEL           ("travel"),
		TV               ("tv"),
		UNKNOWN          ("unknown"),
		VACATION         ("vacation"),
		WORKING          ("working"),
		WORSHIP          ("worship");
		
		private String desc;
		
		public static Rpid getRpid(String rpidString) throws IllegalArgumentException {
			for (Rpid r : values()) {
				if (r.toString().equalsIgnoreCase(rpidString)) return r;
			}
			throw new IllegalArgumentException("There is no matching Rpid for the String.");
		}
		
		private Rpid(String desc) {
			this.desc = desc;
		}
		
		/**
		 * Overridden to return the string description of the constant.
		 */
		@Override
        public String toString() {
			return desc;
		}
	}
	
	private TupleStatus tupleStatus;
	private Rpid        rpid;
	private String      dmNote;
	private String      entity;
	
	/**
	 * Constructor.
	 */
	public SimplePresence() {
		this.tupleStatus = TupleStatus.OPEN;
		this.rpid        = Rpid.UNKNOWN;
		this.dmNote      = "";
		this.entity      = "";
	}
	
	public SimplePresence(TupleStatus tupleStatus) {
		this.tupleStatus = tupleStatus;
		this.rpid        = Rpid.UNKNOWN;
		this.dmNote      = "";
		this.entity      = "";
	}
	
	
	public void setRpid(Rpid rpid) {
		this.rpid = rpid;
	}
	
	public void setDmNote(String dmNote) {
		this.dmNote = dmNote;
	}
	
	public void setEntity(String entity) {
		this.entity = entity;
	}
	
	public void setTupleStatus(TupleStatus tupleStatus) {
		this.tupleStatus = tupleStatus;
	}
	
	
	public Rpid getRpid() {
		return this.rpid;
	}
	
	public String getDmNote() {
		return this.dmNote;
	}
	
	public TupleStatus getTupleStatus() {
		return this.tupleStatus;
	}
	
	private String getEightLength(int hash) {
		StringBuffer buffer = new StringBuffer(Integer.toHexString(hash));
		
		while (buffer.length() < 8) {
			buffer.insert(0, "0");
		}
		
		return new String(buffer);
	}
	
	public String toXML() {
		return
				"<?xml version='1.0' encoding='UTF-8'?>" +
				"<presence xmlns='urn:ietf:params:xml:ns:pidf'" +
				         " xmlns:dm='urn:ietf:params:xml:ns:pidf:data-model'" +
				         " xmlns:rpid='urn:ietf:params:xml:ns:pidf:rpid'" +
				         " xmlns:c='urn:ietf:params:xml:ns:pidf:cipid'" +
				         " entity='" + entity + "'>" +
				"<tuple id='t" + getEightLength(tupleStatus.hashCode()) + "'><status><basic>" + tupleStatus.toString() + "</basic></status></tuple>" +
				"<dm:person id='p" + getEightLength(this.hashCode()) + "'><rpid:activities><rpid:" + rpid.toString() + "/></rpid:activities>" +
//				"<tuple><status><basic>" + tupleStatus.toString() + "</basic></status></tuple>" +
//				"<dm:person><rpid:activities><rpid:" + rpid.toString() + "/></rpid:activities>" +
				((dmNote != null && !dmNote.equals("")) ? "<dm:note>" + dmNote + "</dm:note>" : "") + 
				"</dm:person>" +
				"</presence>";

//		DocumentFactory docFactory = DocumentFactory.getInstance();
//
//		Document xmlDocument = docFactory.createDocument();
//		Element  rootElement = docFactory.createDocument().addElement("presence");
//
//		rootElement.addAttribute("xmlns",      "urn:ietf:params:xml:ns:pidf");
//		rootElement.addAttribute(new QName("xmlns", "dm"),   "urn:ietf:params:xml:ns:pidf:data-model");
//		rootElement.addAttribute(new QName("xmlns", "rpid"), "urn:ietf:params:xml:ns:pidf:rpid");
//		rootElement.addAttribute(new QName("xmlns", "c"),    "urn:ietf:params:xml:ns:pidf:cipid");
//		rootElement.addAttribute("entity",     entity);
//
//		Element tupleElement = rootElement.addElement("tuple");
//		tupleElement.addAttribute("id", "t" + Integer.toHexString(this.hashCode()));
//		tupleElement.addElement("status").addElement("basic").setText(this.tupleStatus.toString());
//
//		Element personElement = rootElement.addElement(new QName("dm", "person"));
//		personElement.addAttribute("id", "p" + Integer.toHexString(this.hashCode()));
//		personElement.addElement(new QName("rpid", "activities")).addElement("rpid:" + this.rpid.toString());
//
//		if (this.dmNote != null && !this.dmNote.trim().equals("")) {
//			personElement.addElement("dm:note").setText(this.dmNote);
//		}
//
//		return xmlDocument.asXML();

//		return result;
	}
	
//	public Presence convertSIPPresenceToXMPP(String sipPresence) {
//		Presence  xmppPresence = new Presence();
//		
//		SAXParser saxParser;
//		try {
//			SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
//			saxParser = saxParserFactory.newSAXParser();
//			
//		} catch (Exception e) {
//			Log.debug("Unable to load parser to parse SIP presence to XMPP Presence.", e);
//			return xmppPresence;
//		}
//		
//		return xmppPresence;
//	}
	
//	public String convertXMPPPresenceToSIP(Presence xmppPresence) {
//		String sipPresence = "";
//		String basic       = "open";
//		String rpid        = "unknown";
//		String dmNote      = "";
//		
//		if (!xmppPresence.isAvailable()) {
//			// Prepare "closed" basic presence.
//			basic = "closed";
//		} else {
//			Presence.Show xmppPresenceShow = xmppPresence.getShow();
//			if (xmppPresenceShow.equals(Presence.Show.away)) {
//				rpid = "away";
//			} else if (xmppPresenceShow.equals(Presence.Show.chat)) {
//				rpid = "away";
//			} else if (xmppPresenceShow.equals(Presence.Show.dnd)) {
//				rpid = "busy";
//			} else if (xmppPresenceShow.equals(Presence.Show.xa)) {
//				rpid = "away";
//			} else {
//				rpid = "";
//			}
//		}
//		
//		sipPresence = "<?xml version='1.0' encoding='UTF-8'?>"
//				+ "<presence xmlns='urn:ietf:params:xml:ns:pidf' xmlns:dm='urn:ietf:params:xml:ns:pidf:data-model' "
//				+ "xmlns:rpid='urn:ietf:params:xml:ns:pidf:rpid' xmlns:c='urn:ietf:params:xml:ns:pidf:cipid' "
//				+ "entity='pres:sip:sipdemo1@192.168.1.199'>"
//				+ "<tuple><status><basic>" + basic + "</basic></status></tuple>"
//				+ "<dm:person id='p3e32d940'><rpid:activities><rpid:" + rpid + "/></rpid:activities></dm:person></presence>";
//		
//		return sipPresence;
//	}
	
	public void parse(String simplePresenceXml) throws Exception {
		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
		SAXParser        saxParser        = saxParserFactory.newSAXParser();
		
		ByteArrayInputStream bais = new ByteArrayInputStream(simplePresenceXml.getBytes());
		
		saxParser.parse(bais, new SimplePresenceParser());
		bais.close();
	}
	
	
	public static SimplePresence parseSimplePresence(String simplePresenceXml) throws Exception {
		SimplePresence simplePresenceObject = new SimplePresence();
		simplePresenceObject.parse(simplePresenceXml);
		
		return simplePresenceObject;
	}
	
	class SimplePresenceParser extends DefaultHandler {
//		private boolean isStartTag   = false;
		private boolean isPresence   = false;
		private boolean isStatus     = false;
		private boolean isStatusType = false;
		private boolean isStatusName = false;
		
		String elementName = null;
		String paramName   = null;
		String userName    = null;
		String statusType  = null;
		String statusName  = null;
		
		public SimplePresenceParser() {
		}
		
		@Override
        public void startElement(String namespaceURI, String sName, String qName, Attributes attrs) throws SAXException {
//			isStartTag  = true;
			elementName = (!sName.equals(""))? sName : qName;
			
			if(elementName.equals("presence")) {
				isPresence = true;
				if (attrs.getIndex("entity") >= 0) {
					entity = attrs.getValue("entity");
				}
			}
			else if(elementName.equals("basic")) {
				isStatus = true;
			}
			else if(elementName.equals("rpid:user-input")) {
				isStatusType = true;
			}
			else if(elementName.startsWith("rpid:")) {
				try {
					String temp = elementName.substring(elementName.indexOf("rpid:") + 5);
					
					if(!temp.equals("activities")) {
						try {
							rpid = Rpid.getRpid(temp);
						}
						catch (IllegalArgumentException ex) {
							Log.debug(ex);
							// Ignore the exception.  Leave it as "unknown".
						}
					}
				}
				catch (Exception ex) {
					Log.debug(ex);
				}
			}
			else if(elementName.equals("dm:note")) {
				isStatusName = true;
			}
			
			if (isPresence) {
//				for(int i = 0; i < attrs.getLength(); i++) {
//					if(attrs.getQName(i).equals("entity")) {
//						userName = attrs.getValue(i).substring(attrs.getValue(i).indexOf("sip:"));
//					}
//				}
			}
		}
		
		@Override
        public void characters(char buf[], int offset, int len) throws SAXException {
			String data = new String(buf, offset, len);
			
			if (isStatus) {
				try {
					tupleStatus = TupleStatus.getTupleStatus(data);
				}
				catch (IllegalArgumentException ex) {
                    // Ignore
                }
			}
			else if (isStatusType) {
//				statusType = data;
			}
			else if (isStatusName) {
				dmNote = data;
				
//				if (rpid.compareTo(Rpid.UNKNOWN) == 0) {
//					try {
//						rpid = Rpid.getRpid(data);
//					}
//					catch (IllegalArgumentException ex) {
//						// Ignore the exception.  Leave it as "unknown".
//					}
//				}
//				statusName = data;
			}
		}
		
		@Override
        public void endElement(String namespaceURI, String sName, String qName) throws SAXException {
//			isStartTag = false;
			elementName = (!sName.equals(""))? sName : qName;
			
			if(elementName.equals("presence")) {
				isPresence   = false;
			}
			else if(elementName.equals("basic")) {
				isStatus     = false;
			}
			else if(elementName.equals("rpid:user-input")) {
				isStatusType = false;
			}
			else if(elementName.equals("dm:note")) {
				isStatusName = false;
			}
		}
		
		@Override
        public void endDocument() throws SAXException {
//			obj.setUser(userName);
//			obj.setType(statusType);
//			obj.setStatus(statusName);
		}
	}
}
