/** File: event.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.View.Event
 * Empty hooks to capture events and inject custom code.
 *
 * Parameters:
 *   (Candy.View.Event) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Event = (function(self, $) {
	/** Class: Candy.View.Event.Chat
	 * Chat-related events
	 */
	self.Chat = {
		/** Function: onAdminMessage
		 * Called when receiving admin messages
		 *
		 * Parameters:
		 *   (Object) args - {subject, message}
		 */
		onAdminMessage: function(args) {
			return;
		},
		
		/** Function: onDisconnect
		 * Called when client disconnects
		 */
		onDisconnect: function() {
			return;
		},
		
		/** Function: onAuthfail
		 * Called when authentication fails
		 */
		onAuthfail: function() {
			return;
		}
	};

	/** Class: Candy.View.Event.Room
	 * Room-related events
	 */
	self.Room = {
		/** Function: onAdd
		 * Called when a new room gets added
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, type=chat|groupchat, element}
		 */
		onAdd: function(args) {
			return;
		},

		/** Function: onShow
		 * Called when a room gets shown
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, element}
		 */
		onShow: function(args) {
			return;
		},

		/** Function: onHide
		 * Called when a room gets hidden
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, element}
		 */
		onHide: function(args) {
			return;
		},

		/** Function: onSubjectChange
		 * Called when a subject of a room gets changed
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, element, subject}
		 */
		onSubjectChange: function(args) {
			return;
		},

		/** Function: onClose
		 * Called after a room has been left/closed
		 *
		 * Parameters:
		 *   (Object) args - {roomJid}
		 */
		onClose: function(args) {
			return;
		},
		
		/** Function: onPresenceChange
		 * Called when presence of user changes (kick, ban)
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, user, reason, type}
		 */
		onPresenceChange: function(args) {
			return;
		}
	};

	/** Class: Candy.View.Event.Roster
	 * Roster-related events
	 */
	self.Roster = {
		/** Function: onUpdate
		 * Called after a user have been added to the roster
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, user, action, element}
		 */
		onUpdate: function(args) {
			return;
		},

		/** Function: onContextMenu
		 * Called when a user clicks on the action menu arrow.
		 * The return value is getting appended to the menulinks.
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, user}
		 *
		 * Returns:
		 *   (Object) - containing menulinks
		 */
		onContextMenu: function(args) {
			return {};
		},
		
		/** Function: afterContextMenu
		 * Called when after a the context menu is rendered
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, element, user}
		 */
		afterContextMenu: function(args) {
			return;
		}
	};

	/** Class: Candy.View.Event.Message
	 * Message-related events
	 */
	self.Message = {
		/** Function: beforeShow
		 * Called before a new message will be shown.
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, nick, message}
		 *
		 * Returns:
		 *   (String) message
		 */
		beforeShow: function(args) {
			return args.message;
		},
		
		/** Function: onShow
		 * Called after a new message has been shown
		 *
		 * Parameters:
		 *   (Object) args - {roomJid, element, nick, message}
		 */
		onShow: function(args) {
			return;
		},
		
		/** Function: beforeSend
		 * Called before a message get sent
		 *
		 * Parameters:
		 *   (String) message
		 *
		 * Returns:
		 *   (String) message
		 */
		beforeSend: function(message) {
			return message;
		}
	};

	return self;
}(Candy.View.Event || {}, jQuery));