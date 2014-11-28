/** File: chatRoster.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 *   (c) 2012-2014 Patrick Stadler & Michael Weibel. All rights reserved.
 */
'use strict';

/* global Candy */

/** Class: Candy.Core.ChatRoster
 * Chat Roster
 */
Candy.Core.ChatRoster = function () {
	/** Object: items
	 * Roster items
	 */
	this.items = {};

	/** Function: add
	 * Add user to roster
	 *
	 * Parameters:
	 *   (Candy.Core.ChatUser) user - User to add
	 */
	this.add = function(user) {
		this.items[user.getJid()] = user;
	};

	/** Function: remove
	 * Remove user from roster
	 *
	 * Parameters:
	 *   (String) jid - User jid
	 */
	this.remove = function(jid) {
		delete this.items[jid];
	};

	/** Function: get
	 * Get user from roster
	 *
	 * Parameters:
	 *   (String) jid - User jid
	 *
	 * Returns:
	 *   (Candy.Core.ChatUser) - User
	 */
	this.get = function(jid) {
		return this.items[jid];
	};

	/** Function: getAll
	 * Get all items
	 *
	 * Returns:
	 *   (Object) - all roster items
	 */
	this.getAll = function() {
		return this.items;
	};
};
