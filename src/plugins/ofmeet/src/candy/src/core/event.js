/** File: event.js
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

/* global Candy, Strophe, jQuery */

/** Class: Candy.Core.Event
 * Chat Events
 *
 * Parameters:
 *   (Candy.Core.Event) self - itself
 *   (Strophe) Strophe - Strophe
 *   (jQuery) $ - jQuery
 */
Candy.Core.Event = (function(self, Strophe, $) {
	/** Function: Login
	 * Notify view that the login window should be displayed
	 *
	 * Parameters:
	 *   (String) presetJid - Preset user JID
	 *
	 * Triggers:
	 *   candy:core.login using {presetJid}
	 */
	self.Login = function(presetJid) {
		/** Event: candy:core.login
		 * Triggered when the login window should be displayed
		 *
		 * Parameters:
		 *   (String) presetJid - Preset user JID
		 */
		$(Candy).triggerHandler('candy:core.login', { presetJid: presetJid } );
	};

	/** Class: Candy.Core.Event.Strophe
	 * Strophe-related events
	 */
	self.Strophe = {
		/** Function: Connect
		 * Acts on strophe status events and notifies view.
		 *
		 * Parameters:
		 *   (Strophe.Status) status - Strophe statuses
		 *
		 * Triggers:
		 *   candy:core.chat.connection using {status}
		 */
		Connect: function(status) {
			Candy.Core.setStropheStatus(status);
			switch(status) {
				case Strophe.Status.CONNECTED:
					Candy.Core.log('[Connection] Connected');
					Candy.Core.Action.Jabber.GetJidIfAnonymous();
					/* falls through */
				case Strophe.Status.ATTACHED:
					Candy.Core.log('[Connection] Attached');
					Candy.Core.Action.Jabber.Presence();
					Candy.Core.Action.Jabber.Autojoin();
					Candy.Core.Action.Jabber.GetIgnoreList();
					break;

				case Strophe.Status.DISCONNECTED:
					Candy.Core.log('[Connection] Disconnected');
					break;

				case Strophe.Status.AUTHFAIL:
					Candy.Core.log('[Connection] Authentication failed');
					break;

				case Strophe.Status.CONNECTING:
					Candy.Core.log('[Connection] Connecting');
					break;

				case Strophe.Status.DISCONNECTING:
					Candy.Core.log('[Connection] Disconnecting');
					break;

				case Strophe.Status.AUTHENTICATING:
					Candy.Core.log('[Connection] Authenticating');
					break;

				case Strophe.Status.ERROR:
				case Strophe.Status.CONNFAIL:
					Candy.Core.log('[Connection] Failed (' + status + ')');
					break;

				default:
					Candy.Core.log('[Connection] What?!');
					break;
			}
			/** Event: candy:core.chat.connection
			 * Connection status updates
			 *
			 * Parameters:
			 *   (Strophe.Status) status - Strophe status
			 */
			$(Candy).triggerHandler('candy:core.chat.connection', { status: status } );
		}
	};

	/** Class: Candy.Core.Event.Jabber
	 * Jabber related events
	 */
	self.Jabber = {
		/** Function: Version
		 * Responds to a version request
		 *
		 * Parameters:
		 *   (String) msg - Raw XML Message
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
		Version: function(msg) {
			Candy.Core.log('[Jabber] Version');
			Candy.Core.Action.Jabber.Version($(msg));
			return true;
		},

		/** Function: Presence
		 * Acts on a presence event
		 *
		 * Parameters:
		 *   (String) msg - Raw XML Message
		 *
		 * Triggers:
		 *   candy:core.presence using {from, stanza}
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
		Presence: function(msg) {
			Candy.Core.log('[Jabber] Presence');
			msg = $(msg);
			if(msg.children('x[xmlns^="' + Strophe.NS.MUC + '"]').length > 0) {
				if (msg.attr('type') === 'error') {
					self.Jabber.Room.PresenceError(msg);
				} else {
					self.Jabber.Room.Presence(msg);
				}
			} else {
				/** Event: candy:core.presence
				 * Presence updates. Emitted only when not a muc presence.
				 *
				 * Parameters:
				 *   (JID) from - From Jid
				 *   (String) stanza - Stanza
				 */
				$(Candy).triggerHandler('candy:core.presence', {'from': msg.attr('from'), 'stanza': msg});
			}
			return true;
		},

		/** Function: Bookmarks
		 * Acts on a bookmarks event. When a bookmark has the attribute autojoin set, joins this room.
		 *
		 * Parameters:
		 *   (String) msg - Raw XML Message
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
		Bookmarks: function(msg) {
			Candy.Core.log('[Jabber] Bookmarks');
			// Autojoin bookmarks
			$('conference', msg).each(function() {
				var item = $(this);
				if(item.attr('autojoin')) {
					Candy.Core.Action.Jabber.Room.Join(item.attr('jid'));
				}
			});
			return true;
		},

		/** Function: PrivacyList
		 * Acts on a privacy list event and sets up the current privacy list of this user.
		 *
		 * If no privacy list has been added yet, create the privacy list and listen again to this event.
		 *
		 * Parameters:
		 *   (String) msg - Raw XML Message
		 *
		 * Returns:
		 *   (Boolean) - false to disable the handler after first call.
		 */
		PrivacyList: function(msg) {
			Candy.Core.log('[Jabber] PrivacyList');
			var currentUser = Candy.Core.getUser();
			msg = $(msg);
			if(msg.attr('type') === 'result') {
				$('list[name="ignore"] item', msg).each(function() {
					var item = $(this);
					if (item.attr('action') === 'deny') {
						currentUser.addToOrRemoveFromPrivacyList('ignore', item.attr('value'));
					}
				});
				Candy.Core.Action.Jabber.SetIgnoreListActive();
				return false;
			}
			return self.Jabber.PrivacyListError(msg);
		},

		/** Function: PrivacyListError
		 * Acts when a privacy list error has been received.
		 *
		 * Currently only handles the case, when a privacy list doesn't exist yet and creates one.
		 *
		 * Parameters:
		 *   (String) msg - Raw XML Message
		 *
		 * Returns:
		 *   (Boolean) - false to disable the handler after first call.
		 */
		PrivacyListError: function(msg) {
			Candy.Core.log('[Jabber] PrivacyListError');
			// check if msg says that privacyList doesn't exist
			if ($('error[code="404"][type="cancel"] item-not-found', msg)) {
				Candy.Core.Action.Jabber.ResetIgnoreList();
				Candy.Core.Action.Jabber.SetIgnoreListActive();
			}
			return false;
		},

		/** Function: Message
		 * Acts on room, admin and server messages and notifies the view if required.
		 *
		 * Parameters:
		 *   (String) msg - Raw XML Message
		 *
		 * Triggers:
		 *   candy:core.chat.message.admin using {type, message}
		 *   candy:core.chat.message.server {type, subject, message}
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
		Message: function(msg) {
			Candy.Core.log('[Jabber] Message');
			msg = $(msg);

			var fromJid = msg.attr('from'),
				type = msg.attr('type') || 'undefined',
				toJid = msg.attr('to');

			// Inspect the message type.
			if (type === 'normal' || type === 'undefined') {
				var mediatedInvite = msg.find('invite'),
					directInvite = msg.find('x[xmlns="jabber:x:conference"]');

				if(mediatedInvite.length > 0) {
					var passwordNode = msg.find('password'),
						password = null,
						continueNode = mediatedInvite.find('continue'),
						continuedThread = null;

					if(passwordNode) {
						password = passwordNode.text();
					}

					if(continueNode) {
						continuedThread = continueNode.attr('thread');
					}

					/** Event: candy:core:chat:invite
					 * Incoming chat invite for a MUC.
					 *
					 * Parameters:
					 *   (String) roomJid - The room the invite is to
					 *   (String) from - User JID that invite is from text
					 *   (String) reason - Reason for invite [default: '']
					 *   (String) password - Password for the room [default: null]
					 *   (String) continuedThread - The thread ID if this is a continuation of a 1-on-1 chat [default: null]
					 */
					$(Candy).triggerHandler('candy:core:chat:invite', {
						roomJid: fromJid,
						from: mediatedInvite.attr('from') || 'undefined',
						reason: mediatedInvite.find('reason').html() || '',
						password: password,
						continuedThread: continuedThread
					});
				}

				if(directInvite.length > 0) {
					/** Event: candy:core:chat:invite
					 * Incoming chat invite for a MUC.
					 *
					 * Parameters:
					 *   (String) roomJid - The room the invite is to
					 *   (String) from - User JID that invite is from text
					 *   (String) reason - Reason for invite [default: '']
					 *   (String) password - Password for the room [default: null]
					 *   (String) continuedThread - The thread ID if this is a continuation of a 1-on-1 chat [default: null]
					 */
					$(Candy).triggerHandler('candy:core:chat:invite', {
						roomJid: directInvite.attr('jid'),
						from: fromJid,
						reason: directInvite.attr('reason') || '',
						password: directInvite.attr('password'),
						continuedThread: directInvite.attr('thread')
					});
				}

				/** Event: candy:core:chat:message:normal
				 * Messages with the type attribute of normal or those
				 * that do not have the optional type attribute.
				 *
				 * Parameters:
				 *   (String) type - Type of the message [default: message]
				 *   (Object) message - Message object.
				 */
				// Detect message with type normal or with no type.
				$(Candy).triggerHandler('candy:core:chat:message:normal', {
					type: (type || 'normal'),
					message: msg
				});

				return true;
			} else if (type !== 'groupchat' && type !== 'chat' && type !== 'error' && type !== 'headline') {
				/** Event: candy:core:chat:message:other
				 * Messages with a type other than the ones listed in RFC3921
				 * section 2.1.1. This allows plugins to catch custom message
				 * types.
				 *
				 * Parameters:
				 *   (String) type - Type of the message [default: message]
				 *   (Object) message - Message object.
				 */
				// Detect message with type normal or with no type.
				$(Candy).triggerHandler('candy:core:chat:message:other', {
					type: type,
					message: msg
				});
				return true;
			}

			// Room message
			if(fromJid !== Strophe.getDomainFromJid(fromJid) && (type === 'groupchat' || type === 'chat' || type === 'error')) {
				self.Jabber.Room.Message(msg);
			// Admin message
			} else if(!toJid && fromJid === Strophe.getDomainFromJid(fromJid)) {
				/** Event: candy:core.chat.message.admin
				 * Admin message
				 *
				 * Parameters:
				 *   (String) type - Type of the message [default: message]
				 *   (String) message - Message text
				 */
				$(Candy).triggerHandler('candy:core.chat.message.admin', { type: (type || 'message'), message: msg.children('body').text() });
			// Server Message
			} else if(toJid && fromJid === Strophe.getDomainFromJid(fromJid)) {
				/** Event: candy:core.chat.message.server
				 * Server message (e.g. subject)
				 *
				 * Parameters:
				 *   (String) type - Message type [default: message]
				 *   (String) subject - Subject text
				 *   (String) message - Message text
				 */
				$(Candy).triggerHandler('candy:core.chat.message.server', {
					type: (type || 'message'),
					subject: msg.children('subject').text(),
					message: msg.children('body').text()
				});
			}
			return true;
		},

		/** Class: Candy.Core.Event.Jabber.Room
		 * Room specific events
		 */
		Room: {
			/** Function: Leave
			 * Leaves a room and cleans up related data and notifies view.
			 *
			 * Parameters:
			 *   (String) msg - Raw XML Message
			 *
			 * Triggers:
			 *   candy:core.presence.leave using {roomJid, roomName, type, reason, actor, user}
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			Leave: function(msg) {
				Candy.Core.log('[Jabber:Room] Leave');
				msg = $(msg);
				var from = Candy.Util.unescapeJid(msg.attr('from')),
					roomJid = Strophe.getBareJidFromJid(from);

				// if room is not joined yet, ignore.
				if (!Candy.Core.getRoom(roomJid)) {
					return true;
				}

				var roomName = Candy.Core.getRoom(roomJid).getName(),
					item = msg.find('item'),
					type = 'leave',
					reason,
					actor;

				delete Candy.Core.getRooms()[roomJid];
				// if user gets kicked, role is none and there's a status code 307
				if(item.attr('role') === 'none') {
					var code = msg.find('status').attr('code');
					if(code === '307') {
						type = 'kick';
					} else if(code === '301') {
						type = 'ban';
					}
					reason = item.find('reason').text();
					actor  = item.find('actor').attr('jid');
				}

				var user = new Candy.Core.ChatUser(from, Strophe.getResourceFromJid(from), item.attr('affiliation'), item.attr('role'));

				/** Event: candy:core.presence.leave
				 * When the local client leaves a room
				 *
				 * Also triggered when the local client gets kicked or banned from a room.
				 *
				 * Parameters:
				 *   (String) roomJid - Room
				 *   (String) roomName - Name of room
				 *   (String) type - Presence type [kick, ban, leave]
				 *   (String) reason - When type equals kick|ban, this is the reason the moderator has supplied.
				 *   (String) actor - When type equals kick|ban, this is the moderator which did the kick
				 *   (Candy.Core.ChatUser) user - user which leaves the room
				 */
				$(Candy).triggerHandler('candy:core.presence.leave', {
					'roomJid': roomJid,
					'roomName': roomName,
					'type': type,
					'reason': reason,
					'actor': actor,
					'user': user
				});
				return true;
			},

			/** Function: Disco
			 * Sets informations to rooms according to the disco info received.
			 *
			 * Parameters:
			 *   (String) msg - Raw XML Message
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			Disco: function(msg) {
				Candy.Core.log('[Jabber:Room] Disco');
				msg = $(msg);
				// Temp fix for #219
				// Don't go further if it's no conference disco reply
				// FIXME: Do this in a more beautiful way
				if(!msg.find('identity[category="conference"]').length) {
					return true;
				}
				var roomJid = Strophe.getBareJidFromJid(Candy.Util.unescapeJid(msg.attr('from')));

				// Client joined a room
				if(!Candy.Core.getRooms()[roomJid]) {
					Candy.Core.getRooms()[roomJid] = new Candy.Core.ChatRoom(roomJid);
				}
				// Room existed but room name was unknown
				var identity = msg.find('identity');
				if(identity.length) {
					var roomName = identity.attr('name'),
						room = Candy.Core.getRoom(roomJid);
					if(room.getName() === null) {
						room.setName(Strophe.unescapeNode(roomName));
					// Room name changed
					}/*else if(room.getName() !== roomName && room.getUser() !== null) {
						// NOTE: We want to notify the View here but jabber doesn't send anything when the room name changes :-(
					}*/
				}
				return true;
			},

			/** Function: Presence
			 * Acts on various presence messages (room leaving, room joining, error presence) and notifies view.
			 *
			 * Parameters:
			 *   (Object) msg - jQuery object of XML message
			 *
			 * Triggers:
			 *   candy:core.presence.room using {roomJid, roomName, user, action, currentUser}
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			Presence: function(msg) {
				Candy.Core.log('[Jabber:Room] Presence');
				var from = Candy.Util.unescapeJid(msg.attr('from')),
					roomJid = Strophe.getBareJidFromJid(from),
					presenceType = msg.attr('type'),
					status = msg.find('status'),
					nickAssign = false,
					nickChange = false;

				if(status.length) {
					// check if status code indicates a nick assignment or nick change
					for(var i = 0, l = status.length; i < l; i++) {
						var $status = $(status[i]),
							code = $status.attr('code');
						if(code === '303') {
							nickChange = true;
						} else if(code === '210') {
							nickAssign = true;
						}
					}
				}

				// Current User joined a room
				var room = Candy.Core.getRoom(roomJid);
				if(!room) {
					Candy.Core.getRooms()[roomJid] = new Candy.Core.ChatRoom(roomJid);
					room = Candy.Core.getRoom(roomJid);
				}

				// Current User left a room
				var currentUser = room.getUser() ? room.getUser() : Candy.Core.getUser();
				if(Strophe.getResourceFromJid(from) === currentUser.getNick() && presenceType === 'unavailable' && nickChange === false) {
					self.Jabber.Room.Leave(msg);
					return true;
				}

				var roster = room.getRoster(),
					action, user,
					nick,
					item = msg.find('item');
				// User joined a room
				if(presenceType !== 'unavailable') {
					if (roster.get(from)) {
						// role/affiliation change
						user = roster.get(from);

						var role = item.attr('role'),
							affiliation = item.attr('affiliation');

						user.setRole(role);
						user.setAffiliation(affiliation);

						// FIXME: currently role/affilation changes are handled with this action
						action = 'join';
					} else {
						nick = Strophe.getResourceFromJid(from);
						user = new Candy.Core.ChatUser(from, nick, item.attr('affiliation'), item.attr('role'));
						// Room existed but client (myself) is not yet registered
						if(room.getUser() === null && (Candy.Core.getUser().getNick() === nick || nickAssign)) {
							room.setUser(user);
							currentUser = user;
						}
						roster.add(user);
						action = 'join';
					}
				// User left a room
				} else {
					user = roster.get(from);
					roster.remove(from);
					if(nickChange) {
						// user changed nick
						nick = item.attr('nick');
						action = 'nickchange';
						user.setPreviousNick(user.getNick());
						user.setNick(nick);
						user.setJid(Strophe.getBareJidFromJid(from) + '/' + nick);
						roster.add(user);
					} else {
						action = 'leave';
						if(item.attr('role') === 'none') {
							if(msg.find('status').attr('code') === '307') {
								action = 'kick';
							} else if(msg.find('status').attr('code') === '301') {
								action = 'ban';
							}
						}
					}
				}
				/** Event: candy:core.presence.room
				 * Room presence updates
				 *
				 * Parameters:
				 *   (String) roomJid - Room JID
				 *   (String) roomName - Room name
				 *   (Candy.Core.ChatUser) user - User which does the presence update
				 *   (String) action - Action [kick, ban, leave, join]
				 *   (Candy.Core.ChatUser) currentUser - Current local user
				 */
				$(Candy).triggerHandler('candy:core.presence.room', {
					'roomJid': roomJid,
					'roomName': room.getName(),
					'user': user,
					'action': action,
					'currentUser': currentUser
				});
				return true;
			},

			/** Function: PresenceError
			 * Acts when a presence of type error has been retrieved.
			 *
			 * Parameters:
			 *   (Object) msg - jQuery object of XML message
			 *
			 * Triggers:
			 *   candy:core.presence.error using {msg, type, roomJid, roomName}
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			PresenceError: function(msg) {
				Candy.Core.log('[Jabber:Room] Presence Error');
				var from = Candy.Util.unescapeJid(msg.attr('from')),
					roomJid = Strophe.getBareJidFromJid(from),
					room = Candy.Core.getRooms()[roomJid],
					roomName = room.getName();

				// Presence error: Remove room from array to prevent error when disconnecting
				Candy.Core.removeRoom(roomJid);
				room = undefined;

				/** Event: candy:core.presence.error
				 * Triggered when a presence error happened
				 *
				 * Parameters:
				 *   (Object) msg - jQuery object of XML message
				 *   (String) type - Error type
				 *   (String) roomJid - Room jid
				 *   (String) roomName - Room name
				 */
				$(Candy).triggerHandler('candy:core.presence.error', {
					'msg' : msg,
					'type': msg.children('error').children()[0].tagName.toLowerCase(),
					'roomJid': roomJid,
					'roomName': roomName
				});
				return true;
			},

			/** Function: Message
			 * Acts on various message events (subject changed, private chat message, multi-user chat message)
			 * and notifies view.
			 *
			 * Parameters:
			 *   (String) msg - jQuery object of XML message
			 *
			 * Triggers:
			 *   candy:core.message using {roomJid, message, timestamp}
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			Message: function(msg) {
				Candy.Core.log('[Jabber:Room] Message');
				// Room subject
				var roomJid, message, name;
				if(msg.children('subject').length > 0 && msg.children('subject').text().length > 0 && msg.attr('type') === 'groupchat') {
					roomJid = Candy.Util.unescapeJid(Strophe.getBareJidFromJid(msg.attr('from')));
					message = { name: Strophe.getNodeFromJid(roomJid), body: msg.children('subject').text(), type: 'subject' };
				// Error messsage
				} else if(msg.attr('type') === 'error') {
					var error = msg.children('error');
					if(error.children('text').length > 0) {
						roomJid = msg.attr('from');
						message = { type: 'info', body: error.children('text').text() };
					}
				// Chat message
				} else if(msg.children('body').length > 0) {
					// Private chat message
					if(msg.attr('type') === 'chat' || msg.attr('type') === 'normal') {
						roomJid = Candy.Util.unescapeJid(msg.attr('from'));
						var bareRoomJid = Strophe.getBareJidFromJid(roomJid),
							// if a 3rd-party client sends a direct message to this user (not via the room) then the username is the node and not the resource.
							isNoConferenceRoomJid = !Candy.Core.getRoom(bareRoomJid);

						name = isNoConferenceRoomJid ? Strophe.getNodeFromJid(roomJid) : Strophe.getResourceFromJid(roomJid);
						message = { name: name, body: msg.children('body').text(), type: msg.attr('type'), isNoConferenceRoomJid: isNoConferenceRoomJid };
					// Multi-user chat message
					} else {
						roomJid = Candy.Util.unescapeJid(Strophe.getBareJidFromJid(msg.attr('from')));
						var resource = Strophe.getResourceFromJid(msg.attr('from'));
						// Message from a user
						if(resource) {
							resource = Strophe.unescapeNode(resource);
							message = { name: resource, body: msg.children('body').text(), type: msg.attr('type') };
						// Message from server (XEP-0045#registrar-statuscodes)
						} else {
							// we are not yet present in the room, let's just drop this message (issue #105)
							if(!Candy.View.Pane.Chat.rooms[msg.attr('from')]) {
								return true;
							}
							message = { name: '', body: msg.children('body').text(), type: 'info' };
						}
					}

					var xhtmlChild = msg.children('html[xmlns="' + Strophe.NS.XHTML_IM + '"]');
					if(Candy.View.getOptions().enableXHTML === true && xhtmlChild.length > 0) {
						var xhtmlMessage = xhtmlChild.children('body[xmlns="' + Strophe.NS.XHTML + '"]').first().html();
						message.xhtmlMessage = xhtmlMessage;
					}
				// Typing notification
				} else if(msg.children('composing').length > 0 || msg.children('inactive').length > 0 || msg.children('paused').length > 0) {
					roomJid = Candy.Util.unescapeJid(msg.attr('from'));
					name = Strophe.getResourceFromJid(roomJid);
					var chatstate;
					if(msg.children('composing').length > 0) {
						chatstate = 'composing';
					} else if(msg.children('paused').length > 0) {
						chatstate = 'paused';
					} else if(msg.children('inactive').length > 0) {
						chatstate = 'inactive';
					} else if(msg.children('gone').length > 0) {
						chatstate = 'gone';
					}
					/** Event: candy:core.message.chatstate
					 * Triggers on any recieved chatstate notification.
					 *
					 * The resulting message object contains the name of the person, the roomJid, and the indicated chatstate.
					 *
					 * The following lists explain those parameters:
					 *
					 * Message Object Parameters:
					 *   (String) name - User name
					 *   (String) roomJid - Room jid
					 *   (String) chatstate - Chatstate being indicated. ("paused", "inactive", "composing", "gone")
					 *
					 * TODO:
					 *   Perhaps handle blank "active" as specified by XEP-0085?
					 */
					$(Candy).triggerHandler('candy:core.message.chatstate', {
						name: name,
						roomJid: roomJid,
						chatstate: chatstate
					});
					return true;
				// Unhandled message
				} else {
					return true;
				}

				// besides the delayed delivery (XEP-0203), there exists also XEP-0091 which is the legacy delayed delivery.
				// the x[xmlns=jabber:x:delay] is the format in XEP-0091.
				var delay = msg.children('delay') ? msg.children('delay') : msg.children('x[xmlns="' + Strophe.NS.DELAY +'"]'),
					timestamp = delay !== undefined ? delay.attr('stamp') : null;

				/** Event: candy:core.message
				 * Triggers on various message events (subject changed, private chat message, multi-user chat message).
				 *
				 * The resulting message object can contain different key-value pairs as stated in the documentation
				 * of the parameters itself.
				 *
				 * The following lists explain those parameters:
				 *
				 * Message Object Parameters:
				 *   (String) name - Room name
				 *   (String) body - Message text
				 *   (String) type - Message type ([normal, chat, groupchat])
				 *                   or 'info' which is used internally for displaying informational messages
				 *   (Boolean) isNoConferenceRoomJid - if a 3rd-party client sends a direct message to
				 *                                     this user (not via the room) then the username is the node
				 *                                     and not the resource.
				 *                                     This flag tells if this is the case.
				 *
				 * Parameters:
				 *   (String) roomJid - Room jid
				 *   (Object) message - Depending on what kind of message, the object consists of different key-value pairs:
				 *                        - Room Subject: {name, body, type}
				 *                        - Error message: {type = 'info', body}
				 *                        - Private chat message: {name, body, type, isNoConferenceRoomJid}
				 *                        - MUC msg from a user: {name, body, type}
				 *                        - MUC msg from server: {name = '', body, type = 'info'}
				 *   (String) timestamp - Timestamp, only when it's an offline message
				 *
				 * TODO:
				 *   Streamline those events sent and rename the parameters.
				 */
				$(Candy).triggerHandler('candy:core.message', {
					roomJid: roomJid,
					message: message,
					timestamp: timestamp
				});
				return true;
			}
		}
	};

	return self;
}(Candy.Core.Event || {}, Strophe, jQuery));
