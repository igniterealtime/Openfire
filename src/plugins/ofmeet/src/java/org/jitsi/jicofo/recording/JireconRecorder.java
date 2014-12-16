/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.recording;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jirecon.*;

import org.jitsi.jicofo.*;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.util.*;

import org.jivesoftware.smack.packet.*;

/**
 * Class implements {@link Recorder} using Jirecon recorder container.
 *
 * @author Pawel Domas
 */
public class JireconRecorder
    extends Recorder
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(JireconRecorder.class);

    /**
     * The name of the property which specifies the token used to authenticate
     * requests to enable media recording.
     */
    static final String MEDIA_RECORDING_TOKEN_PNAME
        = "org.jitsi.videobridge.MEDIA_RECORDING_TOKEN";

    /**
     * FIXME: not sure about that
     * Our room JID in form of room_name@muc_component/focus_nickname
     */
    private final String mucRoomJid;

    /**
     * Recording authentication token.
     */
    private final String token;

    /**
     * Recording status of the Jirecon component.
     */
    private JireconIq.Status status = JireconIq.Status.UNDEFINED;

    /**
     * Recording session identifier assigned by Jirecon.
     */
    private String recordingId;

    /**
     * Creates new instance of <tt>JireconRecorder</tt>.
     * @param mucRoomJid focus room jid in form of
     *                   "room_name@muc_component/focus_nickname".
     * @param recorderComponentJid recorder component address.
     * @param xmpp {@link OperationSetDirectSmackXmpp} instance for current
     *             XMPP connection.
     */
    public JireconRecorder(String mucRoomJid, String recorderComponentJid,
                           OperationSetDirectSmackXmpp xmpp)
    {
        super(recorderComponentJid, xmpp);

        this.mucRoomJid = mucRoomJid;
        this.token
            = FocusBundleActivator.getConfigService()
                    .getString(MEDIA_RECORDING_TOKEN_PNAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRecording()
    {
        return JireconIq.Status.INITIATING == status
            || JireconIq.Status.STARTED == status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setRecording(
            String from, String token, boolean doRecord, String path)
    {
        if (!StringUtils.isNullOrEmpty(this.token)
            && !this.token.equals(token))
        {
            return false;
        }

        if (!isRecording() && doRecord)
        {
            // Send start recording IQ
            JireconIq recording = new JireconIq();

            recording.setTo(recorderComponentJid);
            recording.setType(IQ.Type.SET);
            recording.setFrom(from);

            recording.setMucJid(mucRoomJid);
            recording.setAction(JireconIq.Action.START);
            recording.setOutput(path);

            Packet reply
                = xmpp.getXmppConnection().sendPacketAndGetReply(recording);
            if (reply instanceof JireconIq)
            {
                JireconIq recResponse = (JireconIq) reply;
                if (JireconIq.Status.INITIATING.equals(recResponse.getStatus()))
                {
                    recordingId = recResponse.getRid();
                    logger.info("Received recording ID: " + recordingId);
                    status = JireconIq.Status.INITIATING;
                }
                else
                {
                    logger.error(
                        "Unexpected status received: " + recResponse.toXML());
                }
            }
            else
            {
                logger.error("Unexpected response: " + reply.toXML());
            }
        }
        else if (isRecording() && !doRecord)
        {
            // Send stop recording IQ
            JireconIq recording = new JireconIq();

            recording.setTo(recorderComponentJid);
            recording.setType(IQ.Type.SET);
            recording.setFrom(from);

            recording.setRid(recordingId);
            recording.setMucJid(mucRoomJid);
            recording.setAction(JireconIq.Action.STOP);

            xmpp.getXmppConnection().sendPacket(recording);

            status = JireconIq.Status.STOPPING;
        }

        return true;
    }

    /**
     * Accepts Jirecon packets.
     *
     * {@inheritDoc}
     */
    @Override
    public boolean accept(Packet packet)
    {
        return packet instanceof JireconIq;
    }

    /**
     * Jirecon packets processing logic.
     *
     * {@inheritDoc}
     */
    @Override
    public void processPacket(Packet packet)
    {
        JireconIq recording = (JireconIq) packet;

        if (JireconIq.Action.INFO != recording.getAction()
            && IQ.Type.RESULT == recording.getType()
            || StringUtils.isNullOrEmpty(recording.getRid()))
        {
            logger.warn("Discarded: " + recording.toXML());
            return;
        }

        if (!recording.getRid().equals(recordingId))
        {
            logger.warn(
                "Received IQ for unknown session: " + recording.toXML());
            return;
        }

        if (status != recording.getStatus())
        {
            status = recording.getStatus();

            logger.info("Recording " + recordingId + " status: " + status);

            if (status == JireconIq.Status.STOPPED)
            {
                logger.info("Recording STOPPED: " + recordingId);
                recordingId = null;
            }
        }
        else
        {
            logger.info("Ignored status change: " + recording.toXML());
        }
    }
}
