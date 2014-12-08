/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.log;

import org.jitsi.videobridge.log.*;

/**
 * A utility class with static methods which initialize <tt>Event</tt> instances
 * with pre-determined fields.
 *
 * @author Boris Grozev
 */
public class LogEventFactory
{
    /**
     * The names of the columns of a "focus created" event.
     */
    private static final String[] FOCUS_CREATED_COLUMNS
            = new String[]
            {
                    "room_jid"
            };

    /**
     * The names of the columns of an "endpoint display name" event.
     */
    private static final String[] ENDPOINT_DISPLAY_NAME_COLUMNS
            = new String[]
            {
                    "conference_id",
                    "endpoint_id",
                    "display_name"
            };

    /**
     * The names of the columns of a "peer connection stats" event.
     */
    private static final String[] PEER_CONNECTION_STATS_COLUMNS
            = new String[]
            {
                    "conference_id",
                    "endpoint_id",
                    "stats"
            };

    /**
     * The names of the columns of a "conference room" event.
     */
    private static final String[] CONFERENCE_ROOM_COLUMNS
            = new String[]
            {
                    "conference_id",
                    "room_jid"
            };

    /**
     * Creates a new "focus created" <tt>Event</tt>.
     * @param roomJid the JID of the MUC for which the focus was created.
     *
     * @return the <tt>Event</tt> which was created.
     */
    public static Event focusCreated(
            String roomJid)
    {
        return new Event("focus_created",
                         FOCUS_CREATED_COLUMNS,
                         new Object[]
                                 {
                                         roomJid,
                                 });
    }

    /**
     * Creates a new "endpoint display name changed" <tt>Event</tt>, which
     * conference ID to the JID of the associated MUC.
     *
     * @param conferenceId the ID of the COLIBRI conference.
     * @param endpointId the ID of the COLIBRI endpoint.
     * @param displayName the new display name.
     *
     * @return the <tt>Event</tt> which was created.
     */
    public static Event endpointDisplayNameChanged(
            String conferenceId,
            String endpointId,
            String displayName)
    {
        return new Event("endpoint_display_name",
                         ENDPOINT_DISPLAY_NAME_COLUMNS,
                         new Object[]
                                 {
                                         conferenceId,
                                         endpointId,
                                         displayName
                                 });
    }

    public static Event peerConnectionStats(
            String conferenceId,
            String endpointId,
            String stats)
    {
        return new Event("peer_connection_stats",
                         PEER_CONNECTION_STATS_COLUMNS,
                         new Object[]
                                 {
                                         conferenceId,
                                         endpointId,
                                         stats
                                 });
    }


    /**
     * Creates a new "room conference" <tt>Event</tt> which binds a COLIBRI
     * conference ID to the JID of the associated MUC.
     *
     * @param conferenceId the ID of the COLIBRI conference.
     * @param roomJid the JID of the MUC for which the focus was created.
     *
     * @return the <tt>Event</tt> which was created.
     */
    public static Event conferenceRoom(
            String conferenceId,
            String roomJid)
    {
        return new Event("conference_room",
                         CONFERENCE_ROOM_COLUMNS,
                         new Object[]
                                 {
                                         conferenceId,
                                         roomJid
                                 });
    }
}

