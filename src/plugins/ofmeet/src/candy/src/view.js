/** File: view.js
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

/* global jQuery, Candy, window, Mustache, document */

/** Class: Candy.View
 * The Candy View Class
 *
 * Parameters:
 *   (Candy.View) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View = (function(self, $) {
		/** PrivateObject: _current
		 * Object containing current container & roomJid which the client sees.
		 */
	var _current = { container: null, roomJid: null },
		/** PrivateObject: _options
		 *
		 * Options:
		 *   (String) language - language to use
		 *   (String) assets - path to assets (res) directory (with trailing slash)
		 *   (Object) messages - limit: clean up message pane when n is reached / remove: remove n messages after limit has been reached
		 *   (Object) crop - crop if longer than defined: message.nickname=15, message.body=1000, roster.nickname=15
		 *   (Bool) enableXHTML - [default: false] enables XHTML messages sending & displaying
		 */
		_options = {
			language: 'en',
			assets: 'res/',
			messages: { limit: 2000, remove: 500 },
			crop: {
				message: { nickname: 15, body: 1000 },
				roster: { nickname: 15 }
			},
			enableXHTML: false
		},

		/** PrivateFunction: _setupTranslation
		 * Set dictionary using jQuery.i18n plugin.
		 *
		 * See: view/translation.js
		 * See: libs/jquery-i18n/jquery.i18n.js
		 *
		 * Parameters:
		 *   (String) language - Language identifier
		 */
		_setupTranslation = function(language) {
			$.i18n.load(self.Translation[language]);
		},

		/** PrivateFunction: _registerObservers
		 * Register observers. Candy core will now notify the View on changes.
		 */
		_registerObservers = function() {
			$(Candy).on('candy:core.chat.connection', self.Observer.Chat.Connection);
			$(Candy).on('candy:core.chat.message', self.Observer.Chat.Message);
			$(Candy).on('candy:core.login', self.Observer.Login);
			$(Candy).on('candy:core.autojoin-missing', self.Observer.AutojoinMissing);
			$(Candy).on('candy:core.presence', self.Observer.Presence.update);
			$(Candy).on('candy:core.presence.leave', self.Observer.Presence.update);
			$(Candy).on('candy:core.presence.room', self.Observer.Presence.update);
			$(Candy).on('candy:core.presence.error', self.Observer.PresenceError);
			$(Candy).on('candy:core.message', self.Observer.Message);
		},

		/** PrivateFunction: _registerWindowHandlers
		 * Register window focus / blur / resize handlers.
		 *
		 * jQuery.focus()/.blur() <= 1.5.1 do not work for IE < 9. Fortunately onfocusin/onfocusout will work for them.
		 */
		_registerWindowHandlers = function() {
			if(Candy.Util.getIeVersion() < 9) {
				$(document).focusin(Candy.View.Pane.Window.onFocus).focusout(Candy.View.Pane.Window.onBlur);
			} else {
				$(window).focus(Candy.View.Pane.Window.onFocus).blur(Candy.View.Pane.Window.onBlur);
			}
			$(window).resize(Candy.View.Pane.Chat.fitTabs);
		},

		/** PrivateFunction: _initToolbar
		 * Initialize toolbar.
		 */
		_initToolbar = function() {
			self.Pane.Chat.Toolbar.init();
		},

		/** PrivateFunction: _delegateTooltips
		 * Delegate mouseenter on tooltipified element to <Candy.View.Pane.Chat.Tooltip.show>.
		 */
		_delegateTooltips = function() {
			$('body').delegate('li[data-tooltip]', 'mouseenter', Candy.View.Pane.Chat.Tooltip.show);
		};

	/** Function: init
	 * Initialize chat view (setup DOM, register handlers & observers)
	 *
	 * Parameters:
	 *   (jQuery.element) container - Container element of the whole chat view
	 *   (Object) options - Options: see _options field (value passed here gets extended by the default value in _options field)
	 */
	self.init = function(container, options) {
		// #216
		// Rename `resources` to `assets` but prevent installations from failing
		// after upgrade
		if(options.resources) {
			options.assets = options.resources;
		}
		delete options.resources;

		$.extend(true, _options, options);
		_setupTranslation(_options.language);

		// Set path to emoticons
		Candy.Util.Parser.setEmoticonPath(this.getOptions().assets + 'img/emoticons/');

		// Start DOMination...
		_current.container = container;
		_current.container.html(Mustache.to_html(Candy.View.Template.Chat.pane, {
			tooltipEmoticons : $.i18n._('tooltipEmoticons'),
			tooltipSound : $.i18n._('tooltipSound'),
			tooltipAutoscroll : $.i18n._('tooltipAutoscroll'),
			tooltipStatusmessage : $.i18n._('tooltipStatusmessage'),
			tooltipAdministration : $.i18n._('tooltipAdministration'),
			tooltipUsercount : $.i18n._('tooltipUsercount'),
			assetsPath : this.getOptions().assets
		}, {
			tabs: Candy.View.Template.Chat.tabs,
			rooms: Candy.View.Template.Chat.rooms,
			modal: Candy.View.Template.Chat.modal,
			toolbar: Candy.View.Template.Chat.toolbar,
			soundcontrol: Candy.View.Template.Chat.soundcontrol
		}));

		// ... and let the elements dance.
		_registerWindowHandlers();
		_initToolbar();
		_registerObservers();
		_delegateTooltips();
	};

	/** Function: getCurrent
	 * Get current container & roomJid in an object.
	 *
	 * Returns:
	 *   Object containing container & roomJid
	 */
	self.getCurrent = function() {
		return _current;
	};

	/** Function: getOptions
	 * Gets options
	 *
	 * Returns:
	 *   Object
	 */
	self.getOptions = function() {
		return _options;
	};

	return self;
}(Candy.View || {}, jQuery));
