/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.util.*;

import org.jitsi.jicofo.*;
import org.jitsi.protocol.xmpp.util.*;

import org.jivesoftware.smack.packet.*;

import java.util.*;

/**
 * Class provides template implementation of {@link OperationSetJingle}.
 *
 * @author Pawel Domas
 */
public abstract class AbstractOperationSetJingle
    implements OperationSetJingle
{
    /**
     * The logger.
     */
    private static final Logger logger
        = Logger.getLogger(AbstractOperationSetJingle.class);

    /**
     * The list of active Jingle session.
     */
    protected Map<String, JingleSession> sessions
        = new HashMap<String, JingleSession>();

    protected JingleRequestHandler requestHandler;

    /**
     * Implementing classes should return our JID here.
     */
    protected abstract String getOurJID();

    /**
     * Returns {@link XmppConnection} implementation.
     */
    protected abstract XmppConnection getConnection();

    /**
     * Finds Jingle session for given session identifier.
     *
     * @param sid the identifier of the session for which we're looking for.
     *
     * @return Jingle session for given session identifier or <tt>null</tt>
     *         if no such session exists.
     */
    protected JingleSession getSession(String sid)
    {
        return sessions.get(sid);
    }

    /**
     * Sets the {@link JingleRequestHandler} that will be associated with this
     * instance.
     *
     * @param jingleHandler {@link JingleRequestHandler} object that will be
     *                      receiving notifications from this instance.
     */
    @Override
    public void setRequestHandler(JingleRequestHandler jingleHandler)
    {
        this.requestHandler = jingleHandler;
    }

    /**
     * Sends 'session-initiate' to the peer identified by given <tt>address</tt>
     *
     * @param useBundle <tt>true</tt> if invite IQ should include
     *                  {@link GroupPacketExtension}
     * @param address the XMPP address where 'session-initiate' will be sent.
     * @param contents the list of <tt>ContentPacketExtension</tt> describing
     *                 media offer.
     */
    @Override
    public void initiateSession(boolean useBundle,
                                String address,
                                List<ContentPacketExtension> contents)
    {
        logger.info("INVITE PEER: " + address);

        String sid = JingleIQ.generateSID();

        JingleSession session = new JingleSession(sid, address);

        sessions.put(sid, session);

        JingleIQ inviteIQ
            = JinglePacketFactory.createSessionInitiate(
                    getOurJID(),
                    address,
                    sid,
                    contents);

        if (useBundle)
        {
            GroupPacketExtension group
                = GroupPacketExtension.createBundleGroup(contents);

            inviteIQ.addExtension(group);

            for (ContentPacketExtension content : contents)
            {
                // FIXME: is it mandatory ?
                // http://estos.de/ns/bundle
                content.addChildExtension(
                    new BundlePacketExtension());
            }
        }

        getConnection().sendPacket(inviteIQ);
    }

    /**
     * The logic for processing received JingleIQs.
     *
     * @param iq the <tt>JingleIQ</tt> to process.
     */
    protected void processJingleIQ(JingleIQ iq)
    {
        JingleSession session = getSession(iq.getSID());
        JingleAction action = iq.getAction();

        if (session == null)
        {
            logger.error(
                "Action: " + action
                    + ", no session found for SID " + iq.getSID());
            return;
        }

        if (requestHandler == null)
        {
            logger.error("No request handler set.");
            return;
        }

        if (JingleAction.SESSION_ACCEPT.equals(action))
        {
            requestHandler.onSessionAccept(
                session, iq.getContentList());
        }
        else if (JingleAction.TRANSPORT_INFO.equals(action))
        {
            requestHandler.onTransportInfo(
                session, iq.getContentList());
        }
        else if (JingleAction.ADDSOURCE.equals(action)
            || JingleAction.SOURCEADD.equals(action))
        {
            requestHandler.onAddSource(session, iq.getContentList());
        }
        else if (JingleAction.REMOVESOURCE.equals(action)
            || JingleAction.SOURCEREMOVE.equals(action))
        {
            requestHandler.onRemoveSource(session, iq.getContentList());
        }
        else
        {
            logger.warn("unsupported action " + action);
        }
    }

    /**
     * Sends 'source-add' notification to the peer of given
     * <tt>JingleSession</tt>.
     *
     * @param ssrcs the map of media SSRCs that will be included in
     *              the notification.
     * @param ssrcGroupMap the map of media SSRC groups that will be included in
     *                     the notification.
     * @param session the <tt>JingleSession</tt> used to send the notification.
     */
    @Override
    public void sendAddSourceIQ(MediaSSRCMap ssrcs,
                                MediaSSRCGroupMap ssrcGroupMap,
                                JingleSession session)
    {
        JingleIQ addSourceIq = new JingleIQ();

        addSourceIq.setAction(JingleAction.SOURCEADD);
        addSourceIq.setFrom(getOurJID());
        addSourceIq.setType(IQ.Type.SET);

        for (String media : ssrcs.getMediaTypes())
        {
            ContentPacketExtension content
                = new ContentPacketExtension();

            content.setName(media);

            RtpDescriptionPacketExtension rtpDesc
                = new RtpDescriptionPacketExtension();

            rtpDesc.setMedia(media);

            content.addChildExtension(rtpDesc);

            for (SourcePacketExtension ssrc : ssrcs.getSSRCsForMedia(media))
            {
                try
                {
                    rtpDesc.addChildExtension(
                        ssrc.copy());
                }
                catch (Exception e)
                {
                    logger.error("Copy SSRC error", e);
                }
            }

            addSourceIq.addContent(content);
        }

        if (ssrcGroupMap != null)
        {
            for (String media : ssrcGroupMap.getMediaTypes())
            {
                ContentPacketExtension content
                    = addSourceIq.getContentByName(media);
                RtpDescriptionPacketExtension rtpDesc;

                if (content == null)
                {
                    // It means content was not created when adding SSRCs...
                    logger.warn(
                        "No SSRCs to be added when group exists for media: "
                            + media);

                    content = new ContentPacketExtension();
                    content.setName(media);
                    addSourceIq.addContent(content);

                    rtpDesc = new RtpDescriptionPacketExtension();
                    rtpDesc.setMedia(media);
                    content.addChildExtension(rtpDesc);
                }
                else
                {
                    rtpDesc = content.getFirstChildOfType(
                        RtpDescriptionPacketExtension.class);
                }

                for (SSRCGroup ssrcGroup
                    : ssrcGroupMap.getSSRCGroupsForMedia(media))
                {
                    try
                    {
                        rtpDesc.addChildExtension(ssrcGroup.getExtensionCopy());
                    }
                    catch (Exception e)
                    {
                        logger.error("Copy SSRC GROUP error", e);
                    }
                }
            }
        }

        String peerSid = session.getSessionID();

        addSourceIq.setTo(session.getAddress());
        addSourceIq.setSID(peerSid);

        logger.info("Notify add SSRC" + session.getAddress()
                        + " SID: " + peerSid);

        getConnection().sendPacket(addSourceIq);
    }

    /**
     * Sends 'source-remove' notification to the peer of given
     * <tt>JingleSession</tt>.
     *
     * @param ssrcs the map of media SSRCs that will be included in
     *              the notification.
     * @param ssrcGroupMap the map of media SSRC groups that will be included in
     *                     the notification.
     * @param session the <tt>JingleSession</tt> used to send the notification.
     */
    @Override
    public void sendRemoveSourceIQ(MediaSSRCMap ssrcs,
                                   MediaSSRCGroupMap ssrcGroupMap,
                                   JingleSession session)
    {
        JingleIQ removeSourceIq = new JingleIQ();

        removeSourceIq.setAction(JingleAction.SOURCEREMOVE);
        removeSourceIq.setFrom(getOurJID());
        removeSourceIq.setType(IQ.Type.SET);

        for (String media : ssrcs.getMediaTypes())
        {
            ContentPacketExtension content
                = new ContentPacketExtension();

            content.setName(media);

            RtpDescriptionPacketExtension rtpDesc
                = new RtpDescriptionPacketExtension();
            rtpDesc.setMedia(media);

            content.addChildExtension(rtpDesc);

            for (SourcePacketExtension ssrc : ssrcs.getSSRCsForMedia(media))
            {
                try
                {
                    rtpDesc.addChildExtension(
                        ssrc.copy());
                }
                catch (Exception e)
                {
                    logger.error("Copy SSRC error", e);
                }
            }

            removeSourceIq.addContent(content);
        }

        if (ssrcGroupMap != null)
        {
            for (String media : ssrcGroupMap.getMediaTypes())
            {
                ContentPacketExtension content
                    = removeSourceIq.getContentByName(media);
                RtpDescriptionPacketExtension rtpDesc;

                if (content == null)
                {
                    // It means content was not created when adding SSRCs...
                    logger.warn(
                        "No SSRCs to be removed when group exists for media: "
                            + media);

                    content = new ContentPacketExtension();
                    content.setName(media);
                    removeSourceIq.addContent(content);

                    rtpDesc = new RtpDescriptionPacketExtension();
                    rtpDesc.setMedia(media);
                    content.addChildExtension(rtpDesc);
                }
                else
                {
                    rtpDesc = content.getFirstChildOfType(
                        RtpDescriptionPacketExtension.class);
                }

                for (SSRCGroup ssrcGroup
                    : ssrcGroupMap.getSSRCGroupsForMedia(media))
                {
                    try
                    {
                        rtpDesc.addChildExtension(ssrcGroup.getExtensionCopy());
                    }
                    catch (Exception e)
                    {
                        logger.error("Copy SSRC GROUP error", e);
                    }
                }
            }
        }

        String peerSid = session.getSessionID();

        removeSourceIq.setTo(session.getAddress());
        removeSourceIq.setSID(peerSid);

        logger.info("Notify remove SSRC " + session.getAddress()
                        + " SID: " + peerSid);

        XmppConnection connection = getConnection();

        connection.sendPacket(removeSourceIq);
    }

    /**
     * Terminates given Jingle session by sending 'session-terminate' with some
     * {@link Reason} if provided.
     *
     * @param session the <tt>JingleSession</tt> to terminate.
     * @param reason one of {@link Reason} enum that indicates why the session
     *               is being ended or <tt>null</tt> to omit.
     */
    @Override
    public void terminateSession(JingleSession session, Reason reason)
    {
        logger.info("Terminate session: " + session.getAddress());

        JingleIQ terminate
            = JinglePacketFactory.createSessionTerminate(
                    getOurJID(),
                    session.getAddress(),
                    session.getSessionID(),
                    reason, null);

        getConnection().sendPacket(terminate);

        sessions.remove(session.getSessionID());
    }
}
