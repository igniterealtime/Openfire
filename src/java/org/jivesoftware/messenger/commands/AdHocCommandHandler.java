/**
 * $Revision: 3023 $
 * $Date: 2005-11-02 18:00:15 -0300 (Wed, 02 Nov 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.commands;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.disco.*;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An AdHocCommandHandler is responsbile for providing discoverable information about the
 * supported commands and for handling commands requests. This is an implementation of JEP-50:
 * Ad-Hoc Commands.<p>
 *
 * Ad-hoc commands that require user interaction will have one or more stages. For each stage the
 * user will complete a data form and send it back to the server. The data entered by the user is
 * kept in a SessionData. Instances of {@link AdHocCommand} are stateless. In order to prevent
 * "bad" users from consuming all system memory there exists a limit of simultaneous commands that
 * a user might perform. Configure the system property <tt>"xmpp.command.limit"</tt> to control
 * this limit. User sessions will also timeout and their data destroyed if they have not been
 * executed within a time limit since the session was created. The default timeout value is 10
 * minutes. The timeout value can be modified by setting the system property
 * <tt>"xmpp.command.timeout"</tt>.<p>
 *
 * New commands can be added dynamically by sending the message {@link #addCommand(AdHocCommand)}.
 * The command will immediatelly appear in the disco#items list and might be executed by those
 * users with enough execution permissions.
 *
 * @author Gaston Dombiak
 */
public class AdHocCommandHandler extends IQHandler
        implements ServerFeaturesProvider, DiscoInfoProvider, DiscoItemsProvider {

    private static final String NAMESPACE = "http://jabber.org/protocol/commands";

    private String serverName;
    private IQHandlerInfo info;
    private IQDiscoInfoHandler infoHandler;
    private IQDiscoItemsHandler itemsHandler;
    /**
     * Map that holds the offered commands by this server. Note: Key=commandCode, Value=command.
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

    public AdHocCommandHandler() {
        super("Ad-Hoc Commands Handler");
        info = new IQHandlerInfo("command", NAMESPACE);
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
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
                // TODO Check that the requester has enough permission. Answer forbidden error if requester permissions are not enough

                // Create new session ID
                sessionid = StringUtils.randomString(15);

                Element childElement = reply.setChildElement("command", NAMESPACE);

                if (command.getMaxStages(null) == 0) {
                    // The command does not require any user interaction (returns results only)
                    // Execute the command and return the execution result which may be a
                    // data form (i.e. report data) or a note element
                    Element answer = command.execute(null);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.completed.name());
                    // Add the execution result to the reply
                    childElement.add(answer);
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
                    SessionData session = new SessionData(sessionid);
                    sessions.put(sessionid, session);

                    // Add to the child element the data form the user must complete and
                    // the allowed actions
                    command.addNextStageInformation(null, childElement);
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
                    Element answer = command.execute(session);
                    Element childElement = reply.setChildElement("command", NAMESPACE);
                    childElement.addAttribute("sessionid", sessionid);
                    childElement.addAttribute("node", commandCode);
                    childElement.addAttribute("status", AdHocCommand.Status.completed.name());
                    // Add the execution result to the reply
                    childElement.add(answer);

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

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator<String> getFeatures() {
        ArrayList<String> features = new ArrayList<String>();
        features.add(NAMESPACE);
        return features.iterator();
    }

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        ArrayList<Element> identities = new ArrayList<Element>();
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "automation");
        identity.addAttribute("type", NAMESPACE.equals(node) ? "command-list" : "command-node");
        identities.add(identity);
        return identities.iterator();
    }

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        return Arrays.asList(NAMESPACE, "jabber:x:data").iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        if (NAMESPACE.equals(node)) {
            return true;
        }
        else {
            // TODO Should we include permission checking? Wait for answer from mailing list
            return commands.containsKey(node);
        }
    }

    public Iterator<Element> getItems(String name, String node, JID senderJID) {
        List<Element> answer = new ArrayList<Element>();
        if (!NAMESPACE.equals(node)) {
            answer = Collections.emptyList();
        }
        else {
            Element item;
            for (AdHocCommand command : commands.values()) {
                // TODO Only include commands that the sender can invoke (i.e. has enough permissions)
                item = DocumentHelper.createElement("item");
                item.addAttribute("jid", serverName);
                item.addAttribute("node", command.getCode());
                item.addAttribute("name", command.getLabel());

                answer.add(item);
            }
        }
        return answer.iterator();
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverName = server.getServerInfo().getName();
        infoHandler = server.getIQDiscoInfoHandler();
        itemsHandler = server.getIQDiscoItemsHandler();
    }

    public void start() throws IllegalStateException {
        super.start();
        infoHandler.setServerNodeInfoProvider(NAMESPACE, this);
        itemsHandler.setServerNodeInfoProvider(NAMESPACE, this);
        // Add the "out of the box" commands
        addDefaultCommands();
    }

    public void stop() {
        super.stop();
        infoHandler.removeServerNodeInfoProvider(NAMESPACE);
        itemsHandler.removeServerNodeInfoProvider(NAMESPACE);
        // Stop commands
        for (AdHocCommand command : commands.values()) {
            stopCommand(command);
        }
    }

    /**
     * Adds a new command to the list of supported ad-hoc commands by this server. The new
     * command will appear in the discoverable items list and will be executed for those users
     * with enough permission.
     *
     * @param command the new ad-hoc command to add.
     */
    public void addCommand(AdHocCommand command) {
        commands.put(command.getCode(), command);
        startCommand(command);
    }

    /**
     * Removes the command from the list of ad-hoc commands supported by this server. The command
     * will no longer appear in the discoverable items list.
     *
     * @param command the ad-hoc command to remove.
     */
    public void removeCommand(AdHocCommand command) {
        if (commands.remove(command.getCode()) != null) {
            stopCommand(command);
        }
    }

    private void addDefaultCommands() {
        // TODO Complete when out of the box commands are implemented
        //addCommand(new TimeCommand());
    }

    private void startCommand(AdHocCommand command) {
        infoHandler.setServerNodeInfoProvider(command.getCode(), this);
        itemsHandler.setServerNodeInfoProvider(command.getCode(), this);
    }

    private void stopCommand(AdHocCommand command) {
        infoHandler.removeServerNodeInfoProvider(command.getCode());
        itemsHandler.removeServerNodeInfoProvider(command.getCode());
    }
}
