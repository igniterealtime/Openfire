/**
 * $RCSfile$
 * $Revision: 19161 $
 * $Date: 2005-06-27 16:23:31 -0700 (Mon, 27 Jun 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.chatbot;

import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * A ChatbotSession represents a session of a given user that cannot or is not able to use
 * the workgroup protocol to join a workgroup. Once the user has started a chat with an Agent
 * then this chatbot session will be closed and removed.<p>
 *
 * A chatbot session is a lot like an HttpSession in the sense that attributes of the user
 * may be kept in the session until the session is closed and discarded.
 *
 * @author Gaston Dombiak
 */
public class ChatbotSession {

    /**
     * Reference to the chatbot where this session belongs to.
     */
    private Chatbot chatbot;

    /**
     * The real <b>full JID</b> of the user that is communication with the workgroup through a
     * standard XMPP client. The full JID is stored in order to ensure that this session is unique.
     */
    private JID userJID;

    /**
     * Keeps the date when the user sent his first message and thus this session was created.
     */
    private Date creationDate;

    /**
     * Keeps the date when the user sent his last message to the workgroup. Sessions that have
     * been idle for a while (i.e. the user haven't sent messages) may be subject to be removed.
     */
    private Date lastActiveDate;

    /**
     * Holds data sent by the user. Usually the key will be the name of a property (eg. field
     * of a form) and the associated value will be the value sent by the user.
     */
    private Map<String,List<String>> attributes = new HashMap<String,List<String>>();

    /**
     * Last known message thread that the user is using for sending his messages. It is important
     * to include the same thread when sending messages to the user so that messages may appear
     * in the same window that the user is using for sending messages.
     */
    private String messageThread;

    /**
     * Holds the current position in the dialog defined by the Chatbot.<p>
     * Note: Since currently the steps are hardcoded (except for the dataform which is dynamic
     * by natured) then we can keep an integer that represents the line in the hardcoded dialog
     * in the chatbot. If someday we need to have dynamic steps then this variable should hold
     * a ChatbotStep object where the steps act as a double-linked list.
     */
    private int currentStep = -1;

    /**
     * A substep represent the current question in a dataform. In fact, substeps represent nested
     * steps so it could be used for any nested structure. Currently only dataforms have a nested
     * structure so this variable should be greater than 0 when the user is asking a question of
     * a dataform.<p>
     * Note: If someday we need to have dynamic steps then this variable may be removed.
     */
    private int currentSubstep = -1;

    /**
     * Boolean that represents if an agent is having a chat support with the user of this session.
     * Once a chat support has ended this flag will be changed back to <tt>false</tt> again.
     */
    private boolean startedSupport = false;

    /**
     * Request generated for this user to join the workgroup. The request will be generated after
     * the user completed the form.
     */
    private UserRequest request;

    /**
     * Creates a new session for the specified user.
     *
     * @param user the user that initiated a chat with the workgroup.
     */
    public ChatbotSession(JID user, Chatbot chatbot) {
        this.userJID = user;
        this.chatbot = chatbot;
        this.creationDate = new Date();
        this.lastActiveDate = this.creationDate;
    }

    /**
     * Returns the full JID of the user that owns this session.
     *
     * @return the full JID of the user that owns this session.
     */
    public JID getUserJID() {
        return userJID;
    }

    /**
     * Returns the chatbot where this session was created. This is the chatbot that is having
     * a conversation with the owner of this session.
     *
     * @return the chatbot where this session was created.
     */
    public Chatbot getChatbot() {
        return chatbot;
    }

    /**
     * Returns the last time this session was updated. The session will be updated each time the
     * user changes the step in the chatbot. Once the user has started a support session with an
     * agent it is expected that the last active date will remain inactive for the duration of the
     * support chat session.
     *
     * @return the last time this session was updated.
     */
    public Date getLastActiveDate() {
        return lastActiveDate;
    }

    /**
     * Stores an attribute in the user session.
     *
     * @param attribute the name of the attribute to store.
     * @param values the values of the attribute.
     */
    public void putAttribute(String attribute, List<String> values) {
        attributes.put(attribute, values);
    }

    /**
     * Returns the stored attributes in the session. The returned collection is read-only.
     *
     * @return the stored attributes in the session.
     */
    public Map<String,List<String>> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
        currentSubstep = -1;
        lastActiveDate = new Date();
    }

    public int getCurrentSubstep() {
        return currentSubstep;
    }

    public void setCurrentSubstep(int currentSubstep) {
        this.currentSubstep = currentSubstep;
        lastActiveDate = new Date();
    }

    /**
     * Returns true if the user is currently having a chat with an agent.
     *
     * @return true if the user is currently having a chat with an agent.
     */

    public boolean isStartedSupport() {
        return startedSupport;
    }

    /**
     * Sets if the user is currently having a chat with an agent.
     *
     * @param startedSupport true if the user is currently having a chat with an agent.
     */
    public void setStartedSupport(boolean startedSupport) {
        this.startedSupport = startedSupport;
    }

    /**
     * Updates the referene to the Message thread that the user is using for sending his messages.
     * It is important to send messages to the user using the same thread so that messages appear
     * in the same chat window that the user is using.
     *
     * @param thread last message thread known that the user is using.
     */
    public void setMessageThread(String thread) {
        this.messageThread = thread;
    }

    /**
     * Returns the message thread that the user is using for sending his messages. It is important
     * to send messages to the user using the same thread so that messages may appear in the
     * same chat window that the user is using.
     *
     * @return the last message thread known that the user is using.
     */
    public String getMessageThread() {
        return messageThread;
    }

    /**
     * Sets the generated request for the user to join the queue.
     *
     * @param request the generated request for the user to join the queue.
     */
    public void setRequest(UserRequest request) {
        this.request = request;
    }

    /**
     * Returns the generated request for the user to join the queue or <tt>null</tt> if the user
     * haven't completed the whole form. 
     *
     * @return the generated request for the user to join the queue.
     */
    public UserRequest getRequest() {
        return request;
    }
}
