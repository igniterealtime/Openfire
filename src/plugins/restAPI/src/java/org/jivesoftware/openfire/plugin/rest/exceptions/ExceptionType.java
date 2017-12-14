package org.jivesoftware.openfire.plugin.rest.exceptions;

/**
 * The Class ExceptionType.
 */
public final class ExceptionType {
    /** The Constant ILLEGAL_ARGUMENT_EXCEPTION. */
    public static final String ILLEGAL_ARGUMENT_EXCEPTION = "IllegalArgumentException";
    
    /** The Constant SHARED_GROUP_EXCEPTION. */
    public static final String SHARED_GROUP_EXCEPTION = "SharedGroupException";

    /** The Constant PROPERTY_NOT_FOUND. */
    public static final String PROPERTY_NOT_FOUND = "PropertyNotFoundException";

    /** The Constant USER_ALREADY_EXISTS_EXCEPTION. */
    public static final String USER_ALREADY_EXISTS_EXCEPTION = "UserAlreadyExistsException";

    /** The Constant USER_NOT_FOUND_EXCEPTION. */
    public static final String USER_NOT_FOUND_EXCEPTION = "UserNotFoundException";

    /** The Constant GROUP_ALREADY_EXISTS. */
    public static final String GROUP_ALREADY_EXISTS = "GroupAlreadyExistsException";

    /** The Constant GROUP_NOT_FOUND. */
    public static final String GROUP_NOT_FOUND = "GroupNotFoundException";
    
    /** The Constant ROOM_NOT_FOUND. */
    public static final String ROOM_NOT_FOUND = "RoomNotFoundException";

    /** The Constant NOT_ALLOWED. */
    public static final String NOT_ALLOWED = "NotAllowedException";

    /** The Constant ALREADY_EXISTS. */
    public static final String ALREADY_EXISTS = "AlreadyExistsException";
    /**
     * Instantiates a new exception type.
     */
    private ExceptionType() {
    }
}
