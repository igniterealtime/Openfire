/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.commands;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An AdHocCommandManager is responsible for keeping the list of available commands offered
 * by a service and for processing commands requests. Typically, instances of this class
 * are private to the service offering ad-hoc commands.
 *
 * @author Gaston Dombiak
 */
public class AdHocCommandManager implements Serializable {

    private static final String NAMESPACE = "http://jabber.org/protocol/commands";

    /**
     * Map that holds the offered commands by this service. Note: Key=commandCode, Value=command.
     * commandCode matches the node attribute sent by command requesters.
     */
    private Map<String, AdHocCommand> commands = new ConcurrentHashMap<String, AdHocCommand>();
    /**
     * Map that holds the number of command sessions of each requester.
     * Note: Key=requester full's JID, Value=number of sessions
     */
    private Map<String, AtomicInteger> sessionsCounter = new ConcurrentHashMap<String, AtomicInteger>();
    /**
     * Map that holds the command sessions. Used mainly to quickly locate a SessionData.
     * Note: Key=sessionID, Value=SessionData
     */
    private Map<String, SessionData> sessions = new ConcurrentHashMap<String, SessionData>();

    /**
     * Adds a new command to the list of supported ad-hoc commands by this server. The new
     * command will appear in the discoverable items list and will be executed for those users
     * with enough permission.
     *
     * @param command the new ad-hoc command to add.
     */
    public void addCommand(AdHocCommand command) {
        commands.put(command.getCode(), command);
    }

    /**
     * Removes the command from the list of ad-hoc commands supported by this server. The command
     * will no longer appear in the discoverable items list.
     *
     * @param command the ad-hoc command to remove.
     * @return true if the requested command was removed from the list of available commands.
     */
    public boolean removeCommand(AdHocCommand command) {
        return commands.remove(command.getCode()) != null;
    }

    /**
     * Returns a list with the available commands in this command manager.
     *
     * @return a list with the available commands in this command manager.
     */
    public Collection<AdHocCommand> getCommands() {
        return commands.values();
    }

    /**
     * Returns the command whose code matches the specified code or <tt>null</tt> if none
     * was found.
     *
     * @param code the code of the command to find.
     * @return the command whose code matches the specified code or null if none
     *         was found.
     */
    public AdHocCommand getCommand(String code) {
        return commands.get(code);
    }

    public IQ process(IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        Element iqCommand = packet.getChildElement();

        // Only packets of type SET can be processed
        if (!IQ.Type.set.equals(packet.getType())) {
            // Answer a bad_request error
            reply.setChildElement(iqCommand.createCopy());
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }

        String sessionid = iqCommand.attributeValue("sessionid");
        String commandCode = iqCommand.attributeValue("node");
        String from = packet.getFrom().toString();
        AdHocCommand command = commands.get(commandCode);
        if (sessionid == null) {
            // A new execution request has been received. Check that the command exists
            if (command == null) {
                // Requested command does not exist so return item_not_found error.
                reply.setChildElement(iqCommand.createCopy());
                reply.setError(PacketError.Condition.item_not_found);
            }
            else {
                // Check that the requester has enough permission. Answer forbidden error if
                // requester permissions are not enough to execute the requested command
                if (!command.hasPermission(packet.getFrom())) {
                    reply.setChildElement(iqCommand.createCopy());
                    reply.setError(PacketError.Condition.forbidden);
                    return reply;
                }

                // Create new session ID
                sessionid = StringUtils.randomString(15);

                Element childElement = reply.setChildElement("command", NAMESPACE);

                if (command.getMaxStages(null) == 0) {
                    // The command does not require any user interaction (returns results only)
                    // Execute the command and return the execution result which may be a
                    // data form (i.e. report data) or a note element
                    command.execute(null, childElement);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.completed.name());
                }
                else {
                    // The command requires user interactions (ie. has stages)
                    // Check that the user has not excedded the limit of allowed simultaneous
                    // command sessions.
                    AtomicInteger counter = sessionsCounter.get(from);
                    if (counter == null) {
                        synchronized (from.intern()) {
                            counter = sessionsCounter.get(from);
                            if (counter == null) {
                                counter = new AtomicInteger(0);
                                sessionsCounter.put(from, counter);
                            }
                        }
                    }
                    int limit = JiveGlobals.getIntProperty("xmpp.command.limit", 100);
                    if (counter.incrementAndGet() > limit) {
                        counter.decrementAndGet();
                        // Answer a not_allowed error since the user has exceeded limit. This
                        // checking prevents bad users from consuming all the system memory by not
                        // allowing them to create infinite simultaneous command sessions.
                        reply.setChildElement(iqCommand.createCopy());
                        reply.setError(PacketError.Condition.not_allowed);
                        return reply;
                    }
                    // Originate a new command session.
                    SessionData session = new SessionData(sessionid, packet.getFrom());
                    sessions.put(sessionid, session);

                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.executing.name());

                    // Add to the child element the data form the user must complete and
                    // the allowed actions
                    command.addNextStageInformation(session, childElement);
                }
            }

        }
        else {
            // An execution session already exists and the user has requested to perform a
            // certain action.
            String action = iqCommand.attributeValue("action");
            SessionData session = sessions.get(sessionid);
            // Check that a Session exists for the specified sessionID
            if (session == null) {
                // Answer a bad_request error (bad-sessionid)
                reply.setChildElement(iqCommand.createCopy());
                reply.setError(PacketError.Condition.bad_request);
                return reply;
            }

            // Check if the Session data has expired (default is 10 minutes)
            int timeout = JiveGlobals.getIntProperty("xmpp.command.timeout", 10 * 60 * 1000);
            if (System.currentTimeMillis() - session.getCreationStamp() > timeout) {
                // TODO Check all sessions that might have timed out (use another thread?)
                // Remove the old session
                removeSessionData(sessionid, from);
                // Answer a not_allowed error (session-expired)
                reply.setChildElement(iqCommand.createCopy());
                reply.setError(PacketError.Condition.not_allowed);
                return reply;
            }

            synchronized (sessionid.intern()) {
                // Check if the user is requesting to cancel the command
                if (AdHocCommand.Action.cancel.name().equals(action)) {
                    // User requested to cancel command execution so remove the session data
                    removeSessionData(sessionid, from);
                    // Generate a canceled confirmation response
                    Element childElement = reply.setChildElement("command", NAMESPACE);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.canceled.name());
                }

                // If the user didn't specify an action then follow the default execute action
                if (action == null || AdHocCommand.Action.execute.name().equals(action)) {
                    action = session.getExecuteAction().name();
                }

                // Check that the specified action was previously offered
                if (!session.isValidAction(action)) {
                    // Answer a bad_request error (bad-action)
                    reply.setChildElement(iqCommand.createCopy());
                    reply.setError(PacketError.Condition.bad_request);
                    return reply;
                }
                else if (AdHocCommand.Action.prev.name().equals(action)) {
                    // Move to the previous stage and add to the child element the data form
                    // the user must complete and the allowed actions of the previous stage
                    Element childElement = reply.setChildElement("command", NAMESPACE);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.executing.name());
                    command.addPreviousStageInformation(session, childElement);
                }
                else if (AdHocCommand.Action.next.name().equals(action)) {
                    // Store the completed form in the session data
                    saveCompletedForm(iqCommand, session);
                    // Move to the next stage and add to the child element the new data form
                    // the user must complete and the new allowed actions
                    Element childElement = reply.setChildElement("command", NAMESPACE);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.executing.name());
                    command.addNextStageInformation(session, childElement);
                }
                else if (AdHocCommand.Action.complete.name().equals(action)) {
                    // Store the completed form in the session data
                    saveCompletedForm(iqCommand, session);
                    // Execute the command and return the execution result which may be a
                    // data form (i.e. report data) or a note element
                    Element childElement = reply.setChildElement("command", NAMESPACE);
                    command.execute(session, childElement);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.completed.name());

                    // Command has been executed so remove the session data
                    removeSessionData(sessionid, from);
                }
            }
        }
        return reply;
    }

    /**
     * Stores in the SessionData the fields and their values as specified in the completed
     * data form by the user.
     *
     * @param iqCommand the command element containing the data form element.
     * @param session the SessionData for this command execution.
     */
    private void saveCompletedForm(Element iqCommand, SessionData session) {
        Element formElement = iqCommand.element(QName.get("x", "jabber:x:data"));
        if (formElement != null) {
            // Generate a Map with the variable names and variables values
            Map<String, List<String>> data = new HashMap<String, List<String>>();
            DataForm dataForm = new DataForm(formElement);
            for (FormField field : dataForm.getFields()) {
                data.put(field.getVariable(), field.getValues());
            }
            // Store the variables and their values in the session data
            session.addStageForm(data);
        }
    }

    /**
     * Releases the data kept for the command execution whose id is sessionid. The number of
     * commands executions currently being executed by the user (full JID) will be decreased.
     *
     * @param sessionid id of the session that identifies this command execution.
     * @param from the full JID of the command requester.
     */
    private void removeSessionData(String sessionid, String from) {
        sessions.remove(sessionid);
        if (sessionsCounter.get(from).decrementAndGet() <= 0) {
            // Remove the AtomicInteger when no commands are being executed
            sessionsCounter.remove(from);
        }
    }

    public void stop() {
        // Cancel executions of running commands
        sessions.clear();
        sessionsCounter.clear();
    }

}
