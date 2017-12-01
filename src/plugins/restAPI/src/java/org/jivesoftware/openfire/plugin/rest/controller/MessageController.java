package org.jivesoftware.openfire.plugin.rest.controller;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.plugin.rest.entity.MessageEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

/**
 * The Class MessageController.
 */
public class MessageController {
    /** The Constant INSTANCE. */
    public static final MessageController INSTANCE = new MessageController();

    /**
     * Gets the single instance of MessageController.
     *
     * @return single instance of MessageController
     */
    public static MessageController getInstance() {
        return INSTANCE;
    }

    /**
     * Send broadcast message.
     *
     * @param messageEntity
     *            the message entity
     * @throws ServiceException
     *             the service exception
     */
    public void sendBroadcastMessage(MessageEntity messageEntity) throws ServiceException {
        if (messageEntity.getBody() != null && !messageEntity.getBody().isEmpty()) {
            SessionManager.getInstance().sendServerMessage(null, messageEntity.getBody());
        } else {
            throw new ServiceException("Message content/body is null or empty", "",
                    ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION,
                    Response.Status.BAD_REQUEST);
        }
    }

}
