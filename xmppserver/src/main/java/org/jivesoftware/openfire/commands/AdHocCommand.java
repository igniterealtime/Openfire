/*
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
import org.jivesoftware.openfire.admin.AdminManager;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Map;

/**
 * An ad-hoc command is a stateless object responsbile for executing the provided service. Each
 * subclass will only have one instance that will be shared across all users sessions. Therefore,
 * it is important to not keep any information related to executions as permanent data
 * (i.e. as instance or static variables). Each command has a {@code code} that should be
 * unique within a given JID.<p>
 *
 * Commands may have zero or more stages. Each stage is usually used for gathering information
 * required for the command execution. Users are able to move forward or backward across the
 * different stages. Commands may not be cancelled while they are beig executed. However, users
 * may request the "cancel" action when submiting a stage response indicating that the command
 * execution should be aborted. Thus, releasing any collected information. Commands that require
 * user interaction (i.e. have more than one stage) will have to provide the data forms the user
 * must complete in each stage and the allowed actions the user might perform during each stage
 * (e.g. go to the previous stage or go to the next stage).
 *
 * @author Gaston Dombiak
 */
public abstract class AdHocCommand {

    /**
     * Label of the command. This information may be used to display the command as a button
     * or menu item.
     */
    private String label = getDefaultLabel();

    public AdHocCommand() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Returns true if the requester is allowed to execute this command. By default only admins
     * are allowed to execute commands. Subclasses may redefine this method with any specific
     * logic.<p>
     *
     * Note: The bare JID of the requester will be compared with the bare JID of the admins.
     *
     * @param requester the JID of the user requesting to execute this command.
     * @return true if the requester is allowed to execute this command.
     */
    public boolean hasPermission(JID requester) {
        return AdminManager.getInstance().isUserAdmin(requester, false);
    }

    /**
     * Returns the unique identifier for this command for the containing JID. The code will
     * be used as the node in the disco#items or the node when executing the command.
     *
     * @return the unique identifier for this command for the containing JID.
     */
    public abstract String getCode();

    /**
     * Returns the default label used for describing this commmand. This information is usually
     * used when returning commands as disco#items. Admins can later use {@link #setLabel(String)}
     * to set a new label and reset to the default value at any time.
     *
     * @return the default label used for describing this commmand.
     */
    public abstract String getDefaultLabel();

    /**
     * Returns the max number of stages for this command. The number of stages may vary according
     * to the collected data in previous stages. Therefore, a SessionData object is passed as a
     * parameter. When the max number of stages has been reached then the command is ready to
     * be executed.
     *
     * @param data the gathered data through the command stages or {@code null} if the
     *        command does not have stages or the requester is requesting the execution for the
     *        first time.
     * @return the max number of stages for this command.
     */
    public abstract int getMaxStages(SessionData data);

    /**
     * Executes the command with the specified session data.
     *
     * @param data the gathered data through the command stages or {@code null} if the
     *        command does not have stages.
     * @param command the command element to be sent to the command requester with a reported
     *        data result or note element with the answer of the execution.
     */
    public abstract void execute(SessionData data, Element command);

    /**
     * Adds to the command element the data form or notes required by the current stage. The
     * current stage is specified in the SessionData. This method will never be invoked for
     * commands that have no stages.
     *
     * @param data the gathered data through the command stages or {@code null} if the
     *        command does not have stages or the requester is requesting the execution for the
     *        first time.
     * @param command the command element to be sent to the command requester.
     */
    protected abstract void addStageInformation(SessionData data, Element command);

    /**
     * Returns a collection with the allowed actions based on the current stage as defined
     * in the SessionData. Possible actions are: {@code prev}, {@code next} and {@code complete}.
     * This method will never be invoked for commands that have no stages.
     *
     * @param data the gathered data through the command stages or {@code null} if the
     *        command does not have stages or the requester is requesting the execution for the
     *        first time.
     * @return a collection with the allowed actions based on the current stage as defined
     *         in the SessionData.
     */
    protected abstract List<Action> getActions(SessionData data);

    /**
     * Returns which of the actions available for the current stage is considered the equivalent
     * to "execute". When the requester sends his reply, if no action was defined in the command
     * then the action will be assumed "execute" thus assuming the action returned by this
     * method. This method will never be invoked for commands that have no stages.
     *
     * @param data the gathered data through the command stages or {@code null} if the
     *        command does not have stages or the requester is requesting the execution for the
     *        first time.
     * @return which of the actions available for the current stage is considered the equivalent
     *         to "execute".
     */
    protected abstract Action getExecuteAction(SessionData data);

    /**
     * Increments the stage number by one and adds to the command element the new data form and
     * new allowed actions that the user might perform.
     *
     * @param data the gathered data through the command stages.
     * @param command the command element to be sent to the command requester.
     */
    public void addNextStageInformation(SessionData data, Element command) {
        // Increment the stage number to the next stage
        data.setStage(data.getStage() + 1);
        // Return the data form of the current stage to the command requester. The
        // requester will need to specify the action to follow (e.g. execute, prev,
        // cancel, etc.) and complete the form is going "forward"
        addStageInformation(data, command);
        // Include the available actions at this stage
        addStageActions(data, command);
    }

    /**
     * Decrements the stage number by one and adds to the command the data form and allowed
     * actions that the user might perform of the previous stage.
     *
     * @param data the gathered data through the command stages.
     * @param command the command element to be sent to the command requester.
     */
    public void addPreviousStageInformation(SessionData data, Element command) {
        // Decrement the stage number to the previous stage
        data.setStage(data.getStage() - 1);
        // Return the data form of the current stage to the command requester. The
        // requester will need to specify the action to follow (e.g. execute, prev,
        // cancel, etc.) and complete the form is going "forward"
        addStageInformation(data, command);
        // Include the available actions at this stage
        addStageActions(data, command);
    }

    /**
     * Adds the allowed actions to follow from the current stage. Possible actions are:
     * {@code prev}, {@code next} and {@code complete}.
     *
     * @param data the gathered data through the command stages or {@code null} if the
     *        command does not have stages or the requester is requesting the execution for the
     *        first time.
     * @param command the command element to be sent to the command requester.
     */
    protected void addStageActions(SessionData data, Element command) {
        // Add allowed actions to the response
        Element actions = command.addElement("actions");
        List<Action> validActions = getActions(data);
        for (AdHocCommand.Action action : validActions) {
            actions.addElement(action.name());
        }
        Action executeAction = getExecuteAction(data);
        // Add default execute action to the response
        actions.addAttribute("execute", executeAction.name());

        // Store the allowed actions that the user can follow from this stage
        data.setAllowedActions(validActions);
        // Store the default execute action to follow if the user does not specify an
        // action in his command
        data.setExecuteAction(executeAction);
    }

    protected String get(Map<String, List<String>> data, String key, int value) {
        List<String> list = data.get(key);
        if (list == null) {
            return null;
        } else {
            return list.get(value);
        }
    }


    public enum Status {

        /**
         * The command is being executed.
         */
        executing,

        /**
         * The command has completed. The command session has ended.
         */
        completed,

        /**
         * The command has been canceled. The command session has ended.
         */
        canceled
    }

    public enum Action {

        /**
         * The command should be executed or continue to be executed. This is the default value.
         */
        execute,

        /**
         * The command should be canceled.
         */
        cancel,

        /**
         * The command should be digress to the previous stage of execution.
         */
        prev,

        /**
         * The command should progress to the next stage of execution.
         */
        next,

        /**
         * The command should be completed (if possible).
         */
        complete
    }
}
