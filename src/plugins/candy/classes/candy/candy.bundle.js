/** File: candy.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global jQuery */
/** Class: Candy
 * Candy base class for initalizing the view and the core
 *
 * Parameters:
 *   (Candy) self - itself
 *   (jQuery) $ - jQuery
 */
var Candy = function(self, $) {
    /** Object: about
	 * About candy
	 *
	 * Contains:
	 *   (String) name - Candy
	 *   (Float) version - Candy version
	 */
    self.about = {
        name: "Candy",
        version: "2.2.0"
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
        if (!options.viewClass) {
            options.viewClass = self.View;
        }
        options.viewClass.init($("#candy"), options.view);
        self.Core.init(service, options.core);
    };
    return self;
}(Candy || {}, jQuery);

/** File: core.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, window, Strophe, jQuery */
/** Class: Candy.Core
 * Candy Chat Core
 *
 * Parameters:
 *   (Candy.Core) self - itself
 *   (Strophe) Strophe - Strophe JS
 *   (jQuery) $ - jQuery
 */
Candy.Core = function(self, Strophe, $) {
    /** PrivateVariable: _connection
		 * Strophe connection
		 */
    var _connection = null, /** PrivateVariable: _service
		 * URL of BOSH service
		 */
    _service = null, /** PrivateVariable: _user
		 * Current user (me)
		 */
    _user = null, /** PrivateVariable: _roster
		 * Main roster of contacts
		 */
    _roster = null, /** PrivateVariable: _rooms
		 * Opened rooms, containing instances of Candy.Core.ChatRooms
		 */
    _rooms = {}, /** PrivateVariable: _anonymousConnection
		 * Set in <Candy.Core.connect> when jidOrHost doesn't contain a @-char.
		 */
    _anonymousConnection = false, /** PrivateVariable: _status
		 * Current Strophe connection state
		 */
    _status, /** PrivateVariable: _options
		 * Config options
		 */
    _options = {
        /** Boolean: autojoin
			 * If set to `true` try to get the bookmarks and autojoin the rooms (supported by ejabberd, Openfire).
			 * You may want to define an array of rooms to autojoin: `['room1@conference.host.tld', 'room2...]` (ejabberd, Openfire, ...)
			 */
        autojoin: undefined,
        /** Boolean: disconnectWithoutTabs
			 * If you set to `false`, when you close all of the tabs, the service does not disconnect.
			 * Set to `true`, when you close all of the tabs, the service will disconnect.
			 */
        disconnectWithoutTabs: true,
        /** String: conferenceDomain
			 * Holds the prefix for an XMPP chat server's conference subdomain.
			 * If not set, assumes no specific subdomain.
			 */
        conferenceDomain: undefined,
        /** Boolean: debug
			 * Enable debug
			 */
        debug: false,
        /** List: domains
			 * If non-null, causes login form to offer this
			 * pre-set list of domains to choose between when
			 * logging in.  Any user-provided domain is discarded
			 * and the selected domain is appended.
			 * For each list item, only characters up to the first
			 * whitespace are used, so you can append extra
			 * information to each item if desired.
			 */
        domains: null,
        /** Boolean: hideDomainList
			 * If true, the domain list defined above is suppressed.
			 * Without a selector displayed, the default domain
			 * (usually the first one listed) will be used as
			 * described above.  Probably only makes sense with a
			 * single domain defined.
			 */
        hideDomainList: false,
        /** Boolean: disableCoreNotifications
			 * If set to `true`, the built-in notifications (sounds and badges) are disabled.
			 * This is useful if you are using a plugin to handle notifications.
			 */
        disableCoreNotifications: false,
        /** Boolean: disableWindowUnload
			 * Disable window unload handler which usually disconnects from XMPP
			 */
        disableWindowUnload: false,
        /** Integer: presencePriority
			 * Default priority for presence messages in order to receive messages across different resources
			 */
        presencePriority: 1,
        /** String: resource
			 * JID resource to use when connecting to the server.
			 * Specify `''` (an empty string) to request a random resource.
			 */
        resource: Candy.about.name,
        /** Boolean: useParticipantRealJid
			 * If set true, will direct one-on-one chats to participant's real JID rather than their MUC jid
			 */
        useParticipantRealJid: false,
        /**
			 * Roster version we claim to already have. Used when loading a cached roster.
			 * Defaults to null, indicating we don't have the roster.
			 */
        initialRosterVersion: null,
        /**
			 * Initial roster items. Loaded from a cache, used to bootstrap displaying a roster prior to fetching updates.
			 */
        initialRosterItems: []
    }, /** PrivateFunction: _addNamespace
		 * Adds a namespace.
		 *
		 * Parameters:
		 *   (String) name - namespace name (will become a constant living in Strophe.NS.*)
		 *   (String) value - XML Namespace
		 */
    _addNamespace = function(name, value) {
        Strophe.addNamespace(name, value);
    }, /** PrivateFunction: _addNamespaces
		 * Adds namespaces needed by Candy.
		 */
    _addNamespaces = function() {
        _addNamespace("PRIVATE", "jabber:iq:private");
        _addNamespace("BOOKMARKS", "storage:bookmarks");
        _addNamespace("PRIVACY", "jabber:iq:privacy");
        _addNamespace("DELAY", "urn:xmpp:delay");
        _addNamespace("JABBER_DELAY", "jabber:x:delay");
        _addNamespace("PUBSUB", "http://jabber.org/protocol/pubsub");
        _addNamespace("CARBONS", "urn:xmpp:carbons:2");
    }, _getEscapedJidFromJid = function(jid) {
        var node = Strophe.getNodeFromJid(jid), domain = Strophe.getDomainFromJid(jid);
        return node ? Strophe.escapeNode(node) + "@" + domain : domain;
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
        if (_options.debug) {
            if (typeof window.console !== undefined && typeof window.console.log !== undefined) {
                // Strophe has a polyfill for bind which doesn't work in IE8.
                if (Function.prototype.bind && Candy.Util.getIeVersion() > 8) {
                    self.log = Function.prototype.bind.call(console.log, console);
                } else {
                    self.log = function() {
                        Function.prototype.apply.call(console.log, console, arguments);
                    };
                }
            }
            Strophe.log = function(level, message) {
                var level_name, console_level;
                switch (level) {
                  case Strophe.LogLevel.DEBUG:
                    level_name = "DEBUG";
                    console_level = "log";
                    break;

                  case Strophe.LogLevel.INFO:
                    level_name = "INFO";
                    console_level = "info";
                    break;

                  case Strophe.LogLevel.WARN:
                    level_name = "WARN";
                    console_level = "info";
                    break;

                  case Strophe.LogLevel.ERROR:
                    level_name = "ERROR";
                    console_level = "error";
                    break;

                  case Strophe.LogLevel.FATAL:
                    level_name = "FATAL";
                    console_level = "error";
                    break;
                }
                console[console_level]("[Strophe][" + level_name + "]: " + message);
            };
            self.log("[Init] Debugging enabled");
        }
        _addNamespaces();
        _roster = new Candy.Core.ChatRoster();
        // Connect to BOSH/Websocket service
        _connection = new Strophe.Connection(_service);
        _connection.rawInput = self.rawInput.bind(self);
        _connection.rawOutput = self.rawOutput.bind(self);
        // set caps node
        _connection.caps.node = "https://candy-chat.github.io/candy/";
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
        self.addHandler(self.Event.Jabber.Version, Strophe.NS.VERSION, "iq");
        self.addHandler(self.Event.Jabber.Presence, null, "presence");
        self.addHandler(self.Event.Jabber.Message, null, "message");
        self.addHandler(self.Event.Jabber.Bookmarks, Strophe.NS.PRIVATE, "iq");
        self.addHandler(self.Event.Jabber.Room.Disco, Strophe.NS.DISCO_INFO, "iq", "result");
        self.addHandler(_connection.disco._onDiscoInfo.bind(_connection.disco), Strophe.NS.DISCO_INFO, "iq", "get");
        self.addHandler(_connection.disco._onDiscoItems.bind(_connection.disco), Strophe.NS.DISCO_ITEMS, "iq", "get");
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
        $(Candy).triggerHandler("candy:core.before-connect", {
            connection: _connection
        });
        _anonymousConnection = !_anonymousConnection ? jidOrHost && jidOrHost.indexOf("@") < 0 : true;
        if (jidOrHost && password) {
            // Respect the resource, if provided
            var resource = Strophe.getResourceFromJid(jidOrHost);
            if (resource) {
                _options.resource = resource;
            }
            // authentication
            _connection.connect(_getEscapedJidFromJid(jidOrHost) + "/" + _options.resource, password, Candy.Core.Event.Strophe.Connect);
            if (nick) {
                _user = new self.ChatUser(jidOrHost, nick);
            } else {
                _user = new self.ChatUser(jidOrHost, Strophe.getNodeFromJid(jidOrHost));
            }
        } else if (jidOrHost && nick) {
            // anonymous connect
            _connection.connect(_getEscapedJidFromJid(jidOrHost) + "/" + _options.resource, null, Candy.Core.Event.Strophe.Connect);
            _user = new self.ChatUser(null, nick);
        } else if (jidOrHost) {
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
    self.attach = function(jid, sid, rid, nick) {
        if (nick) {
            _user = new self.ChatUser(jid, nick);
        } else {
            _user = new self.ChatUser(jid, Strophe.getNodeFromJid(jid));
        }
        // Reset before every connection attempt to make sure reconnections work after authfail, alltabsclosed, ...
        _connection.reset();
        self.registerEventHandlers();
        _connection.attach(jid, sid, rid, Candy.Core.Event.Strophe.Connect);
    };
    /** Function: disconnect
	 * Leave all rooms and disconnect
	 */
    self.disconnect = function() {
        if (_connection.connected) {
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
    /** Function: getRoster
	 * Gets main roster
	 *
	 * Returns:
	 *   Instance of Candy.Core.ChatRoster
	 */
    self.getRoster = function() {
        return _roster;
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
        this.log("RECV: " + data);
    };
    /** Function rawOutput
	 * (Overridden from Strophe.Connection.rawOutput)
	 *
	 * Logs all raw output if debug is set to true.
	 */
    self.rawOutput = function(data) {
        this.log("SENT: " + data);
    };
    /** Function: log
	 * Overridden to do something useful if debug is set to true.
	 *
	 * See: Candy.Core#init
	 */
    self.log = function() {};
    /** Function: warn
	 * Print a message to the browser's "info" log
	 * Enabled regardless of debug mode
	 */
    self.warn = function() {
        Function.prototype.apply.call(console.warn, console, arguments);
    };
    /** Function: error
	 * Print a message to the browser's "error" log
	 * Enabled regardless of debug mode
	 */
    self.error = function() {
        Function.prototype.apply.call(console.error, console, arguments);
    };
    return self;
}(Candy.Core || {}, Strophe, jQuery);

/** File: view.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global jQuery, Candy, window, Mustache, document */
/** Class: Candy.View
 * The Candy View Class
 *
 * Parameters:
 *   (Candy.View) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View = function(self, $) {
    /** PrivateObject: _current
		 * Object containing current container & roomJid which the client sees.
		 */
    var _current = {
        container: null,
        roomJid: null
    }, /** PrivateObject: _options
		 *
		 * Options:
		 *   (String) language - language to use
		 *   (String) assets - path to assets (res) directory (with trailing slash)
		 *   (Object) messages - limit: clean up message pane when n is reached / remove: remove n messages after limit has been reached
		 *   (Object) crop - crop if longer than defined: message.nickname=15, message.body=1000, message.url=undefined (not cropped), roster.nickname=15
		 *   (Bool) enableXHTML - [default: false] enables XHTML messages sending & displaying
		 */
    _options = {
        language: "en",
        assets: "res/",
        messages: {
            limit: 2e3,
            remove: 500
        },
        crop: {
            message: {
                nickname: 15,
                body: 1e3,
                url: undefined
            },
            roster: {
                nickname: 15
            }
        },
        enableXHTML: false
    }, /** PrivateFunction: _setupTranslation
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
    }, /** PrivateFunction: _registerObservers
		 * Register observers. Candy core will now notify the View on changes.
		 */
    _registerObservers = function() {
        $(Candy).on("candy:core.chat.connection", self.Observer.Chat.Connection);
        $(Candy).on("candy:core.chat.message", self.Observer.Chat.Message);
        $(Candy).on("candy:core.login", self.Observer.Login);
        $(Candy).on("candy:core.autojoin-missing", self.Observer.AutojoinMissing);
        $(Candy).on("candy:core.presence", self.Observer.Presence.update);
        $(Candy).on("candy:core.presence.leave", self.Observer.Presence.update);
        $(Candy).on("candy:core.presence.room", self.Observer.Presence.update);
        $(Candy).on("candy:core.presence.error", self.Observer.PresenceError);
        $(Candy).on("candy:core.message", self.Observer.Message);
    }, /** PrivateFunction: _registerWindowHandlers
		 * Register window focus / blur / resize handlers.
		 *
		 * jQuery.focus()/.blur() <= 1.5.1 do not work for IE < 9. Fortunately onfocusin/onfocusout will work for them.
		 */
    _registerWindowHandlers = function() {
        if (Candy.Util.getIeVersion() < 9) {
            $(document).focusin(Candy.View.Pane.Window.onFocus).focusout(Candy.View.Pane.Window.onBlur);
        } else {
            $(window).focus(Candy.View.Pane.Window.onFocus).blur(Candy.View.Pane.Window.onBlur);
        }
        $(window).resize(Candy.View.Pane.Chat.fitTabs);
    }, /** PrivateFunction: _initToolbar
		 * Initialize toolbar.
		 */
    _initToolbar = function() {
        self.Pane.Chat.Toolbar.init();
    }, /** PrivateFunction: _delegateTooltips
		 * Delegate mouseenter on tooltipified element to <Candy.View.Pane.Chat.Tooltip.show>.
		 */
    _delegateTooltips = function() {
        $("body").delegate("li[data-tooltip]", "mouseenter", Candy.View.Pane.Chat.Tooltip.show);
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
        if (options.resources) {
            options.assets = options.resources;
        }
        delete options.resources;
        $.extend(true, _options, options);
        _setupTranslation(_options.language);
        // Set path to emoticons
        Candy.Util.Parser.setEmoticonPath(this.getOptions().assets + "img/emoticons/");
        // Start DOMination...
        _current.container = container;
        _current.container.html(Mustache.to_html(Candy.View.Template.Chat.pane, {
            tooltipEmoticons: $.i18n._("tooltipEmoticons"),
            tooltipSound: $.i18n._("tooltipSound"),
            tooltipAutoscroll: $.i18n._("tooltipAutoscroll"),
            tooltipStatusmessage: $.i18n._("tooltipStatusmessage"),
            tooltipAdministration: $.i18n._("tooltipAdministration"),
            tooltipUsercount: $.i18n._("tooltipUsercount"),
            assetsPath: this.getOptions().assets
        }, {
            tabs: Candy.View.Template.Chat.tabs,
            mobile: Candy.View.Template.Chat.mobileIcon,
            rooms: Candy.View.Template.Chat.rooms,
            modal: Candy.View.Template.Chat.modal,
            toolbar: Candy.View.Template.Chat.toolbar
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
}(Candy.View || {}, jQuery);

/** File: util.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, MD5, Strophe, document, escape, jQuery */
/** Class: Candy.Util
 * Candy utils
 *
 * Parameters:
 *   (Candy.Util) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.Util = function(self, $) {
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
	 * Escapes a jid
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
        var node = Strophe.escapeNode(Strophe.getNodeFromJid(jid)), domain = Strophe.getDomainFromJid(jid), resource = Strophe.getResourceFromJid(jid);
        jid = node + "@" + domain;
        if (resource) {
            jid += "/" + resource;
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
        var node = Strophe.unescapeNode(Strophe.getNodeFromJid(jid)), domain = Strophe.getDomainFromJid(jid), resource = Strophe.getResourceFromJid(jid);
        jid = node + "@" + domain;
        if (resource) {
            jid += "/" + resource;
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
            str = str.substr(0, len - 3) + "...";
        }
        return str;
    };
    /** Function: parseAndCropXhtml
	 * Parses the XHTML and applies various Candy related filters to it.
	 *
	 *  - Ensures it contains only valid XHTML
	 *  - Crops text to a max length
	 *  - Parses the text in order to display html
	 *
	 * Parameters:
	 *   (String) str - String containing XHTML
	 *   (Integer) len - Max text length
	 */
    self.parseAndCropXhtml = function(str, len) {
        return $("<div/>").append(self.createHtml($(str).get(0), len)).html();
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
        document.cookie = name + "=" + value + ";expires=" + exp.toUTCString() + ";path=/";
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
        if (document.cookie) {
            var regex = new RegExp(escape(name) + "=([^;]*)", "gm"), matches = regex.exec(document.cookie);
            if (matches) {
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
        document.cookie = name + "=;expires=Thu, 01-Jan-70 00:00:01 GMT;path=/";
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
        var windowWidth = $(document).width(), elemWidth = elem.outerWidth(), marginDiff = elemWidth - elem.outerWidth(true), backgroundPositionAlignment = "left";
        if (pos + elemWidth >= windowWidth) {
            pos -= elemWidth - marginDiff;
            backgroundPositionAlignment = "right";
        }
        return {
            px: pos,
            backgroundPositionAlignment: backgroundPositionAlignment
        };
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
        var windowHeight = $(document).height(), elemHeight = elem.outerHeight(), marginDiff = elemHeight - elem.outerHeight(true), backgroundPositionAlignment = "top";
        if (pos + elemHeight >= windowHeight) {
            pos -= elemHeight - marginDiff;
            backgroundPositionAlignment = "bottom";
        }
        return {
            px: pos,
            backgroundPositionAlignment: backgroundPositionAlignment
        };
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
        // See if we were passed a Date object
        var date;
        if (dateTime.toDateString) {
            date = dateTime;
        } else {
            date = self.iso8601toDate(dateTime);
        }
        if (date.toDateString() === new Date().toDateString()) {
            return date.format($.i18n._("timeFormat"));
        } else {
            return date.format($.i18n._("dateFormat"));
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
        var timestamp = Date.parse(date);
        if (isNaN(timestamp)) {
            var struct = /^(\d{4}|[+\-]\d{6})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{3,}))?)?(?:(Z)|([+\-])(\d{2})(?::?(\d{2}))?))?/.exec(date);
            if (struct) {
                var minutesOffset = 0;
                if (struct[8] !== "Z") {
                    minutesOffset = +struct[10] * 60 + +struct[11];
                    if (struct[9] === "+") {
                        minutesOffset = -minutesOffset;
                    }
                }
                minutesOffset -= new Date().getTimezoneOffset();
                return new Date(+struct[1], +struct[2] - 1, +struct[3], +struct[4], +struct[5] + minutesOffset, +struct[6], struct[7] ? +struct[7].substr(0, 3) : 0);
            } else {
                // XEP-0091 date
                timestamp = Date.parse(date.replace(/^(\d{4})(\d{2})(\d{2})/, "$1-$2-$3") + "Z");
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
        for (prop in obj) {
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
        elem.css({
            display: "none"
        });
        setTimeout(function() {
            this.css({
                display: "block"
            });
        }.bind(elem), 1);
    };
    /** PrivateVariable: ie
	 * Checks for IE version
	 *
	 * From: http://stackoverflow.com/a/5574871/315242
	 */
    var ie = function() {
        var undef, v = 3, div = document.createElement("div"), all = div.getElementsByTagName("i");
        while (// adds innerhtml and continues as long as all[0] is truthy
        div.innerHTML = "<!--[if gt IE " + ++v + "]><i></i><![endif]-->", all[0]) {}
        return v > 4 ? v : undef;
    }();
    /** Function: getIeVersion
	 * Returns local variable `ie` which you can use to detect which IE version
	 * is available.
	 *
	 * Use e.g. like this: if(Candy.Util.getIeVersion() < 9) alert('kaboom');
	 */
    self.getIeVersion = function() {
        return ie;
    };
    /** Function: isMobile
	  * Checks to see if we're on a mobile device.
	  */
    self.isMobile = function() {
        var check = false;
        (function(a) {
            if (/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od|ad)|android|ipad|playbook|silk|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i.test(a) || /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(a.substr(0, 4))) {
                check = true;
            }
        })(navigator.userAgent || navigator.vendor || window.opera);
        return check;
    };
    /** Class: Candy.Util.Parser
	 * Parser for emoticons, links and also supports escaping.
	 */
    self.Parser = {
        /** Function: jid
		 * Parse a JID into an object with each element
		 *
		 * Parameters:
		 * 	(String) jid - The string representation of a JID
		 */
        jid: function(jid) {
            var r = /^(([^@]+)@)?([^\/]+)(\/(.*))?$/i, a = jid.match(r);
            if (!a) {
                throw "not a valid jid (" + jid + ")";
            }
            return {
                node: a[2],
                domain: a[3],
                resource: a[4]
            };
        },
        /** PrivateVariable: _emoticonPath
		 * Path to emoticons.
		 *
		 * Use setEmoticonPath() to change it
		 */
        _emoticonPath: "",
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
        emoticons: [ {
            plain: ":)",
            regex: /((\s):-?\)|:-?\)(\s|$))/gm,
            image: "Smiling.png"
        }, {
            plain: ";)",
            regex: /((\s);-?\)|;-?\)(\s|$))/gm,
            image: "Winking.png"
        }, {
            plain: ":D",
            regex: /((\s):-?D|:-?D(\s|$))/gm,
            image: "Grinning.png"
        }, {
            plain: ";D",
            regex: /((\s);-?D|;-?D(\s|$))/gm,
            image: "Grinning_Winking.png"
        }, {
            plain: ":(",
            regex: /((\s):-?\(|:-?\((\s|$))/gm,
            image: "Unhappy.png"
        }, {
            plain: "^^",
            regex: /((\s)\^\^|\^\^(\s|$))/gm,
            image: "Happy_3.png"
        }, {
            plain: ":P",
            regex: /((\s):-?P|:-?P(\s|$))/gim,
            image: "Tongue_Out.png"
        }, {
            plain: ";P",
            regex: /((\s);-?P|;-?P(\s|$))/gim,
            image: "Tongue_Out_Winking.png"
        }, {
            plain: ":S",
            regex: /((\s):-?S|:-?S(\s|$))/gim,
            image: "Confused.png"
        }, {
            plain: ":/",
            regex: /((\s):-?\/|:-?\/(\s|$))/gm,
            image: "Uncertain.png"
        }, {
            plain: "8)",
            regex: /((\s)8-?\)|8-?\)(\s|$))/gm,
            image: "Sunglasses.png"
        }, {
            plain: "$)",
            regex: /((\s)\$-?\)|\$-?\)(\s|$))/gm,
            image: "Greedy.png"
        }, {
            plain: "oO",
            regex: /((\s)oO|oO(\s|$))/gm,
            image: "Huh.png"
        }, {
            plain: ":x",
            regex: /((\s):x|:x(\s|$))/gm,
            image: "Lips_Sealed.png"
        }, {
            plain: ":666:",
            regex: /((\s):666:|:666:(\s|$))/gm,
            image: "Devil.png"
        }, {
            plain: "<3",
            regex: /((\s)&lt;3|&lt;3(\s|$))/gm,
            image: "Heart.png"
        } ],
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
            for (i = this.emoticons.length - 1; i >= 0; i--) {
                text = text.replace(this.emoticons[i].regex, '$2<img class="emoticon" alt="$1" title="$1" src="' + this._emoticonPath + this.emoticons[i].image + '" />$3');
            }
            return text;
        },
        /** Function: linkify
		 * Replaces URLs with a HTML-link.
		 * big regex adapted from https://gist.github.com/dperini/729294 - Diego Perini, MIT license.
		 *
		 * Parameters:
		 *   (String) text - Text to linkify
		 *
		 * Returns:
		 *   Linkified text
		 */
        linkify: function(text) {
            text = text.replace(/(^|[^\/])(www\.[^\.]+\.[\S]+(\b|$))/gi, "$1http://$2");
            return text.replace(/(\b(?:(?:https?|ftp|file):\/\/)(?:\S+(?::\S*)?@)?(?:(?!(?:10|127)(?:\.\d{1,3}){3})(?!(?:169\.254|192\.168)(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:1\d\d|2[01]\d|22[0-3]|[1-9]\d?)(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)(?:\.(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff]{2,})))(?::\d{2,5})?(?:\/\S*)?)/gi, function(matched, url) {
                return '<a href="' + url + '" target="_blank">' + self.crop(url, Candy.View.getOptions().crop.message.url) + "</a>";
            });
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
            return $("<div/>").text(text).html();
        },
        /** Function: nl2br
		 * replaces newline characters with a <br/> to make multi line messages look nice
		 *
		 * Parameters:
		 *   (String) text - Text to process
		 *
		 * Returns:
		 *   Processed text
		 */
        nl2br: function(text) {
            return text.replace(/\r\n|\r|\n/g, "<br />");
        },
        /** Function: all
		 * Does everything of the parser: escaping, linkifying and emotifying.
		 *
		 * Parameters:
		 *   (String) text - Text to parse
		 *
		 * Returns:
		 *   (String) Parsed text
		 */
        all: function(text) {
            if (text) {
                text = this.escape(text);
                text = this.linkify(text);
                text = this.emotify(text);
                text = this.nl2br(text);
            }
            return text;
        }
    };
    /** Function: createHtml
	 * Copy an HTML DOM element into an XML DOM.
	 *
	 * This function copies a DOM element and all its descendants and returns
	 * the new copy.
	 *
	 * It's a function copied & adapted from [Strophe.js core.js](https://github.com/strophe/strophejs/blob/master/src/core.js).
	 *
	 * Parameters:
	 *   (HTMLElement) elem - A DOM element.
	 *   (Integer) maxLength - Max length of text
	 *   (Integer) currentLength - Current accumulated text length
	 *
	 * Returns:
	 *   A new, copied DOM element tree.
	 */
    self.createHtml = function(elem, maxLength, currentLength) {
        /* jshint -W073 */
        currentLength = currentLength || 0;
        var i, el, j, tag, attribute, value, css, cssAttrs, attr, cssName, cssValue;
        if (elem.nodeType === Strophe.ElementType.NORMAL) {
            tag = elem.nodeName.toLowerCase();
            if (Strophe.XHTML.validTag(tag)) {
                try {
                    el = $("<" + tag + "/>");
                    for (i = 0; i < Strophe.XHTML.attributes[tag].length; i++) {
                        attribute = Strophe.XHTML.attributes[tag][i];
                        value = elem.getAttribute(attribute);
                        if (typeof value === "undefined" || value === null || value === "" || value === false || value === 0) {
                            continue;
                        }
                        if (attribute === "style" && typeof value === "object") {
                            if (typeof value.cssText !== "undefined") {
                                value = value.cssText;
                            }
                        }
                        // filter out invalid css styles
                        if (attribute === "style") {
                            css = [];
                            cssAttrs = value.split(";");
                            for (j = 0; j < cssAttrs.length; j++) {
                                attr = cssAttrs[j].split(":");
                                cssName = attr[0].replace(/^\s*/, "").replace(/\s*$/, "").toLowerCase();
                                if (Strophe.XHTML.validCSS(cssName)) {
                                    cssValue = attr[1].replace(/^\s*/, "").replace(/\s*$/, "");
                                    css.push(cssName + ": " + cssValue);
                                }
                            }
                            if (css.length > 0) {
                                value = css.join("; ");
                                el.attr(attribute, value);
                            }
                        } else {
                            el.attr(attribute, value);
                        }
                    }
                    for (i = 0; i < elem.childNodes.length; i++) {
                        el.append(self.createHtml(elem.childNodes[i], maxLength, currentLength));
                    }
                } catch (e) {
                    // invalid elements
                    Candy.Core.warn("[Util:createHtml] Error while parsing XHTML:", e);
                    el = Strophe.xmlTextNode("");
                }
            } else {
                el = Strophe.xmlGenerator().createDocumentFragment();
                for (i = 0; i < elem.childNodes.length; i++) {
                    el.appendChild(self.createHtml(elem.childNodes[i], maxLength, currentLength));
                }
            }
        } else if (elem.nodeType === Strophe.ElementType.FRAGMENT) {
            el = Strophe.xmlGenerator().createDocumentFragment();
            for (i = 0; i < elem.childNodes.length; i++) {
                el.appendChild(self.createHtml(elem.childNodes[i], maxLength, currentLength));
            }
        } else if (elem.nodeType === Strophe.ElementType.TEXT) {
            var text = elem.nodeValue;
            currentLength += text.length;
            if (maxLength && currentLength > maxLength) {
                text = text.substring(0, maxLength);
            }
            text = Candy.Util.Parser.all(text);
            el = $.parseHTML(text);
        }
        return el;
    };
    return self;
}(Candy.Util || {}, jQuery);

/** File: action.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, $iq, navigator, Candy, $pres, Strophe, jQuery, $msg */
/** Class: Candy.Core.Action
 * Chat Actions (basicly a abstraction of Jabber commands)
 *
 * Parameters:
 *   (Candy.Core.Action) self - itself
 *   (Strophe) Strophe - Strophe
 *   (jQuery) $ - jQuery
 */
Candy.Core.Action = function(self, Strophe, $) {
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
            Candy.Core.getConnection().sendIQ($iq({
                type: "result",
                to: Candy.Util.escapeJid(msg.attr("from")),
                from: Candy.Util.escapeJid(msg.attr("to")),
                id: msg.attr("id")
            }).c("query", {
                xmlns: Strophe.NS.VERSION
            }).c("name", Candy.about.name).up().c("version", Candy.about.version).up().c("os", navigator.userAgent));
        },
        /** Function: SetNickname
		 * Sets the supplied nickname for all rooms (if parameter "room" is not specified) or
		 * sets it only for the specified rooms
		 *
		 * Parameters:
		 *   (String) nickname - New nickname
		 *   (Array) rooms - Rooms
		 */
        SetNickname: function(nickname, rooms) {
            rooms = rooms instanceof Array ? rooms : Candy.Core.getRooms();
            var roomNick, presence, conn = Candy.Core.getConnection();
            $.each(rooms, function(roomJid) {
                roomNick = Candy.Util.escapeJid(roomJid + "/" + nickname);
                presence = $pres({
                    to: roomNick,
                    from: conn.jid,
                    id: "pres:" + conn.getUniqueId()
                });
                Candy.Core.getConnection().send(presence);
            });
        },
        /** Function: Roster
		 * Sends a request for a roster
		 */
        Roster: function() {
            var roster = Candy.Core.getConnection().roster, options = Candy.Core.getOptions();
            roster.registerCallback(Candy.Core.Event.Jabber.RosterPush);
            $.each(options.initialRosterItems, function(i, item) {
                // Blank out resources because their cached value is not relevant
                item.resources = {};
            });
            roster.get(Candy.Core.Event.Jabber.RosterFetch, options.initialRosterVersion, options.initialRosterItems);
            // Bootstrap our roster with cached items
            Candy.Core.Event.Jabber.RosterLoad(roster.items);
        },
        /** Function: Presence
		 * Sends a request for presence
		 *
		 * Parameters:
		 *   (Object) attr - Optional attributes
		 *   (Strophe.Builder) el - Optional element to include in presence stanza
		 */
        Presence: function(attr, el) {
            var conn = Candy.Core.getConnection();
            attr = attr || {};
            if (!attr.id) {
                attr.id = "pres:" + conn.getUniqueId();
            }
            var pres = $pres(attr).c("priority").t(Candy.Core.getOptions().presencePriority.toString()).up().c("c", conn.caps.generateCapsAttrs()).up();
            if (el) {
                pres.node.appendChild(el.node);
            }
            conn.send(pres.tree());
        },
        /** Function: Services
		 * Sends a request for disco items
		 */
        Services: function() {
            Candy.Core.getConnection().sendIQ($iq({
                type: "get",
                xmlns: Strophe.NS.CLIENT
            }).c("query", {
                xmlns: Strophe.NS.DISCO_ITEMS
            }).tree());
        },
        /** Function: Autojoin
		 * When Candy.Core.getOptions().autojoin is true, request autojoin bookmarks (OpenFire)
		 *
		 * Otherwise, if Candy.Core.getOptions().autojoin is an array, join each channel specified.
		 * Channel can be in jid:password format to pass room password if needed.

		 * Triggers:
		 *   candy:core.autojoin-missing in case no autojoin info has been found
		 */
        Autojoin: function() {
            // Request bookmarks
            if (Candy.Core.getOptions().autojoin === true) {
                Candy.Core.getConnection().sendIQ($iq({
                    type: "get",
                    xmlns: Strophe.NS.CLIENT
                }).c("query", {
                    xmlns: Strophe.NS.PRIVATE
                }).c("storage", {
                    xmlns: Strophe.NS.BOOKMARKS
                }).tree());
                var pubsubBookmarkRequest = Candy.Core.getConnection().getUniqueId("pubsub");
                Candy.Core.addHandler(Candy.Core.Event.Jabber.Bookmarks, Strophe.NS.PUBSUB, "iq", "result", pubsubBookmarkRequest);
                Candy.Core.getConnection().sendIQ($iq({
                    type: "get",
                    id: pubsubBookmarkRequest
                }).c("pubsub", {
                    xmlns: Strophe.NS.PUBSUB
                }).c("items", {
                    node: Strophe.NS.BOOKMARKS
                }).tree());
            } else if ($.isArray(Candy.Core.getOptions().autojoin)) {
                $.each(Candy.Core.getOptions().autojoin, function() {
                    self.Jabber.Room.Join.apply(null, this.valueOf().split(":", 2));
                });
            } else {
                /** Event: candy:core.autojoin-missing
				 * Triggered when no autojoin information has been found
				 */
                $(Candy).triggerHandler("candy:core.autojoin-missing");
            }
        },
        /** Function: EnableCarbons
		 * Enable message carbons (XEP-0280)
		 */
        EnableCarbons: function() {
            Candy.Core.getConnection().sendIQ($iq({
                type: "set"
            }).c("enable", {
                xmlns: Strophe.NS.CARBONS
            }).tree());
        },
        /** Function: ResetIgnoreList
		 * Create new ignore privacy list (and reset the previous one, if it exists).
		 */
        ResetIgnoreList: function() {
            Candy.Core.getConnection().sendIQ($iq({
                type: "set",
                from: Candy.Core.getUser().getEscapedJid()
            }).c("query", {
                xmlns: Strophe.NS.PRIVACY
            }).c("list", {
                name: "ignore"
            }).c("item", {
                action: "allow",
                order: "0"
            }).tree());
        },
        /** Function: RemoveIgnoreList
		 * Remove an existing ignore list.
		 */
        RemoveIgnoreList: function() {
            Candy.Core.getConnection().sendIQ($iq({
                type: "set",
                from: Candy.Core.getUser().getEscapedJid()
            }).c("query", {
                xmlns: Strophe.NS.PRIVACY
            }).c("list", {
                name: "ignore"
            }).tree());
        },
        /** Function: GetIgnoreList
		 * Get existing ignore privacy list when connecting.
		 */
        GetIgnoreList: function() {
            var iq = $iq({
                type: "get",
                from: Candy.Core.getUser().getEscapedJid()
            }).c("query", {
                xmlns: Strophe.NS.PRIVACY
            }).c("list", {
                name: "ignore"
            }).tree();
            var iqId = Candy.Core.getConnection().sendIQ(iq);
            // add handler (<#200 at https://github.com/candy-chat/candy/issues/200>)
            Candy.Core.addHandler(Candy.Core.Event.Jabber.PrivacyList, null, "iq", null, iqId);
        },
        /** Function: SetIgnoreListActive
		 * Set ignore privacy list active
		 */
        SetIgnoreListActive: function() {
            Candy.Core.getConnection().sendIQ($iq({
                type: "set",
                from: Candy.Core.getUser().getEscapedJid()
            }).c("query", {
                xmlns: Strophe.NS.PRIVACY
            }).c("active", {
                name: "ignore"
            }).tree());
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
                roomJid = Candy.Util.escapeJid(roomJid);
                var conn = Candy.Core.getConnection(), roomNick = roomJid + "/" + Candy.Core.getUser().getNick(), pres = $pres({
                    to: roomNick,
                    id: "pres:" + conn.getUniqueId()
                }).c("x", {
                    xmlns: Strophe.NS.MUC
                });
                if (password) {
                    pres.c("password").t(password);
                }
                pres.up().c("c", conn.caps.generateCapsAttrs());
                conn.send(pres.tree());
            },
            /** Function: Leave
			 * Leaves a room.
			 *
			 * Parameters:
			 *   (String) roomJid - Room to leave
			 */
            Leave: function(roomJid) {
                var user = Candy.Core.getRoom(roomJid).getUser();
                if (user) {
                    Candy.Core.getConnection().muc.leave(roomJid, user.getNick(), function() {});
                }
            },
            /** Function: Disco
			 * Requests <disco info of a room at http://xmpp.org/extensions/xep-0045.html#disco-roominfo>.
			 *
			 * Parameters:
			 *   (String) roomJid - Room to get info for
			 */
            Disco: function(roomJid) {
                Candy.Core.getConnection().sendIQ($iq({
                    type: "get",
                    from: Candy.Core.getUser().getEscapedJid(),
                    to: Candy.Util.escapeJid(roomJid)
                }).c("query", {
                    xmlns: Strophe.NS.DISCO_INFO
                }).tree());
            },
            /** Function: Message
			 * Send message
			 *
			 * Parameters:
			 *   (String) roomJid - Room to which send the message into
			 *   (String) msg - Message
			 *   (String) type - "groupchat" or "chat" ("chat" is for private messages)
			 *   (String) xhtmlMsg - XHTML formatted message [optional]
			 *
			 * Returns:
			 *   (Boolean) - true if message is not empty after trimming, false otherwise.
			 */
            Message: function(roomJid, msg, type, xhtmlMsg) {
                // Trim message
                msg = $.trim(msg);
                if (msg === "") {
                    return false;
                }
                var nick = null;
                if (type === "chat") {
                    nick = Strophe.getResourceFromJid(roomJid);
                    roomJid = Strophe.getBareJidFromJid(roomJid);
                }
                // muc takes care of the escaping now.
                Candy.Core.getConnection().muc.message(roomJid, nick, msg, xhtmlMsg, type);
                return true;
            },
            /** Function: Invite
			 * Sends an invite stanza to multiple JIDs
			 *
			 * Parameters:
			 *   (String) roomJid - Room to which send the message into
			 *   (Array)  invitees - Array of JIDs to be invited to the room
			 *   (String) reason - Message to include with the invitation [optional]
			 *   (String) password - Password for the MUC, if required [optional]
			 */
            Invite: function(roomJid, invitees, reason, password) {
                reason = $.trim(reason);
                var message = $msg({
                    to: roomJid
                });
                var x = message.c("x", {
                    xmlns: Strophe.NS.MUC_USER
                });
                $.each(invitees, function(i, invitee) {
                    invitee = Strophe.getBareJidFromJid(invitee);
                    x.c("invite", {
                        to: invitee
                    });
                    if (typeof reason !== "undefined" && reason !== "") {
                        x.c("reason", reason);
                    }
                });
                if (typeof password !== "undefined" && password !== "") {
                    x.c("password", password);
                }
                Candy.Core.getConnection().send(message);
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
                Candy.Core.getUser().addToOrRemoveFromPrivacyList("ignore", userJid);
                Candy.Core.Action.Jabber.Room.UpdatePrivacyList();
            },
            /** Function: UpdatePrivacyList
			 * Updates privacy list according to the privacylist in the currentUser
			 */
            UpdatePrivacyList: function() {
                var currentUser = Candy.Core.getUser(), iq = $iq({
                    type: "set",
                    from: currentUser.getEscapedJid()
                }).c("query", {
                    xmlns: "jabber:iq:privacy"
                }).c("list", {
                    name: "ignore"
                }), privacyList = currentUser.getPrivacyList("ignore");
                if (privacyList.length > 0) {
                    $.each(privacyList, function(index, jid) {
                        iq.c("item", {
                            type: "jid",
                            value: Candy.Util.escapeJid(jid),
                            action: "deny",
                            order: index
                        }).c("message").up().up();
                    });
                } else {
                    iq.c("item", {
                        action: "allow",
                        order: "0"
                    });
                }
                Candy.Core.getConnection().sendIQ(iq.tree());
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
                    roomJid = Candy.Util.escapeJid(roomJid);
                    userJid = Candy.Util.escapeJid(userJid);
                    var itemObj = {
                        nick: Strophe.getResourceFromJid(userJid)
                    };
                    switch (type) {
                      case "kick":
                        itemObj.role = "none";
                        break;

                      case "ban":
                        itemObj.affiliation = "outcast";
                        break;

                      default:
                        return false;
                    }
                    Candy.Core.getConnection().sendIQ($iq({
                        type: "set",
                        from: Candy.Core.getUser().getEscapedJid(),
                        to: roomJid
                    }).c("query", {
                        xmlns: Strophe.NS.MUC_ADMIN
                    }).c("item", itemObj).c("reason").t(reason).tree());
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
                    Candy.Core.getConnection().muc.setTopic(Candy.Util.escapeJid(roomJid), subject);
                }
            }
        }
    };
    return self;
}(Candy.Core.Action || {}, Strophe, jQuery);

/** File: chatRoom.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Strophe */
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
        name: Strophe.getNodeFromJid(roomJid)
    };
    /** Variable: user
	 * Current local user of this room.
	 */
    this.user = null;
    /** Variable: Roster
	 * Candy.Core.ChatRoster instance
	 */
    this.roster = new Candy.Core.ChatRoster();
};

/** Function: setUser
 * Set user of this room.
 *
 * Parameters:
 *   (Candy.Core.ChatUser) user - Chat user
 */
Candy.Core.ChatRoom.prototype.setUser = function(user) {
    this.user = user;
};

/** Function: getUser
 * Get current local user
 *
 * Returns:
 *   (Object) - Candy.Core.ChatUser instance or null
 */
Candy.Core.ChatRoom.prototype.getUser = function() {
    return this.user;
};

/** Function: getJid
 * Get room jid
 *
 * Returns:
 *   (String) - Room jid
 */
Candy.Core.ChatRoom.prototype.getJid = function() {
    return this.room.jid;
};

/** Function: setName
 * Set room name
 *
 * Parameters:
 *   (String) name - Room name
 */
Candy.Core.ChatRoom.prototype.setName = function(name) {
    this.room.name = name;
};

/** Function: getName
 * Get room name
 *
 * Returns:
 *   (String) - Room name
 */
Candy.Core.ChatRoom.prototype.getName = function() {
    return this.room.name;
};

/** Function: setRoster
 * Set roster of room
 *
 * Parameters:
 *   (Candy.Core.ChatRoster) roster - Chat roster
 */
Candy.Core.ChatRoom.prototype.setRoster = function(roster) {
    this.roster = roster;
};

/** Function: getRoster
 * Get roster
 *
 * Returns
 *   (Candy.Core.ChatRoster) - instance
 */
Candy.Core.ChatRoom.prototype.getRoster = function() {
    return this.roster;
};

/** File: chatRoster.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy */
/** Class: Candy.Core.ChatRoster
 * Chat Roster
 */
Candy.Core.ChatRoster = function() {
    /** Object: items
	 * Roster items
	 */
    this.items = {};
};

/** Function: add
 * Add user to roster
 *
 * Parameters:
 *   (Candy.Core.ChatUser) user - User to add
 */
Candy.Core.ChatRoster.prototype.add = function(user) {
    this.items[user.getJid()] = user;
};

/** Function: remove
 * Remove user from roster
 *
 * Parameters:
 *   (String) jid - User jid
 */
Candy.Core.ChatRoster.prototype.remove = function(jid) {
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
Candy.Core.ChatRoster.prototype.get = function(jid) {
    return this.items[jid];
};

/** Function: getAll
 * Get all items
 *
 * Returns:
 *   (Object) - all roster items
 */
Candy.Core.ChatRoster.prototype.getAll = function() {
    return this.items;
};

/** File: chatUser.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Strophe */
/** Class: Candy.Core.ChatUser
 * Chat User
 */
Candy.Core.ChatUser = function(jid, nick, affiliation, role, realJid) {
    /** Constant: ROLE_MODERATOR
	 * Moderator role
	 */
    this.ROLE_MODERATOR = "moderator";
    /** Constant: AFFILIATION_OWNER
	 * Affiliation owner
	 */
    this.AFFILIATION_OWNER = "owner";
    /** Object: data
	 * User data containing:
	 * - jid
	 * - realJid
	 * - nick
	 * - affiliation
	 * - role
	 * - privacyLists
	 * - customData to be used by e.g. plugins
	 */
    this.data = {
        jid: jid,
        realJid: realJid,
        nick: Strophe.unescapeNode(nick),
        affiliation: affiliation,
        role: role,
        privacyLists: {},
        customData: {},
        previousNick: undefined,
        status: "unavailable"
    };
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
Candy.Core.ChatUser.prototype.getJid = function() {
    if (this.data.jid) {
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
Candy.Core.ChatUser.prototype.getEscapedJid = function() {
    return Candy.Util.escapeJid(this.data.jid);
};

/** Function: setJid
 * Sets a user's jid
 *
 * Parameters:
 *   (String) jid - New Jid
 */
Candy.Core.ChatUser.prototype.setJid = function(jid) {
    this.data.jid = jid;
};

/** Function: getRealJid
 * Gets an unescaped real jid if known
 *
 * See:
 *   <Candy.Util.unescapeJid>
 *
 * Returns:
 *   (String) - realJid
 */
Candy.Core.ChatUser.prototype.getRealJid = function() {
    if (this.data.realJid) {
        return Candy.Util.unescapeJid(this.data.realJid);
    }
    return;
};

/** Function: getNick
 * Gets user nick
 *
 * Returns:
 *   (String) - nick
 */
Candy.Core.ChatUser.prototype.getNick = function() {
    return Strophe.unescapeNode(this.data.nick);
};

/** Function: setNick
 * Sets a user's nick
 *
 * Parameters:
 *   (String) nick - New nick
 */
Candy.Core.ChatUser.prototype.setNick = function(nick) {
    this.data.nick = nick;
};

/** Function: getName
 * Gets user's name (from contact or nick)
 *
 * Returns:
 *   (String) - name
 */
Candy.Core.ChatUser.prototype.getName = function() {
    var contact = this.getContact();
    if (contact) {
        return contact.getName();
    } else {
        return this.getNick();
    }
};

/** Function: getRole
 * Gets user role
 *
 * Returns:
 *   (String) - role
 */
Candy.Core.ChatUser.prototype.getRole = function() {
    return this.data.role;
};

/** Function: setRole
 * Sets user role
 *
 * Parameters:
 *   (String) role - Role
 */
Candy.Core.ChatUser.prototype.setRole = function(role) {
    this.data.role = role;
};

/** Function: setAffiliation
 * Sets user affiliation
 *
 * Parameters:
 *   (String) affiliation - new affiliation
 */
Candy.Core.ChatUser.prototype.setAffiliation = function(affiliation) {
    this.data.affiliation = affiliation;
};

/** Function: getAffiliation
 * Gets user affiliation
 *
 * Returns:
 *   (String) - affiliation
 */
Candy.Core.ChatUser.prototype.getAffiliation = function() {
    return this.data.affiliation;
};

/** Function: isModerator
 * Check if user is moderator. Depends on the room.
 *
 * Returns:
 *   (Boolean) - true if user has role moderator or affiliation owner
 */
Candy.Core.ChatUser.prototype.isModerator = function() {
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
Candy.Core.ChatUser.prototype.addToOrRemoveFromPrivacyList = function(list, jid) {
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
Candy.Core.ChatUser.prototype.getPrivacyList = function(list) {
    if (!this.data.privacyLists[list]) {
        this.data.privacyLists[list] = [];
    }
    return this.data.privacyLists[list];
};

/** Function: setPrivacyLists
 * Sets privacy lists.
 *
 * Parameters:
 *   (Object) lists - List object
 */
Candy.Core.ChatUser.prototype.setPrivacyLists = function(lists) {
    this.data.privacyLists = lists;
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
Candy.Core.ChatUser.prototype.isInPrivacyList = function(list, jid) {
    if (!this.data.privacyLists[list]) {
        return false;
    }
    return this.data.privacyLists[list].indexOf(jid) !== -1;
};

/** Function: setCustomData
 * Stores custom data
 *
 * Parameter:
 *   (Object) data - Object containing custom data
 */
Candy.Core.ChatUser.prototype.setCustomData = function(data) {
    this.data.customData = data;
};

/** Function: getCustomData
 * Retrieve custom data
 *
 * Returns:
 *   (Object) - Object containing custom data
 */
Candy.Core.ChatUser.prototype.getCustomData = function() {
    return this.data.customData;
};

/** Function: setPreviousNick
 * If user has nickname changed, set previous nickname.
 *
 * Parameters:
 *   (String) previousNick - the previous nickname
 */
Candy.Core.ChatUser.prototype.setPreviousNick = function(previousNick) {
    this.data.previousNick = previousNick;
};

/** Function: hasNicknameChanged
 * Gets the previous nickname if available.
 *
 * Returns:
 *   (String) - previous nickname
 */
Candy.Core.ChatUser.prototype.getPreviousNick = function() {
    return this.data.previousNick;
};

/** Function: getContact
 * Gets the contact matching this user from our roster
 *
 * Returns:
 *   (Candy.Core.Contact) - contact from roster
 */
Candy.Core.ChatUser.prototype.getContact = function() {
    return Candy.Core.getRoster().get(Strophe.getBareJidFromJid(this.data.realJid));
};

/** Function: setStatus
 * Set the user's status
 *
 * Parameters:
 *   (String) status - the new status
 */
Candy.Core.ChatUser.prototype.setStatus = function(status) {
    this.data.status = status;
};

/** Function: getStatus
 * Gets the user's status.
 *
 * Returns:
 *   (String) - status
 */
Candy.Core.ChatUser.prototype.getStatus = function() {
    return this.data.status;
};

/** File: contact.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Strophe, $ */
/** Class: Candy.Core.Contact
 * Roster contact
 */
Candy.Core.Contact = function(stropheRosterItem) {
    /** Object: data
   * Strophe Roster plugin item model containing:
   * - jid
   * - name
   * - subscription
   * - groups
   */
    this.data = stropheRosterItem;
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
Candy.Core.Contact.prototype.getJid = function() {
    if (this.data.jid) {
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
Candy.Core.Contact.prototype.getEscapedJid = function() {
    return Candy.Util.escapeJid(this.data.jid);
};

/** Function: getName
 * Gets user name
 *
 * Returns:
 *   (String) - name
 */
Candy.Core.Contact.prototype.getName = function() {
    if (!this.data.name) {
        return this.getJid();
    }
    return Strophe.unescapeNode(this.data.name);
};

/** Function: getNick
 * Gets user name
 *
 * Returns:
 *   (String) - name
 */
Candy.Core.Contact.prototype.getNick = Candy.Core.Contact.prototype.getName;

/** Function: getSubscription
 * Gets user subscription
 *
 * Returns:
 *   (String) - subscription
 */
Candy.Core.Contact.prototype.getSubscription = function() {
    if (!this.data.subscription) {
        return "none";
    }
    return this.data.subscription;
};

/** Function: getGroups
 * Gets user groups
 *
 * Returns:
 *   (Array) - groups
 */
Candy.Core.Contact.prototype.getGroups = function() {
    return this.data.groups;
};

/** Function: getStatus
 * Gets user status as an aggregate of all resources
 *
 * Returns:
 *   (String) - aggregate status, one of chat|dnd|available|away|xa|unavailable
 */
Candy.Core.Contact.prototype.getStatus = function() {
    var status = "unavailable", self = this, highestResourcePriority;
    $.each(this.data.resources, function(resource, obj) {
        var resourcePriority;
        if (obj.priority === undefined || obj.priority === "") {
            resourcePriority = 0;
        } else {
            resourcePriority = parseInt(obj.priority, 10);
        }
        if (obj.show === "" || obj.show === null || obj.show === undefined) {
            // TODO: Submit this as a bugfix to strophejs-plugins' roster plugin
            obj.show = "available";
        }
        if (highestResourcePriority === undefined || highestResourcePriority < resourcePriority) {
            // This resource is higher priority than the ones we've checked so far, override with this one
            status = obj.show;
            highestResourcePriority = resourcePriority;
        } else if (highestResourcePriority === resourcePriority) {
            // Two resources with the same priority means we have to weight their status
            if (self._weightForStatus(status) > self._weightForStatus(obj.show)) {
                status = obj.show;
            }
        }
    });
    return status;
};

Candy.Core.Contact.prototype._weightForStatus = function(status) {
    switch (status) {
      case "chat":
      case "dnd":
        return 1;

      case "available":
      case "":
        return 2;

      case "away":
        return 3;

      case "xa":
        return 4;

      case "unavailable":
        return 5;
    }
};

/** File: event.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Strophe, jQuery */
/** Class: Candy.Core.Event
 * Chat Events
 *
 * Parameters:
 *   (Candy.Core.Event) self - itself
 *   (Strophe) Strophe - Strophe
 *   (jQuery) $ - jQuery
 */
Candy.Core.Event = function(self, Strophe, $) {
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
        $(Candy).triggerHandler("candy:core.login", {
            presetJid: presetJid
        });
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
            switch (status) {
              case Strophe.Status.CONNECTED:
                Candy.Core.log("[Connection] Connected");
                Candy.Core.Action.Jabber.GetJidIfAnonymous();

              /* falls through */
                case Strophe.Status.ATTACHED:
                Candy.Core.log("[Connection] Attached");
                $(Candy).on("candy:core:roster:fetched", function() {
                    Candy.Core.Action.Jabber.Presence();
                });
                Candy.Core.Action.Jabber.Roster();
                Candy.Core.Action.Jabber.EnableCarbons();
                Candy.Core.Action.Jabber.Autojoin();
                Candy.Core.Action.Jabber.GetIgnoreList();
                break;

              case Strophe.Status.DISCONNECTED:
                Candy.Core.log("[Connection] Disconnected");
                break;

              case Strophe.Status.AUTHFAIL:
                Candy.Core.log("[Connection] Authentication failed");
                break;

              case Strophe.Status.CONNECTING:
                Candy.Core.log("[Connection] Connecting");
                break;

              case Strophe.Status.DISCONNECTING:
                Candy.Core.log("[Connection] Disconnecting");
                break;

              case Strophe.Status.AUTHENTICATING:
                Candy.Core.log("[Connection] Authenticating");
                break;

              case Strophe.Status.ERROR:
              case Strophe.Status.CONNFAIL:
                Candy.Core.log("[Connection] Failed (" + status + ")");
                break;

              default:
                Candy.Core.warn("[Connection] Unknown status received:", status);
                break;
            }
            /** Event: candy:core.chat.connection
			 * Connection status updates
			 *
			 * Parameters:
			 *   (Strophe.Status) status - Strophe status
			 */
            $(Candy).triggerHandler("candy:core.chat.connection", {
                status: status
            });
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
            Candy.Core.log("[Jabber] Version");
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
            Candy.Core.log("[Jabber] Presence");
            msg = $(msg);
            if (msg.children('x[xmlns^="' + Strophe.NS.MUC + '"]').length > 0) {
                if (msg.attr("type") === "error") {
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
                $(Candy).triggerHandler("candy:core.presence", {
                    from: msg.attr("from"),
                    stanza: msg
                });
            }
            return true;
        },
        /** Function: RosterLoad
		 * Acts on the result of loading roster items from a cache
		 *
		 * Parameters:
		 *   (String) items - List of roster items
		 *
 		 * Triggers:
		 *   candy:core.roster.loaded
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
        RosterLoad: function(items) {
            self.Jabber._addRosterItems(items);
            /** Event: candy:core.roster.loaded
			 * Notification of the roster having been loaded from cache
			 */
            $(Candy).triggerHandler("candy:core:roster:loaded", {
                roster: Candy.Core.getRoster()
            });
            return true;
        },
        /** Function: RosterFetch
		 * Acts on the result of a roster fetch
		 *
		 * Parameters:
		 *   (String) items - List of roster items
		 *
 		 * Triggers:
		 *   candy:core.roster.fetched
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
        RosterFetch: function(items) {
            self.Jabber._addRosterItems(items);
            /** Event: candy:core.roster.fetched
			 * Notification of the roster having been fetched
			 */
            $(Candy).triggerHandler("candy:core:roster:fetched", {
                roster: Candy.Core.getRoster()
            });
            return true;
        },
        /** Function: RosterPush
		 * Acts on a roster push
		 *
		 * Parameters:
		 *   (String) stanza - Raw XML Message
		 *
 		 * Triggers:
		 *   candy:core.roster.added
		 *   candy:core.roster.updated
		 *   candy:core.roster.removed
		 *
		 * Returns:
		 *   (Boolean) - true
		 */
        RosterPush: function(items, updatedItem) {
            if (!updatedItem) {
                return true;
            }
            if (updatedItem.subscription === "remove") {
                var contact = Candy.Core.getRoster().get(updatedItem.jid);
                Candy.Core.getRoster().remove(updatedItem.jid);
                /** Event: candy:core.roster.removed
				 * Notification of a roster entry having been removed
 				 *
				 * Parameters:
				 *   (Candy.Core.Contact) contact - The contact that was removed from the roster
				 */
                $(Candy).triggerHandler("candy:core:roster:removed", {
                    contact: contact
                });
            } else {
                var user = Candy.Core.getRoster().get(updatedItem.jid);
                if (!user) {
                    user = self.Jabber._addRosterItem(updatedItem);
                    /** Event: candy:core.roster.added
					 * Notification of a roster entry having been added
	 				 *
					 * Parameters:
					 *   (Candy.Core.Contact) contact - The contact that was added
					 */
                    $(Candy).triggerHandler("candy:core:roster:added", {
                        contact: user
                    });
                } else {
                    /** Event: candy:core.roster.updated
					 * Notification of a roster entry having been updated
	 				 *
					 * Parameters:
					 *   (Candy.Core.Contact) contact - The contact that was updated
					 */
                    $(Candy).triggerHandler("candy:core:roster:updated", {
                        contact: user
                    });
                }
            }
            return true;
        },
        _addRosterItem: function(item) {
            var user = new Candy.Core.Contact(item);
            Candy.Core.getRoster().add(user);
            return user;
        },
        _addRosterItems: function(items) {
            $.each(items, function(i, item) {
                self.Jabber._addRosterItem(item);
            });
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
            Candy.Core.log("[Jabber] Bookmarks");
            // Autojoin bookmarks
            $("conference", msg).each(function() {
                var item = $(this);
                if (item.attr("autojoin")) {
                    Candy.Core.Action.Jabber.Room.Join(item.attr("jid"));
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
            Candy.Core.log("[Jabber] PrivacyList");
            var currentUser = Candy.Core.getUser();
            msg = $(msg);
            if (msg.attr("type") === "result") {
                $('list[name="ignore"] item', msg).each(function() {
                    var item = $(this);
                    if (item.attr("action") === "deny") {
                        currentUser.addToOrRemoveFromPrivacyList("ignore", item.attr("value"));
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
            Candy.Core.log("[Jabber] PrivacyListError");
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
            Candy.Core.log("[Jabber] Message");
            msg = $(msg);
            var type = msg.attr("type") || "normal";
            switch (type) {
              case "normal":
                var invite = self.Jabber._findInvite(msg);
                if (invite) {
                    /** Event: candy:core:chat:invite
						 * Incoming chat invite for a MUC.
						 *
						 * Parameters:
						 *   (String) roomJid - The room the invite is to
						 *   (String) from - User JID that invite is from text
						 *   (String) reason - Reason for invite
						 *   (String) password - Password for the room
						 *   (String) continuedThread - The thread ID if this is a continuation of a 1-on-1 chat
						 */
                    $(Candy).triggerHandler("candy:core:chat:invite", invite);
                }
                /** Event: candy:core:chat:message:normal
					 * Messages with the type attribute of normal or those
					 * that do not have the optional type attribute.
					 *
					 * Parameters:
					 *   (String) type - Type of the message
					 *   (Object) message - Message object.
					 */
                $(Candy).triggerHandler("candy:core:chat:message:normal", {
                    type: type,
                    message: msg
                });
                break;

              case "headline":
                // Admin message
                if (!msg.attr("to")) {
                    /** Event: candy:core.chat.message.admin
						 * Admin message
						 *
						 * Parameters:
						 *   (String) type - Type of the message
						 *   (String) message - Message text
						 */
                    $(Candy).triggerHandler("candy:core.chat.message.admin", {
                        type: type,
                        message: msg.children("body").text()
                    });
                } else {
                    /** Event: candy:core.chat.message.server
						 * Server message (e.g. subject)
						 *
						 * Parameters:
						 *   (String) type - Message type
						 *   (String) subject - Subject text
						 *   (String) message - Message text
						 */
                    $(Candy).triggerHandler("candy:core.chat.message.server", {
                        type: type,
                        subject: msg.children("subject").text(),
                        message: msg.children("body").text()
                    });
                }
                break;

              case "groupchat":
              case "chat":
              case "error":
                // Room message
                self.Jabber.Room.Message(msg);
                break;

              default:
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
                $(Candy).triggerHandler("candy:core:chat:message:other", {
                    type: type,
                    message: msg
                });
            }
            return true;
        },
        _findInvite: function(msg) {
            var mediatedInvite = msg.find("invite"), directInvite = msg.find('x[xmlns="jabber:x:conference"]'), invite;
            if (mediatedInvite.length > 0) {
                var passwordNode = msg.find("password"), password, reasonNode = mediatedInvite.find("reason"), reason, continueNode = mediatedInvite.find("continue");
                if (passwordNode.text() !== "") {
                    password = passwordNode.text();
                }
                if (reasonNode.text() !== "") {
                    reason = reasonNode.text();
                }
                invite = {
                    roomJid: msg.attr("from"),
                    from: mediatedInvite.attr("from"),
                    reason: reason,
                    password: password,
                    continuedThread: continueNode.attr("thread")
                };
            }
            if (directInvite.length > 0) {
                invite = {
                    roomJid: directInvite.attr("jid"),
                    from: msg.attr("from"),
                    reason: directInvite.attr("reason"),
                    password: directInvite.attr("password"),
                    continuedThread: directInvite.attr("thread")
                };
            }
            return invite;
        },
        /** Class: Candy.Core.Event.Jabber.Room
		 * Room specific events
		 */
        Room: {
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
                Candy.Core.log("[Jabber:Room] Disco");
                msg = $(msg);
                // Temp fix for #219
                // Don't go further if it's no conference disco reply
                // FIXME: Do this in a more beautiful way
                if (!msg.find('identity[category="conference"]').length) {
                    return true;
                }
                var roomJid = Strophe.getBareJidFromJid(Candy.Util.unescapeJid(msg.attr("from")));
                // Client joined a room
                if (!Candy.Core.getRooms()[roomJid]) {
                    Candy.Core.getRooms()[roomJid] = new Candy.Core.ChatRoom(roomJid);
                }
                // Room existed but room name was unknown
                var identity = msg.find("identity");
                if (identity.length) {
                    var roomName = identity.attr("name"), room = Candy.Core.getRoom(roomJid);
                    if (room.getName() === null) {
                        room.setName(Strophe.unescapeNode(roomName));
                    }
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
                Candy.Core.log("[Jabber:Room] Presence");
                var from = Candy.Util.unescapeJid(msg.attr("from")), roomJid = Strophe.getBareJidFromJid(from), presenceType = msg.attr("type"), isNewRoom = self.Jabber.Room._msgHasStatusCode(msg, 201), nickAssign = self.Jabber.Room._msgHasStatusCode(msg, 210), nickChange = self.Jabber.Room._msgHasStatusCode(msg, 303);
                // Current User joined a room
                var room = Candy.Core.getRoom(roomJid);
                if (!room) {
                    Candy.Core.getRooms()[roomJid] = new Candy.Core.ChatRoom(roomJid);
                    room = Candy.Core.getRoom(roomJid);
                }
                var roster = room.getRoster(), currentUser = room.getUser() ? room.getUser() : Candy.Core.getUser(), action, user, nick, show = msg.find("show"), item = msg.find("item");
                // User joined a room
                if (presenceType !== "unavailable") {
                    if (roster.get(from)) {
                        // role/affiliation change
                        user = roster.get(from);
                        var role = item.attr("role"), affiliation = item.attr("affiliation");
                        user.setRole(role);
                        user.setAffiliation(affiliation);
                        user.setStatus("available");
                        // FIXME: currently role/affilation changes are handled with this action
                        action = "join";
                    } else {
                        nick = Strophe.getResourceFromJid(from);
                        user = new Candy.Core.ChatUser(from, nick, item.attr("affiliation"), item.attr("role"), item.attr("jid"));
                        // Room existed but client (myself) is not yet registered
                        if (room.getUser() === null && (Candy.Core.getUser().getNick() === nick || nickAssign)) {
                            room.setUser(user);
                            currentUser = user;
                        }
                        user.setStatus("available");
                        roster.add(user);
                        action = "join";
                    }
                    if (show.length > 0) {
                        user.setStatus(show.text());
                    }
                } else {
                    user = roster.get(from);
                    roster.remove(from);
                    if (nickChange) {
                        // user changed nick
                        nick = item.attr("nick");
                        action = "nickchange";
                        user.setPreviousNick(user.getNick());
                        user.setNick(nick);
                        user.setJid(Strophe.getBareJidFromJid(from) + "/" + nick);
                        roster.add(user);
                    } else {
                        action = "leave";
                        if (item.attr("role") === "none") {
                            if (self.Jabber.Room._msgHasStatusCode(msg, 307)) {
                                action = "kick";
                            } else if (self.Jabber.Room._msgHasStatusCode(msg, 301)) {
                                action = "ban";
                            }
                        }
                        if (Strophe.getResourceFromJid(from) === currentUser.getNick()) {
                            // Current User left a room
                            self.Jabber.Room._selfLeave(msg, from, roomJid, room.getName(), action);
                            return true;
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
				 *   (Boolean) isNewRoom - Whether the room is new (has just been created)
				 */
                $(Candy).triggerHandler("candy:core.presence.room", {
                    roomJid: roomJid,
                    roomName: room.getName(),
                    user: user,
                    action: action,
                    currentUser: currentUser,
                    isNewRoom: isNewRoom
                });
                return true;
            },
            _msgHasStatusCode: function(msg, code) {
                return msg.find('status[code="' + code + '"]').length > 0;
            },
            _selfLeave: function(msg, from, roomJid, roomName, action) {
                Candy.Core.log("[Jabber:Room] Leave");
                Candy.Core.removeRoom(roomJid);
                var item = msg.find("item"), reason, actor;
                if (action === "kick" || action === "ban") {
                    reason = item.find("reason").text();
                    actor = item.find("actor").attr("jid");
                }
                var user = new Candy.Core.ChatUser(from, Strophe.getResourceFromJid(from), item.attr("affiliation"), item.attr("role"));
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
                $(Candy).triggerHandler("candy:core.presence.leave", {
                    roomJid: roomJid,
                    roomName: roomName,
                    type: action,
                    reason: reason,
                    actor: actor,
                    user: user
                });
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
                Candy.Core.log("[Jabber:Room] Presence Error");
                var from = Candy.Util.unescapeJid(msg.attr("from")), roomJid = Strophe.getBareJidFromJid(from), room = Candy.Core.getRooms()[roomJid], roomName = room.getName();
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
                $(Candy).triggerHandler("candy:core.presence.error", {
                    msg: msg,
                    type: msg.children("error").children()[0].tagName.toLowerCase(),
                    roomJid: roomJid,
                    roomName: roomName
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
                Candy.Core.log("[Jabber:Room] Message");
                var carbon = false, partnerJid = Candy.Util.unescapeJid(msg.attr("from"));
                if (msg.children('sent[xmlns="' + Strophe.NS.CARBONS + '"]').length > 0) {
                    carbon = true;
                    msg = $(msg.children("sent").children("forwarded").children("message"));
                    partnerJid = Candy.Util.unescapeJid(msg.attr("to"));
                }
                if (msg.children('received[xmlns="' + Strophe.NS.CARBONS + '"]').length > 0) {
                    carbon = true;
                    msg = $(msg.children("received").children("forwarded").children("message"));
                    partnerJid = Candy.Util.unescapeJid(msg.attr("from"));
                }
                // Room subject
                var roomJid, roomName, from, message, name, room, sender;
                if (msg.children("subject").length > 0 && msg.children("subject").text().length > 0 && msg.attr("type") === "groupchat") {
                    roomJid = Candy.Util.unescapeJid(Strophe.getBareJidFromJid(partnerJid));
                    from = Candy.Util.unescapeJid(Strophe.getBareJidFromJid(msg.attr("from")));
                    roomName = Strophe.getNodeFromJid(roomJid);
                    message = {
                        from: from,
                        name: Strophe.getNodeFromJid(from),
                        body: msg.children("subject").text(),
                        type: "subject"
                    };
                } else if (msg.attr("type") === "error") {
                    var error = msg.children("error");
                    if (error.children("text").length > 0) {
                        roomJid = partnerJid;
                        roomName = Strophe.getNodeFromJid(roomJid);
                        message = {
                            from: msg.attr("from"),
                            type: "info",
                            body: error.children("text").text()
                        };
                    }
                } else if (msg.children("body").length > 0) {
                    // Private chat message
                    if (msg.attr("type") === "chat" || msg.attr("type") === "normal") {
                        from = Candy.Util.unescapeJid(msg.attr("from"));
                        var barePartner = Strophe.getBareJidFromJid(partnerJid), bareFrom = Strophe.getBareJidFromJid(from), isNoConferenceRoomJid = !Candy.Core.getRoom(barePartner);
                        if (isNoConferenceRoomJid) {
                            roomJid = barePartner;
                            var partner = Candy.Core.getRoster().get(barePartner);
                            if (partner) {
                                roomName = partner.getName();
                            } else {
                                roomName = Strophe.getNodeFromJid(barePartner);
                            }
                            if (bareFrom === Candy.Core.getUser().getJid()) {
                                sender = Candy.Core.getUser();
                            } else {
                                sender = Candy.Core.getRoster().get(bareFrom);
                            }
                            if (sender) {
                                name = sender.getName();
                            } else {
                                name = Strophe.getNodeFromJid(from);
                            }
                        } else {
                            roomJid = partnerJid;
                            room = Candy.Core.getRoom(Candy.Util.unescapeJid(Strophe.getBareJidFromJid(from)));
                            sender = room.getRoster().get(from);
                            if (sender) {
                                name = sender.getName();
                            } else {
                                name = Strophe.getResourceFromJid(from);
                            }
                            roomName = name;
                        }
                        message = {
                            from: from,
                            name: name,
                            body: msg.children("body").text(),
                            type: msg.attr("type"),
                            isNoConferenceRoomJid: isNoConferenceRoomJid
                        };
                    } else {
                        from = Candy.Util.unescapeJid(msg.attr("from"));
                        roomJid = Candy.Util.unescapeJid(Strophe.getBareJidFromJid(partnerJid));
                        var resource = Strophe.getResourceFromJid(partnerJid);
                        // Message from a user
                        if (resource) {
                            room = Candy.Core.getRoom(roomJid);
                            roomName = room.getName();
                            if (resource === Candy.Core.getUser().getNick()) {
                                sender = Candy.Core.getUser();
                            } else {
                                sender = room.getRoster().get(from);
                            }
                            if (sender) {
                                name = sender.getName();
                            } else {
                                name = Strophe.unescapeNode(resource);
                            }
                            message = {
                                from: roomJid,
                                name: name,
                                body: msg.children("body").text(),
                                type: msg.attr("type")
                            };
                        } else {
                            // we are not yet present in the room, let's just drop this message (issue #105)
                            if (!Candy.Core.getRooms()[partnerJid]) {
                                return true;
                            }
                            roomName = "";
                            message = {
                                from: roomJid,
                                name: "",
                                body: msg.children("body").text(),
                                type: "info"
                            };
                        }
                    }
                    var xhtmlChild = msg.children('html[xmlns="' + Strophe.NS.XHTML_IM + '"]');
                    if (xhtmlChild.length > 0) {
                        var xhtmlMessage = $($("<div>").append(xhtmlChild.children("body").first().contents()).html());
                        message.xhtmlMessage = xhtmlMessage;
                    }
                    self.Jabber.Room._checkForChatStateNotification(msg, roomJid, name);
                } else {
                    return true;
                }
                // besides the delayed delivery (XEP-0203), there exists also XEP-0091 which is the legacy delayed delivery.
                // the x[xmlns=jabber:x:delay] is the format in XEP-0091.
                var delay = msg.children('delay[xmlns="' + Strophe.NS.DELAY + '"]');
                message.delay = false;
                // Default delay to being false.
                if (delay.length < 1) {
                    // The jQuery xpath implementation doesn't support the or operator
                    delay = msg.children('x[xmlns="' + Strophe.NS.JABBER_DELAY + '"]');
                } else {
                    // Add delay to the message object so that we can more easily tell if it's a delayed message or not.
                    message.delay = true;
                }
                var timestamp = delay.length > 0 ? delay.attr("stamp") : new Date().toISOString();
                /** Event: candy:core.message
				 * Triggers on various message events (subject changed, private chat message, multi-user chat message).
				 *
				 * The resulting message object can contain different key-value pairs as stated in the documentation
				 * of the parameters itself.
				 *
				 * The following lists explain those parameters:
				 *
				 * Message Object Parameters:
				 *   (String) from - The unmodified JID that the stanza came from
				 *   (String) name - Sender name
				 *   (String) body - Message text
				 *   (String) type - Message type ([normal, chat, groupchat])
				 *                   or 'info' which is used internally for displaying informational messages
				 *   (Boolean) isNoConferenceRoomJid - if a 3rd-party client sends a direct message to
				 *                                     this user (not via the room) then the username is the node
				 *                                     and not the resource.
				 *                                     This flag tells if this is the case.
				 *   (Boolean) delay - If there is a value for the delay element on a message it is a delayed message.
				 *										 This flag tells if this is the case.
				 *
				 * Parameters:
				 *   (String) roomJid - Room jid. For one-on-one messages, this is sanitized to the bare JID for indexing purposes.
				 *   (String) roomName - Name of the contact
				 *   (Object) message - Depending on what kind of message, the object consists of different key-value pairs:
				 *                        - Room Subject: {name, body, type}
				 *                        - Error message: {type = 'info', body}
				 *                        - Private chat message: {name, body, type, isNoConferenceRoomJid}
				 *                        - MUC msg from a user: {name, body, type}
				 *                        - MUC msg from server: {name = '', body, type = 'info'}
				 *   (String) timestamp - Timestamp, only when it's an offline message
				 *   (Boolean) carbon - Indication of wether or not the message was a carbon
				 *   (String) stanza - The raw XML stanza
				 *
				 * TODO:
				 *   Streamline those events sent and rename the parameters.
				 */
                $(Candy).triggerHandler("candy:core.message", {
                    roomJid: roomJid,
                    roomName: roomName,
                    message: message,
                    timestamp: timestamp,
                    carbon: carbon,
                    stanza: msg
                });
                return true;
            },
            _checkForChatStateNotification: function(msg, roomJid, name) {
                var chatStateElements = msg.children('*[xmlns="http://jabber.org/protocol/chatstates"]');
                if (chatStateElements.length > 0) {
                    /** Event: candy:core:message:chatstate
					 * Triggers on any recieved chatstate notification.
					 *
					 * The resulting message object contains the name of the person, the roomJid, and the indicated chatstate.
					 *
					 * The following lists explain those parameters:
					 *
					 * Message Object Parameters:
					 *   (String) name - User name
					 *   (String) roomJid - Room jid
					 *   (String) chatstate - Chatstate being indicated. ("active", "composing", "paused", "inactive", "gone")
					 *
					 */
                    $(Candy).triggerHandler("candy:core:message:chatstate", {
                        name: name,
                        roomJid: roomJid,
                        chatstate: chatStateElements[0].tagName
                    });
                }
            }
        }
    };
    return self;
}(Candy.Core.Event || {}, Strophe, jQuery);

/** File: observer.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Strophe, Mustache, jQuery */
/** Class: Candy.View.Observer
 * Observes Candy core events
 *
 * Parameters:
 *   (Candy.View.Observer) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Observer = function(self, $) {
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
            var eventName = "candy:view.connection.status-" + args.status;
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
            if ($(Candy).triggerHandler(eventName) === false) {
                return false;
            }
            switch (args.status) {
              case Strophe.Status.CONNECTING:
              case Strophe.Status.AUTHENTICATING:
                Candy.View.Pane.Chat.Modal.show($.i18n._("statusConnecting"), false, true);
                break;

              case Strophe.Status.ATTACHED:
              case Strophe.Status.CONNECTED:
                if (_showConnectedMessageModal === true) {
                    // only show 'connected' if the autojoin error is not shown
                    // which is determined by having a visible modal in this stage.
                    Candy.View.Pane.Chat.Modal.show($.i18n._("statusConnected"));
                    Candy.View.Pane.Chat.Modal.hide();
                }
                break;

              case Strophe.Status.DISCONNECTING:
                Candy.View.Pane.Chat.Modal.show($.i18n._("statusDisconnecting"), false, true);
                break;

              case Strophe.Status.DISCONNECTED:
                var presetJid = Candy.Core.isAnonymousConnection() ? Strophe.getDomainFromJid(Candy.Core.getUser().getJid()) : null;
                Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._("statusDisconnected"), presetJid);
                break;

              case Strophe.Status.AUTHFAIL:
                Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._("statusAuthfail"));
                break;

              default:
                Candy.View.Pane.Chat.Modal.show($.i18n._("status", args.status));
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
            if (args.type === "message") {
                Candy.View.Pane.Chat.adminMessage(args.subject || "", args.message);
            } else if (args.type === "chat" || args.type === "groupchat") {
                // use onInfoMessage as infos from the server shouldn't be hidden by the infoMessage switch.
                Candy.View.Pane.Chat.onInfoMessage(Candy.View.getCurrent().roomJid, args.subject || "", args.message);
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
            if (args.type === "leave") {
                var user = Candy.View.Pane.Room.getUser(args.roomJid);
                Candy.View.Pane.Room.close(args.roomJid);
                self.Presence.notifyPrivateChats(user, args.type);
            } else if (args.type === "kick" || args.type === "ban") {
                var actorName = args.actor ? Strophe.getNodeFromJid(args.actor) : null, actionLabel, translationParams = [ args.roomName ];
                if (actorName) {
                    translationParams.push(actorName);
                }
                switch (args.type) {
                  case "kick":
                    actionLabel = $.i18n._(actorName ? "youHaveBeenKickedBy" : "youHaveBeenKicked", translationParams);
                    break;

                  case "ban":
                    actionLabel = $.i18n._(actorName ? "youHaveBeenBannedBy" : "youHaveBeenBanned", translationParams);
                    break;
                }
                Candy.View.Pane.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.adminMessageReason, {
                    reason: args.reason,
                    _action: actionLabel,
                    _reason: $.i18n._("reasonWas", [ args.reason ])
                }));
                setTimeout(function() {
                    Candy.View.Pane.Chat.Modal.hide(function() {
                        Candy.View.Pane.Room.close(args.roomJid);
                        self.Presence.notifyPrivateChats(args.user, args.type);
                    });
                }, 5e3);
                var evtData = {
                    type: args.type,
                    reason: args.reason,
                    roomJid: args.roomJid,
                    user: args.user
                };
                /** Event: candy:view.presence
				 * Presence update when kicked or banned
				 *
				 * Parameters:
				 *   (String) type - Presence type [kick, ban]
				 *   (String) reason - Reason for the kick|ban [optional]
				 *   (String) roomJid - Room JID
				 *   (Candy.Core.ChatUser) user - User which has been kicked or banned
				 */
                $(Candy).triggerHandler("candy:view.presence", [ evtData ]);
            } else if (args.roomJid) {
                args.roomJid = Candy.Util.unescapeJid(args.roomJid);
                // Initialize room if not yet existing
                if (!Candy.View.Pane.Chat.rooms[args.roomJid]) {
                    if (Candy.View.Pane.Room.init(args.roomJid, args.roomName) === false) {
                        return false;
                    }
                    Candy.View.Pane.Room.show(args.roomJid);
                }
                Candy.View.Pane.Roster.update(args.roomJid, args.user, args.action, args.currentUser);
                // Notify private user chats if existing, but not in case the action is nickchange
                // -- this is because the nickchange presence already contains the new
                // user jid
                if (Candy.View.Pane.Chat.rooms[args.user.getJid()] && args.action !== "nickchange") {
                    Candy.View.Pane.Roster.update(args.user.getJid(), args.user, args.action, args.currentUser);
                    Candy.View.Pane.PrivateRoom.setStatus(args.user.getJid(), args.action);
                }
            } else {
                // Presence for a one-on-one chat
                var bareJid = Strophe.getBareJidFromJid(args.from), room = Candy.View.Pane.Chat.rooms[bareJid];
                if (!room) {
                    return false;
                }
                room.targetJid = bareJid;
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
            Candy.Core.log("[View:Observer] notify Private Chats");
            var roomJid;
            for (roomJid in Candy.View.Pane.Chat.rooms) {
                if (Candy.View.Pane.Chat.rooms.hasOwnProperty(roomJid) && Candy.View.Pane.Room.getUser(roomJid) && user.getJid() === Candy.View.Pane.Room.getUser(roomJid).getJid()) {
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
        switch (args.type) {
          case "not-authorized":
            var message;
            if (args.msg.children("x").children("password").length > 0) {
                message = $.i18n._("passwordEnteredInvalid", [ args.roomName ]);
            }
            Candy.View.Pane.Chat.Modal.showEnterPasswordForm(args.roomJid, args.roomName, message);
            break;

          case "conflict":
            Candy.View.Pane.Chat.Modal.showNicknameConflictForm(args.roomJid);
            break;

          case "registration-required":
            Candy.View.Pane.Chat.Modal.showError("errorMembersOnly", [ args.roomName ]);
            break;

          case "service-unavailable":
            Candy.View.Pane.Chat.Modal.showError("errorMaxOccupantsReached", [ args.roomName ]);
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
        if (args.message.type === "subject") {
            if (!Candy.View.Pane.Chat.rooms[args.roomJid]) {
                Candy.View.Pane.Room.init(args.roomJid, args.roomName);
                Candy.View.Pane.Room.show(args.roomJid);
            }
            Candy.View.Pane.Room.setSubject(args.roomJid, args.message.body);
        } else if (args.message.type === "info") {
            Candy.View.Pane.Chat.infoMessage(args.roomJid, null, args.message.body);
        } else {
            // Initialize room if it's a message for a new private user chat
            if (args.message.type === "chat" && !Candy.View.Pane.Chat.rooms[args.roomJid]) {
                Candy.View.Pane.PrivateRoom.open(args.roomJid, args.roomName, false, args.message.isNoConferenceRoomJid);
            }
            var room = Candy.View.Pane.Chat.rooms[args.roomJid];
            if (room.targetJid === args.roomJid && !args.carbon) {
                // No messages yet received. Lock the room to this resource.
                room.targetJid = args.message.from;
            } else if (room.targetJid === args.message.from) {} else {
                // Message received from alternative resource. Release the resource lock.
                room.targetJid = args.roomJid;
            }
            Candy.View.Pane.Message.show(args.roomJid, args.message.name, args.message.body, args.message.xhtmlMessage, args.timestamp, args.message.from, args.carbon, args.stanza);
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
        Candy.View.Pane.Chat.Modal.showError("errorAutojoinMissing");
    };
    return self;
}(Candy.View.Observer || {}, jQuery);

/** File: chat.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, document, Mustache, Strophe, Audio, jQuery */
/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = function(self, $) {
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
            var roomId = Candy.Util.jidToId(roomJid);
            var evtData = {
                roomJid: roomJid,
                roomName: roomName,
                roomType: roomType,
                roomId: roomId
            };
            /** Event: candy:view.pane.before-tab
       * Before sending a message
       *
       * Parameters:
       *   (String) roomJid - JID of the room the tab is for.
       *   (String) roomName - Name of the room.
       *   (String) roomType - What type of room: `groupchat` or `chat`
       *
       * Returns:
       *   Boolean|undefined - If you want to handle displaying the tab on your own, return false.
       */
            if ($(Candy).triggerHandler("candy:view.pane.before-tab", evtData) === false) {
                event.preventDefault();
                return;
            }
            var html = Mustache.to_html(Candy.View.Template.Chat.tab, {
                roomJid: roomJid,
                roomId: roomId,
                name: roomName || Strophe.getNodeFromJid(roomJid),
                privateUserChat: function() {
                    return roomType === "chat";
                },
                roomType: roomType
            }), tab = $(html).appendTo("#chat-tabs");
            tab.click(self.Chat.tabClick);
            // TODO: maybe we find a better way to get the close element.
            $("a.close", tab).click(self.Chat.tabClose);
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
            return $("#chat-tabs").children('li[data-roomjid="' + roomJid + '"]');
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
            $("#chat-tabs").children().each(function() {
                var tab = $(this);
                if (tab.attr("data-roomjid") === roomJid) {
                    tab.addClass("active");
                } else {
                    tab.removeClass("active");
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
            var unreadElem = this.getTab(roomJid).find(".unread");
            unreadElem.show().text(unreadElem.text() !== "" ? parseInt(unreadElem.text(), 10) + 1 : 1);
            // only increase window unread messages in private chats
            if (self.Chat.rooms[roomJid].type === "chat" || Candy.View.getOptions().updateWindowOnAllMessages === true) {
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
            var unreadElem = self.Chat.getTab(roomJid).find(".unread");
            self.Window.reduceUnreadMessages(unreadElem.text());
            unreadElem.hide().text("");
        },
        /** Function: tabClick
     * Tab click event: show the room associated with the tab and stops the event from doing the default.
     */
        tabClick: function(e) {
            // remember scroll position of current room
            var currentRoomJid = Candy.View.getCurrent().roomJid;
            var roomPane = self.Room.getPane(currentRoomJid, ".message-pane");
            if (roomPane) {
                self.Chat.rooms[currentRoomJid].scrollPosition = roomPane.scrollTop();
            }
            self.Room.show($(this).attr("data-roomjid"));
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
            var roomJid = $(this).parent().attr("data-roomjid");
            // close private user tab
            if (self.Chat.rooms[roomJid].type === "chat") {
                self.Room.close(roomJid);
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
            if (Candy.Core.getOptions().disconnectWithoutTabs) {
                Candy.Core.disconnect();
                self.Chat.Toolbar.hide();
                self.Chat.hideMobileIcon();
                return;
            }
        },
        /** Function: fitTabs
     * Fit tab size according to window size
     */
        fitTabs: function() {
            var availableWidth = $("#chat-tabs").innerWidth(), tabsWidth = 0, tabs = $("#chat-tabs").children();
            tabs.each(function() {
                tabsWidth += $(this).css({
                    width: "auto",
                    overflow: "visible"
                }).outerWidth(true);
            });
            if (tabsWidth > availableWidth) {
                // tabs.[outer]Width() measures the first element in `tabs`. It's no very readable but nearly two times faster than using :first
                var tabDiffToRealWidth = tabs.outerWidth(true) - tabs.width(), tabWidth = Math.floor(availableWidth / tabs.length) - tabDiffToRealWidth;
                tabs.css({
                    width: tabWidth,
                    overflow: "hidden"
                });
            }
        },
        /** Function: hideMobileIcon
     * Hide mobile roster pane icon.
     */
        hideMobileIcon: function() {
            $("#mobile-roster-icon").hide();
        },
        /** Function: showMobileIcon
     * Show mobile roster pane icon.
     */
        showMobileIcon: function() {
            $("#mobile-roster-icon").show();
        },
        /** Function: clickMobileIcon
     * Add class to 'open' roster pane (on mobile).
     */
        clickMobileIcon: function(e) {
            if ($(".room-pane").is(".open")) {
                $(".room-pane").removeClass("open");
            } else {
                $(".room-pane").addClass("open");
            }
            e.preventDefault();
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
            if (Candy.View.getCurrent().roomJid) {
                // Simply dismiss admin message if no room joined so far. TODO: maybe we should show those messages on a dedicated pane?
                message = Candy.Util.Parser.all(message.substring(0, Candy.View.getOptions().crop.message.body));
                if (Candy.View.getOptions().enableXHTML === true) {
                    message = Candy.Util.parseAndCropXhtml(message, Candy.View.getOptions().crop.message.body);
                }
                var timestamp = new Date();
                var html = Mustache.to_html(Candy.View.Template.Chat.adminMessage, {
                    subject: subject,
                    message: message,
                    sender: $.i18n._("administratorMessageSubject"),
                    time: Candy.Util.localizedTime(timestamp),
                    timestamp: timestamp.toISOString()
                });
                $("#chat-rooms").children().each(function() {
                    self.Room.appendToMessagePane($(this).attr("data-roomjid"), html);
                });
                self.Room.scrollToBottom(Candy.View.getCurrent().roomJid);
                /** Event: candy:view.chat.admin-message
         * After admin message display
         *
         * Parameters:
         *   (String) presetJid - Preset user JID
         */
                $(Candy).triggerHandler("candy:view.chat.admin-message", {
                    subject: subject,
                    message: message
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
            message = message || "";
            if (Candy.View.getCurrent().roomJid && self.Chat.rooms[roomJid]) {
                // Simply dismiss info message if no room joined so far. TODO: maybe we should show those messages on a dedicated pane?
                if (Candy.View.getOptions().enableXHTML === true && message.length > 0) {
                    message = Candy.Util.parseAndCropXhtml(message, Candy.View.getOptions().crop.message.body);
                } else {
                    message = Candy.Util.Parser.all(message.substring(0, Candy.View.getOptions().crop.message.body));
                }
                var timestamp = new Date();
                var html = Mustache.to_html(Candy.View.Template.Chat.infoMessage, {
                    subject: subject,
                    message: $.i18n._(message),
                    time: Candy.Util.localizedTime(timestamp),
                    timestamp: timestamp.toISOString()
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
            _supportsNativeAudio: null,
            /** Function: init
       * Register handler and enable or disable sound and status messages.
       */
            init: function() {
                $("#emoticons-icon").click(function(e) {
                    self.Chat.Context.showEmoticonsMenu(e.currentTarget);
                    e.stopPropagation();
                });
                $("#chat-autoscroll-control").click(self.Chat.Toolbar.onAutoscrollControlClick);
                try {
                    if (!!document.createElement("audio").canPlayType) {
                        var a = document.createElement("audio");
                        if (!!a.canPlayType("audio/mpeg;").replace(/no/, "")) {
                            self.Chat.Toolbar._supportsNativeAudio = "mp3";
                        } else if (!!a.canPlayType('audio/ogg; codecs="vorbis"').replace(/no/, "")) {
                            self.Chat.Toolbar._supportsNativeAudio = "ogg";
                        } else if (!!a.canPlayType('audio/mp4; codecs="mp4a.40.2"').replace(/no/, "")) {
                            self.Chat.Toolbar._supportsNativeAudio = "m4a";
                        }
                    }
                } catch (e) {}
                $("#chat-sound-control").click(self.Chat.Toolbar.onSoundControlClick);
                if (Candy.Util.cookieExists("candy-nosound")) {
                    $("#chat-sound-control").click();
                }
                $("#chat-statusmessage-control").click(self.Chat.Toolbar.onStatusMessageControlClick);
                if (Candy.Util.cookieExists("candy-nostatusmessages")) {
                    $("#chat-statusmessage-control").click();
                }
                $(".box-shadow-icon").click(self.Chat.clickMobileIcon);
            },
            /** Function: show
       * Show toolbar.
       */
            show: function() {
                $("#chat-toolbar").show();
            },
            /** Function: hide
       * Hide toolbar.
       */
            hide: function() {
                $("#chat-toolbar").hide();
            },
            /* Function: update
       * Update toolbar for specific room
       */
            update: function(roomJid) {
                var context = $("#chat-toolbar").find(".context"), me = self.Room.getUser(roomJid);
                if (!me || !me.isModerator()) {
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
       * Sound play event handler. Uses native (HTML5) audio if supported,
       * otherwise it will attempt to use bgsound with autostart.
       *
       * Don't call this method directly. Call `playSound()` instead.
       * `playSound()` will only call this method if sound is enabled.
       */
            onPlaySound: function() {
                try {
                    if (self.Chat.Toolbar._supportsNativeAudio !== null) {
                        new Audio(Candy.View.getOptions().assets + "notify." + self.Chat.Toolbar._supportsNativeAudio).play();
                    } else {
                        $("#chat-sound-control bgsound").remove();
                        $("<bgsound/>").attr({
                            src: Candy.View.getOptions().assets + "notify.mp3",
                            loop: 1,
                            autostart: true
                        }).appendTo("#chat-sound-control");
                    }
                } catch (e) {}
            },
            /** Function: onSoundControlClick
       * Sound control click event handler.
       *
       * Toggle sound (overwrite `playSound()`) and handle cookies.
       */
            onSoundControlClick: function() {
                var control = $("#chat-sound-control");
                if (control.hasClass("checked")) {
                    self.Chat.Toolbar.playSound = function() {};
                    Candy.Util.setCookie("candy-nosound", "1", 365);
                } else {
                    self.Chat.Toolbar.playSound = function() {
                        self.Chat.Toolbar.onPlaySound();
                    };
                    Candy.Util.deleteCookie("candy-nosound");
                }
                control.toggleClass("checked");
            },
            /** Function: onAutoscrollControlClick
       * Autoscroll control event handler.
       *
       * Toggle autoscroll
       */
            onAutoscrollControlClick: function() {
                var control = $("#chat-autoscroll-control");
                if (control.hasClass("checked")) {
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
                control.toggleClass("checked");
            },
            /** Function: onStatusMessageControlClick
       * Status message control event handler.
       *
       * Toggle status message
       */
            onStatusMessageControlClick: function() {
                var control = $("#chat-statusmessage-control");
                if (control.hasClass("checked")) {
                    self.Chat.infoMessage = function() {};
                    Candy.Util.setCookie("candy-nostatusmessages", "1", 365);
                } else {
                    self.Chat.infoMessage = function(roomJid, subject, message) {
                        self.Chat.onInfoMessage(roomJid, subject, message);
                    };
                    Candy.Util.deleteCookie("candy-nostatusmessages");
                }
                control.toggleClass("checked");
            },
            /** Function: updateUserCount
       * Update usercount element with count.
       *
       * Parameters:
       *   (Integer) count - Current usercount
       */
            updateUsercount: function(count) {
                $("#chat-usercount").text(count);
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
       *   (String) modalClass - custom class (or space-separate classes) to attach to the modal
       */
            show: function(html, showCloseControl, showSpinner, modalClass) {
                if (showCloseControl) {
                    self.Chat.Modal.showCloseControl();
                } else {
                    self.Chat.Modal.hideCloseControl();
                }
                if (showSpinner) {
                    self.Chat.Modal.showSpinner();
                } else {
                    self.Chat.Modal.hideSpinner();
                }
                // Reset classes to 'modal-common' only in case .show() is called
                // with different arguments before .hide() can remove the last applied
                // custom class
                $("#chat-modal").removeClass().addClass("modal-common");
                if (modalClass) {
                    $("#chat-modal").addClass(modalClass);
                }
                $("#chat-modal").stop(false, true);
                $("#chat-modal-body").html(html);
                $("#chat-modal").fadeIn("fast");
                $("#chat-modal-overlay").show();
            },
            /** Function: hide
       * Hide modal window
       *
       * Parameters:
       *   (Function) callback - Calls the specified function after modal window has been hidden.
       */
            hide: function(callback) {
                // Reset classes to include only `modal-common`.
                $("#chat-modal").removeClass().addClass("modal-common");
                $("#chat-modal").fadeOut("fast", function() {
                    $("#chat-modal-body").text("");
                    $("#chat-modal-overlay").hide();
                });
                // restore initial esc handling
                $(document).keydown(function(e) {
                    if (e.which === 27) {
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
                $("#chat-modal-spinner").show();
            },
            /** Function: hideSpinner
       * Hide loading spinner
       */
            hideSpinner: function() {
                $("#chat-modal-spinner").hide();
            },
            /** Function: showCloseControl
       * Show a close button
       */
            showCloseControl: function() {
                $("#admin-message-cancel").show().click(function(e) {
                    self.Chat.Modal.hide();
                    // some strange behaviour on IE7 (and maybe other browsers) triggers onWindowUnload when clicking on the close button.
                    // prevent this.
                    e.preventDefault();
                });
                // enable esc to close modal
                $(document).keydown(function(e) {
                    if (e.which === 27) {
                        self.Chat.Modal.hide();
                        e.preventDefault();
                    }
                });
            },
            /** Function: hideCloseControl
       * Hide the close button
       */
            hideCloseControl: function() {
                $("#admin-message-cancel").hide().click(function() {});
            },
            /** Function: showLoginForm
       * Show the login form modal
       *
       * Parameters:
       *  (String) message - optional message to display above the form
       *  (String) presetJid - optional user jid. if set, the user will only be prompted for password.
       */
            showLoginForm: function(message, presetJid) {
                var domains = Candy.Core.getOptions().domains;
                var hideDomainList = Candy.Core.getOptions().hideDomainList;
                domains = domains ? domains.map(function(d) {
                    return {
                        domain: d
                    };
                }) : null;
                var customClass = domains && !hideDomainList ? "login-with-domains" : null;
                self.Chat.Modal.show((message ? message : "") + Mustache.to_html(Candy.View.Template.Login.form, {
                    _labelNickname: $.i18n._("labelNickname"),
                    _labelUsername: $.i18n._("labelUsername"),
                    domains: domains,
                    _labelPassword: $.i18n._("labelPassword"),
                    _loginSubmit: $.i18n._("loginSubmit"),
                    displayPassword: !Candy.Core.isAnonymousConnection(),
                    displayUsername: !presetJid,
                    displayDomain: domains ? true : false,
                    displayNickname: Candy.Core.isAnonymousConnection(),
                    presetJid: presetJid ? presetJid : false
                }), null, null, customClass);
                if (hideDomainList) {
                    $("#domain").hide();
                    $(".at-symbol").hide();
                }
                $("#login-form").children(":input:first").focus();
                // register submit handler
                $("#login-form").submit(function() {
                    var username = $("#username").val(), password = $("#password").val(), domain = $("#domain");
                    domain = domain.length ? domain.val().split(" ")[0] : null;
                    if (!Candy.Core.isAnonymousConnection()) {
                        var jid;
                        if (domain) {
                            // domain is stipulated
                            // Ensure there is no domain part in username
                            username = username.split("@")[0];
                            jid = username + "@" + domain;
                        } else {
                            // domain not stipulated
                            // guess the input and create a jid out of it
                            jid = Candy.Core.getUser() && username.indexOf("@") < 0 ? username + "@" + Strophe.getDomainFromJid(Candy.Core.getUser().getJid()) : username;
                        }
                        if (jid.indexOf("@") < 0 && !Candy.Core.getUser()) {
                            Candy.View.Pane.Chat.Modal.showLoginForm($.i18n._("loginInvalid"));
                        } else {
                            //Candy.View.Pane.Chat.Modal.hide();
                            Candy.Core.connect(jid, password);
                        }
                    } else {
                        // anonymous login
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
                    _labelPassword: $.i18n._("labelPassword"),
                    _label: message ? message : $.i18n._("enterRoomPassword", [ roomName ]),
                    _joinSubmit: $.i18n._("enterRoomPasswordSubmit")
                }), true);
                $("#password").focus();
                // register submit handler
                $("#enter-password-form").submit(function() {
                    var password = $("#password").val();
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
                    _labelNickname: $.i18n._("labelNickname"),
                    _label: $.i18n._("nicknameConflict"),
                    _loginSubmit: $.i18n._("loginSubmit")
                }));
                $("#nickname").focus();
                // register submit handler
                $("#nickname-conflict-form").submit(function() {
                    var nickname = $("#nickname").val();
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
                var tooltip = $("#tooltip"), target = $(event.currentTarget);
                if (!content) {
                    content = target.attr("data-tooltip");
                }
                if (tooltip.length === 0) {
                    var html = Mustache.to_html(Candy.View.Template.Chat.tooltip);
                    $("#chat-pane").append(html);
                    tooltip = $("#tooltip");
                }
                $("#context-menu").hide();
                tooltip.stop(false, true);
                tooltip.children("div").html(content);
                var pos = target.offset(), posLeft = Candy.Util.getPosLeftAccordingToWindowBounds(tooltip, pos.left), posTop = Candy.Util.getPosTopAccordingToWindowBounds(tooltip, pos.top);
                tooltip.css({
                    left: posLeft.px,
                    top: posTop.px
                }).removeClass("left-top left-bottom right-top right-bottom").addClass(posLeft.backgroundPositionAlignment + "-" + posTop.backgroundPositionAlignment).fadeIn("fast");
                target.mouseleave(function(event) {
                    event.stopPropagation();
                    $("#tooltip").stop(false, true).fadeOut("fast", function() {
                        $(this).css({
                            top: 0,
                            left: 0
                        });
                    });
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
                if ($("#context-menu").length === 0) {
                    var html = Mustache.to_html(Candy.View.Template.Chat.Context.menu);
                    $("#chat-pane").append(html);
                    $("#context-menu").mouseleave(function() {
                        $(this).fadeOut("fast");
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
                var roomId = self.Chat.rooms[roomJid].id, menu = $("#context-menu"), links = $("ul li", menu);
                $("#tooltip").hide();
                // add specific context-user class if a user is available (when context menu should be opened next to a user)
                if (!user) {
                    user = Candy.Core.getUser();
                }
                links.remove();
                var menulinks = this.getMenuLinks(roomJid, user, elem), id, clickHandler = function(roomJid, user) {
                    return function(event) {
                        event.data.callback(event, roomJid, user);
                        $("#context-menu").hide();
                    };
                };
                for (id in menulinks) {
                    if (menulinks.hasOwnProperty(id)) {
                        var link = menulinks[id], html = Mustache.to_html(Candy.View.Template.Chat.Context.menulinks, {
                            roomId: roomId,
                            "class": link["class"],
                            id: id,
                            label: link.label
                        });
                        $("ul", menu).append(html);
                        $("#context-menu-" + id).bind("click", link, clickHandler(roomJid, user));
                    }
                }
                // if `id` is set the menu is not empty
                if (id) {
                    var pos = elem.offset(), posLeft = Candy.Util.getPosLeftAccordingToWindowBounds(menu, pos.left), posTop = Candy.Util.getPosTopAccordingToWindowBounds(menu, pos.top);
                    menu.css({
                        left: posLeft.px,
                        top: posTop.px
                    }).removeClass("left-top left-bottom right-top right-bottom").addClass(posLeft.backgroundPositionAlignment + "-" + posTop.backgroundPositionAlignment).fadeIn("fast");
                    /** Event: candy:view.roster.after-context-menu
           * After context menu display
           *
           * Parameters:
           *   (String) roomJid - room where the context menu has been triggered
           *   (Candy.Core.ChatUser) user - User
           *   (jQuery.Element) element - Menu element
           */
                    $(Candy).triggerHandler("candy:view.roster.after-context-menu", {
                        roomJid: roomJid,
                        user: user,
                        element: menu
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
                    roomJid: roomJid,
                    user: user,
                    elem: elem,
                    menulinks: this.initialMenuLinks(elem)
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
                $(Candy).triggerHandler("candy:view.roster.context-menu", evtData);
                menulinks = evtData.menulinks;
                for (id in menulinks) {
                    if (menulinks.hasOwnProperty(id) && menulinks[id].requiredPermission !== undefined && !menulinks[id].requiredPermission(user, self.Room.getUser(roomJid), elem)) {
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
                    "private": {
                        requiredPermission: function(user, me) {
                            return me.getNick() !== user.getNick() && Candy.Core.getRoom(Candy.View.getCurrent().roomJid) && !Candy.Core.getUser().isInPrivacyList("ignore", user.getJid());
                        },
                        "class": "private",
                        label: $.i18n._("privateActionLabel"),
                        callback: function(e, roomJid, user) {
                            $("#user-" + Candy.Util.jidToId(roomJid) + "-" + Candy.Util.jidToId(user.getJid())).click();
                        }
                    },
                    ignore: {
                        requiredPermission: function(user, me) {
                            return me.getNick() !== user.getNick() && !Candy.Core.getUser().isInPrivacyList("ignore", user.getJid());
                        },
                        "class": "ignore",
                        label: $.i18n._("ignoreActionLabel"),
                        callback: function(e, roomJid, user) {
                            Candy.View.Pane.Room.ignoreUser(roomJid, user.getJid());
                        }
                    },
                    unignore: {
                        requiredPermission: function(user, me) {
                            return me.getNick() !== user.getNick() && Candy.Core.getUser().isInPrivacyList("ignore", user.getJid());
                        },
                        "class": "unignore",
                        label: $.i18n._("unignoreActionLabel"),
                        callback: function(e, roomJid, user) {
                            Candy.View.Pane.Room.unignoreUser(roomJid, user.getJid());
                        }
                    },
                    kick: {
                        requiredPermission: function(user, me) {
                            return me.getNick() !== user.getNick() && me.isModerator() && !user.isModerator();
                        },
                        "class": "kick",
                        label: $.i18n._("kickActionLabel"),
                        callback: function(e, roomJid, user) {
                            self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.contextModalForm, {
                                _label: $.i18n._("reason"),
                                _submit: $.i18n._("kickActionLabel")
                            }), true);
                            $("#context-modal-field").focus();
                            $("#context-modal-form").submit(function() {
                                Candy.Core.Action.Jabber.Room.Admin.UserAction(roomJid, user.getJid(), "kick", $("#context-modal-field").val());
                                self.Chat.Modal.hide();
                                return false;
                            });
                        }
                    },
                    ban: {
                        requiredPermission: function(user, me) {
                            return me.getNick() !== user.getNick() && me.isModerator() && !user.isModerator();
                        },
                        "class": "ban",
                        label: $.i18n._("banActionLabel"),
                        callback: function(e, roomJid, user) {
                            self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.contextModalForm, {
                                _label: $.i18n._("reason"),
                                _submit: $.i18n._("banActionLabel")
                            }), true);
                            $("#context-modal-field").focus();
                            $("#context-modal-form").submit(function() {
                                Candy.Core.Action.Jabber.Room.Admin.UserAction(roomJid, user.getJid(), "ban", $("#context-modal-field").val());
                                self.Chat.Modal.hide();
                                return false;
                            });
                        }
                    },
                    subject: {
                        requiredPermission: function(user, me) {
                            return me.getNick() === user.getNick() && me.isModerator();
                        },
                        "class": "subject",
                        label: $.i18n._("setSubjectActionLabel"),
                        callback: function(e, roomJid) {
                            self.Chat.Modal.show(Mustache.to_html(Candy.View.Template.Chat.Context.contextModalForm, {
                                _label: $.i18n._("subject"),
                                _submit: $.i18n._("setSubjectActionLabel")
                            }), true);
                            $("#context-modal-field").focus();
                            $("#context-modal-form").submit(function(e) {
                                Candy.Core.Action.Jabber.Room.Admin.SetSubject(roomJid, $("#context-modal-field").val());
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
                var pos = elem.offset(), menu = $("#context-menu"), content = $("ul", menu), emoticons = "", i;
                $("#tooltip").hide();
                for (i = Candy.Util.Parser.emoticons.length - 1; i >= 0; i--) {
                    emoticons = '<img src="' + Candy.Util.Parser._emoticonPath + Candy.Util.Parser.emoticons[i].image + '" alt="' + Candy.Util.Parser.emoticons[i].plain + '" />' + emoticons;
                }
                content.html('<li class="emoticons">' + emoticons + "</li>");
                content.find("img").click(function() {
                    var input = Candy.View.Pane.Room.getPane(Candy.View.getCurrent().roomJid, ".message-form").children(".field"), value = input.val(), emoticon = $(this).attr("alt") + " ";
                    input.val(value ? value + " " + emoticon : emoticon).focus();
                    // Once you make a selction, hide the menu.
                    menu.hide();
                });
                var posLeft = Candy.Util.getPosLeftAccordingToWindowBounds(menu, pos.left), posTop = Candy.Util.getPosTopAccordingToWindowBounds(menu, pos.top);
                menu.css({
                    left: posLeft.px,
                    top: posTop.px
                }).removeClass("left-top left-bottom right-top right-bottom").addClass(posLeft.backgroundPositionAlignment + "-" + posTop.backgroundPositionAlignment).fadeIn("fast");
                return true;
            }
        }
    };
    return self;
}(Candy.View.Pane || {}, jQuery);

/** File: message.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Mustache, jQuery */
/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = function(self, $) {
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
            var roomJid = Candy.View.getCurrent().roomJid, room = Candy.View.Pane.Chat.rooms[roomJid], roomType = room.type, targetJid = room.targetJid, message = $(this).children(".field").val().substring(0, Candy.View.getOptions().crop.message.body), xhtmlMessage, evtData = {
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
            if ($(Candy).triggerHandler("candy:view.message.before-send", evtData) === false) {
                event.preventDefault();
                return;
            }
            message = evtData.message;
            xhtmlMessage = evtData.xhtmlMessage;
            Candy.Core.Action.Jabber.Room.Message(targetJid, message, roomType, xhtmlMessage);
            // Private user chat. Jabber won't notify the user who has sent the message. Just show it as the user hits the button...
            if (roomType === "chat" && message) {
                self.Message.show(roomJid, self.Room.getUser(roomJid).getNick(), message, xhtmlMessage, undefined, Candy.Core.getUser().getJid());
            }
            // Clear input and set focus to it
            $(this).children(".field").val("").focus();
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
     *   (Boolean) carbon - [optional] Indication of wether or not the message was a carbon
     *
     * Triggers:
     *   candy:view.message.before-show using {roomJid, name, message}
     *   candy.view.message.before-render using {template, templateData}
     *   candy:view.message.after-show using {roomJid, name, message, element}
     */
        show: function(roomJid, name, message, xhtmlMessage, timestamp, from, carbon, stanza) {
            message = Candy.Util.Parser.all(message.substring(0, Candy.View.getOptions().crop.message.body));
            if (Candy.View.getOptions().enableXHTML === true && xhtmlMessage) {
                xhtmlMessage = Candy.Util.parseAndCropXhtml(xhtmlMessage, Candy.View.getOptions().crop.message.body);
            }
            timestamp = timestamp || new Date();
            // Assume we have an ISO-8601 date string and convert it to a Date object
            if (!timestamp.toDateString) {
                timestamp = Candy.Util.iso8601toDate(timestamp);
            }
            // Before we add the new message, check to see if we should be automatically scrolling or not.
            var messagePane = self.Room.getPane(roomJid, ".message-pane");
            var enableScroll = messagePane.scrollTop() + messagePane.outerHeight() === messagePane.prop("scrollHeight") || !$(messagePane).is(":visible");
            Candy.View.Pane.Chat.rooms[roomJid].enableScroll = enableScroll;
            var evtData = {
                roomJid: roomJid,
                name: name,
                message: message,
                xhtmlMessage: xhtmlMessage,
                from: from,
                stanza: stanza
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
            if ($(Candy).triggerHandler("candy:view.message.before-show", evtData) === false) {
                return;
            }
            message = evtData.message;
            xhtmlMessage = evtData.xhtmlMessage;
            if (xhtmlMessage !== undefined && xhtmlMessage.length > 0) {
                message = xhtmlMessage;
            }
            if (!message) {
                return;
            }
            var renderEvtData = {
                template: Candy.View.Template.Message.item,
                templateData: {
                    name: name,
                    displayName: Candy.Util.crop(name, Candy.View.getOptions().crop.message.nickname),
                    message: message,
                    time: Candy.Util.localizedTime(timestamp),
                    timestamp: timestamp.toISOString(),
                    roomjid: roomJid,
                    from: from
                },
                stanza: stanza
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
       *                           - (String) time - Localized time of message
       *                           - (String) timestamp - ISO formatted timestamp of message
       */
            $(Candy).triggerHandler("candy:view.message.before-render", renderEvtData);
            var html = Mustache.to_html(renderEvtData.template, renderEvtData.templateData);
            self.Room.appendToMessagePane(roomJid, html);
            var elem = self.Room.getPane(roomJid, ".message-pane").children().last();
            // click on username opens private chat
            elem.find("a.label").click(function(event) {
                event.preventDefault();
                // Check if user is online and not myself
                var room = Candy.Core.getRoom(roomJid);
                if (room && name !== self.Room.getUser(Candy.View.getCurrent().roomJid).getNick() && room.getRoster().get(roomJid + "/" + name)) {
                    if (Candy.View.Pane.PrivateRoom.open(roomJid + "/" + name, name, true) === false) {
                        return false;
                    }
                }
            });
            if (!carbon) {
                var notifyEvtData = {
                    name: name,
                    displayName: Candy.Util.crop(name, Candy.View.getOptions().crop.message.nickname),
                    roomJid: roomJid,
                    message: message,
                    time: Candy.Util.localizedTime(timestamp),
                    timestamp: timestamp.toISOString()
                };
                /** Event: candy:view.message.notify
         * Notify the user (optionally) that a new message has been received
         *
         * Parameters:
         *   (Object) templateData - Template data consists of:
         *                           - (String) name - Name of the sending user
         *                           - (String) displayName - Cropped name of the sending user
         *                           - (String) roomJid - JID into which the message was sent
         *                           - (String) message - Message text
         *                           - (String) time - Localized time of message
         *                           - (String) timestamp - ISO formatted timestamp of message
         *                           - (Boolean) carbon - Indication of wether or not the message was a carbon
         */
                $(Candy).triggerHandler("candy:view.message.notify", notifyEvtData);
                // Check to see if in-core notifications are disabled
                if (!Candy.Core.getOptions().disableCoreNotifications) {
                    if (Candy.View.getCurrent().roomJid !== roomJid || !self.Window.hasFocus()) {
                        self.Chat.increaseUnreadMessages(roomJid);
                        if (!self.Window.hasFocus()) {
                            // Notify the user about a new private message OR on all messages if configured
                            if (Candy.View.Pane.Chat.rooms[roomJid].type === "chat" || Candy.View.getOptions().updateWindowOnAllMessages === true) {
                                self.Chat.Toolbar.playSound();
                            }
                        }
                    }
                }
                if (Candy.View.getCurrent().roomJid === roomJid) {
                    self.Room.scrollToBottom(roomJid);
                }
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
            $(Candy).triggerHandler("candy:view.message.after-show", evtData);
        }
    };
    return self;
}(Candy.View.Pane || {}, jQuery);

/** File: privateRoom.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Strophe, jQuery */
/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = function(self, $) {
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
     *                    then the username is the node and not the resource. This param addresses this case.
     *
     * Triggers:
     *   candy:view.private-room.after-open using {roomJid, type, element}
     */
        open: function(roomJid, roomName, switchToRoom, isNoConferenceRoomJid) {
            var user = isNoConferenceRoomJid ? Candy.Core.getUser() : self.Room.getUser(Strophe.getBareJidFromJid(roomJid)), evtData = {
                roomJid: roomJid,
                roomName: roomName,
                type: "chat"
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
            if ($(Candy).triggerHandler("candy:view.private-room.before-open", evtData) === false) {
                return false;
            }
            // if target user is in privacy list, don't open the private chat.
            if (Candy.Core.getUser().isInPrivacyList("ignore", roomJid)) {
                return false;
            }
            if (!self.Chat.rooms[roomJid]) {
                if (self.Room.init(roomJid, roomName, "chat") === false) {
                    return false;
                }
            }
            if (switchToRoom) {
                self.Room.show(roomJid);
            }
            self.Roster.update(roomJid, new Candy.Core.ChatUser(roomJid, roomName), "join", user);
            self.Roster.update(roomJid, user, "join", user);
            self.PrivateRoom.setStatus(roomJid, "join");
            evtData.element = self.Room.getPane(roomJid);
            /** Event: candy:view.private-room.after-open
       * After opening a new private room
       *
       * Parameters:
       *   (String) roomJid - Room JID
       *   (String) type - 'chat'
       *   (jQuery.Element) element - User element
       */
            $(Candy).triggerHandler("candy:view.private-room.after-open", evtData);
        },
        /** Function: setStatus
     * Set offline or online status for private rooms (when one of the participants leaves the room)
     *
     * Parameters:
     *   (String) roomJid - Private room jid
     *   (String) status - "leave"/"join"
     */
        setStatus: function(roomJid, status) {
            var messageForm = self.Room.getPane(roomJid, ".message-form");
            if (status === "join") {
                self.Chat.getTab(roomJid).addClass("online").removeClass("offline");
                messageForm.children(".field").removeAttr("disabled");
                messageForm.children(".submit").removeAttr("disabled");
                self.Chat.getTab(roomJid);
            } else if (status === "leave") {
                self.Chat.getTab(roomJid).addClass("offline").removeClass("online");
                messageForm.children(".field").attr("disabled", true);
                messageForm.children(".submit").attr("disabled", true);
            }
        },
        /** Function: changeNick
     * Changes the nick for every private room opened with this roomJid.
     *
     * Parameters:
     *   (String) roomJid - Public room jid
     *   (Candy.Core.ChatUser) user - User which changes his nick
     */
        changeNick: function(roomJid, user) {
            Candy.Core.log("[View:Pane:PrivateRoom] changeNick");
            var previousPrivateRoomJid = roomJid + "/" + user.getPreviousNick(), newPrivateRoomJid = roomJid + "/" + user.getNick(), previousPrivateRoomId = Candy.Util.jidToId(previousPrivateRoomJid), newPrivateRoomId = Candy.Util.jidToId(newPrivateRoomJid), room = self.Chat.rooms[previousPrivateRoomJid], roomElement, roomTabElement;
            // it could happen that the new private room is already existing -> close it first.
            // if this is not done, errors appear as two rooms would have the same id
            if (self.Chat.rooms[newPrivateRoomJid]) {
                self.Room.close(newPrivateRoomJid);
            }
            if (room) {
                /* someone I talk with, changed nick */
                room.name = user.getNick();
                room.id = newPrivateRoomId;
                self.Chat.rooms[newPrivateRoomJid] = room;
                delete self.Chat.rooms[previousPrivateRoomJid];
                roomElement = $("#chat-room-" + previousPrivateRoomId);
                if (roomElement) {
                    roomElement.attr("data-roomjid", newPrivateRoomJid);
                    roomElement.attr("id", "chat-room-" + newPrivateRoomId);
                    roomTabElement = $('#chat-tabs li[data-roomjid="' + previousPrivateRoomJid + '"]');
                    roomTabElement.attr("data-roomjid", newPrivateRoomJid);
                    /* TODO: The '@' is defined in the template. Somehow we should
           * extract both things into our CSS or do something else to prevent that.
           */
                    roomTabElement.children("a.label").text("@" + user.getNick());
                    if (Candy.View.getCurrent().roomJid === previousPrivateRoomJid) {
                        Candy.View.getCurrent().roomJid = newPrivateRoomJid;
                    }
                }
            } else {
                /* I changed the nick */
                roomElement = $('.room-pane.roomtype-chat[data-userjid="' + previousPrivateRoomJid + '"]');
                if (roomElement.length) {
                    previousPrivateRoomId = Candy.Util.jidToId(roomElement.attr("data-roomjid"));
                    roomElement.attr("data-userjid", newPrivateRoomJid);
                }
            }
            if (roomElement && roomElement.length) {
                self.Roster.changeNick(previousPrivateRoomId, user);
            }
        }
    };
    return self;
}(Candy.View.Pane || {}, jQuery);

/** File: room.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Mustache, Strophe, jQuery */
/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = function(self, $) {
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
            roomType = roomType || "groupchat";
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
            if ($(Candy).triggerHandler("candy:view.room.before-add", evtData) === false) {
                return false;
            }
            // First room, show sound control
            if (Candy.Util.isEmptyObject(self.Chat.rooms)) {
                self.Chat.Toolbar.show();
                self.Chat.showMobileIcon();
            }
            var roomId = Candy.Util.jidToId(roomJid);
            self.Chat.rooms[roomJid] = {
                id: roomId,
                usercount: 0,
                name: roomName,
                type: roomType,
                messageCount: 0,
                scrollPosition: -1,
                targetJid: roomJid
            };
            $("#chat-rooms").append(Mustache.to_html(Candy.View.Template.Room.pane, {
                roomId: roomId,
                roomJid: roomJid,
                roomType: roomType,
                form: {
                    _messageSubmit: $.i18n._("messageSubmit")
                },
                roster: {
                    _userOnline: $.i18n._("userOnline")
                }
            }, {
                roster: Candy.View.Template.Roster.pane,
                messages: Candy.View.Template.Message.pane,
                form: Candy.View.Template.Room.form
            }));
            self.Chat.addTab(roomJid, roomName, roomType);
            self.Room.getPane(roomJid, ".message-form").submit(self.Message.submit);
            self.Room.scrollToBottom(roomJid);
            evtData.element = self.Room.getPane(roomJid);
            /** Event: candy:view.room.after-add
       * After initialising a room
       *
       * Parameters:
       *   (String) roomJid - Room JID
       *   (String) type - Room Type
       *   (jQuery.Element) element - Room element
       */
            $(Candy).triggerHandler("candy:view.room.after-add", evtData);
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
            var roomId = self.Chat.rooms[roomJid].id, evtData;
            $(".room-pane").each(function() {
                var elem = $(this);
                evtData = {
                    roomJid: elem.attr("data-roomjid"),
                    type: elem.attr("data-roomtype"),
                    element: elem
                };
                if (elem.attr("id") === "chat-room-" + roomId) {
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
           *   (String) type - Room Type
           *   (jQuery.Element) element - Room element
           */
                    $(Candy).triggerHandler("candy:view.room.after-show", evtData);
                } else {
                    elem.hide();
                    /** Event: candy:view.room.after-hide
           * After hiding a room
           *
           * Parameters:
           *   (String) roomJid - Room JID
           *   (String) type - Room Type
           *   (jQuery.Element) element - Room element
           */
                    $(Candy).triggerHandler("candy:view.room.after-hide", evtData);
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
            var timestamp = new Date();
            var html = Mustache.to_html(Candy.View.Template.Room.subject, {
                subject: subject,
                roomName: self.Chat.rooms[roomJid].name,
                _roomSubject: $.i18n._("roomSubject"),
                time: Candy.Util.localizedTime(timestamp),
                timestamp: timestamp.toISOString()
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
            $(Candy).triggerHandler("candy:view.room.after-subject-change", {
                roomJid: roomJid,
                element: self.Room.getPane(roomJid),
                subject: subject
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
            var openRooms = $("#chat-rooms").children();
            if (Candy.View.getCurrent().roomJid === roomJid) {
                Candy.View.getCurrent().roomJid = null;
                if (openRooms.length === 0) {
                    self.Chat.allTabsClosed();
                } else {
                    self.Room.show(openRooms.last().attr("data-roomjid"));
                }
            }
            delete self.Chat.rooms[roomJid];
            /** Event: candy:view.room.after-close
       * After closing a room
       *
       * Parameters:
       *   (String) roomJid - Room JID
       */
            $(Candy).triggerHandler("candy:view.room.after-close", {
                roomJid: roomJid
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
            self.Room.getPane(roomJid, ".message-pane").append(html);
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
            if (self.Window.autoscroll) {
                var options = Candy.View.getOptions().messages;
                if (self.Chat.rooms[roomJid].messageCount > options.limit) {
                    self.Room.getPane(roomJid, ".message-pane").children().slice(0, options.remove).remove();
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
            var messagePane = self.Room.getPane(roomJid, ".message-pane-wrapper");
            if (Candy.View.Pane.Chat.rooms[roomJid].enableScroll === true) {
                messagePane.scrollTop(messagePane.prop("scrollHeight"));
            } else {
                return false;
            }
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
            if (self.Chat.rooms[roomJid].scrollPosition > -1) {
                var messagePane = self.Room.getPane(roomJid, ".message-pane-wrapper");
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
            // If we're on mobile, don't focus the input field.
            if (Candy.Util.isMobile()) {
                return true;
            }
            var pane = self.Room.getPane(roomJid, ".message-form");
            if (pane) {
                // IE8 will fail maybe, because the field isn't there yet.
                try {
                    pane.children(".field")[0].focus();
                } catch (e) {}
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
            var roomPane = self.Room.getPane(roomJid), chatPane = $("#chat-pane");
            roomPane.attr("data-userjid", user.getJid());
            // Set classes based on user role / affiliation
            if (user.isModerator()) {
                if (user.getRole() === user.ROLE_MODERATOR) {
                    chatPane.addClass("role-moderator");
                }
                if (user.getAffiliation() === user.AFFILIATION_OWNER) {
                    chatPane.addClass("affiliation-owner");
                }
            } else {
                chatPane.removeClass("role-moderator affiliation-owner");
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
                $("#user-" + Candy.View.Pane.Chat.rooms[userJid].id + "-" + Candy.Util.jidToId(userJid)).addClass("status-ignored");
            }
            if (Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)]) {
                $("#user-" + Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)].id + "-" + Candy.Util.jidToId(userJid)).addClass("status-ignored");
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
                $("#user-" + Candy.View.Pane.Chat.rooms[userJid].id + "-" + Candy.Util.jidToId(userJid)).removeClass("status-ignored");
            }
            if (Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)]) {
                $("#user-" + Candy.View.Pane.Chat.rooms[Strophe.getBareJidFromJid(roomJid)].id + "-" + Candy.Util.jidToId(userJid)).removeClass("status-ignored");
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
                if (subPane) {
                    if (self.Chat.rooms[roomJid]["pane-" + subPane]) {
                        return self.Chat.rooms[roomJid]["pane-" + subPane];
                    } else {
                        self.Chat.rooms[roomJid]["pane-" + subPane] = $("#chat-room-" + self.Chat.rooms[roomJid].id).find(subPane);
                        return self.Chat.rooms[roomJid]["pane-" + subPane];
                    }
                } else {
                    return $("#chat-room-" + self.Chat.rooms[roomJid].id);
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
                var roomElement = $("#chat-room-" + roomId);
                roomElement.attr("data-userjid", Strophe.getBareJidFromJid(roomElement.attr("data-userjid")) + "/" + user.getNick());
            }
        }
    };
    return self;
}(Candy.View.Pane || {}, jQuery);

/** File: roster.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, Mustache, Strophe, jQuery */
/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = function(self, $) {
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
            Candy.Core.log("[View:Pane:Roster] " + action);
            var roomId = self.Chat.rooms[roomJid].id, userId = Candy.Util.jidToId(user.getJid()), usercountDiff = -1, userElem = $("#user-" + roomId + "-" + userId), evtData = {
                roomJid: roomJid,
                user: user,
                action: action,
                element: userElem
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
            $(Candy).triggerHandler("candy:view.roster.before-update", evtData);
            // a user joined the room
            if (action === "join") {
                usercountDiff = 1;
                if (userElem.length < 1) {
                    self.Roster._insertUser(roomJid, roomId, user, userId, currentUser);
                    self.Roster.showJoinAnimation(user, userId, roomId, roomJid, currentUser);
                } else {
                    usercountDiff = 0;
                    userElem.remove();
                    self.Roster._insertUser(roomJid, roomId, user, userId, currentUser);
                    // it's me, update the toolbar
                    if (currentUser !== undefined && user.getNick() === currentUser.getNick() && self.Room.getUser(roomJid)) {
                        self.Chat.Toolbar.update(roomJid);
                    }
                }
                // Presence of client
                if (currentUser !== undefined && currentUser.getNick() === user.getNick()) {
                    self.Room.setUser(roomJid, user);
                } else {
                    $("#user-" + roomId + "-" + userId).click(self.Roster.userClick);
                }
                $("#user-" + roomId + "-" + userId + " .context").click(function(e) {
                    self.Chat.Context.show(e.currentTarget, roomJid, user);
                    e.stopPropagation();
                });
                // check if current user is ignoring the user who has joined.
                if (currentUser !== undefined && currentUser.isInPrivacyList("ignore", user.getJid())) {
                    Candy.View.Pane.Room.addIgnoreIcon(roomJid, user.getJid());
                }
            } else if (action === "leave") {
                self.Roster.leaveAnimation("user-" + roomId + "-" + userId);
                // always show leave message in private room, even if status messages have been disabled
                if (self.Chat.rooms[roomJid].type === "chat") {
                    self.Chat.onInfoMessage(roomJid, null, $.i18n._("userLeftRoom", [ user.getNick() ]));
                } else {
                    self.Chat.infoMessage(roomJid, null, $.i18n._("userLeftRoom", [ user.getNick() ]), "");
                }
            } else if (action === "nickchange") {
                usercountDiff = 0;
                self.Roster.changeNick(roomId, user);
                self.Room.changeDataUserJidIfUserIsMe(roomId, user);
                self.PrivateRoom.changeNick(roomJid, user);
                var infoMessage = $.i18n._("userChangedNick", [ user.getPreviousNick(), user.getNick() ]);
                self.Chat.infoMessage(roomJid, null, infoMessage);
            } else if (action === "kick") {
                self.Roster.leaveAnimation("user-" + roomId + "-" + userId);
                self.Chat.onInfoMessage(roomJid, null, $.i18n._("userHasBeenKickedFromRoom", [ user.getNick() ]));
            } else if (action === "ban") {
                self.Roster.leaveAnimation("user-" + roomId + "-" + userId);
                self.Chat.onInfoMessage(roomJid, null, $.i18n._("userHasBeenBannedFromRoom", [ user.getNick() ]));
            }
            // Update user count
            Candy.View.Pane.Chat.rooms[roomJid].usercount += usercountDiff;
            if (roomJid === Candy.View.getCurrent().roomJid) {
                Candy.View.Pane.Chat.Toolbar.updateUsercount(Candy.View.Pane.Chat.rooms[roomJid].usercount);
            }
            // in case there's been a join, the element is now there (previously not)
            evtData.element = $("#user-" + roomId + "-" + userId);
            /** Event: candy:view.roster.after-update
       * After updating a room's roster
       *
       * Parameters:
       *   (String) roomJid - Room JID
       *   (Candy.Core.ChatUser) user - User
       *   (String) action - [join, leave, kick, ban]
       *   (jQuery.Element) element - User element
       */
            $(Candy).triggerHandler("candy:view.roster.after-update", evtData);
        },
        _insertUser: function(roomJid, roomId, user, userId, currentUser) {
            var contact = user.getContact();
            var html = Mustache.to_html(Candy.View.Template.Roster.user, {
                roomId: roomId,
                userId: userId,
                userJid: user.getJid(),
                realJid: user.getRealJid(),
                status: user.getStatus(),
                contact_status: contact ? contact.getStatus() : "unavailable",
                nick: user.getNick(),
                displayNick: Candy.Util.crop(user.getNick(), Candy.View.getOptions().crop.roster.nickname),
                role: user.getRole(),
                affiliation: user.getAffiliation(),
                me: currentUser !== undefined && user.getNick() === currentUser.getNick(),
                tooltipRole: $.i18n._("tooltipRole"),
                tooltipIgnored: $.i18n._("tooltipIgnored")
            });
            var userInserted = false, rosterPane = self.Room.getPane(roomJid, ".roster-pane");
            // there are already users in the roster
            if (rosterPane.children().length > 0) {
                // insert alphabetically, sorted by status
                var userSortCompare = self.Roster._userSortCompare(user.getNick(), user.getStatus());
                rosterPane.children().each(function() {
                    var elem = $(this);
                    if (self.Roster._userSortCompare(elem.attr("data-nick"), elem.attr("data-status")) > userSortCompare) {
                        elem.before(html);
                        userInserted = true;
                        return false;
                    }
                    return true;
                });
            }
            // first user in roster
            if (!userInserted) {
                rosterPane.append(html);
            }
        },
        _userSortCompare: function(nick, status) {
            var statusWeight;
            switch (status) {
              case "available":
                statusWeight = 1;
                break;

              case "unavailable":
                statusWeight = 9;
                break;

              default:
                statusWeight = 8;
            }
            return statusWeight + nick.toUpperCase();
        },
        /** Function: userClick
     * Click handler for opening a private room
     */
        userClick: function() {
            var elem = $(this), realJid = elem.attr("data-real-jid"), useRealJid = Candy.Core.getOptions().useParticipantRealJid && (realJid !== undefined && realJid !== null && realJid !== ""), targetJid = useRealJid && realJid ? Strophe.getBareJidFromJid(realJid) : elem.attr("data-jid");
            self.PrivateRoom.open(targetJid, elem.attr("data-nick"), true, useRealJid);
        },
        /** Function: showJoinAnimation
     * Shows join animation if needed
     *
     * FIXME: Refactor. Part of this will be done by the big room improvements
     */
        showJoinAnimation: function(user, userId, roomId, roomJid, currentUser) {
            // don't show if the user has recently changed the nickname.
            var rosterUserId = "user-" + roomId + "-" + userId, $rosterUserElem = $("#" + rosterUserId);
            if (!user.getPreviousNick() || !$rosterUserElem || $rosterUserElem.is(":visible") === false) {
                self.Roster.joinAnimation(rosterUserId);
                // only show other users joining & don't show if there's no message in the room.
                if (currentUser !== undefined && user.getNick() !== currentUser.getNick() && self.Room.getUser(roomJid)) {
                    // always show join message in private room, even if status messages have been disabled
                    if (self.Chat.rooms[roomJid].type === "chat") {
                        self.Chat.onInfoMessage(roomJid, null, $.i18n._("userJoinedRoom", [ user.getNick() ]));
                    } else {
                        self.Chat.infoMessage(roomJid, null, $.i18n._("userJoinedRoom", [ user.getNick() ]));
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
            $("#" + elementId).stop(true).slideDown("normal", function() {
                $(this).animate({
                    opacity: 1
                });
            });
        },
        /** Function: leaveAnimation
     * Leave animation for specified element id and removes the DOM element on completion.
     *
     * Parameters:
     *   (String) elementId - Specific element to do the animation on
     */
        leaveAnimation: function(elementId) {
            $("#" + elementId).stop(true).attr("id", "#" + elementId + "-leaving").animate({
                opacity: 0
            }, {
                complete: function() {
                    $(this).slideUp("normal", function() {
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
            Candy.Core.log("[View:Pane:Roster] changeNick");
            var previousUserJid = Strophe.getBareJidFromJid(user.getJid()) + "/" + user.getPreviousNick(), elementId = "user-" + roomId + "-" + Candy.Util.jidToId(previousUserJid), el = $("#" + elementId);
            el.attr("data-nick", user.getNick());
            el.attr("data-jid", user.getJid());
            el.children("div.label").text(user.getNick());
            el.attr("id", "user-" + roomId + "-" + Candy.Util.jidToId(user.getJid()));
        }
    };
    return self;
}(Candy.View.Pane || {}, jQuery);

/** File: window.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy, jQuery, window */
/** Class: Candy.View.Pane
 * Candy view pane handles everything regarding DOM updates etc.
 *
 * Parameters:
 *   (Candy.View.Pane) self - itself
 *   (jQuery) $ - jQuery
 */
Candy.View.Pane = function(self) {
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
        _plainTitle: window.top.document.title,
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
            if (self.Window._unreadMessagesCount <= 0) {
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
            window.top.document.title = self.Window._plainTitle;
        },
        /** Function: renderUnreadMessages
     * Update window title to show message count.
     *
     * Parameters:
     *   (Integer) count - Number of unread messages to show in window title
     */
        renderUnreadMessages: function(count) {
            window.top.document.title = Candy.View.Template.Window.unreadmessages.replace("{{count}}", count).replace("{{title}}", self.Window._plainTitle);
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
    return self;
}(Candy.View.Pane || {}, jQuery);

/** File: template.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy */
/** Class: Candy.View.Template
 * Contains mustache.js templates
 */
Candy.View.Template = function(self) {
    self.Window = {
        /**
		 * Unread messages - used to extend the window title
		 */
        unreadmessages: "({{count}}) {{title}}"
    };
    self.Chat = {
        pane: '<div id="chat-pane">{{> tabs}}{{> mobile}}{{> toolbar}}{{> rooms}}</div>{{> modal}}',
        rooms: '<div id="chat-rooms" class="rooms"></div>',
        tabs: '<ul id="chat-tabs"></ul>',
        mobileIcon: '<div id="mobile-roster-icon"><a class="box-shadow-icon"></a></div>',
        tab: '<li class="roomtype-{{roomType}}" data-roomjid="{{roomJid}}" data-roomtype="{{roomType}}">' + '<a href="#" class="label">{{#privateUserChat}}@{{/privateUserChat}}{{name}}</a>' + '<a href="#" class="transition"></a><a href="#" class="close">×</a>' + '<small class="unread"></small></li>',
        modal: '<div id="chat-modal"><a id="admin-message-cancel" class="close" href="#">×</a>' + '<span id="chat-modal-body"></span>' + '<img src="{{assetsPath}}img/modal-spinner.gif" id="chat-modal-spinner" />' + '</div><div id="chat-modal-overlay"></div>',
        adminMessage: '<li><small data-timestamp="{{timestamp}}">{{time}}</small><div class="adminmessage">' + '<span class="label">{{sender}}</span>' + '<span class="spacer">▸</span>{{subject}} {{{message}}}</div></li>',
        infoMessage: '<li><small data-timestamp="{{timestamp}}">{{time}}</small><div class="infomessage">' + '<span class="spacer">•</span>{{subject}} {{{message}}}</div></li>',
        toolbar: '<ul id="chat-toolbar">' + '<li id="emoticons-icon" data-tooltip="{{tooltipEmoticons}}"></li>' + '<li id="chat-sound-control" class="checked" data-tooltip="{{tooltipSound}}"></li>' + '<li id="chat-autoscroll-control" class="checked" data-tooltip="{{tooltipAutoscroll}}"></li>' + '<li class="checked" id="chat-statusmessage-control" data-tooltip="{{tooltipStatusmessage}}">' + '</li><li class="context" data-tooltip="{{tooltipAdministration}}"></li>' + '<li class="usercount" data-tooltip="{{tooltipUsercount}}">' + '<span id="chat-usercount"></span></li></ul>',
        Context: {
            menu: '<div id="context-menu"><i class="arrow arrow-top"></i>' + '<ul></ul><i class="arrow arrow-bottom"></i></div>',
            menulinks: '<li class="{{class}}" id="context-menu-{{id}}">{{label}}</li>',
            contextModalForm: '<form action="#" id="context-modal-form">' + '<label for="context-modal-label">{{_label}}</label>' + '<input type="text" name="contextModalField" id="context-modal-field" />' + '<input type="submit" class="button" name="send" value="{{_submit}}" /></form>',
            adminMessageReason: '<a id="admin-message-cancel" class="close" href="#">×</a>' + "<p>{{_action}}</p>{{#reason}}<p>{{_reason}}</p>{{/reason}}"
        },
        tooltip: '<div id="tooltip"><i class="arrow arrow-top"></i>' + '<div></div><i class="arrow arrow-bottom"></i></div>'
    };
    self.Room = {
        pane: '<div class="room-pane roomtype-{{roomType}}" id="chat-room-{{roomId}}" data-roomjid="{{roomJid}}" data-roomtype="{{roomType}}">' + "{{> roster}}{{> messages}}{{> form}}</div>",
        subject: '<li><small data-timestamp="{{timestamp}}">{{time}}</small><div class="subject">' + '<span class="label">{{roomName}}</span>' + '<span class="spacer">▸</span>{{_roomSubject}} {{{subject}}}</div></li>',
        form: '<div class="message-form-wrapper">' + '<form method="post" class="message-form">' + '<input name="message" class="field" type="text" aria-label="Message Form Text Field" autocomplete="off" maxlength="1000" />' + '<input type="submit" class="submit" name="submit" value="{{_messageSubmit}}" /></form></div>'
    };
    self.Roster = {
        pane: '<div class="roster-pane"></div>',
        user: '<div class="user role-{{role}} affiliation-{{affiliation}}{{#me}} me{{/me}}"' + ' id="user-{{roomId}}-{{userId}}" data-jid="{{userJid}}" data-real-jid="{{realJid}}"' + ' data-nick="{{nick}}" data-role="{{role}}" data-affiliation="{{affiliation}}" data-status="{{status}}">' + '<div class="label">{{displayNick}}</div><ul>' + '<li class="context" id="context-{{roomId}}-{{userId}}">&#x25BE;</li>' + '<li class="role role-{{role}} affiliation-{{affiliation}}" data-tooltip="{{tooltipRole}}"></li>' + '<li class="ignore" data-tooltip="{{tooltipIgnored}}"></li></ul></div>'
    };
    self.Message = {
        pane: '<div class="message-pane-wrapper"><ul class="message-pane"></ul></div>',
        item: '<li><small data-timestamp="{{timestamp}}">{{time}}</small><div>' + '<a class="label" href="#" class="name">{{displayName}}</a>' + '<span class="spacer">▸</span>{{{message}}}</div></li>'
    };
    self.Login = {
        form: '<form method="post" id="login-form" class="login-form">' + '{{#displayNickname}}<label for="username">{{_labelNickname}}</label><input type="text" id="username" name="username"/>{{/displayNickname}}' + '{{#displayUsername}}<label for="username">{{_labelUsername}}</label>' + '<input type="text" id="username" name="username"/>' + '{{#displayDomain}} <span class="at-symbol">@</span> ' + '<select id="domain" name="domain">{{#domains}}<option value="{{domain}}">{{domain}}</option>{{/domains}}</select>' + "{{/displayDomain}}" + "{{/displayUsername}}" + '{{#presetJid}}<input type="hidden" id="username" name="username" value="{{presetJid}}"/>{{/presetJid}}' + '{{#displayPassword}}<label for="password">{{_labelPassword}}</label>' + '<input type="password" id="password" name="password" />{{/displayPassword}}' + '<input type="submit" class="button" value="{{_loginSubmit}}" /></form>'
    };
    self.PresenceError = {
        enterPasswordForm: "<strong>{{_label}}</strong>" + '<form method="post" id="enter-password-form" class="enter-password-form">' + '<label for="password">{{_labelPassword}}</label><input type="password" id="password" name="password" />' + '<input type="submit" class="button" value="{{_joinSubmit}}" /></form>',
        nicknameConflictForm: "<strong>{{_label}}</strong>" + '<form method="post" id="nickname-conflict-form" class="nickname-conflict-form">' + '<label for="nickname">{{_labelNickname}}</label><input type="text" id="nickname" name="nickname" />' + '<input type="submit" class="button" value="{{_loginSubmit}}" /></form>',
        displayError: "<strong>{{_error}}</strong>"
    };
    return self;
}(Candy.View.Template || {});

/** File: translation.js
 * Candy - Chats are not dead yet.
 *
 * Legal: See the LICENSE file at the top-level directory of this distribution and at https://github.com/candy-chat/candy/blob/master/LICENSE
 */
"use strict";

/* global Candy */
/** Class: Candy.View.Translation
 * Contains translations
 */
Candy.View.Translation = {
    en: {
        status: "Status: %s",
        statusConnecting: "Connecting...",
        statusConnected: "Connected",
        statusDisconnecting: "Disconnecting...",
        statusDisconnected: "Disconnected",
        statusAuthfail: "Authentication failed",
        roomSubject: "Subject:",
        messageSubmit: "Send",
        labelUsername: "Username:",
        labelNickname: "Nickname:",
        labelPassword: "Password:",
        loginSubmit: "Login",
        loginInvalid: "Invalid JID",
        reason: "Reason:",
        subject: "Subject:",
        reasonWas: "Reason was: %s.",
        kickActionLabel: "Kick",
        youHaveBeenKickedBy: "You have been kicked from %2$s by %1$s",
        youHaveBeenKicked: "You have been kicked from %s",
        banActionLabel: "Ban",
        youHaveBeenBannedBy: "You have been banned from %1$s by %2$s",
        youHaveBeenBanned: "You have been banned from %s",
        privateActionLabel: "Private chat",
        ignoreActionLabel: "Ignore",
        unignoreActionLabel: "Unignore",
        setSubjectActionLabel: "Change Subject",
        administratorMessageSubject: "Administrator",
        userJoinedRoom: "%s joined the room.",
        userLeftRoom: "%s left the room.",
        userHasBeenKickedFromRoom: "%s has been kicked from the room.",
        userHasBeenBannedFromRoom: "%s has been banned from the room.",
        userChangedNick: "%1$s is now known as %2$s.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderator",
        tooltipIgnored: "You ignore this user",
        tooltipEmoticons: "Emoticons",
        tooltipSound: "Play sound for new private messages",
        tooltipAutoscroll: "Autoscroll",
        tooltipStatusmessage: "Display status messages",
        tooltipAdministration: "Room Administration",
        tooltipUsercount: "Room Occupants",
        enterRoomPassword: 'Room "%s" is password protected.',
        enterRoomPasswordSubmit: "Join room",
        passwordEnteredInvalid: 'Invalid password for room "%s".',
        nicknameConflict: "Username already in use. Please choose another one.",
        errorMembersOnly: 'You can\'t join room "%s": Insufficient rights.',
        errorMaxOccupantsReached: 'You can\'t join room "%s": Too many occupants.',
        errorAutojoinMissing: "No autojoin parameter set in configuration. Please set one to continue.",
        antiSpamMessage: "Please do not spam. You have been blocked for a short-time."
    },
    de: {
        status: "Status: %s",
        statusConnecting: "Verbinden...",
        statusConnected: "Verbunden",
        statusDisconnecting: "Verbindung trennen...",
        statusDisconnected: "Verbindung getrennt",
        statusAuthfail: "Authentifizierung fehlgeschlagen",
        roomSubject: "Thema:",
        messageSubmit: "Senden",
        labelUsername: "Benutzername:",
        labelNickname: "Spitzname:",
        labelPassword: "Passwort:",
        loginSubmit: "Anmelden",
        loginInvalid: "Ungültige JID",
        reason: "Begründung:",
        subject: "Titel:",
        reasonWas: "Begründung: %s.",
        kickActionLabel: "Kick",
        youHaveBeenKickedBy: "Du wurdest soeben aus dem Raum %1$s gekickt (%2$s)",
        youHaveBeenKicked: "Du wurdest soeben aus dem Raum %s gekickt",
        banActionLabel: "Ban",
        youHaveBeenBannedBy: "Du wurdest soeben aus dem Raum %1$s verbannt (%2$s)",
        youHaveBeenBanned: "Du wurdest soeben aus dem Raum %s verbannt",
        privateActionLabel: "Privater Chat",
        ignoreActionLabel: "Ignorieren",
        unignoreActionLabel: "Nicht mehr ignorieren",
        setSubjectActionLabel: "Thema ändern",
        administratorMessageSubject: "Administrator",
        userJoinedRoom: "%s hat soeben den Raum betreten.",
        userLeftRoom: "%s hat soeben den Raum verlassen.",
        userHasBeenKickedFromRoom: "%s ist aus dem Raum gekickt worden.",
        userHasBeenBannedFromRoom: "%s ist aus dem Raum verbannt worden.",
        userChangedNick: "%1$s hat den Nicknamen zu %2$s geändert.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderator",
        tooltipIgnored: "Du ignorierst diesen Benutzer",
        tooltipEmoticons: "Smileys",
        tooltipSound: "Ton abspielen bei neuen privaten Nachrichten",
        tooltipAutoscroll: "Autoscroll",
        tooltipStatusmessage: "Statusnachrichten anzeigen",
        tooltipAdministration: "Raum Administration",
        tooltipUsercount: "Anzahl Benutzer im Raum",
        enterRoomPassword: 'Raum "%s" ist durch ein Passwort geschützt.',
        enterRoomPasswordSubmit: "Raum betreten",
        passwordEnteredInvalid: 'Inkorrektes Passwort für Raum "%s".',
        nicknameConflict: "Der Benutzername wird bereits verwendet. Bitte wähle einen anderen.",
        errorMembersOnly: 'Du kannst den Raum "%s" nicht betreten: Ungenügende Rechte.',
        errorMaxOccupantsReached: 'Du kannst den Raum "%s" nicht betreten: Benutzerlimit erreicht.',
        errorAutojoinMissing: 'Keine "autojoin" Konfiguration gefunden. Bitte setze eine konfiguration um fortzufahren.',
        antiSpamMessage: "Bitte nicht spammen. Du wurdest für eine kurze Zeit blockiert."
    },
    fr: {
        status: "Status&thinsp;: %s",
        statusConnecting: "Connexion…",
        statusConnected: "Connecté",
        statusDisconnecting: "Déconnexion…",
        statusDisconnected: "Déconnecté",
        statusAuthfail: "L’identification a échoué",
        roomSubject: "Sujet&thinsp;:",
        messageSubmit: "Envoyer",
        labelUsername: "Nom d’utilisateur&thinsp;:",
        labelNickname: "Pseudo&thinsp;:",
        labelPassword: "Mot de passe&thinsp;:",
        loginSubmit: "Connexion",
        loginInvalid: "JID invalide",
        reason: "Motif&thinsp;:",
        subject: "Titre&thinsp;:",
        reasonWas: "Motif&thinsp;: %s.",
        kickActionLabel: "Kick",
        youHaveBeenKickedBy: "Vous avez été expulsé du salon %1$s (%2$s)",
        youHaveBeenKicked: "Vous avez été expulsé du salon %s",
        banActionLabel: "Ban",
        youHaveBeenBannedBy: "Vous avez été banni du salon %1$s (%2$s)",
        youHaveBeenBanned: "Vous avez été banni du salon %s",
        privateActionLabel: "Chat privé",
        ignoreActionLabel: "Ignorer",
        unignoreActionLabel: "Ne plus ignorer",
        setSubjectActionLabel: "Changer le sujet",
        administratorMessageSubject: "Administrateur",
        userJoinedRoom: "%s vient d’entrer dans le salon.",
        userLeftRoom: "%s vient de quitter le salon.",
        userHasBeenKickedFromRoom: "%s a été expulsé du salon.",
        userHasBeenBannedFromRoom: "%s a été banni du salon.",
        dateFormat: "dd/mm/yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Modérateur",
        tooltipIgnored: "Vous ignorez cette personne",
        tooltipEmoticons: "Smileys",
        tooltipSound: "Jouer un son lors de la réception de messages privés",
        tooltipAutoscroll: "Défilement automatique",
        tooltipStatusmessage: "Afficher les changements d’état",
        tooltipAdministration: "Administration du salon",
        tooltipUsercount: "Nombre d’utilisateurs dans le salon",
        enterRoomPassword: "Le salon %s est protégé par un mot de passe.",
        enterRoomPasswordSubmit: "Entrer dans le salon",
        passwordEnteredInvalid: "Le mot de passe pour le salon %s est invalide.",
        nicknameConflict: "Ce nom d’utilisateur est déjà utilisé. Veuillez en choisir un autre.",
        errorMembersOnly: "Vous ne pouvez pas entrer dans le salon %s&thinsp;: droits insuffisants.",
        errorMaxOccupantsReached: "Vous ne pouvez pas entrer dans le salon %s&thinsp;: limite d’utilisateurs atteinte.",
        antiSpamMessage: "Merci de ne pas spammer. Vous avez été bloqué pendant une courte période."
    },
    nl: {
        status: "Status: %s",
        statusConnecting: "Verbinding maken...",
        statusConnected: "Verbinding is gereed",
        statusDisconnecting: "Verbinding verbreken...",
        statusDisconnected: "Verbinding is verbroken",
        statusAuthfail: "Authenticatie is mislukt",
        roomSubject: "Onderwerp:",
        messageSubmit: "Verstuur",
        labelUsername: "Gebruikersnaam:",
        labelPassword: "Wachtwoord:",
        loginSubmit: "Inloggen",
        loginInvalid: "JID is onjuist",
        reason: "Reden:",
        subject: "Onderwerp:",
        reasonWas: "De reden was: %s.",
        kickActionLabel: "Verwijderen",
        youHaveBeenKickedBy: "Je bent verwijderd van %1$s door %2$s",
        youHaveBeenKicked: "Je bent verwijderd van %s",
        banActionLabel: "Blokkeren",
        youHaveBeenBannedBy: "Je bent geblokkeerd van %1$s door %2$s",
        youHaveBeenBanned: "Je bent geblokkeerd van %s",
        privateActionLabel: "Prive gesprek",
        ignoreActionLabel: "Negeren",
        unignoreActionLabel: "Niet negeren",
        setSubjectActionLabel: "Onderwerp wijzigen",
        administratorMessageSubject: "Beheerder",
        userJoinedRoom: "%s komt de chat binnen.",
        userLeftRoom: "%s heeft de chat verlaten.",
        userHasBeenKickedFromRoom: "%s is verwijderd.",
        userHasBeenBannedFromRoom: "%s is geblokkeerd.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderator",
        tooltipIgnored: "Je negeert deze gebruiker",
        tooltipEmoticons: "Emotie-iconen",
        tooltipSound: "Speel een geluid af bij nieuwe privé berichten.",
        tooltipAutoscroll: "Automatisch scrollen",
        tooltipStatusmessage: "Statusberichten weergeven",
        tooltipAdministration: "Instellingen",
        tooltipUsercount: "Gebruikers",
        enterRoomPassword: 'De Chatroom "%s" is met een wachtwoord beveiligd.',
        enterRoomPasswordSubmit: "Ga naar Chatroom",
        passwordEnteredInvalid: 'Het wachtwoord voor de Chatroom "%s" is onjuist.',
        nicknameConflict: "De gebruikersnaam is reeds in gebruik. Probeer a.u.b. een andere gebruikersnaam.",
        errorMembersOnly: 'Je kunt niet deelnemen aan de Chatroom "%s": Je hebt onvoldoende rechten.',
        errorMaxOccupantsReached: 'Je kunt niet deelnemen aan de Chatroom "%s": Het maximum aantal gebruikers is bereikt.',
        antiSpamMessage: "Het is niet toegestaan om veel berichten naar de server te versturen. Je bent voor een korte periode geblokkeerd."
    },
    es: {
        status: "Estado: %s",
        statusConnecting: "Conectando...",
        statusConnected: "Conectado",
        statusDisconnecting: "Desconectando...",
        statusDisconnected: "Desconectado",
        statusAuthfail: "Falló la autenticación",
        roomSubject: "Asunto:",
        messageSubmit: "Enviar",
        labelUsername: "Usuario:",
        labelPassword: "Clave:",
        loginSubmit: "Entrar",
        loginInvalid: "JID no válido",
        reason: "Razón:",
        subject: "Asunto:",
        reasonWas: "La razón fue: %s.",
        kickActionLabel: "Expulsar",
        youHaveBeenKickedBy: "Has sido expulsado de %1$s por %2$s",
        youHaveBeenKicked: "Has sido expulsado de %s",
        banActionLabel: "Prohibir",
        youHaveBeenBannedBy: "Has sido expulsado permanentemente de %1$s por %2$s",
        youHaveBeenBanned: "Has sido expulsado permanentemente de %s",
        privateActionLabel: "Chat privado",
        ignoreActionLabel: "Ignorar",
        unignoreActionLabel: "No ignorar",
        setSubjectActionLabel: "Cambiar asunto",
        administratorMessageSubject: "Administrador",
        userJoinedRoom: "%s se ha unido a la sala.",
        userLeftRoom: "%s ha dejado la sala.",
        userHasBeenKickedFromRoom: "%s ha sido expulsado de la sala.",
        userHasBeenBannedFromRoom: "%s ha sido expulsado permanentemente de la sala.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderador",
        tooltipIgnored: "Ignoras a éste usuario",
        tooltipEmoticons: "Emoticonos",
        tooltipSound: "Reproducir un sonido para nuevos mensajes privados",
        tooltipAutoscroll: "Desplazamiento automático",
        tooltipStatusmessage: "Mostrar mensajes de estado",
        tooltipAdministration: "Administración de la sala",
        tooltipUsercount: "Usuarios en la sala",
        enterRoomPassword: 'La sala "%s" está protegida mediante contraseña.',
        enterRoomPasswordSubmit: "Unirse a la sala",
        passwordEnteredInvalid: 'Contraseña incorrecta para la sala "%s".',
        nicknameConflict: "El nombre de usuario ya está siendo utilizado. Por favor elija otro.",
        errorMembersOnly: 'No se puede unir a la sala "%s": no tiene privilegios suficientes.',
        errorMaxOccupantsReached: 'No se puede unir a la sala "%s": demasiados participantes.',
        antiSpamMessage: "Por favor, no hagas spam. Has sido bloqueado temporalmente."
    },
    cn: {
        status: "状态: %s",
        statusConnecting: "连接中...",
        statusConnected: "已连接",
        statusDisconnecting: "断开连接中...",
        statusDisconnected: "已断开连接",
        statusAuthfail: "认证失败",
        roomSubject: "主题:",
        messageSubmit: "发送",
        labelUsername: "用户名:",
        labelPassword: "密码:",
        loginSubmit: "登录",
        loginInvalid: "用户名不合法",
        reason: "原因:",
        subject: "主题:",
        reasonWas: "原因是: %s.",
        kickActionLabel: "踢除",
        youHaveBeenKickedBy: "你在 %1$s 被管理者 %2$s 请出房间",
        banActionLabel: "禁言",
        youHaveBeenBannedBy: "你在 %1$s 被管理者 %2$s 禁言",
        privateActionLabel: "单独对话",
        ignoreActionLabel: "忽略",
        unignoreActionLabel: "不忽略",
        setSubjectActionLabel: "变更主题",
        administratorMessageSubject: "管理员",
        userJoinedRoom: "%s 加入房间",
        userLeftRoom: "%s 离开房间",
        userHasBeenKickedFromRoom: "%s 被请出这个房间",
        userHasBeenBannedFromRoom: "%s 被管理者禁言",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "管理",
        tooltipIgnored: "你忽略了这个会员",
        tooltipEmoticons: "表情",
        tooltipSound: "新消息发音",
        tooltipAutoscroll: "滚动条",
        tooltipStatusmessage: "禁用状态消息",
        tooltipAdministration: "房间管理",
        tooltipUsercount: "房间占有者",
        enterRoomPassword: '登录房间 "%s" 需要密码.',
        enterRoomPasswordSubmit: "加入房间",
        passwordEnteredInvalid: '登录房间 "%s" 的密码不正确',
        nicknameConflict: "用户名已经存在，请另选一个",
        errorMembersOnly: '您的权限不够，不能登录房间 "%s" ',
        errorMaxOccupantsReached: '房间 "%s" 的人数已达上限，您不能登录',
        antiSpamMessage: "因为您在短时间内发送过多的消息 服务器要阻止您一小段时间。"
    },
    ja: {
        status: "ステータス: %s",
        statusConnecting: "接続中…",
        statusConnected: "接続されました",
        statusDisconnecting: "ディスコネクト中…",
        statusDisconnected: "ディスコネクトされました",
        statusAuthfail: "認証に失敗しました",
        roomSubject: "トピック：",
        messageSubmit: "送信",
        labelUsername: "ユーザーネーム：",
        labelPassword: "パスワード：",
        loginSubmit: "ログイン",
        loginInvalid: "ユーザーネームが正しくありません",
        reason: "理由：",
        subject: "トピック：",
        reasonWas: "理由: %s。",
        kickActionLabel: "キック",
        youHaveBeenKickedBy: "あなたは%2$sにより%1$sからキックされました。",
        youHaveBeenKicked: "あなたは%sからキックされました。",
        banActionLabel: "アカウントバン",
        youHaveBeenBannedBy: "あなたは%2$sにより%1$sからアカウントバンされました。",
        youHaveBeenBanned: "あなたは%sからアカウントバンされました。",
        privateActionLabel: "プライベートメッセージ",
        ignoreActionLabel: "無視する",
        unignoreActionLabel: "無視をやめる",
        setSubjectActionLabel: "トピックを変える",
        administratorMessageSubject: "管理者",
        userJoinedRoom: "%sは入室しました。",
        userLeftRoom: "%sは退室しました。",
        userHasBeenKickedFromRoom: "%sは部屋からキックされました。",
        userHasBeenBannedFromRoom: "%sは部屋からアカウントバンされました。",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "モデレーター",
        tooltipIgnored: "このユーザーを無視設定にしている",
        tooltipEmoticons: "絵文字",
        tooltipSound: "新しいメッセージが届くたびに音を鳴らす",
        tooltipAutoscroll: "オートスクロール",
        tooltipStatusmessage: "ステータスメッセージを表示",
        tooltipAdministration: "部屋の管理",
        tooltipUsercount: "この部屋の参加者の数",
        enterRoomPassword: '"%s"の部屋に入るにはパスワードが必要です。',
        enterRoomPasswordSubmit: "部屋に入る",
        passwordEnteredInvalid: '"%s"のパスワードと異なるパスワードを入力しました。',
        nicknameConflict: "このユーザーネームはすでに利用されているため、別のユーザーネームを選んでください。",
        errorMembersOnly: '"%s"の部屋に入ることができません: 利用権限を満たしていません。',
        errorMaxOccupantsReached: '"%s"の部屋に入ることができません: 参加者の数はすでに上限に達しました。',
        antiSpamMessage: "スパムなどの行為はやめてください。あなたは一時的にブロックされました。"
    },
    sv: {
        status: "Status: %s",
        statusConnecting: "Ansluter...",
        statusConnected: "Ansluten",
        statusDisconnecting: "Kopplar från...",
        statusDisconnected: "Frånkopplad",
        statusAuthfail: "Autentisering misslyckades",
        roomSubject: "Ämne:",
        messageSubmit: "Skicka",
        labelUsername: "Användarnamn:",
        labelPassword: "Lösenord:",
        loginSubmit: "Logga in",
        loginInvalid: "Ogiltigt JID",
        reason: "Anledning:",
        subject: "Ämne:",
        reasonWas: "Anledningen var: %s.",
        kickActionLabel: "Sparka ut",
        youHaveBeenKickedBy: "Du har blivit utsparkad från %2$s av %1$s",
        youHaveBeenKicked: "Du har blivit utsparkad från %s",
        banActionLabel: "Bannlys",
        youHaveBeenBannedBy: "Du har blivit bannlyst från %1$s av %2$s",
        youHaveBeenBanned: "Du har blivit bannlyst från %s",
        privateActionLabel: "Privat chatt",
        ignoreActionLabel: "Blockera",
        unignoreActionLabel: "Avblockera",
        setSubjectActionLabel: "Ändra ämne",
        administratorMessageSubject: "Administratör",
        userJoinedRoom: "%s kom in i rummet.",
        userLeftRoom: "%s har lämnat rummet.",
        userHasBeenKickedFromRoom: "%s har blivit utsparkad ur rummet.",
        userHasBeenBannedFromRoom: "%s har blivit bannlyst från rummet.",
        dateFormat: "yyyy-mm-dd",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderator",
        tooltipIgnored: "Du blockerar denna användare",
        tooltipEmoticons: "Smilies",
        tooltipSound: "Spela upp ett ljud vid nytt privat meddelande",
        tooltipAutoscroll: "Autoskrolla",
        tooltipStatusmessage: "Visa statusmeddelanden",
        tooltipAdministration: "Rumadministrering",
        tooltipUsercount: "Antal användare i rummet",
        enterRoomPassword: 'Rummet "%s" är lösenordsskyddat.',
        enterRoomPasswordSubmit: "Anslut till rum",
        passwordEnteredInvalid: 'Ogiltigt lösenord för rummet "%s".',
        nicknameConflict: "Upptaget användarnamn. Var god välj ett annat.",
        errorMembersOnly: 'Du kan inte ansluta till rummet "%s": Otillräckliga rättigheter.',
        errorMaxOccupantsReached: 'Du kan inte ansluta till rummet "%s": Rummet är fullt.',
        antiSpamMessage: "Var god avstå från att spamma. Du har blivit blockerad för en kort stund."
    },
    fi: {
        status: "Status: %s",
        statusConnecting: "Muodostetaan yhteyttä...",
        statusConnected: "Yhdistetty",
        statusDisconnecting: "Katkaistaan yhteyttä...",
        statusDisconnected: "Yhteys katkaistu",
        statusAuthfail: "Autentikointi epäonnistui",
        roomSubject: "Otsikko:",
        messageSubmit: "Lähetä",
        labelUsername: "Käyttäjätunnus:",
        labelNickname: "Nimimerkki:",
        labelPassword: "Salasana:",
        loginSubmit: "Kirjaudu sisään",
        loginInvalid: "Virheellinen JID",
        reason: "Syy:",
        subject: "Otsikko:",
        reasonWas: "Syy oli: %s.",
        kickActionLabel: "Potkaise",
        youHaveBeenKickedBy: "Nimimerkki %1$s potkaisi sinut pois huoneesta %2$s",
        youHaveBeenKicked: "Sinut potkaistiin pois huoneesta %s",
        banActionLabel: "Porttikielto",
        youHaveBeenBannedBy: "Nimimerkki %2$s antoi sinulle porttikiellon huoneeseen %1$s",
        youHaveBeenBanned: "Sinulle on annettu porttikielto huoneeseen %s",
        privateActionLabel: "Yksityinen keskustelu",
        ignoreActionLabel: "Hiljennä",
        unignoreActionLabel: "Peruuta hiljennys",
        setSubjectActionLabel: "Vaihda otsikkoa",
        administratorMessageSubject: "Ylläpitäjä",
        userJoinedRoom: "%s tuli huoneeseen.",
        userLeftRoom: "%s lähti huoneesta.",
        userHasBeenKickedFromRoom: "%s potkaistiin pois huoneesta.",
        userHasBeenBannedFromRoom: "%s sai porttikiellon huoneeseen.",
        userChangedNick: "%1$s vaihtoi nimimerkikseen %2$s.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Ylläpitäjä",
        tooltipIgnored: "Olet hiljentänyt tämän käyttäjän",
        tooltipEmoticons: "Hymiöt",
        tooltipSound: "Soita äänimerkki uusista yksityisviesteistä",
        tooltipAutoscroll: "Automaattinen vieritys",
        tooltipStatusmessage: "Näytä statusviestit",
        tooltipAdministration: "Huoneen ylläpito",
        tooltipUsercount: "Huoneen jäsenet",
        enterRoomPassword: 'Huone "%s" on suojattu salasanalla.',
        enterRoomPasswordSubmit: "Liity huoneeseen",
        passwordEnteredInvalid: 'Virheelinen salasana huoneeseen "%s".',
        nicknameConflict: "Käyttäjätunnus oli jo käytössä. Valitse jokin toinen käyttäjätunnus.",
        errorMembersOnly: 'Et voi liittyä huoneeseen "%s": ei oikeuksia.',
        errorMaxOccupantsReached: 'Et voi liittyä huoneeseen "%s": liian paljon jäseniä.',
        errorAutojoinMissing: 'Parametria "autojoin" ei ole määritelty asetuksissa. Tee määrittely jatkaaksesi.',
        antiSpamMessage: "Ethän spämmää. Sinut on nyt väliaikaisesti pistetty jäähylle."
    },
    it: {
        status: "Stato: %s",
        statusConnecting: "Connessione...",
        statusConnected: "Connessione",
        statusDisconnecting: "Disconnessione...",
        statusDisconnected: "Disconnesso",
        statusAuthfail: "Autenticazione fallita",
        roomSubject: "Oggetto:",
        messageSubmit: "Invia",
        labelUsername: "Nome utente:",
        labelPassword: "Password:",
        loginSubmit: "Login",
        loginInvalid: "JID non valido",
        reason: "Ragione:",
        subject: "Oggetto:",
        reasonWas: "Ragione precedente: %s.",
        kickActionLabel: "Espelli",
        youHaveBeenKickedBy: "Sei stato espulso da %2$s da %1$s",
        youHaveBeenKicked: "Sei stato espulso da %s",
        banActionLabel: "Escluso",
        youHaveBeenBannedBy: "Sei stato escluso da %1$s da %2$s",
        youHaveBeenBanned: "Sei stato escluso da %s",
        privateActionLabel: "Stanza privata",
        ignoreActionLabel: "Ignora",
        unignoreActionLabel: "Non ignorare",
        setSubjectActionLabel: "Cambia oggetto",
        administratorMessageSubject: "Amministratore",
        userJoinedRoom: "%s si è unito alla stanza.",
        userLeftRoom: "%s ha lasciato la stanza.",
        userHasBeenKickedFromRoom: "%s è stato espulso dalla stanza.",
        userHasBeenBannedFromRoom: "%s è stato escluso dalla stanza.",
        dateFormat: "dd/mm/yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderatore",
        tooltipIgnored: "Stai ignorando questo utente",
        tooltipEmoticons: "Emoticons",
        tooltipSound: "Riproduci un suono quando arrivano messaggi privati",
        tooltipAutoscroll: "Autoscroll",
        tooltipStatusmessage: "Mostra messaggi di stato",
        tooltipAdministration: "Amministrazione stanza",
        tooltipUsercount: "Partecipanti alla stanza",
        enterRoomPassword: 'La stanza "%s" è protetta da password.',
        enterRoomPasswordSubmit: "Unisciti alla stanza",
        passwordEnteredInvalid: 'Password non valida per la stanza "%s".',
        nicknameConflict: "Nome utente già in uso. Scegline un altro.",
        errorMembersOnly: 'Non puoi unirti alla stanza "%s": Permessi insufficienti.',
        errorMaxOccupantsReached: 'Non puoi unirti alla stanza "%s": Troppi partecipanti.',
        antiSpamMessage: "Per favore non scrivere messaggi pubblicitari. Sei stato bloccato per un po' di tempo."
    },
    pl: {
        status: "Status: %s",
        statusConnecting: "Łączę...",
        statusConnected: "Połączone",
        statusDisconnecting: "Rozłączam...",
        statusDisconnected: "Rozłączone",
        statusAuthfail: "Nieprawidłowa autoryzacja",
        roomSubject: "Temat:",
        messageSubmit: "Wyślij",
        labelUsername: "Nazwa użytkownika:",
        labelNickname: "Ksywka:",
        labelPassword: "Hasło:",
        loginSubmit: "Zaloguj",
        loginInvalid: "Nieprawidłowy JID",
        reason: "Przyczyna:",
        subject: "Temat:",
        reasonWas: "Z powodu: %s.",
        kickActionLabel: "Wykop",
        youHaveBeenKickedBy: "Zostałeś wykopany z %2$s przez %1$s",
        youHaveBeenKicked: "Zostałeś wykopany z %s",
        banActionLabel: "Ban",
        youHaveBeenBannedBy: "Zostałeś zbanowany na %1$s przez %2$s",
        youHaveBeenBanned: "Zostałeś zbanowany na %s",
        privateActionLabel: "Rozmowa prywatna",
        ignoreActionLabel: "Zignoruj",
        unignoreActionLabel: "Przestań ignorować",
        setSubjectActionLabel: "Zmień temat",
        administratorMessageSubject: "Administrator",
        userJoinedRoom: "%s wszedł do pokoju.",
        userLeftRoom: "%s opuścił pokój.",
        userHasBeenKickedFromRoom: "%s został wykopany z pokoju.",
        userHasBeenBannedFromRoom: "%s został zbanowany w pokoju.",
        userChangedNick: "%1$s zmienił ksywkę na %2$s.",
        presenceUnknownWarningSubject: "Uwaga:",
        presenceUnknownWarning: "Rozmówca może nie być połączony. Nie możemy ustalić jego obecności.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderator",
        tooltipIgnored: "Ignorujesz tego rozmówcę",
        tooltipEmoticons: "Emoty",
        tooltipSound: "Sygnał dźwiękowy przy otrzymaniu wiadomości",
        tooltipAutoscroll: "Autoprzewijanie",
        tooltipStatusmessage: "Wyświetl statusy",
        tooltipAdministration: "Administrator pokoju",
        tooltipUsercount: "Obecni rozmówcy",
        enterRoomPassword: 'Pokój "%s" wymaga hasła.',
        enterRoomPasswordSubmit: "Wejdź do pokoju",
        passwordEnteredInvalid: 'Niewłaściwie hasło do pokoju "%s".',
        nicknameConflict: "Nazwa w użyciu. Wybierz inną.",
        errorMembersOnly: 'Nie możesz wejść do pokoju "%s": Niepełne uprawnienia.',
        errorMaxOccupantsReached: 'Nie możesz wejść do pokoju "%s": Siedzi w nim zbyt wielu ludzi.',
        errorAutojoinMissing: "Konfiguracja nie zawiera parametru automatycznego wejścia do pokoju. Wskaż pokój do którego chcesz wejść.",
        antiSpamMessage: "Please do not spam. You have been blocked for a short-time."
    },
    pt: {
        status: "Status: %s",
        statusConnecting: "Conectando...",
        statusConnected: "Conectado",
        statusDisconnecting: "Desligando...",
        statusDisconnected: "Desligado",
        statusAuthfail: "Falha na autenticação",
        roomSubject: "Assunto:",
        messageSubmit: "Enviar",
        labelUsername: "Usuário:",
        labelPassword: "Senha:",
        loginSubmit: "Entrar",
        loginInvalid: "JID inválido",
        reason: "Motivo:",
        subject: "Assunto:",
        reasonWas: "O motivo foi: %s.",
        kickActionLabel: "Excluir",
        youHaveBeenKickedBy: "Você foi excluido de %1$s por %2$s",
        youHaveBeenKicked: "Você foi excluido de %s",
        banActionLabel: "Bloquear",
        youHaveBeenBannedBy: "Você foi excluido permanentemente de %1$s por %2$s",
        youHaveBeenBanned: "Você foi excluido permanentemente de %s",
        privateActionLabel: "Bate-papo privado",
        ignoreActionLabel: "Ignorar",
        unignoreActionLabel: "Não ignorar",
        setSubjectActionLabel: "Trocar Assunto",
        administratorMessageSubject: "Administrador",
        userJoinedRoom: "%s entrou na sala.",
        userLeftRoom: "%s saiu da sala.",
        userHasBeenKickedFromRoom: "%s foi excluido da sala.",
        userHasBeenBannedFromRoom: "%s foi excluido permanentemente da sala.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderador",
        tooltipIgnored: "Você ignora este usuário",
        tooltipEmoticons: "Emoticons",
        tooltipSound: "Reproduzir o som para novas mensagens privados",
        tooltipAutoscroll: "Deslocamento automático",
        tooltipStatusmessage: "Mostrar mensagens de status",
        tooltipAdministration: "Administração da sala",
        tooltipUsercount: "Usuários na sala",
        enterRoomPassword: 'A sala "%s" é protegida por senha.',
        enterRoomPasswordSubmit: "Junte-se à sala",
        passwordEnteredInvalid: 'Senha incorreta para a sala "%s".',
        nicknameConflict: "O nome de usuário já está em uso. Por favor, escolha outro.",
        errorMembersOnly: 'Você não pode participar da sala "%s":  privilégios insuficientes.',
        errorMaxOccupantsReached: 'Você não pode participar da sala "%s": muitos participantes.',
        antiSpamMessage: "Por favor, não envie spam. Você foi bloqueado temporariamente."
    },
    pt_br: {
        status: "Estado: %s",
        statusConnecting: "Conectando...",
        statusConnected: "Conectado",
        statusDisconnecting: "Desconectando...",
        statusDisconnected: "Desconectado",
        statusAuthfail: "Autenticação falhou",
        roomSubject: "Assunto:",
        messageSubmit: "Enviar",
        labelUsername: "Usuário:",
        labelPassword: "Senha:",
        loginSubmit: "Entrar",
        loginInvalid: "JID inválido",
        reason: "Motivo:",
        subject: "Assunto:",
        reasonWas: "Motivo foi: %s.",
        kickActionLabel: "Derrubar",
        youHaveBeenKickedBy: "Você foi derrubado de %2$s por %1$s",
        youHaveBeenKicked: "Você foi derrubado de %s",
        banActionLabel: "Banir",
        youHaveBeenBannedBy: "Você foi banido de %1$s por %2$s",
        youHaveBeenBanned: "Você foi banido de %s",
        privateActionLabel: "Conversa privada",
        ignoreActionLabel: "Ignorar",
        unignoreActionLabel: "Não ignorar",
        setSubjectActionLabel: "Mudar Assunto",
        administratorMessageSubject: "Administrador",
        userJoinedRoom: "%s entrou na sala.",
        userLeftRoom: "%s saiu da sala.",
        userHasBeenKickedFromRoom: "%s foi derrubado da sala.",
        userHasBeenBannedFromRoom: "%s foi banido da sala.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderador",
        tooltipIgnored: "Você ignora este usuário",
        tooltipEmoticons: "Emoticons",
        tooltipSound: "Tocar som para novas mensagens privadas",
        tooltipAutoscroll: "Auto-rolagem",
        tooltipStatusmessage: "Exibir mensagens de estados",
        tooltipAdministration: "Administração de Sala",
        tooltipUsercount: "Participantes da Sala",
        enterRoomPassword: 'Sala "%s" é protegida por senha.',
        enterRoomPasswordSubmit: "Entrar na sala",
        passwordEnteredInvalid: 'Senha inváida para sala "%s".',
        nicknameConflict: "Nome de usuário já em uso. Por favor escolha outro.",
        errorMembersOnly: 'Você não pode entrar na sala "%s": privilégios insuficientes.',
        errorMaxOccupantsReached: 'Você não pode entrar na sala "%s": máximo de participantes atingido.',
        antiSpamMessage: "Por favor, não faça spam. Você foi bloqueado temporariamente."
    },
    ru: {
        status: "Статус: %s",
        statusConnecting: "Подключение...",
        statusConnected: "Подключено",
        statusDisconnecting: "Отключение...",
        statusDisconnected: "Отключено",
        statusAuthfail: "Неверный логин",
        roomSubject: "Топик:",
        messageSubmit: "Послать",
        labelUsername: "Имя:",
        labelNickname: "Ник:",
        labelPassword: "Пароль:",
        loginSubmit: "Логин",
        loginInvalid: "Неверный JID",
        reason: "Причина:",
        subject: "Топик:",
        reasonWas: "Причина была: %s.",
        kickActionLabel: "Выбросить",
        youHaveBeenKickedBy: "Пользователь %1$s выбросил вас из чата %2$s",
        youHaveBeenKicked: "Вас выбросили из чата %s",
        banActionLabel: "Запретить доступ",
        youHaveBeenBannedBy: "Пользователь %1$s запретил вам доступ в чат %2$s",
        youHaveBeenBanned: "Вам запретили доступ в чат %s",
        privateActionLabel: "Один-на-один чат",
        ignoreActionLabel: "Игнорировать",
        unignoreActionLabel: "Отменить игнорирование",
        setSubjectActionLabel: "Изменить топик",
        administratorMessageSubject: "Администратор",
        userJoinedRoom: "%s вошёл в чат.",
        userLeftRoom: "%s вышел из чата.",
        userHasBeenKickedFromRoom: "%s выброшен из чата.",
        userHasBeenBannedFromRoom: "%s запрещён доступ в чат.",
        userChangedNick: "%1$s сменил имя на %2$s.",
        presenceUnknownWarningSubject: "Уведомление:",
        presenceUnknownWarning: "Этот пользователь вероятнее всего оффлайн.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Модератор",
        tooltipIgnored: "Вы игнорируете этого пользователя.",
        tooltipEmoticons: "Смайлики",
        tooltipSound: "Озвучивать новое частное сообщение",
        tooltipAutoscroll: "Авто-прокручивание",
        tooltipStatusmessage: "Показывать статус сообщения",
        tooltipAdministration: "Администрирование чат комнаты",
        tooltipUsercount: "Участники чата",
        enterRoomPassword: 'Чат комната "%s" защищена паролем.',
        enterRoomPasswordSubmit: "Войти в чат",
        passwordEnteredInvalid: 'Неверный пароль для комнаты "%s".',
        nicknameConflict: "Это имя уже используется. Пожалуйста выберите другое имя.",
        errorMembersOnly: 'Вы не можете войти в чат "%s": Недостаточно прав доступа.',
        errorMaxOccupantsReached: 'Вы не можете войти в чат "%s": Слишком много участников.',
        errorAutojoinMissing: "Параметры автовхода не устновлены. Настройте их для продолжения.",
        antiSpamMessage: "Пожалуйста не рассылайте спам. Вас заблокировали на короткое время."
    },
    ca: {
        status: "Estat: %s",
        statusConnecting: "Connectant...",
        statusConnected: "Connectat",
        statusDisconnecting: "Desconnectant...",
        statusDisconnected: "Desconnectat",
        statusAuthfail: "Ha fallat la autenticació",
        roomSubject: "Assumpte:",
        messageSubmit: "Enviar",
        labelUsername: "Usuari:",
        labelPassword: "Clau:",
        loginSubmit: "Entrar",
        loginInvalid: "JID no vàlid",
        reason: "Raó:",
        subject: "Assumpte:",
        reasonWas: "La raó ha estat: %s.",
        kickActionLabel: "Expulsar",
        youHaveBeenKickedBy: "Has estat expulsat de %1$s per %2$s",
        youHaveBeenKicked: "Has estat expulsat de %s",
        banActionLabel: "Prohibir",
        youHaveBeenBannedBy: "Has estat expulsat permanentment de %1$s per %2$s",
        youHaveBeenBanned: "Has estat expulsat permanentment de %s",
        privateActionLabel: "Xat privat",
        ignoreActionLabel: "Ignorar",
        unignoreActionLabel: "No ignorar",
        setSubjectActionLabel: "Canviar assumpte",
        administratorMessageSubject: "Administrador",
        userJoinedRoom: "%s ha entrat a la sala.",
        userLeftRoom: "%s ha deixat la sala.",
        userHasBeenKickedFromRoom: "%s ha estat expulsat de la sala.",
        userHasBeenBannedFromRoom: "%s ha estat expulsat permanentment de la sala.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderador",
        tooltipIgnored: "Estàs ignorant aquest usuari",
        tooltipEmoticons: "Emoticones",
        tooltipSound: "Reproduir un so per a nous missatges",
        tooltipAutoscroll: "Desplaçament automàtic",
        tooltipStatusmessage: "Mostrar missatges d'estat",
        tooltipAdministration: "Administració de la sala",
        tooltipUsercount: "Usuaris dins la sala",
        enterRoomPassword: 'La sala "%s" està protegida amb contrasenya.',
        enterRoomPasswordSubmit: "Entrar a la sala",
        passwordEnteredInvalid: 'Contrasenya incorrecta per a la sala "%s".',
        nicknameConflict: "El nom d'usuari ja s'està utilitzant. Si us plau, escolleix-ne un altre.",
        errorMembersOnly: 'No pots unir-te a la sala "%s": no tens prous privilegis.',
        errorMaxOccupantsReached: 'No pots unir-te a la sala "%s": hi ha masses participants.',
        antiSpamMessage: "Si us plau, no facis spam. Has estat bloquejat temporalment."
    },
    cs: {
        status: "Stav: %s",
        statusConnecting: "Připojování...",
        statusConnected: "Připojeno",
        statusDisconnecting: "Odpojování...",
        statusDisconnected: "Odpojeno",
        statusAuthfail: "Přihlášení selhalo",
        roomSubject: "Předmět:",
        messageSubmit: "Odeslat",
        labelUsername: "Už. jméno:",
        labelNickname: "Přezdívka:",
        labelPassword: "Heslo:",
        loginSubmit: "Přihlásit se",
        loginInvalid: "Neplatné JID",
        reason: "Důvod:",
        subject: "Předmět:",
        reasonWas: "Důvod byl: %s.",
        kickActionLabel: "Vykopnout",
        youHaveBeenKickedBy: "Byl jsi vyloučen z %2$s uživatelem %1$s",
        youHaveBeenKicked: "Byl jsi vyloučen z %s",
        banActionLabel: "Ban",
        youHaveBeenBannedBy: "Byl jsi trvale vyloučen z %1$s uživatelem %2$s",
        youHaveBeenBanned: "Byl jsi trvale vyloučen z %s",
        privateActionLabel: "Soukromý chat",
        ignoreActionLabel: "Ignorovat",
        unignoreActionLabel: "Neignorovat",
        setSubjectActionLabel: "Změnit předmět",
        administratorMessageSubject: "Adminitrátor",
        userJoinedRoom: "%s vešel do místnosti.",
        userLeftRoom: "%s opustil místnost.",
        userHasBeenKickedFromRoom: "%s byl vyloučen z místnosti.",
        userHasBeenBannedFromRoom: "%s byl trvale vyloučen z místnosti.",
        userChangedNick: "%1$s si změnil přezdívku na  %2$s.",
        presenceUnknownWarningSubject: "Poznámka:",
        presenceUnknownWarning: "Tento uživatel může být offiline. Nemůžeme sledovat jeho přítmonost..",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "Moderátor",
        tooltipIgnored: "Tento uživatel je ignorován",
        tooltipEmoticons: "Emotikony",
        tooltipSound: "Přehrát zvuk při nové soukromé zprávě",
        tooltipAutoscroll: "Automaticky rolovat",
        tooltipStatusmessage: "Zobrazovat stavové zprávy",
        tooltipAdministration: "Správa místnosti",
        tooltipUsercount: "Uživatelé",
        enterRoomPassword: 'Místnost "%s" je chráněna heslem.',
        enterRoomPasswordSubmit: "Připojit se do místnosti",
        passwordEnteredInvalid: 'Neplatné heslo pro místnost "%s".',
        nicknameConflict: "Takové přihlašovací jméno je již použito. Vyberte si prosím jiné.",
        errorMembersOnly: 'Nemůžete se připojit do místnosti "%s": Nedostatečné oprávnění.',
        errorMaxOccupantsReached: 'Nemůžete se připojit do místnosti "%s": Příliš mnoho uživatelů.',
        errorAutojoinMissing: "Není nastaven parametr autojoin. Nastavte jej prosím.",
        antiSpamMessage: "Nespamujte prosím. Váš účet byl na chvilku zablokován."
    },
    he: {
        status: "מצב: %s",
        statusConnecting: "כעת מתחבר...",
        statusConnected: "מחובר",
        statusDisconnecting: "כעת מתנתק...",
        statusDisconnected: "מנותק",
        statusAuthfail: "אימות נכשל",
        roomSubject: "נושא:",
        messageSubmit: "שלח",
        labelUsername: "שם משתמש:",
        labelNickname: "שם כינוי:",
        labelPassword: "סיסמה:",
        loginSubmit: "כניסה",
        loginInvalid: "JID לא תקני",
        reason: "סיבה:",
        subject: "נושא:",
        reasonWas: "סיבה היתה: %s.",
        kickActionLabel: "בעט",
        youHaveBeenKickedBy: "נבעטת מתוך %2$s על ידי %1$s",
        youHaveBeenKicked: "נבעטת מתוך %s",
        banActionLabel: "אסור",
        youHaveBeenBannedBy: "נאסרת מתוך %1$s על ידי %2$s",
        youHaveBeenBanned: "נאסרת מתוך %s",
        privateActionLabel: "שיחה פרטית",
        ignoreActionLabel: "התעלם",
        unignoreActionLabel: "בטל התעלמות",
        setSubjectActionLabel: "שנה נושא",
        administratorMessageSubject: "מנהל",
        userJoinedRoom: "%s נכנס(ה) לחדר.",
        userLeftRoom: "%s עזב(ה) את החדר.",
        userHasBeenKickedFromRoom: "%s נבעט(ה) מתוך החדר.",
        userHasBeenBannedFromRoom: "%s נאסר(ה) מתוך החדר.",
        userChangedNick: "%1$s מוכר(ת) כעת בתור %2$s.",
        dateFormat: "dd.mm.yyyy",
        timeFormat: "HH:MM:ss",
        tooltipRole: "אחראי",
        tooltipIgnored: "אתה מתעלם ממשתמש זה",
        tooltipEmoticons: "רגשונים",
        tooltipSound: "נגן צליל עבור הודעות פרטיות חדשות",
        tooltipAutoscroll: "גלילה אוטומטית",
        tooltipStatusmessage: "הצג הודעות מצב",
        tooltipAdministration: "הנהלת חדר",
        tooltipUsercount: "משתתפי חדר",
        enterRoomPassword: 'חדר "%s" הינו מוגן סיסמה.',
        enterRoomPasswordSubmit: "הצטרף לחדר",
        passwordEnteredInvalid: 'סיסמה שגויה לחדר "%s".',
        nicknameConflict: "שם משתמש כבר מצוי בשימוש. אנא בחר אחד אחר.",
        errorMembersOnly: 'אין באפשרותך להצטרף לחדר "%s": הרשאות לקויות.',
        errorMaxOccupantsReached: 'אין באפשרותך להצטרף לחדר "%s": יותר מדי משתתפים.',
        errorAutojoinMissing: "לא נקבע פרמטר הצטרפות אוטומטית בתצורה. אנא הזן כזה כדי להמשיך.",
        antiSpamMessage: "אנא אל תשלח ספאם. נחסמת למשך זמן קצר."
    }
};
//# sourceMappingURL=candy.bundle.map