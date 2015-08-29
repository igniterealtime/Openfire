/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.recording;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ.Recording.*;
import net.java.sip.communicator.util.*;

import org.jitsi.protocol.xmpp.*;

import org.jivesoftware.smack.packet.*;

/**
 * Implements {@link Recorder} using direct Colibri queries sent to
 * the videobridge.
 *
 * @author Pawel Domas
 */
public class JvbRecorder
    extends Recorder
{
    /**
     * The logger instance used by this class.
     */
    private final static Logger logger = Logger.getLogger(JvbRecorder.class);

    /**
     * Colibri conference identifier
     */
    private final String conferenceId;

    /**
     * Recording status.
     */
    boolean isRecording;

    /**
     * Creates new instance of <tt>JvbRecorder</tt>.
     * @param conferenceId colibri conference ID obtained when allocated
     *                     on the bridge
     * @param videoBridgeComponentJid videobridge component address.
     * @param xmpp {@link OperationSetDirectSmackXmpp}
     *              for current XMPP connection.
     */
    public JvbRecorder(String conferenceId,
                       String videoBridgeComponentJid,
                       OperationSetDirectSmackXmpp xmpp)
    {
        super(videoBridgeComponentJid, xmpp);

        this.conferenceId = conferenceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRecording()
    {
        return isRecording;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setRecording(String from, String token,
                                boolean doRecord, String path)
    {
        ColibriConferenceIQ toggleRecordingIq = new ColibriConferenceIQ();

        toggleRecordingIq.setID(conferenceId);
        toggleRecordingIq.setTo(recorderComponentJid);
        toggleRecordingIq.setType(IQ.Type.SET);

        toggleRecordingIq.setRecording(
            new ColibriConferenceIQ.Recording(
                !isRecording ? State.ON : State.OFF, token));
        Packet reply
            = xmpp.getXmppConnection()
                    .sendPacketAndGetReply(toggleRecordingIq);
        logger.info("REC reply received: " + reply.toXML());
        if (reply instanceof ColibriConferenceIQ)
        {
            ColibriConferenceIQ colibriReply = (ColibriConferenceIQ) reply;
            ColibriConferenceIQ.Recording recording
                = colibriReply.getRecording();
            if (recording != null)
            {
                isRecording = recording.getState().equals(State.ON)
                    || recording.getState().equals(State.PENDING);
                logger.info("REC status: " + conferenceId + ": " + isRecording);
            }
            else
            {
                // Recording token is invalid
                return false;
            }
        }
        else
        {
            logger.error(
                conferenceId
                    + " unexpected response received: " + reply.toXML());
        }
        return true;
    }

    @Override
    public boolean accept(Packet packet)
    {
        return false;
        /*if (!(packet instanceof ColibriConferenceIQ))
            return false;

        ColibriConferenceIQ colibriIQ = (ColibriConferenceIQ) packet;
        // Packets must be with <recording/> and from the bridge
        if (colibriIQ.getRecording() == null
                || !recorderComponentJid.equals(packet.getFrom()))
        {
            return false;
        }

        if (!conferenceId.equals(colibriIQ.getID()))
        {
            logger.warn(
                "Received colibri IQ from different conference: "
                        + colibriIQ.getID() + ", expected: " + conferenceId);
            return false;
        }

        return true;*/
    }

    @Override
    public void processPacket(Packet packet)
    {
        //FIXME: should notify the conference about status change,
        //       but currently all processing is done on the fly using
        //       XmppConnection.sendPacketAndGetReply

        /*ColibriConferenceIQ colibriIq = (ColibriConferenceIQ) packet;

        ColibriConferenceIQ.Recording recordingElem
            = colibriIq.getRecording();

        logger.info(
            conferenceId
                + " recording status from the bridge received: "
                + isRecording);

        isRecording = recordingElem.getState();*/
    }
}
