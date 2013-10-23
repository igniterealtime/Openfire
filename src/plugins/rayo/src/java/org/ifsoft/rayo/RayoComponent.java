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

import org.dom4j.*;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.muc.spi.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;

import org.jivesoftware.util.JiveGlobals;

import org.xmpp.packet.JID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
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
										CallEventListener {

    private static final Logger Log = LoggerFactory.getLogger(RayoComponent.class);

    private static final String RAYO_CORE = "urn:xmpp:rayo:1";
    private static final String RAYO_SAY = "urn:xmpp:tropo:say:1";
    private static final String RAYO_HANDSET = "urn:xmpp:rayo:handset:1";

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
    }

    public void doStart()
    {
		Log.info("RayoComponent initialize " + jid);

		XMPPServer server = XMPPServer.getInstance();

		server.getIQDiscoInfoHandler().addServerFeature(RAYO_CORE);
       	rayoProvider = new RayoProvider();
        rayoProvider.setValidator(new Validator());

		server.getIQDiscoInfoHandler().addServerFeature(RAYO_SAY);
        sayProvider = new SayProvider();
        sayProvider.setValidator(new Validator());

		server.getIQDiscoInfoHandler().addServerFeature(RAYO_HANDSET);
        handsetProvider = new HandsetProvider();
        handsetProvider.setValidator(new Validator());

		createIQHandlers();

		Plugin fastpath = server.getPluginManager().getPlugin("fastpath");

		if (fastpath != null)
		{
			Log.info("RayoComponent found Fastpath");
		}
	}

	public void doStop()
	{
		Log.info("RayoComponent shutdown ");

        XMPPServer server = XMPPServer.getInstance();

        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_CORE);
        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_SAY);
        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_HANDSET);

		destroyIQHandlers();
	}

    public String getName() {
        return "rayo";
    }

    public String getDescription() {
        return "XEP-0327: Rayo";
    }

    @Override
    protected String[] discoInfoFeatureNamespaces() {
        return new String[]{RAYO_CORE};
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

			if (RAYO_HANDSET.equals(namespace)) {
				IQ reply = null;

				Object object = handsetProvider.fromXML(element);

				if (object instanceof OnHookCommand) {
					OnHookCommand command = (OnHookCommand) object;
					reply = handleOnOffHookCommand(command, iq);

				} else if (object instanceof OffHookCommand) {
					OffHookCommand command = (OffHookCommand) object;
					reply = handleOnOffHookCommand(command, iq);

				} else if (object instanceof MuteCommand) {
					reply = handleMuteCommand((MuteCommand) object, iq);

				} else if (object instanceof UnmuteCommand) {
					reply = handleMuteCommand((UnmuteCommand) object, iq);

				} else if (object instanceof HoldCommand) {
					reply = handleHoldCommand((HoldCommand) object, iq);

				} else if (object instanceof PrivateCommand) {
					reply = handlePrivateCommand(object, iq);

				} else if (object instanceof PublicCommand) {
					reply = handlePrivateCommand(object, iq);
				}
				return reply;
			}

			if (RAYO_SAY.equals(namespace)) {
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

			if (RAYO_CORE.equals(namespace)) {
				IQ reply = null;

				Object object = rayoProvider.fromXML(element);

				if (object instanceof JoinCommand) {
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
						// implemented as hangup on client

				} else if (object instanceof RedirectCommand) {
					RedirectCommand redirect = (RedirectCommand) object;
					DialCommand dial = new DialCommand();
					dial.setTo(redirect.getTo());
					dial.setFrom(new URI("xmpp:" + iq.getFrom()));
					dial.setHeaders(redirect.getHeaders());

					reply = handleDialCommand((DialCommand) dial, iq);

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

	private IQ handleHoldCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleHoldCommand");

		IQ reply = IQ.createResultIQ(iq);
		String callId = iq.getTo().getNode();		// far party

		CallHandler handler = CallHandler.findCall(callId);

		if (handler != null)
		{
			handler.getCallParticipant().setHeld(true);

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}

		return reply;
	}


	private IQ handleMuteCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleMuteCommand");

		boolean muted = object instanceof MuteCommand;

		IQ reply = IQ.createResultIQ(iq);
		String callId = JID.escapeNode(iq.getFrom().toString());	// handset

		CallHandler handler = CallHandler.findCall(callId);

		if (handler != null)
		{
			handler.setMuted(muted);

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(handler.getCallParticipant().getConferenceId());
				ArrayList memberList = conferenceManager.getMemberList();

				synchronized (memberList)
				{
					for (int i = 0; i < memberList.size(); i++)
					{
						ConferenceMember member = (ConferenceMember) memberList.get(i);
						CallHandler callHandler = member.getCallHandler();
						CallParticipant cp = callHandler.getCallParticipant();

						String target = cp.getCallOwner();

						Log.info( "RayoComponent handleMuteCommand route event to " + target);

						if (target != null)
						{
							Presence presence = new Presence();
							presence.setFrom(callId + "@rayo." + getDomain());
							presence.setTo(target);

							if (muted)
							{
								MutedEvent event = new MutedEvent();
								presence.getElement().add(handsetProvider.toXML(event));
							} else {
								UnmutedEvent event = new UnmutedEvent();
								presence.getElement().add(handsetProvider.toXML(event));
							}

							sendPacket(presence);
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}

		return reply;
	}


	private IQ handlePrivateCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handlePrivateCommand");

		boolean privateCall = object instanceof PrivateCommand;

		IQ reply = IQ.createResultIQ(iq);
		String callId = JID.escapeNode(iq.getFrom().toString());	// handset

		CallHandler handler = CallHandler.findCall(callId);

		if (handler != null)
		{
			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(handler.getCallParticipant().getConferenceId());
				conferenceManager.setPrivateCall(privateCall);
				ArrayList memberList = conferenceManager.getMemberList();

				synchronized (memberList)
				{
					for (int i = 0; i < memberList.size(); i++)
					{
						ConferenceMember member = (ConferenceMember) memberList.get(i);
						CallHandler callHandler = member.getCallHandler();
						CallParticipant cp = callHandler.getCallParticipant();

						String target = cp.getCallOwner();

						Log.info( "RayoComponent handlePrivateCommand route event to " + target);

						if (target != null)
						{
							Presence presence = new Presence();
							presence.setFrom(callId + "@rayo." + getDomain());
							presence.setTo(target);

							if (privateCall)
							{
								PrivateEvent event = new PrivateEvent();
								presence.getElement().add(handsetProvider.toXML(event));
							} else {
								PublicEvent event = new PublicEvent();
								presence.getElement().add(handsetProvider.toXML(event));
							}

							sendPacket(presence);
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}

		return reply;
	}

	private IQ handleOnOffHookCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleOnOffHookCommand");

		IQ reply = IQ.createResultIQ(iq);
		String handsetId = JID.escapeNode(iq.getFrom().toString());

		if (object instanceof OnHookCommand)
		{
			CallHandler handler = CallHandler.findCall(handsetId);

			if (handler != null)
			{
				handleOnOffHook(handsetId, object, plugin.getRelayChannel(handsetId), reply);

			} else {
				reply.setError(PacketError.Condition.item_not_found);
			}

		} else {

			final Handset handset = ((OffHookCommand) object).getHandset();

			if (handset.sipuri == null)	// webrtc handset
			{
				final RelayChannel channel = plugin.createRelayChannel(iq.getFrom(), handset);

				if (channel != null)
				{
					final Element childElement = reply.setChildElement("ref", RAYO_CORE);

					childElement.addAttribute(HOST, LocalIPResolver.getLocalIP());
					childElement.addAttribute(LOCAL_PORT, Integer.toString(channel.getPortA()));
					childElement.addAttribute(REMOTE_PORT, Integer.toString(channel.getPortB()));
					childElement.addAttribute(ID, channel.getAttachment());
					childElement.addAttribute(URI, "handset:" + channel.getAttachment() + "@rayo." + getDomain() + "/" + iq.getFrom().getNode());

					Log.debug("Created WebRTC handset channel {}:{}, {}:{}, {}:{}", new Object[]{HOST, LocalIPResolver.getLocalIP(), LOCAL_PORT, Integer.toString(channel.getPortA()), REMOTE_PORT, Integer.toString(channel.getPortB())});

					handleOnOffHook(handsetId, object, channel, reply);

				} else {
					reply.setError(PacketError.Condition.internal_server_error);
				}

			} else {					// SIP handset

				final Element childElement = reply.setChildElement("ref", RAYO_CORE);

				childElement.addAttribute(ID, handsetId);
				childElement.addAttribute(URI, "handset:" + handsetId + "@rayo." + getDomain() + "/" + iq.getFrom().getNode());

				Log.info("Created SIP handset channel " + handset.sipuri);

				handleOnOffHook(handsetId, object, null, reply);
			}
		}

		return reply;
	}


    private void handleOnOffHook(String handsetId, Object object, RelayChannel channel, IQ reply)
    {
		final boolean flag = object instanceof OnHookCommand;

		Log.info("RayoComponent handleOnOffHook " + flag);

        try {
			CallHandler handler = CallHandler.findCall(handsetId);

			if (handler != null)
			{
				handler.cancelRequest("Reseting handset to " + (flag ? "on" : "off") + "hook");
				handler = null;
			}

			if (!flag)		// offhook
			{
				Handset handset = ((OffHookCommand) object).getHandset();

				String mediaPreference = "PCMU/8000/1";

				if (handset.codec == null || "OPUS".equals(handset.codec))
					mediaPreference = "PCM/48000/2";

				CallParticipant cp = new CallParticipant();
				cp.setCallId(handsetId);
				cp.setConferenceId(handset.mixer);
				cp.setDisplayName("rayo-handset-" + System.currentTimeMillis());
				cp.setName(cp.getDisplayName());
				cp.setVoiceDetection(true);
				cp.setCallOwner(JID.unescapeNode(handsetId));

				String label = (new JID(cp.getCallOwner())).getNode();

				if (handset.group != null && ! "".equals(handset.group))
				{
					label = handset.group;
				}

				ConferenceManager cm = ConferenceManager.getConference(handset.mixer, mediaPreference, label, false);

				if (handset.callId != null && "".equals(handset.callId) == false)
				{
					cm.setCallId(handset.callId);		// set answering far party call id for mixer
				}

				if (handset.group != null && ! "".equals(handset.group))
				{
					cm.setGroupName(handset.group);
				}

				if (cm.isPrivateCall() == false || cm.getMemberList().size() < 2)
				{
					if (channel == null)
					{
						cp.setMediaPreference("PCMU/8000/1");
						cp.setPhoneNumber(handset.sipuri);
						cp.setAutoAnswer(true);
						cp.setProtocol("SIP");

					} else {
						cp.setMediaPreference(mediaPreference);
						cp.setRelayChannel(channel);
						cp.setProtocol("WebRtc");
					}

					OutgoingCallHandler callHandler = new OutgoingCallHandler(this, cp);
					callHandler.start();

					if (channel != null)
					{
						channel.setCallHandler(callHandler);
					}

				} else {

					reply.setError(PacketError.Condition.not_allowed);
				}
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

				final Element childElement = reply.setChildElement("ref", RAYO_CORE);
				childElement.addAttribute(URI, (String) "xmpp:" + entityId + "@rayo." + getDomain() + "/" + treatmentId);

			} catch (Exception e1) {
				reply.setError(PacketError.Condition.internal_server_error);
			}

		} catch (NoSuchElementException e) {	// not call, lets try mixer

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(entityId);

				try {
					conferenceManager.addTreatment(treatmentId);

					final Element childElement = reply.setChildElement("ref", RAYO_CORE);
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
		Map<String, String> headers = command.getHeaders();

		String callId = iq.getTo().getNode();	// destination JID escaped
		String callerId = headers.get("caller_id"); // source JID
		String mixer = headers.get("mixer_name");

		Log.info("RayoComponent handleAcceptCommand " + callerId + " " + callId + " " + mixer);

		IQ reply = IQ.createResultIQ(iq);
		JID callJID = getJID(callId);

		if (callJID != null)		// only for XMPP calls
		{
			if (mixer != null)
			{
				headers.put("call_protocol", "XMPP");
				callerId = callerId.substring(5);		// remove xmpp: prefix

				Presence presence = new Presence();
				presence.setFrom(iq.getTo());
				presence.setTo(callerId);

				presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, headers)));
				sendPacket(presence);

			} else reply.setError(PacketError.Condition.item_not_found);
		}

		return reply;
	}

	private IQ handleAnswerCommand(AnswerCommand command, IQ iq)
	{
		Map<String, String> headers = command.getHeaders();

		IQ reply = IQ.createResultIQ(iq);

		String callId = iq.getTo().getNode(); // destination JID escaped
		String callerId = headers.get("caller_id"); // source JID

		Log.info("RayoComponent AnswerCommand " + callerId + " " + callId);

		if (callerId != null)
		{
			JID callJID = getJID(callId);

			CallHandler callHandler = null;
			CallHandler handsetHandler = null;

			if (callJID != null)								// XMPP call
			{
				callerId = callerId.substring(5);		// remove xmpp: prefix

				headers.put("call_protocol", "XMPP");
				headers.put("call_owner", callerId);
				headers.put("call_action", "join");

				try {
					Presence presence1 = new Presence();												//to caller
					presence1.setFrom(iq.getTo());
					presence1.setTo(callerId);
					presence1.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
					sendPacket(presence1);

					callHandler = CallHandler.findCall(callId);
					handsetHandler = CallHandler.findCall(JID.escapeNode(callerId));

				} catch (Exception e) {
					reply.setError(PacketError.Condition.item_not_found);
					e.printStackTrace();
				}

			} else {

				callHandler = CallHandler.findCall(callId);										// SIP call;
				handsetHandler = CallHandler.findCall(JID.escapeNode(iq.getFrom().toString()));
			}

			if (callHandler != null && handsetHandler != null)
			{
				CallParticipant cp = callHandler.getCallParticipant();
				CallParticipant hp = handsetHandler.getCallParticipant();

				Log.info("RayoComponent handleAnswerCommand found call handlers " + cp.getCallId() + " " + hp.getCallId());

				try {
					long start = System.currentTimeMillis();
					cp.setStartTimestamp(start);
					cp.setHandset(hp);
					hp.setFarParty(cp);
					hp.setStartTimestamp(start);

					cp.setHeaders(headers);

					String recording = cp.getConferenceId() + "-" + cp.getStartTimestamp() + ".au";
					ConferenceManager.recordConference(cp.getConferenceId(), true, recording, "au");

					String destination = iq.getFrom().getNode();
					String source = cp.getName();

					if (callJID != null)
					{
						source = (new JID(callerId)).getNode();

						Config.createCallRecord(source, recording, "xmpp:" + iq.getFrom(), cp.getStartTimestamp(), 0, "dialed") ;
						Config.createCallRecord(destination, recording, "xmpp:" + callerId, cp.getStartTimestamp(), 0, "received");

						sendMessage(new JID(callerId), iq.getFrom(), "Call started", recording);

					} else { // incoming SIP

						Config.createCallRecord(destination, recording, "sip:" + cp.getPhoneNumber(), cp.getStartTimestamp(), 0, "received") ;

						sendMessage(iq.getFrom(), new JID(cp.getCallId() + "@" + getDomain()), "Call started", recording);
					}

				} catch (ParseException e1) {
					reply.setError(PacketError.Condition.internal_server_error);
				}

			} else 	reply.setError(PacketError.Condition.item_not_found);

		} else reply.setError(PacketError.Condition.item_not_found);

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

			CallParticipant cp = callHandler.getCallParticipant();

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(cp.getConferenceId());

				Log.info("RayoComponent handleHangupCommand one person left, cancel call " + conferenceManager.getMemberList().size());

				if (conferenceManager.getMemberList().size() <= 2)
				{
					CallHandler.hangup(callId, "User requested call termination");
				}

			} catch (Exception e) {}

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}

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
		Log.info("RayoComponent handleHandsetDialCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		Map<String, String> headers = command.getHeaders();
		String from = command.getFrom().toString();
		String to = command.getTo().toString();

		boolean toPhone = to.indexOf("sip:") == 0 || to.indexOf("tel:") == 0;
		boolean toXmpp = to.indexOf("xmpp:") == 0;

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

					CallHandler handsetHandler = CallHandler.findCall(source);

					if (handsetHandler != null)
					{
						CallParticipant hp = handsetHandler.getCallParticipant();

						headers.put("mixer_name", hp.getConferenceId());

						if (findUser(destination.getNode()) != null)
						{
							routeXMPPCall(reply, destination, source, calledName, headers, hp.getConferenceId());

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
											routeXMPPCall(reply, session.getAddress(), source, calledName, headers, hp.getConferenceId());
											count++;
										}
									}
								}

							} catch (GroupNotFoundException e) {

								if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").hasChatRoom(destination.getNode())) {

									MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(destination.getNode());

									if (room != null)
									{
										for (MUCRole role : room.getOccupants())
										{
											if (iq.getFrom().toBareJID().equals(role.getUserAddress().toBareJID()) == false)
											{
												routeXMPPCall(reply, role.getUserAddress(), source, calledName, headers, hp.getConferenceId());
												count++;
											}
										}
									}

								} else {
									reply.setError(PacketError.Condition.item_not_found);
								}
							}

							if (count == 0)
							{
								reply.setError(PacketError.Condition.item_not_found);
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

	private void routeXMPPCall(IQ reply, JID destination, String source, String calledName, Map<String, String> headers, String mixer)
	{
		String callId = JID.escapeNode(destination.toString());

		Presence presence = new Presence();
		presence.setFrom(callId + "@rayo." + getDomain());
		presence.setTo(destination);

		OfferEvent offer = new OfferEvent(null);

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

		final Element childElement = reply.setChildElement("ref", RAYO_CORE);
		childElement.addAttribute(URI, (String) "xmpp:" + presence.getFrom());
		childElement.addAttribute(ID, (String) callId);

		presence.getElement().add(rayoProvider.toXML(offer));
		sendPacket(presence);
	}


	private IQ doPhoneAndPcCall(String handsetId, CallParticipant cp, IQ reply)
	{
		Log.info("RayoComponent doPhoneAndPcCall " + handsetId);

		CallHandler handsetHandler = CallHandler.findCall(handsetId);

		if (handsetHandler != null)
		{
			try {
				setMixer(handsetHandler, reply, cp);

				OutgoingCallHandler outgoingCallHandler = new OutgoingCallHandler(this, cp);

	    		//outgoingCallHandler.setOtherCall(handsetHandler);
	   			//handsetHandler.setOtherCall(outgoingCallHandler);

				outgoingCallHandler.start();

				final Element childElement = reply.setChildElement("ref", RAYO_CORE);
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

    private void setMixer(CallHandler handsetHandler, IQ reply, CallParticipant cp)
    {
		CallParticipant hp = handsetHandler.getCallParticipant();

		try {
			hp.setFarParty(cp);
			cp.setHandset(hp);

			long start = System.currentTimeMillis();
			cp.setStartTimestamp(start);
			hp.setStartTimestamp(start);

			String mixer = 	hp.getConferenceId();

			ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(mixer);
			cp.setConferenceId(mixer);
			cp.setCallId(mixer);
			conferenceManager.setCallId(mixer);

			String recording = mixer + "-" + cp.getStartTimestamp() + ".au";
			conferenceManager.recordConference(true, recording, "au");
			Config.createCallRecord(cp.getDisplayName(), recording, cp.getPhoneNumber(), cp.getStartTimestamp(), 0, "dialed") ;

			sendMessage(new JID(cp.getCallOwner()), new JID(cp.getCallId() + "@" + getDomain()), "Call started", recording);

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
					CallParticipant hp = cp.getHandset();

					if (cp != null)
					{
						Log.info("RayoComponent callEventNotification found call paticipant " + cp);

						Map<String, String> headers = cp.getHeaders();
						headers.put("mixer_name", callEvent.getConferenceId());
						headers.put("call_protocol", cp.getProtocol());

						Presence presence = new Presence();
						presence.setFrom(callEvent.getCallId() + "@rayo." + getDomain());
						presence.setTo(from);

						if ("001 STATE CHANGED".equals(myEvent))
						{
							if ("100 INVITED".equals(callState)) {

								if (cp.isAutoAnswer() == false)	// SIP handset, no ringing event
								{
									presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, headers)));
									sendPacket(presence);
								}

							} else if ("200 ESTABLISHED".equals(callState)) {


							} else if ("299 ENDED".equals(callState)) {

							}

						} else if ("250 STARTED SPEAKING".equals(myEvent)) {

							broadcastSpeaking(true, callEvent.getCallId(), callEvent.getConferenceId(), from);

						} else if ("259 STOPPED SPEAKING".equals(myEvent)) {

							broadcastSpeaking(false, callEvent.getCallId(), callEvent.getConferenceId(), from);

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

	private void broadcastSpeaking(Boolean startSpeaking, String callId, String conferenceId, JID from)
	{
		Log.info( "RayoComponent broadcastSpeaking " + startSpeaking + " " + callId + " " + conferenceId + " " + from);

		try {
			ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(conferenceId);
			ArrayList memberList = conferenceManager.getMemberList();

			synchronized (memberList)
			{
				for (int i = 0; i < memberList.size(); i++)
				{
					ConferenceMember member = (ConferenceMember) memberList.get(i);
					CallHandler callHandler = member.getCallHandler();

					if (callHandler != null)
					{
						CallParticipant cp = callHandler.getCallParticipant();

						String target = cp.getCallOwner();

						Log.info( "RayoComponent broadcastSpeaking checking " + target);

						if (target != null && callId.equals(cp.getCallId()) == false)
						{
							Presence presence = new Presence();
							presence.setFrom(callId + "@rayo." + getDomain());
							presence.setTo(target);

							if (startSpeaking)
							{
								StartedSpeakingEvent speaker = new StartedSpeakingEvent();
								speaker.setSpeakerId(callId);
								presence.getElement().add(rayoProvider.toXML(speaker));
							} else {
								StoppedSpeakingEvent speaker = new StoppedSpeakingEvent();
								speaker.setSpeakerId(callId);
								presence.getElement().add(rayoProvider.toXML(speaker));
							}

							sendPacket(presence);
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void finishCallRecord(CallParticipant cp)
	{
		Log.info( "RayoComponent finishCallRecord " + cp.getStartTimestamp());

		if (cp.getStartTimestamp() > 0)
		{
			cp.setEndTimestamp(System.currentTimeMillis());
			Config.updateCallRecord(cp.getStartTimestamp(), (int)((cp.getEndTimestamp() - cp.getStartTimestamp()) / 1000));

			try {
				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(cp.getConferenceId());
				conferenceManager.recordConference(false, null, null);

				String target = cp.getCallOwner();
				JID destination = getJID(conferenceManager.getCallId());

				if (destination == null)
				{
					destination = new JID(conferenceManager.getCallId() + "@" + getDomain());
				}

				if (target == null)
				{
					if (cp.getHandset() != null)
					{
						target = cp.getHandset().getCallOwner();
					}
				}

				if (target != null)
				{
					try {
						sendMessage(new JID(target), destination, "Call ended", null);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			} catch (Exception e) {}

			cp.setStartTimestamp(0);
		}
	}


    private void sendMessage(JID from, JID to, String body, String fileName)
    {
		Log.info( "RayoComponent sendMessage " + from + " " + to + " " + body + " " + fileName);

		int port = HttpBindManager.getInstance().getHttpBindUnsecurePort();
        Message packet = new Message();
        packet.setTo(to);
        packet.setFrom(from);
        packet.setType(Message.Type.chat);
        packet.setThread("http://" + getDomain() + ":" + port + "/rayo/recordings/" + fileName);
        packet.setBody(body);
        sendPacket(packet);
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

				ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(conferenceEvent.getConferenceId());

				String groupName = conferenceManager.getGroupName();
				String callId = conferenceManager.getCallId();

				if (callId == null) callId = conferenceEvent.getConferenceId();	// special case of SIP incoming

				CallHandler farParty = CallHandler.findCall(callId);
				CallHandler callHandler = CallHandler.findCall(conferenceEvent.getCallId());

				if (callHandler != null)
				{
					Log.info("RayoComponent notifyConferenceMonitors found call handler " + callHandler + " " + farParty);

					CallParticipant callParticipant = callHandler.getCallParticipant();

					if (callParticipant != null )
					{
						ArrayList memberList = conferenceManager.getMemberList();

						if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT) && callId.equals(conferenceEvent.getCallId()))
						{
							if (farParty != null && farParty.getCallParticipant().isHeld() == false)		// far party left
							{
								synchronized (memberList)
								{
									for (int i = 0; i < memberList.size(); i++)
									{
										CallHandler participant = ((ConferenceMember) memberList.get(i)).getCallHandler();
										participant.cancelRequest("Far Party has left");
									}
								}
							}
						}

						int memberCount = memberList.size();

						Log.info("RayoComponent notifyConferenceMonitors found owner " + callParticipant.getCallOwner() + " " + memberCount);

						if (groupName == null)
						{
							routeJoinEvent(callParticipant.getCallOwner(), callParticipant, conferenceEvent, memberCount, groupName, callId, farParty);

						} else {

							Group group = GroupManager.getInstance().getGroup(groupName);

							for (JID memberJID : group.getMembers())
							{
								Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

								for (ClientSession session : sessions)
								{
									routeJoinEvent(session.getAddress().toString(), callParticipant, conferenceEvent, memberCount, groupName, callId, farParty);
								}
							}
						}
					}
				}

				if (conferenceManager.getMemberList().size() == 0) {
					conferenceManager.recordConference(false, null, null);
					conferenceManager.endConference(conferenceEvent.getConferenceId());
				}
			}

		} catch (Exception e) {

			Log.error( "RayoComponent Error in notifyConferenceMonitors " + e);
			e.printStackTrace();
		}
    }

    private void routeJoinEvent(String callee, CallParticipant callParticipant, ConferenceEvent conferenceEvent, int memberCount, String groupName, String callId, CallHandler farParty)
    {
		Log.info( "RayoComponent routeJoinEvent " + callee + " " + callId + " " + groupName + " " + memberCount + " " + farParty);

		if (callee == null) return;

		Presence presence = new Presence();
		presence.setFrom(callId + "@rayo." + getDomain());
		presence.setTo(callee);

		Map<String, String> headers = callParticipant.getHeaders();

		headers.put("call_owner", callParticipant.getCallOwner());
		headers.put("call_action", conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT) ? "leave" : "join");
		headers.put("call_protocol", callParticipant.getProtocol());

		headers.put("mixer_name", conferenceEvent.getConferenceId());
		headers.put("group_name", groupName);

		if (memberCount > 2)	// conferencing state
		{
			Log.info( "RayoComponent routeJoinEvent conferenced state " + memberCount);

			if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))
			{
				UnjoinedEvent event = new UnjoinedEvent(null, conferenceEvent.getConferenceId(), JoinDestinationType.MIXER);
				presence.getElement().add(rayoProvider.toXML(event));

			} else {
				JoinedEvent event = new JoinedEvent(null, conferenceEvent.getConferenceId(), JoinDestinationType.MIXER);
				presence.getElement().add(rayoProvider.toXML(event));
			}

			sendPacket(presence);

		} else {

			if (memberCount == 2)	// caller with callee only
			{
				Log.info( "RayoComponent routeJoinEvent answered state " + callId + " " + conferenceEvent.getCallId());

				if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))	// previously conferenced
				{
					Log.info( "RayoComponent routeJoinEvent someone left ");

					if (callId.equals(conferenceEvent.getCallId()) == false) // handset leaving
					{
						Log.info( "RayoComponent routeJoinEvent handset leaving ");

						presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
						sendPacket(presence);

					} else {
						Log.info( "RayoComponent routeJoinEvent far party leaving ");
					}

				} else {

					Log.info( "RayoComponent routeJoinEvent someone joined ");

					if (callId.equals(conferenceEvent.getCallId())) // far party joined
					{
						Log.info( "RayoComponent routeJoinEvent far party joined ");

						presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
						sendPacket(presence);

					} else {	// handset joined

						Log.info( "RayoComponent routeJoinEvent handset joined ");

						if (farParty != null)
						{
							CallParticipant fp = farParty.getCallParticipant();

							if (fp.isHeld())
							{
								Log.info( "RayoComponent routeJoinEvent on hold ");

								fp.setHeld(false);
								presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
								sendPacket(presence);

							} else {
								Log.info( "RayoComponent routeJoinEvent not held " + fp.getProtocol() + " " + fp);

								if ("WebRtc".equals(fp.getProtocol()) == false)
								{
									Log.info( "RayoComponent routeJoinEvent handset joing sip call");

									presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
									sendPacket(presence);
								}
							}
						}
					}
				}

			} else if (memberCount == 1) {		// callee or caller

				if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))
				{
					if (callId.equals(conferenceEvent.getCallId()) == false)	// handset leaving
					{
						if (farParty != null)
						{
							CallParticipant fp = farParty.getCallParticipant();

							if (fp.isHeld())
							{
								presence.getElement().add(handsetProvider.toXML(new OnHoldEvent()));
								sendPacket(presence);
							}
						}

					} else {				// far party leaving

						if (callParticipant.isHeld())
						{
							presence.getElement().add(handsetProvider.toXML(new OnHoldEvent()));
							sendPacket(presence);

						} else {
							finishCallRecord(callParticipant);

							presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("HANGUP"), headers)));
							sendPacket(presence);
						}
					}
				}

			} else {	// nobody left, call ended, signal last handset

				presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("HANGUP"), headers)));
				sendPacket(presence);

				//finishCallRecord(callParticipant);
			}
		}
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
		cp.setMediaPreference("PCMU/8000/1");
		cp.setConferenceId(callId);


		ConferenceManager conferenceManager = ConferenceManager.getConference(callId, cp.getMediaPreference(), cp.getToPhoneNumber(), false);
		conferenceManager.setCallId(callId);

		Map<String, String> headers = cp.getHeaders();
		headers.put("mixer_name", callId);
		headers.put("call_protocol", "SIP");
		headers.put("group_name", cp.getToPhoneNumber());

		if (foundUser != null)		// send this call to specific user
		{
			cp.setCallOwner(foundUser.toString());
			routeSIPCall(foundUser, cp, callId, headers);
			return true;
		}

        try {
            Group group = GroupManager.getInstance().getGroup(cp.getToPhoneNumber());

			conferenceManager.setGroupName(cp.getToPhoneNumber());

			for (JID memberJID : group.getMembers())
			{
				Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

				for (ClientSession session : sessions)
				{
					routeSIPCall(session.getAddress(), cp, callId, headers);
				}
			}

			return true;

        } catch (GroupNotFoundException e) {
            // Group not found

			if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").hasChatRoom(cp.getToPhoneNumber())) {

				MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(cp.getToPhoneNumber());

				if (room != null)
				{
					for (MUCRole role : room.getOccupants())
					{
						routeSIPCall(role.getUserAddress(), cp, callId, headers);
					}
				}
            	return true;

			} else {
            	return false;
			}
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

			OfferEvent offer = new OfferEvent(null);

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

	private IQHandler onHookIQHandler = null;
	private IQHandler offHookIQHandler = null;
	private IQHandler dialIQHandler = null;
	private IQHandler hangupIQHandler = null;


	private void createIQHandlers()
	{
		XMPPServer server = XMPPServer.getInstance();

		onHookIQHandler = new OnHookIQHandler(); server.getIQRouter().addHandler(onHookIQHandler);
		offHookIQHandler = new OffHookIQHandler(); server.getIQRouter().addHandler(offHookIQHandler);
		dialIQHandler = new DialIQHandler(); server.getIQRouter().addHandler(dialIQHandler);
		hangupIQHandler = new HangupIQHandler(); server.getIQRouter().addHandler(hangupIQHandler);
	}

	private void destroyIQHandlers()
	{
		XMPPServer server = XMPPServer.getInstance();

		if (onHookIQHandler != null) {server.getIQRouter().removeHandler(onHookIQHandler); onHookIQHandler = null;}
		if (offHookIQHandler != null) {server.getIQRouter().removeHandler(offHookIQHandler); offHookIQHandler = null;}
		if (dialIQHandler != null) {server.getIQRouter().removeHandler(dialIQHandler); dialIQHandler = null;}
		if (hangupIQHandler != null) {server.getIQRouter().removeHandler(hangupIQHandler); hangupIQHandler = null;}
	}

    private class OnHookIQHandler extends IQHandler
    {
        public OnHookIQHandler() { super("Rayo: XEP 0327 - Onhook");}

        @Override public IQ handleIQ(IQ iq)  {try {return handleIQGet(iq);} catch(Exception e) {return null;} }
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("onhook", RAYO_HANDSET); }
    }

    private class OffHookIQHandler extends IQHandler
    {
        public OffHookIQHandler() { super("Rayo: XEP 0327 - Offhook");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("offhook", RAYO_HANDSET); }
    }

    private class DialIQHandler extends IQHandler
    {
        public DialIQHandler() { super("Rayo: XEP 0327 - Dial");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("dial", RAYO_CORE); }
    }

    private class HangupIQHandler extends IQHandler
    {
        public HangupIQHandler() { super("Rayo: XEP 0327 - Hangup");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("hangup", RAYO_CORE); }
    }
}
