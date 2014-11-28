/** File: core.js
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

/* global Candy, window, Strophe, jQuery */

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
		/** PrivateVariable: _status
		 * Current Strophe connection state
		 */
		_status,
		/** PrivateVariable: _options
		 * Options:
		 *   (Boolean) debug - Debug (Default: false)
		 *   (Array|Boolean) autojoin - Autojoin these channels. When boolean true, do not autojoin, wait if the server sends something.
		 */
		_options = {
			/** Boolean: autojoin
			 * If set to `true` try to get the bookmarks and autojoin the rooms (supported by ejabberd, Openfire).
			 * You may want to define an array of rooms to autojoin: `['room1@conference.host.tld', 'room2...]` (ejabberd, Openfire, ...)
			 */
			autojoin: undefined,
			debug: false,
			disableWindowUnload: false,
			/** Integer: presencePriority
			 * Default priority for presence messages in order to receive messages across different resources
			 */
			presencePriority: 1,
			/** String: resource
			 * JID resource to use when connecting to the server.
			 * Specify `''` (an empty string) to request a random resource.
			 */
			resource: Candy.about.name
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
			_addNamespace('PUBSUB', 'http://jabber.org/protocol/pubsub');
		},

		_getEscapedJidFromJid = function(jid) {
			var node = Strophe.getNodeFromJid(jid),
				domain = Strophe.getDomainFromJid(jid);
			return node ? Strophe.escapeNode(node) + '@' + domain : domain;
		};

	/** Function: init
	 * Initialize Core.
	 *
	 * Parameters:
	 *   (String) service - URL of BOSH/Websocket service
	 *   (Object) options - Options for candy
	 */
	self.init = function(service, options) {
		_service = service;
		// Apply options
		$.extend(true, _options, options);

		// Enable debug logging
		if(_options.debug) {
			if(typeof window.console !== undefined && typeof window.console.log !== undefined) {
				// Strophe has a polyfill for bind which doesn't work in IE8.
				if(Function.prototype.bind && Candy.Util.getIeVersion() > 8) {
					self.log = Function.prototype.bind.call(console.log, console);
				} else {
					self.log = function() {
						Function.prototype.apply.call(console.log, console, arguments);
					};
				}
			}
			self.log('[Init] Debugging enabled');
		}

		_addNamespaces();

		// Connect to BOSH/Websocket service
		_connection = new Strophe.Connection(_service);
		_connection.rawInput = self.rawInput.bind(self);
		_connection.rawOutput = self.rawOutput.bind(self);

		// set caps node
		_connection.caps.node = 'https://candy-chat.github.io/candy/';

		// Window unload handler... works on all browsers but Opera. There is NO workaround.
		// Opera clients getting disconnected 1-2 minutes delayed.
		if (!_options.disableWindowUnload) {
			window.onbeforeunload = self.onWindowUnload;
		}
	};

	/** Function: registerEventHandlers
	 * Adds listening handlers to the connection.
	 *
	 * Use with caution from outside of Candy.
	 */
	self.registerEventHandlers = function() {
		self.addHandler(self.Event.Jabber.Version, Strophe.NS.VERSION, 'iq');
		self.addHandler(self.Event.Jabber.Presence, null, 'presence');
		self.addHandler(self.Event.Jabber.Message, null, 'message');
		self.addHandler(self.Event.Jabber.Bookmarks, Strophe.NS.PRIVATE, 'iq');
		self.addHandler(self.Event.Jabber.Room.Disco, Strophe.NS.DISCO_INFO, 'iq', 'result');

		self.addHandler(_connection.disco._onDiscoInfo.bind(_connection.disco), Strophe.NS.DISCO_INFO, 'iq', 'get');
		self.addHandler(_connection.disco._onDiscoItems.bind(_connection.disco), Strophe.NS.DISCO_ITEMS, 'iq', 'get');
		self.addHandler(_connection.caps._delegateCapabilities.bind(_connection.caps), Strophe.NS.CAPS);
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
		self.registerEventHandlers();
		/** Event: candy:core.before-connect
		 * Triggered before a connection attempt is made.
		 *
		 * Plugins should register their stanza handlers using this event
		 * to ensure that they are set.
		 *
		 * See also <#84 at https://github.com/candy-chat/candy/issues/84>.
		 *
		 * Parameters:
		 *   (Strophe.Connection) conncetion - Strophe connection
		 */
		$(Candy).triggerHandler('candy:core.before-connect', {
			connection: _connection
		});

		_anonymousConnection = !_anonymousConnection ? jidOrHost && jidOrHost.indexOf("@") < 0 : true;

		if(jidOrHost && password) {
			// authentication
			_connection.connect(_getEscapedJidFromJid(jidOrHost) + '/' + _options.resource, password, Candy.Core.Event.Strophe.Connect);
			if (nick) {
				_user = new self.ChatUser(jidOrHost, nick);
			} else {
				_user = new self.ChatUser(jidOrHost, Strophe.getNodeFromJid(jidOrHost));
			}
		} else if(jidOrHost && nick) {
			// anonymous connect
			_connection.connect(_getEscapedJidFromJid(jidOrHost) + '/' + _options.resource, null, Candy.Core.Event.Strophe.Connect);
			_user = new self.ChatUser(null, nick); // set jid to null because we'll later receive it
		} else if(jidOrHost) {
			Candy.Core.Event.Login(jidOrHost);
		} else {
			// display login modal
			Candy.Core.Event.Login();
		}
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
		self.registerEventHandlers();
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

	/** Function: removeRoom
	 * Removes a room from the rooms list
	 *
	 * Parameters:
	 *   (String) roomJid - roomJid
	 */
	self.removeRoom = function(roomJid) {
		delete _rooms[roomJid];
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

	/** Function: getStropheStatus
	 * Get the status set by Strophe.
	 *
	 * Returns:
	 *   (Strophe.Status.*) - one of Strophe's statuses
	 */
	self.getStropheStatus = function() {
		return _status;
	};

	/** Function: setStropheStatus
	 * Set the strophe status
	 *
	 * Called by:
	 *   Candy.Core.Event.Strophe.Connect
	 *
	 * Parameters:
	 *   (Strophe.Status.*) status - Strophe's status
	 */
	self.setStropheStatus = function(status) {
		_status = status;
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
		_connection.options.sync = true;
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
