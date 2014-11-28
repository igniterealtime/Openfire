/** File: observer.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 *   (c) 2012-2014 Patrick Stadler & Michael Weibel
 */
'use strict';

/* global Candy, Strophe, Mustache, jQuery */

/** Class: Candy.View.Observer
 * Observes Candy core events
 *
 * Parameters:
 *   (Candy.View.Observer) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Observer = (function(self, $) {
	/** PrivateVariable: _showConnectedMessageModal
	 * Ugly way to determine if the 'connected' modal should be shown.
	 * Is set to false in case no autojoin param is set.
	 */
	var _showConnectedMessageModal = true;

	/** Class: Candy.View.Observer.Chat
	 * Chat events
	 */
	self.Chat = {
		/** Function: Connection
		 * The update method gets called whenever an event to which "Chat" is subscribed.
		 *
		 * Currently listens for connection status updates
		 *
		 * Parameters:
		 *   (jQuery.Event) event - jQuery Event object
		 *   (Object) args - {status (Strophe.Status.*)}
		 */
		Connection: function(event, args) {
			var eventName = 'candy:view.connection.status-' + args.status;
			/** Event: candy:view.connection.status-<STROPHE-STATUS>
			 * Using this event, you can alter the default Candy (View) behaviour when reacting
			 * to connection updates.
			 *
			 * STROPHE-STATUS has to be replaced by one of <Strophe.Status at https://github.com/strophe/strophejs/blob/master/src/core.js#L276>:
			 *   - ERROR: 0,
			 *   - CONNECTING: 1,
			 *   - CONNFAIL: 2,
			 *   - AUTHENTICATING: 3,
			 *   - AUTHFAIL: 4,
			 *   - CONNECTED: 5,
			 *   - DISCONNECTED: 6,
			 *   - DISCONNECTING: 7,
			 *   - ATTACHED: 8
			 *
			 *
			 * If your event handler returns `false`, no View changes will take place.
			 * You can, of course, also return `true` and do custom things but still
			 * let Candy (View) do it's job.
			 *
			 * This event has been implemented due to <issue #202 at https://github.com/candy-chat/candy/issues/202>
			 * and here's an example use-case for it:
			 *
			 * (start code)
			 *   // react to DISCONNECTED event
			 *   $(Candy).on('candy:view.connection.status-6', function() {
			 *     // on next browser event loop
			 *     setTimeout(function() {
			 *       // reload page to automatically reattach on disconnect
			 *       window.location.reload();
			 *     }, 0);
			 *     // stop view changes right here.
			 *     return false;
			 *   });
			 * (end code)
			 */
			if($(Candy).triggerHandler(eventName) === false) {
				return false;
			}

			switch(args.status) {
				case Strophe.Status.CONNECTING:
				case Strophe.Status.AUTHENTICATING:
					Candy.View.Pane.Chat.Modal.show($.i18n._('statusConnecting'), false, true);
					break;
				case Strophe.Status.ATTACHED:
				case Strophe.Status.CONNECTED:
					if(_showConnectedMessageModal === true) {
						// only show 'connected' if the autojoin error is not shown
						// which is determined by having a visible modal in this stage.
						Candy.View.Pane.Chat.Modal.show($.i18n._('statusConnected'));
						Candy.View.Pane.Chat.Modal.hide();
					}
					break;

				case Strophe.Status.DISCONNECTING:
					Candy.View.Pane.Chat.Modal.show($.i18n._('statusDisconnecting'), false, true);
					break;

				case Strophe.Status.DISCONNECTED:
					var presetJid = Candy.Core.isAnonymousConnection() ? Strophe.getDomainFromJid(Candy.Core.getUser().getJid()) : null;
					Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._('statusDisconnected'), presetJid);
					break;

				case Strophe.Status.AUTHFAIL:
					Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._('statusAuthfail'));
					break;

				default:
					Candy.View.Pane.Chat.Modal.show($.i18n._('status', args.status));
					break;
			}
		},

		/** Function: Message
		 * Dispatches admin and info messages
		 *
		 * Parameters:
		 *   (jQuery.Event) event - jQuery Event object
		 *   (Object) args - {type (message/chat/groupchat), subject (if type = message), message}
		 */
		Message: function(event, args) {
			if(args.type === 'message') {
				Candy.View.Pane.Chat.adminMessage((args.subject || ''), args.message);
			} else if(args.type === 'chat' || args.type === 'groupchat') {
				// use onInfoMessage as infos from the server shouldn't be hidden by the infoMessage switch.
				Candy.View.Pane.Chat.onInfoMessage(Candy.View.getCurrent().roomJid, (args.subject || ''), args.message);
			}
		}
	};

	/** Class: Candy.View.Observer.Presence
	 * Presence update events
	 */
	self.Presence = {
		/** Function: update
		 * Every presence update gets dispatched from this method.
		 *
		 * Parameters:
		 *   (jQuery.Event) event - jQuery.Event object
		 *   (Object) args - Arguments differ on each type
		 *
		 * Uses:
		 *   - <notifyPrivateChats>
		 */
		update: function(event, args) {
			// Client left
			if(args.type === 'leave') {
				var user = Candy.View.Pane.Room.getUser(args.roomJid);
				Candy.View.Pane.Room.close(args.roomJid);
				self.Presence.notifyPrivateChats(user, args.type);
			// Client has been kicked or banned
			} else if (args.type === 'kick' || args.type === 'ban') {
				var actorName = args.actor ? Strophe.getNodeFromJid(args.actor) : null,
					actionLabel,
					translationParams = [args.roomName];

				if (actorName) {
					translationParams.push(actorName);
				}

				switch(args.type) {
					case 'kick':
						actionLabel = $.i18n._((actorName ? 'youHaveBeenKickedBy' : 'youHaveBeenKicked'), translationParams);
						break;
					case 'ban':
						actionLabel = $.i18n._((actorName ? 'youHaveBeenBannedBy' : 'youHaveBeenBanned'), translationParams);
						break;
				}
				Candy.View.Pane.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.adminMessageReason, {
					reason: args.reason,
					_action: actionLabel,
					_reason: $.i18n._('reasonWas', [args.reason])
				}));
				setTimeout(function() {
					Candy.View.Pane.Chat.Modal.hide(function() {
						Candy.View.Pane.Room.close(args.roomJid);
						self.Presence.notifyPrivateChats(args.user, args.type);
					});
				}, 5000);

				var evtData = { type: args.type, reason: args.reason, roomJid: args.roomJid, user: args.user };

				/** Event: candy:view.presence
				 * Presence update when kicked or banned
				 *
				 * Parameters:
				 *   (String) type - Presence type [kick, ban]
				 *   (String) reason - Reason for the kick|ban [optional]
				 *   (String) roomJid - Room JID
				 *   (Candy.Core.ChatUser) user - User which has been kicked or banned
				 */
				$(Candy).triggerHandler('candy:view.presence', [evtData]);

			// A user changed presence
			} else if(args.roomJid) {
				args.roomJid = Candy.Util.unescapeJid(args.roomJid);
				// Initialize room if not yet existing
				if(!Candy.View.Pane.Chat.rooms[args.roomJid]) {
					if(Candy.View.Pane.Room.init(args.roomJid, args.roomName) === false) {
						return false;
					}

					Candy.View.Pane.Room.show(args.roomJid);
				}
				Candy.View.Pane.Roster.update(args.roomJid, args.user, args.action, args.currentUser);
				// Notify private user chats if existing, but not in case the action is nickchange
				// -- this is because the nickchange presence already contains the new
				// user jid
				if(Candy.View.Pane.Chat.rooms[args.user.getJid()] && args.action !== 'nickchange') {
					Candy.View.Pane.Roster.update(args.user.getJid(), args.user, args.action, args.currentUser);
					Candy.View.Pane.PrivateRoom.setStatus(args.user.getJid(), args.action);
				}
			}
		},

		/** Function: notifyPrivateChats
		 * Notify private user chats if existing
		 *
		 * Parameters:
		 *   (Candy.Core.ChatUser) user - User which has done the event
		 *   (String) type - Event type (leave, join, kick/ban)
		 */
		notifyPrivateChats: function(user, type) {
			Candy.Core.log('[View:Observer] notify Private Chats');
			var roomJid;
			for(roomJid in Candy.View.Pane.Chat.rooms) {
				if(Candy.View.Pane.Chat.rooms.hasOwnProperty(roomJid) && Candy.View.Pane.Room.getUser(roomJid) && user.getJid() === Candy.View.Pane.Room.getUser(roomJid).getJid()) {
					Candy.View.Pane.Roster.update(roomJid, user, type, user);
					Candy.View.Pane.PrivateRoom.setStatus(roomJid, type);
				}
			}
		}
	};

	/** Function: Candy.View.Observer.PresenceError
	 * Presence errors get handled in this method
	 *
	 * Parameters:
	 *   (jQuery.Event) event - jQuery.Event object
	 *   (Object) args - {msg, type, roomJid, roomName}
	 */
	self.PresenceError = function(obj, args) {
		switch(args.type) {
			case 'not-authorized':
				var message;
				if (args.msg.children('x').children('password').length > 0) {
					message = $.i18n._('passwordEnteredInvalid', [args.roomName]);
				}
				Candy.View.Pane.Chat.Modal.showEnterPasswordForm(args.roomJid, args.roomName, message);
				break;
			case 'conflict':
				Candy.View.Pane.Chat.Modal.showNicknameConflictForm(args.roomJid);
				break;
			case 'registration-required':
				Candy.View.Pane.Chat.Modal.showError('errorMembersOnly', [args.roomName]);
				break;
			case 'service-unavailable':
				Candy.View.Pane.Chat.Modal.showError('errorMaxOccupantsReached', [args.roomName]);
				break;
		}
	};

	/** Function: Candy.View.Observer.Message
	 * Messages received get dispatched from this method.
	 *
	 * Parameters:
	 *   (jQuery.Event) event - jQuery Event object
	 *   (Object) args - {message, roomJid}
	 */
	self.Message = function(event, args) {
		if(args.message.type === 'subject') {
			if (!Candy.View.Pane.Chat.rooms[args.roomJid]) {
				Candy.View.Pane.Room.init(args.roomJid, args.message.name);
				Candy.View.Pane.Room.show(args.roomJid);
			}
			Candy.View.Pane.Room.setSubject(args.roomJid, args.message.body);
		} else if(args.message.type === 'info') {
			Candy.View.Pane.Chat.infoMessage(args.roomJid, args.message.body);
		} else {
			// Initialize room if it's a message for a new private user chat
			if(args.message.type === 'chat' && !Candy.View.Pane.Chat.rooms[args.roomJid]) {
				Candy.View.Pane.PrivateRoom.open(args.roomJid, args.message.name, false, args.message.isNoConferenceRoomJid);
			}
			Candy.View.Pane.Message.show(args.roomJid, args.message.name, args.message.body, args.message.xhtmlMessage, args.timestamp);
		}
	};

	/** Function: Candy.View.Observer.Login
	 * The login event gets dispatched to this method
	 *
	 * Parameters:
	 *   (jQuery.Event) event - jQuery Event object
	 *   (Object) args - {presetJid}
	 */
	self.Login = function(event, args) {
		Candy.View.Pane.Chat.Modal.showLoginForm(null, args.presetJid);
	};

	/** Class: Candy.View.Observer.AutojoinMissing
	 * Displays an error about missing autojoin information
	 */
	self.AutojoinMissing = function() {
		_showConnectedMessageModal = false;
		Candy.View.Pane.Chat.Modal.showError('errorAutojoinMissing');
	};

	return self;
}(Candy.View.Observer || {}, jQuery));
