/**
 * 
 */
package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import java.util.logging.Logger;

import ymsg.network.event.SessionChatEvent;
import ymsg.network.event.SessionConferenceEvent;
import ymsg.network.event.SessionErrorEvent;
import ymsg.network.event.SessionEvent;
import ymsg.network.event.SessionExceptionEvent;
import ymsg.network.event.SessionFileTransferEvent;
import ymsg.network.event.SessionFriendEvent;
import ymsg.network.event.SessionListener;
import ymsg.network.event.SessionNewMailEvent;
import ymsg.network.event.SessionNotifyEvent;


/**
 * NoopSessionListener provides a mechanism to quickly create a SessionListener
 * for the ymsg9 library.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public class NoopSessionListener implements SessionListener {

    
    /** The logger. @see java.util.logging.Logger */
    private static final Logger logger = Logger.getLogger("NoopSessionListener");
    
    /**
     * @see ymsg.network.event.SessionListener#fileTransferReceived(ymsg.network.event.SessionFileTransferEvent)
     */
    /**
     * @see ymsg.network.event.SessionListener#fileTransferReceived(ymsg.network.event.SessionFileTransferEvent)
     */
    /**
     * @see ymsg.network.event.SessionListener#fileTransferReceived(ymsg.network.event.SessionFileTransferEvent)
     */
    public void fileTransferReceived(SessionFileTransferEvent arg0) {
		logger.info(arg0.toString());
	}

	/**
	 * @see ymsg.network.event.SessionListener#connectionClosed(ymsg.network.event.SessionEvent)
	 */
	public void connectionClosed(SessionEvent arg0) {
		logger.info(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#listReceived(ymsg.network.event.SessionEvent)
	 */
	public void listReceived(SessionEvent arg0) {
		logger.info(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#messageReceived(ymsg.network.event.SessionEvent)
	 */
	public void messageReceived(SessionEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#buzzReceived(ymsg.network.event.SessionEvent)
	 */
	public void buzzReceived(SessionEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#offlineMessageReceived(ymsg.network.event.SessionEvent)
	 */
	public void offlineMessageReceived(SessionEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#errorPacketReceived(ymsg.network.event.SessionErrorEvent)
	 */
	public void errorPacketReceived(SessionErrorEvent arg0) {
		logger.severe(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#inputExceptionThrown(ymsg.network.event.SessionExceptionEvent)
	 */
	public void inputExceptionThrown(SessionExceptionEvent arg0) {
		arg0.getException().printStackTrace();
        logger.severe(arg0.toString());
	}

	/**
	 * @see ymsg.network.event.SessionListener#newMailReceived(ymsg.network.event.SessionNewMailEvent)
	 */
	public void newMailReceived(SessionNewMailEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#notifyReceived(ymsg.network.event.SessionNotifyEvent)
	 */
	public void notifyReceived(SessionNotifyEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#contactRequestReceived(ymsg.network.event.SessionEvent)
	 */
	public void contactRequestReceived(SessionEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#contactRejectionReceived(ymsg.network.event.SessionEvent)
	 */
	public void contactRejectionReceived(SessionEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#conferenceInviteReceived(ymsg.network.event.SessionConferenceEvent)
	 */
	public void conferenceInviteReceived(SessionConferenceEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#conferenceInviteDeclinedReceived(ymsg.network.event.SessionConferenceEvent)
	 */
	public void conferenceInviteDeclinedReceived(SessionConferenceEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#conferenceLogonReceived(ymsg.network.event.SessionConferenceEvent)
	 */
	public void conferenceLogonReceived(SessionConferenceEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#conferenceLogoffReceived(ymsg.network.event.SessionConferenceEvent)
	 */
	/**
	 * @see ymsg.network.event.SessionListener#conferenceLogoffReceived(ymsg.network.event.SessionConferenceEvent)
	 */
	public void conferenceLogoffReceived(SessionConferenceEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#conferenceMessageReceived(ymsg.network.event.SessionConferenceEvent)
	 */
	public void conferenceMessageReceived(SessionConferenceEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#friendsUpdateReceived(ymsg.network.event.SessionFriendEvent)
	 */
	public void friendsUpdateReceived(SessionFriendEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#friendAddedReceived(ymsg.network.event.SessionFriendEvent)
	 */
	public void friendAddedReceived(SessionFriendEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#friendRemovedReceived(ymsg.network.event.SessionFriendEvent)
	 */
	public void friendRemovedReceived(SessionFriendEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#chatLogonReceived(ymsg.network.event.SessionChatEvent)
	 */
	public void chatLogonReceived(SessionChatEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#chatLogoffReceived(ymsg.network.event.SessionChatEvent)
	 */
	public void chatLogoffReceived(SessionChatEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#chatMessageReceived(ymsg.network.event.SessionChatEvent)
	 */
	public void chatMessageReceived(SessionChatEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#chatUserUpdateReceived(ymsg.network.event.SessionChatEvent)
	 */
	public void chatUserUpdateReceived(SessionChatEvent arg0) {
		logger.finest(arg0.toString());
		
	}

	/**
	 * @see ymsg.network.event.SessionListener#chatConnectionClosed(ymsg.network.event.SessionEvent)
	 */
	public void chatConnectionClosed(SessionEvent arg0) {
		logger.finest(arg0.toString());
		
	}
}
