/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.Logger;

import org.jitsi.jicofo.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;
import org.xmpp.component.*;
import org.xmpp.packet.IQ;

/**
 * XMPP component that listens for {@link org.jitsi.jicofo.xmpp.ConferenceIq}
 * and allocates {@link org.jitsi.jicofo.JitsiMeetConference}s appropriately.
 *
 * @author Pawel Domas
 */
public class FocusComponent
    extends AbstractComponent
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(FocusComponent.class);

    /**
     * Name of system and configuration property which specifies the JID from
     * which shutdown requests will be accepted.
     */
    public static final String SHUTDOWN_ALLOWED_JID_PNAME
        = "org.jitsi.focus.shutdown.ALLOWED_JID";

    /**
     * The JID from which shutdown request are accepted.
     */
    private final String shutdownAllowedJid;

    /**
     * Indicates if the focus is anonymous user or authenticated system admin.
     */
    private final boolean isFocusAnonymous;

    /**
     * The JID of focus user that will enter the MUC room. Can be user to
     * recognize real focus of the conference.
     */
    private final String focusAuthJid;

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
     * @param focusAuthJid the JID of authenticated focus user which will be
     *                     advertised to conference participants.
     */
    public FocusComponent(boolean anonymousFocus, String focusAuthJid)
    {
        this.isFocusAnonymous = anonymousFocus;
        this.focusAuthJid = focusAuthJid;
        this.shutdownAllowedJid
            = FocusBundleActivator.getConfigService()
                    .getString(SHUTDOWN_ALLOWED_JID_PNAME);

        new ConferenceIqProvider();
        new ColibriIQProvider();
    }

    /**
     * Initializes this component.
     */
    public void init()
    {
        this.focusManager = ServiceUtils.getService(
            FocusBundleActivator.bundleContext, FocusManager.class);

        focusManager.start();
    }

    /**
     * Releases resources used by this instance.
     */
    public void dispose()
    {
        focusManager.stop();
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
    protected IQ handleIQGet(IQ iq)
        throws Exception
    {
        try
        {
            org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);
            if (smackIq instanceof ColibriStatsIQ)
            {
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
            logger.error(e, e);
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
    public IQ handleIQSet(IQ iq)
        throws Exception
    {
        try
        {
            org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);

            if (smackIq instanceof ConferenceIq)
            {
                ConferenceIq query = (ConferenceIq) smackIq;
                ConferenceIq response = new ConferenceIq();
                String room = query.getRoom();

                logger.info("Focus request for room: " + room);

                if (focusManager.isShutdownInProgress()
                    && focusManager.getConference(room) == null)
                {
                    // Service unavailable
                    org.jivesoftware.smack.packet.IQ smackReply
                        = ColibriConferenceIQ
                            .createGracefulShutdownErrorResponse(query);
                    // Fix error responses
                    return IQUtils.convert(smackReply);
                }

                boolean ready
                    = focusManager.conferenceRequest(
                            room, query.getPropertiesMap());

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
                response.setFocusJid(focusAuthJid);

                return IQUtils.convert(response);
            }
            else if (smackIq instanceof GracefulShutdownIQ)
            {
                GracefulShutdownIQ gracefulShutdownIQ
                    = (GracefulShutdownIQ) smackIq;

                String from = gracefulShutdownIQ.getFrom();
                if (StringUtils.isNullOrEmpty(shutdownAllowedJid)
                    || !shutdownAllowedJid.equals(from))
                {
                    // Forbidden
                    XMPPError forbiddenError
                        = new XMPPError(XMPPError.Condition.forbidden);

                    logger.warn("Rejected shutdown request from: " + from);

                    return IQUtils.convert(
                        org.jivesoftware.smack.packet.IQ.createErrorResponse(
                                smackIq, forbiddenError));
                }

                logger.info("Accepted shutdown request from: " + from);

                focusManager.enableGracefulShutdownMode();

                return IQUtils.convert(
                    org.jivesoftware.smack.packet.IQ.createResultIQ(smackIq));
            }
            else
            {
                return super.handleIQSet(iq);
            }
        }
        catch (Exception e)
        {
            logger.error(e, e);
            throw e;
        }
    }
}
