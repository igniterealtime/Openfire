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
