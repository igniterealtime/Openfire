/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.recording;

import org.jitsi.protocol.xmpp.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;

/**
 * Abstract class used by {@link org.jitsi.jicofo.JitsiMeetConference} for
 * controlling recording functionality.
 *
 * @author Pawel Domas
 */
public abstract class Recorder
    implements PacketListener,
               PacketFilter
{
    /**
     * Recorder component XMPP address.
     */
    protected final String recorderComponentJid;

    /**
     * Smack operation set for current XMPP connection.
     */
    protected final OperationSetDirectSmackXmpp xmpp;

    public Recorder(String recorderComponentJid,
                    OperationSetDirectSmackXmpp xmpp)
    {
        this.recorderComponentJid = recorderComponentJid;
        this.xmpp = xmpp;
        xmpp.addPacketHandler(this, this);
    }

    /**
     * Releases resources and stops any future processing.
     */
    public void dispose()
    {
        xmpp.removePacketHandler(this);
    }

    /**
     * Returns current conference recording status.
     * @return <tt>true</tt> if the conference is currently being recorded
     *         or <tt>false</tt> otherwise.
     */
    public abstract boolean isRecording();

    /**
     * Toggles recording status of the conference handled by this instance.
     *
     * @param from JID of the user that wants to modify recording status.
     * @param token recording security token(check by the implementation).
     * @param doRecord <tt>true</tt> to enable recording.
     * @param path output recording path(implementation specific).
     *
     * @return <tt>true</tt> if security token was successfully verified and
     *         appropriate control actions have been taken or <tt>false</tt>
     *         otherwise.
     */
    public abstract boolean setRecording(
        String from, String token, boolean doRecord, String path);
}
