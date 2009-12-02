/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.xmpp.workgroup.chatbot;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.fastpath.dataforms.FormElement;
import org.jivesoftware.openfire.fastpath.dataforms.FormManager;
import org.jivesoftware.openfire.fastpath.dataforms.WorkgroupForm;
import org.jivesoftware.openfire.fastpath.history.ChatTranscriptManager;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettings;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager;
import org.jivesoftware.openfire.fastpath.settings.chat.KeyEnum;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.UserCommunicationMethod;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.interceptor.ChatbotInterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.InterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.PacketRejectedException;
import org.jivesoftware.xmpp.workgroup.interceptor.QueueInterceptorManager;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * A Chatbot holds a sequence of steps where each step represents an interaction with a
 * user. The user may navigate sequentially between the defined steps in the Chatbot.<p>
 *
 * ChatBots are stateless so the information of the chat for each user is kept in a
 * {@link ChatbotSession}. Each {@link org.jivesoftware.xmpp.workgroup.Workgroup} will have
 * a different Chatbot.<p>
 *
 * Currently the bot steps are hardcoded but a future version may let them be dynamic. The
 * current step is kept in the ChatbotSession as an integer number. When filling out a data
 * form a substep is used to register the current question that the user is completing.
 * These are the current supported steps:
 * <ol>
 *  <li><b>step = -1</b> - The chat session was just created but no message was sent.</li>
 *  <li><b>step = 0</b> - The welcome message was sent to the user. Since the user will receive the
 *  join question after the welcome message then this position is changed immediatelly.</li>
 *  <li><b>step = 1</b> - The join question was sent to the user and an answer is expected.</li>
 *  <li><b>step = 2</b> - The user decided to join the workgroup so the dataform is presented to
 *  the user. Each field in the dataform will generate a new message for the user. After the
 *  question was answered then the next message is sent. The same step value will be used for all
 *  the dataform fields. For each question a substep is used to represent the current question that
 *  the user is completing. The user may go back or repeat the question by sending commands.</li>
 *  <li><b>step = 3</b> - The user completed the form and joined a queue. At this point only
 *  commands are accepted from the user.</li>
 *  <li><b>step = 4</b> - The user left the queue since a room invitation was sent to him to start
 *  a chat session with an agent. At this point the user may ask to receive the invitation
 *  again.</li>
 *  <li><b>step = 5</b> - The user accepted the invitation and is now having a chat with an agent.
 *   At this point only commands are accepted from the user.</li>
 *  <li><b>step = 6</b> - The user has finished the chat with the agent and is asked if he wants to
 *  receive the chat transcript by email.</li>
 *  <li><b>step = 7</b> - The user wants to get the transcript by email but hasn't specified an
 *  email address so ask him to enter an email address and then send the chat transcript by
 *  email.</li>
 * </ol>
 *
 * The Chatbot allows the user to execute commands. Commands may help the user go back to
 * previous questions so he can change an answer or repeat the last question in case the user
 * closed his window accidentally.
 * These are the supported commands:
 * <ol>
 *  <li><b>back</b> - User is requesting to go back one step in the dialog and repeat the
 *      previous message</li>
 *  <li><b>repeat</b> - User is requesting to repeat the last message</li>
 *  <li><b>help</b> - User is requesting the list of available commands</li>
 *  <li><b>bye</b> - User is closing the chat session no matter the if he already joined a
 *      waiting queue or not</li>
 *  <li><b>position</b> - User wants to know his position in the waiting queue</li>
 * </ol>
 *
 * <b>Future ideas</b>
 * <ul>
 * <li>Configure the chat bot with JIDs of agents or admin that are allowed to:<ol>
 *      <li>obtain workgroup and queue statistics</li>
 *      <li>obtain current workgroup configureation</li>
 *      <li>change the chatbot configuration</li>
 *      <li>change the workgroup configuration, etc. etc. etc.</li></ol></li>
 * <li>After the user ends the chat session with the agent offer the user to fill out
 * a QoS survey.</li>
 * </ul>
 *
 * @author Gaston Dombiak
 */
public class Chatbot implements UserCommunicationMethod {

	private static final Logger Log = LoggerFactory.getLogger(Chatbot.class);
	
    /**
     * Holds the workgroup where the chatbot is working. This is a one-to-one relation so this
     * chatbot is the only chatbot that will be answering Messages sent to the workgroup.
     */
    private Workgroup workgroup;

    /**
     * The chat settings stores all the messages that the chatbot will use. The messages can
     * be modified from the Admin Console.
     */
    private ChatSettings settings;

    /**
     * Holds the chat sessions of all the users that are trying to join this workgroup.
     */
    private Map<String, ChatbotSession> sessions = new ConcurrentHashMap<String, ChatbotSession>();

    // TODO use resource bundles for these joining answers
    /**
     * Text that assumes that a user sent a positive answer.
     */
    private String yes = "yes";

    /**
     * Text that assumes that a user sent a negative answer.
     */
    private String no = "no";

    /**
     * Creates a new chatbot responsible for answering Messages sent to the specified workgroup.
     *
     * @param workgroup Workgroup where the new chatbot will be working.
     */
    public Chatbot(Workgroup workgroup) {
        this.workgroup = workgroup;
        this.settings = ChatSettingsManager.getInstance().getChatSettings(workgroup);
    }

    /**
     * Returns the session of the specified user in a given workgroup. If no session exists then
     * a new one will be created for the user if requested. If the session of the user remains
     * inactive for some time then it may be discarded.
     *
     * @param user the user to return his session in a given workgroup.
     * @param create true if a new session should be created if it doesn't already exist.
     * @return the session of the specified user in a given workgroup.
     */
    public ChatbotSession getSession(JID user, boolean create) {
        String fullJID = user.toString();
        ChatbotSession session = sessions.get(fullJID);
        if (session == null && create) {
            synchronized (fullJID.intern()) {
                session = sessions.get(fullJID);
                if (session == null) {
                    session = new ChatbotSession(user, this);
                    sessions.put(fullJID, session);
                }
            }
        }
        return session;
    }

    /**
     * Process a message sent by the owner of the specified session.
     *
     * @param session the session whose owner (i.e. user) sent the message.
     * @param message message sent by the owner of the session to the workgroup.
     */
    public void onMessage(ChatbotSession session, Message message) {
        InterceptorManager interceptorManager = ChatbotInterceptorManager.getInstance();
        try {
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), message, true,
                    false);

            // Update the Message thread that the user is using in the session
            session.setMessageThread(message.getThread());
            // Check if the workgroup is opened
            synchronized(session) {
                if (workgroup.getStatus() != Workgroup.Status.OPEN) {
                    // Send message saying that the workgroup is closed/not available
                    sendReply(message, getWorkgroupClosedMessage());
                }
                else if (handleCommand(message, session)) {
                    // The sent message executed a command so do nothing
                }
                else if (session.getCurrentStep() < 0) {
                    // Send the welcome message
                    sendWelcomeMessage(message);
                    // Send the join question
                    sendJoinQuestion(message, session);
                }
                else if (session.getCurrentStep() == 1) {
                    // User is answering join question
                    if (yes.equalsIgnoreCase(message.getBody().trim())) {
                        // User accepted to join the workgroup so send the first question of
                        // the form
                        userAcceptedJoining(message, session);
                    }
                    else if (no.equalsIgnoreCase(message.getBody().trim())) {
                        // User rejected to join the workgroup so send a goodbye message and close
                        // the chat session
                        closeSession(message);
                    }
                    else {
                        // The user sent an unknown answer so repeat the join question
                        sendJoinQuestion(message, session);
                    }
                }
                else if (session.getCurrentStep() == 2) {
                    // User is filling out the form
                    if (userAnsweredField(message, session)) {
                        // User answered correctly the question so send the next question or if the
                        // form has been filled out then join the queue
                        if (session.getCurrentSubstep() < getForm().getFormElements().size()-1) {
                            sendNextQuestion(message, session);
                        }
                        else {
                            userJoinQueue(message, session);
                        }
                    }
                    else {
                        // The user sent an unknown answer so repeat the last question
                        repeatQuestion(message, session);
                    }
                }
                else if (session.getCurrentStep() == 4) {
                    // User is answering if he wants to get another room invitation
                    if (yes.equalsIgnoreCase(message.getBody().trim())) {
                        // User accepted to receive another room invitation so send another
                        // room invitation
                        sendRoomInvitation(message, session);
                    }
                    else if (no.equalsIgnoreCase(message.getBody().trim())) {
                        // User declined to receive another room invitation so do nothing
                    }
                    else {
                        // The user sent an unknown answer so repeat the invitation question
                        sendInvitationQuestion(message.getFrom(), session);
                    }
                }
                else if (session.getCurrentStep() == 6) {
                    // User is answering email question
                    if (yes.equalsIgnoreCase(message.getBody().trim())) {
                        // User accepted to receive the transcript by email
                        List<String> emailValue = session.getAttributes().get("email");
                        if (emailValue == null || emailValue.isEmpty()) {
                            // The user wants to get the transcript by email but he hasn't provided
                            // an email yet so ask for one
                            sendGetEmailQuestion(message, session);
                        }
                        else {
                            // Send the transcript by email
                            sendTranscriptByMail(emailValue.get(0), message, session);
                            // Send a goodbye message and close the chat session
                            closeSession(message);
                        }
                    }
                    else if (no.equalsIgnoreCase(message.getBody().trim())) {
                        // User rejected to receive the transcript by email so send a goodbye
                        // message and close the chat session
                        closeSession(message);
                    }
                    else {
                        // The user sent an unknown answer so repeat the email question
                        sendEmailQuestion(message.getFrom(), session);
                    }
                }
                else if (session.getCurrentStep() == 7) {
                    // Send the transcript by email
                    sendTranscriptByMail(message.getBody().trim(), message, session);
                    // Send a goodbye message and close the chat session
                    closeSession(message);
                }
                else {
                    // User is waiting in a queue and the sent message contains an unkown content
                    // so send message saying that the command was not acceptable (i.e. unknown command)
                    sendReply(message, getNotAcceptableMessage());
                }
            }
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), message, true,
                    true);
        }
        catch (PacketRejectedException e) {
            workgroup.rejectPacket(message, e);
        }
    }

    private void userJoinQueue(Message message, ChatbotSession session) {
        InterceptorManager interceptorManager = QueueInterceptorManager.getInstance();
        try {
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), message, true,
                    false);
            if (getRoutingMessage() != null && getRoutingMessage().length() > 0) {
                sendReply(message, getRoutingMessage());
            }
            // Set that we are currently joining a waiting queue
            session.setCurrentStep(3);

            // Received a Join Queue request from a visitor, create a new request.
            UserRequest request = new UserRequest(session, workgroup);
            // Let the workgroup process the new request
            if (!workgroup.queueRequest(request)) {
                // It was not possible to add the request to a queue so send message saying that
                // the workgroup is not accepting new join requests
                sendReply(message, getCannotJoinMessage());
                // Send the goodbye message and close the session
                closeSession(message);
            }
            else {
                session.setRequest(request);
            }
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), message, true,
                    true);
        }
        catch (PacketRejectedException e) {
            workgroup.rejectPacket(message, e);
        }
    }

    private void userDepartQueue(Message message) {
        // Remove the user from the queue if he was waiting in the queue
        try {
            Request request = UserRequest.getRequest(workgroup, message.getFrom());
            InterceptorManager interceptorManager = QueueInterceptorManager.getInstance();
            try {
                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), message, true,
                        false);
                request.cancel(Request.CancelType.DEPART);
                // Remove the session (the goodbye message is sent when leaving the queue)
                removeSession(message.getFrom());
                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), message, true,
                        true);
            }
            catch (PacketRejectedException e) {
                workgroup.rejectPacket(message, e);
            }
        }
        catch (NotFoundException e) {
            // Send the goodbye message and close the session
            closeSession(message);
        }
    }

    private void sendWelcomeMessage(Message message) {
        String welcomeMessage = getWelcomeMessage();
        welcomeMessage = welcomeMessage.replace("${workgroup}", workgroup.getJID().toString());
        sendReply(message, welcomeMessage);
    }

    private void sendJoinQuestion(Message message, ChatbotSession session) {
        sendReply(message, getJoinQuestion());
        // Set that we are currently waiting for a response to the join question
        session.setCurrentStep(1);
    }

    private void sendPreviousQuestion(Message message, ChatbotSession session) {
        if (session.getCurrentSubstep() == 0) {
            sendJoinQuestion(message, session);
        }
        else {
            // Send the next question to the user
            sendQuestion(message, session, session.getCurrentSubstep()-1);
        }
    }

    private void sendNextQuestion(Message message, ChatbotSession session) {
        // Send the next question to the user
        sendQuestion(message, session, session.getCurrentSubstep()+1);
    }

    private void sendEmailQuestion(JID receiver, ChatbotSession session) {
        // Ask the user if he wants to receive the chat transcript by email
        session.setCurrentStep(6);
        sendMessage(receiver, session.getMessageThread(), getSendEmailQuestion());
    }

    private void sendRoomInvitation(Message message, ChatbotSession session) {
        UserRequest request = session.getRequest();
        // Send again the room invitation to the user
        workgroup.sendUserInvitiation(request, request.getInvitedRoomID());
        // Send a confirmation to the user notifying that the invitation was sent
        sendMessage(message.getFrom(), session.getMessageThread(), getInvitationResentMessage());
    }

    public void sendInvitationQuestion(JID receiver, ChatbotSession session) {
        sendMessage(receiver, session.getMessageThread(), getSendInvitationQuestion());
    }

    private void sendGetEmailQuestion(Message message, ChatbotSession session) {
        // Ask the user for his email address
        session.setCurrentStep(7);
        sendMessage(message.getFrom(), session.getMessageThread(), getGetEmailQuestion());
    }

    private void sendTranscriptByMail(String email, Message message, ChatbotSession session) {
        // Send the transcript by email
        ChatTranscriptManager.sendTranscriptByMail(session.getRequest().getSessionID(), email);
        // Confirm the user that the email was sent to his email address
        sendEmailSentConfirmation(email, message);
    }

    private void sendEmailSentConfirmation(String email, Message message) {
        String emailSentMessage = getEmailSentMessage();
        emailSentMessage = emailSentMessage.replace("${email}", email);
        sendReply(message, emailSentMessage);
    }

    private void sendHelpMessage(Message message) {
        sendReply(message, getHelpHelpMessage());
        sendReply(message, getBackHelpMessage());
        sendReply(message, getRepeatHelpMessage());
        sendReply(message, getByeHelpMessage());
        sendReply(message, getPositionHelpMessage());
    }

    /**
     * Repeat the last question to the user. This may happen either if because the user requested
     * it or because the user sent an invalid answer.
     *
     * @param message the sent message by the user.
     * @param session the session that keeps the state of the user chat.
     */
    private void repeatQuestion(Message message, ChatbotSession session) {
        // Resend the last question to the user
        sendQuestion(message, session, session.getCurrentSubstep());
    }

    private void userAcceptedJoining(Message message, ChatbotSession session) {
        if (getForm() != null && !getForm().getFormElements().isEmpty()) {
            if (getFilloutFormMessage() != null && getFilloutFormMessage().length() > 0) {
                // Send the message informing that a form must be filled out
                sendReply(message, getFilloutFormMessage());
            }
            session.setCurrentStep(2);
            // Send the first question to the user
            sendQuestion(message, session, 0);
        }
        else {
            // Since there is no form to fill out just join the queue
            userJoinQueue(message, session);
        }
    }

    private void closeSession(Message message) {
        sendReply(message, getByeMessage());
        // Remove the session of this user
        removeSession(message.getFrom());
    }

    private void removeSession(JID user) {
        // Remove the session of this user
        sessions.remove(user.toString());
    }

    private void sendQuestion(Message message, ChatbotSession session, int position) {
        FormElement field = getForm().getFormElementAt(position);
        if (field == null) {
            return;
        }
        if (field.getAnswerType() == WorkgroupForm.FormEnum.hidden) {
            // Auto accept hidden fields
            Message fakeMessage = message.createCopy();
            StringBuilder builder = new StringBuilder();
            for (Iterator<String> it=field.getAnswers().iterator(); it.hasNext();) {
                builder.append(it.next());
                if (it.hasNext()) {
                    builder.append("/");
                }
            }
            fakeMessage.setBody(builder.toString());
            // Set that we are currently waiting for a response to the next question
            session.setCurrentSubstep(position);
            // Simulate that the user sent this message (with the hidden field)
            onMessage(session, fakeMessage);
        }
        String text = field.getLabel();
        if (field.getAnswerType() == WorkgroupForm.FormEnum.radio_button ||
                field.getAnswerType() == WorkgroupForm.FormEnum.dropdown_box ||
                field.getAnswerType() == WorkgroupForm.FormEnum.checkbox) {
            // Append the options to the message body
            if (!field.getAnswers().isEmpty()) {
                StringBuilder builder = new StringBuilder(text);
                builder.append(" [");
                builder.append(Request.encodeMetadataValue(field.getAnswers()));
                builder.append("]");
                text = builder.toString();
            }
        }
        sendReply(message, text);
        // Set that we are currently waiting for a response to the next question
        session.setCurrentSubstep(position);
    }

    /**
     * Returns true if the received message contains a valid answer for the current question.
     *
     * @param message the sent message by the user.
     * @param session the session that keeps the state of the user chat.
     * @return true if the received message contains a valid answer for the current question.
     */
    private boolean userAnsweredField(Message message, ChatbotSession session) {
        boolean validAnswer = false;
        List<String> answers = Request.decodeMetadataValue(message.getBody().trim());

        FormElement field = getForm().getFormElementAt(session.getCurrentSubstep());
        List<String> options = field.getAnswers();
        if (!options.isEmpty()) {
            for (String answer : answers) {
                // Check that the each answer is an allowed option
                validAnswer = false;
                for (String option : options) {
                    // Skip any CR character present in the option
                    option = option.replace("\r", "");
                    if (option.equalsIgnoreCase(answer)) {
                        validAnswer = true;
                        break;
                    }
                }
                if (!validAnswer) {
                    return false;
                }
            }
        }
        else {
            // The question allows any value so we accept this answer
            validAnswer = true;
        }

        if (validAnswer) {
            // Store the answer since it's a valid answer
            session.putAttribute(field.getVariable(), answers);
        }
        return validAnswer;
    }

    /**
     * Returns true if the message sent by the user requested to run a command. Depending on the
     * stage of the conversation different commands may be executed.
     *
     * @param message the message.
     * @param session the session.
     * @return true if the message sent by the user requested to run a command.
     */
    private boolean handleCommand(Message message, ChatbotSession session) {
        String command = message.getBody().trim();
        if (getHelpCommand().equalsIgnoreCase(command)) {
            sendHelpMessage(message);
            return true;
        }
        else if (getByeCommand().equalsIgnoreCase(command)) {
            userDepartQueue(message);
            return true;
        }
        if (session.getCurrentStep() == 1) {
            if (getRepeatCommand().equalsIgnoreCase(command)) {
                // Send the join question
                sendJoinQuestion(message, session);
                return true;
            }
            else if (getPositionCommand().equalsIgnoreCase(command)) {
                // Tell the user that he is not waiting in the queue
                sendReply(message, getNotInQueueMessage());
                return true;
            }
        }
        else if (session.getCurrentStep() == 2) {
            if (getBackCommand().equalsIgnoreCase(command)) {
                sendPreviousQuestion(message, session);
                return true;
            }
            else if (getRepeatCommand().equalsIgnoreCase(command)) {
                // Resend the last question
                repeatQuestion(message, session);
                return true;
            }
            else if (getPositionCommand().equalsIgnoreCase(command)) {
                // Tell the user that he is not waiting in the queue
                sendReply(message, getNotInQueueMessage());
                return true;
            }
        }
        else if (session.getCurrentStep() == 3) {
            if (getPositionCommand().equalsIgnoreCase(command)) {
                try {
                    UserRequest request = UserRequest.getRequest(workgroup, message.getFrom());
                    request.updateQueueStatus(true);
                }
                catch (NotFoundException e) {
                    // Tell the user that he is not waiting in the queue
                    sendReply(message, getNotInQueueMessage());
                }
                return true;
            }
        }
        else if (session.getCurrentStep() == 6) {
            if (getRepeatCommand().equalsIgnoreCase(command)) {
                // Resend the last question
                sendEmailQuestion(message.getFrom(), session);
                return true;
            }
        }
        else if (session.getCurrentStep() == 7) {
            if (getRepeatCommand().equalsIgnoreCase(command)) {
                // Resend the last question
                sendGetEmailQuestion(message, session);
                return true;
            }
        }
        return false;
    }

    private void sendReply(Message message, String reply) {
        Message packet = new Message();
        packet.setTo(message.getFrom());
        packet.setFrom(message.getTo());
        packet.setThread(message.getThread());
        packet.setType(message.getType());
        packet.setBody(reply);
        send(packet);
    }

    private void sendMessage(JID receiver, String thread, String body) {
        Message packet = new Message();
        packet.setTo(receiver);
        packet.setFrom(workgroup.getJID());
        packet.setThread(thread);
        if (thread != null) {
            packet.setType(Message.Type.chat);
        }
        packet.setBody(body);
        send(packet);
    }

    private void send(Message packet) {
        InterceptorManager interceptorManager = ChatbotInterceptorManager.getInstance();
        try {
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet, false,
                    false);
            workgroup.send(packet);
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet, false,
                    true);
        }
        catch (PacketRejectedException e) {
            Log.warn("Packet was not sent " +
                    "due to interceptor REJECTION: " + packet.toXML(), e);
        }
    }

    public void notifyQueueStatus(JID sender, JID receiver, UserRequest request, boolean isPolling) {
        // Get the chatbot session of the user
        ChatbotSession session = getSession(receiver, false);
        if (session != null) {
            Message packet = new Message();
            packet.setTo(receiver);
            packet.setFrom(sender);
            packet.setThread(session.getMessageThread());
            if (session.getMessageThread() != null) {
                packet.setType(Message.Type.chat);
            }
            String body = getPositionMessage().replace("${position}",
                    String.valueOf(request.getPosition()  +  1));
            body = body.replace("${waitTime}", String.valueOf(request.getTimeStatus()));
            packet.setBody(body);
            send(packet);
        }
    }

    public void notifyQueueDepartued(JID sender, JID receiver, UserRequest request,
            Request.CancelType type) {
        // Get the chatbot session of the user
        ChatbotSession session = getSession(receiver, false);
        if (session != null) {
            Message packet = new Message();
            packet.setTo(receiver);
            packet.setFrom(sender);
            packet.setThread(session.getMessageThread());
            if (session.getMessageThread() != null) {
                packet.setType(Message.Type.chat);
            }
            packet.setBody(getDepartureConfirmedMessage());
            send(packet);
            // Remove the session of this user
            removeSession(receiver);
        }
    }

    public void invitationsSent(UserRequest request) {
        JID receiver = request.getUserJID();
        // Get the chatbot session of the user
        ChatbotSession session = getSession(receiver, false);
        if (session != null) {
            session.setCurrentStep(4);
            // Send a notification saying that an invitation has been sent to start a chat
            // with an agent
            sendMessage(receiver, session.getMessageThread(), getInvitationSentMessage());
        }
    }

    public void checkInvitation(UserRequest request) {
        JID receiver = request.getUserJID();
        // Get the chatbot session of the user
        ChatbotSession session = getSession(receiver, false);
        if (session != null) {
            // Ask the user if he wants to receive a new invitation
            sendInvitationQuestion(receiver, session);
        }
    }

    public void supportStarted(UserRequest request) {
        JID receiver = request.getUserJID();
        // Get the chatbot session of the user
        ChatbotSession session = getSession(receiver, false);
        if (session != null) {
            session.setStartedSupport(true);
            session.setCurrentStep(5);
        }
    }

    public void supportEnded(UserRequest request) {
        JID receiver = request.getUserJID();
        // Get the chatbot session of the user
        ChatbotSession session = getSession(receiver, false);
        if (session != null) {
            // Set that the session's user has finished the chat with an agent
            session.setStartedSupport(false);
            // Ask the user if he wants to receive the chat transcript by email
            sendEmailQuestion(receiver, session);
        }
    }

    /**
     * Returns the welcome message to send to the user after the user sent his first message.
     *
     * @return the welcome message to send to the user after the user sent his first message.
     */
    private String getWelcomeMessage() {
        return settings.getChatSetting(KeyEnum.welcome_message).getValue();
    }

    /**
     *  Returns the question to send to the user asking if he wants to join the workgroup.
     *
     * @return the question to send to the user asking if he wants to join the workgroup.
     */
    private String getJoinQuestion() {
        return settings.getChatSetting(KeyEnum.join_question).getValue();
    }

    /**
     * Returns the message to send if the user does not want to join the workgroup.
     *
     * @return the message to send if the user does not want to join the workgroup.
     */
    private String getByeMessage() {
        return settings.getChatSetting(KeyEnum.bye_message).getValue();
    }

    /**
     * Returns the message to send to the user informing that a form must be filled out. This
     * message is optional.
     *
     * @return the message to send to the user informing that a form must be filled out.
     */
    private String getFilloutFormMessage() {
        return settings.getChatSetting(KeyEnum.fillout_form_message).getValue();
    }

    public WorkgroupForm getForm() {
        return FormManager.getInstance().getWebForm(workgroup);
    }

    /**
     * Returns the message to inform the user that he has entered a waiting queue and
     * that an agent will be with him in a moment.
     *
     * @return the message to inform the user that he has entered a waiting queue and
     *         that an agent will be with him in a moment.
     */
    private String getRoutingMessage() {
        return settings.getChatSetting(KeyEnum.routing_message).getValue();
    }
    /**
     * Returns the message that informs the user his position in the waiting queue.
     *
     * @return the message that informs the user his position in the waiting queue.
     */
    private String getPositionMessage() {
        return settings.getChatSetting(KeyEnum.position_message).getValue();
    }

    /**
     * Returns the message to send to the user when the user asks to leave the waiting queue.
     *
     * @return the message to send to the user when the user asks to leave the waiting queue.
     */
    private String getDepartureConfirmedMessage() {
        return settings.getChatSetting(KeyEnum.departure_confirmed_message).getValue();
    }

    /**
     * Returns the message to inform the user that the requested command cannot be processed.
     *
     * @return the message to inform the user that the requested command cannot be processed.
     */
    private String getNotAcceptableMessage() {
        return settings.getChatSetting(KeyEnum.not_acceptable_message).getValue();
    }

    /**
     * Returns the message to inform the user that he is not in a waiting queue yet. This message
     * may be sent to the user if the user sent a "position" command and the user is not in the
     * queue yet.
     *
     * @return the message to inform the user that he is not in a waiting queue yet.
     */
    private String getNotInQueueMessage() {
        return settings.getChatSetting(KeyEnum.not_in_queue_message).getValue();
    }

    /**
     * Returns the message to send to the user informing that the workgroup is currently closed.
     *
     * @return the message to send to the user informing that the workgroup is currently closed.
     */
    private String getWorkgroupClosedMessage() {
        return settings.getChatSetting(KeyEnum.workgroup_closed_message).getValue();
    }

    /**
     * Returns the message to send to the user informing that the user is not allowed to join
     * the queue. This may happen because the workgroup is closed or the specific user cannot join
     * due to some restriction policy.
     *
     * @return the message to send to the user informing that the user is not allowed to join
     *         the queue.
     */
    private String getCannotJoinMessage() {
        return settings.getChatSetting(KeyEnum.cannot_join_message).getValue();
    }

    /**
     * Returns the message to send to the user asking if he wants to receive the chat transcript
     * by email.
     *
     * @return the message to send to the user asking if he wants to receive the chat transcript
     *         by email.
     */
    private String getSendEmailQuestion() {
        return settings.getChatSetting(KeyEnum.send_email_question).getValue();
    }

    /**
     * Returns the message to send to the user informing that he is now being routed to an agent.
     *
     * @return the message to send to the user informing that he is now being routed to an agent.
     */
    private String getInvitationSentMessage() {
        return settings.getChatSetting(KeyEnum.invitation_sent_message).getValue();
    }

    /**
     * Returns the message to send to the user asking if he wants to receive again the room
     * invitation.
     *
     * @return the message to send to the user asking if he wants to receive again the room
     *         invitation.
     */
    private String getSendInvitationQuestion() {
        return settings.getChatSetting(KeyEnum.send_invitation_question).getValue();
    }

    /**
     * Returns the message that will ask the user for his email address so that the chatbot can
     * send the chat transcript by email. This question will only be made if the user hasn't
     * entered an email address before (that means that the form is not asking for an email).
     *
     * @return the message that will ask the user for his email address so that the chatbot can
     *         send the chat transcript by email.
     */
    private String getGetEmailQuestion() {
        return settings.getChatSetting(KeyEnum.send_get_email_question).getValue();
    }

    /**
     * Returns the message to send to the user informing that the invitation was sent again.
     *
     * @return the message to send to the user informing that the invitation was sent again.
     */
    private String getInvitationResentMessage() {
        return settings.getChatSetting(KeyEnum.invitation_resent_message).getValue();
    }

    /**
     * Returns the message to send to the user informing that the chat transcript was sent by email.
     *
     * @return the message to send to the user informing that the chat transcript was sent by email.
     */
    private String getEmailSentMessage() {
        return settings.getChatSetting(KeyEnum.email_sent_message).getValue();
    }

    /**
     * Returns the message that describes the effect of running the <b>back</b> command.
     *
     * @return the message that describes the effect of running the <b>back</b> command.
     */
    private String getBackHelpMessage() {
        return settings.getChatSetting(KeyEnum.back_help_message).getValue();
    }

    /**
     * Returns the message that describes the effect of running the <b>repeat</b> command.
     *
     * @return the message that describes the effect of running the <b>repeat</b> command.
     */
    private String getRepeatHelpMessage() {
        return settings.getChatSetting(KeyEnum.repeat_help_message).getValue();
    }

    /**
     * Returns the message that describes the effect of running the <b>help</b> command.
     *
     * @return the message that describes the effect of running the <b>help</b> command.
     */
    private String getHelpHelpMessage() {
        return settings.getChatSetting(KeyEnum.help_help_message).getValue();
    }

    /**
     * Returns the message that describes the effect of running the <b>bye</b> command.
     *
     * @return the message that describes the effect of running the <b>bye</b> command.
     */
    private String getByeHelpMessage() {
        return settings.getChatSetting(KeyEnum.bye_help_message).getValue();
    }

    /**
     * Returns the message that describes the effect of running the <b>position</b> command.
     *
     * @return the message that describes the effect of running the <b>position</b> command.
     */
    private String getPositionHelpMessage() {
        return settings.getChatSetting(KeyEnum.position_help_message).getValue();
    }

    /**
     * Returns the string that indicates that the user is requesting to go back one step in
     * the dialog and repeat the previous message.
     *
     * @return the string that indicates that the user is requesting to go back one step in
     *         the dialog and repeat the previous message.
     */
    private String getBackCommand() {
        return settings.getChatSetting(KeyEnum.back_command).getValue();
    }

    /**
     * Returns the string that indicates that the user is requesting to repeat the last message.
     * This may happen if for instance the user closed his chat window by mistake.
     *
     * @return the string that indicates that the user is requesting to repeat the last message.
     */
    private String getRepeatCommand() {
        return settings.getChatSetting(KeyEnum.repeat_command).getValue();
    }

    /**
     * Returns the string that indicates that the user is requesting the list of available commands.
     *
     * @return the string that indicates that the user is requesting the list of available commands.
     */
    private String getHelpCommand() {
        return settings.getChatSetting(KeyEnum.help_command).getValue();
    }

    /**
     * Returns the string that indicates that the user is closing the chat session no matter
     * the if he already joined a waiting queue or not. If the user joined a waiting queue then
     * the user will leave the queue.
     *
     * @return the string that indicates that the user is closing the chat session no matter
     *         the if he already joined a waiting queue or not.
     */
    private String getByeCommand() {
        return settings.getChatSetting(KeyEnum.bye_command).getValue();
    }

    /**
     * Returns the string that indicates that the user wants to know his position in the
     * waiting queue. If the user is not in the waiting queue then send the "notInQueueMessage"
     * message.
     *
     * @return the string that indicates that the user wants to know his position in the
     *         waiting queue.
     */
    private String getPositionCommand() {
        return settings.getChatSetting(KeyEnum.position_command).getValue();
    }

    /**
     * Removes idle sessions. After a session has been removed, if a user sends a message to the
     * chatbot then the script will start from scratch. Therefore, all the stored information
     * in the session will be lost.
     */
    public void cleanup() {
        final long deadline = System.currentTimeMillis() - getIdleTimeout();
        for (ChatbotSession session : sessions.values()) {
            // Do not clean up sessions whose users are having a chat with an agent.
            if (!session.isStartedSupport() && session.getLastActiveDate().getTime() < deadline)  {
                Log.debug("Removing idle chat " +
                        "session for: " +
                        session.getUserJID());
                removeSession(session.getUserJID());
            }
        }
    }

    /**
     * Sets the time to wait before considering an idle session candidate to be removed.
     *
     * @param timeout the number of milliseconds that a session must be idle.
     */
    public void setIdleTimeout(long timeout) {
        try {
            workgroup.getProperties().setProperty("chatbot.session.timeout",
                    String.valueOf(timeout));
        }
        catch (UnauthorizedException e) {
            Log.error("Error setting timeout",
                    e);
        }
    }

    /**
     * Returns the milliseconds that a session must be idle to be considered candidate for removal.
     *
     * @return the milliseconds that a session must be idle to be considered candidate for removal.
     */
    public long getIdleTimeout() {
        long timeout = 30 * 60 * 1000;
        try {
            timeout =Long.parseLong(workgroup.getProperties().getProperty(
                    "chatbot.session.timeout"));
        }
        catch (NumberFormatException e) {
            // Ignore.
        }
        return timeout;
    }
}
