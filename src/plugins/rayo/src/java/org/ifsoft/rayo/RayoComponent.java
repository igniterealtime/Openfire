/**
 * $Revision $
 * $Date $
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ifsoft.rayo;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;

import org.xmpp.packet.JID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.component.AbstractComponent;
import org.xmpp.jnodes.*;
import org.xmpp.jnodes.nio.LocalIPResolver;
import org.xmpp.packet.*;

import java.util.*;
import java.text.ParseException;
import java.net.URI;
import java.net.URISyntaxException;

import com.rayo.core.*;
import com.rayo.core.verb.*;
import com.rayo.core.validation.*;
import com.rayo.core.xml.providers.*;

import com.sun.voip.server.*;
import com.sun.voip.*;

import org.voicebridge.*;




public class RayoComponent 	extends 	AbstractComponent
							implements 	TreatmentDoneListener,
										CallEventListener,
										DirectCallEventListener {

    private static final Logger Log = LoggerFactory.getLogger(RayoComponent.class);

    private static final String HOST = "host";
    private static final String LOCAL_PORT = "localport";
    private static final String REMOTE_PORT = "remoteport";
    private static final String ID = "id";
    private static final String URI = "uri";
    private static final String defaultIncomingConferenceId = "IncomingCallsConference";

    public static RayoComponent self;

    private final RayoPlugin plugin;

    private RayoProvider rayoProvider = null;
    private SayProvider sayProvider = null;
    private HandsetProvider handsetProvider = null;

    public RayoComponent(final RayoPlugin plugin)
    {
		self = this;
        this.plugin = plugin;
        rayoProvider = new RayoProvider();
        rayoProvider.setValidator(new Validator());

        sayProvider = new SayProvider();
        sayProvider.setValidator(new Validator());

        handsetProvider = new HandsetProvider();
        handsetProvider.setValidator(new Validator());
    }

    public String getName() {
        return "rayo";
    }

    public String getDescription() {
        return "XEP-0327: Rayo";
    }

    @Override
    protected String[] discoInfoFeatureNamespaces() {
        return new String[]{"urn:xmpp:rayo:1"};
    }

    @Override
    protected String discoInfoIdentityCategoryType() {
        return "rayo";
    }

    @Override
    protected IQ handleIQGet(IQ iq) throws Exception {

        final Element element = iq.getChildElement();
        final String namespace = element.getNamespaceURI();

        try {

			if ("urn:xmpp:rayo:handset:1".equals(namespace)) {
				IQ reply = null;

				Object object = handsetProvider.fromXML(element);

				if (object instanceof OnHookCommand) {
					reply = handleOnOffHookCommand((OnHookCommand) object, iq);

				} else if (object instanceof OffHookCommand) {
					reply = handleOnOffHookCommand((OffHookCommand) object, iq);
				}
				return reply;
			}

			if ("urn:xmpp:tropo:say:1".equals(namespace)) {
				IQ reply = null;

				Object object = sayProvider.fromXML(element);

				if (object instanceof Say) {
					reply = handleSay((Say) object, iq);

				} else if (object instanceof PauseCommand) {
					reply = handlePauseCommand(true, iq);

				} else if (object instanceof ResumeCommand) {
					reply = handlePauseCommand(false, iq);
				}
				return reply;
			}

			if ("urn:xmpp:rayo:1".equals(namespace)) {
				IQ reply = null;

				Object object = rayoProvider.fromXML(element);

				if (object instanceof ConnectCommand) {

				} else if (object instanceof HoldCommand) {

				} else if (object instanceof UnholdCommand) {

				} else if (object instanceof MuteCommand) {

				} else if (object instanceof UnmuteCommand) {

				} else if (object instanceof JoinCommand) {
					reply = handleJoinCommand((JoinCommand) object, iq);

				} else if (object instanceof UnjoinCommand) {
					reply = handleUnjoinCommand((UnjoinCommand) object, iq);

				} else if (object instanceof AcceptCommand) {
					reply = handleAcceptCommand((AcceptCommand) object, iq);

				} else if (object instanceof AnswerCommand) {
					reply = handleAnswerCommand((AnswerCommand) object, iq);

				} else if (object instanceof HangupCommand) {
					reply = handleHangupCommand(iq);

				} else if (object instanceof RejectCommand) {

				} else if (object instanceof RedirectCommand) {

				} else if (object instanceof DialCommand) {
					reply = handleDialCommand((DialCommand) object, iq);

				} else if (object instanceof StopCommand) {

				} else if (object instanceof DtmfCommand) {
					reply = handleDtmfCommand((DtmfCommand) object, iq);

				} else if (object instanceof DestroyMixerCommand) {

				}

				return reply;
			}
			return null; // feature not implemented.

        } catch (Exception e) {
            e.printStackTrace();

            final IQ reply = IQ.createResultIQ(iq);
            reply.setError(PacketError.Condition.internal_server_error);
            return reply;
        }
    }


	private IQ handleOnOffHookCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleOnOffHookCommand");

		IQ reply = IQ.createResultIQ(iq);

		if (object instanceof OnHookCommand)
		{
			RelayChannel channel = plugin.getRelayChannel(JID.escapeNode(iq.getFrom().toString()));

			if (channel != null)
			{
				handleOnOffHook(object, channel);

			} else {
				reply.setError(PacketError.Condition.item_not_found);
			}

		} else {

			final Handset handset = ((OffHookCommand) object).getHandset();
			final RelayChannel channel = plugin.createRelayChannel(iq.getFrom(), handset);

			if (channel != null)		// rayo handset component can have only one call
			{
				final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");

				childElement.addAttribute(HOST, LocalIPResolver.getLocalIP());
				childElement.addAttribute(LOCAL_PORT, Integer.toString(channel.getPortA()));
				childElement.addAttribute(REMOTE_PORT, Integer.toString(channel.getPortB()));
				childElement.addAttribute(ID, channel.getAttachment());
				childElement.addAttribute(URI, "handset:" + channel.getAttachment() + "@rayo." + getDomain() + "/" + iq.getFrom().getNode());

				Log.debug("Created handset channel {}:{}, {}:{}, {}:{}", new Object[]{HOST, LocalIPResolver.getLocalIP(), LOCAL_PORT, Integer.toString(channel.getPortA()), REMOTE_PORT, Integer.toString(channel.getPortB())});

				handleOnOffHook(object, channel);

			} else {
				reply.setError(PacketError.Condition.internal_server_error);
			}
		}

		return reply;
	}


    private void handleOnOffHook(Object object, RelayChannel channel)
    {
		final boolean flag = object instanceof OnHookCommand;

		Log.info("RayoComponent handleOnOffHook " + flag);

        try {
			OutgoingCallHandler callHandler = channel.getCallHandler();

			if (callHandler != null)
			{
				callHandler.cancelRequest("Reseting handset to " + (flag ? "on" : "off") + "hook");
				callHandler = null;
			}

			if (!flag)		// offhook
			{
				Handset handset = ((OffHookCommand) object).getHandset();

				String mediaPreference = "PCMU/8000/1";

				if (handset.codec == null || "OPUS".equals(handset.codec))
					mediaPreference = "PCM/48000/2";

				CallParticipant cp = new CallParticipant();
				cp.setCallId(channel.getAttachment());
				cp.setProtocol("WebRtc");
				cp.setConferenceId(handset.mixer);
				cp.setMediaPreference(mediaPreference);
				cp.setRelayChannel(channel);
				cp.setDisplayName(channel.getAttachment());
				cp.setVoiceDetection(true);
				cp.setCallOwner(channel.getFrom().toString());

				if (handset.group != null && ! "".equals(handset.group))
				{
					// set for new or existing conference

					ConferenceManager.getConference(handset.mixer, channel.getMediaPreference(), handset.group, false);
					ConferenceManager.setDisplayName(handset.mixer, handset.group);
				}

				callHandler = new OutgoingCallHandler(this, cp);
				callHandler.start();

				channel.setCallHandler(callHandler);
			}

        } catch (Exception e) {
            e.printStackTrace();
        }

	}

	private IQ handleSay(Say command, IQ iq)
	{
		Log.info("RayoComponent handleSay " + iq.getFrom());

		IQ reply = IQ.createResultIQ(iq);
		final String entityId = iq.getTo().getNode();
		final String treatmentId = command.getPrompt().getText();

		try {
			CallHandler callHandler = CallHandler.findCall(entityId);

			try {
				callHandler.playTreatmentToCall(treatmentId, this);

				final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");
				childElement.addAttribute(URI, (String) "xmpp:" + entityId + "@rayo." + getDomain() + "/" + treatmentId);

			} catch (Exception e1) {
				reply.setError(PacketError.Condition.internal_server_error);
			}

		} catch (NoSuchElementException e) {	// not call, lets try mixer

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(entityId);

				try {
					conferenceManager.addTreatment(treatmentId);

					final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");
					childElement.addAttribute(URI, (String) "xmpp:" + entityId + "@rayo." + getDomain() + "/" + treatmentId);

				} catch (Exception e2) {
					reply.setError(PacketError.Condition.internal_server_error);
				}

			} catch (ParseException e1) {

				reply.setError(PacketError.Condition.item_not_found);
			}
		}

		return reply;
	}

	private IQ handlePauseCommand(boolean flag, IQ iq)
	{
		Log.info("RayoComponent handlePauseCommand " + iq.getFrom());

		IQ reply = IQ.createResultIQ(iq);
		final String entityId = iq.getTo().getNode();
		final String treatmentId = iq.getTo().getResource();

		try {
			CallHandler callHandler = CallHandler.findCall(entityId);

			try {
				callHandler.getMember().pauseTreatment(treatmentId, flag);

			} catch (Exception e1) {
				reply.setError(PacketError.Condition.internal_server_error);
			}

		} catch (NoSuchElementException e) {	// not call, lets try mixer

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(entityId);
				conferenceManager.getWGManager().pauseConferenceTreatment(treatmentId, flag);

			} catch (ParseException e1) {

				reply.setError(PacketError.Condition.item_not_found);
			}
		}

		return reply;
	}


	private IQ handleAcceptCommand(AcceptCommand command, IQ iq)
	{
		String callId = iq.getTo().getNode();
		Log.info("RayoComponent handleAcceptCommand " + iq.getFrom() + " " + callId);

		IQ reply = IQ.createResultIQ(iq);
		JID callJID = getJID(callId);

		if (callJID != null)		// only for XMPP calls
		{
			Map<String, String> headers = command.getHeaders();
			headers.put("call_protocol", "XMPP");

			try {
				Presence presence = new Presence();
				presence.setFrom(iq.getTo());
				presence.setTo(callJID);

				presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, headers)));
				sendPacket(presence);

			} catch (Exception e) {
				reply.setError(PacketError.Condition.item_not_found);
			}
		}

		return reply;
	}

	private IQ handleAnswerCommand(AnswerCommand command, IQ iq)
	{
		String callId = iq.getTo().getNode();
		Log.info("RayoComponent AnswerCommand " + iq.getFrom() + " " + callId);

		IQ reply = IQ.createResultIQ(iq);
		JID callJID = getJID(callId);

		Map<String, String> headers = command.getHeaders();
		CallHandler callHandler = null;

		if (callJID != null)		// XMPP call
		{
			headers.put("call_protocol", "XMPP");

			try {
				Presence presence1 = new Presence();												//to caller
				presence1.setFrom(iq.getTo());
				presence1.setTo(callJID);
				presence1.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
				sendPacket(presence1);

				Presence presence2 = new Presence();												//to called
				presence2.setFrom(iq.getTo());
				presence2.setTo(iq.getFrom());
				presence2.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
				sendPacket(presence2);

				RelayChannel channel = plugin.getRelayChannel(callId);

				if (channel != null)
				{
					callHandler = channel.getCallHandler();

				} else 	reply.setError(PacketError.Condition.item_not_found);

			} catch (Exception e) {
				reply.setError(PacketError.Condition.item_not_found);
				e.printStackTrace();
			}

		} else {

			callHandler = CallHandler.findCall(callId);	// SIP call;

			Presence presence = new Presence();												//to called
			presence.setFrom(iq.getTo());
			presence.setTo(iq.getFrom());
			presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
			sendPacket(presence);
		}

		if (callHandler != null)
		{
			Log.info("RayoComponent handleAnswerCommand found call handler " + callHandler);

			CallParticipant cp = callHandler.getCallParticipant();

			if (cp != null)
			{
				try {
					cp.setStartTimestamp(System.currentTimeMillis());
					cp.setHeaders(headers);
					//cp.setCallOwner(iq.getFrom().toString());

					String recording = cp.getConferenceId() + "-" + cp.getStartTimestamp() + ".au";
					ConferenceManager.recordConference(cp.getConferenceId(), true, recording, "au");

					String destination = iq.getFrom().getNode();
					String source = cp.getName();

					if (callJID != null)
					{
						source = callJID.getNode();
						Config.createCallRecord(source, recording, "xmpp:" + iq.getFrom(), cp.getStartTimestamp(), 0, "dialed") ;
						Config.createCallRecord(destination, recording, "xmpp:" + callJID, cp.getStartTimestamp(), 0, "received") ;

					} else { // incoming SIP

						Config.createCallRecord(destination, recording, "sip:" + cp.getPhoneNumber(), cp.getStartTimestamp(), 0, "received") ;
					}

				} catch (ParseException e1) {
					reply.setError(PacketError.Condition.internal_server_error);
				}
			} else 	reply.setError(PacketError.Condition.item_not_found);

		} else 	reply.setError(PacketError.Condition.item_not_found);

		return reply;
	}



	private IQ handleHangupCommand(IQ iq)
	{
		String callId = iq.getTo().getNode();

		Log.info("RayoComponent handleHangupCommand " + iq.getFrom() + " " + callId);

		IQ reply = IQ.createResultIQ(iq);

		CallHandler callHandler = CallHandler.findCall(callId);

		if (callHandler != null)
		{
			Log.info("RayoComponent handleHangupCommand found callhandler " + callId);

			CallHandler.hangup(callId, "User requested call termination");

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}

		try {
			JID otherParty = getJID(callId);

			if (otherParty != null)
			{
				Log.info("RayoComponent handleHangupCommand found other party " + otherParty);

				Presence presence1 = new Presence();
				presence1.setFrom(iq.getTo());
				presence1.setTo(otherParty);
				presence1.getElement().add(rayoProvider.toXML(new EndEvent(null, null)));
				sendPacket(presence1);
			}

		} catch (Exception e) { }

		return reply;
	}

	private JID getJID(String jid)
	{
		jid = JID.unescapeNode(jid);

		if (jid.indexOf("@") == -1 || jid.indexOf("/") == -1) return null;

		try {
			return new JID(jid);

		} catch (Exception e) {

			return null;
		}
	}

	private IQ handleDtmfCommand(DtmfCommand command, IQ iq)
	{
		Log.info("RayoComponent handleDtmfCommand " + iq.getFrom());

		IQ reply = IQ.createResultIQ(iq);

		try {
			CallHandler callHandler = CallHandler.findCall(iq.getTo().getNode());
			callHandler.dtmfKeys(command.getTones());

		} catch (NoSuchElementException e) {
			reply.setError(PacketError.Condition.item_not_found);
		}

		return reply;
	}

	private IQ handleJoinCommand(JoinCommand command, IQ iq)
	{
		Log.info("RayoComponent handleJoinCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		String mixer = null;

		if (command.getType() == JoinDestinationType.CALL) {
			// TODO join.getTo()
		} else {
			  mixer = command.getTo();
		}

		if (mixer != null)
		{
			try {
				IncomingCallHandler.transferCall(iq.getTo().getNode(), mixer);

			} catch (Exception e) {
				reply.setError(PacketError.Condition.internal_server_error);
			}

		} else {
			reply.setError(PacketError.Condition.feature_not_implemented);
		}

		return reply;
	}

	private IQ handleUnjoinCommand(UnjoinCommand command, IQ iq)
	{
		Log.info("RayoComponent handleUnjoinCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		try {
			IncomingCallHandler.transferCall(iq.getTo().getNode(), defaultIncomingConferenceId);

		} catch (Exception e) {
			reply.setError(PacketError.Condition.internal_server_error);
		}

		return reply;
	}

	private IQ handleDialCommand(DialCommand command, IQ iq)
	{
		Log.info("RayoComponent handleDialCommand " + iq.getFrom());

		Map<String, String> headers = command.getHeaders();
		String bridged = headers.get("voicebridge");

		if (bridged == null)
			return handleHandsetDialCommand(command, iq);
		else
			return handleBridgedDialCommand(command, iq);
	}


	private IQ handleHandsetDialCommand(DialCommand command, IQ iq)
	{
		Log.info("RayoComponent handleHandsetDialCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		Map<String, String> headers = command.getHeaders();
		String from = command.getFrom().toString();
		String to = command.getTo().toString();

		boolean toPhone = to.indexOf("sip:") == 0 || to.indexOf("tel:") == 0;
		boolean toXmpp = to.indexOf("xmpp:") == 0;

		String callId = headers.get("call_id");
		String callerName = headers.get("caller_name");
		String calledName = headers.get("called_name");

		String handsetId = iq.getFrom().toString();

		JoinCommand join = command.getJoin();

        if (join != null)
        {
        	if (join.getType() == JoinDestinationType.CALL) {
        		// TODO join.getTo()
        	} else {

        	}

			reply.setError(PacketError.Condition.feature_not_implemented);

        } else {

			if (callId == null)
			{
					callId =  "rayo-call-" + System.currentTimeMillis();
					headers.put("call_id", callId);
			}

			if (callerName == null)
			{
					callerName =  iq.getFrom().getNode();
					headers.put("caller_name", callerName);
			}

			if (toPhone)
			{
				if (calledName == null)
				{
						calledName =  to;
						headers.put("called_name", calledName);
				}

				CallParticipant cp = new CallParticipant();
				cp.setVoiceDetection(true);
				cp.setCallOwner(handsetId);
				cp.setMediaPreference("PCMU/8000/1");
				cp.setProtocol("SIP");
				cp.setCallId(callId);
				cp.setDisplayName(callerName);
				cp.setPhoneNumber(to);
				cp.setName(calledName);
				cp.setHeaders(headers);

				reply = doPhoneAndPcCall(JID.escapeNode(handsetId), cp, reply);

			} else if (toXmpp){

				headers.put("call_protocol", "XMPP");

				JID destination = getJID(to.substring(5));

				if (destination != null)
				{
					String source = JID.escapeNode(handsetId);

					RelayChannel channel = plugin.getRelayChannel(source); // user mixer of caller

					if (channel != null)
					{
						headers.put("mixer_name", channel.getHandset().mixer);
						headers.put("codec_name", channel.getHandset().codec);

						if (findUser(destination.getNode()) != null)
						{
							routeXMPPCall(reply, destination, source, callId, calledName, headers);

						} else {
							int count = 0;

							try {
								Group group = GroupManager.getInstance().getGroup(destination.getNode());

								for (JID memberJID : group.getMembers())
								{
									if (iq.getFrom().toBareJID().equals(memberJID.toBareJID()) == false)
									{
										Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

										for (ClientSession session : sessions)
										{
											routeXMPPCall(reply, session.getAddress(), source, callId, calledName, headers);
											count++;
										}
									}
								}

								if (count == 0)
								{
									reply.setError(PacketError.Condition.item_not_found);
									return reply;
								}
											// tag conf with group name

								ConferenceManager.setDisplayName(channel.getHandset().mixer, destination.getNode());

							} catch (GroupNotFoundException e) {
								reply.setError(PacketError.Condition.item_not_found);
								return reply;
							}
						}

					} else {
						reply.setError(PacketError.Condition.item_not_found);
					}

				} else {
					reply.setError(PacketError.Condition.item_not_found);
				}

			} else {
				reply.setError(PacketError.Condition.feature_not_implemented);
			}
		}

		return reply;
	}

	private void routeXMPPCall(IQ reply, JID destination, String source, String callId, String calledName, Map<String, String> headers)
	{
		Presence presence = new Presence();
		presence.setFrom(source + "@rayo." + getDomain());
		presence.setTo(destination);

		OfferEvent offer = new OfferEvent(callId);

		try {
			offer.setFrom(new URI("xmpp:" + JID.unescapeNode(source)));
			offer.setTo(new URI("xmpp:" + destination));

		} catch (URISyntaxException e) {
			reply.setError(PacketError.Condition.feature_not_implemented);
			return;
		}

		if (calledName == null)
		{
				calledName =  presence.getTo().getNode();
				headers.put("called_name", calledName);
		}

		offer.setHeaders(headers);

		presence.getElement().add(rayoProvider.toXML(offer));
		sendPacket(presence);

		final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");
		childElement.addAttribute(URI, (String) "xmpp:" + destination);
		childElement.addAttribute(ID, (String)  JID.escapeNode(destination.toString()));
	}


	private IQ handleBridgedDialCommand(DialCommand command, IQ iq)
	{
		Log.info("RayoComponent handleBridgedDialCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		Map<String, String> headers = command.getHeaders();
		String from = command.getFrom().toString();
		String to = command.getTo().toString();

		boolean fromPhone 	= from.indexOf("sip:") == 0 || from.indexOf("tel:") == 0;
		boolean toPhone 	= to.indexOf("sip:") == 0 || to.indexOf("tel:") == 0;
		boolean toHandset 	= to.indexOf("handset:") == 0;
		boolean fromHandset = from.indexOf("handset:") == 0;

		String mixer = null;
		String callerId = headers.get("caller_id");
		String calledId = headers.get("called_id");
		String callerName = headers.get("caller_name");
		String calledName = headers.get("called_name");

		JoinCommand join = command.getJoin();

        if (join != null && join.getTo() != null)
        {
        	if (join.getType() == JoinDestinationType.CALL) {
        		// TODO join.getTo()
        	} else {
        		  mixer = join.getTo();
        	}
        }

		if (callerId == null) callerId =  "rayo-caller-" + System.currentTimeMillis();
		if (calledId == null) calledId =  "rayo-called-" + System.currentTimeMillis();


		CallParticipant cp = new CallParticipant();
		cp.setVoiceDetection(true);
		cp.setCallOwner(iq.getFrom().toString());

		if (fromPhone && toPhone)				// Phone to Phone
		{
			cp.setMediaPreference("PCMU/8000/1");
			cp.setProtocol("SIP");
			cp.setCallId(calledId);
			cp.setPhoneNumber(to.substring(4));
			cp.setName(calledName == null ? cp.getPhoneNumber() : calledName);

			cp.setSecondPartyCallId(callerId);
			cp.setSecondPartyNumber(from.substring(4));
			cp.setSecondPartyName(callerName == null ? cp.getSecondPartyNumber() : callerName);

			if (mixer == null)											// Direct call, migrate to TwoParty later with Join to mixer
			{
				DirectCallHandler callHandler = new DirectCallHandler(cp, this);
				callHandler.start();

			} else {													// TwoParty, use a mixer

				if (mixer == null) mixer = cp.getPhoneNumber();
				cp.setConferenceId(mixer);

				TwoPartyCallHandler callHandler = new TwoPartyCallHandler(this, cp);
				callHandler.start();
			}

			final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");
			childElement.addAttribute(URI, (String) "xmpp:" + calledId + "@rayo." + getDomain());



		} else if (fromPhone && !toPhone)	{							// Phone to PC

			cp.setMediaPreference("PCMU/8000/1");
			cp.setProtocol("SIP");
			cp.setCallId(callerId);
			cp.setPhoneNumber(from.substring(4));
			cp.setName(callerName == null ? cp.getPhoneNumber() : callerName);

			if (mixer == null) mixer = cp.getPhoneNumber();
			cp.setConferenceId(mixer);

			if (toHandset)												// (no offer)
			{
				//doPhoneAndPcCall(new JID(to.substring(8)), cp, reply, mixer);

			} else {													// (offer)

			}


		} else if (!fromPhone && toPhone)	{							// PC to Phone

			cp.setMediaPreference("PCMU/8000/1");
			cp.setProtocol("SIP");
			cp.setCallId(calledId);
			cp.setPhoneNumber(to.substring(4));
			cp.setName(calledName == null ? cp.getPhoneNumber() : calledName);

			if (mixer == null) mixer = cp.getPhoneNumber();
			cp.setConferenceId(mixer);

			if (fromHandset)											// (no offer)
			{
				//doPhoneAndPcCall(new JID(from.substring(8)), cp, reply, mixer);

			} else {													// (offer)

			}


		} else if (!fromPhone && !toPhone)	{							// PC to PC

			if (fromHandset && toHandset)								// (intercom)
			{

			} else {													// (private wire)

				if (toHandset)											// offer from
				{

				} else {												// offer to

				}
			}
		}
		return reply;
	}

	private IQ doPhoneAndPcCall(String handsetId, CallParticipant cp, IQ reply)
	{
		Log.info("RayoComponent doPhoneAndPcCall " + handsetId);

		RelayChannel channel = plugin.getRelayChannel(handsetId);

		if (channel != null)
		{
			try {
				setMixer(channel, reply, cp);

				OutgoingCallHandler outgoingCallHandler = new OutgoingCallHandler(this, cp);

	    		//OutgoingCallHandler handsetHandler = channel.getCallHandler();
	    		//outgoingCallHandler.setOtherCall(handsetHandler);
	   			//handsetHandler.setOtherCall(outgoingCallHandler);

				outgoingCallHandler.start();

				final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");
				childElement.addAttribute(URI, (String) "xmpp:" + cp.getCallId() + "@rayo." + getDomain());
				childElement.addAttribute(ID, (String)  cp.getCallId());

			} catch (Exception e) {
				e.printStackTrace();
				reply.setError(PacketError.Condition.internal_server_error);
			}

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}
		return reply;
	}

    private void setMixer(RelayChannel channel, IQ reply, CallParticipant cp)
    {
		String username = channel.getFrom().getNode();
		String mixer = 	channel.getHandset().mixer;

		ConferenceManager conferenceManager = ConferenceManager.getConference(	mixer,
																				channel.getMediaPreference(),
																				username, false);
		try {
			cp.setConferenceId(mixer);
			cp.setStartTimestamp(System.currentTimeMillis());
			String recording = mixer + "-" + cp.getStartTimestamp() + ".au";
			conferenceManager.recordConference(true, recording, "au");
			Config.createCallRecord(cp.getDisplayName(), recording, cp.getPhoneNumber(), cp.getStartTimestamp(), 0, "dialed") ;

		} catch (ParseException e1) {
			reply.setError(PacketError.Condition.internal_server_error);
		}
    }


    @Override
    public String getDomain() {
        return XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }


    public HandsetProvider getHandsetProvider() {
        return handsetProvider;
    }

    public void treatmentDoneNotification(TreatmentManager treatmentManager)
    {
		Log.info("RayoComponent treatmentDoneNotification " + treatmentManager.getId());
	}

    public void callEventNotification(com.sun.voip.CallEvent callEvent)
    {
		Log.info("RayoComponent callEventNotification " + callEvent);
		JID from = getJID(callEvent.getCallInfo());

		if (from != null)
		{
			String myEvent = com.sun.voip.CallEvent.getEventString(callEvent.getEvent());
			String callState = callEvent.getCallState().toString();

			try {
				CallHandler callHandler = CallHandler.findCall(callEvent.getCallId());

				if (callHandler != null)
				{
					Log.info("RayoComponent callEventNotification found call handler " + callHandler);

					CallParticipant cp = callHandler.getCallParticipant();

					if (cp != null)
					{
						Log.info("RayoComponent callEventNotification found call paticipant " + cp);

						Map<String, String> headers = cp.getHeaders();

						headers.put("call_id", callEvent.getCallId());
						headers.put("mixer_name", callEvent.getConferenceId());
						headers.put("call_protocol", cp.getProtocol());

						Presence presence = new Presence();
						presence.setFrom(callEvent.getCallId() + "@rayo." + getDomain());
						presence.setTo(from);

						if ("001 STATE CHANGED".equals(myEvent))
						{
							if ("100 INVITED".equals(callState)) {
								presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, headers)));
								sendPacket(presence);

							} else if ("200 ESTABLISHED".equals(callState)) {
								presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
								sendPacket(presence);

							} else if ("299 ENDED".equals(callState)) {
								presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("HANGUP"), headers)));
								sendPacket(presence);

								finishCallRecord(cp);
							}

						} else if ("250 STARTED SPEAKING".equals(myEvent)) {
							StartedSpeakingEvent speaker = new StartedSpeakingEvent();
							speaker.setSpeakerId(callEvent.getCallId());
							presence.getElement().add(rayoProvider.toXML(speaker));
							sendPacket(presence);

						} else if ("259 STOPPED SPEAKING".equals(myEvent)) {
							StoppedSpeakingEvent speaker = new StoppedSpeakingEvent();
							speaker.setSpeakerId(callEvent.getCallId());
							presence.getElement().add(rayoProvider.toXML(speaker));
							sendPacket(presence);

						} else if ("269 DTMF".equals(myEvent)) {
							presence.getElement().add(rayoProvider.toXML(new DtmfEvent(callEvent.getCallId(), callEvent.getDtmfKey())));
							sendPacket(presence);

						} else if ("230 TREATMENT DONE".equals(myEvent)) {
							//presence.getElement().add(rayoProvider.toXML(new DtmfEvent(callEvent.getCallId(), callEvent.getDtmfKey())));
							//sendPacket(presence);
							Log.info("230 TREATMENT DONE");
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    }

	private void finishCallRecord(CallParticipant cp)
	{
		if (cp.getStartTimestamp() > 0)
		{
			cp.setEndTimestamp(System.currentTimeMillis());
			Config.updateCallRecord(cp.getStartTimestamp(), (int)((cp.getEndTimestamp() - cp.getStartTimestamp()) / 1000));

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(cp.getConferenceId());
				conferenceManager.recordConference(false, null, null);
			} catch (Exception e) {}

			cp.setStartTimestamp(0);
		}
	}


    public void initiated(String name, String callId)
    {
		Log.info( "RayoComponent initiated " + name + " " + callId);

		Presence presence = new Presence();
		presence.setFrom(callId + "@rayo." + getDomain());
		presence.setTo(new JID(name));
		presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, null)));
		sendPacket(presence);
	}

    public void established(String name, String callId)
    {
		Log.info( "RayoComponent established " + name + " " + callId);

		Presence presence = new Presence();
		presence.setFrom(callId + "@rayo." + getDomain());
		presence.setTo(new JID(name));
		presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, null)));
		sendPacket(presence);
	}

    public void failed(String name, String callId)
    {
		Log.info( "RayoComponent failed " + name + " " + callId);

		Presence presence = new Presence();
		presence.setFrom(callId + "@rayo." + getDomain());
		presence.setTo(new JID(name));
		presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("ERROR"), null)));
		sendPacket(presence);
	}

    public void terminated(String name, String callId)
    {
		Log.info( "RayoComponent terminated " + name + " " + callId);

		Presence presence = new Presence();
		presence.setFrom(callId + "@rayo." + getDomain());
		presence.setTo(new JID(name));
		presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("HANGUP"), null)));
		sendPacket(presence);
	}

	public void sendPacket(Packet packet)
	{
		try {
			ComponentManagerFactory.getComponentManager().sendPacket(this, packet);
		} catch (Exception e) {

			Log.error("RayoComponent sendPacket " + e);
            e.printStackTrace();
		}
	}

    public void notifyConferenceMonitors(ConferenceEvent conferenceEvent)
    {
		Log.info( "RayoComponent notifyConferenceMonitors " + conferenceEvent.toString());

		try {

			if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT) || conferenceEvent.equals(ConferenceEvent.MEMBER_JOINED))
			{
				Log.info("RayoComponent notifyConferenceMonitors looking for call " + conferenceEvent.getCallId() + " " + conferenceEvent.getMemberCount());

				CallHandler callHandler = CallHandler.findCall(conferenceEvent.getCallId());

				if (callHandler != null)
				{
					Log.info("RayoComponent notifyConferenceMonitors found call handler " + callHandler);

					CallParticipant callParticipant = callHandler.getCallParticipant();

					if (callParticipant != null)
					{
						Log.info("RayoComponent notifyConferenceMonitors found owner " + callParticipant.getCallOwner());

						String groupName = ConferenceManager.getDisplayName(conferenceEvent.getConferenceId());

						if (groupName == null)
						{
							routeJoinEvent(callParticipant.getCallOwner(), callParticipant, conferenceEvent);

						} else {

							Group group = GroupManager.getInstance().getGroup(groupName);

							for (JID memberJID : group.getMembers())
							{
								Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

								for (ClientSession session : sessions)
								{
									routeJoinEvent(memberJID.toString(), callParticipant, conferenceEvent);
								}
							}
						}
					}
				}

				try {
					ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(conferenceEvent.getConferenceId());

					if (conferenceManager.getMemberList().size() == 0) {
						conferenceManager.recordConference(false, null, null);
						conferenceManager.endConference(conferenceEvent.getConferenceId());
					}

				} catch (Exception e) {
					//e.printStackTrace();
				}
			}

		} catch (Exception e) {

			Log.error( "RayoComponent Error in notifyConferenceMonitors " + e);
			e.printStackTrace();
		}
    }

    private void routeJoinEvent(String callee, CallParticipant callParticipant, ConferenceEvent conferenceEvent)
    {
		Presence presence = new Presence();
		presence.setFrom(conferenceEvent.getCallId() + "@rayo." + getDomain());
		presence.setTo(callee);

		if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))
		{
			UnjoinedEvent event = new UnjoinedEvent(null, conferenceEvent.getConferenceId(), JoinDestinationType.MIXER);
			presence.getElement().add(rayoProvider.toXML(event));

			finishCallRecord(callParticipant);

		} else {
			JoinedEvent event = new JoinedEvent(null, conferenceEvent.getConferenceId(), JoinDestinationType.MIXER);
			presence.getElement().add(rayoProvider.toXML(event));
		}

		sendPacket(presence);
	}


    private JID findUser(String username)
    {
		Collection<ClientSession> sessions = SessionManager.getInstance().getSessions();
		JID foundUser = null;

		for (ClientSession session : sessions)
		{
			try{
				String userId = session.getAddress().getNode();

				if (username.equals(userId))
				{
					Log.info("Incoming SIP, findUser " + session.getAddress());

					foundUser = session.getAddress();
					break;
				}

			} catch (Exception e) { }
		}
		return foundUser;
	}


    public boolean routeIncomingSIP(CallParticipant cp)
    {
		Log.info("Incoming SIP, call route to user " + cp.getToPhoneNumber());

		JID foundUser = findUser(cp.getToPhoneNumber());

		String callId = "rayo-incoming-" + System.currentTimeMillis();
		cp.setCallId(callId);
		cp.setConferenceId(callId);

		Map<String, String> headers = cp.getHeaders();
		headers.put("mixer_name", callId);
		headers.put("call_protocol", "SIP");

		if (foundUser != null)		// send this call to specific user
		{
			routeSIPCall(foundUser, cp, callId, headers);
			return true;
		}
									// send to members of group
        try {
            Group group = GroupManager.getInstance().getGroup(cp.getToPhoneNumber());
			headers.put("group_name", cp.getToPhoneNumber());

			for (JID memberJID : group.getMembers())
			{
				Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

				for (ClientSession session : sessions)
				{
					routeSIPCall(memberJID, cp, callId, headers);
				}
			}

			return true;

        } catch (GroupNotFoundException e) {
            // Group not found
            return false;
		}
	}

    public void routeSIPCall(JID callee, CallParticipant cp, String callId, Map<String, String> headers)
    {
		Log.info("routeSIPCall to user " + callee);

		if (callee != null)		// send this call to user
		{
			Presence presence = new Presence();
			presence.setFrom(callId + "@rayo." + getDomain());
			presence.setTo(callee);

			OfferEvent offer = new OfferEvent(callId);

			try {
				offer.setTo(new URI("xmpp:" + callee.toString()));
				offer.setFrom(new URI("sip:" + cp.getPhoneNumber()));

			} catch (URISyntaxException e) {
				Log.error("SIP phone nos not URI " + cp.getPhoneNumber() + " " + callee);
			}

			headers.put("called_name", callee.getNode());
			headers.put("caller_name", cp.getName());
			offer.setHeaders(headers);

			presence.getElement().add(rayoProvider.toXML(offer));
			sendPacket(presence);
		}
	}
}
