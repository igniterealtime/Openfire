/**
 *    Copyright 2012 Voxbone SA/NV
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ifsoft.sip;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.SipException;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.*;
import org.slf4j.Logger;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;


public class SipSubscription extends TimerTask
{
    private static final Logger Log = LoggerFactory.getLogger(SipSubscription.class);
	boolean active = false; // false=pending true=active
	String localTag;
	String remoteTag;
	Address localParty;
	Address remoteParty;
	String callId;

	long cseq;

	String contact;
	LinkedList<Address> rl;

	long expires;

	private static Timer timer = new Timer("Subscription Thread");


	/*
	 * Creates a subscription from information in an xml file
	 */
	SipSubscription(String file)
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try
		{
			DocumentBuilder db = dbf.newDocumentBuilder();

			Document dom = db.parse(file);
			Element docEle = dom.getDocumentElement();

			NodeList nl = docEle.getChildNodes();
			if (nl != null && nl.getLength() > 0)
			{
				for (int i = 0 ; i < nl.getLength();i++)
				{
					Node n = nl.item(i);
					if (n instanceof Element)
					{
						Element el = (Element) n;
						Log.debug("Got a node of " + el.getNodeName() + ":" + el.getTextContent());
						if (el.getNodeName().equals("active"))
						{
							this.active = Boolean.parseBoolean(el.getTextContent());
						}
						else if (el.getNodeName().equals("remote"))
						{
							this.remoteTag = el.getAttribute("tag");
							this.remoteParty = SipService.addressFactory.createAddress(el.getTextContent());
						}
						else if (el.getNodeName().equals("local"))
						{
							this.localTag = el.getAttribute("tag");
							this.localParty = SipService.addressFactory.createAddress(el.getTextContent());
						}
						else if (el.getNodeName().equals("callid"))
						{
							this.callId = el.getTextContent();
						}
						else if (el.getNodeName().equals("cseq"))
						{
							this.cseq = Long.parseLong(el.getTextContent());
						}
						else if (el.getNodeName().equals("contact"))
						{
							this.contact = el.getTextContent();
						}
						else if (el.getNodeName().equals("routeset"))
						{
							this.rl = new LinkedList<Address>();
							NodeList routeList = el.getElementsByTagName("route");
							for (int j = 0; j < routeList.getLength(); j++)
							{
								Element route = (Element) routeList.item(j);

								Address addr = SipService.addressFactory.createAddress(route.getTextContent());
								this.rl.add(addr);
							}
						}
						else if (el.getNodeName().equals("expires"))
						{
							this.expires = Long.parseLong(el.getTextContent());
						}
					}
				}
			}
		}
		catch (ParserConfigurationException e)
		{
			Log.error("Error loading subscriptions from file", e);
		}
		catch (SAXException e)
		{
			Log.error("Error loading subscriptions from file", e);
		}
		catch (IOException e)
		{
			Log.error("Error loading subscriptions from file", e);
		}
		catch (DOMException e)
		{
			Log.error("Error loading subscriptions from file", e);
		}
		catch (ParseException e)
		{
			Log.error("Error loading subscriptions from file", e);
		}
	}

	/*
	 * Creates an outbound subscription
	 */
	SipSubscription(String from, String to) throws ParseException
	{
		this.localParty = SipService.addressFactory.createAddress("sip:" + from + "@" + SipService.sipListener.host);
		this.remoteParty = SipService.addressFactory.createAddress("sip:" + to + "@" + SipService.sipListener.host);

		this.localTag = Integer.toString((int) (Math.random() * 100000));
		this.remoteTag = null;

		callId = SipService.sipProvider.getNewCallId().getCallId();

		this.cseq = 1;

		@SuppressWarnings("unused")
		ListeningPoint listeningPoint = SipService.sipProvider.getListeningPoint(ListeningPoint.UDP);
		this.contact = "sip:" + to + "@" + SipService.getRemoteIP();

		rl = new LinkedList<Address>();

		expires = System.currentTimeMillis() + (3600 * 1000);
	}

	public void schedule()
	{
		timer.schedule(this, 1800 * 1000, 1800 * 1000);
	}

	public void schedule(long nextCall)
	{
		timer.schedule(this, nextCall, 1800 * 1000);
	}

	/*
	 * Creates a Subscription Object based on a received subscribe
	 */
	SipSubscription(Request req)
	{
		CallIdHeader ch = (CallIdHeader) req.getHeader("Call-ID");
		ToHeader th = (ToHeader) req.getHeader("To");
		FromHeader fh = (FromHeader) req.getHeader("From");
		rl = new LinkedList<Address>();

		this.callId = ch.getCallId();

		// incoming request from=remote

		ContactHeader cont = (ContactHeader) req.getHeader(ContactHeader.NAME);
		if (cont != null)
		{
			this.contact = cont.getAddress().getURI().toString();
		}

		// This is a server dialog. The top most record route
		// header is the one that is closest to us. We extract the
		// route list in the same order as the addresses in the
		// incoming request.

		if (rl.isEmpty())
		{
			ListIterator<?> rrl = req.getHeaders(RecordRouteHeader.NAME);
			while (rrl.hasNext())
			{
				RecordRouteHeader rrh = (RecordRouteHeader) rrl.next();
				rl.add(rrh.getAddress());
			}
		}

		remoteTag = fh.getTag();
		remoteParty = fh.getAddress();
		localTag = th.getTag();
		localParty = th.getAddress();

		if (localTag == null)
		{
			localTag = Integer.toString((int) (Math.random() * 100000));
		}

		ExpiresHeader eh = (ExpiresHeader) req.getHeader(ExpiresHeader.NAME);
		this.expires = System.currentTimeMillis() + (eh.getExpires() * 1000);

		cseq = 1;
	}

	public void makeActive()
	{
		this.active = true;
	}

	public boolean isActive()
	{
		return this.active;
	}

/*
	public void sendNotify(boolean expire, Presence pres)
	{
		FromHeader fromHeader = null;
		ToHeader toHeader = null;
		URI requestURI = null;

		try
		{
			requestURI = SipService.addressFactory.createURI(this.contact);
			toHeader = SipService.headerFactory.createToHeader(this.remoteParty, this.remoteTag);
			fromHeader = SipService.headerFactory.createFromHeader(this.localParty, this.localTag);

			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			ViaHeader viaHeader = null;

			ListeningPoint lp = SipService.sipProvider.getListeningPoint(ListeningPoint.UDP);

			viaHeader = SipService.headerFactory.createViaHeader(lp.getIPAddress(), lp.getPort(), lp.getTransport(), null);
			viaHeaders.add(viaHeader);

			CallIdHeader callIdHeader = SipService.headerFactory.createCallIdHeader(this.callId);
			CSeqHeader cSeqHeader = SipService.headerFactory.createCSeqHeader(this.cseq++, Request.NOTIFY);
			MaxForwardsHeader maxForwards = SipService.headerFactory.createMaxForwardsHeader(70);

			Request request = null;
			if (pres != null)
			{
				ContentTypeHeader ch = SipService.headerFactory.createContentTypeHeader("application", "pidf+xml");

				request = SipService.messageFactory.createRequest(requestURI, Request.NOTIFY, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards, ch, pres.buildPidf(((SipURI) this.remoteParty.getURI()).getHost()));
			}
			else
			{
				request = SipService.messageFactory.createRequest(requestURI, Request.NOTIFY, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
			}

			EventHeader eph = SipService.headerFactory.createEventHeader("presence");
			request.addHeader(eph);

			if (expire)
			{
				SubscriptionStateHeader ssh = SipService.headerFactory.createSubscriptionStateHeader("terminated;reason=timeout");
				request.addHeader(ssh);
			}
			else
			{
				long duration = (this.expires - System.currentTimeMillis()) / 1000;
				SubscriptionStateHeader ssh = SipService.headerFactory.createSubscriptionStateHeader("active;expires=" + duration);
				request.addHeader(ssh);
				try
				{
					SipSubscriptionManager.saveWatcher(this);
				}
				catch (IOException e)
				{
					Log.error("Error persisting watcher", e);
				}
				catch (SAXException e)
				{
					Log.error("Error persisting watcher", e);
				}
			}

			String fromUser = ((SipURI) this.localParty.getURI()).getUser();
			Address localAddress = SipService.addressFactory.createAddress("sip:" + fromUser + "@" + lp.getIPAddress() + ":" + lp.getPort());

			ContactHeader ch = SipService.headerFactory.createContactHeader(localAddress);
			request.addHeader(ch);

			if (this.rl != null && !this.rl.isEmpty())
			{
				ListIterator<Address> li = this.rl.listIterator();
				while (li.hasNext())
				{
					request.addHeader(SipService.headerFactory.createRouteHeader(li.next()));
				}
			}

			ClientTransaction t = SipService.sipProvider.getNewClientTransaction(request);

			t.sendRequest();
		}
		catch (ParseException e)
		{
			Log.error("Error on SipSubscription:sendNotify", e);
		}
		catch (InvalidArgumentException e)
		{
			Log.error("Error on SipSubscription:sendNotify", e);
		}
		catch (TransactionUnavailableException e)
		{
			Log.error("Error on SipSubscription:sendNotify", e);
		}
		catch (SipException e)
		{
			Log.error("Error on SipSubscription:sendNotify", e);
		}
	}
*/
	public void sendSubscribe(boolean expire)
	{
		FromHeader fromHeader = null;
		ToHeader toHeader = null;
		URI requestURI = null;

		try
		{
			requestURI = SipService.addressFactory.createURI(this.contact);
			toHeader = SipService.headerFactory.createToHeader(this.remoteParty, this.remoteTag);
			fromHeader = SipService.headerFactory.createFromHeader(this.localParty, this.localTag);

			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			ViaHeader viaHeader = null;

			ListeningPoint lp = SipService.sipProvider.getListeningPoint(ListeningPoint.UDP);

			viaHeader = SipService.headerFactory.createViaHeader(lp.getIPAddress(), lp.getPort(), lp.getTransport(), null);
			viaHeaders.add(viaHeader);

			CallIdHeader callIdHeader = SipService.headerFactory.createCallIdHeader(this.callId);
			CSeqHeader cSeqHeader = SipService.headerFactory.createCSeqHeader(this.cseq++, Request.SUBSCRIBE);
			MaxForwardsHeader maxForwards = SipService.headerFactory.createMaxForwardsHeader(70);

			Request request = SipService.messageFactory.createRequest(requestURI, "SUBSCRIBE", callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

			EventHeader eph = SipService.headerFactory.createEventHeader("presence");
			request.addHeader(eph);

			if (expire)
			{
				ExpiresHeader eh = SipService.headerFactory.createExpiresHeader(0);
				request.addHeader(eh);
				this.cancel();
			}
			else
			{
				ExpiresHeader eh = SipService.headerFactory.createExpiresHeader(3600);
				request.addHeader(eh);
				this.expires = System.currentTimeMillis() + (3600 * 1000);
				try
				{
					SipSubscriptionManager.saveSubscription(this);
				}
				catch (IOException e)
				{
					Log.error("Error persisting subscriber", e);
				}
				catch (SAXException e)
				{
					Log.error("Error persisting subscriber", e);
				}
			}

			String fromUser = ((SipURI) this.localParty.getURI()).getUser();
			Address localAddress = SipService.addressFactory.createAddress("sip:" + fromUser + "@" + lp.getIPAddress() + ":" + lp.getPort());

			ContactHeader ch = SipService.headerFactory.createContactHeader(localAddress);
			request.addHeader(ch);

			if (this.rl != null && !this.rl.isEmpty())
			{
				ListIterator<Address> li = this.rl.listIterator();
				while (li.hasNext())
				{
					request.addHeader(SipService.headerFactory.createRouteHeader(li.next()));
				}
			}

			ClientTransaction t = SipService.sipProvider.getNewClientTransaction(request);

			t.sendRequest();
		}
		catch (ParseException e)
		{
			Log.error("Error on SipSubscription:sendSubscribe", e);
		}
		catch (InvalidArgumentException e)
		{
			Log.error("Error on SipSubscription:sendSubscribe", e);
		}
		catch (TransactionUnavailableException e)
		{
			Log.error("Error on SipSubscription:sendSubscribe", e);
		}
		catch (SipException e)
		{
			Log.error("Error on SipSubscription:sendSubscribe", e);
		}
	}


	// update subscribe on receipt of 200 Ok
	public void updateSubscription(Response resp)
	{
		ContactHeader cont = (ContactHeader) resp.getHeader(ContactHeader.NAME);
		ToHeader th = (ToHeader) resp.getHeader("To");
		FromHeader fh = (FromHeader) resp.getHeader("From");

		if (cont != null)
		{
			this.contact = cont.getAddress().getURI().toString();
		}

		// This is a client dialog so we extract the record
		// route from the response and reverse its order to
		// create a route list.

		// ignore Record-Route in 1xx messages
		if (this.rl.isEmpty() && resp.getStatusCode() >= 200)
		{
			ListIterator<?> rrl = resp.getHeaders(RecordRouteHeader.NAME);
			while (rrl.hasNext())
			{
				RecordRouteHeader rrh = (RecordRouteHeader) rrl.next();
				this.rl.addFirst(rrh.getAddress());
			}
		}
		this.remoteTag = th.getTag();
		this.localTag = fh.getTag();

		try
		{
			SipSubscriptionManager.saveSubscription(this);
		}
		catch (IOException e)
		{
			Log.error("Error persisting subscriber", e);
		}
		catch (SAXException e)
		{
			Log.error("Error persisting subscriber", e);
		}
	}

	// bug Fix for sip communicator, if the 200ok lacks a contact get it from the first notify
	public void updateSubscription(Request req)
	{
		ContactHeader cont = (ContactHeader) req.getHeader(ContactHeader.NAME);
		if (cont != null && this.contact.endsWith(SipService.getRemoteIP()))
		{
			this.contact = cont.getAddress().getURI().toString();
		}
	}

	public void buildSubscriptionXML(ContentHandler hd) throws SAXException
	{
		AttributesImpl atts = new AttributesImpl();

		String activeStr = Boolean.toString(this.active);
		hd.startElement("", "", "active", atts);
		hd.characters(activeStr.toCharArray(), 0, activeStr.length());
		hd.endElement("", "", "active");

		atts.addAttribute("", "", "tag", "", remoteTag);
		hd.startElement("", "", "remote", atts);
		String party = remoteParty.toString();
		hd.characters(party.toCharArray(), 0, party.length());
		hd.endElement("", "", "remote");

		atts.clear();

		atts.addAttribute("", "", "tag", "", localTag);
		hd.startElement("", "", "local", atts);
		party = localParty.toString();
		hd.characters(party.toCharArray(), 0, party.length());
		hd.endElement("", "", "local");
		atts.clear();

		hd.startElement("", "", "callid", atts);
		hd.characters(callId.toCharArray(), 0, callId.length());
		hd.endElement("", "", "callid");

		String cseqStr = Long.toString(this.cseq);
		hd.startElement("", "", "cseq", atts);
		hd.characters(cseqStr.toCharArray(), 0, cseqStr.length());
		hd.endElement("", "", "cseq");

		hd.startElement("", "", "contact", atts);
		hd.characters(contact.toCharArray(), 0, contact.length());
		hd.endElement("", "", "contact");

		hd.startElement("", "", "routeset", atts);
		ListIterator<Address> li = rl.listIterator();
		while(li.hasNext())
		{
			String addr = li.next().toString();
			hd.startElement("", "", "route", atts);
			hd.characters(addr.toCharArray(), 0, addr.length());
			hd.endElement("", "", "route");

		}
		hd.endElement("", "", "routeset");

		String expireStr = Long.toString(this.expires);
		hd.startElement("", "", "expires", atts);
		hd.characters(expireStr.toCharArray(), 0, expireStr.length());
		hd.endElement("", "", "expires");
	}

	// This is a task to refresh subscriptions
	public void run()
	{
		// Time's up, refresh the subscription
		sendSubscribe(false);
	}

}
