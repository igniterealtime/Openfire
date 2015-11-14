/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.openfire;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.util.*;

import org.slf4j.*;
import org.slf4j.Logger;

import org.jitsi.jicofo.*;
import org.jitsi.jicofo.osgi.*;
import org.jitsi.jicofo.xmpp.ConferenceIq;
import org.jitsi.jicofo.xmpp.ConferenceIqProvider;
import org.jitsi.jicofo.xmpp.IQUtils;
import org.jitsi.util.*;

import org.jivesoftware.smack.packet.*;

import org.osgi.framework.*;
import org.xmpp.component.*;
import org.xmpp.packet.IQ;


public class FocusComponent extends AbstractComponent
{
    private static final Logger Log = LoggerFactory.getLogger(FocusComponent.class);

    /**
     * Indicates if the focus is anonymous user or authenticated system admin.
     */
    private final boolean isFocusAnonymous;

    /**
     * Optional password for focus user authentication. If authenticated login
     * is used we expect focus user to have admin privileges, so that it has
     * explicit moderator rights. Also in this case focus component will always
     * return 'ready=true' status, so that users don't have to wait for
     * the focus to create the room. If focus is authenticated and is not
     * an admin then will refuse to join MUC room.
     */

    /**
     * The manager object that creates and expires
     * {@link org.jitsi.jicofo.JitsiMeetConference}s.
     */
    private FocusManager focusManager;

    /**
     * Creates new instance of <tt>FocusComponent</tt>.
     * @param anonymousFocus indicates if the focus user is anonymous.
     */
    public FocusComponent(boolean anonymousFocus)
    {
        this.isFocusAnonymous = anonymousFocus;

        new ConferenceIqProvider();
        new ColibriIQProvider();
    }

    /**
     * Initializes this component.
     */
    public void init()
    {
		Log.info("Focus Manager init");
        this.focusManager = ServiceUtils.getService(FocusBundleActivator.bundleContext, FocusManager.class);
        focusManager.start();
    }

    /**
     * Releases resources used by this instance.
     */
    public void dispose()
    {
        focusManager.stop();
    }

    /**
     * Called as part of the execution of {@link AbstractComponent#shutdown()}
     * to enable this <tt>Component</tt> to finish cleaning resources up after
     * it gets completely shutdown.
     *
     * @see AbstractComponent#postComponentShutdown()
     */
    @Override
    public void postComponentShutdown()
    {
        super.postComponentShutdown();

        OSGi.stop();

        dispose();
    }

    /**
     * Called as part of the execution of {@link AbstractComponent#start()} to
     * enable this <tt>Component</tt> to finish initializing resources after it
     * gets completely started.
     *
     * @see AbstractComponent#postComponentStart()
     */
    @Override
    public void postComponentStart()
    {
        super.postComponentStart();

        FocusBundleActivator activator = new FocusBundleActivator()
		{
			@Override
			public void start(BundleContext bundleContext)  throws Exception
			{

			}

			@Override
			public void stop(BundleContext bundleContext) throws Exception
			{
				// TODO Auto-generated method stub
			}
		};

        OSGi.start(activator);

        init();
    }

    @Override
    public String getDescription()
    {
        return "Manages Jitsi Meet conferences";
    }

    @Override
    public String getName()
    {
        return "Jitsi Meet Focus";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] discoInfoFeatureNamespaces()
    {
        return
            new String[]
                {
                    ConferenceIq.NAMESPACE
                };
    }

    @Override
    protected IQ handleIQGet(IQ iq) throws Exception
    {
		Log.info("handleIQGet\n" + iq);
        try
        {
            org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);

            if (smackIq instanceof ColibriStatsIQ)
            {
				Log.info("handleIQGet ColibriStatsIQ " + smackIq);

                // Supports only empty colibri queries
                ColibriStatsIQ colibriStatsQuery
                    = (ColibriStatsIQ) smackIq;

                // Reply with stats
                ColibriStatsIQ statsReply = new ColibriStatsIQ();

                statsReply.setType(
                    org.jivesoftware.smack.packet.IQ.Type.RESULT);
                statsReply.setPacketID(iq.getID());
                statsReply.setTo(iq.getFrom().toString());

                int conferenceCount = focusManager.getConferenceCount();

                // Return conference count
                statsReply.addStat(
                    new ColibriStatsExtension.Stat(
                        "conferences",
                        Integer.toString(conferenceCount)));
                statsReply.addStat(
                    new ColibriStatsExtension.Stat(
                        "graceful_shutdown",
                        focusManager.isShutdownInProgress()
                                ? "true" : "false"));

                return IQUtils.convert(statsReply);
            }
            else
            {
                return super.handleIQGet(iq);
            }
        }
        catch (Exception e)
        {
            Log.error("handleIQGet", e);
            throw e;
        }
    }

    /**
     * Handles an <tt>org.xmpp.packet.IQ</tt> stanza of type <tt>set</tt> which
     * represents a request.
     *
     * @param iq the <tt>org.xmpp.packet.IQ</tt> stanza of type <tt>set</tt>
     * which represents the request to handle
     * @return an <tt>org.xmpp.packet.IQ</tt> stanza which represents the
     * response to the specified request or <tt>null</tt> to reply with
     * <tt>feature-not-implemented</tt>
     * @throws Exception to reply with <tt>internal-server-error</tt> to the
     * specified request
     * @see AbstractComponent#handleIQSet(IQ)
     */
    @Override
    public IQ handleIQSet(IQ iq)  throws Exception
    {
		Log.info("handleIQSet\n" + iq);

        try
        {
            org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);

            if (focusManager != null && smackIq instanceof ConferenceIq)
            {
                ConferenceIq query = (ConferenceIq) smackIq;
                ConferenceIq response = new ConferenceIq();
                String room = query.getRoom();

                Log.info("Focus request for room: " + room);

                if (focusManager.isShutdownInProgress() && focusManager.getConference(room) == null)
                {
                    // Service unavailable
                    org.jivesoftware.smack.packet.IQ smackReply  = ColibriConferenceIQ.createGracefulShutdownErrorResponse(query);
                    // Fix error responses
                    return IQUtils.convert(smackReply);
                }

                boolean ready = focusManager.conferenceRequest(room, query.getPropertiesMap());

                if (!isFocusAnonymous)
                {
                    // Focus is authenticated system admin, so we let
                    // them in immediately. Focus will get OWNER anyway.
                    ready = true;
                }

                response.setType(org.jivesoftware.smack.packet.IQ.Type.RESULT);
                response.setPacketID(query.getPacketID());
                response.setFrom(query.getTo());
                response.setTo(query.getFrom());
                response.setRoom(query.getRoom());
                response.setReady(ready);

                return IQUtils.convert(response);
            }
            else
            {
                return super.handleIQSet(iq);
            }
        }
        catch (Exception e)
        {
            Log.error("handleIQSet", e);
            throw e;
        }
    }
}
