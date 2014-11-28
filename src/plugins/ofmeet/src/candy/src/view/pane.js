/** File: pane.js
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

/* global Candy, document, Mustache, Strophe, Audio, jQuery */

/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = (function(self, $) {

	/** Class: Candy.View.Pane.Window
	 * Window related view updates
	 */
	self.Window = {
		/** PrivateVariable: _hasFocus
		 * Window has focus
		 */
		_hasFocus: true,
		/** PrivateVariable: _plainTitle
		 * Document title
		 */
		_plainTitle: document.title,
		/** PrivateVariable: _unreadMessagesCount
		 * Unread messages count
		 */
		_unreadMessagesCount: 0,

		/** Variable: autoscroll
		 * Boolean whether autoscroll is enabled
		 */
		autoscroll: true,

		/** Function: hasFocus
		 * Checks if window has focus
		 *
		 * Returns:
		 *   (Boolean)
		 */
		hasFocus: function() {
			return self.Window._hasFocus;
		},

		/** Function: increaseUnreadMessages
		 * Increases unread message count in window title by one.
		 */
		increaseUnreadMessages: function() {
			self.Window.renderUnreadMessages(++self.Window._unreadMessagesCount);
		},

		/** Function: reduceUnreadMessages
		 * Reduce unread message count in window title by `num`.
		 *
		 * Parameters:
		 *   (Integer) num - Unread message count will be reduced by this value
		 */
		reduceUnreadMessages: function(num) {
			self.Window._unreadMessagesCount -= num;
			if(self.Window._unreadMessagesCount <= 0) {
				self.Window.clearUnreadMessages();
			} else {
				self.Window.renderUnreadMessages(self.Window._unreadMessagesCount);
			}
		},

		/** Function: clearUnreadMessages
		 * Clear unread message count in window title.
		 */
		clearUnreadMessages: function() {
			self.Window._unreadMessagesCount = 0;
			document.title = self.Window._plainTitle;
		},

		/** Function: renderUnreadMessages
		 * Update window title to show message count.
		 *
		 * Parameters:
		 *   (Integer) count - Number of unread messages to show in window title
		 */
		renderUnreadMessages: function(count) {
			document.title = Candy.View.Template.Window.unreadmessages.replace('{{count}}', count).replace('{{title}}', self.Window._plainTitle);
		},

		/** Function: onFocus
		 * Window focus event handler.
		 */
		onFocus: function() {
			self.Window._hasFocus = true;
			if (Candy.View.getCurrent().roomJid) {
				self.Room.setFocusToForm(Candy.View.getCurrent().roomJid);
				self.Chat.clearUnreadMessages(Candy.View.getCurrent().roomJid);
			}
		},

		/** Function: onBlur
		 * Window blur event handler.
		 */
		onBlur: function() {
			self.Window._hasFocus = false;
		}
	};

	/** Class: Candy.View.Pane.Chat
	 * Chat-View related view updates
	 */
	self.Chat = {
		/** Variable: rooms
		 * Contains opened room elements
		 */
		rooms: [],

		/** Function: addTab
		 * Add a tab to the chat pane.
		 *
		 * Parameters:
		 *   (String) roomJid - JID of room
		 *   (String) roomName - Tab label
		 *   (String) roomType - Type of room: `groupchat` or `chat`
		 */
		addTab: function(roomJid, roomName, roomType) {
			var roomId = Candy.Util.jidToId(roomJid),
				html = Mustache.to_html(Candy.View.Template.Chat.tab, {
					roomJid: roomJid,
					roomId: roomId,
					name: roomName || Strophe.getNodeFromJid(roomJid),
					privateUserChat: function() {return roomType === 'chat';},
					roomType: roomType
				}),
				tab = $(html).appendTo('#chat-tabs');

			tab.click(self.Chat.tabClick);
			// TODO: maybe we find a better way to get the close element.
			$('a.close', tab).click(self.Chat.tabClose);

			self.Chat.fitTabs();
		},

		/** Function: getTab
		 * Get tab by JID.
		 *
		 * Parameters:
		 *   (String) roomJid - JID of room
		 *
		 * Returns:
		 *   (jQuery object) - Tab element
		 */
		getTab: function(roomJid) {
			return $('#chat-tabs').children('li[data-roomjid="' + roomJid + '"]');
		},

		/** Function: removeTab
		 * Remove tab element.
		 *
		 * Parameters:
		 *   (String) roomJid - JID of room
		 */
		removeTab: function(roomJid) {
			self.Chat.getTab(roomJid).remove();
			self.Chat.fitTabs();
		},

		/** Function: setActiveTab
		 * Set the active tab.
		 *
		 * Add CSS classname `active` to the choosen tab and remove `active` from all other.
		 *
		 * Parameters:
		 *   (String) roomJid - JID of room
		 */
		setActiveTab: function(roomJid) {
			$('#chat-tabs').children().each(function() {
				var tab = $(this);
				if(tab.attr('data-roomjid') === roomJid) {
					tab.addClass('active');
				} else {
					tab.removeClass('active');
				}
			});
		},

		/** Function: increaseUnreadMessages
		 * Increase unread message count in a tab by one.
		 *
		 * Parameters:
		 *   (String) roomJid - JID of room
		 *
		 * Uses:
		 *   - <Window.increaseUnreadMessages>
		 */
		increaseUnreadMessages: function(roomJid) {
			var unreadElem = this.getTab(roomJid).find('.unread');
			unreadElem.show().text(unreadElem.text() !== '' ? parseInt(unreadElem.text(), 10) + 1 : 1);
			// only increase window unread messages in private chats
			if (self.Chat.rooms[roomJid].type === 'chat') {
				self.Window.increaseUnreadMessages();
			}
		},

		/** Function: clearUnreadMessages
		 * Clear unread message count in a tab.
		 *
		 * Parameters:
		 *   (String) roomJid - JID of room
		 *
		 * Uses:
		 *   - <Window.reduceUnreadMessages>
		 */
		clearUnreadMessages: function(roomJid) {
			var unreadElem = self.Chat.getTab(roomJid).find('.unread');
			self.Window.reduceUnreadMessages(unreadElem.text());
			unreadElem.hide().text('');
		},

		/** Function: tabClick
		 * Tab click event: show the room associated with the tab and stops the event from doing the default.
		 */
		tabClick: function(e) {
			// remember scroll position of current room
			var currentRoomJid = Candy.View.getCurrent().roomJid;
			self.Chat.rooms[currentRoomJid].scrollPosition = self.Room.getPane(currentRoomJid, '.message-pane-wrapper').scrollTop();

			self.Room.show($(this).attr('data-roomjid'));
			e.preventDefault();
		},

		/** Function: tabClose
		 * Tab close (click) event: Leave the room (groupchat) or simply close the tab (chat).
		 *
		 * Parameters:
		 *   (DOMEvent) e - Event triggered
		 *
		 * Returns:
		 *   (Boolean) - false, this will stop the event from bubbling
		 */
		tabClose: function() {
			var roomJid = $(this).parent().attr('data-roomjid');
			// close private user tab
			if(self.Chat.rooms[roomJid].type === 'chat') {
				self.Room.close(roomJid);
			// close multi-user room tab
			} else {
				Candy.Core.Action.Jabber.Room.Leave(roomJid);
			}
			return false;
		},

		/** Function: allTabsClosed
		 * All tabs closed event: Disconnect from service. Hide sound control.
		 *
		 * TODO: Handle window close
		 *
		 * Returns:
		 *   (Boolean) - false, this will stop the event from bubbling
		 */
		allTabsClosed: function() {
			Candy.Core.disconnect();
			self.Chat.Toolbar.hide();
			return;
		},

		/** Function: fitTabs
		 * Fit tab size according to window size
		 */
		fitTabs: function() {
			var availableWidth = $('#chat-tabs').innerWidth(),
				tabsWidth = 0,
				tabs = $('#chat-tabs').children();
			tabs.each(function() {
				tabsWidth += $(this).css({width: 'auto', overflow: 'visible'}).outerWidth(true);
			});
			if(tabsWidth > availableWidth) {
				// tabs.[outer]Width() measures the first element in `tabs`. It's no very readable but nearly two times faster than using :first
				var tabDiffToRealWidth = tabs.outerWidth(true) - tabs.width(),
					tabWidth = Math.floor((availableWidth) / tabs.length) - tabDiffToRealWidth;
				tabs.css({width: tabWidth, overflow: 'hidden'});
			}
		},

		/** Function: adminMessage
		 * Display admin message
		 *
		 * Parameters:
		 *   (String) subject - Admin message subject
		 *   (String) message - Message to be displayed
		 *
		 * Triggers:
		 *   candy:view.chat.admin-message using {subject, message}
		 */
		adminMessage: function(subject, message) {
			if(Candy.View.getCurrent().roomJid) { // Simply dismiss admin message if no room joined so far. TODO: maybe we should show those messages on a dedicated pane?
				var html = Mustache.to_html(Candy.View.Template.Chat.adminMessage, {
					subject: subject,
					message: message,
					sender: $.i18n._('administratorMessageSubject'),
					time: Candy.Util.localizedTime(new Date().toGMTString())
				});
				$('#chat-rooms').children().each(function() {
					self.Room.appendToMessagePane($(this).attr('data-roomjid'), html);
				});
				self.Room.scrollToBottom(Candy.View.getCurrent().roomJid);

				/** Event: candy:view.chat.admin-message
				 * After admin message display
				 *
				 * Parameters:
				 *   (String) presetJid - Preset user JID
				 */
				$(Candy).triggerHandler('candy:view.chat.admin-message', {
					'subject' : subject,
					'message' : message
				});
			}
		},

		/** Function: infoMessage
		 * Display info message. This is a wrapper for <onInfoMessage> to be able to disable certain info messages.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 *   (String) subject - Subject
		 *   (String) message - Message
		 */
		infoMessage: function(roomJid, subject, message) {
			self.Chat.onInfoMessage(roomJid, subject, message);
		},

		/** Function: onInfoMessage
		 * Display info message. Used by <infoMessage> and several other functions which do not wish that their info message
		 * can be disabled (such as kick/ban message or leave/join message in private chats).
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 *   (String) subject - Subject
		 *   (String) message - Message
		 */
		onInfoMessage: function(roomJid, subject, message) {
			if(Candy.View.getCurrent().roomJid) { // Simply dismiss info message if no room joined so far. TODO: maybe we should show those messages on a dedicated pane?
				var html = Mustache.to_html(Candy.View.Template.Chat.infoMessage, {
					subject: subject,
					message: $.i18n._(message),
					time: Candy.Util.localizedTime(new Date().toGMTString())
				});
				self.Room.appendToMessagePane(roomJid, html);
				if (Candy.View.getCurrent().roomJid === roomJid) {
					self.Room.scrollToBottom(Candy.View.getCurrent().roomJid);
				}
			}
		},

		/** Class: Candy.View.Pane.Toolbar
		 * Chat toolbar for things like emoticons toolbar, room management etc.
		 */
		Toolbar: {
			_supportsNativeAudio: false,

			/** Function: init
			 * Register handler and enable or disable sound and status messages.
			 */
			init: function() {
				$('#emoticons-icon').click(function(e) {
				self.Chat.Context.showEmoticonsMenu(e.currentTarget);
					e.stopPropagation();
				});
				$('#chat-autoscroll-control').click(self.Chat.Toolbar.onAutoscrollControlClick);

				var a = document.createElement('audio');
				self.Chat.Toolbar._supportsNativeAudio = !!(a.canPlayType && a.canPlayType('audio/mpeg;').replace(/no/, ''));
				$('#chat-sound-control').click(self.Chat.Toolbar.onSoundControlClick);
				if(Candy.Util.cookieExists('candy-nosound')) {
					$('#chat-sound-control').click();
				}
				$('#chat-statusmessage-control').click(self.Chat.Toolbar.onStatusMessageControlClick);
				if(Candy.Util.cookieExists('candy-nostatusmessages')) {
					$('#chat-statusmessage-control').click();
				}
			},

			/** Function: show
			 * Show toolbar.
			 */
			show: function() {
				$('#chat-toolbar').show();
			},

			/** Function: hide
			 * Hide toolbar.
			 */
			hide: function() {
				$('#chat-toolbar').hide();
			},

			/* Function: update
			 * Update toolbar for specific room
			 */
			update: function(roomJid) {
				var context = $('#chat-toolbar').find('.context'),
					me = self.Room.getUser(roomJid);
				if(!me || !me.isModerator()) {
					context.hide();
				} else {
					context.show().click(function(e) {
						self.Chat.Context.show(e.currentTarget, roomJid);
						e.stopPropagation();
					});
				}
				self.Chat.Toolbar.updateUsercount(self.Chat.rooms[roomJid].usercount);
			},

			/** Function: playSound
			 * Play sound (default method).
			 */
			playSound: function() {
				self.Chat.Toolbar.onPlaySound();
			},

			/** Function: onPlaySound
			 * Sound play event handler. Uses native (HTML5) audio if supported
			 *
			 * Don't call this method directly. Call `playSound()` instead.
			 * `playSound()` will only call this method if sound is enabled.
			 */
			onPlaySound: function() {
				try {
					if(self.Chat.Toolbar._supportsNativeAudio) {
						new Audio(Candy.View.getOptions().assets + 'notify.mp3').play();
					} else {
						var chatSoundPlayer = document.getElementById('chat-sound-player');
						chatSoundPlayer.SetVariable('method:stop', '');
						chatSoundPlayer.SetVariable('method:play', '');
					}
				} catch (e) {}
			},

			/** Function: onSoundControlClick
			 * Sound control click event handler.
			 *
			 * Toggle sound (overwrite `playSound()`) and handle cookies.
			 */
			onSoundControlClick: function() {
				var control = $('#chat-sound-control');
				if(control.hasClass('checked')) {
					self.Chat.Toolbar.playSound = function() {};
					Candy.Util.setCookie('candy-nosound', '1', 365);
				} else {
					self.Chat.Toolbar.playSound = function() {
						self.Chat.Toolbar.onPlaySound();
					};
					Candy.Util.deleteCookie('candy-nosound');
				}
				control.toggleClass('checked');
			},

			/** Function: onAutoscrollControlClick
			 * Autoscroll control event handler.
			 *
			 * Toggle autoscroll
			 */
			onAutoscrollControlClick: function() {
				var control = $('#chat-autoscroll-control');
				if(control.hasClass('checked')) {
					self.Room.scrollToBottom = function(roomJid) {
						self.Room.onScrollToStoredPosition(roomJid);
					};
					self.Window.autoscroll = false;
				} else {
					self.Room.scrollToBottom = function(roomJid) {
						self.Room.onScrollToBottom(roomJid);
					};
					self.Room.scrollToBottom(Candy.View.getCurrent().roomJid);
					self.Window.autoscroll = true;
				}
				control.toggleClass('checked');
			},

			/** Function: onStatusMessageControlClick
			 * Status message control event handler.
			 *
			 * Toggle status message
			 */
			onStatusMessageControlClick: function() {
				var control = $('#chat-statusmessage-control');
				if(control.hasClass('checked')) {
					self.Chat.infoMessage = function() {};
					Candy.Util.setCookie('candy-nostatusmessages', '1', 365);
				} else {
					self.Chat.infoMessage = function(roomJid, subject, message) {
						self.Chat.onInfoMessage(roomJid, subject, message);
					};
					Candy.Util.deleteCookie('candy-nostatusmessages');
				}
				control.toggleClass('checked');
			},

			/** Function: updateUserCount
			 * Update usercount element with count.
			 *
			 * Parameters:
			 *   (Integer) count - Current usercount
			 */
			updateUsercount: function(count) {
				$('#chat-usercount').text(count);
			}
		},

		/** Class: Candy.View.Pane.Modal
		 * Modal window
		 */
		Modal: {
			/** Function: show
			 * Display modal window
			 *
			 * Parameters:
			 *   (String) html - HTML code to put into the modal window
			 *   (Boolean) showCloseControl - set to true if a close button should be displayed [default false]
			 *   (Boolean) showSpinner - set to true if a loading spinner should be shown [default false]
			 */
			show: function(html, showCloseControl, showSpinner) {
				if(showCloseControl) {
					self.Chat.Modal.showCloseControl();
				} else {
					self.Chat.Modal.hideCloseControl();
				}
				if(showSpinner) {
					self.Chat.Modal.showSpinner();
				} else {
					self.Chat.Modal.hideSpinner();
				}
				$('#chat-modal').stop(false, true);
				$('#chat-modal-body').html(html);
				$('#chat-modal').fadeIn('fast');
				$('#chat-modal-overlay').show();
			},

			/** Function: hide
			 * Hide modal window
			 *
			 * Parameters:
			 *   (Function) callback - Calls the specified function after modal window has been hidden.
			 */
			hide: function(callback) {
				$('#chat-modal').fadeOut('fast', function() {
					$('#chat-modal-body').text('');
					$('#chat-modal-overlay').hide();
				});
				// restore initial esc handling
				$(document).keydown(function(e) {
					if(e.which === 27) {
						e.preventDefault();
					}
				});
				if (callback) {
					callback();
				}
			},

			/** Function: showSpinner
			 * Show loading spinner
			 */
			showSpinner: function() {
				$('#chat-modal-spinner').show();
			},

			/** Function: hideSpinner
			 * Hide loading spinner
			 */
			hideSpinner: function() {
				$('#chat-modal-spinner').hide();
			},

			/** Function: showCloseControl
			 * Show a close button
			 */
			showCloseControl: function() {
				$('#admin-message-cancel').show().click(function(e) {
					self.Chat.Modal.hide();
					// some strange behaviour on IE7 (and maybe other browsers) triggers onWindowUnload when clicking on the close button.
					// prevent this.
					e.preventDefault();
				});

				// enable esc to close modal
				$(document).keydown(function(e) {
					if(e.which === 27) {
						self.Chat.Modal.hide();
						e.preventDefault();
					}
				});
			},

			/** Function: hideCloseControl
			 * Hide the close button
			 */
			hideCloseControl: function() {
				$('#admin-message-cancel').hide().click(function() {});
			},

			/** Function: showLoginForm
			 * Show the login form modal
			 *
			 * Parameters:
			 *  (String) message - optional message to display above the form
			 *	(String) presetJid - optional user jid. if set, the user will only be prompted for password.
			 */
			showLoginForm: function(message, presetJid) {
				self.Chat.Modal.show((message ? message : '') + Mustache.to_html(Candy.View.Template.Login.form, {
					_labelNickname: $.i18n._('labelNickname'),
					_labelUsername: $.i18n._('labelUsername'),
					_labelPassword: $.i18n._('labelPassword'),
					_loginSubmit: $.i18n._('loginSubmit'),
					displayPassword: !Candy.Core.isAnonymousConnection(),
					displayUsername: !presetJid,
					displayNickname: Candy.Core.isAnonymousConnection(),
					presetJid: presetJid ? presetJid : false
				}));
				$('#login-form').children(':input:first').focus();

				// register submit handler
				$('#login-form').submit(function() {
					var username = $('#username').val(),
						password = $('#password').val();

					if (!Candy.Core.isAnonymousConnection()) {
						// guess the input and create a jid out of it
						var jid = Candy.Core.getUser() && username.indexOf("@") < 0 ?
							username + '@' + Strophe.getDomainFromJid(Candy.Core.getUser().getJid()) : username;

						if(jid.indexOf("@") < 0 && !Candy.Core.getUser()) {
							Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._('loginInvalid'));
						} else {
							//Candy.View.Pane.Chat.Modal.hide();
							Candy.Core.connect(jid, password);
						}
					} else { // anonymous login
						Candy.Core.connect(presetJid, null, username);
					}
					return false;
				});
			},

			/** Function: showEnterPasswordForm
			 * Shows a form for entering room password
			 *
			 * Parameters:
			 *   (String) roomJid - Room jid to join
			 *   (String) roomName - Room name
			 *   (String) message - [optional] Message to show as the label
			 */
			showEnterPasswordForm: function(roomJid, roomName, message) {
				self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.PresenceError.enterPasswordForm, {
					roomName: roomName,
					_labelPassword: $.i18n._('labelPassword'),
					_label: (message ? message : $.i18n._('enterRoomPassword', [roomName])),
					_joinSubmit: $.i18n._('enterRoomPasswordSubmit')
				}), true);
				$('#password').focus();

				// register submit handler
				$('#enter-password-form').submit(function() {
					var password = $('#password').val();

					self.Chat.Modal.hide(function() {
						Candy.Core.Action.Jabber.Room.Join(roomJid, password);
					});
					return false;
				});
			},

			/** Function: showNicknameConflictForm
			 * Shows a form indicating that the nickname is already taken and
			 * for chosing a new nickname
			 *
			 * Parameters:
			 *   (String) roomJid - Room jid to join
			 */
			showNicknameConflictForm: function(roomJid) {
				self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.PresenceError.nicknameConflictForm, {
					_labelNickname: $.i18n._('labelNickname'),
					_label: $.i18n._('nicknameConflict'),
					_loginSubmit: $.i18n._('loginSubmit')
				}));
				$('#nickname').focus();

				// register submit handler
				$('#nickname-conflict-form').submit(function() {
					var nickname = $('#nickname').val();

					self.Chat.Modal.hide(function() {
						Candy.Core.getUser().data.nick = nickname;
						Candy.Core.Action.Jabber.Room.Join(roomJid);
					});
					return false;
				});
			},

			/** Function: showError
			 * Show modal containing error message
			 *
			 * Parameters:
			 *   (String) message - key of translation to display
			 *   (Array) replacements - array containing replacements for translation (%s)
			 */
			showError: function(message, replacements) {
				self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.PresenceError.displayError, {
					_error: $.i18n._(message, replacements)
				}), true);
			}
		},

		/** Class: Candy.View.Pane.Tooltip
		 * Class to display tooltips over specific elements
		 */
		Tooltip: {
			/** Function: show
			 * Show a tooltip on event.currentTarget with content specified or content within the target's attribute data-tooltip.
			 *
			 * On mouseleave on the target, hide the tooltip.
			 *
			 * Parameters:
			 *   (Event) event - Triggered event
			 *   (String) content - Content to display [optional]
			 */
			show: function(event, content) {
				var tooltip = $('#tooltip'),
					target = $(event.currentTarget);

				if(!content) {
					content = target.attr('data-tooltip');
				}

				if(tooltip.length === 0) {
					var html = Mustache.to_html(Candy.View.Template.Chat.tooltip);
					$('#chat-pane').append(html);
					tooltip = $('#tooltip');
				}

				$('#context-menu').hide();

				tooltip.stop(false, true);
				tooltip.children('div').html(content);

				var pos = target.offset(),
						posLeft = Candy.Util.getPosLeftAccordingToWindowBounds(tooltip, pos.left),
						posTop  = Candy.Util.getPosTopAccordingToWindowBounds(tooltip, pos.top);

				tooltip
					.css({'left': posLeft.px, 'top': posTop.px})
					.removeClass('left-top left-bottom right-top right-bottom')
					.addClass(posLeft.backgroundPositionAlignment + '-' + posTop.backgroundPositionAlignment)
					.fadeIn('fast');

				target.mouseleave(function(event) {
					event.stopPropagation();
					$('#tooltip').stop(false, true).fadeOut('fast', function() {$(this).css({'top': 0, 'left': 0});});
				});
			}
		},

		/** Class: Candy.View.Pane.Context
		 * Context menu for actions and settings
		 */
		Context: {
			/** Function: init
			 * Initialize context menu and setup mouseleave handler.
			 */
			init: function() {
				if ($('#context-menu').length === 0) {
					var html = Mustache.to_html(Candy.View.Template.Chat.Context.menu);
					$('#chat-pane').append(html);
					$('#context-menu').mouseleave(function() {
						$(this).fadeOut('fast');
					});
				}
			},

			/** Function: show
			 * Show context menu (positions it according to the window height/width)
			 *
			 * Parameters:
			 *   (Element) elem - On which element it should be shown
			 *   (String) roomJid - Room Jid of the room it should be shown
			 *   (Candy.Core.chatUser) user - User
			 *
			 * Uses:
			 *   <getMenuLinks> for getting menulinks the user has access to
			 *   <Candy.Util.getPosLeftAccordingToWindowBounds> for positioning
			 *   <Candy.Util.getPosTopAccordingToWindowBounds> for positioning
			 *
			 * Triggers:
			 *   candy:view.roster.after-context-menu using {roomJid, user, elements}
			 */
			show: function(elem, roomJid, user) {
				elem = $(elem);
				var roomId = self.Chat.rooms[roomJid].id,
					menu = $('#context-menu'),
					links = $('ul li', menu);

				$('#tooltip').hide();

				// add specific context-user class if a user is available (when context menu should be opened next to a user)
				if(!user) {
					user = Candy.Core.getUser();
				}

				links.remove();

				var menulinks = this.getMenuLinks(roomJid, user, elem),
					id,
					clickHandler = function(roomJid, user) {
						return function(event) {
							event.data.callback(event, roomJid, user);
							$('#context-menu').hide();
						};
					};

				for(id in menulinks) {
					if(menulinks.hasOwnProperty(id)) {
						var link = menulinks[id],
							html = Mustache.to_html(Candy.View.Template.Chat.Context.menulinks, {
								'roomId'   : roomId,
								'class'    : link['class'],
								'id'       : id,
								'label'    : link.label
							});
						$('ul', menu).append(html);
						$('#context-menu-' + id).bind('click', link, clickHandler(roomJid, user));
					}
				}
				// if `id` is set the menu is not empty
				if(id) {
					var pos = elem.offset(),
						posLeft = Candy.Util.getPosLeftAccordingToWindowBounds(menu, pos.left),
						posTop  = Candy.Util.getPosTopAccordingToWindowBounds(menu, pos.top);

					menu
						.css({'left': posLeft.px, 'top': posTop.px})
						.removeClass('left-top left-bottom right-top right-bottom')
						.addClass(posLeft.backgroundPositionAlignment + '-' + posTop.backgroundPositionAlignment)
						.fadeIn('fast');

					/** Event: candy:view.roster.after-context-menu
					 * After context menu display
					 *
					 * Parameters:
					 *   (String) roomJid - room where the context menu has been triggered
					 *   (Candy.Core.ChatUser) user - User
					 *   (jQuery.Element) element - Menu element
					 */
					$(Candy).triggerHandler('candy:view.roster.after-context-menu', {
						'roomJid' : roomJid,
						'user' : user,
						'element': menu
					});

					return true;
				}
			},

			/** Function: getMenuLinks
			 * Extends <initialMenuLinks> with menu links gathered from candy:view.roster.contextmenu
			 *
			 * Parameters:
			 *   (String) roomJid - Room in which the menu will be displayed
			 *   (Candy.Core.ChatUser) user - User
			 *   (jQuery.Element) elem - Parent element of the context menu
			 *
			 * Triggers:
			 *   candy:view.roster.context-menu using {roomJid, user, elem}
			 *
			 * Returns:
			 *   (Object) - object containing the extended menulinks.
			 */
			getMenuLinks: function(roomJid, user, elem) {
				var menulinks, id;

				var evtData = {
					'roomJid' : roomJid,
					'user' : user,
					'elem': elem,
					'menulinks': this.initialMenuLinks(elem)
				};

				/** Event: candy:view.roster.context-menu
				 * Modify existing menu links (add links)
				 *
				 * In order to modify the links you need to change the object passed with an additional
				 * key "menulinks" containing the menulink object.
				 *
				 * Parameters:
				 *   (String) roomJid - Room on which the menu should be displayed
				 *   (Candy.Core.ChatUser) user - User
				 *   (jQuery.Element) elem - Parent element of the context menu
				 */
				$(Candy).triggerHandler('candy:view.roster.context-menu', evtData);

				menulinks = evtData.menulinks;

				for(id in menulinks) {
					if(menulinks.hasOwnProperty(id) && menulinks[id].requiredPermission !== undefined && !menulinks[id].requiredPermission(user, self.Room.getUser(roomJid), elem)) {
						delete menulinks[id];
					}
				}
				return menulinks;
			},

			/** Function: initialMenuLinks
			 * Returns initial menulinks. The following are initial:
			 *
			 * - Private Chat
			 * - Ignore
			 * - Unignore
			 * - Kick
			 * - Ban
			 * - Change Subject
			 *
			 * Returns:
			 *   (Object) - object containing those menulinks
			 */
			initialMenuLinks: function() {
				return {
					'private': {
						requiredPermission: function(user, me) {
							return me.getNick() !== user.getNick() && Candy.Core.getRoom(Candy.View.getCurrent().roomJid) && !Candy.Core.getUser().isInPrivacyList('ignore', user.getJid());
						},
						'class' : 'private',
						'label' : $.i18n._('privateActionLabel'),
						'callback' : function(e, roomJid, user) {
							$('#user-' + Candy.Util.jidToId(roomJid) + '-' + Candy.Util.jidToId(user.getJid())).click();
						}
					},
					'ignore': {
						requiredPermission: function(user, me) {
							return me.getNick() !== user.getNick() && !Candy.Core.getUser().isInPrivacyList('ignore', user.getJid());
						},
						'class' : 'ignore',
						'label' : $.i18n._('ignoreActionLabel'),
						'callback' : function(e, roomJid, user) {
							Candy.View.Pane.Room.ignoreUser(roomJid, user.getJid());
						}
					},
					'unignore': {
						requiredPermission: function(user, me) {
							return me.getNick() !== user.getNick() && Candy.Core.getUser().isInPrivacyList('ignore', user.getJid());
						},
						'class' : 'unignore',
						'label' : $.i18n._('unignoreActionLabel'),
						'callback' : function(e, roomJid, user) {
							Candy.View.Pane.Room.unignoreUser(roomJid, user.getJid());
						}
					},
					'kick': {
						requiredPermission: function(user, me) {
							return me.getNick() !== user.getNick() && me.isModerator() && !user.isModerator();
						},
						'class' : 'kick',
						'label' : $.i18n._('kickActionLabel'),
						'callback' : function(e, roomJid, user) {
							self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.contextModalForm, {
								_label: $.i18n._('reason'),
								_submit: $.i18n._('kickActionLabel')
							}), true);
							$('#context-modal-field').focus();
							$('#context-modal-form').submit(function() {
								Candy.Core.Action.Jabber.Room.Admin.UserAction(roomJid, user.getJid(), 'kick', $('#context-modal-field').val());
								self.Chat.Modal.hide();
								return false; // stop propagation & preventDefault, as otherwise you get disconnected (wtf?)
							});
						}
					},
					'ban': {
						requiredPermission: function(user, me) {
							return me.getNick() !== user.getNick() && me.isModerator() && !user.isModerator();
						},
						'class' : 'ban',
						'label' : $.i18n._('banActionLabel'),
						'callback' : function(e, roomJid, user) {
							self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.contextModalForm, {
								_label: $.i18n._('reason'),
								_submit: $.i18n._('banActionLabel')
							}), true);
							$('#context-modal-field').focus();
							$('#context-modal-form').submit(function() {
								Candy.Core.Action.Jabber.Room.Admin.UserAction(roomJid, user.getJid(), 'ban', $('#context-modal-field').val());
								self.Chat.Modal.hide();
								return false; // stop propagation & preventDefault, as otherwise you get disconnected (wtf?)
							});
						}
					},
					'subject': {
						requiredPermission: function(user, me) {
							return me.getNick() === user.getNick() && me.isModerator();
						},
						'class': 'subject',
						'label' : $.i18n._('setSubjectActionLabel'),
						'callback': function(e, roomJid) {
							self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.contextModalForm, {
								_label: $.i18n._('subject'),
								_submit: $.i18n._('setSubjectActionLabel')
							}), true);
							$('#context-modal-field').focus();
							$('#context-modal-form').submit(function(e) {
								Candy.Core.Action.Jabber.Room.Admin.SetSubject(roomJid, $('#context-modal-field').val());
								self.Chat.Modal.hide();
								e.preventDefault();
							});
						}
					}
				};
			},

			/** Function: showEmoticonsMenu
			 * Shows the special emoticons menu
			 *
			 * Parameters:
			 *   (Element) elem - Element on which it should be positioned to.
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			showEmoticonsMenu: function(elem) {
				elem = $(elem);
				var pos = elem.offset(),
					menu = $('#context-menu'),
					content = $('ul', menu),
					emoticons = '',
					i;

				$('#tooltip').hide();

				for(i = Candy.Util.Parser.emoticons.length-1; i >= 0; i--) {
					emoticons = '<img src="' + Candy.Util.Parser._emoticonPath + Candy.Util.Parser.emoticons[i].image + '" alt="' + Candy.Util.Parser.emoticons[i].plain + '" />' + emoticons;
				}
				content.html('<li class="emoticons">' + emoticons + '</li>');
				content.find('img').click(function() {
					var input = Candy.View.Pane.Room.getPane(Candy.View.getCurrent().roomJid, '.message-form').children('.field'),
						value = input.val(),
						emoticon = $(this).attr('alt') + ' ';
					input.val(value ? value + ' ' + emoticon : emoticon).focus();
				});

				var posLeft = Candy.Util.getPosLeftAccordingToWindowBounds(menu, pos.left),
					posTop  = Candy.Util.getPosTopAccordingToWindowBounds(menu, pos.top);

				menu
					.css({'left': posLeft.px, 'top': posTop.px})
					.removeClass('left-top left-bottom right-top right-bottom')
					.addClass(posLeft.backgroundPositionAlignment + '-' + posTop.backgroundPositionAlignment)
					.fadeIn('fast');

				return true;
			}
		}
	};

	/** Class: Candy.View.Pane.Room
	 * Everything which belongs to room view things belongs here.
	 */
	self.Room = {
		/** Function: init
		 * Initialize a new room and inserts the room html into the DOM
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 *   (String) roomName - Room name
		 *   (String) roomType - Type: either "groupchat" or "chat" (private chat)
		 *
		 * Uses:
		 *   - <Candy.Util.jidToId>
		 *   - <Candy.View.Pane.Chat.addTab>
		 *   - <getPane>
		 *
		 * Triggers:
		 *   candy:view.room.after-add using {roomJid, type, element}
		 *
		 * Returns:
		 *   (String) - the room id of the element created.
		 */
		init: function(roomJid, roomName, roomType) {
			roomType = roomType || 'groupchat';
			roomJid = Candy.Util.unescapeJid(roomJid);

			var evtData = {
				roomJid: roomJid,
				type: roomType
			};
			/** Event: candy:view.room.before-add
			 * Before initialising a room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (String) type - Room Type
			 *
			 * Returns:
			 *   Boolean - if you don't want to initialise the room, return false.
			 */
			if($(Candy).triggerHandler('candy:view.room.before-add', evtData) === false) {
				return false;
			}

			// First room, show sound control
			if(Candy.Util.isEmptyObject(self.Chat.rooms)) {
				self.Chat.Toolbar.show();
			}

			var roomId = Candy.Util.jidToId(roomJid);
			self.Chat.rooms[roomJid] = {id: roomId, usercount: 0, name: roomName, type: roomType, messageCount: 0, scrollPosition: -1};

			$('#chat-rooms').append(Mustache.to_html(Candy.View.Template.Room.pane, {
				roomId: roomId,
				roomJid: roomJid,
				roomType: roomType,
				form: {
					_messageSubmit: $.i18n._('messageSubmit')
				},
				roster: {
					_userOnline: $.i18n._('userOnline')
				}
			}, {
				roster: Candy.View.Template.Roster.pane,
				messages: Candy.View.Template.Message.pane,
				form: Candy.View.Template.Room.form
			}));
			self.Chat.addTab(roomJid, roomName, roomType);
			self.Room.getPane(roomJid, '.message-form').submit(self.Message.submit);

			evtData.element = self.Room.getPane(roomJid);

			/** Event: candy:view.room.after-add
			 * After initialising a room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (String) type - Room Type
			 *   (jQuery.Element) element - Room element
			 */
			$(Candy).triggerHandler('candy:view.room.after-add', evtData);

			return roomId;
		},

		/** Function: show
		 * Show a specific room and hides the other rooms (if there are any)
		 *
		 * Parameters:
		 *   (String) roomJid - room jid to show
		 *
		 * Triggers:
		 *   candy:view.room.after-show using {roomJid, element}
		 *   candy:view.room.after-hide using {roomJid, element}
		 */
		show: function(roomJid) {
			var roomId = self.Chat.rooms[roomJid].id,
				evtData;

			$('.room-pane').each(function() {
				var elem = $(this);
				evtData = {
					'roomJid': elem.attr('data-roomjid'),
					'element' : elem
				};

				if(elem.attr('id') === ('chat-room-' + roomId)) {
					elem.show();
					Candy.View.getCurrent().roomJid = roomJid;
					self.Chat.setActiveTab(roomJid);
					self.Chat.Toolbar.update(roomJid);
					self.Chat.clearUnreadMessages(roomJid);
					self.Room.setFocusToForm(roomJid);
					self.Room.scrollToBottom(roomJid);

					/** Event: candy:view.room.after-show
					 * After showing a room
					 *
					 * Parameters:
					 *   (String) roomJid - Room JID
					 *   (jQuery.Element) element - Room element
					 */
					$(Candy).triggerHandler('candy:view.room.after-show', evtData);

				} else if(elem.is(':visible')) {
					elem.hide();

					/** Event: candy:view.room.after-hide
					 * After hiding a room
					 *
					 * Parameters:
					 *   (String) roomJid - Room JID
					 *   (jQuery.Element) element - Room element
					 */
					$(Candy).triggerHandler('candy:view.room.after-hide', evtData);
				}
			});
		},

		/** Function: setSubject
		 * Called when someone changes the subject in the channel
		 *
		 * Triggers:
		 *   candy:view.room.after-subject-change using {roomJid, element, subject}
		 *
		 * Parameters:
		 *   (String) roomJid - Room Jid
		 *   (String) subject - The new subject
		 */
		setSubject: function(roomJid, subject) {
			subject = Candy.Util.Parser.linkify(Candy.Util.Parser.escape(subject));
			var html = Mustache.to_html(Candy.View.Template.Room.subject, {
				subject: subject,
				roomName: self.Chat.rooms[roomJid].name,
				_roomSubject: $.i18n._('roomSubject'),
				time: Candy.Util.localizedTime(new Date().toGMTString())
			});
			self.Room.appendToMessagePane(roomJid, html);
			self.Room.scrollToBottom(roomJid);

			/** Event: candy:view.room.after-subject-change
			 * After changing the subject of a room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (jQuery.Element) element - Room element
			 *   (String) subject - New subject
			 */
			$(Candy).triggerHandler('candy:view.room.after-subject-change', {
				'roomJid': roomJid,
				'element' : self.Room.getPane(roomJid),
				'subject' : subject
			});
		},

		/** Function: close
		 * Close a room and remove everything in the DOM belonging to this room.
		 *
		 * NOTICE: There's a rendering bug in Opera when all rooms have been closed.
		 *         (Take a look in the source for a more detailed description)
		 *
		 * Triggers:
		 *   candy:view.room.after-close using {roomJid}
		 *
		 * Parameters:
		 *   (String) roomJid - Room to close
		 */
		close: function(roomJid) {
			self.Chat.removeTab(roomJid);
			self.Window.clearUnreadMessages();

			/* TODO:
				There's a rendering bug in Opera which doesn't redraw (remove) the message form.
				Only a cosmetical issue (when all tabs are closed) but it's annoying...
				This happens when form has no focus too. Maybe it's because of CSS positioning.
			*/
			self.Room.getPane(roomJid).remove();
			var openRooms = $('#chat-rooms').children();
			if(Candy.View.getCurrent().roomJid === roomJid) {
				Candy.View.getCurrent().roomJid = null;
				if(openRooms.length === 0) {
					self.Chat.allTabsClosed();
				} else {
					self.Room.show(openRooms.last().attr('data-roomjid'));
				}
			}
			delete self.Chat.rooms[roomJid];

			/** Event: candy:view.room.after-close
			 * After closing a room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 */
			$(Candy).triggerHandler('candy:view.room.after-close', {
				'roomJid' : roomJid
			});
		},

		/** Function: appendToMessagePane
		 * Append a new message to the message pane.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 *   (String) html - rendered message html
		 */
		appendToMessagePane: function(roomJid, html) {
			self.Room.getPane(roomJid, '.message-pane').append(html);
			self.Chat.rooms[roomJid].messageCount++;
			self.Room.sliceMessagePane(roomJid);
		},

		/** Function: sliceMessagePane
		 * Slices the message pane after the max amount of messages specified in the Candy View options (limit setting).
		 *
		 * This is done to hopefully prevent browsers from getting slow after a certain amount of messages in the DOM.
		 *
		 * The slice is only done when autoscroll is on, because otherwise someone might lose exactly the message he want to look for.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 */
		sliceMessagePane: function(roomJid) {
			// Only clean if autoscroll is enabled
			if(self.Window.autoscroll) {
				var options = Candy.View.getOptions().messages;
				if(self.Chat.rooms[roomJid].messageCount > options.limit) {
					self.Room.getPane(roomJid, '.message-pane').children().slice(0, options.remove).remove();
					self.Chat.rooms[roomJid].messageCount -= options.remove;
				}
			}
		},

		/** Function: scrollToBottom
		 * Scroll to bottom wrapper for <onScrollToBottom> to be able to disable it by overwriting the function.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 *
		 * Uses:
		 *   - <onScrollToBottom>
		 */
		scrollToBottom: function(roomJid) {
			self.Room.onScrollToBottom(roomJid);
		},

		/** Function: onScrollToBottom
		 * Scrolls to the latest message received/sent.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 */
		onScrollToBottom: function(roomJid) {
			var messagePane = self.Room.getPane(roomJid, '.message-pane-wrapper');
			messagePane.scrollTop(messagePane.prop('scrollHeight'));
		},

		/** Function: onScrollToStoredPosition
		 * When autoscroll is off, the position where the scrollbar is has to be stored for each room, because it otherwise
		 * goes to the top in the message window.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 */
		onScrollToStoredPosition: function(roomJid) {
			// This should only apply when entering a room...
			// ... therefore we set scrollPosition to -1 after execution.
			if(self.Chat.rooms[roomJid].scrollPosition > -1) {
				var messagePane = self.Room.getPane(roomJid, '.message-pane-wrapper');
				messagePane.scrollTop(self.Chat.rooms[roomJid].scrollPosition);
				self.Chat.rooms[roomJid].scrollPosition = -1;
			}
		},

		/** Function: setFocusToForm
		 * Set focus to the message input field within the message form.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID
		 */
		setFocusToForm: function(roomJid) {
			var pane = self.Room.getPane(roomJid, '.message-form');
			if (pane) {
				// IE8 will fail maybe, because the field isn't there yet.
				try {
					pane.children('.field')[0].focus();
				} catch(e) {
					// fail silently
				}
			}
		},

		/** Function: setUser
		 * Sets or updates the current user in the specified room (called by <Candy.View.Pane.Roster.update>) and set specific informations
		 * (roles and affiliations) on the room tab (chat-pane).
		 *
		 * Parameters:
		 *   (String) roomJid - Room in which the user is set to.
		 *   (Candy.Core.ChatUser) user - The user
		 */
		setUser: function(roomJid, user) {
			self.Chat.rooms[roomJid].user = user;
			var roomPane = self.Room.getPane(roomJid),
				chatPane = $('#chat-pane');

			roomPane.attr('data-userjid', user.getJid());
			// Set classes based on user role / affiliation
			if(user.isModerator()) {
				if (user.getRole() === user.ROLE_MODERATOR) {
					chatPane.addClass('role-moderator');
				}
				if (user.getAffiliation() === user.AFFILIATION_OWNER) {
					chatPane.addClass('affiliation-owner');
				}
			} else {
				chatPane.removeClass('role-moderator affiliation-owner');
			}
			self.Chat.Context.init();
		},

		/** Function: getUser
		 * Get the current user in the room specified with the jid
		 *
		 * Parameters:
		 *   (String) roomJid - Room of which the user should be returned from
		 *
		 * Returns:
		 *   (Candy.Core.ChatUser) - user
		 */
		getUser: function(roomJid) {
			return self.Chat.rooms[roomJid].user;
		},

		/** Function: ignoreUser
		 * Ignore specified user and add the ignore icon to the roster item of the user
		 *
		 * Parameters:
		 *   (String) roomJid - Room in which the user should be ignored
		 *   (String) userJid - User which should be ignored
		 */
		ignoreUser: function(roomJid, userJid) {
			Candy.Core.Action.Jabber.Room.IgnoreUnignore(userJid);
			Candy.View.Pane.Room.addIgnoreIcon(roomJid, userJid);
		},

		/** Function: unignoreUser
		 * Unignore an ignored user and remove the ignore icon of the roster item.
		 *
		 * Parameters:
		 *   (String) roomJid - Room in which the user should be unignored
		 *   (String) userJid - User which should be unignored
		 */
		unignoreUser: function(roomJid, userJid) {
			Candy.Core.Action.Jabber.Room.IgnoreUnignore(userJid);
			Candy.View.Pane.Room.removeIgnoreIcon(roomJid, userJid);
		},

		/** Function: addIgnoreIcon
		 * Add the ignore icon to the roster item of the specified user
		 *
		 * Parameters:
		 *   (String) roomJid - Room in which the roster item should be updated
		 *   (String) userJid - User of which the roster item should be updated
		 */
		addIgnoreIcon: function(roomJid, userJid) {
			if (Candy.View.Pane.Chat.rooms[userJid]) {
				$('#user-' + Candy.View.Pane.Chat.rooms[userJid].id + '-' + Candy.Util.jidToId(userJid)).addClass('status-ignored');
			}
			if (Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)]) {
				$('#user-' + Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)].id + '-' + Candy.Util.jidToId(userJid)).addClass('status-ignored');
			}
		},

		/** Function: removeIgnoreIcon
		 * Remove the ignore icon to the roster item of the specified user
		 *
		 * Parameters:
		 *   (String) roomJid - Room in which the roster item should be updated
		 *   (String) userJid - User of which the roster item should be updated
		 */
		removeIgnoreIcon: function(roomJid, userJid) {
			if (Candy.View.Pane.Chat.rooms[userJid]) {
				$('#user-' + Candy.View.Pane.Chat.rooms[userJid].id + '-' + Candy.Util.jidToId(userJid)).removeClass('status-ignored');
			}
			if (Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)]) {
				$('#user-' + Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)].id + '-' + Candy.Util.jidToId(userJid)).removeClass('status-ignored');
			}
		},

		/** Function: getPane
		 * Get the chat room pane or a subPane of it (if subPane is specified)
		 *
		 * Parameters:
		 *   (String) roomJid - Room in which the pane lies
		 *   (String) subPane - Sub pane of the chat room pane if needed [optional]
		 */
		getPane: function(roomJid, subPane) {
			if (self.Chat.rooms[roomJid]) {
				if(subPane) {
					if(self.Chat.rooms[roomJid]['pane-' + subPane]) {
						return self.Chat.rooms[roomJid]['pane-' + subPane];
					} else {
						self.Chat.rooms[roomJid]['pane-' + subPane] = $('#chat-room-' + self.Chat.rooms[roomJid].id).find(subPane);
						return self.Chat.rooms[roomJid]['pane-' + subPane];
					}
				} else {
					return $('#chat-room-' + self.Chat.rooms[roomJid].id);
				}
			}
		},

		/** Function: changeDataUserJidIfUserIsMe
		 * Changes the room's data-userjid attribute if the specified user is the current user.
		 *
		 * Parameters:
		 *   (String) roomId - Id of the room
		 *   (Candy.Core.ChatUser) user - User
		 */
		changeDataUserJidIfUserIsMe: function(roomId, user) {
			if (user.getNick() === Candy.Core.getUser().getNick()) {
				var roomElement = $('#chat-room-' + roomId);
				roomElement.attr('data-userjid', Strophe.getBareJidFromJid(roomElement.attr('data-userjid')) + '/' + user.getNick());
			}
		}
	};

	/** Class: Candy.View.Pane.PrivateRoom
	 * Private room handling
	 */
	self.PrivateRoom = {
		/** Function: open
		 * Opens a new private room
		 *
		 * Parameters:
		 *   (String) roomJid - Room jid to open
		 *   (String) roomName - Room name
		 *   (Boolean) switchToRoom - If true, displayed room switches automatically to this room
		 *                            (e.g. when user clicks itself on another user to open a private chat)
		 *   (Boolean) isNoConferenceRoomJid - true if a 3rd-party client sends a direct message to this user (not via the room)
		 *										then the username is the node and not the resource. This param addresses this case.
		 *
		 * Triggers:
		 *   candy:view.private-room.after-open using {roomJid, type, element}
		 */
		open: function(roomJid, roomName, switchToRoom, isNoConferenceRoomJid) {
			var user = isNoConferenceRoomJid ? Candy.Core.getUser() : self.Room.getUser(Strophe.getBareJidFromJid(roomJid)),
				evtData = {
					'roomJid': roomJid,
					'roomName': roomName,
					'type': 'chat',
				};

			/** Event: candy:view.private-room.before-open
			 * Before opening a new private room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (String) roomName - Room name
			 *   (String) type - 'chat'
			 *
			 * Returns:
			 *   Boolean - if you don't want to open the private room, return false
			 */
			if($(Candy).triggerHandler('candy:view.private-room.before-open', evtData) === false) {
				return false;
			}

			// if target user is in privacy list, don't open the private chat.
			if (Candy.Core.getUser().isInPrivacyList('ignore', roomJid)) {
				return false;
			}
			if(!self.Chat.rooms[roomJid]) {
				if(self.Room.init(roomJid, roomName, 'chat') === false) {
					return false;
				}
			}
			if(switchToRoom) {
				self.Room.show(roomJid);
			}

			self.Roster.update(roomJid, new Candy.Core.ChatUser(roomJid, roomName), 'join', user);
			self.Roster.update(roomJid, user, 'join', user);
			self.PrivateRoom.setStatus(roomJid, 'join');



			// We can't track the presence of a user if it's not a conference jid
			if(isNoConferenceRoomJid) {
				self.Chat.infoMessage(roomJid, $.i18n._('presenceUnknownWarningSubject'), $.i18n._('presenceUnknownWarning'));
			}

			evtData.element = self.Room.getPane(roomJid);
			/** Event: candy:view.private-room.after-open
			 * After opening a new private room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (String) type - 'chat'
			 *   (jQuery.Element) element - User element
			 */
			$(Candy).triggerHandler('candy:view.private-room.after-open', evtData);
		},

		/** Function: setStatus
		 * Set offline or online status for private rooms (when one of the participants leaves the room)
		 *
		 * Parameters:
		 *   (String) roomJid - Private room jid
		 *   (String) status - "leave"/"join"
		 */
		setStatus: function(roomJid, status) {
			var messageForm = self.Room.getPane(roomJid, '.message-form');
			if(status === 'join') {
				self.Chat.getTab(roomJid).addClass('online').removeClass('offline');

				messageForm.children('.field').removeAttr('disabled');
				messageForm.children('.submit').removeAttr('disabled');

				self.Chat.getTab(roomJid);
			} else if(status === 'leave') {
				self.Chat.getTab(roomJid).addClass('offline').removeClass('online');

				messageForm.children('.field').attr('disabled', true);
				messageForm.children('.submit').attr('disabled', true);
			}
		},

		/** Function: changeNick
		 * Changes the nick for every private room opened with this roomJid.
		 *
		 * Parameters:
		 *   (String) roomJid - Public room jid
		 *   (Candy.Core.ChatUser) user - User which changes his nick
		 */
		changeNick: function changeNick(roomJid, user) {
			Candy.Core.log('[View:Pane:PrivateRoom] changeNick');

			var previousPrivateRoomJid = roomJid + '/' + user.getPreviousNick(),
				newPrivateRoomJid = roomJid + '/' + user.getNick(),
				previousPrivateRoomId = Candy.Util.jidToId(previousPrivateRoomJid),
				newPrivateRoomId = Candy.Util.jidToId(newPrivateRoomJid),
				room = self.Chat.rooms[previousPrivateRoomJid],
				roomElement,
				roomTabElement;

			// it could happen that the new private room is already existing -> close it first.
			// if this is not done, errors appear as two rooms would have the same id
			if (self.Chat.rooms[newPrivateRoomJid]) {
				self.Room.close(newPrivateRoomJid);
			}

			if (room) { /* someone I talk with, changed nick */
				room.name = user.getNick();
				room.id   = newPrivateRoomId;

				self.Chat.rooms[newPrivateRoomJid] = room;
				delete self.Chat.rooms[previousPrivateRoomJid];

				roomElement = $('#chat-room-' + previousPrivateRoomId);
				if (roomElement) {
					roomElement.attr('data-roomjid', newPrivateRoomJid);
					roomElement.attr('id', 'chat-room-' + newPrivateRoomId);

					roomTabElement = $('#chat-tabs li[data-roomjid="' + previousPrivateRoomJid + '"]');
					roomTabElement.attr('data-roomjid', newPrivateRoomJid);

					/* TODO: The '@' is defined in the template. Somehow we should
					 * extract both things into our CSS or do something else to prevent that.
					 */
					roomTabElement.children('a.label').text('@' + user.getNick());

					if (Candy.View.getCurrent().roomJid === previousPrivateRoomJid) {
						Candy.View.getCurrent().roomJid = newPrivateRoomJid;
					}
				}
			} else { /* I changed the nick */
				roomElement = $('.room-pane.roomtype-chat[data-userjid="' + previousPrivateRoomJid + '"]');
				if (roomElement.length) {
					previousPrivateRoomId = Candy.Util.jidToId(roomElement.attr('data-roomjid'));
					roomElement.attr('data-userjid', newPrivateRoomJid);
				}
			}
			if (roomElement && roomElement.length) {
				self.Roster.changeNick(previousPrivateRoomId, user);
			}
		}
	};

	/** Class Candy.View.Pane.Roster
	 * Handles everyhing regarding roster updates.
	 */
	self.Roster = {
		/** Function: update
		 * Called by <Candy.View.Observer.Presence.update> to update the roster if needed.
		 * Adds/removes users from the roster list or updates informations on their items (roles, affiliations etc.)
		 *
		 * TODO: Refactoring, this method has too much LOC.
		 *
		 * Parameters:
		 *   (String) roomJid - Room JID in which the update happens
		 *   (Candy.Core.ChatUser) user - User on which the update happens
		 *   (String) action - one of "join", "leave", "kick" and "ban"
		 *   (Candy.Core.ChatUser) currentUser - Current user
		 *
		 * Triggers:
		 *   candy:view.roster.before-update using {roomJid, user, action, element}
		 *   candy:view.roster.after-update using {roomJid, user, action, element}
		 */
		update: function(roomJid, user, action, currentUser) {
			Candy.Core.log('[View:Pane:Roster] ' + action);
			var roomId = self.Chat.rooms[roomJid].id,
				userId = Candy.Util.jidToId(user.getJid()),
				usercountDiff = -1,
				userElem = $('#user-' + roomId + '-' + userId),
				evtData = {
					'roomJid' : roomJid,
					'user' : user,
					'action': action,
					'element': userElem
				};

			/** Event: candy:view.roster.before-update
			 * Before updating the roster of a room
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (Candy.Core.ChatUser) user - User
			 *   (String) action - [join, leave, kick, ban]
			 *   (jQuery.Element) element - User element
			 */
			$(Candy).triggerHandler('candy:view.roster.before-update', evtData);

			// a user joined the room
			if(action === 'join') {
				usercountDiff = 1;
				var html = Mustache.to_html(Candy.View.Template.Roster.user, {
						roomId: roomId,
						userId : userId,
						userJid: user.getJid(),
						nick: user.getNick(),
						displayNick: Candy.Util.crop(user.getNick(), Candy.View.getOptions().crop.roster.nickname),
						role: user.getRole(),
						affiliation: user.getAffiliation(),
						me: currentUser !== undefined && user.getNick() === currentUser.getNick(),
						tooltipRole: $.i18n._('tooltipRole'),
						tooltipIgnored: $.i18n._('tooltipIgnored')
					});

				if(userElem.length < 1) {
					var userInserted = false,
						rosterPane = self.Room.getPane(roomJid, '.roster-pane');

					// there are already users in the roster
					if(rosterPane.children().length > 0) {
						// insert alphabetically
						var userSortCompare = user.getNick().toUpperCase();
						rosterPane.children().each(function() {
							var elem = $(this);
							if(elem.attr('data-nick').toUpperCase() > userSortCompare) {
								elem.before(html);
								userInserted = true;
								return false;
							}
							return true;
						});
					}
					// first user in roster
					if(!userInserted) {
						rosterPane.append(html);
					}

					self.Roster.showJoinAnimation(user, userId, roomId, roomJid, currentUser);
				// user is in room but maybe the affiliation/role has changed
				} else {
					usercountDiff = 0;
					userElem.replaceWith(html);
					$('#user-' + roomId + '-' + userId).css({opacity: 1}).show();
					// it's me, update the toolbar
					if(currentUser !== undefined && user.getNick() === currentUser.getNick() && self.Room.getUser(roomJid)) {
						self.Chat.Toolbar.update(roomJid);
					}
				}

				// Presence of client
				if (currentUser !== undefined && currentUser.getNick() === user.getNick()) {
					self.Room.setUser(roomJid, user);
				// add click handler for private chat
				} else {
					$('#user-' + roomId + '-' + userId).click(self.Roster.userClick);
				}

				$('#user-' + roomId + '-' + userId + ' .context').click(function(e) {
					self.Chat.Context.show(e.currentTarget, roomJid, user);
					e.stopPropagation();
				});

				// check if current user is ignoring the user who has joined.
				if (currentUser !== undefined && currentUser.isInPrivacyList('ignore', user.getJid())) {
					Candy.View.Pane.Room.addIgnoreIcon(roomJid, user.getJid());
				}
			// a user left the room
			} else if(action === 'leave') {
				self.Roster.leaveAnimation('user-' + roomId + '-' + userId);
				// always show leave message in private room, even if status messages have been disabled
				if (self.Chat.rooms[roomJid].type === 'chat') {
					self.Chat.onInfoMessage(roomJid, $.i18n._('userLeftRoom', [user.getNick()]));
				} else {
					self.Chat.infoMessage(roomJid, $.i18n._('userLeftRoom', [user.getNick()]));
				}

			} else if(action === 'nickchange') {
				usercountDiff = 0;
				self.Roster.changeNick(roomId, user);
				self.Room.changeDataUserJidIfUserIsMe(roomId, user);
				self.PrivateRoom.changeNick(roomJid, user);
				var infoMessage = $.i18n._('userChangedNick', [user.getPreviousNick(), user.getNick()]);
				self.Chat.onInfoMessage(roomJid, infoMessage);
			// user has been kicked
			} else if(action === 'kick') {
				self.Roster.leaveAnimation('user-' + roomId + '-' + userId);
				self.Chat.onInfoMessage(roomJid, $.i18n._('userHasBeenKickedFromRoom', [user.getNick()]));
			// user has been banned
			} else if(action === 'ban') {
				self.Roster.leaveAnimation('user-' + roomId + '-' + userId);
				self.Chat.onInfoMessage(roomJid, $.i18n._('userHasBeenBannedFromRoom', [user.getNick()]));
			}

			// Update user count
			Candy.View.Pane.Chat.rooms[roomJid].usercount += usercountDiff;

			if(roomJid === Candy.View.getCurrent().roomJid) {
				Candy.View.Pane.Chat.Toolbar.updateUsercount(Candy.View.Pane.Chat.rooms[roomJid].usercount);
			}


			// in case there's been a join, the element is now there (previously not)
			evtData.element = $('#user-' + roomId + '-' + userId);
			/** Event: candy:view.roster.after-update
			 * After updating a room's roster
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (Candy.Core.ChatUser) user - User
			 *   (String) action - [join, leave, kick, ban]
			 *   (jQuery.Element) element - User element
			 */
			$(Candy).triggerHandler('candy:view.roster.after-update', evtData);
		},

		/** Function: userClick
		 * Click handler for opening a private room
		 */
		userClick: function() {
			var elem = $(this);
			self.PrivateRoom.open(elem.attr('data-jid'), elem.attr('data-nick'), true);
		},

		/** Function: showJoinAnimation
		 * Shows join animation if needed
		 *
		 * FIXME: Refactor. Part of this will be done by the big room improvements
		 */
		showJoinAnimation: function(user, userId, roomId, roomJid, currentUser) {
			// don't show if the user has recently changed the nickname.
			var rosterUserId = 'user-' + roomId + '-' + userId,
				$rosterUserElem = $('#' + rosterUserId);
			if (!user.getPreviousNick() || !$rosterUserElem || $rosterUserElem.is(':visible') === false) {
				self.Roster.joinAnimation(rosterUserId);
				// only show other users joining & don't show if there's no message in the room.
				if(currentUser !== undefined && user.getNick() !== currentUser.getNick() && self.Room.getUser(roomJid)) {
					// always show join message in private room, even if status messages have been disabled
					if (self.Chat.rooms[roomJid].type === 'chat') {
						self.Chat.onInfoMessage(roomJid, $.i18n._('userJoinedRoom', [user.getNick()]));
					} else {
						self.Chat.infoMessage(roomJid, $.i18n._('userJoinedRoom', [user.getNick()]));
					}
				}
			}
		},

		/** Function: joinAnimation
		 * Animates specified elementId on join
		 *
		 * Parameters:
		 *   (String) elementId - Specific element to do the animation on
		 */
		joinAnimation: function(elementId) {
			$('#' + elementId).stop(true).slideDown('normal', function() {
				$(this).animate({opacity: 1});
			});
		},

		/** Function: leaveAnimation
		 * Leave animation for specified element id and removes the DOM element on completion.
		 *
		 * Parameters:
		 *   (String) elementId - Specific element to do the animation on
		 */
		leaveAnimation: function(elementId) {
			$('#' + elementId).stop(true).attr('id', '#' + elementId + '-leaving').animate({opacity: 0}, {
				complete: function() {
					$(this).slideUp('normal', function() {
						$(this).remove();
					});
				}
			});
		},

		/** Function: changeNick
		 * Change nick of an existing user in the roster
		 *
		 * UserId has to be recalculated from the user because at the time of this call,
		 * the user is already set with the new jid & nick.
		 *
		 * Parameters:
		 *   (String) roomId - Id of the room
		 *   (Candy.Core.ChatUser) user - User object
		 */
		changeNick: function(roomId, user) {
			Candy.Core.log('[View:Pane:Roster] changeNick');
			var previousUserJid = Strophe.getBareJidFromJid(user.getJid()) + '/' + user.getPreviousNick(),
				elementId = 'user-' + roomId + '-' + Candy.Util.jidToId(previousUserJid),
				el = $('#' + elementId);

			el.attr('data-nick', user.getNick());
			el.attr('data-jid', user.getJid());
			el.children('div.label').text(user.getNick());
			el.attr('id', 'user-' + roomId + '-' + Candy.Util.jidToId(user.getJid()));
		}
	};

	/** Class: Candy.View.Pane.Message
	 * Message submit/show handling
	 */
	self.Message = {
		/** Function: submit
		 * on submit handler for message field sends the message to the server and if it's a private chat, shows the message
		 * immediately because the server doesn't send back those message.
		 *
		 * Parameters:
		 *   (Event) event - Triggered event
		 *
		 * Triggers:
		 *   candy:view.message.before-send using {message}
		 *
		 * FIXME: as everywhere, `roomJid` might be slightly incorrect in this case
		 *        - maybe rename this as part of a refactoring.
		 */
		submit: function(event) {
			var roomJid = Candy.View.getCurrent().roomJid,
				roomType = Candy.View.Pane.Chat.rooms[roomJid].type,
				message = $(this).children('.field').val().substring(0, Candy.View.getOptions().crop.message.body),
				xhtmlMessage,
				evtData = {
					roomJid: roomJid,
					message: message,
					xhtmlMessage: xhtmlMessage
				};

			/** Event: candy:view.message.before-send
			 * Before sending a message
			 *
			 * Parameters:
			 *   (String) roomJid - room to which the message should be sent
			 *   (String) message - Message text
			 *   (String) xhtmlMessage - XHTML formatted message [default: undefined]
			 *
			 * Returns:
			 *   Boolean|undefined - if you like to stop sending the message, return false.
			 */
			if($(Candy).triggerHandler('candy:view.message.before-send', evtData) === false) {
				event.preventDefault();
				return;
			}

			message = evtData.message;
			xhtmlMessage = evtData.xhtmlMessage;

			Candy.Core.Action.Jabber.Room.Message(roomJid, message, roomType, xhtmlMessage);
			// Private user chat. Jabber won't notify the user who has sent the message. Just show it as the user hits the button...
			if(roomType === 'chat' && message) {
				self.Message.show(roomJid, self.Room.getUser(roomJid).getNick(), message);
			}
			// Clear input and set focus to it
			$(this).children('.field').val('').focus();
			event.preventDefault();
		},

		/** Function: show
		 * Show a message in the message pane
		 *
		 * Parameters:
		 *   (String) roomJid - room in which the message has been sent to
		 *   (String) name - Name of the user which sent the message
		 *   (String) message - Message
		 *   (String) xhtmlMessage - XHTML formatted message [if options enableXHTML is true]
		 *   (String) timestamp - [optional] Timestamp of the message, if not present, current date.
		 *
		 * Triggers:
		 *   candy:view.message.before-show using {roomJid, name, message}
		 *   candy.view.message.before-render using {template, templateData}
		 *   candy:view.message.after-show using {roomJid, name, message, element}
		 */
		show: function(roomJid, name, message, xhtmlMessage, timestamp) {
			message = Candy.Util.Parser.all(message.substring(0, Candy.View.getOptions().crop.message.body));
			if(xhtmlMessage) {
				xhtmlMessage = Candy.Util.parseAndCropXhtml(xhtmlMessage, Candy.View.getOptions().crop.message.body);
			}

			var evtData = {
				'roomJid': roomJid,
				'name': name,
				'message': message,
				'xhtmlMessage': xhtmlMessage
			};

			/** Event: candy:view.message.before-show
			 * Before showing a new message
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (String) name - Name of the sending user
			 *   (String) message - Message text
			 *
			 * Returns:
			 *   Boolean - if you don't want to show the message, return false
			 */
			if($(Candy).triggerHandler('candy:view.message.before-show', evtData) === false) {
				return;
			}

			message = evtData.message;
			xhtmlMessage = evtData.xhtmlMessage;
			if(xhtmlMessage !== undefined && xhtmlMessage.length > 0) {
				message = xhtmlMessage;
			}

			if(!message) {
				return;
			}

			var renderEvtData = {
				template: Candy.View.Template.Message.item,
				templateData: {
					name: name,
					displayName: Candy.Util.crop(name, Candy.View.getOptions().crop.message.nickname),
					message: message,
					time: Candy.Util.localizedTime(timestamp || new Date().toGMTString())
				}
			};

			/** Event: candy:view.message.before-render
			 * Before rendering the message element
			 *
			 * Parameters:
			 *   (String) template - Template to use
			 *   (Object) templateData - Template data consists of:
			 *                           - (String) name - Name of the sending user
			 *                           - (String) displayName - Cropped name of the sending user
			 *                           - (String) message - Message text
			 *                           - (String) time - Localized time
			 */
			$(Candy).triggerHandler('candy:view.message.before-render', renderEvtData);

			var html = Mustache.to_html(renderEvtData.template, renderEvtData.templateData);
			self.Room.appendToMessagePane(roomJid, html);
			var elem = self.Room.getPane(roomJid, '.message-pane').children().last();
			// click on username opens private chat
			elem.find('a.label').click(function(event) {
				event.preventDefault();
				// Check if user is online and not myself
				var room = Candy.Core.getRoom(roomJid);
				if(room && name !== self.Room.getUser(Candy.View.getCurrent().roomJid).getNick() && room.getRoster().get(roomJid + '/' + name)) {
					if(Candy.View.Pane.PrivateRoom.open(roomJid + '/' + name, name, true) === false) {
						return false;
					}
				}
			});

			// Notify the user about a new private message
			if(Candy.View.getCurrent().roomJid !== roomJid || !self.Window.hasFocus()) {
				self.Chat.increaseUnreadMessages(roomJid);
				if(Candy.View.Pane.Chat.rooms[roomJid].type === 'chat' && !self.Window.hasFocus()) {
					self.Chat.Toolbar.playSound();
				}
			}
			if(Candy.View.getCurrent().roomJid === roomJid) {
				self.Room.scrollToBottom(roomJid);
			}

			evtData.element = elem;

			/** Event: candy:view.message.after-show
			 * Triggered after showing a message
			 *
			 * Parameters:
			 *   (String) roomJid - Room JID
			 *   (jQuery.Element) element - User element
			 *   (String) name - Name of the sending user
			 *   (String) message - Message text
			 */
			$(Candy).triggerHandler('candy:view.message.after-show', evtData);
		}
	};

	return self;
}(Candy.View.Pane || {}, jQuery));
