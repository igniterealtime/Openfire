package org.jivesoftware.openfire.pubsub;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.ParamUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

public class PubSubServiceInfo {
    private PubSubService pubSubService;

    private PubSubModule pubSubModule;
    private XMPPServer xmppServer;
    private UserManager userManager;
    private GroupManager groupManager;

    private String labelPreFix = "pubsub.service.form.";
    private String variablePreFix = "pubsub#";

    public PubSubServiceInfo(PubSubService pubSubService) {
        if (pubSubService == null) {
            throw new IllegalArgumentException("Argument 'pubSubService' cannot be null.");
        }
        this.pubSubService = pubSubService;

        xmppServer = XMPPServer.getInstance();
        pubSubModule = xmppServer.getPubSubModule();
        groupManager = GroupManager.getInstance();
        userManager = xmppServer.getUserManager();
    }

    public Collection<Node> getNodes() {
        return pubSubService.getNodes();
    }

    public Node getNode(String nodeID) {
        return pubSubService.getNode(nodeID);
    }

    public List<Node> getLeafNodes() {
        List<Node> leafNodes = new ArrayList<Node>();
        for (Node node : pubSubService.getNodes()) {
            if (!node.isCollectionNode()) {
                leafNodes.add(node);
            }
        }
        return leafNodes;
    }

    public CollectionNode getRootCollectionNode() {
        return pubSubService.getRootCollectionNode();
    }

    public String getServiceID() {
        return pubSubService.getServiceID();
    }

    /*
     * Returns a DataForm for configuring the pubsub service. Configurable fields
     * 'serviceEnabled', 'nodeCreationRestricted', 'allowedToCreate', 'sysadmins'
     * Some fields which appear to be configurable on the PubSubService interface
     * are not configurable due to the PubSubModule implementation these include:
     * 'MultipleSubscriptionsEnabled', 'InstantNodeSupported',
     * 'CollectionNodesSupported' they are therefore not included on the form.
     */
    public DataForm getServiceConfigurationForm() {

        DataForm form = new DataForm(DataForm.Type.result);

        FormField formField = form.addField();
        formField.setVariable(variablePreFix + "serviceEnabled");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString(labelPreFix + "serviceEnabled"));
        formField.addValue(pubSubModule.isServiceEnabled());

        formField = form.addField();
        formField.setVariable(variablePreFix + "nodeCreationRestricted");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString(labelPreFix + "nodeCreationRestricted"));
        formField.addValue(pubSubModule.isNodeCreationRestricted());

        formField = form.addField();
        formField.setVariable(variablePreFix + "allowedToCreate");
        formField.setType(FormField.Type.jid_multi);
        formField.setLabel(LocaleUtils.getLocalizedString(labelPreFix + "allowedToCreate"));
        for (String jid : pubSubModule.getUsersAllowedToCreate()) {
            formField.addValue(jid);
        }

        formField = form.addField();
        formField.setVariable(variablePreFix + "sysadmins");
        formField.setType(FormField.Type.jid_multi);
        formField.setLabel(LocaleUtils.getLocalizedString(labelPreFix + "sysadmins"));
        for (String jid : pubSubModule.getSysadmins()) {
            formField.addValue(jid);
        }

        return form;
    }

    public JID getValidJID(String username) {
        if (username != null && !username.isEmpty()) {
            try {
                if (username.contains("@")) {
                    JID jid = new JID(username);
                    if (userManager.isRegisteredUser(jid)) {
                        return jid;
                    }
                } else if (userManager.isRegisteredUser(username)) {
                    return xmppServer.createJID(username, null);
                }
            } catch (IllegalArgumentException e) {
            }
        }
        // Return null if JID is invalid or user not registered
        return null;
    }

    public boolean isValidGroup(String groupName) {
        if (groupName != null && !groupName.isEmpty()) {
            try {
                Group group = groupManager.getGroup(groupName);
                if (group != null) {
                    return true;
                }
            } catch (GroupNotFoundException e) {
            }
        }
        return false;
    }

    public DataForm processForm(DataForm form, HttpServletRequest request, Collection<String> excludedFields) {

        DataForm completedForm = new DataForm(DataForm.Type.submit);

        for (FormField field : form.getFields()) {

            if (excludedFields == null || !excludedFields.contains(field.getVariable())) {

                FormField completedField = completedForm.addField(field.getVariable(), field.getLabel(), field.getType());

                switch (field.getType()) {
                case boolean_type:
                    completedField.addValue(ParamUtils.getBooleanParameter(request, field.getVariable()));
                    break;
                case jid_multi:
                    for (String param : ParamUtils.getParameters(request, field.getVariable())) {
                        completedField.addValue(param);
                    }
                    break;
                case list_multi:
                    for (String param : ParamUtils.getParameters(request, field.getVariable())) {
                        completedField.addValue(param);
                    }
                    break;
                case list_single:
                    completedField.addValue(ParamUtils.getParameter(request, field.getVariable()));
                    break;
                case text_single:
                    completedField.addValue(ParamUtils.getParameter(request, field.getVariable()));
                    break;
                default:
                    break;
                }

                for(FormField.Option option: field.getOptions()) {
                    completedField.addOption(option.getLabel(), option.getValue());
                }

            }
        }
        return completedForm;
    }

    public void configureService(DataForm form) {

        for (FormField field : form.getFields()) {
            switch (field.getVariable().substring(field.getVariable().indexOf("#") + 1)) {
            case "serviceEnabled":
                if (field.getFirstValue() != null) {
                    pubSubModule.setServiceEnabled("1".equals(field.getFirstValue()));
                }
                break;
            case "nodeCreationRestricted":
                if (field.getFirstValue() != null) {
                    pubSubModule.setNodeCreationRestricted("1".equals(field.getFirstValue()));
                }
                break;
            case "allowedToCreate":
                pubSubModule.setUserAllowedToCreate(field.getValues());
                break;
            case "sysadmins":
                pubSubModule.setSysadmins(field.getValues());
                break;
            default:
                // Shouldn't end up here
                break;
            }
        }
    }

    public void validateAdditions(DataForm form, HttpServletRequest request, Map<String, listType> listTypes,
            Map<String, String> errors) {

        for (FormField field : form.getFields()) {
            if (listTypes.containsKey(field.getVariable())) {
                switch (listTypes.get(field.getVariable())) {
                case group:
                    if (ParamUtils.getParameter(request, field.getVariable() + "-Add") != null) {
                        String groupName = ParamUtils.getParameter(request, field.getVariable() + "-Additional");
                        if (isValidGroup(groupName)) {

                            if (!field.getValues().contains(groupName)) {
                                field.addValue(groupName);
                            } else {
                                // Group already in list
                                errors.put(field.getVariable(), LocaleUtils.getLocalizedString(
                                        "pubsub.form.already_in_list",
                                        Arrays.asList(LocaleUtils.getLocalizedString("pubsub.form.group"), groupName)));
                            }
                        } else {
                            // Not a valid group
                            errors.put(field.getVariable(), LocaleUtils.getLocalizedString("pubsub.form.not_valid",
                                    Arrays.asList(groupName, LocaleUtils.getLocalizedString("pubsub.form.group"))));
                        }
                    }

                    break;
                case user:
                    if (ParamUtils.getParameter(request, field.getVariable() + "-Add") != null) {
                        String username = ParamUtils.getParameter(request, field.getVariable() + "-Additional");
                        JID newUser = getValidJID(username);
                        if (newUser != null) {

                            if (!field.getValues().contains(newUser.toBareJID())) {
                                field.addValue(newUser.toBareJID());
                            } else {
                                // User already in list
                                errors.put(field.getVariable(), LocaleUtils.getLocalizedString(
                                        "pubsub.form.already_in_list",
                                        Arrays.asList(LocaleUtils.getLocalizedString("pubsub.form.user"), username)));
                            }
                        } else {
                            // Not a valid username
                            errors.put(field.getVariable(), LocaleUtils.getLocalizedString("pubsub.form.not_valid",
                                    Arrays.asList(username, LocaleUtils.getLocalizedString("pubsub.form.user"))));
                        }
                    }
                    break;
                default:
                    break;
                }
            }
        }
    }

    public enum listType {
        user, group;
    }

}
