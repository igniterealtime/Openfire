/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chatbot;

import org.jivesoftware.messenger.Entity;
import org.jivesoftware.messenger.ChannelHandler;

/**
 * <p>Represents any server-side plug-in that act as a user on the system.</p>
 * <p/>
 * <p>As a normal user on the system, chatbots have all the account resources of a
 * user but no need to authenticate. They must have unique chatbot IDs that do not
 * clash with user IDs since chatbots and users share the same resources (e.g.
 * private storage) and are unique identified to these resources by ID.</p>
 *
 * @author Iain Shigeoka
 */
public interface Chatbot extends Entity, ChannelHandler {
}
