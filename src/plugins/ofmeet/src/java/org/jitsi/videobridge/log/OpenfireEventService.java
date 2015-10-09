/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge.log;

import org.jitsi.service.configuration.*;
import org.jitsi.util.*;
import org.json.simple.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;
import org.slf4j.Logger;

import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.util.*;
import org.xmpp.packet.*;


/**
 * Allows logging of {@link org.jitsi.videobridge.log.Event}s using an
 * <tt>Openfire</tt> instance.
 *
 */
public class OpenfireEventService implements LoggingService
{
    private static final Logger Log = LoggerFactory.getLogger(OpenfireEventService.class);

    /**
     * The <tt>Executor</tt> which is to perform the task of sending data to
     * <tt>Openfire</tt>.
     */
    private final Executor executor  = ExecutorUtils.newCachedThreadPool(true, OpenfireEventService.class.getName());


    /**
     * Initializes a new <tt>OpenfireEventService</tt> instance, by reading
     * its configuration from <tt>cfg</tt>.
     * @param cfg the <tt>ConfigurationService</tt> to use.
     *
     * @throws Exception if initialization fails
     */
    OpenfireEventService() throws Exception
    {
        Log.info("Initialized OpenfireEventService");
    }

    /**
     * Logs an <tt>Event</tt> to an <tt>InfluxDB</tt> database. This method
     * returns without blocking, the blocking operations are performed in
     * by a thread from {@link #executor}.
     *
     * @param e the <tt>Event</tt> to log.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void logEvent(final Event e)
    {
        // The following is a sample JSON message in the format used by InfluxDB
        //  [
        //    {
        //     "name": "series_name",
        //     "columns": ["column1", "column2"],
        //     "points": [["value1", 1234]]
        //    }
        //  ]

		if ("focus_created".equals(e.getName())) return;

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", e.getName());

        JSONArray columns = new JSONArray();
        JSONArray point = new JSONArray();

        if (e.useLocalTime())
        {
            columns.add("time");
            point.add(System.currentTimeMillis());
        }

        Collections.addAll(columns, e.getColumns());
        Collections.addAll(point, e.getValues());

        jsonObject.put("columns", columns);

        JSONArray points = new JSONArray();
        points.add(point);
        jsonObject.put("points", points);

        JSONArray jsonArray = new JSONArray();
        jsonArray.add(jsonObject);

        // TODO: this is probably a good place to optimize by grouping multiple
        // events in a single POST message and/or multiple points for events
        // of the same type together).

        final String jsonString = jsonArray.toJSONString();

        executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                sendPost(e.getName(), jsonString);
            }
        });
    }

    /**
     * Sends the string <tt>s</tt> as the contents of an HTTP POST request to
     * {@link #url}.
     * @param s the content of the POST request.
     */
    private void sendPost(final String summary, final String detail)
    {
        try
        {
			String focusUsername = "focus";
			String focusUserJid = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.focus.user.jid");

			if (focusUserJid != null)
			{
				focusUsername = (new JID(focusUserJid)).getNode();
			}
			Log.info("OpenfireEventService sendPost " + focusUsername + " " + detail);
			SecurityAuditManager.getInstance().logEvent(focusUsername, summary, detail);
        }
        catch (Exception e)
        {
            Log.error("Failed to send event to Openfire: " + e);
        }
    }
}
