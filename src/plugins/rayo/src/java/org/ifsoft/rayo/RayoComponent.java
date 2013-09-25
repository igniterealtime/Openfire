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
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.component.AbstractComponent;
import org.xmpp.jnodes.*;
import org.xmpp.jnodes.nio.LocalIPResolver;
import org.xmpp.packet.*;

import java.util.*;
import java.text.ParseException;

import com.rayo.core.*;
import com.rayo.core.verb.*;
import com.rayo.core.validation.*;
import com.rayo.core.xml.providers.*;

import com.sun.voip.server.*;
import com.sun.voip.*;

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

        if ("urn:xmpp:rayo:handset:1".equals(namespace)) {
        	IQ reply = null;

            Object object = handsetProvider.fromXML(element);

			if (object instanceof Handset) {
				reply = handleHandset((Handset) object, iq);

			} else if (object instanceof OnHookCommand) {
				reply = handleOnOffHookCommand((Handset) object, true, iq);

			} else if (object instanceof OffHookCommand) {
				reply = handleOnOffHookCommand((Handset) object, false, iq);
			}
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
		}

        if ("urn:xmpp:rayo:1".equals(namespace)) {
        	IQ reply = null;

            Object object = rayoProvider.fromXML(element);

			if (object instanceof ConnectCommand) {

			} else if (object instanceof AcceptCommand) {

			} else if (object instanceof HoldCommand) {

			} else if (object instanceof UnholdCommand) {

			} else if (object instanceof MuteCommand) {

			} else if (object instanceof UnmuteCommand) {

			} else if (object instanceof JoinCommand) {
				reply = handleJoinCommand((JoinCommand) object, iq);

			} else if (object instanceof UnjoinCommand) {
				reply = handleUnjoinCommand((UnjoinCommand) object, iq);


			} else if (object instanceof AnswerCommand) {

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
    }


	private IQ handlePauseCommand(boolean flag, IQ iq)
	{
		Log.info("RayoComponent handlePauseCommand " + iq.getFrom());

		final IQ reply = IQ.createResultIQ(iq);
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

	private IQ handleSay(Say command, IQ iq)
	{
		Log.info("RayoComponent handleSay " + iq.getFrom());

		final IQ reply = IQ.createResultIQ(iq);
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

	private IQ handleHangupCommand(IQ iq)
	{
		Log.info("RayoComponent handleHangupCommand " + iq.getFrom());

		final IQ reply = IQ.createResultIQ(iq);

		try {
			CallHandler callHandler = CallHandler.findCall(iq.getTo().getNode());
			CallHandler.hangup(iq.getTo().getNode(), "User requested call termination");

		} catch (NoSuchElementException e) {
			reply.setError(PacketError.Condition.item_not_found);
		}

		return reply;
	}

	private IQ handleDtmfCommand(DtmfCommand command, IQ iq)
	{
		Log.info("RayoComponent handleDtmfCommand " + iq.getFrom());

		final IQ reply = IQ.createResultIQ(iq);

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

        final IQ reply = IQ.createResultIQ(iq);

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

        final IQ reply = IQ.createResultIQ(iq);

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

        final IQ reply = IQ.createResultIQ(iq);

		Map<String, String> headers = command.getHeaders();
		String from = command.getFrom().toString();
		String to = command.getTo().toString();

		boolean toPhone = to.indexOf("sip:") == 0 || to.indexOf("tel:") == 0;
		boolean toXmpp = to.indexOf("xmpp:") == 0;

		String codec = headers.get("caller-id");
		String mixer = "rayo-mixer-" + System.currentTimeMillis();

		String callerId = headers.get("caller-id");
		String calledId = headers.get("called-id");
		String callerName = headers.get("caller-name");
		String calledName = headers.get("called-name");

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

		if (toPhone)
		{
			cp.setMediaPreference("PCMU/8000/1");
			cp.setProtocol("SIP");
			cp.setCallId(calledId);
			cp.setPhoneNumber(to.substring(4));
			cp.setName(calledName == null ? cp.getPhoneNumber() : calledName);

			mixer = mixer == null ? cp.getPhoneNumber() : mixer;
			cp.setConferenceId(mixer);

			doPhoneAndPcCall(iq.getFrom(), cp, reply, mixer);

		} else if (toXmpp){

		} else {
			reply.setError(PacketError.Condition.feature_not_implemented);
		}

		return reply;
	}

	private IQ handleBridgedDialCommand(DialCommand command, IQ iq)
	{
		Log.info("RayoComponent handleBridgedDialCommand " + iq.getFrom());

        final IQ reply = IQ.createResultIQ(iq);

		Map<String, String> headers = command.getHeaders();
		String from = command.getFrom().toString();
		String to = command.getTo().toString();

		boolean fromPhone 	= from.indexOf("sip:") == 0 || from.indexOf("tel:") == 0;
		boolean toPhone 	= to.indexOf("sip:") == 0 || to.indexOf("tel:") == 0;
		boolean toHandset 	= to.indexOf("handset:") == 0;
		boolean fromHandset = from.indexOf("handset:") == 0;

		String mixer = null;
		String callerId = headers.get("caller-id");
		String calledId = headers.get("called-id");
		String callerName = headers.get("caller-name");
		String calledName = headers.get("called-name");

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
				doPhoneAndPcCall(new JID(to.substring(8)), cp, reply, mixer);

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
				doPhoneAndPcCall(new JID(from.substring(8)), cp, reply, mixer);

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

	private void doPhoneAndPcCall(JID handsetJid, CallParticipant cp, IQ reply, String mixer)
	{
		Log.info("RayoComponent doPhoneAndPcCall " + handsetJid + " " + mixer);

		RelayChannel channel = plugin.getRelayChannel(handsetJid.getNode());

		if (channel != null)
		{
			try {
				setMixer(mixer, channel, reply);
				IncomingCallHandler.transferCall(channel.getAttachment(), mixer);

			} catch (Exception e) {
				reply.setError(PacketError.Condition.internal_server_error);
			}

			OutgoingCallHandler outgoingCallHandler = new OutgoingCallHandler(this, cp);
			outgoingCallHandler.start();

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}
	}

    private void setMixer(String mixer, RelayChannel channel, IQ reply)
    {
		ConferenceManager conferenceManager = ConferenceManager.getConference(	mixer,
																				channel.getMediaPreference(),
																				channel.getFrom().getNode(), false);
		try {
			if (conferenceManager.getMemberList().size() == 0) {
				conferenceManager.recordConference(true, mixer + "-" + System.currentTimeMillis() + ".au", "au");
			}

		} catch (ParseException e1) {
			reply.setError(PacketError.Condition.internal_server_error);
		}
    }


	private IQ handleHandset(Handset handset, IQ iq)
	{
		Log.info("RayoComponent handleHandset " + iq.getFrom());

        final IQ reply = IQ.createResultIQ(iq);
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

		} else {
			reply.setError(PacketError.Condition.internal_server_error);
		}

		return reply;
	}

	private IQ handleOnOffHookCommand(Handset handset, boolean flag, IQ iq)
	{
		Log.info("RayoComponent handleOnOffHookCommand " + flag);

		final IQ reply = IQ.createResultIQ(iq);

		RelayChannel channel = plugin.getRelayChannel(iq.getTo().getNode());

		if (channel != null)
		{
			handleOnOffHook(flag, channel, handset);

		} else {
			reply.setError(PacketError.Condition.item_not_found);
		}
		return reply;
	}


    private void handleOnOffHook(boolean flag, RelayChannel channel, Handset handset)
    {
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
				String mediaPreference = "PCMU/8000/1";

				if (handset.codec == null || "OPUS".equals(handset.codec))
					mediaPreference = "PCM/48000/2";

				CallParticipant cp = new CallParticipant();
				cp.setCallId(channel.getAttachment());
				cp.setProtocol("WebRtc");
				cp.setConferenceId(defaultIncomingConferenceId);
				cp.setMediaPreference(mediaPreference);
				cp.setRelayChannel(channel);
				cp.setDisplayName(channel.getAttachment());
				cp.setVoiceDetection(true);
				cp.setCallOwner(channel.getFrom().toString());

				callHandler = new OutgoingCallHandler(this, cp);
				callHandler.start();
			}

        } catch (Exception e) {
            e.printStackTrace();
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
		JID from = new JID(callEvent.getInfo());

		if (from != null)
		{
			String myEvent = com.sun.voip.CallEvent.getEventString(callEvent.getEvent());
			String callState = callEvent.getCallState().toString();

			Map<String, String> headers = new HashMap<String, String>();
			headers.put("info", callEvent.getInfo() == null ? "" : callEvent.getInfo());
			headers.put("callId", callEvent.getCallId() == null ? "" : callEvent.getCallId());
			headers.put("mixer", callEvent.getConferenceId() == null ? "" : callEvent.getConferenceId());
			headers.put("callInfo", callEvent.getCallInfo() == null ? "" : callEvent.getCallInfo());

			Presence presence = new Presence();
			presence.setFrom("rayo." + getDomain());
			presence.setTo(from);

			if ("001 STATE CHANGED".equals(myEvent)) {

				if ("110 ANSWERED".equals(callState)) {
					presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, headers)));
					sendPacket(presence);

				} else if ("200 ESTABLISHED".equals(callState)) {
					presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
					sendPacket(presence);

				} else if ("299 ENDED".equals(callState)) {
					presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("HANGUP"), headers)));
					sendPacket(presence);
				}
			} else if ("250 STARTED SPEAKING".equals(myEvent)) {
				StartedSpeakingEvent speaker = new StartedSpeakingEvent();
				speaker.setSpeakerId(headers.get("callId"));
				presence.getElement().add(rayoProvider.toXML(speaker));
				sendPacket(presence);

			} else if ("259 STOPPED SPEAKING".equals(myEvent)) {
				StoppedSpeakingEvent speaker = new StoppedSpeakingEvent();
				speaker.setSpeakerId(headers.get("callId"));
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

						Presence presence = new Presence();
						presence.setFrom(conferenceEvent.getCallId() + "@rayo." + getDomain());
						presence.setTo(callParticipant.getCallOwner());

						if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))
						{
							UnjoinedEvent event = new UnjoinedEvent(null, conferenceEvent.getConferenceId(), JoinDestinationType.MIXER);
							presence.getElement().add(rayoProvider.toXML(event));
						} else {
							JoinedEvent event = new JoinedEvent(null, conferenceEvent.getConferenceId(), JoinDestinationType.MIXER);
							presence.getElement().add(rayoProvider.toXML(event));
						}

						sendPacket(presence);
					}
				}

				try {
					ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(conferenceEvent.getConferenceId());

					if (conferenceManager.getMemberList().size() == 0) {
						conferenceManager.recordConference(false, null, null);
						conferenceManager.endConference(conferenceEvent.getConferenceId());
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		} catch (Exception e) {

			Log.error( "RayoComponent Error in notifyConferenceMonitors " + e);
			e.printStackTrace();
		}
    }
}
