/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip;

import java.net.InetSocketAddress;
import org.voicebridge.*;
import org.xmpp.jnodes.IChannel;
import com.sun.voip.server.*;

import java.util.*;

/**
 * A Class to represent a call participant - a party in a call.
 */
public class CallParticipant {
    /**
     * parameters used for this call
     */
    private int     callAnswerTimeout		   = 0;
    private String  callAnsweredTreatment          = null;
    private String  callEndTreatment     	   = null;
    private String  callEstablishedTreatment       = null;
    private String  callId			   = null;
    private String  callOwner				= null;
    private int	    callTimeout			   = 0;
    private String  conferenceId                   = null;
    private String  conferenceJoinTreatment	   = null;
    private String  conferenceLeaveTreatment	   = null;
    private String  conferenceDisplayName	   = null;
    private String  displayName                    = null;
    private boolean distributedBridge		   = false;
    private boolean doNotRecord			   = false;
    private boolean dtmfDetection        	   = true;
    private boolean dtmfSuppression		   = true;
    private String  encryptionKey		   = null;
    private String  encryptionAlgorithm		   = null;
    private String  firstConferenceMemberTreatment = null;
    private boolean forwardDtmfKeys		   = false;
    private String  forwardingCallId		   = null;
    private boolean ignoreTelephoneEvents	   = false;
    private String  inputTreatment		   = null;
    private boolean isConferenceMuted	           = false;
    private boolean isConferenceSilenced	   = false;
    private boolean isMuted		   	   = false;
    private boolean isMuteWhisperGroup	   	   = false;
    private boolean isRecorder			   = false;
    private String  mediaPreference		   = null;
    private boolean migrateCall                    = false;
    private boolean mixMinus			   = true;
    private int     joinConfirmationTimeout        = 0;
    private String  name                           = null;
    private String  toPhoneNumber                    = null;
    private String  phoneNumber                    = null;
    private String  phoneNumberLocation            = null;
    private String  recordDirectory		   = null;
    private String  fromRecordingFile		   = null;
    private String  fromRecordingType		   = null;
    private String  toRecordingFile		   = null;
    private String  toRecordingType		   = null;
    private boolean useConferenceReceiverThread    = false;
    private String  protocol			   = null;
    private String  remoteCallId		   = null;
    private String  sipProxy			   = null;
    private boolean speexEncode			   = false;
    private String  whisperGroupId		   = null;
    private String  voipGateway			   = null;
    private boolean voiceDetection       	   = false;
    private boolean voiceDetectionWhileMuted       = false;
    private boolean handleSessionProgress	   = false;
    private String remoteMediaInfo		   = null;
    private ProxyCredentials proxyCredentials = null;
    private IChannel channel = null;
    private boolean autoAnswer = false;
    private long startTimestamp = 0;
    private long endTimestamp = 0;
    private Map<String, String> headers = new HashMap<String, String>();
    private CallParticipant farParty = null;
    private CallParticipant handset = null;
    private boolean isHeld = false;

    /*
     * Second party in a two party call or target of migration
     */
    private String  secondPartyCallEndTreatment    = null;
    private String  secondPartyCallId		   = null;
    private String  secondPartyNumber              = null;
    private String  secondPartyName                = null;
    private int     secondPartyTimeout             = 0;
    private String  secondPartyTreatment           = null;
    private boolean secondPartyVoiceDetection      = false;
    private String  rtmfpSendStream					= null;
    private String  rtmfpRecieveStream				= null;
    private CallHandler otherCall					= null;

    /**
     * Constructor
     */
    public CallParticipant() {
    }


    /**
     * Get/Set proxy credentials
     */
    public ProxyCredentials getProxyCredentials()
    {
        return proxyCredentials;
    }

    public void setProxyCredentials(ProxyCredentials proxyCredentials)
    {
        this.proxyCredentials = proxyCredentials;
    }

    public CallHandler getOtherCall()
    {
        return otherCall;
    }

    public void setOtherCall(CallHandler otherCall)
    {
        this.otherCall = otherCall;
    }

    public IChannel getChannel()
    {
        return channel;
    }

    public void setChannel(IChannel channel)
    {
        this.channel = channel;
    }

    public long getStartTimestamp()
    {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp)
    {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp()
    {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp)
    {
        this.endTimestamp = endTimestamp;
    }

    public CallParticipant getFarParty()
    {
        return farParty;
    }

    public void setFarParty(CallParticipant farParty)
    {
        this.farParty = farParty;
    }

    public CallParticipant getHandset()
    {
        return handset;
    }

    public void setHandset(CallParticipant handset)
    {
        this.handset = handset;
    }

    public Map<String, String> getHeaders()
    {
        return headers;
    }

    public void setHeaders(Map<String, String> headers)
    {
        this.headers = headers;
    }

    /**
     * Get RTMFP send stream name
     */
    public String getRtmfpSendStream() {
        return rtmfpSendStream;
    }

    /**
     * Get RTMFP recieve stream name
     */
    public String getRtmfpRecieveStream() {
        return rtmfpRecieveStream;
    }

    /**
     * Get call answer timeout
     */
    public int getCallAnswerTimeout() {
    return callAnswerTimeout;
    }

    /**
     * Get audio treatment string for call answered
     */
    public String getCallAnsweredTreatment() {
        return callAnsweredTreatment;
    }

    /**
     * Get audio treatment string for call end
     */
    public String getCallEndTreatment() {
        return callEndTreatment;
    }

    /**
     * Set RTMFP send stream name
     */
    public void setRtmfpSendStream(String rtmfpSendStream) {
        this.rtmfpSendStream = rtmfpSendStream;
    }

    /**
     * Set RTMFP recieve stream name
     */
    public void setRtmfpRecieveStream(String rtmfpRecieveStream) {
        this.rtmfpRecieveStream = rtmfpRecieveStream;
    }

    /**
     * Get audio treatment string for call established
     */
    public String getCallEstablishedTreatment() {
        return callEstablishedTreatment;
    }

    /**
     * Get call Id.  This call Id is used to uniquely identify
     * one of our calls.  It is not associated with the SIP CallId.
     */
    public String getCallId() {
    return callId;
    }

    /**
     * Get call Oner.  This call owner is used to uniquely identify
     * owner of the call.
     */
    public String getCallOwner() {
    return callOwner;
    }

    /**
     * Get the call timeout value.
     */
    public int getCallTimeout() {
    return callTimeout;
    }

    /**
     * Get conference id
     */
    public String getConferenceId() {
        return conferenceId;
    }

    /**
     * Get audio treatment string for joining a conference
     */
    public String getConferenceJoinTreatment() {
        return conferenceJoinTreatment;
    }

    /**
     * Get audio treatment string for leaving a conference
     */
    public String getConferenceLeaveTreatment() {
        return conferenceLeaveTreatment;
    }

    /**
     * returns the conference display name
     */
    public String getConferenceDisplayName() {
    return conferenceDisplayName;
    }

    /**
     * returns the caller id display name
     */
    public String getDisplayName() {
    return displayName;
    }

    /**
     * returns whether or not this is a call from a distributed bridge
     */
    public boolean isDistributedBridge() {
    return distributedBridge;
    }

    /**
     * is dtmf detection enabled
     */
    public boolean dtmfDetection() {
    return dtmfDetection;
    }

    /**
     * is dtmf suppression enabled
     */
    public boolean dtmfSuppression() {
    return dtmfSuppression;
    }

    /**
     * should telephone events be ignored
     */
    public boolean ignoreTelephoneEvents() {
    return ignoreTelephoneEvents;
    }

    public String getInputTreatment() {
    return inputTreatment;
    }

    /**
     * encryption key
     */
    public String getEncryptionKey() {
    return encryptionKey;
    }

    /**
     * encryption algorithm
     */
    public String getEncryptionAlgorithm() {
    return encryptionAlgorithm;
    }

    /**
     * Get first conference member treatment
     */
    public String getFirstConferenceMemberTreatment() {
    return firstConferenceMemberTreatment;
    }

    /**
     * Forward dtmf keys to other calls in a whisper group
     * Used for ExecMC outgoing calls.
     */
    public boolean getForwardDtmfKeys() {
    return forwardDtmfKeys;
    }

    /**
     * Get Forwarding callId.  This is the id of the call
     * which forwards data to this call.
     */
    public String getForwardingCallId() {
    return forwardingCallId;
    }

    /**
     * Handle SESSION_PROGRESS SIP message
     */
    public boolean getHandleSessionProgress() {
    return handleSessionProgress;
    }

    /**
     * Get remote media Info for non-signaling call agent
     */
    public String getRemoteMediaInfo() {
    return remoteMediaInfo;
    }

    /**
     * get join confirmation timeout
     */
    public int getJoinConfirmationTimeout() {
    return joinConfirmationTimeout;
    }

    /**
     * Is call muted
     */
    public boolean isMuted() {
    return isMuted;
    }

    public boolean isHeld() {
    return isHeld;
    }

    /**
     * Is the call muted from the whisper group
     */
    public boolean isMuteWhisperGroup() {
    return isMuteWhisperGroup;
    }

    /**
     * Is conference muted from this call
     */
    public boolean isConferenceMuted() {
    return isConferenceMuted;
    }

    /**
     * Is main conference silenced from this call
     */
    public boolean isConferenceSilenced() {
        return isConferenceSilenced;
    }

    /**
     * Is this a recorder
     */
    public boolean isRecorder() {
    return isRecorder;
    }
    /**
     * Is call auto-answered
     *
    */

    public boolean isAutoAnswer()
    {
        return autoAnswer;
    }
    /**
     * Get media preference
     */
    public String getMediaPreference() {
    return mediaPreference;
    }

    /**
     * Is call being migrated
     */
    public boolean migrateCall() {
    return migrateCall;
    }

    /**
     * Is mixMinus enabled
     */
    public boolean mixMinus() {
    return mixMinus;
    }

    /**
     * returns the name of the call participant
     * @return the name
     */
    public String getName() {
    return name;
    }

    /**
     * returns whether or not it's okay to record this call
     */
    public boolean doNotRecord() {
    return doNotRecord;
    }

    /**
     * returns the phone number of the call participant
     * @return the phone number
     */
    public String getPhoneNumber() {
    return phoneNumber;
    }

    public String getToPhoneNumber() {
    return toPhoneNumber;
    }

    /**
     * returns the phone number location
     * @return the phone number location
     */
    public String getPhoneNumberLocation() {
    return phoneNumberLocation;
    }

    /**
     * returns the record directory
     * @return the record directory
     */
    public String getRecordDirectory() {
    return recordDirectory;
    }

    /**
     * returns path to recording file with data from member.
     */
    public String getFromRecordingFile() {
    return fromRecordingFile;
    }

    /**
     * returns type of recording.
     */
    public String getFromRecordingType() {
    return fromRecordingType;
    }

    /**
     * returns path to recording file with data to member.
     */
    public String getToRecordingFile() {
    return toRecordingFile;
    }

    /**
     * returns type of recording.
     */
    public String getToRecordingType() {
    return toRecordingType;
    }

    /**
     * returns the signaling protocol, SIP or h.323
     */
    public String getProtocol() {
    return protocol;
    }

    /**
     * returns the remote callId.  This is used for calling from
     * one bridge to another.
     */
    public String getRemoteCallId() {
    return remoteCallId;
    }

    /**
     * returns the Sip proxy
     */
    public String getSipProxy() {
    return sipProxy;
    }

    /**
     * returns whether or not to Speex encode
     */
    public boolean speexEncode() {
    return speexEncode;
    }

    /**
     * returns the whisper group this call is initially in
     */
    public String getWhisperGroupId() {
    return whisperGroupId;
    }

    /**
     * returns voip gateway address
     */
    public String getVoIPGateway() {
    return voipGateway;
    }

    /**
     * Is this a meeting central softphone it can use the
     * conference receiver thread instead of having a separate
     * receiver thread.
     */
    public boolean useConferenceReceiverThread() {
    return useConferenceReceiverThread;
    }

    /**
     * is voice detection enabled
     */
    public boolean voiceDetection() {
    return voiceDetection;
    }

    /**
     * should voice detection be done even while muted?
     */
    public boolean voiceDetectionWhileMuted() {
    return voiceDetectionWhileMuted;
    }

    /**
     * Set call answer timeout
     */
    public void setCallAnswerTimeout(int callAnswerTimeout) {
    this.callAnswerTimeout = callAnswerTimeout;
    }

    /**
     * Set audio treatment string for call answered
     */
    public void setCallAnsweredTreatment(String callAnsweredTreatment) {
        this.callAnsweredTreatment = callAnsweredTreatment;
    }

    /**
     * Set audio treatment string for call end
     */
    public void setCallEndTreatment(String callEndTreatment) {
    this.callEndTreatment = callEndTreatment;
    }

    /**
     * Set audio treatment string for call established
     */
    public void setCallEstablishedTreatment(String callEstablishedTreatment) {
        this.callEstablishedTreatment = callEstablishedTreatment;
    }

    /**
     * Set call Number
     */
    public void setCallId(String callId) {
    this.callId = callId;
    }

    /**
     * Set call Owner
     */
    public void setCallOwner(String callOwner) {
    this.callOwner = callOwner;
    }

    /**
     * Set call timeout (seconds)
     */
    public void setCallTimeout(int callTimeout) {
    this.callTimeout = callTimeout;
    }

    /**
     * Set conference id
     */
    public void setConferenceId(String conferenceId) {
    this.conferenceId = conferenceId;
    }

    /**
     * Set audio treatment string for joining a conference
     */
    public void setConferenceJoinTreatment(String conferenceJoinTreatment) {
    this.conferenceJoinTreatment = conferenceJoinTreatment;
    }

    /**
     * Set audio treatment string for leaving a conference
     */
    public void setConferenceLeaveTreatment(String conferenceLeaveTreatment) {
    this.conferenceLeaveTreatment = conferenceLeaveTreatment;
    }

    /**
     * Set the flag to mute the conference from this call.
     */
    public void setConferenceMuted(boolean isConferenceMuted) {
    this.isConferenceMuted = isConferenceMuted;
    }

    /**
     * Set the flag to silence the main conference from this call.
     */
    public void setConferenceSilenced(boolean isConferenceSilenced) {
        this.isConferenceSilenced = isConferenceSilenced;
    }

    /**
     * Set the conference display name
     */
    public void setConferenceDisplayName(String conferenceDisplayName) {
    this.conferenceDisplayName = conferenceDisplayName;
    }

    /**
     * set the caller id display name
     */
    public void setDisplayName(String displayName) {
    this.displayName = displayName;
    }

    /**
     * set flag to indicate this is a distributed bridge
     */
    public void setDistributedBridge(boolean distributedBridge) {
    this.distributedBridge = distributedBridge;
    }

    /**
     * Set dtmf detection flag
     */
    public void setDtmfDetection(boolean dtmfDetection) {
    this.dtmfDetection = dtmfDetection;
    }

    /**
     * Set dtmf suppression flag
     */
    public void setDtmfSuppression(boolean dtmfSuppression) {
    this.dtmfSuppression = dtmfSuppression;
    }

    /**
     * Set ignore telephone events flag
     */
    public void setIgnoreTelephoneEvents(boolean ignoreTelephoneEvents) {
    this.ignoreTelephoneEvents = ignoreTelephoneEvents;
    }

    public void setInputTreatment(String inputTreatment) {
    this.inputTreatment = inputTreatment;
    }

    /**
     * Set encryption key
     */
    public void setEncryptionKey(String encryptionKey) {
    this.encryptionKey = encryptionKey;
    }

    /**
     * Set encryption algorithm
     */
    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
    this.encryptionAlgorithm = encryptionAlgorithm;
    }

    /**
     * Set first conference member treatment
     */
    public void setFirstConferenceMemberTreatment(
        String firstConferenceMemberTreatment) {

    this.firstConferenceMemberTreatment =
        firstConferenceMemberTreatment;
    }

    /**
     * Set forward dtmf keys
     */
    public void setForwardDtmfKeys(boolean forwardDtmfKeys) {
    this.forwardDtmfKeys = forwardDtmfKeys;
    }

    /*
     * Set Forwarding callId.  This is the id of the call
     * which forwards data to this call.
     */
    public void setForwardingCallId(String forwardingCallId) {
    this.forwardingCallId = forwardingCallId;
    }

    /**
     * set handleSessionProgress
     */
    public void setHandleSessionProgress(boolean handleSessionProgress) {
    this.handleSessionProgress = handleSessionProgress;
    }

    /**
     * Set remote media info for non-signaling call agent.
     */
    public void setRemoteMediaInfo(String remoteMediaInfo) {
    this.remoteMediaInfo = remoteMediaInfo;
    }

    /**
     * Set join confirmation timeout
     */
    public void setJoinConfirmationTimeout(int joinConfirmationTimeout) {
    this.joinConfirmationTimeout = joinConfirmationTimeout;
    }

    /**
     * Set flag to migrate call
     */
    public void setMigrateCall(boolean migrateCall) {
    this.migrateCall = migrateCall;
    }

    /**
     * Set media preference
     */
    public void setMediaPreference(String mediaPreference) {
    this.mediaPreference = mediaPreference;
    }

    /**
     * Set flag to enable / disable mix minus
     */
    public void setMixMinus(boolean mixMinus) {
    this.mixMinus = mixMinus;
    }

    /**
     * Set the flag to mute the conference from this call.
     */
    public void setMuted(boolean isMuted) {
    this.isMuted = isMuted;
    }

    public void setHeld(boolean isHeld) {
    this.isHeld = isHeld;
    }

    /**
     * Set the flag to mute the this call from the whisper group.
     */
    public void setMuteWhisperGroup(boolean isMuteWhisperGroup) {
        this.isMuteWhisperGroup = isMuteWhisperGroup;
    }

    /**
     * Set isRecorder
     */
    public void setRecorder(boolean isRecorder) {
    this.isRecorder = isRecorder;
    }
    /**
     * Set autoAnswer
     */
    public void setAutoAnswer(boolean autoAnswer) {
    this.autoAnswer = autoAnswer;
    }

    /**
     * Set the name of the call participant
     */
    public void setName(String name) {
    this.name = name;
    }

    /**
     * Set whether or not it's ok to record this call
     */
    public void setDoNotRecord(boolean doNotRecord) {
    this.doNotRecord = doNotRecord;
    }

    /**
     * Set the phone number of the call participant
     */
    public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    }

    public void setToPhoneNumber(String phoneNumber) {
    this.toPhoneNumber = phoneNumber;
    }

    /**
     * Set the phone number location
     */
    public void setPhoneNumberLocation(String phoneNumberLocation) {
    this.phoneNumberLocation = phoneNumberLocation;
    }

    /**
     * Set the record directory
     */
    public void setRecordDirectory(String recordDirectory) {
    this.recordDirectory = recordDirectory;
    }

    /**
     * Sets the recording file path for data from member.
     */
    public void setFromRecordingFile(String fromRecordingFile) {
    this.fromRecordingFile = fromRecordingFile;
    }

    /**
     * Sets the recording type for data from member.
     */
    public void setFromRecordingType(String fromRecordingType) {
        this.fromRecordingType = fromRecordingType;
    }

    /**
     * Sets the recording file path for data to member.
     */
    public void setToRecordingFile(String toRecordingFile) {
    this.toRecordingFile = toRecordingFile;
    }

    /**
     * Sets the recording file type for data to member.
     */
    public void setToRecordingType(String toRecordingType) {
        this.toRecordingType = toRecordingType;
    }

    /*
     * Sets the signaling protocol
     */
    public void setProtocol(String protocol) {
    this.protocol = protocol;
    }

    /*
     * Sets the remote call id.  This is used for bridge to bridge calls.
     */
    public void setRemoteCallId(String remoteCallId) {
    this.remoteCallId = remoteCallId;
    }

    /**
     * Sets the Sip proxy
     */
    public void setSipProxy(String sipProxy) {
    this.sipProxy = sipProxy;
    }

    /**
     * Sets speexEncode flag
     */
    public void setSpeexEncode(boolean speexEncode) {
    this.speexEncode = speexEncode;
    }

    /**
     * Sets the whisperGroup this call is initially in
     */
    public void setWhisperGroupId(String whisperGroupId) {
    this.whisperGroupId = whisperGroupId;
    }

    /**
     * Sets the address of the SIP server
     */
    public void setVoIPGateway(String voipGateway) {
    this.voipGateway = voipGateway;
    }

    /**
     * Is this a meeting central softphone it can use the
     * conference receiver thread instead of having a separate
     * receiver thread.
     */
    public void setUseConferenceReceiverThread(
        boolean useConferenceReceiverThread) {

    this.useConferenceReceiverThread = true;
    }

    /**
     * set voice detection flag
     */
    public void setVoiceDetection(boolean voiceDetection) {
    this.voiceDetection = voiceDetection;
    }

    /**
     * set voice detection while muted flag
     */
    public void setVoiceDetectionWhileMuted(boolean voiceDetectionWhileMuted) {
    this.voiceDetectionWhileMuted = voiceDetectionWhileMuted;
    }

    /*
     * The rest of the call parameters are two party calls
     */

    /**
     * get second party number
     */
    public String getSecondPartyNumber() {
    return secondPartyNumber;
    }

    /**
     * get second party name
     */
    public String getSecondPartyName() {
        return secondPartyName;
    }

    /**
     * get second party timeout
     */
    public int getSecondPartyTimeout() {
    return secondPartyTimeout;
    }

    /**
     * get second party treatment
     */
    public String getSecondPartyTreatment() {
    return secondPartyTreatment;
    }

    /**
     * get second party call end treatment
     */
    public String getSecondPartyCallEndTreatment() {
    return secondPartyCallEndTreatment;
    }

    /**
     * get second party call Id
     */
    public String getSecondPartyCallId() {
    return secondPartyCallId;
    }

    /**
     * get second party voice detection
     */
    public boolean getSecondPartyVoiceDetection() {
    return secondPartyVoiceDetection;
    }

    /**
     * Set second party number
     */
    public void setSecondPartyNumber(String secondPartyNumber) {
    this.secondPartyNumber = secondPartyNumber;
    }

    /**
     * Set second party name
     */
    public void setSecondPartyName(String secondPartyName) {
    this.secondPartyName = secondPartyName;
    }

    /**
     * Set second party timeout
     */
    public void setSecondPartyTimeout(int secondPartyTimeout) {
    this.secondPartyTimeout = secondPartyTimeout;
    }

    /**
     * Set second party treatment
     */
    public void setSecondPartyTreatment(String secondPartyTreatment) {
    this.secondPartyTreatment = secondPartyTreatment;
    }

    /**
     * Set second party call end treatment
     */
    public void setSecondPartyCallEndTreatment(
        String secondPartyCallEndTreatment) {

    this.secondPartyCallEndTreatment = secondPartyCallEndTreatment;
    }

    /**
     * Set second party call Id
     */
    public void setSecondPartyCallId(String secondPartyCallId) {
    this.secondPartyCallId = secondPartyCallId;
    }

    /**
     * Set second party voice detection
     */
    public void setSecondPartyVoiceDetection(
        boolean secondPartyVoiceDetection) {

    this.secondPartyVoiceDetection = secondPartyVoiceDetection;
    }

    /**
     * Put together the parameters into a request string which can be
     * sent to the bridge server to initiate a call.
     */
    public String getCallSetupRequest() {
    String request = "";

        if (callAnswerTimeout != 0) {
        request += "callAnswerTimeout=" + callAnswerTimeout + "\r\n";
        }

        if (callAnsweredTreatment != null) {
            request +=
        "callAnsweredTreatment=" + callAnsweredTreatment + "\r\n";
    }

        if (callEndTreatment != null) {
        request += "callEndTreatment=" + callEndTreatment + "\r\n";
    }

        if (callEstablishedTreatment != null) {
        request += "callEstablishedTreatment="
        + callEstablishedTreatment + "\r\n";
    }

    if (callId != null) {
        request += "callId=" + callId + "\r\n";
    }

    if (callTimeout != 0) {
        request += "callTimeout=" + callTimeout + "\r\n";
    }

        if (conferenceId != null) {
            request += "conferenceId=" + conferenceId + "\r\n";
    }

    if (conferenceJoinTreatment != null) {
            request += "conferenceJoinTreatment=" + conferenceJoinTreatment
        + "\r\n";
    }

    if (conferenceLeaveTreatment != null) {
            request += "conferenceLeaveTreatment="
        + conferenceLeaveTreatment + "\r\n";
    }

    if (conferenceDisplayName != null) {
            request += "conferenceDisplayName=" + conferenceDisplayName + "\r\n";
    }

    if (displayName != null) {
            request += "displayName=" + displayName + "\r\n";
    }

    if (distributedBridge == true) {
        request += "distributedBridge=true\r\n";
    }

    if (doNotRecord == true) {
        request += "doNotRecord=true\r\n";
    }

    if (dtmfDetection == true) {
            request += "dtmfDetection=true\r\n";
    }

    if (dtmfSuppression == false) {
            request += "dtmfSuppression=false\r\n";
    }

    if (ignoreTelephoneEvents == true) {
        request += "ignoreTelephoneEvents=true\r\n";
    }

    if (inputTreatment != null) {
        request += "inputTreatment=" + inputTreatment + "\r\n";
    }

    if (encryptionKey != null) {
        request += "encryptionKey=" + encryptionKey + "\r\n";
    }

    if (encryptionAlgorithm != null) {
        request += "encryptionAlgorithm=" + encryptionAlgorithm
        + "\r\n";
    }

    if (firstConferenceMemberTreatment != null) {
            request += "firstConferenceMemberTreatment="
        + firstConferenceMemberTreatment + "\r\n";
    }

    if (forwardDtmfKeys == true) {
        request += "forwardDtmfKeys=true\r\n";
    }

    if (forwardingCallId != null) {
        request += "forwardDataFrom=" + forwardingCallId + "\r\n";
    }

    if (handleSessionProgress == true) {
        request += "handleSessionProgress=true\r\n";
    }

    if (remoteMediaInfo != null) {
        request +=
        "remoteMediaInfo=" + remoteMediaInfo + "\r\n";
    }

    if (isMuted == true) {
        request += "mute=true\r\n";
    }

    if (isMuteWhisperGroup == true) {
        request += "muteWhisperGroup=true\r\n";
    }

        if (isConferenceMuted == true) {
        request += "muteConference=true\r\n";
    }

        if (isConferenceSilenced == true) {
            request += "SilenceMainConference=true\r\n";
        }

        if (isRecorder == true) {
            request += "recorder=true\r\n";
        }

    if (joinConfirmationTimeout != 0) {
        request += "joinConfirmationTimeout="
        + joinConfirmationTimeout + "\r\n";
        }

    if (useConferenceReceiverThread == true) {
        request += "useConferenceReceiverThread=true\r\n";
    }

    if (mixMinus == false) {
        request += "mixMinus=false\r\n";
    }

        // decide whether to migrate or just call
    if (phoneNumber != null) {
            if (migrateCall) {
                request += "migrate=" + callId + ":" + phoneNumber + "\r\n";
            } else {
                request += "phoneNumber=" + phoneNumber + "\r\n";
        }
        }

    if (phoneNumberLocation != null) {
            request += "phoneNumberLocation=" + phoneNumberLocation + "\r\n";
    }

    if (recordDirectory != null) {
        request += "recordDirectory=" + recordDirectory + "\r\n";
    }

    if (fromRecordingFile != null) {
        request += "recordFromMember=true:0:" + fromRecordingFile + "\r\n";
        }

    if (toRecordingFile != null) {
        request += "recordToMember=true:0:" + toRecordingFile + "\r\n";
        }

    if (name != null) {
            request += "name=" + name + "\r\n";
    }

    if (protocol != null) {
        request += "protocol=" + protocol + "\r\n";
    }

    if (remoteCallId != null) {
        request += "remoteCallId=" + remoteCallId + "\r\n";
    }

    if (sipProxy != null) {
        request += "sipProxy=" + sipProxy + "\r\n";
    }

    if (speexEncode == true) {
        request += "speexEncode=" + speexEncode + "\r\n";
    }

    if (whisperGroupId != null) {
        request += "whisperGroup=" + whisperGroupId + "\r\n";
    }

    if (voipGateway != null) {
        request += "voipGateway=" + voipGateway + "\r\n";
    }

    if (voiceDetection == true) {
        request += "voiceDetection=true\r\n";
        }

    if (voiceDetectionWhileMuted == true) {
        request += "voiceDetectionWhileMuted=true\r\n";
        }

        if (secondPartyNumber != null) {
            request += "secondPartyNumber=" + secondPartyNumber + "\r\n";
        }

        if (secondPartyName != null) {
            request += "secondPartyName=" + secondPartyName + "\r\n";
        }

        if (secondPartyTimeout != 0) {
            request += "secondPartyTimeout=" + secondPartyTimeout + "\r\n";
        }

        if (secondPartyCallEndTreatment != null) {
            request += "secondPartyCallEndTreatment="
            + secondPartyCallEndTreatment + "\r\n";
        }

        if (secondPartyCallId != null) {
            request += "secondPartyCallId=" + secondPartyCallId + "\r\n";
        }

    if (secondPartyTreatment != null) {
        request += "secondPartyTreatment=" + secondPartyTreatment + "\r\n";
    }

        if (secondPartyVoiceDetection == true) {
            request += "secondPartyVoiceDetection=true\r\n";
        }

        request += "\r\n";
    return request;
    }

    public String toConsiseString() {
    if (phoneNumber == null) {
        if (inputTreatment == null) {
            return "Anonymous";
        }

        if (name != null) {
        return name + "@" + inputTreatment;
        }

        return inputTreatment;
    }

    int end = phoneNumber.indexOf("@");

        if (end >= 0) {
        if (name == null || name.equals("Anonymous")) {
                return phoneNumber;
        }

        int start = 0;

        if (phoneNumber.indexOf("sip:") == 0) {
            start = phoneNumber.indexOf(":") + 1;
        }

        String s = name.replaceAll(" ", "_");

        if (s.equals(phoneNumber.substring(start, end))) {
        return phoneNumber;
        }
        }

        if (name == null || name.equals("") || name.equals(phoneNumber)) {
            return "Anonymous@" + phoneNumber;
        }

        return name + "@" + phoneNumber;
    }

    /**
     * String representation of this call participant
     * @return the string representation
     */
    public String toString() {
    return callId + "::" + toConsiseString();
    }

}
