/** File: util.js
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

/* global Candy, MD5, Strophe, document, escape, jQuery */

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
			jid += '/' + resource;
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
			jid += '/' + resource;
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
		return $('<div/>').append(self.createHtml($(str).get(0), len)).html();
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
		var timestamp = Date.parse(date);
		if(isNaN(timestamp)) {
			var struct = /^(\d{4}|[+\-]\d{6})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{3,}))?)?(?:(Z)|([+\-])(\d{2})(?::?(\d{2}))?))?/.exec(date);
			if(struct) {
				var minutesOffset = 0;
				if(struct[8] !== 'Z') {
					minutesOffset = +struct[10] * 60 + (+struct[11]);
					if(struct[9] === '+') {
						minutesOffset = -minutesOffset;
					}
				}
				minutesOffset -= new Date().getTimezoneOffset();
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

	/** PrivateVariable: ie
	 * Checks for IE version
	 *
	 * From: http://stackoverflow.com/a/5574871/315242
	 */
	var ie = (function(){
		var undef,
			v = 3,
			div = document.createElement('div'),
			all = div.getElementsByTagName('i');
		while (
			// adds innerhtml and continues as long as all[0] is truthy
			div.innerHTML = '<!--[if gt IE ' + (++v) + ']><i></i><![endif]-->',
			all[0]
		) {}
		return v > 4 ? v : undef;
	}());

	/** Function: getIeVersion
	 * Returns local variable `ie` which you can use to detect which IE version
	 * is available.
	 *
	 * Use e.g. like this: if(Candy.Util.getIeVersion() < 9) alert('kaboom');
	 */
	self.getIeVersion = function() {
		return ie;
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
			return text.replace(/\r\n|\r|\n/g, '<br />');
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
			if(text) {
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
			if(Strophe.XHTML.validTag(tag)) {
				try {
					el = $('<' + tag + '/>');
					for(i = 0; i < Strophe.XHTML.attributes[tag].length; i++) {
						attribute = Strophe.XHTML.attributes[tag][i];
						value = elem.getAttribute(attribute);
						if(typeof value === 'undefined' || value === null || value === '' || value === false || value === 0) {
							continue;
						}
						if(attribute === 'style' && typeof value === 'object') {
							if(typeof value.cssText !== 'undefined') {
								value = value.cssText; // we're dealing with IE, need to get CSS out
							}
						}
						// filter out invalid css styles
						if(attribute === 'style') {
							css = [];
							cssAttrs = value.split(';');
							for(j = 0; j < cssAttrs.length; j++) {
								attr = cssAttrs[j].split(':');
								cssName = attr[0].replace(/^\s*/, "").replace(/\s*$/, "").toLowerCase();
								if(Strophe.XHTML.validCSS(cssName)) {
									cssValue = attr[1].replace(/^\s*/, "").replace(/\s*$/, "");
									css.push(cssName + ': ' + cssValue);
								}
							}
							if(css.length > 0) {
								value = css.join('; ');
								el.attr(attribute, value);
							}
						} else {
							el.attr(attribute, value);
						}
					}

					for (i = 0; i < elem.childNodes.length; i++) {
						el.append(self.createHtml(elem.childNodes[i], maxLength, currentLength));
					}
				} catch(e) { // invalid elements
					Candy.Core.log("[Util:createHtml] Error while parsing XHTML:");
					Candy.Core.log(e);
					el = Strophe.xmlTextNode('');
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
			if(maxLength && currentLength > maxLength) {
				text = text.substring(0, maxLength);
			}
			text = Candy.Util.Parser.all(text);
			el = $.parseHTML(text);
		}

		return el;
		/* jshint +W073 */
	};

	return self;
}(Candy.Util || {}, jQuery));
