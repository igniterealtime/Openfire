/**
 * $Revision: 3023 $
 * $Date: 2005-11-02 18:00:15 -0300 (Wed, 02 Nov 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A SessionData instance is responsible for keeping information gathered during the many stages
 * of the command being executed. Each session data is associated with the <tt>sessionid</tt>
 * attribute included in the <tt>command</tt> child element of the IQ packet.
 *
 * @author Gaston Dombiak
 */
class SessionData {

    private long creationStamp;

    private String id;

    /**
     * Map that keeps the association of variables and values obtained in each stage.
     * Note: Key=stage number, Value=Map with key=variable name and value=variable values.
     */
    private Map<Integer, Map<String, List<String>>> stagesData = new HashMap<Integer, Map<String, List<String>>>();

    /**
     * Keeps the default execution action to follow if the command requester does not include
     * an action in his command.
     */
    private AdHocCommand.Action executeAction;

    private List<AdHocCommand.Action> allowedActions = new ArrayList<AdHocCommand.Action>();

    /**
     * Indicates the current stage where the requester is located. Stages are numbered from 0.
     */
    private int stage;

    public SessionData(String sessionid) {
        this.id = sessionid;
        this.creationStamp = System.currentTimeMillis();
        this.stage = 0;
    }

    public String getId() {
        return id;
    }

    public long getCreationStamp() {
        return creationStamp;
    }

    public AdHocCommand.Action getExecuteAction() {
        return executeAction;
    }

    public void setExecuteAction(AdHocCommand.Action executeAction) {
        this.executeAction = executeAction;
    }

    /**
     * Sets the valid actions that the user can follow from the current stage.
     *
     * @param allowedActions list of valid actions.
     */
    public void setAllowedActions(List<AdHocCommand.Action> allowedActions) {
        if (allowedActions == null) {
            allowedActions = new ArrayList<AdHocCommand.Action>();
        }
        this.allowedActions = allowedActions;
    }

    /**
     * Returns true if the specified action is valid in the current stage. The action should have
     * previously been offered to the user.
     *
     * @param actionName the name of the action to validate.
     * @return true if the specified action is valid in the current stage.
     */
    public boolean isValidAction(String actionName) {
        for (AdHocCommand.Action action : allowedActions) {
            if (actionName.equals(action.name())) {
                return true;
            }
        }
        return false;
    }

    public void addStageForm(Map<String, List<String>> data) {
        stagesData.put(stage, data);
    }

    /**
     * Returns a Map with all the variables and values obtained during all the command stages.
     *
     * @return a Map with all the variables and values obtained during all the command stages.
     */
    public Map<String, List<String>> getData() {
        Map<String, List<String>> data = new HashMap<String, List<String>>();
        data.putAll((Map<String, List<String>>) stagesData.values());
        return data;
    }

    /**
     * Returns the current stage where the requester is located. Stages are numbered from 0. A
     * stage with value 0 means that a command request has just been received and no data form
     * has been sent to the requester yet. The first sent data form of the first stage would be
     * represented as stage 1.
     *
     * @return the current stage where the requester is located.
     */
    public int getStage() {
        return stage;
    }

    /**
     * Sets the current stage where the requester is located. Stages are numbered from 0. A
     * stage with value 0 means that a command request has just been received and no data form
     * has been sent to the requester yet. The first sent data form of the first stage would be
     * represented as stage 1.
     *
     * @param stage the current stage where the requester is located.
     */
    public void setStage(int stage) {
        this.stage = stage;
    }

}
