/**
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
import java.util.concurrent.*;
import java.text.ParseException;
import java.net.*;

import com.rayo.core.*;
import com.rayo.core.verb.*;
import com.rayo.core.validation.*;
import com.rayo.core.xml.providers.*;

import com.sun.voip.server.*;
import com.sun.voip.*;

import org.voicebridge.*;

import com.jcumulus.server.rtmfp.ServerPipelineFactory;
import com.jcumulus.server.rtmfp.Sessions;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;


public class RayoComponent 	extends 	AbstractComponent
                            implements 	TreatmentDoneListener,
                                        CallEventListener {

    private static final Logger Log = LoggerFactory.getLogger(RayoComponent.class);

    private static final String RAYO_CORE 	= "urn:xmpp:rayo:1";
    private static final String RAYO_RECORD = "urn:xmpp:rayo:record:1";
    private static final String RAYO_SAY 	= "urn:xmpp:tropo:say:1";
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
    private RecordProvider recordProvider = null;
    private SayProvider sayProvider = null;
    private HandsetProvider handsetProvider = null;

    private static ConnectionlessBootstrap bootstrap = null;
    public static Channel channel = null;
    private static Sessions sessions;


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

        server.getIQDiscoInfoHandler().addServerFeature(RAYO_RECORD);
        recordProvider = new RecordProvider();
        recordProvider.setValidator(new Validator());

        server.getIQDiscoInfoHandler().addServerFeature(RAYO_SAY);
        sayProvider = new SayProvider();
        sayProvider.setValidator(new Validator());

        server.getIQDiscoInfoHandler().addServerFeature(RAYO_HANDSET);
        handsetProvider = new HandsetProvider();
        handsetProvider.setValidator(new Validator());

        createIQHandlers();

        try{
            Log.info("Starting jCumulus.....");

            sessions = new Sessions();
            ExecutorService executorservice = Executors.newCachedThreadPool();
            NioDatagramChannelFactory niodatagramchannelfactory = new NioDatagramChannelFactory(executorservice);
            bootstrap = new ConnectionlessBootstrap(niodatagramchannelfactory);
            OrderedMemoryAwareThreadPoolExecutor orderedmemoryawarethreadpoolexecutor = new OrderedMemoryAwareThreadPoolExecutor(10, 0x100000L, 0x40000000L, 100L, TimeUnit.MILLISECONDS, Executors.defaultThreadFactory());

            bootstrap.setPipelineFactory(new ServerPipelineFactory(sessions, orderedmemoryawarethreadpoolexecutor));
            bootstrap.setOption("reuseAddress", Boolean.valueOf(true));
            bootstrap.setOption("sendBufferSize", Integer.valueOf(1215));
            bootstrap.setOption("receiveBufferSize", Integer.valueOf(2048));
            bootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(2048));

            InetSocketAddress inetsocketaddress = new InetSocketAddress(JiveGlobals.getIntProperty("voicebridge.rtmfp.port", 1935));

            Log.info("Listening on " + inetsocketaddress.getPort() + " port");

            channel = bootstrap.bind(inetsocketaddress);

        } catch (Exception e) {
            Log.error("jCumulus startup failure");
            e.printStackTrace();
        }
    }

    public void doStop()
    {
        Log.info("RayoComponent shutdown ");

        XMPPServer server = XMPPServer.getInstance();

        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_CORE);
        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_RECORD);
        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_SAY);
        server.getIQDiscoInfoHandler().removeServerFeature(RAYO_HANDSET);

        destroyIQHandlers();

        Log.info("jCumulus stopping...");

        channel.close();
        bootstrap.releaseExternalResources();
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
    synchronized protected IQ handleIQGet(IQ iq) throws Exception {

        Log.info("RayoComponent handleIQGet \n" + iq.toString());

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

                } else if (object instanceof CreateSpeakerCommand) {
                    reply = handleCreateSpeakerCommand(object, iq);

                } else if (object instanceof DestroySpeakerCommand) {
                    reply = handleDestroySpeakerCommand(object, iq);

                } else if (object instanceof PutOnSpeakerCommand) {
                    reply = handleOnOffSpeakerCommand(object, iq, true);

                } else if (object instanceof TakeOffSpeakerCommand) {
                    reply = handleOnOffSpeakerCommand(object, iq, false);

                } else if (object instanceof TalkCommand) {
                    reply = handleOnOffTalkCommand(object, iq, false);

                } else if (object instanceof UntalkCommand) {
                    reply = handleOnOffTalkCommand(object, iq, true);
                }
                return reply;
            }

            if (RAYO_RECORD.equals(namespace)) {
                IQ reply = null;

                Object object = recordProvider.fromXML(element);

                if (object instanceof Record) {
                    reply = handleRecord((Record) object, iq);

                } else if (object instanceof PauseCommand) {
                    reply = handlePauseRecordCommand(true, iq);

                } else if (object instanceof ResumeCommand) {
                    reply = handlePauseRecordCommand(false, iq);
                }
                return reply;
            }

            if (RAYO_SAY.equals(namespace)) {
                IQ reply = null;

                Object object = sayProvider.fromXML(element);

                if (object instanceof Say) {
                    reply = handleSay((Say) object, iq);

                } else if (object instanceof PauseCommand) {
                    reply = handlePauseSayCommand(true, iq);

                } else if (object instanceof ResumeCommand) {
                    reply = handlePauseSayCommand(false, iq);
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

                    reply = handleDialCommand((DialCommand) dial, iq, true);

                } else if (object instanceof DialCommand) {
                    reply = handleDialCommand((DialCommand) object, iq, false);

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
                            presence.setFrom(callId + "@" + getDomain());
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
                            presence.setFrom(callId + "@" + getDomain());
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
                    childElement.addAttribute(URI, "xmpp:" + channel.getAttachment() + "@" + getDomain() + "/webrtc");

                    Log.debug("Created WebRTC handset channel {}:{}, {}:{}, {}:{}", new Object[]{HOST, LocalIPResolver.getLocalIP(), LOCAL_PORT, Integer.toString(channel.getPortA()), REMOTE_PORT, Integer.toString(channel.getPortB())});

                    handleOnOffHook(handsetId, object, channel, reply);

                } else {
                    reply.setError(PacketError.Condition.internal_server_error);
                }

            } else {					// SIP handset

                final Element childElement = reply.setChildElement("ref", RAYO_CORE);

                childElement.addAttribute(ID, handsetId);
                childElement.addAttribute(URI, handset.sipuri);

                Log.info("Created SIP handset channel " + handset.sipuri);

                handleOnOffHook(handsetId, object, null, reply);
            }
        }

        return reply;
    }


    private IQ handleOnOffTalkCommand(Object object, IQ iq, boolean mute)
    {
        Log.info("RayoComponent handleOnOffTalkCommand");

        IQ reply = IQ.createResultIQ(iq);
        String callId = iq.getTo().getNode();
        String speakerId = JID.escapeNode(iq.getFrom().toBareJID() + "/speaker");
        String bridgeCallId = "call-" + callId + speakerId;
        String bridgeSpeakerId = "spkr-" + speakerId + callId;

        CallHandler callHandler = CallHandler.findCall(bridgeCallId);
        CallHandler callHandler2 = CallHandler.findCall(bridgeSpeakerId);

        if (callHandler != null)
        {
            CallHandler spkrHandler = CallHandler.findCall(speakerId);

            if (spkrHandler != null)
            {
                MemberReceiver memberReceiver = spkrHandler.getMemberReceiver();
                MemberSender memberSender = callHandler.getMemberSender();

                if (!mute)
                {
                    memberReceiver.setChannel(new SpeakerChannel(callHandler.getMemberReceiver()));
                } else {
                    memberReceiver.setChannel(null);
                }

                CallParticipant cp = spkrHandler.getCallParticipant();
                cp.setMuted(mute);	// mic on/off
            }

        } else {
            reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }



    private IQ handleOnOffSpeakerCommand(Object object, IQ iq, boolean flag)
    {
        Log.info("RayoComponent handleOnOffSpeakerCommand");

        IQ reply = IQ.createResultIQ(iq);
        String callId = iq.getTo().getNode();
        String speakerId = JID.escapeNode(iq.getFrom().toBareJID() + "/speaker");

        CallHandler callHandler = CallHandler.findCall(callId);
        CallHandler spkrHandler = CallHandler.findCall(speakerId);

        if (callHandler != null)
        {
            if (spkrHandler != null)
            {
                Log.info("RayoComponent handleOnOffSpeakerCommand, found call " + callId);

                bridgeMixers(spkrHandler, callHandler, flag, iq.getFrom());
            }

        } else {	// not call, we use callId for mixer name

            ConferenceManager cm = ConferenceManager.getConference(callId, "PCMU/8000/1", callId, false);

            if (spkrHandler != null)
            {
                Log.info("RayoComponent handleOnOffSpeakerCommand, found conference " + callId);

                CallParticipant sp = spkrHandler.getCallParticipant();
                String spkrMixer = sp.getConferenceId();
                String callMixer = callId;

                bridgeMixers(spkrMixer, speakerId, callMixer, callId, flag, iq.getFrom());
            }
        }

        return reply;
    }

    private void bridgeMixers(CallHandler spkrHandler, CallHandler callHandler, boolean flag, JID from)
    {
        Log.info("RayoComponent bridgeMixers");

        CallParticipant sp = spkrHandler.getCallParticipant();
        CallParticipant cp = callHandler.getCallParticipant();

        String callMixer = cp.getConferenceId();
        String spkrMixer = sp.getConferenceId();

        bridgeMixers(spkrMixer, sp.getCallId(), callMixer, cp.getCallId(), flag, from);
    }

    synchronized private void bridgeMixers(String spkrMixer, String speakerId, String callMixer, String callId, boolean flag, JID from)
    {
        Log.info("RayoComponent bridgeMixers " + speakerId + " " + callMixer + " " + callId  + " " + flag);

        String bridgeSpeakerId = "spkr-" + speakerId + callId;
        String bridgeCallId = "call-" + callId + speakerId;

        CallHandler bridge1 = CallHandler.findCall(bridgeSpeakerId);
        if (bridge1 != null) bridge1.cancelRequest("Speaker terminated");

        CallHandler bridge2 = CallHandler.findCall(bridgeCallId);
        if (bridge2 != null) bridge2.cancelRequest("Speaker terminated");

        if (flag)
        {
            synchronized (this)
            {
                CallParticipant bp1 = new CallParticipant();
                bp1.setCallId(bridgeSpeakerId);
                bp1.setConferenceId(spkrMixer);
                bp1.setPhoneNumber(speakerId);
                bp1.setDisplayName("SPKR");
                bp1.setVoiceDetection(false);
                bp1.setProtocol("Speaker");
                bridge1 = new OutgoingCallHandler(this, bp1);

                CallParticipant bp2 = new CallParticipant();
                bp2.setCallId(bridgeCallId);
                bp2.setConferenceId(callMixer);
                bp2.setPhoneNumber(speakerId);
                bp2.setDisplayName("CALL");
                bp2.setVoiceDetection(true);
                bp2.setCallOwner(from.toString());
                bp2.setProtocol("Speaker");
                bp2.setOtherCall(bridge1);
                bridge2 = new OutgoingCallHandler(this, bp2);

                bridge1.start();

                try {
                    Thread.sleep(3000);
                } catch (Exception e) {}

                bridge2.start();
            }
        }

    }

    private IQ handleDestroySpeakerCommand(Object object, IQ iq)
    {
        Log.info("RayoComponent handleDestroySpeakerCommand");

        IQ reply = IQ.createResultIQ(iq);
        DestroySpeakerCommand speaker = (DestroySpeakerCommand) object;

        try {
            String speakerId = JID.escapeNode(iq.getFrom().toBareJID() + "/speaker");

            CallHandler handler = CallHandler.findCall(speakerId);

            if (handler != null)
            {
                killSpeaker(handler);
            }

        } catch (Exception e) {
           e.printStackTrace();
           reply.setError(PacketError.Condition.not_allowed);
        }
        return reply;
    }

    private void killSpeaker(CallHandler handler)
    {
        Log.info("RayoComponent killSpeaker");

        try {

            handler.cancelRequest("Speaker is destroyed");

            CallParticipant cp = handler.getCallParticipant();
            String confId = cp.getConferenceId();
            handler = null;

            ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(confId);

            ArrayList memberList = conferenceManager.getMemberList();

            synchronized (memberList)
            {
                for (int i = 0; i < memberList.size(); i++)
                {
                    CallHandler participant = ((ConferenceMember) memberList.get(i)).getCallHandler();

                    if (participant != null)
                    {
                        participant.cancelRequest("Speaker is destroyed");
                        participant = null;
                    }
                }
            }


        } catch (Exception e) {
           e.printStackTrace();
        }
    }

    private IQ handleCreateSpeakerCommand(Object object, IQ iq)
    {
        Log.info("RayoComponent handleCreateSpeakerCommand");

        IQ reply = IQ.createResultIQ(iq);
        CreateSpeakerCommand speaker = (CreateSpeakerCommand) object;

        try {
            String speakerId = JID.escapeNode(iq.getFrom().toBareJID() + "/speaker");
            String label = iq.getFrom().getNode();

            CallHandler handler = CallHandler.findCall(speakerId);

            if (handler != null)
            {
                //killSpeaker(handler);
                final Element childElement = reply.setChildElement("ref", RAYO_CORE);
                childElement.addAttribute(ID, speakerId);
                childElement.addAttribute(URI, "xmpp:" + iq.getFrom().toBareJID() + "/speaker");
                return reply;
            }

            String mediaPreference = "PCMU/8000/1";

            if (speaker.codec == null || "OPUS".equals(speaker.codec))
                mediaPreference = "PCM/48000/2";

            CallParticipant cp = new CallParticipant();
            cp.setCallId(speakerId);
            cp.setConferenceId(speaker.mixer);
            cp.setDisplayName("rayo-speaker-" + System.currentTimeMillis());
            cp.setName(cp.getDisplayName());
            cp.setVoiceDetection(true);
            cp.setCallOwner(iq.getFrom().toString());
            cp.setPhoneNumber(speaker.sipuri);
            cp.setAutoAnswer(true);
            cp.setProtocol("SIP");
            cp.setMuted(true);	// set mic off

            ConferenceManager cm = ConferenceManager.getConference(speaker.mixer, mediaPreference, label, false);

            OutgoingCallHandler callHandler = new OutgoingCallHandler(this, cp);
            callHandler.start();

            final Element childElement = reply.setChildElement("ref", RAYO_CORE);
            childElement.addAttribute(ID, speakerId);
            childElement.addAttribute(URI, "xmpp:" + iq.getFrom().toBareJID() + "/speaker");

        } catch (Exception e) {
           e.printStackTrace();
           reply.setError(PacketError.Condition.not_allowed);
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
                        if (handset.sipuri.indexOf("sip:") == 0)
                        {
                            cp.setPhoneNumber(handset.sipuri);
                            cp.setAutoAnswer(true);
                            cp.setProtocol("SIP");

                        } else if (handset.sipuri.indexOf("rtmfp:") == 0) {

                            String[] tokens = handset.sipuri.split(":");

                            if (tokens.length == 3)
                            {
                                cp.setProtocol("Rtmfp");
                                cp.setRtmfpSendStream(tokens[1]);
                                cp.setRtmfpRecieveStream(tokens[2]);
                                cp.setAutoAnswer(true);

                            } else {
                                reply.setError(PacketError.Condition.not_allowed);
                                return;
                            }

                        } else {
                            reply.setError(PacketError.Condition.not_allowed);
                            return;
                        }

                    } else {
                        cp.setMediaPreference(mediaPreference);
                        cp.setChannel(channel);
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

    private IQ handleRecord(Record command, IQ iq)
    {
        Log.info("RayoComponent handleRecord " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);
        final String callId = JID.escapeNode(iq.getFrom().toString());
        final String uri = command.getTo().toString();

        CallHandler callHandler = CallHandler.findCall(callId);

        if (callHandler != null)
        {
            try {
                final String fileName = uri.substring(5);			// expecting file: prefix

                callHandler.getCallParticipant().setRecordDirectory(System.getProperty("com.sun.voip.server.Bridge.soundsDirectory", "."));
                callHandler.getMemberReceiver().setRecordFromMember(true, fileName, "au");

                final Element childElement = reply.setChildElement("ref", RAYO_CORE);
                childElement.addAttribute(ID, fileName);
                childElement.addAttribute(URI, (String) uri);

            } catch (Exception e1) {
                e1.printStackTrace();
                reply.setError(PacketError.Condition.not_allowed);
            }
        } else {
            reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    private IQ handlePauseRecordCommand(boolean flag, IQ iq)
    {
        Log.info("RayoComponent handlePauseRecordCommand " + iq.getFrom() + " " + iq.getTo());

        IQ reply = IQ.createResultIQ(iq);
        final String callId = JID.escapeNode(iq.getFrom().toString());

        CallHandler callHandler = CallHandler.findCall(callId);

        if (callHandler != null)
        {
            try {
                CallParticipant cp = callHandler.getCallParticipant();
                String fileName = cp.getFromRecordingFile();
                cp.setRecordDirectory(System.getProperty("com.sun.voip.server.Bridge.soundsDirectory", "."));

                callHandler.getMemberReceiver().setRecordFromMember(flag, fileName, "au");

            } catch (Exception e1) {
                e1.printStackTrace();
                reply.setError(PacketError.Condition.not_allowed);
            }

        } else {

            reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    private IQ handleSay(Say command, IQ iq)
    {
        Log.info("RayoComponent handleSay " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);
        final String entityId = iq.getTo().getNode();
        final String treatmentId = command.getPrompt().getText();

        CallHandler callHandler = CallHandler.findCall(entityId);

        if (callHandler != null)
        {
            try {
                callHandler.playTreatmentToCall(treatmentId, this);

                final Element childElement = reply.setChildElement("ref", RAYO_CORE);
                childElement.addAttribute(ID, treatmentId);
                childElement.addAttribute(URI, (String) "xmpp:" + entityId + "@" + getDomain() + "/" + treatmentId);

            } catch (Exception e1) {
                e1.printStackTrace();
                reply.setError(PacketError.Condition.not_allowed);
            }

        } else {	// not call, lets try mixer

            try {
                ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(entityId);

                try {
                    conferenceManager.addTreatment(treatmentId);

                    final Element childElement = reply.setChildElement("ref", RAYO_CORE);
                    childElement.addAttribute(ID, treatmentId);
                    childElement.addAttribute(URI, (String) "xmpp:" + entityId + "@" + getDomain() + "/" + treatmentId);

                } catch (Exception e2) {
                    e2.printStackTrace();
                    reply.setError(PacketError.Condition.not_allowed);
                }

            } catch (ParseException e1) {
                reply.setError(PacketError.Condition.item_not_found);
            }
        }

        return reply;
    }

    private IQ handlePauseSayCommand(boolean flag, IQ iq)
    {
        Log.info("RayoComponent handlePauseSayCommand " + iq.getFrom() + " " + iq.getTo());

        IQ reply = IQ.createResultIQ(iq);
        final JID entityId = getJID(iq.getTo().getNode());

        if (entityId != null)
        {
            final String treatmentId = entityId.getResource();
            final String callId = entityId.getNode();

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler != null)
            {
                callHandler.getMember().pauseTreatment(treatmentId, flag);

            } else  {	// not call, lets try mixer

                try {
                    ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(callId);
                    conferenceManager.getWGManager().pauseConferenceTreatment(treatmentId, flag);

                } catch (ParseException e1) {

                    reply.setError(PacketError.Condition.item_not_found);
                }
            }

        } else {

            reply.setError(PacketError.Condition.item_not_found);
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
                setRingingState(presence, ConferenceManager.isTransferCall(mixer), headers);
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
                    callHandler = CallHandler.findCall(callId);
                    handsetHandler = CallHandler.findCall(JID.escapeNode(callerId));

                    if (handsetHandler != null)
                    {
                        CallParticipant hp = handsetHandler.getCallParticipant();

                        Presence presence1 = new Presence();												//to caller
                        presence1.setFrom(iq.getTo());
                        presence1.setTo(callerId);
                        setAnsweredState(presence1, ConferenceManager.isTransferCall(hp.getConferenceId()), headers);
                        sendPacket(presence1);
                    }

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

                        sendMessage(new JID(callerId), iq.getFrom(), "Call started", recording, "chat");

                    } else { // incoming SIP

                        Config.createCallRecord(destination, recording, "sip:" + cp.getPhoneNumber(), cp.getStartTimestamp(), 0, "received") ;

                        sendMessage(iq.getFrom(), new JID(cp.getCallId() + "@" + getDomain()), "Call started", recording, "chat");
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
            //reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    private JID getJID(String jid)
    {
        if (jid != null)
        {
            jid = JID.unescapeNode(jid);

            if (jid.indexOf("@") == -1 || jid.indexOf("/") == -1) return null;

            try {
                return new JID(jid);

            } catch (Exception e) {

                return null;
            }

        } else return null;
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
                ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(mixer);

                if (CallHandler.findCall("colibri-" + mixer) == null)	// other participant than colibri
                {
                    if (conferenceManager.getMemberList().size() == 1)	// handset already in call
                    {
                        String recording = mixer + "-" + System.currentTimeMillis() + ".au";
                        conferenceManager.recordConference(true, recording, "au");
                        sendMucMessage(mixer, recording, iq.getFrom(), "started voice recording");
                    }
                }

                sendMucMessage(mixer, null, iq.getFrom(), iq.getFrom().getNode() + " joined voice conversation");

            } catch (ParseException pe) {				// colibri joining as first participant

                try {
                    ConferenceManager conferenceManager = ConferenceManager.getConference(mixer, "PCM/48000/2", mixer, false);
                    String recording = mixer + "-" + System.currentTimeMillis() + ".au";
                    conferenceManager.recordConference(true, recording, "au");
                    sendMucMessage(mixer, recording, iq.getFrom(), "started voice recording");

                } catch (Exception e) {
                    reply.setError(PacketError.Condition.item_not_found);
                }

            } catch (Exception e) {
                reply.setError(PacketError.Condition.item_not_found);
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

        String mixer = null;

        if (command.getType() == JoinDestinationType.CALL) {
            // TODO join.getFrom()
        } else {
              mixer = command.getFrom();
        }

        if (mixer != null)
        {
            try {
                ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(mixer);

                if (conferenceManager.getMemberList().size() == 1)
                {
                    conferenceManager.recordConference(false, null, null);
                    sendMucMessage(mixer, null, iq.getFrom(), "stopped voice recording");
                }

                sendMucMessage(mixer, null, iq.getFrom(), iq.getFrom().getNode() + " left voice conversation");

            } catch (Exception e) {
                reply.setError(PacketError.Condition.item_not_found);
            }

        } else {
            reply.setError(PacketError.Condition.feature_not_implemented);
        }

        return reply;
    }

    private void attachVideobridge(String conferenceId, JID participant, String mediaPreference)
    {
        //if (XMPPServer.getInstance().getPluginManager().getPlugin("jitsivideobridge") != null)
        //{
            Log.info("attachVideobridge Found Jitsi Videobridge, attaching.." + conferenceId);

            if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").hasChatRoom(conferenceId)) {

                MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(conferenceId);

                if (room != null)
                {
                    for (MUCRole role : room.getOccupants())
                    {
                        if (participant.toBareJID().equals(role.getUserAddress().toBareJID()))
                        {
                            Log.info("attachVideobridge Found participant " + participant.toBareJID());

                            try {
                                CallParticipant vp = new CallParticipant();
                                vp.setCallId("colibri-" + conferenceId);
                                vp.setCallOwner(participant.toString());
                                vp.setProtocol("Videobridge");
                                vp.setPhoneNumber(participant.getNode());
                                vp.setMediaPreference(mediaPreference);
                                vp.setConferenceId(conferenceId);

                                OutgoingCallHandler videoBridgeHandler = new OutgoingCallHandler(null, vp);
                                videoBridgeHandler.start();

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;
                        }
                    }
                }
            }
        //}
    }

    private void detachVideobridge(String conferenceId)
    {
        try {
            Log.info("Jitsi Videobridge, detaching.." + conferenceId);

            CallHandler callHandler = CallHandler.findCall("colibri-" + conferenceId);

            if (callHandler != null)
            {
                CallHandler.hangup("colibri-" + conferenceId, "Detaching from Jitsi Videobridge");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private boolean isMixerMuc(String mixer)
    {
        Log.info("RayoComponent isMixerMuc " + mixer);

        boolean isMuc = false;

        try {
            if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").hasChatRoom(mixer)) {

                isMuc =  null != XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(mixer);
            }

        } catch (Exception e) {}

        return isMuc;
    }

    private void sendMucMessage(String mixer, String recording, JID participant, String message)
    {
        if (isMixerMuc(mixer))	// not working properly. sending only to single person
        {
            sendMessage(new JID(mixer + "@conference." + getDomain()), participant, message, recording, "groupchat");
        }

    }

    private IQ handleDialCommand(DialCommand command, IQ iq, boolean transferCall)
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
                cp.setProtocol("SIP");
                cp.setDisplayName(callerName);
                cp.setPhoneNumber(to);
                cp.setName(calledName);
                cp.setHeaders(headers);

                reply = doPhoneAndPcCall(JID.escapeNode(handsetId), cp, reply, transferCall);

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
                        headers.put("codec_name", "PCM/48000/2".equals(hp.getMediaPreference()) ? "OPUS" : "PCMU");

                        try {
                            ConferenceManager conferenceManager = ConferenceManager.findConferenceManager(hp.getConferenceId());
                            conferenceManager.setTransferCall(transferCall);

                        } catch (Exception e) {}

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
        presence.setFrom(callId + "@" + getDomain());
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


    private IQ doPhoneAndPcCall(String handsetId, CallParticipant cp, IQ reply, boolean transferCall)
    {
        Log.info("RayoComponent doPhoneAndPcCall " + handsetId);

        CallHandler handsetHandler = CallHandler.findCall(handsetId);

        if (handsetHandler != null)
        {
            try {
                setMixer(handsetHandler, reply, cp, transferCall);

                OutgoingCallHandler outgoingCallHandler = new OutgoingCallHandler(this, cp);

                //outgoingCallHandler.setOtherCall(handsetHandler);
                //handsetHandler.setOtherCall(outgoingCallHandler);

                outgoingCallHandler.start();

                final Element childElement = reply.setChildElement("ref", RAYO_CORE);
                childElement.addAttribute(URI, (String) "xmpp:" + cp.getCallId() + "@" + getDomain());
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

    private void setMixer(CallHandler handsetHandler, IQ reply, CallParticipant cp, boolean transferCall)
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
            cp.setMediaPreference(hp.getMediaPreference());
            conferenceManager.setCallId(mixer);
            conferenceManager.setTransferCall(transferCall);

            String recording = mixer + "-" + cp.getStartTimestamp() + ".au";
            conferenceManager.recordConference(true, recording, "au");
            Config.createCallRecord(cp.getDisplayName(), recording, cp.getPhoneNumber(), cp.getStartTimestamp(), 0, "dialed") ;

            sendMessage(new JID(cp.getCallOwner()), new JID(cp.getCallId() + "@" + getDomain()), "Call started", recording, "chat");

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
                        presence.setFrom(callEvent.getCallId() + "@" + getDomain());
                        presence.setTo(from);

                        if ("001 STATE CHANGED".equals(myEvent))
                        {
                            if ("100 INVITED".equals(callState)) {

                                if (cp.isAutoAnswer() == false)	// SIP handset, no ringing event
                                {
                                    setRingingState(presence, ConferenceManager.isTransferCall(callEvent.getConferenceId()), headers);
                                    sendPacket(presence);
                                }

                            } else if ("200 ESTABLISHED".equals(callState)) {


                            } else if ("299 ENDED".equals(callState)) {

/*
                                if (callEvent.getCallId().indexOf("2fspeaker") > -1 && callEvent.getInfo().indexOf("Reason='System shutdown'") == -1)
                                {
                                    CallParticipant cp2 = new CallParticipant();
                                    cp2.setCallId(cp.getCallId());
                                    cp2.setConferenceId(cp.getConferenceId());
                                    cp2.setDisplayName(cp.getDisplayName());
                                    cp2.setName(cp.getDisplayName());
                                    cp2.setCallOwner(cp.getCallOwner());
                                    cp2.setPhoneNumber(cp.getPhoneNumber());
                                    cp2.setVoiceDetection(true);
                                    cp2.setAutoAnswer(true);
                                    cp2.setProtocol("SIP");
                                    cp2.setMuted(true);	// set mic off

                                    OutgoingCallHandler callHandlerNew = new OutgoingCallHandler(this, cp2);
                                    callHandlerNew.start();
                                }
*/
                            }

                        } else if ("250 STARTED SPEAKING".equals(myEvent)) {

                            broadcastSpeaking(true, callEvent.getCallId(), callEvent.getConferenceId(), from);

                        } else if ("259 STOPPED SPEAKING".equals(myEvent)) {

                            broadcastSpeaking(false, callEvent.getCallId(), callEvent.getConferenceId(), from);

                        } else if ("269 DTMF".equals(myEvent)) {
                            presence.getElement().add(rayoProvider.toXML(new DtmfEvent(callEvent.getCallId(), callEvent.getDtmfKey())));
                            sendPacket(presence);

                        } else if ("230 TREATMENT DONE".equals(myEvent)) {
                            presence.setFrom(callEvent.getCallId() + "@" + getDomain() + "/" + callEvent.getTreatmentId());
                            SayCompleteEvent complete = new SayCompleteEvent();
                            complete.setReason(SayCompleteEvent.Reason.valueOf("SUCCESS"));
                            presence.getElement().add(sayProvider.toXML(complete));
                            sendPacket(presence);
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

            //sendMucMessage(conferenceId, null, from, from.getNode() + (startSpeaking ? " started" : " stopped") + " speaking");

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

                        if (target != null && target.equals(from.toString()) == false)
                        {
                            Presence presence = new Presence();
                            presence.setFrom(conferenceId + "@" + getDomain());
                            presence.setTo(target);

                            if (startSpeaking)
                            {
                                StartedSpeakingEvent speaker = new StartedSpeakingEvent();
                                speaker.setSpeakerId(JID.escapeNode(from.toString()));
                                presence.getElement().add(rayoProvider.toXML(speaker));
                            } else {
                                StoppedSpeakingEvent speaker = new StoppedSpeakingEvent();
                                speaker.setSpeakerId(JID.escapeNode(from.toString()));
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

                        if (target.equals(destination.toString()) == false)
                        {
                            sendMessage(new JID(target), destination, "Call ended", null, "chat");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch (Exception e) {}

            cp.setStartTimestamp(0);
        }
    }


    private void sendMessage(JID from, JID to, String body, String fileName, String type)
    {
        Log.info( "RayoComponent sendMessage " + from + " " + to + " " + body + " " + fileName);

        int port = HttpBindManager.getInstance().getHttpBindUnsecurePort();
        Message packet = new Message();
        packet.setTo(to);
        packet.setFrom(from);
        packet.setType("chat".equals(type) ? Message.Type.chat : Message.Type.groupchat);
        if (fileName != null)
        {
            String url = "http://" + getDomain() + ":" + port + "/rayo/recordings/" + fileName;
            packet.setThread(url);
            body = body + " " + url;
        }

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

        if (defaultIncomingConferenceId.equals(conferenceEvent.getConferenceId())) return;

        ConferenceManager conferenceManager = null;

        try {

            if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT) || conferenceEvent.equals(ConferenceEvent.MEMBER_JOINED))
            {
                Log.info("RayoComponent notifyConferenceMonitors looking for call " + conferenceEvent.getCallId() + " " + conferenceEvent.getMemberCount());

                try {
                    conferenceManager = ConferenceManager.findConferenceManager(conferenceEvent.getConferenceId());
                } catch (Exception e) {}

                if (conferenceManager != null)
                {
                    String groupName = conferenceManager.getGroupName();
                    String callId = conferenceManager.getCallId();

                    if (callId == null) callId = conferenceEvent.getConferenceId();	// special case of SIP incoming

                    CallHandler farParty = CallHandler.findCall(callId);
                    CallHandler callHandler = CallHandler.findCall(conferenceEvent.getCallId());

                    if (callHandler != null)
                    {
                        Log.info("RayoComponent notifyConferenceMonitors found call handler " + callHandler + " " + farParty);

                        CallParticipant callParticipant = callHandler.getCallParticipant();

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

                        /*
                            When mixer is an muc, assume a conference call just sent join/unjoin
                            When mixer is a group, assume a third party call, inform group members

                        */

                        if (groupName == null)
                        {
                            if (isMixerMuc(conferenceEvent.getConferenceId()))
                            {
                                MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(conferenceEvent.getConferenceId());

                                Log.info("RayoComponent notifyConferenceMonitors routing to room occupants of " + conferenceEvent.getConferenceId());

                                for ( MUCRole role : room.getOccupants())
                                {
                                    String jid = role.getUserAddress().toString();
                                    Log.info("RayoComponent notifyConferenceMonitors routing to room occupant " + jid);

                                    Presence presence = new Presence();
                                    presence.setFrom(conferenceEvent.getCallId() + "@" + getDomain());
                                    presence.setTo(jid);

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

                            } else {
                                Log.info("RayoComponent notifyConferenceMonitors routing to owner " + callParticipant.getCallOwner() + " " + memberCount);
                                routeJoinEvent(callParticipant.getCallOwner(), callParticipant, conferenceEvent, memberCount, groupName, callId, farParty, conferenceManager);
                            }

                        } else {

                            Group group = GroupManager.getInstance().getGroup(groupName);

                            for (JID memberJID : group.getMembers())
                            {
                                Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

                                for (ClientSession session : sessions)
                                {
                                    routeJoinEvent(session.getAddress().toString(), callParticipant, conferenceEvent, memberCount, groupName, callId, farParty, conferenceManager);
                                }
                            }
                        }

                        if (memberCount == 0 && conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))
                        {
                            conferenceManager.recordConference(false, null, null);
                            conferenceManager.endConference(conferenceEvent.getConferenceId());

                            CallParticipant heldCall = conferenceManager.getHeldCall();

                            if (heldCall != null)
                            {
                                JID target = getJID(heldCall.getCallId());

                                if (target != null)
                                {
                                    Presence presence = new Presence();
                                    presence.setFrom(callId + "@" + getDomain());
                                    presence.setTo(target);
                                    presence.getElement().add(rayoProvider.toXML(new EndEvent(null, EndEvent.Reason.valueOf("HANGUP"), callParticipant.getHeaders())));
                                    sendPacket(presence);
                                }
                            }

                        } else if (memberCount == 2) {

                            conferenceManager.setTransferCall(false);	// reset after informing on redirect
                        }
                    }
                }
            }

        } catch (Exception e) {

            Log.error( "RayoComponent Error in notifyConferenceMonitors " + e);
            e.printStackTrace();
        }
    }

    private void routeJoinEvent(String callee, CallParticipant callParticipant, ConferenceEvent conferenceEvent, int memberCount, String groupName, String callId, CallHandler farParty, ConferenceManager conferenceManager)
    {
        Log.info( "RayoComponent routeJoinEvent " + callee + " " + callId + " " + groupName + " " + memberCount + " " + farParty);

        if (callee == null) return;

        Presence presence = new Presence();
        presence.setFrom(callId + "@" + getDomain());
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

                        setAnsweredState(presence, conferenceManager.isTransferCall(), headers);
                        sendPacket(presence);

                    } else {
                        Log.info( "RayoComponent routeJoinEvent far party leaving ");
                    }

                } else {

                    Log.info( "RayoComponent routeJoinEvent someone joined ");

                    if (callId.equals(conferenceEvent.getCallId())) // far party joined
                    {
                        Log.info( "RayoComponent routeJoinEvent far party joined ");

                        setAnsweredState(presence, conferenceManager.isTransferCall(), headers);
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
                                conferenceManager.setHeldCall(null);
                                setAnsweredState(presence, conferenceManager.isTransferCall(), headers);
                                sendPacket(presence);

                            } else {
                                Log.info( "RayoComponent routeJoinEvent not held " + fp.getProtocol() + " " + fp);

                                if ("WebRtc".equals(fp.getProtocol()) == false)
                                {
                                    Log.info( "RayoComponent routeJoinEvent handset joing sip call");

                                    setAnsweredState(presence, conferenceManager.isTransferCall(), headers);
                                    sendPacket(presence);
                                }
                            }
                        }
                    }
                }

            } else if (memberCount == 1) {		// callee or caller

                if (conferenceEvent.equals(ConferenceEvent.MEMBER_LEFT))
                {
                    Log.info( "RayoComponent routeJoinEvent only one person left");

                    if (callId.equals(conferenceEvent.getCallId()) == false)	// handset leaving
                    {
                        if (farParty != null)
                        {
                            Log.info( "RayoComponent routeJoinEvent handset leaving call " + farParty.getCallParticipant());

                            CallParticipant fp = farParty.getCallParticipant();

                            if (callParticipant.isAutoAnswer()) fp.setHeld(true);	// sip phone as handset hangup

                            if (fp.isHeld())
                            {
                                Log.info( "RayoComponent routeJoinEvent call held with " + callParticipant);

                                presence.getElement().add(handsetProvider.toXML(new OnHoldEvent()));
                                sendPacket(presence);

                                conferenceManager.setHeldCall(callParticipant);
                            }
                        }

                    } else {				// far party leaving

                        Log.info( "RayoComponent routeJoinEvent far party leaving call " + callParticipant);

                        if (callParticipant.isHeld())
                        {
                            Log.info( "RayoComponent routeJoinEvent call held with " + farParty);

                            presence.getElement().add(handsetProvider.toXML(new OnHoldEvent()));
                            sendPacket(presence);

                            conferenceManager.setHeldCall(farParty.getCallParticipant());

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

                finishCallRecord(callParticipant);
            }
        }
    }


    private void setAnsweredState(Presence presence, boolean isTransfer, Map<String, String> headers)
    {
        if (isTransfer)
        {
            presence.getElement().add(handsetProvider.toXML(new TransferredEvent()));
        } else {
            presence.getElement().add(rayoProvider.toXML(new AnsweredEvent(null, headers)));
        }
    }

    private void setRingingState(Presence presence, boolean isTransfer, Map<String, String> headers)
    {
        if (isTransfer)
        {
            presence.getElement().add(handsetProvider.toXML(new TransferringEvent()));
        } else {
            presence.getElement().add(rayoProvider.toXML(new RingingEvent(null, headers)));
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
        boolean canRoute = false;
        Group group = null;

        JID foundUser = findUser(cp.getToPhoneNumber());

        if (foundUser != null)
            canRoute = true;

        else {
            try {
                group = GroupManager.getInstance().getGroup(cp.getToPhoneNumber());
                canRoute = true;

            } catch (GroupNotFoundException e) {

            }
        }

        Log.info("Incoming SIP, call route to entity " + cp.getToPhoneNumber() + " " + canRoute);

        if (canRoute)
        {
            String callId = "rayo-incoming-" + System.currentTimeMillis();
            cp.setCallId(callId);
            cp.setConferenceId(callId);

            if (cp.getMediaPreference() == null) cp.setMediaPreference("PCMU/8000/1");	// regular phone

            ConferenceManager conferenceManager = ConferenceManager.getConference(callId, cp.getMediaPreference(), cp.getToPhoneNumber(), false);
            conferenceManager.setCallId(callId);

            Map<String, String> headers = cp.getHeaders();
            headers.put("mixer_name", callId);
            headers.put("call_protocol", "SIP");
            headers.put("codec_name", "PCM/48000/2".equals(cp.getMediaPreference()) ? "OPUS" : "PCMU");
            headers.put("group_name", cp.getToPhoneNumber());

            if (foundUser != null)		// send this call to specific user
            {
                cp.setCallOwner(foundUser.toString());
                routeSIPCall(foundUser, cp, callId, headers);

            } else {

                conferenceManager.setGroupName(cp.getToPhoneNumber());

                for (JID memberJID : group.getMembers())
                {
                    Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(memberJID.getNode());

                    for (ClientSession session : sessions)
                    {
                        routeSIPCall(session.getAddress(), cp, callId, headers);
                    }
                }
            }
        }

        return canRoute;
    }

    public void routeSIPCall(JID callee, CallParticipant cp, String callId, Map<String, String> headers)
    {
        Log.info("routeSIPCall to user " + callee);

        if (callee != null)		// send this call to user
        {
            Presence presence = new Presence();
            presence.setFrom(callId + "@" + getDomain());
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
    private IQHandler privateIQHandler = null;
    private IQHandler publicIQHandler = null;
    private IQHandler muteIQHandler = null;
    private IQHandler unmuteIQHandler = null;
    private IQHandler holdIQHandler = null;

    private IQHandler sayIQHandler = null;
    private IQHandler pauseSayIQHandler = null;
    private IQHandler resumeSayIQHandler = null;

    private IQHandler recordIQHandler = null;
    private IQHandler pauseRecordIQHandler = null;
    private IQHandler resumeRecordIQHandler = null;

    private IQHandler acceptIQHandler = null;
    private IQHandler answerIQHandler = null;
    private IQHandler dialIQHandler = null;
    private IQHandler hangupIQHandler = null;
    private IQHandler redirectIQHandler = null;
    private IQHandler dtmfIQHandler = null;

    private void createIQHandlers()
    {
        XMPPServer server = XMPPServer.getInstance();

        onHookIQHandler 	= new OnHookIQHandler(); server.getIQRouter().addHandler(onHookIQHandler);
        offHookIQHandler 	= new OffHookIQHandler(); server.getIQRouter().addHandler(offHookIQHandler);
        privateIQHandler 	= new PrivateIQHandler(); server.getIQRouter().addHandler(privateIQHandler);
        publicIQHandler 	= new PublicIQHandler(); server.getIQRouter().addHandler(publicIQHandler);
        muteIQHandler 		= new MuteIQHandler(); server.getIQRouter().addHandler(muteIQHandler);
        unmuteIQHandler		= new UnmuteIQHandler(); server.getIQRouter().addHandler(unmuteIQHandler);
        holdIQHandler 		= new HoldIQHandler(); server.getIQRouter().addHandler(holdIQHandler);

        recordIQHandler 		= new RecordIQHandler(); server.getIQRouter().addHandler(recordIQHandler);
        pauseRecordIQHandler 	= new PauseRecordIQHandler(); server.getIQRouter().addHandler(pauseRecordIQHandler);
        resumeRecordIQHandler 	= new ResumeRecordIQHandler(); server.getIQRouter().addHandler(resumeRecordIQHandler);

        sayIQHandler 		= new SayIQHandler(); server.getIQRouter().addHandler(sayIQHandler);
        pauseSayIQHandler	= new PauseSayIQHandler(); server.getIQRouter().addHandler(pauseSayIQHandler);
        resumeSayIQHandler 	= new ResumeSayIQHandler(); server.getIQRouter().addHandler(resumeSayIQHandler);

        acceptIQHandler 	= new AcceptIQHandler(); server.getIQRouter().addHandler(acceptIQHandler);
        answerIQHandler 	= new AnswerIQHandler(); server.getIQRouter().addHandler(answerIQHandler);
        dialIQHandler 		= new DialIQHandler(); server.getIQRouter().addHandler(dialIQHandler);
        hangupIQHandler 	= new HangupIQHandler(); server.getIQRouter().addHandler(hangupIQHandler);
        redirectIQHandler	= new RedirectIQHandler(); server.getIQRouter().addHandler(redirectIQHandler);
        dtmfIQHandler 		= new DtmfIQHandler(); server.getIQRouter().addHandler(dtmfIQHandler);
    }

    private void destroyIQHandlers()
    {
        XMPPServer server = XMPPServer.getInstance();

        if (onHookIQHandler != null) {server.getIQRouter().removeHandler(onHookIQHandler); onHookIQHandler = null;}
        if (offHookIQHandler != null) {server.getIQRouter().removeHandler(offHookIQHandler); offHookIQHandler = null;}
        if (privateIQHandler != null) {server.getIQRouter().removeHandler(privateIQHandler); privateIQHandler = null;}
        if (publicIQHandler != null) {server.getIQRouter().removeHandler(publicIQHandler); publicIQHandler = null;}
        if (muteIQHandler != null) {server.getIQRouter().removeHandler(muteIQHandler); muteIQHandler = null;}
        if (unmuteIQHandler != null) {server.getIQRouter().removeHandler(unmuteIQHandler); unmuteIQHandler = null;}
        if (holdIQHandler != null) {server.getIQRouter().removeHandler(holdIQHandler); holdIQHandler = null;}

        if (sayIQHandler != null) {server.getIQRouter().removeHandler(sayIQHandler); sayIQHandler = null;}
        if (pauseSayIQHandler != null) {server.getIQRouter().removeHandler(pauseSayIQHandler); pauseSayIQHandler = null;}
        if (resumeSayIQHandler != null) {server.getIQRouter().removeHandler(resumeSayIQHandler); resumeSayIQHandler = null;}

        if (acceptIQHandler != null) {server.getIQRouter().removeHandler(acceptIQHandler); acceptIQHandler = null;}
        if (answerIQHandler != null) {server.getIQRouter().removeHandler(answerIQHandler); answerIQHandler = null;}
        if (dialIQHandler != null) {server.getIQRouter().removeHandler(dialIQHandler); dialIQHandler = null;}
        if (hangupIQHandler != null) {server.getIQRouter().removeHandler(hangupIQHandler); hangupIQHandler = null;}
        if (redirectIQHandler != null) {server.getIQRouter().removeHandler(redirectIQHandler); redirectIQHandler = null;}
        if (dtmfIQHandler != null) {server.getIQRouter().removeHandler(dtmfIQHandler); dtmfIQHandler = null;}
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
    private class PrivateIQHandler extends IQHandler
    {
        public PrivateIQHandler() { super("Rayo: XEP 0327 - Private");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("private", RAYO_HANDSET); }
    }
    private class PublicIQHandler extends IQHandler
    {
        public PublicIQHandler() { super("Rayo: XEP 0327 - Public");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("public", RAYO_HANDSET); }
    }

    private class MuteIQHandler extends IQHandler
    {
        public MuteIQHandler() { super("Rayo: XEP 0327 - Mute");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("mute", RAYO_HANDSET); }
    }

    private class UnmuteIQHandler extends IQHandler
    {
        public UnmuteIQHandler() { super("Rayo: XEP 0327 - Unmute");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("unmute", RAYO_HANDSET); }
    }

    private class HoldIQHandler extends IQHandler
    {
        public HoldIQHandler() { super("Rayo: XEP 0327 - Hold");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("hold", RAYO_HANDSET); }
    }



    private class RecordIQHandler extends IQHandler
    {
        public RecordIQHandler() { super("Rayo: XEP 0327 - Record");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("record", RAYO_RECORD); }
    }

    private class PauseRecordIQHandler extends IQHandler
    {
        public PauseRecordIQHandler() { super("Rayo: XEP 0327 - Pause Record");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("pause", RAYO_RECORD); }
    }

    private class ResumeRecordIQHandler extends IQHandler
    {
        public ResumeRecordIQHandler() { super("Rayo: XEP 0327 - Resume Record");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("resume", RAYO_RECORD); }
    }



    private class SayIQHandler extends IQHandler
    {
        public SayIQHandler() { super("Rayo: XEP 0327 - Say (text to speech)");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("say", RAYO_SAY); }
    }

    private class PauseSayIQHandler extends IQHandler
    {
        public PauseSayIQHandler() { super("Rayo: XEP 0327 - Pause Say");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("pause", RAYO_SAY); }
    }

    private class ResumeSayIQHandler extends IQHandler
    {
        public ResumeSayIQHandler() { super("Rayo: XEP 0327 - Resume Say");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("resume", RAYO_SAY); }
    }




    private class AcceptIQHandler extends IQHandler
    {
        public AcceptIQHandler() { super("Rayo: XEP 0327 - Accept");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("accept", RAYO_CORE); }
    }

    private class AnswerIQHandler extends IQHandler
    {
        public AnswerIQHandler() { super("Rayo: XEP 0327 - Answer");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("answer", RAYO_CORE); }
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

    private class RedirectIQHandler extends IQHandler
    {
        public RedirectIQHandler() { super("Rayo: XEP 0327 - Redirect");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("redirect", RAYO_CORE); }
    }

    private class DtmfIQHandler extends IQHandler
    {
        public DtmfIQHandler() { super("Rayo: XEP 0327 - DTMF");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("dtmf", RAYO_CORE); }
    }
}
