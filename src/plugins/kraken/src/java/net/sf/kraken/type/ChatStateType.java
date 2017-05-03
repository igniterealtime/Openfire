/*
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.type;

/**
 * An enumeration for different chat state type.
 *
 * This class contains a list of all of the possible chat states.
 *
 * @author Daniel Henninger
 */
public enum ChatStateType {

    /**
     * Active (user is actively participating in a conversation)
     */
    active,

    /**
     * Composing (user is currently composing a message)
     */
    composing,

    /**
     * Paused (user has paused while composing a message)
     */
    paused,

    /**
     * Inactive (user has not interacted with the chat session for a short period of time)
     */
    inactive,

    /**
     * Gone (user has not interacted with the chat session for a long period of time)
     */
    gone

}
