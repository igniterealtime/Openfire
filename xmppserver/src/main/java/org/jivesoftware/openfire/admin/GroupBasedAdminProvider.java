package org.jivesoftware.openfire.admin;

import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the users of specific group Openfire administrators.
 *
 * <p>To use, set the System Property `provider.admin.className` to the value `org.jivesoftware.openfire.admin.GroupBasedAdminProvider`</p>
 *
 * <p>The list of Openfire administrators will be taken from the members of the group defined by the System Property
 * `provider.group.groupBasedAdminProvider.groupName` - which defaults to `openfire-administrators`</p>
 *
 * <p><strong>NOTE: </strong> Although the system properties `provider.admin.className` and `provider.group.groupBasedAdminProvider.groupName` are dynamic, a restart may
 * be required as the list of admin users may be cached.</p>
 */
public class GroupBasedAdminProvider implements AdminProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupBasedAdminProvider.class);
    private static final SystemProperty<String> GROUP_NAME = SystemProperty.Builder.ofType(String.class)
        .setKey("provider.group.groupBasedAdminProvider.groupName")
        .setDefaultValue("openfire-administrators")
        .setDynamic(true)
        .build();

    @Override
    public List<JID> getAdmins() {
        final String groupName = GROUP_NAME.getValue();
        try {
            // Note; the list of admins is already cached, so if the list is being refreshed force a cache refresh too
            return new ArrayList<>(GroupManager.getInstance().getGroup(groupName, true).getMembers());
        } catch (GroupNotFoundException e) {
            LOGGER.error(String.format("Unable to retrieve members of group '%s' - assuming no administrators", groupName), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void setAdmins(List<JID> admins) {
        throw new UnsupportedOperationException("The GroupAdminProvider is read only");
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
