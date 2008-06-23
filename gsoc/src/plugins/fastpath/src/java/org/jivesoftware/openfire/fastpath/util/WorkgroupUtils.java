/**
 * $RCSfile$
 * $Revision$
 * $Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.util;

import org.jivesoftware.openfire.fastpath.dataforms.FormManager;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsCreator;
import org.jivesoftware.xmpp.workgroup.*;
import org.jivesoftware.xmpp.workgroup.dispatcher.AgentSelector;
import org.jivesoftware.xmpp.workgroup.spi.dispatcher.BasicAgentSelector;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.jivesoftware.stringprep.Stringprep;
import org.jivesoftware.stringprep.StringprepException;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * A Utility class to allow for creation and modification of workgroups and queues.
 *
 * @author Derek DeMoro
 */
public class WorkgroupUtils {

    public static String updateWorkgroup(String workgroupName, String displayName,
            String description, int maxSize, int minSize, long requestTimeout, long offerTimeout) 
    {
        final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        Workgroup workgroup;
        try {
            workgroup = workgroupManager.getWorkgroup(new JID(workgroupName));
        }
        catch (UserNotFoundException e) {
            return getUpdateMessage(false, "The JID specified is invalid.");
        }
        workgroup.setDisplayName(displayName);
        workgroup.setDescription(description);
        if (maxSize < minSize) {
            return getUpdateMessage(false, "Max size must be greater or equal to min size.");
        }

        workgroup.setMaxChats(maxSize);
        workgroup.setMinChats(minSize);
        workgroup.setRequestTimeout(requestTimeout);
        workgroup.setOfferTimeout(offerTimeout);


        return getUpdateMessage(true, "Workgroup has been updated");
    }

    public static void toggleStatus(String workgroupName) {
        final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        Workgroup workgroup;
        try {
            workgroup = workgroupManager.getWorkgroup(new JID(workgroupName));
        }
        catch (UserNotFoundException e) {
            return;
        }

        Workgroup.Status status = workgroup.getStatus();
        if (status == Workgroup.Status.READY) {
            workgroup.setStatus(Workgroup.Status.CLOSED);
        }
        else {
            workgroup.setStatus(Workgroup.Status.READY);
        }
    }

    public static String getUpdateMessage(boolean successfull, String message) {
        String returnString;
        if (successfull) {
            returnString = " <div class=\"jive-success\">\n" +
                    "            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
                    "                <tbody>\n" +
                    "                    <tr><td class=\"jive-icon\"><img src=\"images/success-16x16.gif\" width=\"16\" height=\"16\"\n" +
                    "                                                   border=\"0\"></td>\n" +
                    "                        <td class=\"jive-icon-label\">\n" +
                    "                            " + message + "\n" +
                    "                        </td></tr>\n" +
                    "                </tbody>\n" +
                    "            </table>\n" +
                    "        </div><br>";
        }
        else {
            returnString = "     <p class=\"jive-error-text\">\n" +
                    "            " + message + "\n" +
                    "            </p>";
        }

        return returnString;
    }

    public synchronized static List<AgentSelector> getAvailableAgentSelectors() {
        List<AgentSelector> answer = new ArrayList<AgentSelector>();
        // First, add in built-in list of algorithms.
        for (Class newClass : getBuiltInAgentSelectorClasses()) {
            try {
                AgentSelector algorithm = (AgentSelector)newClass.newInstance();
                answer.add(algorithm);
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }

        // Now get custom algorithms.
        List<String> classNames = JiveGlobals.getProperties("agentSelector.classes");
        for (String className : classNames) {
            install_algorithm:
            try {
                Class algorithmClass = loadClass(className);
                // Make sure that the interceptor isn't already installed.
                for (AgentSelector agentSelector : answer) {
                    if (algorithmClass.equals(agentSelector.getClass())) {
                        break install_algorithm;
                    }
                }
                AgentSelector algorithm = (AgentSelector)algorithmClass.newInstance();
                answer.add(algorithm);
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }
        return answer;
    }

    private static Collection<Class> getBuiltInAgentSelectorClasses() {
        return Arrays.asList((Class)BasicAgentSelector.class);
    }

    private static Class loadClass(String className) throws ClassNotFoundException {
        try {
            return ClassUtils.forName(className);
        }
        catch (ClassNotFoundException e) {
            return WorkgroupUtils.class.getClassLoader().loadClass(className);
        }
    }

    public synchronized static void addAgentSelectorClass(Class newClass) throws IllegalArgumentException {
        try {
            AgentSelector newAlgorithm = (AgentSelector)newClass.newInstance();
            // Make sure the interceptor isn't already in the list.
            List<AgentSelector> availableAgentSelectors = getAvailableAgentSelectors();
            for (AgentSelector algorithm : availableAgentSelectors) {
                if (newAlgorithm.getClass().equals(algorithm.getClass())) {
                    return;
                }
            }
            // Add in the new algorithm
            availableAgentSelectors.add(newAlgorithm);
            // Write out new class names.
            JiveGlobals.deleteProperty("agentSelector.classes");
            for (int i = 0; i < availableAgentSelectors.size(); i++) {
                String cName = availableAgentSelectors.get(i).getClass().getName();
                JiveGlobals.setProperty("agentSelector.classes." + i, cName);
            }
        }
        catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        catch (InstantiationException e2) {
            throw new IllegalArgumentException(e2.getMessage());
        }
        catch (ClassCastException e5) {
            throw new IllegalArgumentException("Class is not a AgentSelector");
        }

    }

    /**
     * Create a new Workgroup.
     *
     * @param workgroupName the name of the workgroup.
     * @param description the description of the workgroup.
     * @param agents the agents, in a comma delimited string.
     * @return a map of errors (if any)
     */
    public static Map createWorkgroup(String workgroupName, String description, String agents) {
        Map<String, String> errors = new HashMap<String, String>();

        // Get a workgroup manager
        WorkgroupManager wgManager = WorkgroupManager.getInstance();
        if (wgManager == null) {
            errors.put("general_error", "The server is down");
            return errors;
        }

        String defaultQueueName = "Default Queue";
        // Validate
        if (workgroupName == null) {
            errors.put("wgName", "");
        }
        else {
            try {
                workgroupName = workgroupName.trim().toLowerCase();
                workgroupName = Stringprep.nodeprep(workgroupName);
            }
            catch (StringprepException se) {
                errors.put("wgName", "");
            }
        }
        // do a create if there were no errors
        RequestQueue queue = null;
        if (errors.size() == 0) {
            try {
                // Create new workgroup
                Workgroup workgroup = wgManager.createWorkgroup(workgroupName);
                workgroup.setDescription(description);
                // Create a default workgroup queue
                queue = workgroup.createRequestQueue(defaultQueueName);
                //workgroup.setMaxChats(maxChats);
                //workgroup.setMinChats(minChats);
                // Make the workgroup ready by default:
                workgroup.setStatus(Workgroup.Status.READY);
                // Create default messages and images for the new workgroup
                ChatSettingsCreator.getInstance().createDefaultSettings(workgroup.getJID());

                // Add generic web form
                FormManager formManager = FormManager.getInstance();
                formManager.createGenericForm(workgroup);
            }
            catch (UserAlreadyExistsException uaee) {
                errors.put("exists", "");
            }
            catch (Exception e) {
                Log.error(e);
                errors.put("general", "");
            }
        }

        if (ModelUtil.hasLength(agents)) {
            addAgents(queue, agents);
        }
        return errors;
    }

    /**
     * Adds agents to a request queue.
     *
     * @param queue  the <code>RequestQueue</code> to add agents to.
     * @param agents a comma-delimited list of agents.
     */
    public static void addAgents(RequestQueue queue, String agents) {
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        AgentManager agentManager = workgroupManager.getAgentManager();

        // loop thru all params
        StringTokenizer tokenizer = new StringTokenizer(agents, ", \t\n\r\f");
        while (tokenizer.hasMoreTokens()) {
            String usernameToken = tokenizer.nextToken();
            if (usernameToken.indexOf('@') != -1) {
                usernameToken = JID.escapeNode(usernameToken);
            }
            try {
                // See if they are a user in the system.
                UserManager.getInstance().getUser(usernameToken);
                usernameToken += ("@" + ComponentManagerFactory.getComponentManager().getServerName());
                JID address = new JID(usernameToken.trim());
                Agent agent;

                if (agentManager.hasAgent(address)) {
                    agent = agentManager.getAgent(address);
                }
                else {
                    agent = agentManager.createAgent(address);
                }
                queue.addMember(agent);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }
}