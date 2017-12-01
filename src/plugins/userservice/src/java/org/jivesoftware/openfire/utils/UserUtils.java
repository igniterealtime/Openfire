package org.jivesoftware.openfire.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.jivesoftware.openfire.entity.UserEntity;
import org.jivesoftware.openfire.entity.UserProperty;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;

/**
 * The Class UserUtils.
 */
public class UserUtils {

    /**
     * Instantiates a new user utils.
     */
    private UserUtils() {
        throw new AssertionError();
    }

    /**
     * Convert users to user entities.
     *
     * @param users
     *            the users
     * @param userSearch
     * @return the list
     */
    public static List<UserEntity> convertUsersToUserEntities(Collection<User> users, String userSearch) {
        List<UserEntity> result = new ArrayList<UserEntity>();

        for (User user : users) {
            if (userSearch != null) {
                if (!user.getUsername().contains(userSearch)) {
                    continue;
                }
            }

            result.add(convertUserToUserEntity(user));
        }
        return result;
    }

    /**
     * Convert user to user entity.
     *
     * @param user
     *            the user
     * @return the user entity
     */
    public static UserEntity convertUserToUserEntity(User user) {
        UserEntity userEntity = new UserEntity(user.getUsername(), user.getName(), user.getEmail());

        List<UserProperty> userProperties = new ArrayList<UserProperty>();
        for (Entry<String, String> property : user.getProperties().entrySet()) {
            userProperties.add(new UserProperty(property.getKey(), property.getValue()));
        }
        userEntity.setProperties(userProperties);

        return userEntity;
    }

    /**
     * Checks if is valid sub type.
     *
     * @param subType
     *            the sub type
     * @return true, if is valid sub type
     * @throws UserAlreadyExistsException
     */
    public static void checkSubType(int subType) throws UserAlreadyExistsException {
        if (!(subType >= -1 && subType <= 3)) {
            throw new UserAlreadyExistsException();
        }
    }
}
