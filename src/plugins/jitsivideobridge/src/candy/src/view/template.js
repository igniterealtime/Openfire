/** File: template.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.View.Template
 * Contains mustache.js templates
 */
Candy.View.Template = (function(self){
	self.Window = {
		/**
		 * Unread messages - used to extend the window title
		 */
		unreadmessages: '({{count}}) {{title}}'
	};

	self.Chat = {
		pane: '<div id="chat-pane">{{> tabs}}{{> toolbar}}{{> rooms}}</div>{{> modal}}',
		rooms: '<div id="chat-rooms" class="rooms"></div>',
		tabs: '<ul id="chat-tabs"></ul>',
		tab: '<li class="roomtype-{{roomType}}" data-roomjid="{{roomJid}}" data-roomtype="{{roomType}}"><a href="#" class="label">{{#privateUserChat}}@{{/privateUserChat}}{{name}}</a><a href="#" class="transition"></a><a href="#" class="close">\u00D7</a><small class="unread"></small></li>',
		modal: '<div id="chat-modal"><a id="admin-message-cancel" class="close" href="#">\u00D7</a><span id="chat-modal-body"></span><img src="{{resourcesPath}}img/modal-spinner.gif" id="chat-modal-spinner" /></div><div id="chat-modal-overlay"></div>',
		adminMessage: '<dt>{{time}}</dt><dd class="adminmessage"><span class="label">{{sender}}</span>{{subject}} {{message}}</dd>',
		infoMessage: '<dt>{{time}}</dt><dd class="infomessage">{{subject}} {{message}}</dd>',
		toolbar: '<ul id="chat-toolbar"><li id="emoticons-icon" data-tooltip="{{tooltipEmoticons}}"></li><li id="chat-sound-control" class="checked" data-tooltip="{{tooltipSound}}">{{> soundcontrol}}</li><li id="chat-autoscroll-control" class="checked" data-tooltip="{{tooltipAutoscroll}}"></li><li class="checked" id="chat-statusmessage-control" data-tooltip="{{tooltipStatusmessage}}"></li><li class="context" data-tooltip="{{tooltipAdministration}}"></li><li class="usercount" data-tooltip="{{tooltipUsercount}}"><span id="chat-usercount"></span></li></ul>',
		soundcontrol:	'<script type="text/javascript">var audioplayerListener = new Object(); audioplayerListener.onInit = function() { };'
						+ '</script><object id="chat-sound-player" type="application/x-shockwave-flash" data="{{resourcesPath}}audioplayer.swf"'
						+ ' width="0" height="0"><param name="movie" value="{{resourcesPath}}audioplayer.swf" /><param name="AllowScriptAccess"'
						+ ' value="always" /><param name="FlashVars" value="listener=audioplayerListener&amp;mp3={{resourcesPath}}notify.mp3" />'
						+ '</object>',
		Context: {
			menu: '<div id="context-menu"><ul></ul></div>',
			menulinks: '<li class="{{class}}" id="context-menu-{{id}}">{{label}}</li>',
			contextModalForm: '<form action="#" id="context-modal-form"><label for="context-modal-label">{{_label}}</label><input type="text" name="contextModalField" id="context-modal-field" /><input type="submit" class="button" name="send" value="{{_submit}}" /></form>',
			adminMessageReason: '<a id="admin-message-cancel" class="close" href="#">×</a><p>{{_action}}</p>{{#reason}}<p>{{_reason}}</p>{{/reason}}'
		},
		tooltip: '<div id="tooltip"><div></div></div>'
	};

	self.Room = {
		pane: '<div class="room-pane roomtype-{{roomType}}" id="chat-room-{{roomId}}" data-roomjid="{{roomJid}}" data-roomtype="{{roomType}}">{{> roster}}{{> messages}}{{> form}}</div>',
		subject: '<dt>{{time}}</dt><dd class="subject"><span class="label">{{roomName}}</span>{{_roomSubject}} {{subject}}</dd>',
		form: '<div class="message-form-wrapper"></div><form method="post" class="message-form"><input name="message" class="field" type="text" autocomplete="off" maxlength="1000" /><input type="submit" class="submit" name="submit" value="{{_messageSubmit}}" /></form>'
	};

	self.Roster = {
		pane: '<div class="roster-pane"></div>',
		user: '<div class="user role-{{role}} affiliation-{{affiliation}}{{#me}} me{{/me}}" id="user-{{roomId}}-{{userId}}" data-jid="{{userJid}}" data-nick="{{nick}}" data-role="{{role}}" data-affiliation="{{affiliation}}"><div class="label">{{displayNick}}</div><ul><li class="context" id="context-{{roomId}}-{{userId}}"></li><li class="role role-{{role}} affiliation-{{affiliation}}" data-tooltip="{{tooltipRole}}"></li><li class="ignore" data-tooltip="{{tooltipIgnored}}"></li></ul></div>'
	};

	self.Message = {
		pane: '<div class="message-pane-wrapper"><dl class="message-pane"></dl></div>',
		item: '<dt>{{time}}</dt><dd><span class="label"><a href="#" class="name">{{displayName}}</a></span>{{{message}}}</dd>'
	};

	self.Login = {
		form: '<form method="post" id="login-form" class="login-form">'
			+ '{{#displayUsername}}<label for="username">{{_labelUsername}}</label><input type="text" id="username" name="username"/>{{/displayUsername}}'
			+ '{{#presetJid}}<input type="hidden" id="username" name="username" value="{{presetJid}}"/>{{/presetJid}}'
			+ '{{#displayPassword}}<label for="password">{{_labelPassword}}</label><input type="password" id="password" name="password" />{{/displayPassword}}'
			+ '<input type="submit" class="button" value="{{_loginSubmit}}" /></form>'
	};
	
	self.PresenceError = {
		enterPasswordForm: '<strong>{{_label}}</strong>'
			+ '<form method="post" id="enter-password-form" class="enter-password-form">'
			+ '<label for="password">{{_labelPassword}}</label><input type="password" id="password" name="password" />'
			+ '<input type="submit" class="button" value="{{_joinSubmit}}" /></form>',
		nicknameConflictForm: '<strong>{{_label}}</strong>'
			+ '<form method="post" id="nickname-conflict-form" class="nickname-conflict-form">'
			+ '<label for="nickname">{{_labelNickname}}</label><input type="text" id="nickname" name="nickname" />'
			+ '<input type="submit" class="button" value="{{_loginSubmit}}" /></form>',
		displayError: '<strong>{{_error}}</strong>'
	};

	return self;
}(Candy.View.Template || {}));
