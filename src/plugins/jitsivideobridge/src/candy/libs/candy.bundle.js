/** File: candy.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/*jslint regexp: true, browser: true, confusion: true, sloppy: true, white: true, nomen: true, plusplus: true, maxerr: 50, indent: 4 */
/*global jQuery: true, MD5: true, escape: true, Mustache: true, console: true, Strophe: true, $iq: true, $pres: true */

/** Class: Candy
 * Candy base class for initalizing the view and the core
 *
 * Parameters:
 *   (Candy) self - itself
 *   (jQuery) $ - jQuery
 */
var Candy = (function(self, $) {
	/** Object: about
	 * About candy
	 *
	 * Contains:
	 *   (String) name - Candy
	 *   (Float) version - Candy version
	 */
	self.about = {
		name: 'Candy',
		version: '1.0.9'
	};

	/** Function: init
	 * Init view & core
	 *
	 * Parameters:
	 *   (String) service - URL to the BOSH interface
	 *   (Object) options - Options for candy
	 *
	 * Options:
	 *   (Boolean) debug - Debug (Default: false)
	 *   (Array|Boolean) autojoin - Autojoin these channels. When boolean true, do not autojoin, wait if the server sends something.
	 */
	self.init = function(service, options) {
		self.View.init($('#candy'), options.view);
		self.Core.init(service, options.core);
	};

	return self;
}(Candy || {}, jQuery));
/** File: core.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.Core
 * Candy Chat Core
 *
 * Parameters:
 *   (Candy.Core) self - itself
 *   (Strophe) Strophe - Strophe JS
 *   (jQuery) $ - jQuery
 */
Candy.Core = (function(self, Strophe, $) {
		/** PrivateVariable: _connection
		 * Strophe connection
		 */
	var _connection = null,
		/** PrivateVariable: _service
		 * URL of BOSH service
		 */
		_service = null,
		/** PrivateVariable: _user
		 * Current user (me)
		 */
		_user = null,
		/** PrivateVariable: _rooms
		 * Opened rooms, containing instances of Candy.Core.ChatRooms
		 */
		_rooms = {},
		/** PrivateVariable: _anonymousConnection
		 * Set in <Candy.Core.connect> when jidOrHost doesn't contain a @-char.
		 */
		_anonymousConnection = false,
		/** PrivateVariable: _options
		 * Options:
		 *   (Boolean) debug - Debug (Default: false)
		 *   (Array|Boolean) autojoin - Autojoin these channels. When boolean true, do not autojoin, wait if the server sends something.
		 */
		_options = {
			/** Boolean: autojoin
			 * If set to `true` try to get the bookmarks and autojoin the rooms (supported by Openfire).
			 * You may want to define an array of rooms to autojoin: `['room1@conference.host.tld', 'room2...]` (ejabberd, Openfire, ...)
			 */
			autojoin: true,
			debug: false
		},

		/** PrivateFunction: _addNamespace
		 * Adds a namespace.
		 *
		 * Parameters:
		 *   (String) name - namespace name (will become a constant living in Strophe.NS.*)
		 *   (String) value - XML Namespace
		 */
		_addNamespace = function(name, value) {
			Strophe.addNamespace(name, value);
		},

		/** PrivateFunction: _addNamespaces
		 * Adds namespaces needed by Candy.
		 */
		_addNamespaces = function() {
			_addNamespace('PRIVATE', 'jabber:iq:private');
			_addNamespace('BOOKMARKS', 'storage:bookmarks');
			_addNamespace('PRIVACY', 'jabber:iq:privacy');
			_addNamespace('DELAY', 'jabber:x:delay');
		},

		/** PrivateFunction: _registerEventHandlers
		 * Adds listening handlers to the connection.
		 */
		_registerEventHandlers = function() {
			self.addHandler(self.Event.Jabber.Version, Strophe.NS.VERSION, 'iq');
			self.addHandler(self.Event.Jabber.Presence, null, 'presence');
			self.addHandler(self.Event.Jabber.Message, null, 'message');
			self.addHandler(self.Event.Jabber.Bookmarks, Strophe.NS.PRIVATE, 'iq');
			self.addHandler(self.Event.Jabber.Room.Disco, Strophe.NS.DISCO_INFO, 'iq');
			self.addHandler(self.Event.Jabber.PrivacyList, Strophe.NS.PRIVACY, 'iq', 'result');
			self.addHandler(self.Event.Jabber.PrivacyListError, Strophe.NS.PRIVACY, 'iq', 'error');
		};

	/** Function: init
	 * Initialize Core.
	 *
	 * Parameters:
	 *   (String) service - URL of BOSH service
	 *   (Object) options - Options for candy
	 */
	self.init = function(service, options) {
		_service = service;
		// Apply options
		$.extend(true, _options, options);

		// Enable debug logging
		if(_options.debug) {
			self.log = function(str) {
				try { // prevent erroring
					if(typeof window.console !== undefined && typeof window.console.log !== undefined) {
						console.log(str);
					}
				} catch(e) {
					//console.error(e);
				}
			};
			self.log('[Init] Debugging enabled');
		}

		_addNamespaces();
		
		if (_options.websockets)
		{
			// Connect to openfire websockets service
			_connection = new Openfire.Connection(_service);			
		} else {
			// Connect to BOSH service
			_connection = new Strophe.Connection(_service);		
		}
		
		_connection.rawInput = self.rawInput.bind(self);
		_connection.rawOutput = self.rawOutput.bind(self);

		// Window unload handler... works on all browsers but Opera. There is NO workaround.
		// Opera clients getting disconnected 1-2 minutes delayed.
		window.onbeforeunload = self.onWindowUnload;

		// Prevent Firefox from aborting AJAX requests when pressing ESC
		if($.browser.mozilla) {
			$(document).keydown(function(e) {
				if(e.which === 27) {
					e.preventDefault();
				}
			});
		}
	};

	/** Function: connect
	 * Connect to the jabber host.
	 *
	 * There are four different procedures to login:
	 *   connect('JID', 'password') - Connect a registered user
	 *   connect('domain') - Connect anonymously to the domain. The user should receive a random JID.
	 *   connect('domain', null, 'nick') - Connect anonymously to the domain. The user should receive a random JID but with a nick set.
	 *   connect('JID') - Show login form and prompt for password. JID input is hidden.
	 *   connect() - Show login form and prompt for JID and password.
	 *
	 * See:
	 *   <Candy.Core.attach()> for attaching an already established session.
	 *
	 * Parameters:
	 *   (String) jidOrHost - JID or Host
	 *   (String) password  - Password of the user
	 *   (String) nick      - Nick of the user. Set one if you want to anonymously connect but preset a nick. If jidOrHost is a domain
	 *                        and this param is not set, Candy will prompt for a nick.
	 */
	self.connect = function(jidOrHost, password, nick) {
		// Reset before every connection attempt to make sure reconnections work after authfail, alltabsclosed, ...
		_connection.reset();
		_registerEventHandlers();

		_anonymousConnection = !_anonymousConnection ? jidOrHost && jidOrHost.indexOf("@") < 0 : true;

		if(jidOrHost && password) {
			// authentication
			_connection.connect(_getEscapedJidFromJid(jidOrHost) + '/' + Candy.about.name, password, Candy.Core.Event.Strophe.Connect);
			_user = new self.ChatUser(jidOrHost, Strophe.getNodeFromJid(jidOrHost));
		} else if(jidOrHost && nick) {
			// anonymous connect
			_connection.connect(_getEscapedJidFromJid(jidOrHost) + '/' + Candy.about.name, null, Candy.Core.Event.Strophe.Connect);
			_user = new self.ChatUser(null, nick); // set jid to null because we'll later receive it
		} else if(jidOrHost) {
			Candy.Core.Event.Login(jidOrHost);
		} else {
			// display login modal
			Candy.Core.Event.Login();
		}
	};
	
	_getEscapedJidFromJid = function(jid) {
		var node = Strophe.getNodeFromJid(jid),
			domain = Strophe.getDomainFromJid(jid);
		return node ? Strophe.escapeNode(node) + '@' + domain : domain;
	};

	/** Function: attach
	 * Attach an already binded & connected session to the server
	 *
	 * _See_ Strophe.Connection.attach
	 *
	 * Parameters:
	 *   (String) jid - Jabber ID
	 *   (Integer) sid - Session ID
	 *   (Integer) rid - rid
	 */
	self.attach = function(jid, sid, rid) {
		_user = new self.ChatUser(jid, Strophe.getNodeFromJid(jid));
		_registerEventHandlers();
		_connection.attach(jid, sid, rid, Candy.Core.Event.Strophe.Connect);
	};

	/** Function: disconnect
	 * Leave all rooms and disconnect
	 */
	self.disconnect = function() {
		if(_connection.connected) {
			$.each(self.getRooms(), function() {
				Candy.Core.Action.Jabber.Room.Leave(this.getJid());
			});
			_connection.disconnect();
		}
	};
	
	/** Function: addHandler
	 * Wrapper for Strophe.Connection.addHandler() to add a stanza handler for the connection.
	 *
	 * Parameters:
	 *   (Function) handler - The user callback.
	 *   (String) ns - The namespace to match.
	 *   (String) name - The stanza name to match.
	 *   (String) type - The stanza type attribute to match.
	 *   (String) id - The stanza id attribute to match.
	 *   (String) from - The stanza from attribute to match.
	 *   (String) options - The handler options
	 *
	 * Returns:
	 *   A reference to the handler that can be used to remove it.
	 */
	self.addHandler = function(handler, ns, name, type, id, from, options) {
		return _connection.addHandler(handler, ns, name, type, id, from, options);
	};

	/** Function: getUser
	 * Gets current user
	 *
	 * Returns:
	 *   Instance of Candy.Core.ChatUser
	 */
	self.getUser = function() {
		return _user;
	};

	/** Function: setUser
	 * Set current user. Needed when anonymous login is used, as jid gets retrieved later.
	 *
	 * Parameters:
	 *   (Candy.Core.ChatUser) user - User instance
	 */
	self.setUser = function(user) {
		_user = user;
	};

	/** Function: getConnection
	 * Gets Strophe connection
	 *
	 * Returns:
	 *   Instance of Strophe.Connection
	 */
	self.getConnection = function() {
		return _connection;
	};

	/** Function: getRooms
	 * Gets all joined rooms
	 *
	 * Returns:
	 *   Object containing instances of Candy.Core.ChatRoom
	 */
	self.getRooms = function() {
		return _rooms;
	};

	/** Function: isAnonymousConnection
	 * Returns true if <Candy.Core.connect> was first called with a domain instead of a jid as the first param.
	 *
	 * Returns:
	 *   (Boolean)
	 */
	self.isAnonymousConnection = function() {
		return _anonymousConnection;
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

    /** Function: getRoom
	 * Gets a specific room
	 *
	 * Parameters:
	 *   (String) roomJid - JID of the room
	 *
	 * Returns:
	 *   If the room is joined, instance of Candy.Core.ChatRoom, otherwise null.
	 */
	self.getRoom = function(roomJid) {
		if (_rooms[roomJid]) {
			return _rooms[roomJid];
		}
		return null;
	};

	/** Function: onWindowUnload
	 * window.onbeforeunload event which disconnects the client from the Jabber server.
	 */
	self.onWindowUnload = function() {
		// Enable synchronous requests because Safari doesn't send asynchronous requests within unbeforeunload events.
		// Only works properly when following patch is applied to strophejs: https://github.com/metajack/strophejs/issues/16/#issuecomment-600266
		_connection.sync = true;
		self.disconnect();
		_connection.flush();
	};

	/** Function: rawInput
	 * (Overridden from Strophe.Connection.rawInput)
	 *
	 * Logs all raw input if debug is set to true.
	 */
	self.rawInput = function(data) {
		this.log('RECV: ' + data);
	};

	/** Function rawOutput
	 * (Overridden from Strophe.Connection.rawOutput)
	 *
	 * Logs all raw output if debug is set to true.
	 */
	self.rawOutput = function(data) {
		this.log('SENT: ' + data);
	};

	/** Function: log
	 * Overridden to do something useful if debug is set to true.
	 *
	 * See: Candy.Core#init
	 */
	self.log = function() {};

	return self;
}(Candy.Core || {}, Strophe, jQuery));
/** File: view.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

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
		 *   (String) resources - path to resources directory (with trailing slash)
		 *   (Object) messages - limit: clean up message pane when n is reached / remove: remove n messages after limit has been reached
		 *   (Object) crop - crop if longer than defined: message.nickname=15, message.body=1000, roster.nickname=15
		 */
		_options = {
			language: 'en',
			resources: 'res/',
			messages: { limit: 2000, remove: 500 },
			crop: {
				message: { nickname: 15, body: 1000 },
				roster: { nickname: 15 }
			}
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
			$.i18n.setDictionary(self.Translation[language]);
		},

		/** PrivateFunction: _registerObservers
		 * Register observers. Candy core will now notify the View on changes.
		 */
		_registerObservers = function() {
			Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.CHAT, self.Observer.Chat);
			Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.PRESENCE, self.Observer.Presence);
			Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.PRESENCE_ERROR, self.Observer.PresenceError);
			Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.MESSAGE, self.Observer.Message);
			Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.LOGIN, self.Observer.Login);
		},

		/** PrivateFunction: _registerWindowHandlers
		 * Register window focus / blur / resize handlers.
		 *
		 * jQuery.focus()/.blur() <= 1.5.1 do not work for IE < 9. Fortunately onfocusin/onfocusout will work for them.
		 */
		_registerWindowHandlers = function() {
			// Cross-browser focus handling
			if($.browser.msie && !$.browser.version.match('^9'))	{
				$(document).focusin(Candy.View.Pane.Window.onFocus).focusout(Candy.View.Pane.Window.onBlur);
			} else {
				$(window).focus(Candy.View.Pane.Window.onFocus).blur(Candy.View.Pane.Window.onBlur);
			}
			$(window).resize(Candy.View.Pane.Chat.fitTabs);
		},

		/** PrivateFunction: _registerToolbarHandlers
		 * Register toolbar handlers and disable sound if cookie says so.
		 */
		_registerToolbarHandlers = function() {
			$('#emoticons-icon').click(function(e) {
				self.Pane.Chat.Context.showEmoticonsMenu(e.currentTarget);
				e.stopPropagation();
			});
			$('#chat-autoscroll-control').click(Candy.View.Pane.Chat.Toolbar.onAutoscrollControlClick);

			$('#chat-sound-control').click(Candy.View.Pane.Chat.Toolbar.onSoundControlClick);
			if(Candy.Util.cookieExists('candy-nosound')) {
				$('#chat-sound-control').click();
			}
			$('#chat-statusmessage-control').click(Candy.View.Pane.Chat.Toolbar.onStatusMessageControlClick);
			if(Candy.Util.cookieExists('candy-nostatusmessages')) {
				$('#chat-statusmessage-control').click();
			}
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
		$.extend(true, _options, options);
		_setupTranslation(_options.language);
		
		// Set path to emoticons
		Candy.Util.Parser.setEmoticonPath(this.getOptions().resources + 'img/emoticons/');

		// Start DOMination...
		_current.container = container;
		_current.container.html(Mustache.to_html(Candy.View.Template.Chat.pane, {
			tooltipEmoticons : $.i18n._('tooltipEmoticons'),
			tooltipSound : $.i18n._('tooltipSound'),
			tooltipAutoscroll : $.i18n._('tooltipAutoscroll'),
			tooltipStatusmessage : $.i18n._('tooltipStatusmessage'),
			tooltipAdministration : $.i18n._('tooltipAdministration'),
			tooltipUsercount : $.i18n._('tooltipUsercount'),
			resourcesPath : this.getOptions().resources
		}, {
			tabs: Candy.View.Template.Chat.tabs,
			rooms: Candy.View.Template.Chat.rooms,
			modal: Candy.View.Template.Chat.modal,
			toolbar: Candy.View.Template.Chat.toolbar,
			soundcontrol: Candy.View.Template.Chat.soundcontrol
		}));

		// ... and let the elements dance.
		_registerWindowHandlers();
		_registerToolbarHandlers();
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
/** File: util.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.Util
 * Candy utils
 *
 * Parameters:
 *   (Candy.Util) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.Util = (function(self, $){
	/** Function: jidToId
	 * Translates a jid to a MD5-Id
	 *
	 * Parameters:
	 *   (String) jid - Jid
	 *
	 * Returns:
	 *   MD5-ified jid
	 */
	self.jidToId = function(jid) {
		return MD5.hexdigest(jid);
	};
	
	/** Function: escapeJid
	 * Escapes a jid (node & resource get escaped)
	 *
	 * See:
	 *   XEP-0106
	 *
	 * Parameters:
	 *   (String) jid - Jid
	 *
	 * Returns:
	 *   (String) - escaped jid
	 */
	self.escapeJid = function(jid) {
		var node = Strophe.escapeNode(Strophe.getNodeFromJid(jid)),
			domain = Strophe.getDomainFromJid(jid),
			resource = Strophe.getResourceFromJid(jid);
			
		jid = node + '@' + domain;
		if (resource) {
			jid += '/' + Strophe.escapeNode(resource);
		}
		
		return jid;
	};
	
	/** Function: unescapeJid
	 * Unescapes a jid (node & resource get unescaped)
	 *
	 * See:
	 *   XEP-0106
	 *
	 * Parameters:
	 *   (String) jid - Jid
	 *
	 * Returns:
	 *   (String) - unescaped Jid
	 */
	self.unescapeJid = function(jid) {
		var node = Strophe.unescapeNode(Strophe.getNodeFromJid(jid)),
			domain = Strophe.getDomainFromJid(jid),
			resource = Strophe.getResourceFromJid(jid);
		
		jid = node + '@' + domain;
		if(resource) {
			jid += '/' + Strophe.unescapeNode(resource);
		}
		
		return jid;
	};

	/** Function: crop
	 * Crop a string with the specified length
	 *
	 * Parameters:
	 *   (String) str - String to crop
	 *   (Integer) len - Max length
	 */
	self.crop = function(str, len) {
		if (str.length > len) {
			str = str.substr(0, len - 3) + '...';
		}
		return str;
	};

	/** Function: setCookie
	 * Sets a new cookie
	 *
	 * Parameters:
	 *   (String) name - cookie name
	 *   (String) value - Value
	 *   (Integer) lifetime_days - Lifetime in days
	 */
	self.setCookie = function(name, value, lifetime_days) {
		var exp = new Date();
		exp.setDate(new Date().getDate() + lifetime_days);
		document.cookie = name + '=' + value + ';expires=' + exp.toUTCString() + ';path=/';
	};

	/** Function: cookieExists
	 * Tests if a cookie with the given name exists
	 *
	 * Parameters:
	 *   (String) name - Cookie name
	 *
	 * Returns:
	 *   (Boolean) - true/false
	 */
	self.cookieExists = function(name) {
		return document.cookie.indexOf(name) > -1;
	};

	/** Function: getCookie
	 * Returns the cookie value if there's one with this name, otherwise returns undefined
	 *
	 * Parameters:
	 *   (String) name - Cookie name
	 *
	 * Returns:
	 *   Cookie value or undefined
	 */
	self.getCookie = function(name) {
	    if(document.cookie)	{
				var regex = new RegExp(escape(name) + '=([^;]*)', 'gm'),
					matches = regex.exec(document.cookie);
					if(matches) {
						return matches[1];
					}
	    }
	};

	/** Function: deleteCookie
	 * Deletes a cookie with the given name
	 *
	 * Parameters:
	 *   (String) name - cookie name
	 */
	self.deleteCookie = function(name) {
		document.cookie = name + '=;expires=Thu, 01-Jan-70 00:00:01 GMT;path=/';
	};

	/** Function: getPosLeftAccordingToWindowBounds
	 * Fetches the window width and element width
	 * and checks if specified position + element width is bigger
	 * than the window width.
	 *
	 * If this evaluates to true, the position gets substracted by the element width.
	 *
	 * Parameters:
	 *   (jQuery.Element) elem - Element to position
	 *   (Integer) pos - Position left
	 *
	 * Returns:
	 *   Object containing `px` (calculated position in pixel) and `alignment` (alignment of the element in relation to pos, either 'left' or 'right')
	 */
	self.getPosLeftAccordingToWindowBounds = function(elem, pos) {
		var windowWidth = $(document).width(),
			elemWidth   = elem.outerWidth(),
			marginDiff = elemWidth - elem.outerWidth(true),
			backgroundPositionAlignment = 'left';

		if (pos + elemWidth >= windowWidth) {
			pos -= elemWidth - marginDiff;
			backgroundPositionAlignment = 'right';
		}

		return { px: pos, backgroundPositionAlignment: backgroundPositionAlignment };
	};

	/** Function: getPosTopAccordingToWindowBounds
	 * Fetches the window height and element height
	 * and checks if specified position + element height is bigger
	 * than the window height.
	 *
	 * If this evaluates to true, the position gets substracted by the element height.
	 *
	 * Parameters:
	 *   (jQuery.Element) elem - Element to position
	 *   (Integer) pos - Position top
	 *
	 * Returns:
	 *   Object containing `px` (calculated position in pixel) and `alignment` (alignment of the element in relation to pos, either 'top' or 'bottom')
	 */
	self.getPosTopAccordingToWindowBounds = function(elem, pos) {
		var windowHeight = $(document).height(),
			elemHeight   = elem.outerHeight(),
			marginDiff = elemHeight - elem.outerHeight(true),
			backgroundPositionAlignment = 'top';

		if (pos + elemHeight >= windowHeight) {
			pos -= elemHeight - marginDiff;
			backgroundPositionAlignment = 'bottom';
		}

		return { px: pos, backgroundPositionAlignment: backgroundPositionAlignment };
	};

	/** Function: localizedTime
	 * Localizes ISO-8610 Date with the time/dateformat specified in the translation.
	 *
	 * See: libs/dateformat/dateFormat.js
	 * See: src/view/translation.js
	 * See: jquery-i18n/jquery.i18n.js
	 *
	 * Parameters:
	 *   (String) dateTime - ISO-8610 Datetime
	 *
	 * Returns:
	 *   If current date is equal to the date supplied, format with timeFormat, otherwise with dateFormat
	 */
	self.localizedTime = function(dateTime) {
		if (dateTime === undefined) {
			return undefined;
		}

		var date = self.iso8601toDate(dateTime);
		if(date.toDateString() === new Date().toDateString()) {
			return date.format($.i18n._('timeFormat'));
		} else {
			return date.format($.i18n._('dateFormat'));
		}
	};

	/** Function: iso8610toDate
	 * Parses a ISO-8610 Date to a Date-Object.
	 *
	 * Uses a fallback if the client's browser doesn't support it.
	 *
	 * Quote:
	 *   ECMAScript revision 5 adds native support for ISO-8601 dates in the Date.parse method,
	 *   but many browsers currently on the market (Safari 4, Chrome 4, IE 6-8) do not support it.
	 *
	 * Credits:
	 *  <Colin Snover at http://zetafleet.com/blog/javascript-dateparse-for-iso-8601>
	 *
	 * Parameters:
	 *   (String) date - ISO-8610 Date
	 *
	 * Returns:
	 *   Date-Object
	 */
	self.iso8601toDate = function(date) {
        var timestamp = Date.parse(date), minutesOffset = 0;
        if(isNaN(timestamp)) {
			var struct = /^(\d{4}|[+\-]\d{6})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{3,}))?)?(?:(Z)|([+\-])(\d{2})(?::?(\d{2}))?))?/.exec(date);
			if(struct) {
				if(struct[8] !== 'Z') {
					minutesOffset = +struct[10] * 60 + (+struct[11]);
					if(struct[9] === '+') {
						minutesOffset = -minutesOffset;
					}
				}
				return new Date(+struct[1], +struct[2] - 1, +struct[3], +struct[4], +struct[5] + minutesOffset, +struct[6], struct[7] ? +struct[7].substr(0, 3) : 0);
			} else {
				// XEP-0091 date
				timestamp = Date.parse(date.replace(/^(\d{4})(\d{2})(\d{2})/, '$1-$2-$3') + 'Z');
			}
        }
        return new Date(timestamp);
	};

	/** Function: isEmptyObject
	 * IE7 doesn't work with jQuery.isEmptyObject (<=1.5.1), workaround.
	 *
	 * Parameters:
	 *   (Object) obj - the object to test for
	 *
	 * Returns:
	 *   Boolean true or false.
	 */
	self.isEmptyObject = function(obj) {
		var prop;
		for(prop in obj) {
			if (obj.hasOwnProperty(prop)) {
				return false;
			}
		}
		return true;
	};

	/** Function: forceRedraw
	 * Fix IE7 not redrawing under some circumstances.
	 *
	 * Parameters:
	 *   (jQuery.element) elem - jQuery element to redraw
	 */
	self.forceRedraw = function(elem) {
		elem.css({display:'none'});
		setTimeout(function() {
			this.css({display:'block'});
		}.bind(elem), 1);
	};

	/** Class: Candy.Util.Parser
	 * Parser for emoticons, links and also supports escaping.
	 */
	self.Parser = {
		/** PrivateVariable: _emoticonPath
		 * Path to emoticons.
		 *
		 * Use setEmoticonPath() to change it
		 */
		_emoticonPath: '',
		
		/** Function: setEmoticonPath
		 * Set emoticons location.
		 *
		 * Parameters:
		 *   (String) path - location of emoticons with trailing slash
		 */
		setEmoticonPath: function(path) {
			this._emoticonPath = path;
		},

		/** Array: emoticons
		 * Array containing emoticons to be replaced by their images.
		 *
		 * Can be overridden/extended.
		 */
		emoticons: [
			{
				plain: ':)',
				regex: /((\s):-?\)|:-?\)(\s|$))/gm,
				image: 'Smiling.png'
			},
			{
				plain: ';)',
				regex: /((\s);-?\)|;-?\)(\s|$))/gm,
				image: 'Winking.png'
			},
			{
				plain: ':D',
				regex: /((\s):-?D|:-?D(\s|$))/gm,
				image: 'Grinning.png'
			},
			{
				plain: ';D',
				regex: /((\s);-?D|;-?D(\s|$))/gm,
				image: 'Grinning_Winking.png'
			},
			{
				plain: ':(',
				regex: /((\s):-?\(|:-?\((\s|$))/gm,
				image: 'Unhappy.png'
			},
			{
				plain: '^^',
				regex: /((\s)\^\^|\^\^(\s|$))/gm,
				image: 'Happy_3.png'
			},
			{
				plain: ':P',
				regex: /((\s):-?P|:-?P(\s|$))/igm,
				image: 'Tongue_Out.png'
			},
			{
				plain: ';P',
				regex: /((\s);-?P|;-?P(\s|$))/igm,
				image: 'Tongue_Out_Winking.png'
			},
			{
				plain: ':S',
				regex: /((\s):-?S|:-?S(\s|$))/igm,
				image: 'Confused.png'
			},
			{
				plain: ':/',
				regex: /((\s):-?\/|:-?\/(\s|$))/gm,
				image: 'Uncertain.png'
			},
			{
				plain: '8)',
				regex: /((\s)8-?\)|8-?\)(\s|$))/gm,
				image: 'Sunglasses.png'
			},
			{
				plain: '$)',
				regex: /((\s)\$-?\)|\$-?\)(\s|$))/gm,
				image: 'Greedy.png'
			},
			{
				plain: 'oO',
				regex: /((\s)oO|oO(\s|$))/gm,
				image: 'Huh.png'
			},
			{
				plain: ':x',
				regex: /((\s):x|:x(\s|$))/gm,
				image: 'Lips_Sealed.png'
			},
			{
				plain: ':666:',
				regex: /((\s):666:|:666:(\s|$))/gm,
				image: 'Devil.png'
			},
			{
				plain: '<3',
				regex: /((\s)&lt;3|&lt;3(\s|$))/gm,
				image: 'Heart.png'
			}
		],

		/** Function: emotify
		 * Replaces text-emoticons with their image equivalent.
		 *
		 * Parameters:
		 *   (String) text - Text to emotify
		 *
		 * Returns:
		 *   Emotified text
		 */
		emotify: function(text) {
			var i;
			for(i = this.emoticons.length-1; i >= 0; i--) {
				text = text.replace(this.emoticons[i].regex, '$2<img class="emoticon" alt="$1" src="' + this._emoticonPath + this.emoticons[i].image + '" />$3');
			}
			return text;
		},

		/** Function: linkify
		 * Replaces URLs with a HTML-link.
		 *
		 * Parameters:
		 *   (String) text - Text to linkify
		 *
		 * Returns:
		 *   Linkified text
		 */
		linkify: function(text) {
			text = text.replace(/(^|[^\/])(www\.[^\.]+\.[\S]+(\b|$))/gi, '$1http://$2');
			return text.replace(/(\b(https?|ftp|file):\/\/[\-A-Z0-9+&@#\/%?=~_|!:,.;]*[\-A-Z0-9+&@#\/%=~_|])/ig, '<a href="$1" target="_blank">$1</a>');
		},

		/** Function: escape
		 * Escapes a text using a jQuery function (like htmlspecialchars in PHP)
		 *
		 * Parameters:
		 *   (String) text - Text to escape
		 *
		 * Returns:
		 *   Escaped text
		 */
		escape: function(text) {
			return $('<div/>').text(text).html();
		},

		/** Function: all
		 * Does everything of the parser: escaping, linkifying and emotifying.
		 *
		 * Parameters:
		 *   (String) text - Text to parse
		 *
		 * Returns:
		 *   Parsed text
		 */
		all: function(text) {
			if(text) {
				text = this.escape(text);
				text = this.linkify(text);
				text = this.emotify(text);
			}
			return text;
		}
	};

	return self;
}(Candy.Util || {}, jQuery));


/** Class: Candy.Util.Observable
 * A class can be extended with the observable to be able to notify observers
 */
Candy.Util.Observable = (function(self) {
	/** PrivateObject: _observers
	 * List of observers
	 */
	var _observers = {};

	/** Function: addObserver
	 * Add an observer to the observer list
	 *
	 * Parameters:
	 *   (String) key - The key the observer listens to
	 *   (Callback) obj - The observer callback
	 */
	self.addObserver = function(key, obj) {
		if (_observers[key] === undefined) {
			_observers[key] = [];
		}
		_observers[key].push(obj);
	};

	/** Function: deleteObserver
	 * Delete observer from list
	 *
	 * Parameters:
	 *   (String) key - Key in which the observer lies
	 *   (Callback) obj - The observer callback to be deleted
	 */
	self.deleteObserver = function(key, obj) {
		delete _observers[key][obj];
	};

	/** Function: clearObservers
	 * Deletes all observers in list
	 *
	 * Parameters:
	 *   (String) key - If defined, remove observers of this key, otherwise remove all including all keys.
	 */
	self.clearObservers = function(key) {
		if (key !== undefined) {
			_observers[key] = [];
		} else {
			_observers = {};
		}
	};

	/** Function: notifyObservers
	 * Notify all of its observers of a certain event.
	 *
	 * Parameters:
	 *   (String) key - Key to notify
	 *   (Object) arg - An argument passed to the update-method of the observers
	 */
	self.notifyObservers = function(key, arg) {
		var observer = _observers[key], i;
		for(i = observer.length-1; i >= 0; i--) {
			observer[i].update(self, arg);
		}
	};

	return self;
}(Candy.Util.Observable || {}));
/** File: action.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.Core.Action
 * Chat Actions (basicly a abstraction of Jabber commands)
 *
 * Parameters:
 *   (Candy.Core.Action) self - itself
 *   (Strophe) Strophe - Strophe
 *   (jQuery) $ - jQuery
 */
Candy.Core.Action = (function(self, Strophe, $) {
	/** Class: Candy.Core.Action.Jabber
	 * Jabber actions
	 */
	self.Jabber = {
		/** Function: Version
		 * Replies to a version request
		 *
		 * Parameters:
		 *   (jQuery.element) msg - jQuery element
		 */
		Version: function(msg) {
			Candy.Core.getConnection().send($iq({type: 'result', to: msg.attr('from'), from: msg.attr('to'), id: msg.attr('id')}).c('query', {name: Candy.about.name, version: Candy.about.version, os: navigator.userAgent}));
		},

		/** Function: Roster
		 * Sends a request for a roster
		 */
		Roster: function() {
			Candy.Core.getConnection().send($iq({type: 'get', xmlns: Strophe.NS.CLIENT}).c('query', {xmlns: Strophe.NS.ROSTER}).tree());
		},

		/** Function: Presence
		 * Sends a request for presence
		 *
		 * Parameters:
		 *   (Object) attr - Optional attributes
		 */
		Presence: function(attr) {
			Candy.Core.getConnection().send($pres(attr).tree());
		},

		/** Function: Services
		 * Sends a request for disco items
		 */
		Services: function() {
			Candy.Core.getConnection().send($iq({type: 'get', xmlns: Strophe.NS.CLIENT}).c('query', {xmlns: Strophe.NS.DISCO_ITEMS}).tree());
		},

		/** Function: Autojoin
		 * When Candy.Core.getOptions().autojoin is true, request autojoin bookmarks (OpenFire)
		 *
		 * Otherwise, if Candy.Core.getOptions().autojoin is an array, join each channel specified.
		 */
		Autojoin: function() {
			// Request bookmarks
			if(Candy.Core.getOptions().autojoin === true) {
				Candy.Core.getConnection().send($iq({type: 'get', xmlns: Strophe.NS.CLIENT}).c('query', {xmlns: Strophe.NS.PRIVATE}).c('storage', {xmlns: Strophe.NS.BOOKMARKS}).tree());
			// Join defined rooms
			} else if($.isArray(Candy.Core.getOptions().autojoin)) {
				$.each(Candy.Core.getOptions().autojoin, function() {
					self.Jabber.Room.Join(this.valueOf());
				});
			}
		},

		/** Function: ResetIgnoreList
		 * Create new ignore privacy list (and reset the old one, if it exists).
		 */
		ResetIgnoreList: function() {
			Candy.Core.getConnection().send($iq({type: 'set', from: Candy.Core.getUser().getJid(), id: 'set1'})
				.c('query', {xmlns: Strophe.NS.PRIVACY }).c('list', {name: 'ignore'}).c('item', {'action': 'allow', 'order': '0'}).tree());
		},

		/** Function: RemoveIgnoreList
		 * Remove an existing ignore list.
		 */
		RemoveIgnoreList: function() {
			Candy.Core.getConnection().send($iq({type: 'set', from: Candy.Core.getUser().getJid(), id: 'remove1'})
				.c('query', {xmlns: Strophe.NS.PRIVACY }).c('list', {name: 'ignore'}).tree());
		},

		/** Function: GetIgnoreList
		 * Get existing ignore privacy list when connecting.
		 */
		GetIgnoreList: function() {
			Candy.Core.getConnection().send($iq({type: 'get', from: Candy.Core.getUser().getJid(), id: 'get1'})
				.c('query', {xmlns: Strophe.NS.PRIVACY }).c('list', {name: 'ignore'}).tree());
		},

		/** Function: SetIgnoreListActive
		 * Set ignore privacy list active
		 */
		SetIgnoreListActive: function() {
			Candy.Core.getConnection().send($iq({type: 'set', from: Candy.Core.getUser().getJid(), id: 'set2'})
				.c('query', {xmlns: Strophe.NS.PRIVACY }).c('active', {name:'ignore'}).tree());
		},

		/** Function: GetJidIfAnonymous
		 * On anonymous login, initially we don't know the jid and as a result, Candy.Core._user doesn't have a jid.
		 * Check if user doesn't have a jid and get it if necessary from the connection.
		 */
		GetJidIfAnonymous: function() {
			if (!Candy.Core.getUser().getJid()) {
				Candy.Core.log("[Jabber] Anonymous login");
				Candy.Core.getUser().data.jid = Candy.Core.getConnection().jid;
			}
		},

		/** Class: Candy.Core.Action.Jabber.Room
		 * Room-specific commands
		 */
		Room: {
			/** Function: Join
			 * Requests disco of specified room and joins afterwards.
			 *
			 * TODO:
			 *   maybe we should wait for disco and later join the room?
			 *   but what if we send disco but don't want/can join the room
			 *
			 * Parameters:
			 *   (String) roomJid - Room to join
			 *   (String) password - [optional] Password for the room
			 */
			Join: function(roomJid, password) {
				self.Jabber.Room.Disco(roomJid);
				Candy.Core.getConnection().muc.join(roomJid, Candy.Core.getUser().getNick(), null, null, password);
			},

			/** Function: Leave
			 * Leaves a room.
			 *
			 * Parameters:
			 *   (String) roomJid - Room to leave
			 */
			Leave: function(roomJid) {
				Candy.Core.getConnection().muc.leave(roomJid, Candy.Core.getRoom(roomJid).getUser().getNick(), function() {});
			},

			/** Function: Disco
			 * Requests <disco info of a room at http://xmpp.org/extensions/xep-0045.html#disco-roominfo>.
			 *
			 * Parameters:
			 *   (String) roomJid - Room to get info for
			 */
			Disco: function(roomJid) {
				Candy.Core.getConnection().send($iq({type: 'get', from: Candy.Core.getUser().getJid(), to: roomJid, id: 'disco3'}).c('query', {xmlns: Strophe.NS.DISCO_INFO}).tree());
			},

			/** Function: Message
			 * Send message
			 *
			 * Parameters:
			 *   (String) roomJid - Room to which send the message into
			 *   (String) msg - Message
			 *   (String) type - "groupchat" or "chat" ("chat" is for private messages)
			 *
			 * Returns:
			 *   (Boolean) - true if message is not empty after trimming, false otherwise.
			 */
			Message: function(roomJid, msg, type) {
				// Trim message
				msg = $.trim(msg);
				if(msg === '') {
					return false;
				}
				Candy.Core.getConnection().muc.message(Candy.Util.escapeJid(roomJid), undefined, msg, type);
				return true;
			},

			/** Function: IgnoreUnignore
			 * Checks if the user is already ignoring the target user, if yes: unignore him, if no: ignore him.
			 *
			 * Uses the ignore privacy list set on connecting.
			 *
			 * Parameters:
			 *   (String) userJid - Target user jid
			 */
			IgnoreUnignore: function(userJid) {
				Candy.Core.getUser().addToOrRemoveFromPrivacyList('ignore', userJid);
				Candy.Core.Action.Jabber.Room.UpdatePrivacyList();
			},

			/** Function: UpdatePrivacyList
			 * Updates privacy list according to the privacylist in the currentUser
			 */
			UpdatePrivacyList: function() {
				var currentUser = Candy.Core.getUser(),
					iq = $iq({type: 'set', from: currentUser.getJid(), id: 'edit1'})
						.c('query', {xmlns: 'jabber:iq:privacy' })
							.c('list', {name: 'ignore'}),
					privacyList = currentUser.getPrivacyList('ignore');
				if (privacyList.length > 0) {
					$.each(privacyList, function(index, jid) {
						iq.c('item', {type:'jid', value: Candy.Util.escapeJid(jid), action: 'deny', order : index})
							.c('message').up().up();
					});
				} else {
					iq.c('item', {action: 'allow', order : '0'});
				}
				Candy.Core.getConnection().send(iq.tree());
			},

			/** Class: Candy.Core.Action.Jabber.Room.Admin
			 * Room administration commands
			 */
			Admin: {
				/** Function: UserAction
				 * Kick or ban a user
				 *
				 * Parameters:
				 *   (String) roomJid - Room in which the kick/ban should be done
				 *   (String) userJid - Victim
				 *   (String) type - "kick" or "ban"
				 *   (String) msg - Reason
				 *
				 * Returns:
				 *   (Boolean) - true if sent successfully, false if type is not one of "kick" or "ban".
				 */
				UserAction: function(roomJid, userJid, type, reason) {
					var iqId,
						itemObj = {nick: Strophe.escapeNode(Strophe.getResourceFromJid(userJid))};
					switch(type) {
						case 'kick':
							iqId = 'kick1';
							itemObj.role = 'none';
							break;
						case 'ban':
							iqId = 'ban1';
							itemObj.affiliation = 'outcast';
							break;
						default:
							return false;
					}
					Candy.Core.getConnection().send($iq({type: 'set', from: Candy.Core.getUser().getJid(), to: roomJid, id: iqId}).c('query', {xmlns: Strophe.NS.MUC_ADMIN }).c('item', itemObj).c('reason').t(reason).tree());
					return true;
				},

				/** Function: SetSubject
				 * Sets subject (topic) of a room.
				 *
				 * Parameters:
				 *   (String) roomJid - Room
				 *   (String) subject - Subject to set
				 */
				SetSubject: function(roomJid, subject) {
					Candy.Core.getConnection().muc.setTopic(roomJid, subject);
				}
			}
		}
	};

	return self;
}(Candy.Core.Action || {}, Strophe, jQuery));
/** File: chatRoom.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.Core.ChatRoom
 * Candy Chat Room
 *
 * Parameters:
 *   (String) roomJid - Room jid
 */
Candy.Core.ChatRoom = function(roomJid) {
	/** Object: room
	 * Object containing roomJid and name.
	 */
	this.room = {
		jid: roomJid,
		name: null
	};

	/** Variable: user
	 * Current local user of this room.
	 */
	this.user = null;

	/** Variable: Roster
	 * Candy.Core.ChatRoster instance
	 */
	this.roster = new Candy.Core.ChatRoster();

	/** Function: setUser
	 * Set user of this room.
	 *
	 * Parameters:
	 *   (Candy.Core.ChatUser) user - Chat user
	 */
	this.setUser = function(user) {
		this.user = user;
	};

	/** Function: getUser
	 * Get current local user
	 *
	 * Returns:
	 *   (Object) - Candy.Core.ChatUser instance or null
	 */
	this.getUser = function() {
		return this.user;
	};

	/** Function: getJid
	 * Get room jid
	 *
	 * Returns:
	 *   (String) - Room jid
	 */
	this.getJid = function() {
		return this.room.jid;
	};

	/** Function: setName
	 * Set room name
	 *
	 * Parameters:
	 *   (String) name - Room name
	 */
	this.setName = function(name) {
		this.room.name = name;
	};

	/** Function: getName
	 * Get room name
	 *
	 * Returns:
	 *   (String) - Room name
	 */
	this.getName = function() {
		return this.room.name;
	};

	/** Function: setRoster
	 * Set roster of room
	 *
	 * Parameters:
	 *   (Candy.Core.ChatRoster) roster - Chat roster
	 */
	this.setRoster = function(roster) {
		this.roster = roster;
	};

	/** Function: getRoster
	 * Get roster
	 *
	 * Returns
	 *   (Candy.Core.ChatRoster) - instance
	 */
	this.getRoster = function() {
		return this.roster;
	};
};
/** File: chatRoster.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

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
/** File: chatUser.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.Core.ChatUser
 * Chat User
 */
Candy.Core.ChatUser = function(jid, nick, affiliation, role) {
	/** Constant: ROLE_MODERATOR
	 * Moderator role
	 */
	this.ROLE_MODERATOR    = 'moderator';

	/** Constant: AFFILIATION_OWNER
	 * Affiliation owner
	 */
	this.AFFILIATION_OWNER = 'owner';

	/** Object: data
	 * User data containing:
	 * - jid
	 * - nick
	 * - affiliation
	 * - role
	 * - privacyLists
	 * - customData to be used by e.g. plugins
	 */
	this.data = {
		jid: jid,
		nick: Strophe.unescapeNode(nick),
		affiliation: affiliation,
		role: role,
		privacyLists: {},
		customData: {}
	};
	
	/** Function: getJid
	 * Gets an unescaped user jid
	 *
	 * See:
	 *   <Candy.Util.unescapeJid>
	 *
	 * Returns:
	 *   (String) - jid
	 */
	this.getJid = function() {
		if(this.data.jid) {
			return Candy.Util.unescapeJid(this.data.jid);
		}
		return;
	};
	
	/** Function: getEscapedJid
	 * Escapes the user's jid (node & resource get escaped)
	 *
	 * See:
	 *   <Candy.Util.escapeJid>
	 *
	 * Returns:
	 *   (String) - escaped jid
	 */
	this.getEscapedJid = function() {
		return Candy.Util.escapeJid(this.data.jid);
	};

	/** Function: getNick
	 * Gets user nick
	 *
	 * Returns:
	 *   (String) - nick
	 */
	this.getNick = function() {
		return Strophe.unescapeNode(this.data.nick);
	};

	/** Function: getRole
	 * Gets user role
	 *
	 * Returns:
	 *   (String) - role
	 */
	this.getRole = function() {
		return this.data.role;
	};

	/** Function: getAffiliation
	 * Gets user affiliation
	 *
	 * Returns:
	 *   (String) - affiliation
	 */
	this.getAffiliation = function() {
		return this.data.affiliation;
	};

	/** Function: isModerator
	 * Check if user is moderator. Depends on the room.
	 *
	 * Returns:
	 *   (Boolean) - true if user has role moderator or affiliation owner
	 */
	this.isModerator = function() {
		return this.getRole() === this.ROLE_MODERATOR || this.getAffiliation() === this.AFFILIATION_OWNER;
	};

	/** Function: addToOrRemoveFromPrivacyList
	 * Convenience function for adding/removing users from ignore list.
	 *
	 * Check if user is already in privacy list. If yes, remove it. If no, add it.
	 *
	 * Parameters:
	 *   (String) list - To which privacy list the user should be added / removed from. Candy supports curently only the "ignore" list.
	 *   (String) jid  - User jid to add/remove
	 *
	 * Returns:
	 *   (Array) - Current privacy list.
	 */
	this.addToOrRemoveFromPrivacyList = function(list, jid) {
		if (!this.data.privacyLists[list]) {
			this.data.privacyLists[list] = [];
		}
		var index = -1;
		if ((index = this.data.privacyLists[list].indexOf(jid)) !== -1) {
			this.data.privacyLists[list].splice(index, 1);
		} else {
			this.data.privacyLists[list].push(jid);
		}
		return this.data.privacyLists[list];
	};

	/** Function: getPrivacyList
	 * Returns the privacy list of the listname of the param.
	 *
	 * Parameters:
	 *   (String) list - To which privacy list the user should be added / removed from. Candy supports curently only the "ignore" list.
	 *
	 * Returns:
	 *   (Array) - Privacy List
	 */
	this.getPrivacyList = function(list) {
		if (!this.data.privacyLists[list]) {
			this.data.privacyLists[list] = [];
		}
		return this.data.privacyLists[list];
	};

	/** Function: isInPrivacyList
	 * Tests if this user ignores the user provided by jid.
	 *
	 * Parameters:
	 *   (String) list - Privacy list
	 *   (String) jid  - Jid to test for
	 *
	 * Returns:
	 *   (Boolean)
	 */
	this.isInPrivacyList = function(list, jid) {
		if (!this.data.privacyLists[list]) {
			return false;
		}
		return this.data.privacyLists[list].indexOf(jid) !== -1;
	};

	/** Function: setCustomData
	 * Stores custom data
	 *
	 *	Parameter:
	 *	  (Object) data - Object containing custom data
	 */
	this.setCustomData = function(data) {
		this.data.customData = data;
	};

	/** Function: getCustomData
	 * Retrieve custom data
	 *
	 *	Returns:
	 *	  (Object) - Object containing custom data
	 */
	this.getCustomData = function() {
		return this.data.customData;
	};
};
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

/** Class: Candy.Core.Event
 * Chat Events
 *
 * Parameters:
 *   (Candy.Core.Event) self - itself
 *   (Strophe) Strophe - Strophe
 *   (jQuery) $ - jQuery
 *   (Candy.Util.Observable) observable - Observable to mixin
 */
Candy.Core.Event = (function(self, Strophe, $, observable) {
	/**
	 * Mixin observable
	 */
	var i;
	for (i in observable) {
		if (observable.hasOwnProperty(i)) {
			self[i] = observable[i];
		}
	}

	/** Enum: KEYS
	 * Observer keys
	 *
	 * CHAT - Chat events
	 * PRESENCE - Presence events
	 * MESSAGE - Message events
	 * LOGIN - Login event
	 */
	self.KEYS = {
		CHAT: 1,
		PRESENCE: 2,
		MESSAGE: 3,
		LOGIN: 4,
		PRESENCE_ERROR: 5
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
		 */
		Connect: function(status) {
			switch(status) {
				case Strophe.Status.CONNECTED:
					Candy.Core.log('[Connection] Connected');
					Candy.Core.Action.Jabber.GetJidIfAnonymous();
					// fall through because the same things need to be done :)
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

			self.notifyObservers(self.KEYS.CHAT, { type: 'connection', status: status } );
		}
	};

	/** Function: Login
	 * Notify view that the login window should be displayed
	 *
	 * Parameters:
	 *   (String) presetJid - Preset user JID
	 */
	self.Login = function(presetJid) {
		self.notifyObservers(self.KEYS.LOGIN, { presetJid: presetJid } );
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
			// Autojoin bookmarks (Openfire)
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

			$('list[name="ignore"] item', msg).each(function() {
				var item = $(this);
				if (item.attr('action') === 'deny') {
					currentUser.addToOrRemoveFromPrivacyList('ignore', item.attr('value'));
				}
			});
			Candy.Core.Action.Jabber.SetIgnoreListActive();
			return false;
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
		 * Returns:
		 *   (Boolean) - true
		 */
		Message: function(msg) {
			Candy.Core.log('[Jabber] Message');
			var msg = $(msg),
				fromJid = msg.attr('from'),
				type = msg.attr('type'),
				toJid = msg.attr('to');
			// Room message
			if(fromJid !== Strophe.getDomainFromJid(fromJid) && (type === 'groupchat' || type === 'chat' || type === 'error')) {
				self.Jabber.Room.Message(msg);
			// Admin message
			} else if(!toJid && fromJid === Strophe.getDomainFromJid(fromJid)) {
				self.notifyObservers(self.KEYS.CHAT, { type: (type || 'message'), message: msg.children('body').text() });
			// Server Message
			} else if(toJid && fromJid === Strophe.getDomainFromJid(fromJid)) {
				self.notifyObservers(self.KEYS.CHAT, { type: (type || 'message'), subject: msg.children('subject').text(), message: msg.children('body').text() });
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
			 * Returns:
			 *   (Boolean) - true
			 */
			Leave: function(msg) {
				Candy.Core.log('[Jabber:Room] Leave');
				var msg = $(msg),
					from = msg.attr('from'),
					roomJid = Strophe.getBareJidFromJid(from);

				// if room is not joined yet, ignore.
				if (!Candy.Core.getRoom(roomJid)) {
					return false;
				}

				var roomName = Candy.Core.getRoom(roomJid).getName(),
					item = msg.find('item'),
					type = 'leave',
					reason,
					actor;

				delete Candy.Core.getRooms()[roomJid];
				// if user gets kicked, role is none and there's a status code 307
				if(item.attr('role') === 'none') {
					if(msg.find('status').attr('code') === '307') {
						type = 'kick';
					} else if(msg.find('status').attr('code') === '301') {
						type = 'ban';
					}
					reason = item.find('reason').text();
					actor  = item.find('actor').attr('jid');
				}

				var user = new Candy.Core.ChatUser(from, Strophe.getResourceFromJid(from), item.attr('affiliation'), item.attr('role'));

				self.notifyObservers(self.KEYS.PRESENCE, { 'roomJid': roomJid, 'roomName': roomName, 'type': type, 'reason': reason, 'actor': actor, 'user': user } );
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
				var msg = $(msg),
					roomJid = Strophe.getBareJidFromJid(msg.attr('from'));

				// Client joined a room
				if(!Candy.Core.getRooms()[roomJid]) {
					Candy.Core.getRooms()[roomJid] = new Candy.Core.ChatRoom(roomJid);
				}
				// Room existed but room name was unknown
				var roomName = msg.find('identity').attr('name'),
					room = Candy.Core.getRoom(roomJid);
				if(room.getName() === null) {
					room.setName(roomName);
				// Room name changed
				}/*else if(room.getName() !== roomName && room.getUser() !== null) {
					// NOTE: We want to notify the View here but jabber doesn't send anything when the room name changes :-(
				}*/
				return true;
			},

			/** Function: Presence
			 * Acts on various presence messages (room leaving, room joining, error presence) and notifies view.
			 *
			 * Parameters:
			 *   (Object) msg - jQuery object of XML message
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			Presence: function(msg) {
				Candy.Core.log('[Jabber:Room] Presence');
				var from = Candy.Util.unescapeJid(msg.attr('from')),
					roomJid = Strophe.getBareJidFromJid(from),
					presenceType = msg.attr('type');

				// Client left a room
				if(Strophe.getResourceFromJid(from) === Candy.Core.getUser().getNick() && presenceType === 'unavailable') {
					self.Jabber.Room.Leave(msg);
					return true;
				}

				// Client joined a room
				var room = Candy.Core.getRoom(roomJid);
				if(!room) {
					Candy.Core.getRooms()[roomJid] = new Candy.Core.ChatRoom(roomJid);
					room = Candy.Core.getRoom(roomJid);
				}

				var roster = room.getRoster(),
					action, user,
					item = msg.find('item');
				// User joined a room
				if(presenceType !== 'unavailable') {
					var nick = Strophe.getResourceFromJid(from);
					user = new Candy.Core.ChatUser(from, nick, item.attr('affiliation'), item.attr('role'));
					// Room existed but client (myself) is not yet registered
					if(room.getUser() === null && Candy.Core.getUser().getNick() === nick) {
						room.setUser(user);
					}
					roster.add(user);
					action = 'join';
				// User left a room
				} else {
					action = 'leave';
					if(item.attr('role') === 'none') {
						if(msg.find('status').attr('code') === '307') {
							action = 'kick';
						} else if(msg.find('status').attr('code') === '301') {
							action = 'ban';
						}
					}
					user = roster.get(from);
					roster.remove(from);
				}

				self.notifyObservers(self.KEYS.PRESENCE, {'roomJid': roomJid, 'roomName': room.getName(), 'user': user, 'action': action, 'currentUser': Candy.Core.getUser() } );
				return true;
			},
			
			/** Function: PresenceError
			 * Acts when a presence of type error has been retrieved.
			 *
			 * Parameters:
			 *   (Object) msg - jQuery object of XML message
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
				delete room;
				
				self.notifyObservers(self.KEYS.PRESENCE_ERROR, {'msg' : msg, 'type': msg.children('error').children()[0].tagName.toLowerCase(), 'roomJid': roomJid, 'roomName': roomName});
			},

			/** Function: Message
			 * Acts on various message events (subject changed, private chat message, multi-user chat message)
			 * and notifies view.
			 *
			 * Parameters:
			 *   (String) msg - jQuery object of XML message
			 *
			 * Returns:
			 *   (Boolean) - true
			 */
			Message: function(msg) {
				Candy.Core.log('[Jabber:Room] Message');
				// Room subject
				var roomJid, message;
				if(msg.children('subject').length > 0) {
					roomJid = Candy.Util.unescapeJid(Strophe.getBareJidFromJid(msg.attr('from')));
					message = { name: Strophe.getNodeFromJid(roomJid), body: msg.children('subject').text(), type: 'subject' };
				// Error messsage
				} else if(msg.attr('type') === 'error') {
					var error = msg.children('error');
					if(error.attr('code') === '500' && error.children('text').length > 0) {
						roomJid = msg.attr('from');
						message = { type: 'info', body: error.children('text').text() };
					}
				// Chat message
				} else if(msg.children('body').length > 0) {
					// Private chat message
					if(msg.attr('type') === 'chat') {
						roomJid = Candy.Util.unescapeJid(msg.attr('from'));
						var bareRoomJid = Strophe.getBareJidFromJid(roomJid),
							// if a 3rd-party client sends a direct message to this user (not via the room) then the username is the node and not the resource.
							isNoConferenceRoomJid = !Candy.Core.getRoom(bareRoomJid),
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
							message = { name: '', body: msg.children('body').text(), type: 'info' };
						}
					}
				// Unhandled message
				} else {
					return true;
				}

				// besides the delayed delivery (XEP-0203), there exists also XEP-0091 which is the legacy delayed delivery.
				// the x[xmlns=jabber:x:delay] is the format in XEP-0091.
				var delay = msg.children('delay') ? msg.children('delay') : msg.children('x[xmlns="' + Strophe.NS.DELAY +'"]'),
					timestamp = delay !== undefined ? delay.attr('stamp') : null;

				self.notifyObservers(self.KEYS.MESSAGE, {roomJid: roomJid, message: message, timestamp: timestamp } );
				return true;
			}
		}
	};

	return self;
}(Candy.Core.Event || {}, Strophe, jQuery, Candy.Util.Observable));
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
}(Candy.View.Event || {}, jQuery));/** File: observer.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.View.Observer
 * Observes Candy core events
 *
 * Parameters:
 *   (Candy.View.Observer) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Observer = (function(self, $) {
	/** Class: Candy.View.Observer.Chat
	 * Chat events
	 */
	self.Chat = {
		/** Function: update
		 * The update method gets called whenever an event to which "Chat" is subscribed.
		 *
		 * Currently listens for connection status updates & admin messages / motd
		 *
		 * Parameters:
		 *   (Candy.Core.Event) obj - Candy core event object
		 *   (Object) args - {type, connection or message & subject}
		 */
		update: function(obj, args) {
			if(args.type === 'connection') {
				switch(args.status) {
					case Strophe.Status.CONNECTING:
					case Strophe.Status.AUTHENTICATING:
						Candy.View.Pane.Chat.Modal.show($.i18n._('statusConnecting'), false, true);
						break;

					case Strophe.Status.ATTACHED:
					case Strophe.Status.CONNECTED:
						Candy.View.Pane.Chat.Modal.show($.i18n._('statusConnected'));
						Candy.View.Pane.Chat.Modal.hide();
						break;

					case Strophe.Status.DISCONNECTING:
						Candy.View.Pane.Chat.Modal.show($.i18n._('statusDisconnecting'), false, true);
						break;

					case Strophe.Status.DISCONNECTED:
						var presetJid = Candy.Core.isAnonymousConnection() ? Strophe.getDomainFromJid(Candy.Core.getUser().getJid()) : null;
						Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._('statusDisconnected'), presetJid);
						Candy.View.Event.Chat.onDisconnect();
						break;

					case Strophe.Status.AUTHFAIL:
						Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._('statusAuthfail'));
						Candy.View.Event.Chat.onAuthfail();
						break;

					default:
						Candy.View.Pane.Chat.Modal.show($.i18n._('status', args.status));
						break;
				}
			} else if(args.type === 'message') {
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
		 *   (Candy.Core.Event) obj - Candy core event object
		 *   (Object) args - Arguments differ on each type
		 *
		 * Uses:
		 *   - <notifyPrivateChats>
		 */
		update: function(obj, args) {
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
				Candy.View.Event.Room.onPresenceChange({ type: args.type, reason: args.reason, roomJid: args.roomJid, user: args.user });
			// A user changed presence
			} else {
				// Initialize room if not yet existing
				if(!Candy.View.Pane.Chat.rooms[args.roomJid]) {
					Candy.View.Pane.Room.init(args.roomJid, args.roomName);
					Candy.View.Pane.Room.show(args.roomJid);
				}
				Candy.View.Pane.Roster.update(args.roomJid, args.user, args.action, args.currentUser);
				// Notify private user chats if existing
				if(Candy.View.Pane.Chat.rooms[args.user.getJid()]) {
					Candy.View.Pane.Roster.update(args.user.getJid(), args.user, args.action, args.currentUser);
					Candy.View.Pane.PrivateRoom.setStatus(args.user.getJid(), args.action);
				}
			}
		},

		/** Function: notifyPrivateChats
		 * Notify private user chats if existing
		 *
		 * Parameters:
		 *   (Candy.Core.chatUser) user - User which has done the event
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
	
	/** Class: Candy.View.Observer.PresenceError
	 * Presence error events
	 */
	self.PresenceError = {
		/** Function: update
		 * Presence errors get handled in this method
		 *
		 * Parameters:
		 *   (Candy.Core.Event) obj - Candy core event object
		 *   (Object) args - {msg, type, roomJid, roomName}
		 */
		update: function(obj, args) {
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
		}
	}

	/** Class: Candy.View.Observer.Message
	 * Message related events
	 */
	self.Message = {
		/** Function: update
		 * Messages received get dispatched from this method.
		 *
		 * Parameters:
		 *   (Candy.Core.Event) obj - Candy core event object
		 *   (Object) args - {message, roomJid}
		 */
		update: function(obj, args) {
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
				Candy.View.Pane.Message.show(args.roomJid, args.message.name, args.message.body, args.timestamp);
			}
		}
	};

	/** Class: Candy.View.Observer.Login
	 * Handles when display login window should appear
	 */
	self.Login = {
		/** Function: update
		 * The login event gets dispatched to this method
		 *
		 * Parameters:
		 *   (Candy.Core.Event) obj - Candy core event object
		 *   (Object) args - {presetJid}
		 */
		update: function(obj, args) {
			Candy.View.Pane.Chat.Modal.showLoginForm(null, args.presetJid);
		}
	};

	return self;
}(Candy.View.Observer || {}, jQuery));/** File: pane.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

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
					privateUserChat: function() { return roomType === 'chat'; },
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
		tabClose: function(e) {
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
			// this is a workaround because browsers prevent to close non-js-opened windows
			/*if($.browser.msie) {
				this.focus();
				self.opener = this;
				self.close();
			} else {
				window.open(location.href, '_self');
				window.close();
			}*/
		},

		/** Function: fitTabs
		 * Fit tab size according to window size
		 */
		fitTabs: function() {
			var availableWidth = $('#chat-tabs').innerWidth(),
				tabsWidth = 0,
				tabs = $('#chat-tabs').children();
			tabs.each(function() {
				tabsWidth += $(this).css({ width: 'auto', overflow: 'visible' }).outerWidth(true);
			});
			if(tabsWidth > availableWidth) {
				// tabs.[outer]Width() measures the first element in `tabs`. It's no very readable but nearly two times faster than using :first
				var tabDiffToRealWidth = tabs.outerWidth(true) - tabs.width(),
					tabWidth = Math.floor((availableWidth) / tabs.length) - tabDiffToRealWidth;
				tabs.css({ width: tabWidth, overflow: 'hidden' });
			}
		},

		/** Function: updateToolbar
		 * Show toolbar
		 */
		updateToolbar: function(roomJid) {
			$('#chat-toolbar').find('.context').click(function(e) {
				self.Chat.Context.show(e.currentTarget, roomJid);
				e.stopPropagation();
			});
			Candy.View.Pane.Chat.Toolbar.updateUsercount(Candy.View.Pane.Chat.rooms[roomJid].usercount);
		},

		/** Function: adminMessage
		 * Display admin message
		 *
		 * Parameters:
		 *   (String) subject - Admin message subject
		 *   (String) message - Message to be displayed
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

				Candy.View.Event.Chat.onAdminMessage({'subject' : subject, 'message' : message});
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

			/** Function: playSound
			 * Play sound (default method).
			 */
			playSound: function() {
				self.Chat.Toolbar.onPlaySound();
			},

			/** Function: onPlaySound
			 * Sound play event handler.
			 *
			 * Don't call this method directly. Call `playSound()` instead.
			 * `playSound()` will only call this method if sound is enabled.
			 */
			onPlaySound: function() {
				var chatSoundPlayer = document.getElementById('chat-sound-player');
				chatSoundPlayer.SetVariable('method:stop', '');
				chatSoundPlayer.SetVariable('method:play', '');
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
					_labelUsername: $.i18n._('labelUsername'),
					_labelPassword: $.i18n._('labelPassword'),
					_loginSubmit: $.i18n._('loginSubmit'),
					displayPassword: !Candy.Core.isAnonymousConnection(),
					displayUsername: Candy.Core.isAnonymousConnection() || !presetJid,
					presetJid: presetJid ? presetJid : false
				}));
				$('#login-form').children()[0].focus();

				// register submit handler
				$('#login-form').submit(function(event) {
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
					_labelNickname: $.i18n._('labelUsername'),
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

				tooltip.css({'left': posLeft.px, 'top': posTop.px, backgroundPosition: posLeft.backgroundPositionAlignment + ' ' + posTop.backgroundPositionAlignment}).fadeIn('fast');

				target.mouseleave(function(event) {
					event.stopPropagation();
					$('#tooltip').stop(false, true).fadeOut('fast', function() { $(this).css({'top': 0, 'left': 0}); });
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
			 * Uses:
			 *   <getMenuLinks> for getting menulinks the user has access to
			 *   <Candy.Util.getPosLeftAccordingToWindowBounds> for positioning
			 *   <Candy.Util.getPosTopAccordingToWindowBounds> for positioning
			 *
			 * Calls:
			 *   <Candy.View.Event.Roster.afterContextMenu> after showing the context menu
			 *
			 * Parameters:
			 *   (Element) elem - On which element it should be shown
			 *   (String) roomJid - Room Jid of the room it should be shown
			 *   (Candy.Core.chatUser) user - User
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

					menu.css({'left': posLeft.px, 'top': posTop.px, backgroundPosition: posLeft.backgroundPositionAlignment + ' ' + posTop.backgroundPositionAlignment});
					menu.fadeIn('fast');

					Candy.View.Event.Roster.afterContextMenu({'roomJid' : roomJid, 'user' : user, 'element': menu });

					return true;
				}
			},

			/** Function: getMenuLinks
			 * Extends <initialMenuLinks> with <Candy.View.Event.Roster.onContextMenu> links and returns those.
			 *
			 * Returns:
			 *   (Object) - object containing the extended menulinks.
			 */
			getMenuLinks: function(roomJid, user, elem) {
				var menulinks = $.extend(this.initialMenuLinks(elem), Candy.View.Event.Roster.onContextMenu({'roomJid' : roomJid, 'user' : user, 'elem': elem })),
					id;

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
							$('#context-modal-form').submit(function(event) {
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
							$('#context-modal-form').submit(function(e) {
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
						'callback': function(e, roomJid, user) {
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

				menu.css({'left': posLeft.px, 'top': posTop.px, backgroundPosition: posLeft.backgroundPositionAlignment + ' ' + posTop.backgroundPositionAlignment});
				menu.fadeIn('fast');

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
		 * Calls:
		 *   - <Candy.View.Event.Room.onAdd>
		 *
		 * Returns:
		 *   (String) - the room id of the element created.
		 */
		init: function(roomJid, roomName, roomType) {
			roomType = roomType || 'groupchat';
			// First room, show sound control
			if(Candy.Util.isEmptyObject(self.Chat.rooms)) {
				self.Chat.Toolbar.show();
			}

			var roomId = Candy.Util.jidToId(roomJid);
			self.Chat.rooms[roomJid] = { id: roomId, usercount: 0, name: roomName, type: roomType, messageCount: 0, scrollPosition: -1 };

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

			Candy.View.Event.Room.onAdd({'roomJid': roomJid, 'type': roomType, 'element': self.Room.getPane(roomJid)});

			return roomId;
		},

		/** Function: show
		 * Show a specific room and hides the other rooms (if there are any)
		 *
		 * Parameters:
		 *   (String) roomJid - room jid to show
		 */
		show: function(roomJid) {
			var roomId = self.Chat.rooms[roomJid].id;
			$('.room-pane').each(function() {
				var elem = $(this);
				if(elem.attr('id') === ('chat-room-' + roomId)) {
					elem.show();
					Candy.View.getCurrent().roomJid = roomJid;
					self.Chat.updateToolbar(roomJid);
					self.Chat.setActiveTab(roomJid);
					self.Chat.clearUnreadMessages(roomJid);
					self.Room.setFocusToForm(roomJid);
					self.Room.scrollToBottom(roomJid);

					Candy.View.Event.Room.onShow({'roomJid': roomJid, 'element' : elem});
				} else {
					elem.hide();

					Candy.View.Event.Room.onHide({'roomJid': roomJid, 'element' : elem});
				}
			});
		},

		/** Function: setSubject
		 * Called when someone changes the subject in the channel
		 *
		 * Parameters:
		 *   (String) roomJid - Room Jid
		 *   (String) subject - The new subject
		 */
		setSubject: function(roomJid, subject) {
			var html = Mustache.to_html(Candy.View.Template.Room.subject, {
				subject: subject,
				roomName: self.Chat.rooms[roomJid].name,
				_roomSubject: $.i18n._('roomSubject'),
				time: Candy.Util.localizedTime(new Date().toGMTString())
			});
			self.Room.appendToMessagePane(roomJid, html);
			self.Room.scrollToBottom(roomJid);

			Candy.View.Event.Room.onSubjectChange({'roomJid': roomJid, 'element' : self.Room.getPane(roomJid), 'subject' : subject});
		},

		/** Function: close
		 * Close a room and remove everything in the DOM belonging to this room.
		 *
		 * NOTICE: There's a rendering bug in Opera when all rooms have been closed. (Take a look in the source for a more detailed description)
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

			Candy.View.Event.Room.onClose({'roomJid' : roomJid});
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
					self.Room.getPane(roomJid, '.message-pane').children().slice(0, options.remove*2).remove();
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
		 * Calls:
		 *   - <Candy.View.Event.Room.onAdd>
		 */
		open: function(roomJid, roomName, switchToRoom, isNoConferenceRoomJid) {
			var user = isNoConferenceRoomJid ? Candy.Core.getUser() : self.Room.getUser(Strophe.getBareJidFromJid(roomJid));
			// if target user is in privacy list, don't open the private chat.
			if (Candy.Core.getUser().isInPrivacyList('ignore', roomJid)) {
				return false;
			}
			if(!self.Chat.rooms[roomJid]) {
				self.Room.init(roomJid, roomName, 'chat');
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

			Candy.View.Event.Room.onAdd({'roomJid': roomJid, type: 'chat', 'element': self.Room.getPane(roomJid)});
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
			} else {
				self.Chat.getTab(roomJid).addClass('offline').removeClass('online');

				messageForm.children('.field').attr('disabled', true);
				messageForm.children('.submit').attr('disabled', true);
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
		 */
		update: function(roomJid, user, action, currentUser) {
			var roomId = self.Chat.rooms[roomJid].id,
				userId = Candy.Util.jidToId(user.getJid()),
				usercountDiff = -1;

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
					}),
					userElem = $('#user-' + roomId + '-' + userId);

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

					self.Roster.joinAnimation('user-' + roomId + '-' + userId);
					// only show other users joining & don't show if there's no message in the room.
					if(currentUser !== undefined && user.getNick() !== currentUser.getNick() && self.Room.getUser(roomJid)) {
						// always show join message in private room, even if status messages have been disabled
						if (self.Chat.rooms[roomJid].type === 'chat') {
							self.Chat.onInfoMessage(roomJid, $.i18n._('userJoinedRoom', [user.getNick()]));
						} else {
							self.Chat.infoMessage(roomJid, $.i18n._('userJoinedRoom', [user.getNick()]));
						}
					}
				// user is in room but maybe the affiliation/role has changed
				} else {
					usercountDiff = 0;
					userElem.replaceWith(html);
					$('#user-' + roomId + '-' + userId).css({opacity: 1}).show();
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

			Candy.View.Event.Roster.onUpdate({'roomJid' : roomJid, 'user' : user, 'action': action, 'element': $('#user-' + roomId + '-' + userId)});
		},

		/** Function: userClick
		 * Click handler for opening a private room
		 */
		userClick: function() {
			var elem = $(this);
			self.PrivateRoom.open(elem.attr('data-jid'), elem.attr('data-nick'), true);
		},

		/** Function: joinAnimation
		 * Animates specified elementId on join
		 *
		 * Parameters:
		 *   (String) elementId - Specific element to do the animation on
		 */
		joinAnimation: function(elementId) {
			$('#' + elementId).stop(true).slideDown('normal', function() { $(this).animate({ opacity: 1 }); });
		},

		/** Function: leaveAnimation
		 * Leave animation for specified element id and removes the DOM element on completion.
		 *
		 * Parameters:
		 *   (String) elementId - Specific element to do the animation on
		 */
		leaveAnimation: function(elementId) {
			$('#' + elementId).stop(true).attr('id', '#' + elementId + '-leaving').animate({ opacity: 0 }, {
				complete: function() {
					$(this).slideUp('normal', function() { $(this).remove(); });
				}
			});
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
		 */
		submit: function(event) {
			var roomType = Candy.View.Pane.Chat.rooms[Candy.View.getCurrent().roomJid].type,
				message = $(this).children('.field').val().substring(0, Candy.View.getOptions().crop.message.body);

			message = Candy.View.Event.Message.beforeSend(message);

			Candy.Core.Action.Jabber.Room.Message(Candy.View.getCurrent().roomJid, message, roomType);
			// Private user chat. Jabber won't notify the user who has sent the message. Just show it as the user hits the button...
			if(roomType === 'chat' && message) {
				self.Message.show(Candy.View.getCurrent().roomJid, self.Room.getUser(Candy.View.getCurrent().roomJid).getNick(), message);
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
		 *   (String) timestamp - [optional] Timestamp of the message, if not present, current date.
		 */
		show: function(roomJid, name, message, timestamp) {
			message = Candy.Util.Parser.all(message.substring(0, Candy.View.getOptions().crop.message.body));
			message = Candy.View.Event.Message.beforeShow({'roomJid': roomJid, 'nick': name, 'message': message});
			if(!message) {
				return;
			}

			var html = Mustache.to_html(Candy.View.Template.Message.item, {
				name: name,
				displayName: Candy.Util.crop(name, Candy.View.getOptions().crop.message.nickname),
				message: message,
				time: Candy.Util.localizedTime(timestamp || new Date().toGMTString())
			});
			self.Room.appendToMessagePane(roomJid, html);
			var elem = self.Room.getPane(roomJid, '.message-pane').children().last();
			// click on username opens private chat
			elem.find('a.name').click(function(event) {
				event.preventDefault();
				// Check if user is online and not myself
				if(name !== self.Room.getUser(Candy.View.getCurrent().roomJid).getNick() && Candy.Core.getRoom(roomJid).getRoster().get(roomJid + '/' + name)) {
					Candy.View.Pane.PrivateRoom.open(roomJid + '/' + name, name, true);
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

			Candy.View.Event.Message.onShow({'roomJid': roomJid, 'element': elem, 'nick': name, 'message': message});
		}
	};

	return self;
}(Candy.View.Pane || {}, jQuery));
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
/** File: translation.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

/** Class: Candy.View.Translation
 * Contains translations
 */
Candy.View.Translation = {
	'en' : {
		'status': 'Status: %s',
		'statusConnecting': 'Connecting...',
		'statusConnected' : 'Connected',
		'statusDisconnecting': 'Disconnecting...',
		'statusDisconnected' : 'Disconnected',
		'statusAuthfail': 'Authentication failed',

		'roomSubject'  : 'Subject:',
		'messageSubmit': 'Send',

		'labelUsername': 'Username:',
		'labelPassword': 'Password:',
		'loginSubmit'  : 'Login',
		'loginInvalid'  : 'Invalid JID',

		'reason'				: 'Reason:',
		'subject'				: 'Subject:',
		'reasonWas'				: 'Reason was: %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'You have been kicked from %2$s by %1$s',
		'youHaveBeenKicked'     : 'You have been kicked from %s',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'You have been banned from %1$s by %2$s',
		'youHaveBeenBanned'     : 'You have been banned from %s',

		'privateActionLabel' : 'Private chat',
		'ignoreActionLabel'  : 'Ignore',
		'unignoreActionLabel' : 'Unignore',

		'setSubjectActionLabel': 'Change Subject',

		'administratorMessageSubject' : 'Administrator',

		'userJoinedRoom'           : '%s joined the room.',
		'userLeftRoom'             : '%s left the room.',
		'userHasBeenKickedFromRoom': '%s has been kicked from the room.',
		'userHasBeenBannedFromRoom': '%s has been banned from the room.',

		'presenceUnknownWarningSubject': 'Notice:',
		'presenceUnknownWarning'       : 'This user might be offline. We can\'t track his presence.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderator',
		'tooltipIgnored'		: 'You ignore this user',
		'tooltipEmoticons'		: 'Emoticons',
		'tooltipSound'			: 'Play sound for new private messages',
		'tooltipAutoscroll'		: 'Autoscroll',
		'tooltipStatusmessage'	: 'Display status messages',
		'tooltipAdministration'	: 'Room Administration',
		'tooltipUsercount'		: 'Room Occupants',

		'enterRoomPassword' : 'Room "%s" is password protected.',
		'enterRoomPasswordSubmit' : 'Join room',
		'passwordEnteredInvalid' : 'Invalid password for room "%s".',

		'nicknameConflict': 'Username already in use. Please choose another one.',

		'errorMembersOnly': 'You can\'t join room "%s": Insufficient rights.',
		'errorMaxOccupantsReached': 'You can\'t join room "%s": Too many occupants.',

		'antiSpamMessage' : 'Please do not spam. You have been blocked for a short-time.'
	},
	
	'de' : {
		'status': 'Status: %s',
		'statusConnecting': 'Verbinden...',
		'statusConnected' : 'Verbunden',
		'statusDisconnecting': 'Verbindung trennen...',
		'statusDisconnected' : 'Verbindung getrennt',
		'statusAuthfail': 'Authentifizierung fehlgeschlagen',

		'roomSubject'  : 'Thema:',
		'messageSubmit': 'Senden',

		'labelUsername': 'Benutzername:',
		'labelPassword': 'Passwort:',
		'loginSubmit'  : 'Anmelden',
		'loginInvalid'  : 'Ungültige JID',

		'reason'				: 'Begründung:',
		'subject'				: 'Titel:',
		'reasonWas'				: 'Begründung: %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'Du wurdest soeben aus dem Raum %1$s gekickt (%2$s)',
		'youHaveBeenKicked'     : 'Du wurdest soeben aus dem Raum %s gekickt',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'Du wurdest soeben aus dem Raum %1$s verbannt (%2$s)',
		'youHaveBeenBanned'     : 'Du wurdest soeben aus dem Raum %s verbannt',

		'privateActionLabel' : 'Privater Chat',
		'ignoreActionLabel'  : 'Ignorieren',
		'unignoreActionLabel' : 'Nicht mehr ignorieren',

		'setSubjectActionLabel': 'Thema ändern',

		'administratorMessageSubject' : 'Administrator',

		'userJoinedRoom'           : '%s hat soeben den Raum betreten.',
		'userLeftRoom'             : '%s hat soeben den Raum verlassen.',
		'userHasBeenKickedFromRoom': '%s ist aus dem Raum gekickt worden.',
		'userHasBeenBannedFromRoom': '%s ist aus dem Raum verbannt worden.',

		'presenceUnknownWarningSubject': 'Hinweis:',
		'presenceUnknownWarning'       : 'Dieser Benutzer könnte bereits abgemeldet sein. Wir können seine Anwesenheit nicht verfolgen.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderator',
		'tooltipIgnored'		: 'Du ignorierst diesen Benutzer',
		'tooltipEmoticons'		: 'Smileys',
		'tooltipSound'			: 'Ton abspielen bei neuen privaten Nachrichten',
		'tooltipAutoscroll'		: 'Autoscroll',
		'tooltipStatusmessage'	: 'Statusnachrichten anzeigen',
		'tooltipAdministration'	: 'Raum Administration',
		'tooltipUsercount'		: 'Anzahl Benutzer im Raum',

		'enterRoomPassword' : 'Raum "%s" ist durch ein Passwort geschützt.',
		'enterRoomPasswordSubmit' : 'Raum betreten',
		'passwordEnteredInvalid' : 'Inkorrektes Passwort für Raum "%s".',

		'nicknameConflict': 'Der Benutzername wird bereits verwendet. Bitte wähle einen anderen.',

		'errorMembersOnly': 'Du kannst den Raum "%s" nicht betreten: Ungenügende Rechte.',
		'errorMaxOccupantsReached': 'Du kannst den Raum "%s" nicht betreten: Benutzerlimit erreicht.',

		'antiSpamMessage' : 'Bitte nicht spammen. Du wurdest für eine kurze Zeit blockiert.'
	},
	
	'fr' : {
		'status': 'Status: %s',
		'statusConnecting': 'Connecter...',
		'statusConnected' : 'Connecté.',
		'statusDisconnecting': 'Déconnecter....',
		'statusDisconnected' : 'Déconnecté.',
		'statusAuthfail': 'Authentification a échoué',

		'roomSubject'  : 'Sujet:',
		'messageSubmit': 'Envoyer',

		'labelUsername': 'Nom d\'utilisateur:',
		'labelPassword': 'Mot de passe:',
		'loginSubmit'  : 'Inscription',
		'loginInvalid'  : 'JID invalide',

		'reason'				: 'Justification:',
		'subject'				: 'Titre:',
		'reasonWas'				: 'Justification: %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'Tu as été expulsé de le salon %1$s (%2$s)',
		'youHaveBeenKicked'     : 'Tu as été expulsé de le salon %s',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'Tu as été banni de le salon %1$s (%2$s)',
		'youHaveBeenBanned'     : 'Tu as été banni de le salon %s',

		'privateActionLabel' : 'Chat privé',
		'ignoreActionLabel'  : 'Ignorer',
		'unignoreActionLabel' : 'Ne plus ignorer',

		'setSubjectActionLabel': 'Changer le sujet',

		'administratorMessageSubject' : 'Administrateur',

		'userJoinedRoom'           : '%s vient d\'entrer dans le salon.',
		'userLeftRoom'             : '%s vient de quitter le salon.',
		'userHasBeenKickedFromRoom': '%s a été expulsé du salon.',
		'userHasBeenBannedFromRoom': '%s a été banni du salon.',

		'presenceUnknownWarningSubject': 'Note:',
		'presenceUnknownWarning'       : 'Cet utilisateur n\'est malheureusement plus connecté, le message ne sera pas envoyé.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Modérateur',
		'tooltipIgnored'		: 'Tu ignores cette personne',
		'tooltipEmoticons'		: 'Smileys',
		'tooltipSound'			: 'Jouer un son lorsque tu reçois de nouveaux messages privés',
		'tooltipAutoscroll'		: 'Auto-defilement',
		'tooltipStatusmessage'	: 'Messages d\'état',
		'tooltipAdministration'	: 'Administrer le salon',
		'tooltipUsercount'		: 'Nombre d\'utilisateurs dans le salon',

		'enterRoomPassword' : 'Le salon "%s" est protégé par un mot de passe.',
		'enterRoomPasswordSubmit' : 'Entrer dans le salon',
		'passwordEnteredInvalid' : 'Le mot de passe four le salon "%s" est invalide.',

		'nicknameConflict': 'Le nom d\'utilisateur est déjà utilisé. Choisi un autre.',

		'errorMembersOnly': 'Tu ne peut pas entrer de le salon "%s": droits insuffisants.',
		'errorMaxOccupantsReached': 'Tu ne peut pas entrer de le salon "%s": Limite d\'utilisateur atteint.',

		'antiSpamMessage' : 'S\'il te plaît, pas de spam. Tu as été bloqué pendant une courte période..'
	},
	
	'nl' : {
		'status': 'Status: %s',
		'statusConnecting': 'Verbinding maken...',
		'statusConnected' : 'Verbinding is gereed',
		'statusDisconnecting': 'Verbinding verbreken...',
		'statusDisconnected' : 'Verbinding is verbroken',
		'statusAuthfail': 'Authenticatie is mislukt',

		'roomSubject'  : 'Onderwerp:',
		'messageSubmit': 'Verstuur',

		'labelUsername': 'Gebruikersnaam:',
		'labelPassword': 'Wachtwoord:',
		'loginSubmit'  : 'Inloggen',
		'loginInvalid'  : 'JID is onjuist',

		'reason'				: 'Reden:',
		'subject'				: 'Onderwerp:',
		'reasonWas'				: 'De reden was: %s.',
		'kickActionLabel'		: 'Verwijderen',
		'youHaveBeenKickedBy'   : 'Je bent verwijderd van %1$s door %2$s',
		'youHaveBeenKicked'     : 'Je bent verwijderd van %s',
		'banActionLabel'		: 'Blokkeren',
		'youHaveBeenBannedBy'   : 'Je bent geblokkeerd van %1$s door %2$s',
		'youHaveBeenBanned'     : 'Je bent geblokkeerd van %s',

		'privateActionLabel' : 'Prive gesprek',
		'ignoreActionLabel'  : 'Negeren',
		'unignoreActionLabel' : 'Niet negeren',

		'setSubjectActionLabel': 'Onderwerp wijzigen',

		'administratorMessageSubject' : 'Beheerder',

		'userJoinedRoom'           : '%s komt de chat binnen.',
		'userLeftRoom'             : '%s heeft de chat verlaten.',
		'userHasBeenKickedFromRoom': '%s is verwijderd.',
		'userHasBeenBannedFromRoom': '%s is geblokkeerd.',

		'presenceUnknownWarningSubject': 'Mededeling:',
		'presenceUnknownWarning'       : 'Deze gebruiker is waarschijnlijk offline, we kunnen zijn/haar aanwezigheid niet vaststellen.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderator',
		'tooltipIgnored'		: 'Je negeert deze gebruiker',
		'tooltipEmoticons'		: 'Emotie-iconen',
		'tooltipSound'			: 'Speel een geluid af bij nieuwe privé berichten.',
		'tooltipAutoscroll'		: 'Automatisch scrollen',
		'tooltipStatusmessage'	: 'Statusberichten weergeven',
		'tooltipAdministration'	: 'Instellingen',
		'tooltipUsercount'		: 'Gebruikers',

		'enterRoomPassword' : 'De Chatroom "%s" is met een wachtwoord beveiligd.',
		'enterRoomPasswordSubmit' : 'Ga naar Chatroom',
		'passwordEnteredInvalid' : 'Het wachtwoord voor de Chatroom "%s" is onjuist.',

		'nicknameConflict': 'De gebruikersnaam is reeds in gebruik. Probeer a.u.b. een andere gebruikersnaam.',

		'errorMembersOnly': 'Je kunt niet deelnemen aan de Chatroom "%s": Je hebt onvoldoende rechten.',
		'errorMaxOccupantsReached': 'Je kunt niet deelnemen aan de Chatroom "%s": Het maximum aantal gebruikers is bereikt.',

		'antiSpamMessage' : 'Het is niet toegestaan om veel berichten naar de server te versturen. Je bent voor een korte periode geblokkeerd.'
	},
	
	'es': {
		'status': 'Estado: %s',
		'statusConnecting': 'Conectando...',
		'statusConnected' : 'Conectado',
		'statusDisconnecting': 'Desconectando...',
		'statusDisconnected' : 'Desconectado',
		'statusAuthfail': 'Falló la autenticación',

		'roomSubject'  : 'Asunto:',
		'messageSubmit': 'Enviar',

		'labelUsername': 'Usuario:',
		'labelPassword': 'Clave:',
		'loginSubmit'  : 'Entrar',
		'loginInvalid'  : 'JID no válido',

		'reason'				: 'Razón:',
		'subject'				: 'Asunto:',
		'reasonWas'				: 'La razón fue: %s.',
		'kickActionLabel'		: 'Expulsar',
		'youHaveBeenKickedBy'   : 'Has sido expulsado de %1$s por %2$s',
		'youHaveBeenKicked'     : 'Has sido expulsado de %s',
		'banActionLabel'		: 'Prohibir',
		'youHaveBeenBannedBy'   : 'Has sido expulsado permanentemente de %1$s por %2$s',
		'youHaveBeenBanned'     : 'Has sido expulsado permanentemente de %s',

		'privateActionLabel' : 'Chat privado',
		'ignoreActionLabel'  : 'Ignorar',
		'unignoreActionLabel' : 'No ignorar',

		'setSubjectActionLabel': 'Cambiar asunto',

		'administratorMessageSubject' : 'Administrador',

		'userJoinedRoom'           : '%s se ha unido a la sala.',
		'userLeftRoom'             : '%s ha dejado la sala.',
		'userHasBeenKickedFromRoom': '%s ha sido expulsado de la sala.',
		'userHasBeenBannedFromRoom': '%s ha sido expulsado permanentemente de la sala.',

		'presenceUnknownWarningSubject': 'Atención:',
		'presenceUnknownWarning'       : 'Éste usuario podría estar desconectado..',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderador',
		'tooltipIgnored'		: 'Ignoras a éste usuario',
		'tooltipEmoticons'		: 'Emoticonos',
		'tooltipSound'			: 'Reproducir un sonido para nuevos mensajes privados',
		'tooltipAutoscroll'		: 'Desplazamiento automático',
		'tooltipStatusmessage'	: 'Mostrar mensajes de estado',
		'tooltipAdministration'	: 'Administración de la sala',
		'tooltipUsercount'		: 'Usuarios en la sala',

		'enterRoomPassword' : 'La sala "%s" está protegida mediante contraseña.',
		'enterRoomPasswordSubmit' : 'Unirse a la sala',
		'passwordEnteredInvalid' : 'Contraseña incorrecta para la sala "%s".',

		'nicknameConflict': 'El nombre de usuario ya está siendo utilizado. Por favor elija otro.',

		'errorMembersOnly': 'No se puede unir a la sala "%s": no tiene privilegios suficientes.',
		'errorMaxOccupantsReached': 'No se puede unir a la sala "%s": demasiados participantes.',

		'antiSpamMessage' : 'Por favor, no hagas spam. Has sido bloqueado temporalmente.'
	},

	'cn': {
		'status': '状态: %s',
		'statusConnecting': '连接中...',
		'statusConnected': '已连接',
		'statusDisconnecting': '断开连接中...',
		'statusDisconnected': '已断开连接',
		'statusAuthfail': '认证失败',

		'roomSubject': '主题:',
		'messageSubmit': '发送',

		'labelUsername': '用户名:',
		'labelPassword': '密码:',
		'loginSubmit': '登录',
		'loginInvalid': '用户名不合法',

		'reason': '原因:',
		'subject': '主题:',
		'reasonWas': '原因是: %s.',
		'kickActionLabel': '踢除',
		'youHaveBeenKickedBy': '你在 %1$s 被管理者 %2$s 请出房间',
		'banActionLabel': '禁言',
		'youHaveBeenBannedBy': '你在 %1$s 被管理者 %2$s 禁言',

		'privateActionLabel': '单独对话',
		'ignoreActionLabel': '忽略',
		'unignoreActionLabel': '不忽略',

		'setSubjectActionLabel': '变更主题',

		'administratorMessageSubject': '管理员',

		'userJoinedRoom': '%s 加入房间',
		'userLeftRoom': '%s 离开房间',
		'userHasBeenKickedFromRoom': '%s 被请出这个房间',
		'userHasBeenBannedFromRoom': '%s 被管理者禁言',

		'presenceUnknownWarningSubject': '注意:',
		'presenceUnknownWarning': '这个会员可能已经下线，不能追踪到他的连接信息',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole': '管理',
		'tooltipIgnored': '你忽略了这个会员',
		'tooltipEmoticons': '表情',
		'tooltipSound': '新消息发音',
		'tooltipAutoscroll': '滚动条',
		'tooltipStatusmessage': '禁用状态消息',
		'tooltipAdministration': '房间管理',
		'tooltipUsercount': '房间占有者',

		'enterRoomPassword': '登录房间 "%s" 需要密码.',
		'enterRoomPasswordSubmit': '加入房间',
		'passwordEnteredInvalid': '登录房间 "%s" 的密码不正确',

		'nicknameConflict': '用户名已经存在，请另选一个',

		'errorMembersOnly': '您的权限不够，不能登录房间 "%s" ',
		'errorMaxOccupantsReached': '房间 "%s" 的人数已达上限，您不能登录',

		'antiSpamMessage': '因为您在短时间内发送过多的消息 服务器要阻止您一小段时间。'
	},
	
	'ja' : {
		'status'        : 'ステータス: %s',
		'statusConnecting'  : '接続中…',
		'statusConnected'   : '接続されました',
		'statusDisconnecting'   : 'ディスコネクト中…',
		'statusDisconnected'    : 'ディスコネクトされました',
		'statusAuthfail'    : '認証に失敗しました',

		'roomSubject'       : 'トピック：',
		'messageSubmit'     : '送信',

		'labelUsername'     : 'ユーザーネーム：',
		'labelPassword'     : 'パスワード：',
		'loginSubmit'       : 'ログイン',
		'loginInvalid'      : 'ユーザーネームが正しくありません',

		'reason'        : '理由：',
		'subject'       : 'トピック：',
		'reasonWas'     : '理由: %s。',
		'kickActionLabel'   : 'キック',
		'youHaveBeenKickedBy'   : 'あなたは%2$sにより%1$sからキックされました。',
		'youHaveBeenKicked'     : 'あなたは%sからキックされました。',
		'banActionLabel'    : 'アカウントバン',
		'youHaveBeenBannedBy'   : 'あなたは%2$sにより%1$sからアカウントバンされました。',
		'youHaveBeenBanned'     : 'あなたは%sからアカウントバンされました。',

		'privateActionLabel'    : 'プライベートメッセージ',
		'ignoreActionLabel' : '無視する',
		'unignoreActionLabel'   : '無視をやめる',

		'setSubjectActionLabel'     : 'トピックを変える',

		'administratorMessageSubject'   : '管理者',

		'userJoinedRoom'        : '%sは入室しました。',
		'userLeftRoom'          : '%sは退室しました。',
		'userHasBeenKickedFromRoom' : '%sは部屋からキックされました。',
		'userHasBeenBannedFromRoom' : '%sは部屋からアカウントバンされました。',

		'presenceUnknownWarningSubject' : '忠告：',
		'presenceUnknownWarning'    : 'このユーザーのステータスは不明です。',

		'dateFormat'        : 'dd.mm.yyyy',
		'timeFormat'        : 'HH:MM:ss',

		'tooltipRole'       : 'モデレーター',
		'tooltipIgnored'    : 'このユーザーを無視設定にしている',
		'tooltipEmoticons'  : '絵文字',
		'tooltipSound'      : '新しいメッセージが届くたびに音を鳴らす',
		'tooltipAutoscroll' : 'オートスクロール',
		'tooltipStatusmessage'  : 'ステータスメッセージを表示',
		'tooltipAdministration' : '部屋の管理',
		'tooltipUsercount'  : 'この部屋の参加者の数',

		'enterRoomPassword'     : '"%s"の部屋に入るにはパスワードが必要です。',
		'enterRoomPasswordSubmit'   : '部屋に入る',
		'passwordEnteredInvalid'    : '"%s"のパスワードと異なるパスワードを入力しました。',

		'nicknameConflict'  : 'このユーザーネームはすでに利用されているため、別のユーザーネームを選んでください。',

		'errorMembersOnly'      : '"%s"の部屋に入ることができません: 利用権限を満たしていません。',
		'errorMaxOccupantsReached'  : '"%s"の部屋に入ることができません: 参加者の数はすでに上限に達しました。',

		'antiSpamMessage'   : 'スパムなどの行為はやめてください。あなたは一時的にブロックされました。'
	}
};