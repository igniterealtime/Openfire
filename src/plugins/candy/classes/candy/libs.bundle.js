// This code was written by Tyler Akins and has been placed in the
// public domain.  It would be nice if you left this header intact.
// Base64 code from Tyler Akins -- http://rumkin.com
var Base64 = function() {
    var keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    var obj = {
        /**
         * Encodes a string in base64
         * @param {String} input The string to encode in base64.
         */
        encode: function(input) {
            var output = "";
            var chr1, chr2, chr3;
            var enc1, enc2, enc3, enc4;
            var i = 0;
            do {
                chr1 = input.charCodeAt(i++);
                chr2 = input.charCodeAt(i++);
                chr3 = input.charCodeAt(i++);
                enc1 = chr1 >> 2;
                enc2 = (chr1 & 3) << 4 | chr2 >> 4;
                enc3 = (chr2 & 15) << 2 | chr3 >> 6;
                enc4 = chr3 & 63;
                if (isNaN(chr2)) {
                    enc3 = enc4 = 64;
                } else if (isNaN(chr3)) {
                    enc4 = 64;
                }
                output = output + keyStr.charAt(enc1) + keyStr.charAt(enc2) + keyStr.charAt(enc3) + keyStr.charAt(enc4);
            } while (i < input.length);
            return output;
        },
        /**
         * Decodes a base64 string.
         * @param {String} input The string to decode.
         */
        decode: function(input) {
            var output = "";
            var chr1, chr2, chr3;
            var enc1, enc2, enc3, enc4;
            var i = 0;
            // remove all characters that are not A-Z, a-z, 0-9, +, /, or =
            input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");
            do {
                enc1 = keyStr.indexOf(input.charAt(i++));
                enc2 = keyStr.indexOf(input.charAt(i++));
                enc3 = keyStr.indexOf(input.charAt(i++));
                enc4 = keyStr.indexOf(input.charAt(i++));
                chr1 = enc1 << 2 | enc2 >> 4;
                chr2 = (enc2 & 15) << 4 | enc3 >> 2;
                chr3 = (enc3 & 3) << 6 | enc4;
                output = output + String.fromCharCode(chr1);
                if (enc3 != 64) {
                    output = output + String.fromCharCode(chr2);
                }
                if (enc4 != 64) {
                    output = output + String.fromCharCode(chr3);
                }
            } while (i < input.length);
            return output;
        }
    };
    return obj;
}();

/*
 * A JavaScript implementation of the Secure Hash Algorithm, SHA-1, as defined
 * in FIPS PUB 180-1
 * Version 2.1a Copyright Paul Johnston 2000 - 2002.
 * Other contributors: Greg Holt, Andrew Kepert, Ydnar, Lostinet
 * Distributed under the BSD License
 * See http://pajhome.org.uk/crypt/md5 for details.
 */
/* Some functions and variables have been stripped for use with Strophe */
/*
 * These are the functions you'll usually want to call
 * They take string arguments and return either hex or base-64 encoded strings
 */
function b64_sha1(s) {
    return binb2b64(core_sha1(str2binb(s), s.length * 8));
}

function str_sha1(s) {
    return binb2str(core_sha1(str2binb(s), s.length * 8));
}

function b64_hmac_sha1(key, data) {
    return binb2b64(core_hmac_sha1(key, data));
}

function str_hmac_sha1(key, data) {
    return binb2str(core_hmac_sha1(key, data));
}

/*
 * Calculate the SHA-1 of an array of big-endian words, and a bit length
 */
function core_sha1(x, len) {
    /* append padding */
    x[len >> 5] |= 128 << 24 - len % 32;
    x[(len + 64 >> 9 << 4) + 15] = len;
    var w = new Array(80);
    var a = 1732584193;
    var b = -271733879;
    var c = -1732584194;
    var d = 271733878;
    var e = -1009589776;
    var i, j, t, olda, oldb, oldc, oldd, olde;
    for (i = 0; i < x.length; i += 16) {
        olda = a;
        oldb = b;
        oldc = c;
        oldd = d;
        olde = e;
        for (j = 0; j < 80; j++) {
            if (j < 16) {
                w[j] = x[i + j];
            } else {
                w[j] = rol(w[j - 3] ^ w[j - 8] ^ w[j - 14] ^ w[j - 16], 1);
            }
            t = safe_add(safe_add(rol(a, 5), sha1_ft(j, b, c, d)), safe_add(safe_add(e, w[j]), sha1_kt(j)));
            e = d;
            d = c;
            c = rol(b, 30);
            b = a;
            a = t;
        }
        a = safe_add(a, olda);
        b = safe_add(b, oldb);
        c = safe_add(c, oldc);
        d = safe_add(d, oldd);
        e = safe_add(e, olde);
    }
    return [ a, b, c, d, e ];
}

/*
 * Perform the appropriate triplet combination function for the current
 * iteration
 */
function sha1_ft(t, b, c, d) {
    if (t < 20) {
        return b & c | ~b & d;
    }
    if (t < 40) {
        return b ^ c ^ d;
    }
    if (t < 60) {
        return b & c | b & d | c & d;
    }
    return b ^ c ^ d;
}

/*
 * Determine the appropriate additive constant for the current iteration
 */
function sha1_kt(t) {
    return t < 20 ? 1518500249 : t < 40 ? 1859775393 : t < 60 ? -1894007588 : -899497514;
}

/*
 * Calculate the HMAC-SHA1 of a key and some data
 */
function core_hmac_sha1(key, data) {
    var bkey = str2binb(key);
    if (bkey.length > 16) {
        bkey = core_sha1(bkey, key.length * 8);
    }
    var ipad = new Array(16), opad = new Array(16);
    for (var i = 0; i < 16; i++) {
        ipad[i] = bkey[i] ^ 909522486;
        opad[i] = bkey[i] ^ 1549556828;
    }
    var hash = core_sha1(ipad.concat(str2binb(data)), 512 + data.length * 8);
    return core_sha1(opad.concat(hash), 512 + 160);
}

/*
 * Add integers, wrapping at 2^32. This uses 16-bit operations internally
 * to work around bugs in some JS interpreters.
 */
function safe_add(x, y) {
    var lsw = (x & 65535) + (y & 65535);
    var msw = (x >> 16) + (y >> 16) + (lsw >> 16);
    return msw << 16 | lsw & 65535;
}

/*
 * Bitwise rotate a 32-bit number to the left.
 */
function rol(num, cnt) {
    return num << cnt | num >>> 32 - cnt;
}

/*
 * Convert an 8-bit or 16-bit string to an array of big-endian words
 * In 8-bit function, characters >255 have their hi-byte silently ignored.
 */
function str2binb(str) {
    var bin = [];
    var mask = 255;
    for (var i = 0; i < str.length * 8; i += 8) {
        bin[i >> 5] |= (str.charCodeAt(i / 8) & mask) << 24 - i % 32;
    }
    return bin;
}

/*
 * Convert an array of big-endian words to a string
 */
function binb2str(bin) {
    var str = "";
    var mask = 255;
    for (var i = 0; i < bin.length * 32; i += 8) {
        str += String.fromCharCode(bin[i >> 5] >>> 24 - i % 32 & mask);
    }
    return str;
}

/*
 * Convert an array of big-endian words to a base-64 string
 */
function binb2b64(binarray) {
    var tab = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    var str = "";
    var triplet, j;
    for (var i = 0; i < binarray.length * 4; i += 3) {
        triplet = (binarray[i >> 2] >> 8 * (3 - i % 4) & 255) << 16 | (binarray[i + 1 >> 2] >> 8 * (3 - (i + 1) % 4) & 255) << 8 | binarray[i + 2 >> 2] >> 8 * (3 - (i + 2) % 4) & 255;
        for (j = 0; j < 4; j++) {
            if (i * 8 + j * 6 > binarray.length * 32) {
                str += "=";
            } else {
                str += tab.charAt(triplet >> 6 * (3 - j) & 63);
            }
        }
    }
    return str;
}

/*
 * A JavaScript implementation of the RSA Data Security, Inc. MD5 Message
 * Digest Algorithm, as defined in RFC 1321.
 * Version 2.1 Copyright (C) Paul Johnston 1999 - 2002.
 * Other contributors: Greg Holt, Andrew Kepert, Ydnar, Lostinet
 * Distributed under the BSD License
 * See http://pajhome.org.uk/crypt/md5 for more info.
 */
/*
 * Everything that isn't used by Strophe has been stripped here!
 */
var MD5 = function() {
    /*
     * Add integers, wrapping at 2^32. This uses 16-bit operations internally
     * to work around bugs in some JS interpreters.
     */
    var safe_add = function(x, y) {
        var lsw = (x & 65535) + (y & 65535);
        var msw = (x >> 16) + (y >> 16) + (lsw >> 16);
        return msw << 16 | lsw & 65535;
    };
    /*
     * Bitwise rotate a 32-bit number to the left.
     */
    var bit_rol = function(num, cnt) {
        return num << cnt | num >>> 32 - cnt;
    };
    /*
     * Convert a string to an array of little-endian words
     */
    var str2binl = function(str) {
        var bin = [];
        for (var i = 0; i < str.length * 8; i += 8) {
            bin[i >> 5] |= (str.charCodeAt(i / 8) & 255) << i % 32;
        }
        return bin;
    };
    /*
     * Convert an array of little-endian words to a string
     */
    var binl2str = function(bin) {
        var str = "";
        for (var i = 0; i < bin.length * 32; i += 8) {
            str += String.fromCharCode(bin[i >> 5] >>> i % 32 & 255);
        }
        return str;
    };
    /*
     * Convert an array of little-endian words to a hex string.
     */
    var binl2hex = function(binarray) {
        var hex_tab = "0123456789abcdef";
        var str = "";
        for (var i = 0; i < binarray.length * 4; i++) {
            str += hex_tab.charAt(binarray[i >> 2] >> i % 4 * 8 + 4 & 15) + hex_tab.charAt(binarray[i >> 2] >> i % 4 * 8 & 15);
        }
        return str;
    };
    /*
     * These functions implement the four basic operations the algorithm uses.
     */
    var md5_cmn = function(q, a, b, x, s, t) {
        return safe_add(bit_rol(safe_add(safe_add(a, q), safe_add(x, t)), s), b);
    };
    var md5_ff = function(a, b, c, d, x, s, t) {
        return md5_cmn(b & c | ~b & d, a, b, x, s, t);
    };
    var md5_gg = function(a, b, c, d, x, s, t) {
        return md5_cmn(b & d | c & ~d, a, b, x, s, t);
    };
    var md5_hh = function(a, b, c, d, x, s, t) {
        return md5_cmn(b ^ c ^ d, a, b, x, s, t);
    };
    var md5_ii = function(a, b, c, d, x, s, t) {
        return md5_cmn(c ^ (b | ~d), a, b, x, s, t);
    };
    /*
     * Calculate the MD5 of an array of little-endian words, and a bit length
     */
    var core_md5 = function(x, len) {
        /* append padding */
        x[len >> 5] |= 128 << len % 32;
        x[(len + 64 >>> 9 << 4) + 14] = len;
        var a = 1732584193;
        var b = -271733879;
        var c = -1732584194;
        var d = 271733878;
        var olda, oldb, oldc, oldd;
        for (var i = 0; i < x.length; i += 16) {
            olda = a;
            oldb = b;
            oldc = c;
            oldd = d;
            a = md5_ff(a, b, c, d, x[i + 0], 7, -680876936);
            d = md5_ff(d, a, b, c, x[i + 1], 12, -389564586);
            c = md5_ff(c, d, a, b, x[i + 2], 17, 606105819);
            b = md5_ff(b, c, d, a, x[i + 3], 22, -1044525330);
            a = md5_ff(a, b, c, d, x[i + 4], 7, -176418897);
            d = md5_ff(d, a, b, c, x[i + 5], 12, 1200080426);
            c = md5_ff(c, d, a, b, x[i + 6], 17, -1473231341);
            b = md5_ff(b, c, d, a, x[i + 7], 22, -45705983);
            a = md5_ff(a, b, c, d, x[i + 8], 7, 1770035416);
            d = md5_ff(d, a, b, c, x[i + 9], 12, -1958414417);
            c = md5_ff(c, d, a, b, x[i + 10], 17, -42063);
            b = md5_ff(b, c, d, a, x[i + 11], 22, -1990404162);
            a = md5_ff(a, b, c, d, x[i + 12], 7, 1804603682);
            d = md5_ff(d, a, b, c, x[i + 13], 12, -40341101);
            c = md5_ff(c, d, a, b, x[i + 14], 17, -1502002290);
            b = md5_ff(b, c, d, a, x[i + 15], 22, 1236535329);
            a = md5_gg(a, b, c, d, x[i + 1], 5, -165796510);
            d = md5_gg(d, a, b, c, x[i + 6], 9, -1069501632);
            c = md5_gg(c, d, a, b, x[i + 11], 14, 643717713);
            b = md5_gg(b, c, d, a, x[i + 0], 20, -373897302);
            a = md5_gg(a, b, c, d, x[i + 5], 5, -701558691);
            d = md5_gg(d, a, b, c, x[i + 10], 9, 38016083);
            c = md5_gg(c, d, a, b, x[i + 15], 14, -660478335);
            b = md5_gg(b, c, d, a, x[i + 4], 20, -405537848);
            a = md5_gg(a, b, c, d, x[i + 9], 5, 568446438);
            d = md5_gg(d, a, b, c, x[i + 14], 9, -1019803690);
            c = md5_gg(c, d, a, b, x[i + 3], 14, -187363961);
            b = md5_gg(b, c, d, a, x[i + 8], 20, 1163531501);
            a = md5_gg(a, b, c, d, x[i + 13], 5, -1444681467);
            d = md5_gg(d, a, b, c, x[i + 2], 9, -51403784);
            c = md5_gg(c, d, a, b, x[i + 7], 14, 1735328473);
            b = md5_gg(b, c, d, a, x[i + 12], 20, -1926607734);
            a = md5_hh(a, b, c, d, x[i + 5], 4, -378558);
            d = md5_hh(d, a, b, c, x[i + 8], 11, -2022574463);
            c = md5_hh(c, d, a, b, x[i + 11], 16, 1839030562);
            b = md5_hh(b, c, d, a, x[i + 14], 23, -35309556);
            a = md5_hh(a, b, c, d, x[i + 1], 4, -1530992060);
            d = md5_hh(d, a, b, c, x[i + 4], 11, 1272893353);
            c = md5_hh(c, d, a, b, x[i + 7], 16, -155497632);
            b = md5_hh(b, c, d, a, x[i + 10], 23, -1094730640);
            a = md5_hh(a, b, c, d, x[i + 13], 4, 681279174);
            d = md5_hh(d, a, b, c, x[i + 0], 11, -358537222);
            c = md5_hh(c, d, a, b, x[i + 3], 16, -722521979);
            b = md5_hh(b, c, d, a, x[i + 6], 23, 76029189);
            a = md5_hh(a, b, c, d, x[i + 9], 4, -640364487);
            d = md5_hh(d, a, b, c, x[i + 12], 11, -421815835);
            c = md5_hh(c, d, a, b, x[i + 15], 16, 530742520);
            b = md5_hh(b, c, d, a, x[i + 2], 23, -995338651);
            a = md5_ii(a, b, c, d, x[i + 0], 6, -198630844);
            d = md5_ii(d, a, b, c, x[i + 7], 10, 1126891415);
            c = md5_ii(c, d, a, b, x[i + 14], 15, -1416354905);
            b = md5_ii(b, c, d, a, x[i + 5], 21, -57434055);
            a = md5_ii(a, b, c, d, x[i + 12], 6, 1700485571);
            d = md5_ii(d, a, b, c, x[i + 3], 10, -1894986606);
            c = md5_ii(c, d, a, b, x[i + 10], 15, -1051523);
            b = md5_ii(b, c, d, a, x[i + 1], 21, -2054922799);
            a = md5_ii(a, b, c, d, x[i + 8], 6, 1873313359);
            d = md5_ii(d, a, b, c, x[i + 15], 10, -30611744);
            c = md5_ii(c, d, a, b, x[i + 6], 15, -1560198380);
            b = md5_ii(b, c, d, a, x[i + 13], 21, 1309151649);
            a = md5_ii(a, b, c, d, x[i + 4], 6, -145523070);
            d = md5_ii(d, a, b, c, x[i + 11], 10, -1120210379);
            c = md5_ii(c, d, a, b, x[i + 2], 15, 718787259);
            b = md5_ii(b, c, d, a, x[i + 9], 21, -343485551);
            a = safe_add(a, olda);
            b = safe_add(b, oldb);
            c = safe_add(c, oldc);
            d = safe_add(d, oldd);
        }
        return [ a, b, c, d ];
    };
    var obj = {
        /*
         * These are the functions you'll usually want to call.
         * They take string arguments and return either hex or base-64 encoded
         * strings.
         */
        hexdigest: function(s) {
            return binl2hex(core_md5(str2binl(s), s.length * 8));
        },
        hash: function(s) {
            return binl2str(core_md5(str2binl(s), s.length * 8));
        }
    };
    return obj;
}();

/*
    This program is distributed under the terms of the MIT license.
    Please see the LICENSE file for details.

    Copyright 2006-2008, OGG, LLC
*/
/* jshint undef: true, unused: true:, noarg: true, latedef: true */
/*global document, window, setTimeout, clearTimeout, console,
    ActiveXObject, Base64, MD5, DOMParser */
// from sha1.js
/*global core_hmac_sha1, binb2str, str_hmac_sha1, str_sha1, b64_hmac_sha1*/
/** File: strophe.js
 *  A JavaScript library for XMPP BOSH/XMPP over Websocket.
 *
 *  This is the JavaScript version of the Strophe library.  Since JavaScript
 *  had no facilities for persistent TCP connections, this library uses
 *  Bidirectional-streams Over Synchronous HTTP (BOSH) to emulate
 *  a persistent, stateful, two-way connection to an XMPP server.  More
 *  information on BOSH can be found in XEP 124.
 *
 *  This version of Strophe also works with WebSockets.
 *  For more information on XMPP-over WebSocket see this RFC draft:
 *  http://tools.ietf.org/html/draft-ietf-xmpp-websocket-00
 */
/** PrivateFunction: Function.prototype.bind
 *  Bind a function to an instance.
 *
 *  This Function object extension method creates a bound method similar
 *  to those in Python.  This means that the 'this' object will point
 *  to the instance you want.  See
 *  <a href='https://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/Function/bind'>MDC's bind() documentation</a> and
 *  <a href='http://benjamin.smedbergs.us/blog/2007-01-03/bound-functions-and-function-imports-in-javascript/'>Bound Functions and Function Imports in JavaScript</a>
 *  for a complete explanation.
 *
 *  This extension already exists in some browsers (namely, Firefox 3), but
 *  we provide it to support those that don't.
 *
 *  Parameters:
 *    (Object) obj - The object that will become 'this' in the bound function.
 *    (Object) argN - An option argument that will be prepended to the
 *      arguments given for the function call
 *
 *  Returns:
 *    The bound function.
 */
if (!Function.prototype.bind) {
    Function.prototype.bind = function(obj) {
        var func = this;
        var _slice = Array.prototype.slice;
        var _concat = Array.prototype.concat;
        var _args = _slice.call(arguments, 1);
        return function() {
            return func.apply(obj ? obj : this, _concat.call(_args, _slice.call(arguments, 0)));
        };
    };
}

/** PrivateFunction: Array.prototype.indexOf
 *  Return the index of an object in an array.
 *
 *  This function is not supplied by some JavaScript implementations, so
 *  we provide it if it is missing.  This code is from:
 *  http://developer.mozilla.org/En/Core_JavaScript_1.5_Reference:Objects:Array:indexOf
 *
 *  Parameters:
 *    (Object) elt - The object to look for.
 *    (Integer) from - The index from which to start looking. (optional).
 *
 *  Returns:
 *    The index of elt in the array or -1 if not found.
 */
if (!Array.prototype.indexOf) {
    Array.prototype.indexOf = function(elt) {
        var len = this.length;
        var from = Number(arguments[1]) || 0;
        from = from < 0 ? Math.ceil(from) : Math.floor(from);
        if (from < 0) {
            from += len;
        }
        for (;from < len; from++) {
            if (from in this && this[from] === elt) {
                return from;
            }
        }
        return -1;
    };
}

/* All of the Strophe globals are defined in this special function below so
 * that references to the globals become closures.  This will ensure that
 * on page reload, these references will still be available to callbacks
 * that are still executing.
 */
(function(callback) {
    var Strophe;
    /** Function: $build
 *  Create a Strophe.Builder.
 *  This is an alias for 'new Strophe.Builder(name, attrs)'.
 *
 *  Parameters:
 *    (String) name - The root element name.
 *    (Object) attrs - The attributes for the root element in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
    function $build(name, attrs) {
        return new Strophe.Builder(name, attrs);
    }
    /** Function: $msg
 *  Create a Strophe.Builder with a <message/> element as the root.
 *
 *  Parmaeters:
 *    (Object) attrs - The <message/> element attributes in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
    function $msg(attrs) {
        return new Strophe.Builder("message", attrs);
    }
    /** Function: $iq
 *  Create a Strophe.Builder with an <iq/> element as the root.
 *
 *  Parameters:
 *    (Object) attrs - The <iq/> element attributes in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
    function $iq(attrs) {
        return new Strophe.Builder("iq", attrs);
    }
    /** Function: $pres
 *  Create a Strophe.Builder with a <presence/> element as the root.
 *
 *  Parameters:
 *    (Object) attrs - The <presence/> element attributes in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
    function $pres(attrs) {
        return new Strophe.Builder("presence", attrs);
    }
    /** Class: Strophe
 *  An object container for all Strophe library functions.
 *
 *  This class is just a container for all the objects and constants
 *  used in the library.  It is not meant to be instantiated, but to
 *  provide a namespace for library objects, constants, and functions.
 */
    Strophe = {
        /** Constant: VERSION
     *  The version of the Strophe library. Unreleased builds will have
     *  a version of head-HASH where HASH is a partial revision.
     */
        VERSION: "1.1.3",
        /** Constants: XMPP Namespace Constants
     *  Common namespace constants from the XMPP RFCs and XEPs.
     *
     *  NS.HTTPBIND - HTTP BIND namespace from XEP 124.
     *  NS.BOSH - BOSH namespace from XEP 206.
     *  NS.CLIENT - Main XMPP client namespace.
     *  NS.AUTH - Legacy authentication namespace.
     *  NS.ROSTER - Roster operations namespace.
     *  NS.PROFILE - Profile namespace.
     *  NS.DISCO_INFO - Service discovery info namespace from XEP 30.
     *  NS.DISCO_ITEMS - Service discovery items namespace from XEP 30.
     *  NS.MUC - Multi-User Chat namespace from XEP 45.
     *  NS.SASL - XMPP SASL namespace from RFC 3920.
     *  NS.STREAM - XMPP Streams namespace from RFC 3920.
     *  NS.BIND - XMPP Binding namespace from RFC 3920.
     *  NS.SESSION - XMPP Session namespace from RFC 3920.
     *  NS.XHTML_IM - XHTML-IM namespace from XEP 71.
     *  NS.XHTML - XHTML body namespace from XEP 71.
     */
        NS: {
            HTTPBIND: "http://jabber.org/protocol/httpbind",
            BOSH: "urn:xmpp:xbosh",
            CLIENT: "jabber:client",
            AUTH: "jabber:iq:auth",
            ROSTER: "jabber:iq:roster",
            PROFILE: "jabber:iq:profile",
            DISCO_INFO: "http://jabber.org/protocol/disco#info",
            DISCO_ITEMS: "http://jabber.org/protocol/disco#items",
            MUC: "http://jabber.org/protocol/muc",
            SASL: "urn:ietf:params:xml:ns:xmpp-sasl",
            STREAM: "http://etherx.jabber.org/streams",
            BIND: "urn:ietf:params:xml:ns:xmpp-bind",
            SESSION: "urn:ietf:params:xml:ns:xmpp-session",
            VERSION: "jabber:iq:version",
            STANZAS: "urn:ietf:params:xml:ns:xmpp-stanzas",
            XHTML_IM: "http://jabber.org/protocol/xhtml-im",
            XHTML: "http://www.w3.org/1999/xhtml"
        },
        /** Constants: XHTML_IM Namespace
     *  contains allowed tags, tag attributes, and css properties.
     *  Used in the createHtml function to filter incoming html into the allowed XHTML-IM subset.
     *  See http://xmpp.org/extensions/xep-0071.html#profile-summary for the list of recommended
     *  allowed tags and their attributes.
     */
        XHTML: {
            tags: [ "a", "blockquote", "br", "cite", "em", "img", "li", "ol", "p", "span", "strong", "ul", "body" ],
            attributes: {
                a: [ "href" ],
                blockquote: [ "style" ],
                br: [],
                cite: [ "style" ],
                em: [],
                img: [ "src", "alt", "style", "height", "width" ],
                li: [ "style" ],
                ol: [ "style" ],
                p: [ "style" ],
                span: [ "style" ],
                strong: [],
                ul: [ "style" ],
                body: []
            },
            css: [ "background-color", "color", "font-family", "font-size", "font-style", "font-weight", "margin-left", "margin-right", "text-align", "text-decoration" ],
            validTag: function(tag) {
                for (var i = 0; i < Strophe.XHTML.tags.length; i++) {
                    if (tag == Strophe.XHTML.tags[i]) {
                        return true;
                    }
                }
                return false;
            },
            validAttribute: function(tag, attribute) {
                if (typeof Strophe.XHTML.attributes[tag] !== "undefined" && Strophe.XHTML.attributes[tag].length > 0) {
                    for (var i = 0; i < Strophe.XHTML.attributes[tag].length; i++) {
                        if (attribute == Strophe.XHTML.attributes[tag][i]) {
                            return true;
                        }
                    }
                }
                return false;
            },
            validCSS: function(style) {
                for (var i = 0; i < Strophe.XHTML.css.length; i++) {
                    if (style == Strophe.XHTML.css[i]) {
                        return true;
                    }
                }
                return false;
            }
        },
        /** Constants: Connection Status Constants
     *  Connection status constants for use by the connection handler
     *  callback.
     *
     *  Status.ERROR - An error has occurred
     *  Status.CONNECTING - The connection is currently being made
     *  Status.CONNFAIL - The connection attempt failed
     *  Status.AUTHENTICATING - The connection is authenticating
     *  Status.AUTHFAIL - The authentication attempt failed
     *  Status.CONNECTED - The connection has succeeded
     *  Status.DISCONNECTED - The connection has been terminated
     *  Status.DISCONNECTING - The connection is currently being terminated
     *  Status.ATTACHED - The connection has been attached
     */
        Status: {
            ERROR: 0,
            CONNECTING: 1,
            CONNFAIL: 2,
            AUTHENTICATING: 3,
            AUTHFAIL: 4,
            CONNECTED: 5,
            DISCONNECTED: 6,
            DISCONNECTING: 7,
            ATTACHED: 8
        },
        /** Constants: Log Level Constants
     *  Logging level indicators.
     *
     *  LogLevel.DEBUG - Debug output
     *  LogLevel.INFO - Informational output
     *  LogLevel.WARN - Warnings
     *  LogLevel.ERROR - Errors
     *  LogLevel.FATAL - Fatal errors
     */
        LogLevel: {
            DEBUG: 0,
            INFO: 1,
            WARN: 2,
            ERROR: 3,
            FATAL: 4
        },
        /** PrivateConstants: DOM Element Type Constants
     *  DOM element types.
     *
     *  ElementType.NORMAL - Normal element.
     *  ElementType.TEXT - Text data element.
     *  ElementType.FRAGMENT - XHTML fragment element.
     */
        ElementType: {
            NORMAL: 1,
            TEXT: 3,
            CDATA: 4,
            FRAGMENT: 11
        },
        /** PrivateConstants: Timeout Values
     *  Timeout values for error states.  These values are in seconds.
     *  These should not be changed unless you know exactly what you are
     *  doing.
     *
     *  TIMEOUT - Timeout multiplier. A waiting request will be considered
     *      failed after Math.floor(TIMEOUT * wait) seconds have elapsed.
     *      This defaults to 1.1, and with default wait, 66 seconds.
     *  SECONDARY_TIMEOUT - Secondary timeout multiplier. In cases where
     *      Strophe can detect early failure, it will consider the request
     *      failed if it doesn't return after
     *      Math.floor(SECONDARY_TIMEOUT * wait) seconds have elapsed.
     *      This defaults to 0.1, and with default wait, 6 seconds.
     */
        TIMEOUT: 1.1,
        SECONDARY_TIMEOUT: .1,
        /** Function: addNamespace
     *  This function is used to extend the current namespaces in
     *  Strophe.NS.  It takes a key and a value with the key being the
     *  name of the new namespace, with its actual value.
     *  For example:
     *  Strophe.addNamespace('PUBSUB', "http://jabber.org/protocol/pubsub");
     *
     *  Parameters:
     *    (String) name - The name under which the namespace will be
     *      referenced under Strophe.NS
     *    (String) value - The actual namespace.
     */
        addNamespace: function(name, value) {
            Strophe.NS[name] = value;
        },
        /** Function: forEachChild
     *  Map a function over some or all child elements of a given element.
     *
     *  This is a small convenience function for mapping a function over
     *  some or all of the children of an element.  If elemName is null, all
     *  children will be passed to the function, otherwise only children
     *  whose tag names match elemName will be passed.
     *
     *  Parameters:
     *    (XMLElement) elem - The element to operate on.
     *    (String) elemName - The child element tag name filter.
     *    (Function) func - The function to apply to each child.  This
     *      function should take a single argument, a DOM element.
     */
        forEachChild: function(elem, elemName, func) {
            var i, childNode;
            for (i = 0; i < elem.childNodes.length; i++) {
                childNode = elem.childNodes[i];
                if (childNode.nodeType == Strophe.ElementType.NORMAL && (!elemName || this.isTagEqual(childNode, elemName))) {
                    func(childNode);
                }
            }
        },
        /** Function: isTagEqual
     *  Compare an element's tag name with a string.
     *
     *  This function is case insensitive.
     *
     *  Parameters:
     *    (XMLElement) el - A DOM element.
     *    (String) name - The element name.
     *
     *  Returns:
     *    true if the element's tag name matches _el_, and false
     *    otherwise.
     */
        isTagEqual: function(el, name) {
            return el.tagName.toLowerCase() == name.toLowerCase();
        },
        /** PrivateVariable: _xmlGenerator
     *  _Private_ variable that caches a DOM document to
     *  generate elements.
     */
        _xmlGenerator: null,
        /** PrivateFunction: _makeGenerator
     *  _Private_ function that creates a dummy XML DOM document to serve as
     *  an element and text node generator.
     */
        _makeGenerator: function() {
            var doc;
            // IE9 does implement createDocument(); however, using it will cause the browser to leak memory on page unload.
            // Here, we test for presence of createDocument() plus IE's proprietary documentMode attribute, which would be
            // less than 10 in the case of IE9 and below.
            if (document.implementation.createDocument === undefined || document.implementation.createDocument && document.documentMode && document.documentMode < 10) {
                doc = this._getIEXmlDom();
                doc.appendChild(doc.createElement("strophe"));
            } else {
                doc = document.implementation.createDocument("jabber:client", "strophe", null);
            }
            return doc;
        },
        /** Function: xmlGenerator
     *  Get the DOM document to generate elements.
     *
     *  Returns:
     *    The currently used DOM document.
     */
        xmlGenerator: function() {
            if (!Strophe._xmlGenerator) {
                Strophe._xmlGenerator = Strophe._makeGenerator();
            }
            return Strophe._xmlGenerator;
        },
        /** PrivateFunction: _getIEXmlDom
     *  Gets IE xml doc object
     *
     *  Returns:
     *    A Microsoft XML DOM Object
     *  See Also:
     *    http://msdn.microsoft.com/en-us/library/ms757837%28VS.85%29.aspx
     */
        _getIEXmlDom: function() {
            var doc = null;
            var docStrings = [ "Msxml2.DOMDocument.6.0", "Msxml2.DOMDocument.5.0", "Msxml2.DOMDocument.4.0", "MSXML2.DOMDocument.3.0", "MSXML2.DOMDocument", "MSXML.DOMDocument", "Microsoft.XMLDOM" ];
            for (var d = 0; d < docStrings.length; d++) {
                if (doc === null) {
                    try {
                        doc = new ActiveXObject(docStrings[d]);
                    } catch (e) {
                        doc = null;
                    }
                } else {
                    break;
                }
            }
            return doc;
        },
        /** Function: xmlElement
     *  Create an XML DOM element.
     *
     *  This function creates an XML DOM element correctly across all
     *  implementations. Note that these are not HTML DOM elements, which
     *  aren't appropriate for XMPP stanzas.
     *
     *  Parameters:
     *    (String) name - The name for the element.
     *    (Array|Object) attrs - An optional array or object containing
     *      key/value pairs to use as element attributes. The object should
     *      be in the format {'key': 'value'} or {key: 'value'}. The array
     *      should have the format [['key1', 'value1'], ['key2', 'value2']].
     *    (String) text - The text child data for the element.
     *
     *  Returns:
     *    A new XML DOM element.
     */
        xmlElement: function(name) {
            if (!name) {
                return null;
            }
            var node = Strophe.xmlGenerator().createElement(name);
            // FIXME: this should throw errors if args are the wrong type or
            // there are more than two optional args
            var a, i, k;
            for (a = 1; a < arguments.length; a++) {
                if (!arguments[a]) {
                    continue;
                }
                if (typeof arguments[a] == "string" || typeof arguments[a] == "number") {
                    node.appendChild(Strophe.xmlTextNode(arguments[a]));
                } else if (typeof arguments[a] == "object" && typeof arguments[a].sort == "function") {
                    for (i = 0; i < arguments[a].length; i++) {
                        if (typeof arguments[a][i] == "object" && typeof arguments[a][i].sort == "function") {
                            node.setAttribute(arguments[a][i][0], arguments[a][i][1]);
                        }
                    }
                } else if (typeof arguments[a] == "object") {
                    for (k in arguments[a]) {
                        if (arguments[a].hasOwnProperty(k)) {
                            node.setAttribute(k, arguments[a][k]);
                        }
                    }
                }
            }
            return node;
        },
        /*  Function: xmlescape
     *  Excapes invalid xml characters.
     *
     *  Parameters:
     *     (String) text - text to escape.
     *
     *  Returns:
     *      Escaped text.
     */
        xmlescape: function(text) {
            text = text.replace(/\&/g, "&amp;");
            text = text.replace(/</g, "&lt;");
            text = text.replace(/>/g, "&gt;");
            text = text.replace(/'/g, "&apos;");
            text = text.replace(/"/g, "&quot;");
            return text;
        },
        /** Function: xmlTextNode
     *  Creates an XML DOM text node.
     *
     *  Provides a cross implementation version of document.createTextNode.
     *
     *  Parameters:
     *    (String) text - The content of the text node.
     *
     *  Returns:
     *    A new XML DOM text node.
     */
        xmlTextNode: function(text) {
            return Strophe.xmlGenerator().createTextNode(text);
        },
        /** Function: xmlHtmlNode
     *  Creates an XML DOM html node.
     *
     *  Parameters:
     *    (String) html - The content of the html node.
     *
     *  Returns:
     *    A new XML DOM text node.
     */
        xmlHtmlNode: function(html) {
            var node;
            //ensure text is escaped
            if (window.DOMParser) {
                var parser = new DOMParser();
                node = parser.parseFromString(html, "text/xml");
            } else {
                node = new ActiveXObject("Microsoft.XMLDOM");
                node.async = "false";
                node.loadXML(html);
            }
            return node;
        },
        /** Function: getText
     *  Get the concatenation of all text children of an element.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    A String with the concatenated text of all text element children.
     */
        getText: function(elem) {
            if (!elem) {
                return null;
            }
            var str = "";
            if (elem.childNodes.length === 0 && elem.nodeType == Strophe.ElementType.TEXT) {
                str += elem.nodeValue;
            }
            for (var i = 0; i < elem.childNodes.length; i++) {
                if (elem.childNodes[i].nodeType == Strophe.ElementType.TEXT) {
                    str += elem.childNodes[i].nodeValue;
                }
            }
            return Strophe.xmlescape(str);
        },
        /** Function: copyElement
     *  Copy an XML DOM element.
     *
     *  This function copies a DOM element and all its descendants and returns
     *  the new copy.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    A new, copied DOM element tree.
     */
        copyElement: function(elem) {
            var i, el;
            if (elem.nodeType == Strophe.ElementType.NORMAL) {
                el = Strophe.xmlElement(elem.tagName);
                for (i = 0; i < elem.attributes.length; i++) {
                    el.setAttribute(elem.attributes[i].nodeName.toLowerCase(), elem.attributes[i].value);
                }
                for (i = 0; i < elem.childNodes.length; i++) {
                    el.appendChild(Strophe.copyElement(elem.childNodes[i]));
                }
            } else if (elem.nodeType == Strophe.ElementType.TEXT) {
                el = Strophe.xmlGenerator().createTextNode(elem.nodeValue);
            }
            return el;
        },
        /** Function: createHtml
     *  Copy an HTML DOM element into an XML DOM.
     *
     *  This function copies a DOM element and all its descendants and returns
     *  the new copy.
     *
     *  Parameters:
     *    (HTMLElement) elem - A DOM element.
     *
     *  Returns:
     *    A new, copied DOM element tree.
     */
        createHtml: function(elem) {
            var i, el, j, tag, attribute, value, css, cssAttrs, attr, cssName, cssValue;
            if (elem.nodeType == Strophe.ElementType.NORMAL) {
                tag = elem.nodeName.toLowerCase();
                if (Strophe.XHTML.validTag(tag)) {
                    try {
                        el = Strophe.xmlElement(tag);
                        for (i = 0; i < Strophe.XHTML.attributes[tag].length; i++) {
                            attribute = Strophe.XHTML.attributes[tag][i];
                            value = elem.getAttribute(attribute);
                            if (typeof value == "undefined" || value === null || value === "" || value === false || value === 0) {
                                continue;
                            }
                            if (attribute == "style" && typeof value == "object") {
                                if (typeof value.cssText != "undefined") {
                                    value = value.cssText;
                                }
                            }
                            // filter out invalid css styles
                            if (attribute == "style") {
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
                                    el.setAttribute(attribute, value);
                                }
                            } else {
                                el.setAttribute(attribute, value);
                            }
                        }
                        for (i = 0; i < elem.childNodes.length; i++) {
                            el.appendChild(Strophe.createHtml(elem.childNodes[i]));
                        }
                    } catch (e) {
                        // invalid elements
                        el = Strophe.xmlTextNode("");
                    }
                } else {
                    el = Strophe.xmlGenerator().createDocumentFragment();
                    for (i = 0; i < elem.childNodes.length; i++) {
                        el.appendChild(Strophe.createHtml(elem.childNodes[i]));
                    }
                }
            } else if (elem.nodeType == Strophe.ElementType.FRAGMENT) {
                el = Strophe.xmlGenerator().createDocumentFragment();
                for (i = 0; i < elem.childNodes.length; i++) {
                    el.appendChild(Strophe.createHtml(elem.childNodes[i]));
                }
            } else if (elem.nodeType == Strophe.ElementType.TEXT) {
                el = Strophe.xmlTextNode(elem.nodeValue);
            }
            return el;
        },
        /** Function: escapeNode
     *  Escape the node part (also called local part) of a JID.
     *
     *  Parameters:
     *    (String) node - A node (or local part).
     *
     *  Returns:
     *    An escaped node (or local part).
     */
        escapeNode: function(node) {
            return node.replace(/^\s+|\s+$/g, "").replace(/\\/g, "\\5c").replace(/ /g, "\\20").replace(/\"/g, "\\22").replace(/\&/g, "\\26").replace(/\'/g, "\\27").replace(/\//g, "\\2f").replace(/:/g, "\\3a").replace(/</g, "\\3c").replace(/>/g, "\\3e").replace(/@/g, "\\40");
        },
        /** Function: unescapeNode
     *  Unescape a node part (also called local part) of a JID.
     *
     *  Parameters:
     *    (String) node - A node (or local part).
     *
     *  Returns:
     *    An unescaped node (or local part).
     */
        unescapeNode: function(node) {
            return node.replace(/\\20/g, " ").replace(/\\22/g, '"').replace(/\\26/g, "&").replace(/\\27/g, "'").replace(/\\2f/g, "/").replace(/\\3a/g, ":").replace(/\\3c/g, "<").replace(/\\3e/g, ">").replace(/\\40/g, "@").replace(/\\5c/g, "\\");
        },
        /** Function: getNodeFromJid
     *  Get the node portion of a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the node.
     */
        getNodeFromJid: function(jid) {
            if (jid.indexOf("@") < 0) {
                return null;
            }
            return jid.split("@")[0];
        },
        /** Function: getDomainFromJid
     *  Get the domain portion of a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the domain.
     */
        getDomainFromJid: function(jid) {
            var bare = Strophe.getBareJidFromJid(jid);
            if (bare.indexOf("@") < 0) {
                return bare;
            } else {
                var parts = bare.split("@");
                parts.splice(0, 1);
                return parts.join("@");
            }
        },
        /** Function: getResourceFromJid
     *  Get the resource portion of a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the resource.
     */
        getResourceFromJid: function(jid) {
            var s = jid.split("/");
            if (s.length < 2) {
                return null;
            }
            s.splice(0, 1);
            return s.join("/");
        },
        /** Function: getBareJidFromJid
     *  Get the bare JID from a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the bare JID.
     */
        getBareJidFromJid: function(jid) {
            return jid ? jid.split("/")[0] : null;
        },
        /** Function: log
     *  User overrideable logging function.
     *
     *  This function is called whenever the Strophe library calls any
     *  of the logging functions.  The default implementation of this
     *  function does nothing.  If client code wishes to handle the logging
     *  messages, it should override this with
     *  > Strophe.log = function (level, msg) {
     *  >   (user code here)
     *  > };
     *
     *  Please note that data sent and received over the wire is logged
     *  via Strophe.Connection.rawInput() and Strophe.Connection.rawOutput().
     *
     *  The different levels and their meanings are
     *
     *    DEBUG - Messages useful for debugging purposes.
     *    INFO - Informational messages.  This is mostly information like
     *      'disconnect was called' or 'SASL auth succeeded'.
     *    WARN - Warnings about potential problems.  This is mostly used
     *      to report transient connection errors like request timeouts.
     *    ERROR - Some error occurred.
     *    FATAL - A non-recoverable fatal error occurred.
     *
     *  Parameters:
     *    (Integer) level - The log level of the log message.  This will
     *      be one of the values in Strophe.LogLevel.
     *    (String) msg - The log message.
     */
        /* jshint ignore:start */
        log: function(level, msg) {
            return;
        },
        /* jshint ignore:end */
        /** Function: debug
     *  Log a message at the Strophe.LogLevel.DEBUG level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
        debug: function(msg) {
            this.log(this.LogLevel.DEBUG, msg);
        },
        /** Function: info
     *  Log a message at the Strophe.LogLevel.INFO level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
        info: function(msg) {
            this.log(this.LogLevel.INFO, msg);
        },
        /** Function: warn
     *  Log a message at the Strophe.LogLevel.WARN level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
        warn: function(msg) {
            this.log(this.LogLevel.WARN, msg);
        },
        /** Function: error
     *  Log a message at the Strophe.LogLevel.ERROR level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
        error: function(msg) {
            this.log(this.LogLevel.ERROR, msg);
        },
        /** Function: fatal
     *  Log a message at the Strophe.LogLevel.FATAL level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
        fatal: function(msg) {
            this.log(this.LogLevel.FATAL, msg);
        },
        /** Function: serialize
     *  Render a DOM element and all descendants to a String.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    The serialized element tree as a String.
     */
        serialize: function(elem) {
            var result;
            if (!elem) {
                return null;
            }
            if (typeof elem.tree === "function") {
                elem = elem.tree();
            }
            var nodeName = elem.nodeName;
            var i, child;
            if (elem.getAttribute("_realname")) {
                nodeName = elem.getAttribute("_realname");
            }
            result = "<" + nodeName;
            for (i = 0; i < elem.attributes.length; i++) {
                if (elem.attributes[i].nodeName != "_realname") {
                    result += " " + elem.attributes[i].nodeName.toLowerCase() + "='" + elem.attributes[i].value.replace(/&/g, "&amp;").replace(/\'/g, "&apos;").replace(/>/g, "&gt;").replace(/</g, "&lt;") + "'";
                }
            }
            if (elem.childNodes.length > 0) {
                result += ">";
                for (i = 0; i < elem.childNodes.length; i++) {
                    child = elem.childNodes[i];
                    switch (child.nodeType) {
                      case Strophe.ElementType.NORMAL:
                        // normal element, so recurse
                        result += Strophe.serialize(child);
                        break;

                      case Strophe.ElementType.TEXT:
                        // text element to escape values
                        result += Strophe.xmlescape(child.nodeValue);
                        break;

                      case Strophe.ElementType.CDATA:
                        // cdata section so don't escape values
                        result += "<![CDATA[" + child.nodeValue + "]]>";
                    }
                }
                result += "</" + nodeName + ">";
            } else {
                result += "/>";
            }
            return result;
        },
        /** PrivateVariable: _requestId
     *  _Private_ variable that keeps track of the request ids for
     *  connections.
     */
        _requestId: 0,
        /** PrivateVariable: Strophe.connectionPlugins
     *  _Private_ variable Used to store plugin names that need
     *  initialization on Strophe.Connection construction.
     */
        _connectionPlugins: {},
        /** Function: addConnectionPlugin
     *  Extends the Strophe.Connection object with the given plugin.
     *
     *  Parameters:
     *    (String) name - The name of the extension.
     *    (Object) ptype - The plugin's prototype.
     */
        addConnectionPlugin: function(name, ptype) {
            Strophe._connectionPlugins[name] = ptype;
        }
    };
    /** Class: Strophe.Builder
 *  XML DOM builder.
 *
 *  This object provides an interface similar to JQuery but for building
 *  DOM element easily and rapidly.  All the functions except for toString()
 *  and tree() return the object, so calls can be chained.  Here's an
 *  example using the $iq() builder helper.
 *  > $iq({to: 'you', from: 'me', type: 'get', id: '1'})
 *  >     .c('query', {xmlns: 'strophe:example'})
 *  >     .c('example')
 *  >     .toString()
 *  The above generates this XML fragment
 *  > <iq to='you' from='me' type='get' id='1'>
 *  >   <query xmlns='strophe:example'>
 *  >     <example/>
 *  >   </query>
 *  > </iq>
 *  The corresponding DOM manipulations to get a similar fragment would be
 *  a lot more tedious and probably involve several helper variables.
 *
 *  Since adding children makes new operations operate on the child, up()
 *  is provided to traverse up the tree.  To add two children, do
 *  > builder.c('child1', ...).up().c('child2', ...)
 *  The next operation on the Builder will be relative to the second child.
 */
    /** Constructor: Strophe.Builder
 *  Create a Strophe.Builder object.
 *
 *  The attributes should be passed in object notation.  For example
 *  > var b = new Builder('message', {to: 'you', from: 'me'});
 *  or
 *  > var b = new Builder('messsage', {'xml:lang': 'en'});
 *
 *  Parameters:
 *    (String) name - The name of the root element.
 *    (Object) attrs - The attributes for the root element in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder.
 */
    Strophe.Builder = function(name, attrs) {
        // Set correct namespace for jabber:client elements
        if (name == "presence" || name == "message" || name == "iq") {
            if (attrs && !attrs.xmlns) {
                attrs.xmlns = Strophe.NS.CLIENT;
            } else if (!attrs) {
                attrs = {
                    xmlns: Strophe.NS.CLIENT
                };
            }
        }
        // Holds the tree being built.
        this.nodeTree = Strophe.xmlElement(name, attrs);
        // Points to the current operation node.
        this.node = this.nodeTree;
    };
    Strophe.Builder.prototype = {
        /** Function: tree
     *  Return the DOM tree.
     *
     *  This function returns the current DOM tree as an element object.  This
     *  is suitable for passing to functions like Strophe.Connection.send().
     *
     *  Returns:
     *    The DOM tree as a element object.
     */
        tree: function() {
            return this.nodeTree;
        },
        /** Function: toString
     *  Serialize the DOM tree to a String.
     *
     *  This function returns a string serialization of the current DOM
     *  tree.  It is often used internally to pass data to a
     *  Strophe.Request object.
     *
     *  Returns:
     *    The serialized DOM tree in a String.
     */
        toString: function() {
            return Strophe.serialize(this.nodeTree);
        },
        /** Function: up
     *  Make the current parent element the new current element.
     *
     *  This function is often used after c() to traverse back up the tree.
     *  For example, to add two children to the same element
     *  > builder.c('child1', {}).up().c('child2', {});
     *
     *  Returns:
     *    The Stophe.Builder object.
     */
        up: function() {
            this.node = this.node.parentNode;
            return this;
        },
        /** Function: attrs
     *  Add or modify attributes of the current element.
     *
     *  The attributes should be passed in object notation.  This function
     *  does not move the current element pointer.
     *
     *  Parameters:
     *    (Object) moreattrs - The attributes to add/modify in object notation.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
        attrs: function(moreattrs) {
            for (var k in moreattrs) {
                if (moreattrs.hasOwnProperty(k)) {
                    this.node.setAttribute(k, moreattrs[k]);
                }
            }
            return this;
        },
        /** Function: c
     *  Add a child to the current element and make it the new current
     *  element.
     *
     *  This function moves the current element pointer to the child,
     *  unless text is provided.  If you need to add another child, it
     *  is necessary to use up() to go back to the parent in the tree.
     *
     *  Parameters:
     *    (String) name - The name of the child.
     *    (Object) attrs - The attributes of the child in object notation.
     *    (String) text - The text to add to the child.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
        c: function(name, attrs, text) {
            var child = Strophe.xmlElement(name, attrs, text);
            this.node.appendChild(child);
            if (!text) {
                this.node = child;
            }
            return this;
        },
        /** Function: cnode
     *  Add a child to the current element and make it the new current
     *  element.
     *
     *  This function is the same as c() except that instead of using a
     *  name and an attributes object to create the child it uses an
     *  existing DOM element object.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
        cnode: function(elem) {
            var impNode;
            var xmlGen = Strophe.xmlGenerator();
            try {
                impNode = xmlGen.importNode !== undefined;
            } catch (e) {
                impNode = false;
            }
            var newElem = impNode ? xmlGen.importNode(elem, true) : Strophe.copyElement(elem);
            this.node.appendChild(newElem);
            this.node = newElem;
            return this;
        },
        /** Function: t
     *  Add a child text element.
     *
     *  This *does not* make the child the new current element since there
     *  are no children of text elements.
     *
     *  Parameters:
     *    (String) text - The text data to append to the current element.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
        t: function(text) {
            var child = Strophe.xmlTextNode(text);
            this.node.appendChild(child);
            return this;
        },
        /** Function: h
     *  Replace current element contents with the HTML passed in.
     *
     *  This *does not* make the child the new current element
     *
     *  Parameters:
     *    (String) html - The html to insert as contents of current element.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
        h: function(html) {
            var fragment = document.createElement("body");
            // force the browser to try and fix any invalid HTML tags
            fragment.innerHTML = html;
            // copy cleaned html into an xml dom
            var xhtml = Strophe.createHtml(fragment);
            while (xhtml.childNodes.length > 0) {
                this.node.appendChild(xhtml.childNodes[0]);
            }
            return this;
        }
    };
    /** PrivateClass: Strophe.Handler
 *  _Private_ helper class for managing stanza handlers.
 *
 *  A Strophe.Handler encapsulates a user provided callback function to be
 *  executed when matching stanzas are received by the connection.
 *  Handlers can be either one-off or persistant depending on their
 *  return value. Returning true will cause a Handler to remain active, and
 *  returning false will remove the Handler.
 *
 *  Users will not use Strophe.Handler objects directly, but instead they
 *  will use Strophe.Connection.addHandler() and
 *  Strophe.Connection.deleteHandler().
 */
    /** PrivateConstructor: Strophe.Handler
 *  Create and initialize a new Strophe.Handler.
 *
 *  Parameters:
 *    (Function) handler - A function to be executed when the handler is run.
 *    (String) ns - The namespace to match.
 *    (String) name - The element name to match.
 *    (String) type - The element type to match.
 *    (String) id - The element id attribute to match.
 *    (String) from - The element from attribute to match.
 *    (Object) options - Handler options
 *
 *  Returns:
 *    A new Strophe.Handler object.
 */
    Strophe.Handler = function(handler, ns, name, type, id, from, options) {
        this.handler = handler;
        this.ns = ns;
        this.name = name;
        this.type = type;
        this.id = id;
        this.options = options || {
            matchBare: false
        };
        // default matchBare to false if undefined
        if (!this.options.matchBare) {
            this.options.matchBare = false;
        }
        if (this.options.matchBare) {
            this.from = from ? Strophe.getBareJidFromJid(from) : null;
        } else {
            this.from = from;
        }
        // whether the handler is a user handler or a system handler
        this.user = true;
    };
    Strophe.Handler.prototype = {
        /** PrivateFunction: isMatch
     *  Tests if a stanza matches the Strophe.Handler.
     *
     *  Parameters:
     *    (XMLElement) elem - The XML element to test.
     *
     *  Returns:
     *    true if the stanza matches and false otherwise.
     */
        isMatch: function(elem) {
            var nsMatch;
            var from = null;
            if (this.options.matchBare) {
                from = Strophe.getBareJidFromJid(elem.getAttribute("from"));
            } else {
                from = elem.getAttribute("from");
            }
            nsMatch = false;
            if (!this.ns) {
                nsMatch = true;
            } else {
                var that = this;
                Strophe.forEachChild(elem, null, function(elem) {
                    if (elem.getAttribute("xmlns") == that.ns) {
                        nsMatch = true;
                    }
                });
                nsMatch = nsMatch || elem.getAttribute("xmlns") == this.ns;
            }
            if (nsMatch && (!this.name || Strophe.isTagEqual(elem, this.name)) && (!this.type || elem.getAttribute("type") == this.type) && (!this.id || elem.getAttribute("id") == this.id) && (!this.from || from == this.from)) {
                return true;
            }
            return false;
        },
        /** PrivateFunction: run
     *  Run the callback on a matching stanza.
     *
     *  Parameters:
     *    (XMLElement) elem - The DOM element that triggered the
     *      Strophe.Handler.
     *
     *  Returns:
     *    A boolean indicating if the handler should remain active.
     */
        run: function(elem) {
            var result = null;
            try {
                result = this.handler(elem);
            } catch (e) {
                if (e.sourceURL) {
                    Strophe.fatal("error: " + this.handler + " " + e.sourceURL + ":" + e.line + " - " + e.name + ": " + e.message);
                } else if (e.fileName) {
                    if (typeof console != "undefined") {
                        console.trace();
                        console.error(this.handler, " - error - ", e, e.message);
                    }
                    Strophe.fatal("error: " + this.handler + " " + e.fileName + ":" + e.lineNumber + " - " + e.name + ": " + e.message);
                } else {
                    Strophe.fatal("error: " + e.message + "\n" + e.stack);
                }
                throw e;
            }
            return result;
        },
        /** PrivateFunction: toString
     *  Get a String representation of the Strophe.Handler object.
     *
     *  Returns:
     *    A String.
     */
        toString: function() {
            return "{Handler: " + this.handler + "(" + this.name + "," + this.id + "," + this.ns + ")}";
        }
    };
    /** PrivateClass: Strophe.TimedHandler
 *  _Private_ helper class for managing timed handlers.
 *
 *  A Strophe.TimedHandler encapsulates a user provided callback that
 *  should be called after a certain period of time or at regular
 *  intervals.  The return value of the callback determines whether the
 *  Strophe.TimedHandler will continue to fire.
 *
 *  Users will not use Strophe.TimedHandler objects directly, but instead
 *  they will use Strophe.Connection.addTimedHandler() and
 *  Strophe.Connection.deleteTimedHandler().
 */
    /** PrivateConstructor: Strophe.TimedHandler
 *  Create and initialize a new Strophe.TimedHandler object.
 *
 *  Parameters:
 *    (Integer) period - The number of milliseconds to wait before the
 *      handler is called.
 *    (Function) handler - The callback to run when the handler fires.  This
 *      function should take no arguments.
 *
 *  Returns:
 *    A new Strophe.TimedHandler object.
 */
    Strophe.TimedHandler = function(period, handler) {
        this.period = period;
        this.handler = handler;
        this.lastCalled = new Date().getTime();
        this.user = true;
    };
    Strophe.TimedHandler.prototype = {
        /** PrivateFunction: run
     *  Run the callback for the Strophe.TimedHandler.
     *
     *  Returns:
     *    true if the Strophe.TimedHandler should be called again, and false
     *      otherwise.
     */
        run: function() {
            this.lastCalled = new Date().getTime();
            return this.handler();
        },
        /** PrivateFunction: reset
     *  Reset the last called time for the Strophe.TimedHandler.
     */
        reset: function() {
            this.lastCalled = new Date().getTime();
        },
        /** PrivateFunction: toString
     *  Get a string representation of the Strophe.TimedHandler object.
     *
     *  Returns:
     *    The string representation.
     */
        toString: function() {
            return "{TimedHandler: " + this.handler + "(" + this.period + ")}";
        }
    };
    /** Class: Strophe.Connection
 *  XMPP Connection manager.
 *
 *  This class is the main part of Strophe.  It manages a BOSH connection
 *  to an XMPP server and dispatches events to the user callbacks as
 *  data arrives.  It supports SASL PLAIN, SASL DIGEST-MD5, SASL SCRAM-SHA1
 *  and legacy authentication.
 *
 *  After creating a Strophe.Connection object, the user will typically
 *  call connect() with a user supplied callback to handle connection level
 *  events like authentication failure, disconnection, or connection
 *  complete.
 *
 *  The user will also have several event handlers defined by using
 *  addHandler() and addTimedHandler().  These will allow the user code to
 *  respond to interesting stanzas or do something periodically with the
 *  connection.  These handlers will be active once authentication is
 *  finished.
 *
 *  To send data to the connection, use send().
 */
    /** Constructor: Strophe.Connection
 *  Create and initialize a Strophe.Connection object.
 *
 *  The transport-protocol for this connection will be chosen automatically
 *  based on the given service parameter. URLs starting with "ws://" or
 *  "wss://" will use WebSockets, URLs starting with "http://", "https://"
 *  or without a protocol will use BOSH.
 *
 *  To make Strophe connect to the current host you can leave out the protocol
 *  and host part and just pass the path, e.g.
 *
 *  > var conn = new Strophe.Connection("/http-bind/");
 *
 *  WebSocket options:
 *
 *  If you want to connect to the current host with a WebSocket connection you
 *  can tell Strophe to use WebSockets through a "protocol" attribute in the
 *  optional options parameter. Valid values are "ws" for WebSocket and "wss"
 *  for Secure WebSocket.
 *  So to connect to "wss://CURRENT_HOSTNAME/xmpp-websocket" you would call
 *
 *  > var conn = new Strophe.Connection("/xmpp-websocket/", {protocol: "wss"});
 *
 *  Note that relative URLs _NOT_ starting with a "/" will also include the path
 *  of the current site.
 *
 *  Also because downgrading security is not permitted by browsers, when using
 *  relative URLs both BOSH and WebSocket connections will use their secure
 *  variants if the current connection to the site is also secure (https).
 *
 *  BOSH options:
 *
 *  by adding "sync" to the options, you can control if requests will
 *  be made synchronously or not. The default behaviour is asynchronous.
 *  If you want to make requests synchronous, make "sync" evaluate to true:
 *  > var conn = new Strophe.Connection("/http-bind/", {sync: true});
 *  You can also toggle this on an already established connection:
 *  > conn.options.sync = true;
 *
 *
 *  Parameters:
 *    (String) service - The BOSH or WebSocket service URL.
 *    (Object) options - A hash of configuration options
 *
 *  Returns:
 *    A new Strophe.Connection object.
 */
    Strophe.Connection = function(service, options) {
        // The service URL
        this.service = service;
        // Configuration options
        this.options = options || {};
        var proto = this.options.protocol || "";
        // Select protocal based on service or options
        if (service.indexOf("ws:") === 0 || service.indexOf("wss:") === 0 || proto.indexOf("ws") === 0) {
            this._proto = new Strophe.Websocket(this);
        } else {
            this._proto = new Strophe.Bosh(this);
        }
        /* The connected JID. */
        this.jid = "";
        /* the JIDs domain */
        this.domain = null;
        /* stream:features */
        this.features = null;
        // SASL
        this._sasl_data = {};
        this.do_session = false;
        this.do_bind = false;
        // handler lists
        this.timedHandlers = [];
        this.handlers = [];
        this.removeTimeds = [];
        this.removeHandlers = [];
        this.addTimeds = [];
        this.addHandlers = [];
        this._authentication = {};
        this._idleTimeout = null;
        this._disconnectTimeout = null;
        this.do_authentication = true;
        this.authenticated = false;
        this.disconnecting = false;
        this.connected = false;
        this.errors = 0;
        this.paused = false;
        this._data = [];
        this._uniqueId = 0;
        this._sasl_success_handler = null;
        this._sasl_failure_handler = null;
        this._sasl_challenge_handler = null;
        // Max retries before disconnecting
        this.maxRetries = 5;
        // setup onIdle callback every 1/10th of a second
        this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
        // initialize plugins
        for (var k in Strophe._connectionPlugins) {
            if (Strophe._connectionPlugins.hasOwnProperty(k)) {
                var ptype = Strophe._connectionPlugins[k];
                // jslint complaints about the below line, but this is fine
                var F = function() {};
                // jshint ignore:line
                F.prototype = ptype;
                this[k] = new F();
                this[k].init(this);
            }
        }
    };
    Strophe.Connection.prototype = {
        /** Function: reset
     *  Reset the connection.
     *
     *  This function should be called after a connection is disconnected
     *  before that connection is reused.
     */
        reset: function() {
            this._proto._reset();
            // SASL
            this.do_session = false;
            this.do_bind = false;
            // handler lists
            this.timedHandlers = [];
            this.handlers = [];
            this.removeTimeds = [];
            this.removeHandlers = [];
            this.addTimeds = [];
            this.addHandlers = [];
            this._authentication = {};
            this.authenticated = false;
            this.disconnecting = false;
            this.connected = false;
            this.errors = 0;
            this._requests = [];
            this._uniqueId = 0;
        },
        /** Function: pause
     *  Pause the request manager.
     *
     *  This will prevent Strophe from sending any more requests to the
     *  server.  This is very useful for temporarily pausing
     *  BOSH-Connections while a lot of send() calls are happening quickly.
     *  This causes Strophe to send the data in a single request, saving
     *  many request trips.
     */
        pause: function() {
            this.paused = true;
        },
        /** Function: resume
     *  Resume the request manager.
     *
     *  This resumes after pause() has been called.
     */
        resume: function() {
            this.paused = false;
        },
        /** Function: getUniqueId
     *  Generate a unique ID for use in <iq/> elements.
     *
     *  All <iq/> stanzas are required to have unique id attributes.  This
     *  function makes creating these easy.  Each connection instance has
     *  a counter which starts from zero, and the value of this counter
     *  plus a colon followed by the suffix becomes the unique id. If no
     *  suffix is supplied, the counter is used as the unique id.
     *
     *  Suffixes are used to make debugging easier when reading the stream
     *  data, and their use is recommended.  The counter resets to 0 for
     *  every new connection for the same reason.  For connections to the
     *  same server that authenticate the same way, all the ids should be
     *  the same, which makes it easy to see changes.  This is useful for
     *  automated testing as well.
     *
     *  Parameters:
     *    (String) suffix - A optional suffix to append to the id.
     *
     *  Returns:
     *    A unique string to be used for the id attribute.
     */
        getUniqueId: function(suffix) {
            if (typeof suffix == "string" || typeof suffix == "number") {
                return ++this._uniqueId + ":" + suffix;
            } else {
                return ++this._uniqueId + "";
            }
        },
        /** Function: connect
     *  Starts the connection process.
     *
     *  As the connection process proceeds, the user supplied callback will
     *  be triggered multiple times with status updates.  The callback
     *  should take two arguments - the status code and the error condition.
     *
     *  The status code will be one of the values in the Strophe.Status
     *  constants.  The error condition will be one of the conditions
     *  defined in RFC 3920 or the condition 'strophe-parsererror'.
     *
     *  The Parameters _wait_, _hold_ and _route_ are optional and only relevant
     *  for BOSH connections. Please see XEP 124 for a more detailed explanation
     *  of the optional parameters.
     *
     *  Parameters:
     *    (String) jid - The user's JID.  This may be a bare JID,
     *      or a full JID.  If a node is not supplied, SASL ANONYMOUS
     *      authentication will be attempted.
     *    (String) pass - The user's password.
     *    (Function) callback - The connect callback function.
     *    (Integer) wait - The optional HTTPBIND wait value.  This is the
     *      time the server will wait before returning an empty result for
     *      a request.  The default setting of 60 seconds is recommended.
     *    (Integer) hold - The optional HTTPBIND hold value.  This is the
     *      number of connections the server will hold at one time.  This
     *      should almost always be set to 1 (the default).
     *    (String) route - The optional route value.
     */
        connect: function(jid, pass, callback, wait, hold, route) {
            this.jid = jid;
            /** Variable: authzid
         *  Authorization identity.
         */
            this.authzid = Strophe.getBareJidFromJid(this.jid);
            /** Variable: authcid
         *  Authentication identity (User name).
         */
            this.authcid = Strophe.getNodeFromJid(this.jid);
            /** Variable: pass
         *  Authentication identity (User password).
         */
            this.pass = pass;
            /** Variable: servtype
         *  Digest MD5 compatibility.
         */
            this.servtype = "xmpp";
            this.connect_callback = callback;
            this.disconnecting = false;
            this.connected = false;
            this.authenticated = false;
            this.errors = 0;
            // parse jid for domain
            this.domain = Strophe.getDomainFromJid(this.jid);
            this._changeConnectStatus(Strophe.Status.CONNECTING, null);
            this._proto._connect(wait, hold, route);
        },
        /** Function: attach
     *  Attach to an already created and authenticated BOSH session.
     *
     *  This function is provided to allow Strophe to attach to BOSH
     *  sessions which have been created externally, perhaps by a Web
     *  application.  This is often used to support auto-login type features
     *  without putting user credentials into the page.
     *
     *  Parameters:
     *    (String) jid - The full JID that is bound by the session.
     *    (String) sid - The SID of the BOSH session.
     *    (String) rid - The current RID of the BOSH session.  This RID
     *      will be used by the next request.
     *    (Function) callback The connect callback function.
     *    (Integer) wait - The optional HTTPBIND wait value.  This is the
     *      time the server will wait before returning an empty result for
     *      a request.  The default setting of 60 seconds is recommended.
     *      Other settings will require tweaks to the Strophe.TIMEOUT value.
     *    (Integer) hold - The optional HTTPBIND hold value.  This is the
     *      number of connections the server will hold at one time.  This
     *      should almost always be set to 1 (the default).
     *    (Integer) wind - The optional HTTBIND window value.  This is the
     *      allowed range of request ids that are valid.  The default is 5.
     */
        attach: function(jid, sid, rid, callback, wait, hold, wind) {
            this._proto._attach(jid, sid, rid, callback, wait, hold, wind);
        },
        /** Function: xmlInput
     *  User overrideable function that receives XML data coming into the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Strophe.Connection.xmlInput = function (elem) {
     *  >   (user code)
     *  > };
     *
     *  Due to limitations of current Browsers' XML-Parsers the opening and closing
     *  <stream> tag for WebSocket-Connoctions will be passed as selfclosing here.
     *
     *  BOSH-Connections will have all stanzas wrapped in a <body> tag. See
     *  <Strophe.Bosh.strip> if you want to strip this tag.
     *
     *  Parameters:
     *    (XMLElement) elem - The XML data received by the connection.
     */
        /* jshint unused:false */
        xmlInput: function(elem) {
            return;
        },
        /* jshint unused:true */
        /** Function: xmlOutput
     *  User overrideable function that receives XML data sent to the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Strophe.Connection.xmlOutput = function (elem) {
     *  >   (user code)
     *  > };
     *
     *  Due to limitations of current Browsers' XML-Parsers the opening and closing
     *  <stream> tag for WebSocket-Connoctions will be passed as selfclosing here.
     *
     *  BOSH-Connections will have all stanzas wrapped in a <body> tag. See
     *  <Strophe.Bosh.strip> if you want to strip this tag.
     *
     *  Parameters:
     *    (XMLElement) elem - The XMLdata sent by the connection.
     */
        /* jshint unused:false */
        xmlOutput: function(elem) {
            return;
        },
        /* jshint unused:true */
        /** Function: rawInput
     *  User overrideable function that receives raw data coming into the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Strophe.Connection.rawInput = function (data) {
     *  >   (user code)
     *  > };
     *
     *  Parameters:
     *    (String) data - The data received by the connection.
     */
        /* jshint unused:false */
        rawInput: function(data) {
            return;
        },
        /* jshint unused:true */
        /** Function: rawOutput
     *  User overrideable function that receives raw data sent to the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Strophe.Connection.rawOutput = function (data) {
     *  >   (user code)
     *  > };
     *
     *  Parameters:
     *    (String) data - The data sent by the connection.
     */
        /* jshint unused:false */
        rawOutput: function(data) {
            return;
        },
        /* jshint unused:true */
        /** Function: send
     *  Send a stanza.
     *
     *  This function is called to push data onto the send queue to
     *  go out over the wire.  Whenever a request is sent to the BOSH
     *  server, all pending data is sent and the queue is flushed.
     *
     *  Parameters:
     *    (XMLElement |
     *     [XMLElement] |
     *     Strophe.Builder) elem - The stanza to send.
     */
        send: function(elem) {
            if (elem === null) {
                return;
            }
            if (typeof elem.sort === "function") {
                for (var i = 0; i < elem.length; i++) {
                    this._queueData(elem[i]);
                }
            } else if (typeof elem.tree === "function") {
                this._queueData(elem.tree());
            } else {
                this._queueData(elem);
            }
            this._proto._send();
        },
        /** Function: flush
     *  Immediately send any pending outgoing data.
     *
     *  Normally send() queues outgoing data until the next idle period
     *  (100ms), which optimizes network use in the common cases when
     *  several send()s are called in succession. flush() can be used to
     *  immediately send all pending data.
     */
        flush: function() {
            // cancel the pending idle period and run the idle function
            // immediately
            clearTimeout(this._idleTimeout);
            this._onIdle();
        },
        /** Function: sendIQ
     *  Helper function to send IQ stanzas.
     *
     *  Parameters:
     *    (XMLElement) elem - The stanza to send.
     *    (Function) callback - The callback function for a successful request.
     *    (Function) errback - The callback function for a failed or timed
     *      out request.  On timeout, the stanza will be null.
     *    (Integer) timeout - The time specified in milliseconds for a
     *      timeout to occur.
     *
     *  Returns:
     *    The id used to send the IQ.
    */
        sendIQ: function(elem, callback, errback, timeout) {
            var timeoutHandler = null;
            var that = this;
            if (typeof elem.tree === "function") {
                elem = elem.tree();
            }
            var id = elem.getAttribute("id");
            // inject id if not found
            if (!id) {
                id = this.getUniqueId("sendIQ");
                elem.setAttribute("id", id);
            }
            var handler = this.addHandler(function(stanza) {
                // remove timeout handler if there is one
                if (timeoutHandler) {
                    that.deleteTimedHandler(timeoutHandler);
                }
                var iqtype = stanza.getAttribute("type");
                if (iqtype == "result") {
                    if (callback) {
                        callback(stanza);
                    }
                } else if (iqtype == "error") {
                    if (errback) {
                        errback(stanza);
                    }
                } else {
                    throw {
                        name: "StropheError",
                        message: "Got bad IQ type of " + iqtype
                    };
                }
            }, null, "iq", null, id);
            // if timeout specified, setup timeout handler.
            if (timeout) {
                timeoutHandler = this.addTimedHandler(timeout, function() {
                    // get rid of normal handler
                    that.deleteHandler(handler);
                    // call errback on timeout with null stanza
                    if (errback) {
                        errback(null);
                    }
                    return false;
                });
            }
            this.send(elem);
            return id;
        },
        /** PrivateFunction: _queueData
     *  Queue outgoing data for later sending.  Also ensures that the data
     *  is a DOMElement.
     */
        _queueData: function(element) {
            if (element === null || !element.tagName || !element.childNodes) {
                throw {
                    name: "StropheError",
                    message: "Cannot queue non-DOMElement."
                };
            }
            this._data.push(element);
        },
        /** PrivateFunction: _sendRestart
     *  Send an xmpp:restart stanza.
     */
        _sendRestart: function() {
            this._data.push("restart");
            this._proto._sendRestart();
            this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
        },
        /** Function: addTimedHandler
     *  Add a timed handler to the connection.
     *
     *  This function adds a timed handler.  The provided handler will
     *  be called every period milliseconds until it returns false,
     *  the connection is terminated, or the handler is removed.  Handlers
     *  that wish to continue being invoked should return true.
     *
     *  Because of method binding it is necessary to save the result of
     *  this function if you wish to remove a handler with
     *  deleteTimedHandler().
     *
     *  Note that user handlers are not active until authentication is
     *  successful.
     *
     *  Parameters:
     *    (Integer) period - The period of the handler.
     *    (Function) handler - The callback function.
     *
     *  Returns:
     *    A reference to the handler that can be used to remove it.
     */
        addTimedHandler: function(period, handler) {
            var thand = new Strophe.TimedHandler(period, handler);
            this.addTimeds.push(thand);
            return thand;
        },
        /** Function: deleteTimedHandler
     *  Delete a timed handler for a connection.
     *
     *  This function removes a timed handler from the connection.  The
     *  handRef parameter is *not* the function passed to addTimedHandler(),
     *  but is the reference returned from addTimedHandler().
     *
     *  Parameters:
     *    (Strophe.TimedHandler) handRef - The handler reference.
     */
        deleteTimedHandler: function(handRef) {
            // this must be done in the Idle loop so that we don't change
            // the handlers during iteration
            this.removeTimeds.push(handRef);
        },
        /** Function: addHandler
     *  Add a stanza handler for the connection.
     *
     *  This function adds a stanza handler to the connection.  The
     *  handler callback will be called for any stanza that matches
     *  the parameters.  Note that if multiple parameters are supplied,
     *  they must all match for the handler to be invoked.
     *
     *  The handler will receive the stanza that triggered it as its argument.
     *  The handler should return true if it is to be invoked again;
     *  returning false will remove the handler after it returns.
     *
     *  As a convenience, the ns parameters applies to the top level element
     *  and also any of its immediate children.  This is primarily to make
     *  matching /iq/query elements easy.
     *
     *  The options argument contains handler matching flags that affect how
     *  matches are determined. Currently the only flag is matchBare (a
     *  boolean). When matchBare is true, the from parameter and the from
     *  attribute on the stanza will be matched as bare JIDs instead of
     *  full JIDs. To use this, pass {matchBare: true} as the value of
     *  options. The default value for matchBare is false.
     *
     *  The return value should be saved if you wish to remove the handler
     *  with deleteHandler().
     *
     *  Parameters:
     *    (Function) handler - The user callback.
     *    (String) ns - The namespace to match.
     *    (String) name - The stanza name to match.
     *    (String) type - The stanza type attribute to match.
     *    (String) id - The stanza id attribute to match.
     *    (String) from - The stanza from attribute to match.
     *    (String) options - The handler options
     *
     *  Returns:
     *    A reference to the handler that can be used to remove it.
     */
        addHandler: function(handler, ns, name, type, id, from, options) {
            var hand = new Strophe.Handler(handler, ns, name, type, id, from, options);
            this.addHandlers.push(hand);
            return hand;
        },
        /** Function: deleteHandler
     *  Delete a stanza handler for a connection.
     *
     *  This function removes a stanza handler from the connection.  The
     *  handRef parameter is *not* the function passed to addHandler(),
     *  but is the reference returned from addHandler().
     *
     *  Parameters:
     *    (Strophe.Handler) handRef - The handler reference.
     */
        deleteHandler: function(handRef) {
            // this must be done in the Idle loop so that we don't change
            // the handlers during iteration
            this.removeHandlers.push(handRef);
        },
        /** Function: disconnect
     *  Start the graceful disconnection process.
     *
     *  This function starts the disconnection process.  This process starts
     *  by sending unavailable presence and sending BOSH body of type
     *  terminate.  A timeout handler makes sure that disconnection happens
     *  even if the BOSH server does not respond.
     *
     *  The user supplied connection callback will be notified of the
     *  progress as this process happens.
     *
     *  Parameters:
     *    (String) reason - The reason the disconnect is occuring.
     */
        disconnect: function(reason) {
            this._changeConnectStatus(Strophe.Status.DISCONNECTING, reason);
            Strophe.info("Disconnect was called because: " + reason);
            if (this.connected) {
                var pres = false;
                this.disconnecting = true;
                if (this.authenticated) {
                    pres = $pres({
                        xmlns: Strophe.NS.CLIENT,
                        type: "unavailable"
                    });
                }
                // setup timeout handler
                this._disconnectTimeout = this._addSysTimedHandler(3e3, this._onDisconnectTimeout.bind(this));
                this._proto._disconnect(pres);
            }
        },
        /** PrivateFunction: _changeConnectStatus
     *  _Private_ helper function that makes sure plugins and the user's
     *  callback are notified of connection status changes.
     *
     *  Parameters:
     *    (Integer) status - the new connection status, one of the values
     *      in Strophe.Status
     *    (String) condition - the error condition or null
     */
        _changeConnectStatus: function(status, condition) {
            // notify all plugins listening for status changes
            for (var k in Strophe._connectionPlugins) {
                if (Strophe._connectionPlugins.hasOwnProperty(k)) {
                    var plugin = this[k];
                    if (plugin.statusChanged) {
                        try {
                            plugin.statusChanged(status, condition);
                        } catch (err) {
                            Strophe.error("" + k + " plugin caused an exception " + "changing status: " + err);
                        }
                    }
                }
            }
            // notify the user's callback
            if (this.connect_callback) {
                try {
                    this.connect_callback(status, condition);
                } catch (e) {
                    Strophe.error("User connection callback caused an " + "exception: " + e);
                }
            }
        },
        /** PrivateFunction: _doDisconnect
     *  _Private_ function to disconnect.
     *
     *  This is the last piece of the disconnection logic.  This resets the
     *  connection and alerts the user's connection callback.
     */
        _doDisconnect: function() {
            // Cancel Disconnect Timeout
            if (this._disconnectTimeout !== null) {
                this.deleteTimedHandler(this._disconnectTimeout);
                this._disconnectTimeout = null;
            }
            Strophe.info("_doDisconnect was called");
            this._proto._doDisconnect();
            this.authenticated = false;
            this.disconnecting = false;
            // delete handlers
            this.handlers = [];
            this.timedHandlers = [];
            this.removeTimeds = [];
            this.removeHandlers = [];
            this.addTimeds = [];
            this.addHandlers = [];
            // tell the parent we disconnected
            this._changeConnectStatus(Strophe.Status.DISCONNECTED, null);
            this.connected = false;
        },
        /** PrivateFunction: _dataRecv
     *  _Private_ handler to processes incoming data from the the connection.
     *
     *  Except for _connect_cb handling the initial connection request,
     *  this function handles the incoming data for all requests.  This
     *  function also fires stanza handlers that match each incoming
     *  stanza.
     *
     *  Parameters:
     *    (Strophe.Request) req - The request that has data ready.
     *    (string) req - The stanza a raw string (optiona).
     */
        _dataRecv: function(req, raw) {
            Strophe.info("_dataRecv called");
            var elem = this._proto._reqToData(req);
            if (elem === null) {
                return;
            }
            if (this.xmlInput !== Strophe.Connection.prototype.xmlInput) {
                if (elem.nodeName === this._proto.strip && elem.childNodes.length) {
                    this.xmlInput(elem.childNodes[0]);
                } else {
                    this.xmlInput(elem);
                }
            }
            if (this.rawInput !== Strophe.Connection.prototype.rawInput) {
                if (raw) {
                    this.rawInput(raw);
                } else {
                    this.rawInput(Strophe.serialize(elem));
                }
            }
            // remove handlers scheduled for deletion
            var i, hand;
            while (this.removeHandlers.length > 0) {
                hand = this.removeHandlers.pop();
                i = this.handlers.indexOf(hand);
                if (i >= 0) {
                    this.handlers.splice(i, 1);
                }
            }
            // add handlers scheduled for addition
            while (this.addHandlers.length > 0) {
                this.handlers.push(this.addHandlers.pop());
            }
            // handle graceful disconnect
            if (this.disconnecting && this._proto._emptyQueue()) {
                this._doDisconnect();
                return;
            }
            var typ = elem.getAttribute("type");
            var cond, conflict;
            if (typ !== null && typ == "terminate") {
                // Don't process stanzas that come in after disconnect
                if (this.disconnecting) {
                    return;
                }
                // an error occurred
                cond = elem.getAttribute("condition");
                conflict = elem.getElementsByTagName("conflict");
                if (cond !== null) {
                    if (cond == "remote-stream-error" && conflict.length > 0) {
                        cond = "conflict";
                    }
                    this._changeConnectStatus(Strophe.Status.CONNFAIL, cond);
                } else {
                    this._changeConnectStatus(Strophe.Status.CONNFAIL, "unknown");
                }
                this.disconnect("unknown stream-error");
                return;
            }
            // send each incoming stanza through the handler chain
            var that = this;
            Strophe.forEachChild(elem, null, function(child) {
                var i, newList;
                // process handlers
                newList = that.handlers;
                that.handlers = [];
                for (i = 0; i < newList.length; i++) {
                    var hand = newList[i];
                    // encapsulate 'handler.run' not to lose the whole handler list if
                    // one of the handlers throws an exception
                    try {
                        if (hand.isMatch(child) && (that.authenticated || !hand.user)) {
                            if (hand.run(child)) {
                                that.handlers.push(hand);
                            }
                        } else {
                            that.handlers.push(hand);
                        }
                    } catch (e) {
                        // if the handler throws an exception, we consider it as false
                        Strophe.warn("Removing Strophe handlers due to uncaught exception: " + e.message);
                    }
                }
            });
        },
        /** Attribute: mechanisms
     *  SASL Mechanisms available for Conncection.
     */
        mechanisms: {},
        /** PrivateFunction: _connect_cb
     *  _Private_ handler for initial connection request.
     *
     *  This handler is used to process the initial connection request
     *  response from the BOSH server. It is used to set up authentication
     *  handlers and start the authentication process.
     *
     *  SASL authentication will be attempted if available, otherwise
     *  the code will fall back to legacy authentication.
     *
     *  Parameters:
     *    (Strophe.Request) req - The current request.
     *    (Function) _callback - low level (xmpp) connect callback function.
     *      Useful for plugins with their own xmpp connect callback (when their)
     *      want to do something special).
     */
        _connect_cb: function(req, _callback, raw) {
            Strophe.info("_connect_cb was called");
            this.connected = true;
            var bodyWrap = this._proto._reqToData(req);
            if (!bodyWrap) {
                return;
            }
            if (this.xmlInput !== Strophe.Connection.prototype.xmlInput) {
                if (bodyWrap.nodeName === this._proto.strip && bodyWrap.childNodes.length) {
                    this.xmlInput(bodyWrap.childNodes[0]);
                } else {
                    this.xmlInput(bodyWrap);
                }
            }
            if (this.rawInput !== Strophe.Connection.prototype.rawInput) {
                if (raw) {
                    this.rawInput(raw);
                } else {
                    this.rawInput(Strophe.serialize(bodyWrap));
                }
            }
            var conncheck = this._proto._connect_cb(bodyWrap);
            if (conncheck === Strophe.Status.CONNFAIL) {
                return;
            }
            this._authentication.sasl_scram_sha1 = false;
            this._authentication.sasl_plain = false;
            this._authentication.sasl_digest_md5 = false;
            this._authentication.sasl_anonymous = false;
            this._authentication.legacy_auth = false;
            // Check for the stream:features tag
            var hasFeatures = bodyWrap.getElementsByTagName("stream:features").length > 0;
            if (!hasFeatures) {
                hasFeatures = bodyWrap.getElementsByTagName("features").length > 0;
            }
            var mechanisms = bodyWrap.getElementsByTagName("mechanism");
            var matched = [];
            var i, mech, found_authentication = false;
            if (!hasFeatures) {
                this._proto._no_auth_received(_callback);
                return;
            }
            if (mechanisms.length > 0) {
                for (i = 0; i < mechanisms.length; i++) {
                    mech = Strophe.getText(mechanisms[i]);
                    if (this.mechanisms[mech]) matched.push(this.mechanisms[mech]);
                }
            }
            this._authentication.legacy_auth = bodyWrap.getElementsByTagName("auth").length > 0;
            found_authentication = this._authentication.legacy_auth || matched.length > 0;
            if (!found_authentication) {
                this._proto._no_auth_received(_callback);
                return;
            }
            if (this.do_authentication !== false) this.authenticate(matched);
        },
        /** Function: authenticate
     * Set up authentication
     *
     *  Contiunues the initial connection request by setting up authentication
     *  handlers and start the authentication process.
     *
     *  SASL authentication will be attempted if available, otherwise
     *  the code will fall back to legacy authentication.
     *
     */
        authenticate: function(matched) {
            var i;
            // Sorting matched mechanisms according to priority.
            for (i = 0; i < matched.length - 1; ++i) {
                var higher = i;
                for (var j = i + 1; j < matched.length; ++j) {
                    if (matched[j].prototype.priority > matched[higher].prototype.priority) {
                        higher = j;
                    }
                }
                if (higher != i) {
                    var swap = matched[i];
                    matched[i] = matched[higher];
                    matched[higher] = swap;
                }
            }
            // run each mechanism
            var mechanism_found = false;
            for (i = 0; i < matched.length; ++i) {
                if (!matched[i].test(this)) continue;
                this._sasl_success_handler = this._addSysHandler(this._sasl_success_cb.bind(this), null, "success", null, null);
                this._sasl_failure_handler = this._addSysHandler(this._sasl_failure_cb.bind(this), null, "failure", null, null);
                this._sasl_challenge_handler = this._addSysHandler(this._sasl_challenge_cb.bind(this), null, "challenge", null, null);
                this._sasl_mechanism = new matched[i]();
                this._sasl_mechanism.onStart(this);
                var request_auth_exchange = $build("auth", {
                    xmlns: Strophe.NS.SASL,
                    mechanism: this._sasl_mechanism.name
                });
                if (this._sasl_mechanism.isClientFirst) {
                    var response = this._sasl_mechanism.onChallenge(this, null);
                    request_auth_exchange.t(Base64.encode(response));
                }
                this.send(request_auth_exchange.tree());
                mechanism_found = true;
                break;
            }
            if (!mechanism_found) {
                // if none of the mechanism worked
                if (Strophe.getNodeFromJid(this.jid) === null) {
                    // we don't have a node, which is required for non-anonymous
                    // client connections
                    this._changeConnectStatus(Strophe.Status.CONNFAIL, "x-strophe-bad-non-anon-jid");
                    this.disconnect("x-strophe-bad-non-anon-jid");
                } else {
                    // fall back to legacy authentication
                    this._changeConnectStatus(Strophe.Status.AUTHENTICATING, null);
                    this._addSysHandler(this._auth1_cb.bind(this), null, null, null, "_auth_1");
                    this.send($iq({
                        type: "get",
                        to: this.domain,
                        id: "_auth_1"
                    }).c("query", {
                        xmlns: Strophe.NS.AUTH
                    }).c("username", {}).t(Strophe.getNodeFromJid(this.jid)).tree());
                }
            }
        },
        _sasl_challenge_cb: function(elem) {
            var challenge = Base64.decode(Strophe.getText(elem));
            var response = this._sasl_mechanism.onChallenge(this, challenge);
            var stanza = $build("response", {
                xmlns: Strophe.NS.SASL
            });
            if (response !== "") {
                stanza.t(Base64.encode(response));
            }
            this.send(stanza.tree());
            return true;
        },
        /** PrivateFunction: _auth1_cb
     *  _Private_ handler for legacy authentication.
     *
     *  This handler is called in response to the initial <iq type='get'/>
     *  for legacy authentication.  It builds an authentication <iq/> and
     *  sends it, creating a handler (calling back to _auth2_cb()) to
     *  handle the result
     *
     *  Parameters:
     *    (XMLElement) elem - The stanza that triggered the callback.
     *
     *  Returns:
     *    false to remove the handler.
     */
        /* jshint unused:false */
        _auth1_cb: function(elem) {
            // build plaintext auth iq
            var iq = $iq({
                type: "set",
                id: "_auth_2"
            }).c("query", {
                xmlns: Strophe.NS.AUTH
            }).c("username", {}).t(Strophe.getNodeFromJid(this.jid)).up().c("password").t(this.pass);
            if (!Strophe.getResourceFromJid(this.jid)) {
                // since the user has not supplied a resource, we pick
                // a default one here.  unlike other auth methods, the server
                // cannot do this for us.
                this.jid = Strophe.getBareJidFromJid(this.jid) + "/strophe";
            }
            iq.up().c("resource", {}).t(Strophe.getResourceFromJid(this.jid));
            this._addSysHandler(this._auth2_cb.bind(this), null, null, null, "_auth_2");
            this.send(iq.tree());
            return false;
        },
        /* jshint unused:true */
        /** PrivateFunction: _sasl_success_cb
     *  _Private_ handler for succesful SASL authentication.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
        _sasl_success_cb: function(elem) {
            if (this._sasl_data["server-signature"]) {
                var serverSignature;
                var success = Base64.decode(Strophe.getText(elem));
                var attribMatch = /([a-z]+)=([^,]+)(,|$)/;
                var matches = success.match(attribMatch);
                if (matches[1] == "v") {
                    serverSignature = matches[2];
                }
                if (serverSignature != this._sasl_data["server-signature"]) {
                    // remove old handlers
                    this.deleteHandler(this._sasl_failure_handler);
                    this._sasl_failure_handler = null;
                    if (this._sasl_challenge_handler) {
                        this.deleteHandler(this._sasl_challenge_handler);
                        this._sasl_challenge_handler = null;
                    }
                    this._sasl_data = {};
                    return this._sasl_failure_cb(null);
                }
            }
            Strophe.info("SASL authentication succeeded.");
            if (this._sasl_mechanism) this._sasl_mechanism.onSuccess();
            // remove old handlers
            this.deleteHandler(this._sasl_failure_handler);
            this._sasl_failure_handler = null;
            if (this._sasl_challenge_handler) {
                this.deleteHandler(this._sasl_challenge_handler);
                this._sasl_challenge_handler = null;
            }
            this._addSysHandler(this._sasl_auth1_cb.bind(this), null, "stream:features", null, null);
            // we must send an xmpp:restart now
            this._sendRestart();
            return false;
        },
        /** PrivateFunction: _sasl_auth1_cb
     *  _Private_ handler to start stream binding.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
        _sasl_auth1_cb: function(elem) {
            // save stream:features for future usage
            this.features = elem;
            var i, child;
            for (i = 0; i < elem.childNodes.length; i++) {
                child = elem.childNodes[i];
                if (child.nodeName == "bind") {
                    this.do_bind = true;
                }
                if (child.nodeName == "session") {
                    this.do_session = true;
                }
            }
            if (!this.do_bind) {
                this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
                return false;
            } else {
                this._addSysHandler(this._sasl_bind_cb.bind(this), null, null, null, "_bind_auth_2");
                var resource = Strophe.getResourceFromJid(this.jid);
                if (resource) {
                    this.send($iq({
                        type: "set",
                        id: "_bind_auth_2"
                    }).c("bind", {
                        xmlns: Strophe.NS.BIND
                    }).c("resource", {}).t(resource).tree());
                } else {
                    this.send($iq({
                        type: "set",
                        id: "_bind_auth_2"
                    }).c("bind", {
                        xmlns: Strophe.NS.BIND
                    }).tree());
                }
            }
            return false;
        },
        /** PrivateFunction: _sasl_bind_cb
     *  _Private_ handler for binding result and session start.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
        _sasl_bind_cb: function(elem) {
            if (elem.getAttribute("type") == "error") {
                Strophe.info("SASL binding failed.");
                var conflict = elem.getElementsByTagName("conflict"), condition;
                if (conflict.length > 0) {
                    condition = "conflict";
                }
                this._changeConnectStatus(Strophe.Status.AUTHFAIL, condition);
                return false;
            }
            // TODO - need to grab errors
            var bind = elem.getElementsByTagName("bind");
            var jidNode;
            if (bind.length > 0) {
                // Grab jid
                jidNode = bind[0].getElementsByTagName("jid");
                if (jidNode.length > 0) {
                    this.jid = Strophe.getText(jidNode[0]);
                    if (this.do_session) {
                        this._addSysHandler(this._sasl_session_cb.bind(this), null, null, null, "_session_auth_2");
                        this.send($iq({
                            type: "set",
                            id: "_session_auth_2"
                        }).c("session", {
                            xmlns: Strophe.NS.SESSION
                        }).tree());
                    } else {
                        this.authenticated = true;
                        this._changeConnectStatus(Strophe.Status.CONNECTED, null);
                    }
                }
            } else {
                Strophe.info("SASL binding failed.");
                this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
                return false;
            }
        },
        /** PrivateFunction: _sasl_session_cb
     *  _Private_ handler to finish successful SASL connection.
     *
     *  This sets Connection.authenticated to true on success, which
     *  starts the processing of user handlers.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
        _sasl_session_cb: function(elem) {
            if (elem.getAttribute("type") == "result") {
                this.authenticated = true;
                this._changeConnectStatus(Strophe.Status.CONNECTED, null);
            } else if (elem.getAttribute("type") == "error") {
                Strophe.info("Session creation failed.");
                this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
                return false;
            }
            return false;
        },
        /** PrivateFunction: _sasl_failure_cb
     *  _Private_ handler for SASL authentication failure.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
        /* jshint unused:false */
        _sasl_failure_cb: function(elem) {
            // delete unneeded handlers
            if (this._sasl_success_handler) {
                this.deleteHandler(this._sasl_success_handler);
                this._sasl_success_handler = null;
            }
            if (this._sasl_challenge_handler) {
                this.deleteHandler(this._sasl_challenge_handler);
                this._sasl_challenge_handler = null;
            }
            if (this._sasl_mechanism) this._sasl_mechanism.onFailure();
            this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
            return false;
        },
        /* jshint unused:true */
        /** PrivateFunction: _auth2_cb
     *  _Private_ handler to finish legacy authentication.
     *
     *  This handler is called when the result from the jabber:iq:auth
     *  <iq/> stanza is returned.
     *
     *  Parameters:
     *    (XMLElement) elem - The stanza that triggered the callback.
     *
     *  Returns:
     *    false to remove the handler.
     */
        _auth2_cb: function(elem) {
            if (elem.getAttribute("type") == "result") {
                this.authenticated = true;
                this._changeConnectStatus(Strophe.Status.CONNECTED, null);
            } else if (elem.getAttribute("type") == "error") {
                this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
                this.disconnect("authentication failed");
            }
            return false;
        },
        /** PrivateFunction: _addSysTimedHandler
     *  _Private_ function to add a system level timed handler.
     *
     *  This function is used to add a Strophe.TimedHandler for the
     *  library code.  System timed handlers are allowed to run before
     *  authentication is complete.
     *
     *  Parameters:
     *    (Integer) period - The period of the handler.
     *    (Function) handler - The callback function.
     */
        _addSysTimedHandler: function(period, handler) {
            var thand = new Strophe.TimedHandler(period, handler);
            thand.user = false;
            this.addTimeds.push(thand);
            return thand;
        },
        /** PrivateFunction: _addSysHandler
     *  _Private_ function to add a system level stanza handler.
     *
     *  This function is used to add a Strophe.Handler for the
     *  library code.  System stanza handlers are allowed to run before
     *  authentication is complete.
     *
     *  Parameters:
     *    (Function) handler - The callback function.
     *    (String) ns - The namespace to match.
     *    (String) name - The stanza name to match.
     *    (String) type - The stanza type attribute to match.
     *    (String) id - The stanza id attribute to match.
     */
        _addSysHandler: function(handler, ns, name, type, id) {
            var hand = new Strophe.Handler(handler, ns, name, type, id);
            hand.user = false;
            this.addHandlers.push(hand);
            return hand;
        },
        /** PrivateFunction: _onDisconnectTimeout
     *  _Private_ timeout handler for handling non-graceful disconnection.
     *
     *  If the graceful disconnect process does not complete within the
     *  time allotted, this handler finishes the disconnect anyway.
     *
     *  Returns:
     *    false to remove the handler.
     */
        _onDisconnectTimeout: function() {
            Strophe.info("_onDisconnectTimeout was called");
            this._proto._onDisconnectTimeout();
            // actually disconnect
            this._doDisconnect();
            return false;
        },
        /** PrivateFunction: _onIdle
     *  _Private_ handler to process events during idle cycle.
     *
     *  This handler is called every 100ms to fire timed handlers that
     *  are ready and keep poll requests going.
     */
        _onIdle: function() {
            var i, thand, since, newList;
            // add timed handlers scheduled for addition
            // NOTE: we add before remove in the case a timed handler is
            // added and then deleted before the next _onIdle() call.
            while (this.addTimeds.length > 0) {
                this.timedHandlers.push(this.addTimeds.pop());
            }
            // remove timed handlers that have been scheduled for deletion
            while (this.removeTimeds.length > 0) {
                thand = this.removeTimeds.pop();
                i = this.timedHandlers.indexOf(thand);
                if (i >= 0) {
                    this.timedHandlers.splice(i, 1);
                }
            }
            // call ready timed handlers
            var now = new Date().getTime();
            newList = [];
            for (i = 0; i < this.timedHandlers.length; i++) {
                thand = this.timedHandlers[i];
                if (this.authenticated || !thand.user) {
                    since = thand.lastCalled + thand.period;
                    if (since - now <= 0) {
                        if (thand.run()) {
                            newList.push(thand);
                        }
                    } else {
                        newList.push(thand);
                    }
                }
            }
            this.timedHandlers = newList;
            clearTimeout(this._idleTimeout);
            this._proto._onIdle();
            // reactivate the timer only if connected
            if (this.connected) {
                this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
            }
        }
    };
    if (callback) {
        callback(Strophe, $build, $msg, $iq, $pres);
    }
    /** Class: Strophe.SASLMechanism
 *
 *  encapsulates SASL authentication mechanisms.
 *
 *  User code may override the priority for each mechanism or disable it completely.
 *  See <priority> for information about changing priority and <test> for informatian on
 *  how to disable a mechanism.
 *
 *  By default, all mechanisms are enabled and the priorities are
 *
 *  SCRAM-SHA1 - 40
 *  DIGEST-MD5 - 30
 *  Plain - 20
 */
    /**
 * PrivateConstructor: Strophe.SASLMechanism
 * SASL auth mechanism abstraction.
 *
 *  Parameters:
 *    (String) name - SASL Mechanism name.
 *    (Boolean) isClientFirst - If client should send response first without challenge.
 *    (Number) priority - Priority.
 *
 *  Returns:
 *    A new Strophe.SASLMechanism object.
 */
    Strophe.SASLMechanism = function(name, isClientFirst, priority) {
        /** PrivateVariable: name
   *  Mechanism name.
   */
        this.name = name;
        /** PrivateVariable: isClientFirst
   *  If client sends response without initial server challenge.
   */
        this.isClientFirst = isClientFirst;
        /** Variable: priority
   *  Determines which <SASLMechanism> is chosen for authentication (Higher is better).
   *  Users may override this to prioritize mechanisms differently.
   *
   *  In the default configuration the priorities are
   *
   *  SCRAM-SHA1 - 40
   *  DIGEST-MD5 - 30
   *  Plain - 20
   *
   *  Example: (This will cause Strophe to choose the mechanism that the server sent first)
   *
   *  > Strophe.SASLMD5.priority = Strophe.SASLSHA1.priority;
   *
   *  See <SASL mechanisms> for a list of available mechanisms.
   *
   */
        this.priority = priority;
    };
    Strophe.SASLMechanism.prototype = {
        /**
   *  Function: test
   *  Checks if mechanism able to run.
   *  To disable a mechanism, make this return false;
   *
   *  To disable plain authentication run
   *  > Strophe.SASLPlain.test = function() {
   *  >   return false;
   *  > }
   *
   *  See <SASL mechanisms> for a list of available mechanisms.
   *
   *  Parameters:
   *    (Strophe.Connection) connection - Target Connection.
   *
   *  Returns:
   *    (Boolean) If mechanism was able to run.
   */
        /* jshint unused:false */
        test: function(connection) {
            return true;
        },
        /* jshint unused:true */
        /** PrivateFunction: onStart
   *  Called before starting mechanism on some connection.
   *
   *  Parameters:
   *    (Strophe.Connection) connection - Target Connection.
   */
        onStart: function(connection) {
            this._connection = connection;
        },
        /** PrivateFunction: onChallenge
   *  Called by protocol implementation on incoming challenge. If client is
   *  first (isClientFirst == true) challenge will be null on the first call.
   *
   *  Parameters:
   *    (Strophe.Connection) connection - Target Connection.
   *    (String) challenge - current challenge to handle.
   *
   *  Returns:
   *    (String) Mechanism response.
   */
        /* jshint unused:false */
        onChallenge: function(connection, challenge) {
            throw new Error("You should implement challenge handling!");
        },
        /* jshint unused:true */
        /** PrivateFunction: onFailure
   *  Protocol informs mechanism implementation about SASL failure.
   */
        onFailure: function() {
            this._connection = null;
        },
        /** PrivateFunction: onSuccess
   *  Protocol informs mechanism implementation about SASL success.
   */
        onSuccess: function() {
            this._connection = null;
        }
    };
    /** Constants: SASL mechanisms
   *  Available authentication mechanisms
   *
   *  Strophe.SASLAnonymous - SASL Anonymous authentication.
   *  Strophe.SASLPlain - SASL Plain authentication.
   *  Strophe.SASLMD5 - SASL Digest-MD5 authentication
   *  Strophe.SASLSHA1 - SASL SCRAM-SHA1 authentication
   */
    // Building SASL callbacks
    /** PrivateConstructor: SASLAnonymous
 *  SASL Anonymous authentication.
 */
    Strophe.SASLAnonymous = function() {};
    Strophe.SASLAnonymous.prototype = new Strophe.SASLMechanism("ANONYMOUS", false, 10);
    Strophe.SASLAnonymous.test = function(connection) {
        return connection.authcid === null;
    };
    Strophe.Connection.prototype.mechanisms[Strophe.SASLAnonymous.prototype.name] = Strophe.SASLAnonymous;
    /** PrivateConstructor: SASLPlain
 *  SASL Plain authentication.
 */
    Strophe.SASLPlain = function() {};
    Strophe.SASLPlain.prototype = new Strophe.SASLMechanism("PLAIN", true, 20);
    Strophe.SASLPlain.test = function(connection) {
        return connection.authcid !== null;
    };
    Strophe.SASLPlain.prototype.onChallenge = function(connection) {
        var auth_str = connection.authzid;
        auth_str = auth_str + "\x00";
        auth_str = auth_str + connection.authcid;
        auth_str = auth_str + "\x00";
        auth_str = auth_str + connection.pass;
        return auth_str;
    };
    Strophe.Connection.prototype.mechanisms[Strophe.SASLPlain.prototype.name] = Strophe.SASLPlain;
    /** PrivateConstructor: SASLSHA1
 *  SASL SCRAM SHA 1 authentication.
 */
    Strophe.SASLSHA1 = function() {};
    /* TEST:
 * This is a simple example of a SCRAM-SHA-1 authentication exchange
 * when the client doesn't support channel bindings (username 'user' and
 * password 'pencil' are used):
 *
 * C: n,,n=user,r=fyko+d2lbbFgONRv9qkxdawL
 * S: r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,
 * i=4096
 * C: c=biws,r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,
 * p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=
 * S: v=rmF9pqV8S7suAoZWja4dJRkFsKQ=
 *
 */
    Strophe.SASLSHA1.prototype = new Strophe.SASLMechanism("SCRAM-SHA-1", true, 40);
    Strophe.SASLSHA1.test = function(connection) {
        return connection.authcid !== null;
    };
    Strophe.SASLSHA1.prototype.onChallenge = function(connection, challenge, test_cnonce) {
        var cnonce = test_cnonce || MD5.hexdigest(Math.random() * 1234567890);
        var auth_str = "n=" + connection.authcid;
        auth_str += ",r=";
        auth_str += cnonce;
        connection._sasl_data.cnonce = cnonce;
        connection._sasl_data["client-first-message-bare"] = auth_str;
        auth_str = "n,," + auth_str;
        this.onChallenge = function(connection, challenge) {
            var nonce, salt, iter, Hi, U, U_old, i, k;
            var clientKey, serverKey, clientSignature;
            var responseText = "c=biws,";
            var authMessage = connection._sasl_data["client-first-message-bare"] + "," + challenge + ",";
            var cnonce = connection._sasl_data.cnonce;
            var attribMatch = /([a-z]+)=([^,]+)(,|$)/;
            while (challenge.match(attribMatch)) {
                var matches = challenge.match(attribMatch);
                challenge = challenge.replace(matches[0], "");
                switch (matches[1]) {
                  case "r":
                    nonce = matches[2];
                    break;

                  case "s":
                    salt = matches[2];
                    break;

                  case "i":
                    iter = matches[2];
                    break;
                }
            }
            if (nonce.substr(0, cnonce.length) !== cnonce) {
                connection._sasl_data = {};
                return connection._sasl_failure_cb();
            }
            responseText += "r=" + nonce;
            authMessage += responseText;
            salt = Base64.decode(salt);
            salt += "\x00\x00\x00";
            Hi = U_old = core_hmac_sha1(connection.pass, salt);
            for (i = 1; i < iter; i++) {
                U = core_hmac_sha1(connection.pass, binb2str(U_old));
                for (k = 0; k < 5; k++) {
                    Hi[k] ^= U[k];
                }
                U_old = U;
            }
            Hi = binb2str(Hi);
            clientKey = core_hmac_sha1(Hi, "Client Key");
            serverKey = str_hmac_sha1(Hi, "Server Key");
            clientSignature = core_hmac_sha1(str_sha1(binb2str(clientKey)), authMessage);
            connection._sasl_data["server-signature"] = b64_hmac_sha1(serverKey, authMessage);
            for (k = 0; k < 5; k++) {
                clientKey[k] ^= clientSignature[k];
            }
            responseText += ",p=" + Base64.encode(binb2str(clientKey));
            return responseText;
        }.bind(this);
        return auth_str;
    };
    Strophe.Connection.prototype.mechanisms[Strophe.SASLSHA1.prototype.name] = Strophe.SASLSHA1;
    /** PrivateConstructor: SASLMD5
 *  SASL DIGEST MD5 authentication.
 */
    Strophe.SASLMD5 = function() {};
    Strophe.SASLMD5.prototype = new Strophe.SASLMechanism("DIGEST-MD5", false, 30);
    Strophe.SASLMD5.test = function(connection) {
        return connection.authcid !== null;
    };
    /** PrivateFunction: _quote
 *  _Private_ utility function to backslash escape and quote strings.
 *
 *  Parameters:
 *    (String) str - The string to be quoted.
 *
 *  Returns:
 *    quoted string
 */
    Strophe.SASLMD5.prototype._quote = function(str) {
        return '"' + str.replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"';
    };
    Strophe.SASLMD5.prototype.onChallenge = function(connection, challenge, test_cnonce) {
        var attribMatch = /([a-z]+)=("[^"]+"|[^,"]+)(?:,|$)/;
        var cnonce = test_cnonce || MD5.hexdigest("" + Math.random() * 1234567890);
        var realm = "";
        var host = null;
        var nonce = "";
        var qop = "";
        var matches;
        while (challenge.match(attribMatch)) {
            matches = challenge.match(attribMatch);
            challenge = challenge.replace(matches[0], "");
            matches[2] = matches[2].replace(/^"(.+)"$/, "$1");
            switch (matches[1]) {
              case "realm":
                realm = matches[2];
                break;

              case "nonce":
                nonce = matches[2];
                break;

              case "qop":
                qop = matches[2];
                break;

              case "host":
                host = matches[2];
                break;
            }
        }
        var digest_uri = connection.servtype + "/" + connection.domain;
        if (host !== null) {
            digest_uri = digest_uri + "/" + host;
        }
        var A1 = MD5.hash(connection.authcid + ":" + realm + ":" + this._connection.pass) + ":" + nonce + ":" + cnonce;
        var A2 = "AUTHENTICATE:" + digest_uri;
        var responseText = "";
        responseText += "charset=utf-8,";
        responseText += "username=" + this._quote(connection.authcid) + ",";
        responseText += "realm=" + this._quote(realm) + ",";
        responseText += "nonce=" + this._quote(nonce) + ",";
        responseText += "nc=00000001,";
        responseText += "cnonce=" + this._quote(cnonce) + ",";
        responseText += "digest-uri=" + this._quote(digest_uri) + ",";
        responseText += "response=" + MD5.hexdigest(MD5.hexdigest(A1) + ":" + nonce + ":00000001:" + cnonce + ":auth:" + MD5.hexdigest(A2)) + ",";
        responseText += "qop=auth";
        this.onChallenge = function() {
            return "";
        }.bind(this);
        return responseText;
    };
    Strophe.Connection.prototype.mechanisms[Strophe.SASLMD5.prototype.name] = Strophe.SASLMD5;
})(function() {
    window.Strophe = arguments[0];
    window.$build = arguments[1];
    window.$msg = arguments[2];
    window.$iq = arguments[3];
    window.$pres = arguments[4];
});

/*
    This program is distributed under the terms of the MIT license.
    Please see the LICENSE file for details.

    Copyright 2006-2008, OGG, LLC
*/
/* jshint undef: true, unused: true:, noarg: true, latedef: true */
/*global window, setTimeout, clearTimeout,
    XMLHttpRequest, ActiveXObject,
    Strophe, $build */
/** PrivateClass: Strophe.Request
 *  _Private_ helper class that provides a cross implementation abstraction
 *  for a BOSH related XMLHttpRequest.
 *
 *  The Strophe.Request class is used internally to encapsulate BOSH request
 *  information.  It is not meant to be used from user's code.
 */
/** PrivateConstructor: Strophe.Request
 *  Create and initialize a new Strophe.Request object.
 *
 *  Parameters:
 *    (XMLElement) elem - The XML data to be sent in the request.
 *    (Function) func - The function that will be called when the
 *      XMLHttpRequest readyState changes.
 *    (Integer) rid - The BOSH rid attribute associated with this request.
 *    (Integer) sends - The number of times this same request has been
 *      sent.
 */
Strophe.Request = function(elem, func, rid, sends) {
    this.id = ++Strophe._requestId;
    this.xmlData = elem;
    this.data = Strophe.serialize(elem);
    // save original function in case we need to make a new request
    // from this one.
    this.origFunc = func;
    this.func = func;
    this.rid = rid;
    this.date = NaN;
    this.sends = sends || 0;
    this.abort = false;
    this.dead = null;
    this.age = function() {
        if (!this.date) {
            return 0;
        }
        var now = new Date();
        return (now - this.date) / 1e3;
    };
    this.timeDead = function() {
        if (!this.dead) {
            return 0;
        }
        var now = new Date();
        return (now - this.dead) / 1e3;
    };
    this.xhr = this._newXHR();
};

Strophe.Request.prototype = {
    /** PrivateFunction: getResponse
     *  Get a response from the underlying XMLHttpRequest.
     *
     *  This function attempts to get a response from the request and checks
     *  for errors.
     *
     *  Throws:
     *    "parsererror" - A parser error occured.
     *
     *  Returns:
     *    The DOM element tree of the response.
     */
    getResponse: function() {
        var node = null;
        if (this.xhr.responseXML && this.xhr.responseXML.documentElement) {
            node = this.xhr.responseXML.documentElement;
            if (node.tagName == "parsererror") {
                Strophe.error("invalid response received");
                Strophe.error("responseText: " + this.xhr.responseText);
                Strophe.error("responseXML: " + Strophe.serialize(this.xhr.responseXML));
                throw "parsererror";
            }
        } else if (this.xhr.responseText) {
            Strophe.error("invalid response received");
            Strophe.error("responseText: " + this.xhr.responseText);
            Strophe.error("responseXML: " + Strophe.serialize(this.xhr.responseXML));
        }
        return node;
    },
    /** PrivateFunction: _newXHR
     *  _Private_ helper function to create XMLHttpRequests.
     *
     *  This function creates XMLHttpRequests across all implementations.
     *
     *  Returns:
     *    A new XMLHttpRequest.
     */
    _newXHR: function() {
        var xhr = null;
        if (window.XMLHttpRequest) {
            xhr = new XMLHttpRequest();
            if (xhr.overrideMimeType) {
                xhr.overrideMimeType("text/xml");
            }
        } else if (window.ActiveXObject) {
            xhr = new ActiveXObject("Microsoft.XMLHTTP");
        }
        // use Function.bind() to prepend ourselves as an argument
        xhr.onreadystatechange = this.func.bind(null, this);
        return xhr;
    }
};

/** Class: Strophe.Bosh
 *  _Private_ helper class that handles BOSH Connections
 *
 *  The Strophe.Bosh class is used internally by Strophe.Connection
 *  to encapsulate BOSH sessions. It is not meant to be used from user's code.
 */
/** File: bosh.js
 *  A JavaScript library to enable BOSH in Strophejs.
 *
 *  this library uses Bidirectional-streams Over Synchronous HTTP (BOSH)
 *  to emulate a persistent, stateful, two-way connection to an XMPP server.
 *  More information on BOSH can be found in XEP 124.
 */
/** PrivateConstructor: Strophe.Bosh
 *  Create and initialize a Strophe.Bosh object.
 *
 *  Parameters:
 *    (Strophe.Connection) connection - The Strophe.Connection that will use BOSH.
 *
 *  Returns:
 *    A new Strophe.Bosh object.
 */
Strophe.Bosh = function(connection) {
    this._conn = connection;
    /* request id for body tags */
    this.rid = Math.floor(Math.random() * 4294967295);
    /* The current session ID. */
    this.sid = null;
    // default BOSH values
    this.hold = 1;
    this.wait = 60;
    this.window = 5;
    this._requests = [];
};

Strophe.Bosh.prototype = {
    /** Variable: strip
     *
     *  BOSH-Connections will have all stanzas wrapped in a <body> tag when
     *  passed to <Strophe.Connection.xmlInput> or <Strophe.Connection.xmlOutput>.
     *  To strip this tag, User code can set <Strophe.Bosh.strip> to "body":
     *
     *  > Strophe.Bosh.prototype.strip = "body";
     *
     *  This will enable stripping of the body tag in both
     *  <Strophe.Connection.xmlInput> and <Strophe.Connection.xmlOutput>.
     */
    strip: null,
    /** PrivateFunction: _buildBody
     *  _Private_ helper function to generate the <body/> wrapper for BOSH.
     *
     *  Returns:
     *    A Strophe.Builder with a <body/> element.
     */
    _buildBody: function() {
        var bodyWrap = $build("body", {
            rid: this.rid++,
            xmlns: Strophe.NS.HTTPBIND
        });
        if (this.sid !== null) {
            bodyWrap.attrs({
                sid: this.sid
            });
        }
        return bodyWrap;
    },
    /** PrivateFunction: _reset
     *  Reset the connection.
     *
     *  This function is called by the reset function of the Strophe Connection
     */
    _reset: function() {
        this.rid = Math.floor(Math.random() * 4294967295);
        this.sid = null;
    },
    /** PrivateFunction: _connect
     *  _Private_ function that initializes the BOSH connection.
     *
     *  Creates and sends the Request that initializes the BOSH connection.
     */
    _connect: function(wait, hold, route) {
        this.wait = wait || this.wait;
        this.hold = hold || this.hold;
        // build the body tag
        var body = this._buildBody().attrs({
            to: this._conn.domain,
            "xml:lang": "en",
            wait: this.wait,
            hold: this.hold,
            content: "text/xml; charset=utf-8",
            ver: "1.6",
            "xmpp:version": "1.0",
            "xmlns:xmpp": Strophe.NS.BOSH
        });
        if (route) {
            body.attrs({
                route: route
            });
        }
        var _connect_cb = this._conn._connect_cb;
        this._requests.push(new Strophe.Request(body.tree(), this._onRequestStateChange.bind(this, _connect_cb.bind(this._conn)), body.tree().getAttribute("rid")));
        this._throttledRequestHandler();
    },
    /** PrivateFunction: _attach
     *  Attach to an already created and authenticated BOSH session.
     *
     *  This function is provided to allow Strophe to attach to BOSH
     *  sessions which have been created externally, perhaps by a Web
     *  application.  This is often used to support auto-login type features
     *  without putting user credentials into the page.
     *
     *  Parameters:
     *    (String) jid - The full JID that is bound by the session.
     *    (String) sid - The SID of the BOSH session.
     *    (String) rid - The current RID of the BOSH session.  This RID
     *      will be used by the next request.
     *    (Function) callback The connect callback function.
     *    (Integer) wait - The optional HTTPBIND wait value.  This is the
     *      time the server will wait before returning an empty result for
     *      a request.  The default setting of 60 seconds is recommended.
     *      Other settings will require tweaks to the Strophe.TIMEOUT value.
     *    (Integer) hold - The optional HTTPBIND hold value.  This is the
     *      number of connections the server will hold at one time.  This
     *      should almost always be set to 1 (the default).
     *    (Integer) wind - The optional HTTBIND window value.  This is the
     *      allowed range of request ids that are valid.  The default is 5.
     */
    _attach: function(jid, sid, rid, callback, wait, hold, wind) {
        this._conn.jid = jid;
        this.sid = sid;
        this.rid = rid;
        this._conn.connect_callback = callback;
        this._conn.domain = Strophe.getDomainFromJid(this._conn.jid);
        this._conn.authenticated = true;
        this._conn.connected = true;
        this.wait = wait || this.wait;
        this.hold = hold || this.hold;
        this.window = wind || this.window;
        this._conn._changeConnectStatus(Strophe.Status.ATTACHED, null);
    },
    /** PrivateFunction: _connect_cb
     *  _Private_ handler for initial connection request.
     *
     *  This handler is used to process the Bosh-part of the initial request.
     *  Parameters:
     *    (Strophe.Request) bodyWrap - The received stanza.
     */
    _connect_cb: function(bodyWrap) {
        var typ = bodyWrap.getAttribute("type");
        var cond, conflict;
        if (typ !== null && typ == "terminate") {
            // an error occurred
            Strophe.error("BOSH-Connection failed: " + cond);
            cond = bodyWrap.getAttribute("condition");
            conflict = bodyWrap.getElementsByTagName("conflict");
            if (cond !== null) {
                if (cond == "remote-stream-error" && conflict.length > 0) {
                    cond = "conflict";
                }
                this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, cond);
            } else {
                this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, "unknown");
            }
            this._conn._doDisconnect();
            return Strophe.Status.CONNFAIL;
        }
        // check to make sure we don't overwrite these if _connect_cb is
        // called multiple times in the case of missing stream:features
        if (!this.sid) {
            this.sid = bodyWrap.getAttribute("sid");
        }
        var wind = bodyWrap.getAttribute("requests");
        if (wind) {
            this.window = parseInt(wind, 10);
        }
        var hold = bodyWrap.getAttribute("hold");
        if (hold) {
            this.hold = parseInt(hold, 10);
        }
        var wait = bodyWrap.getAttribute("wait");
        if (wait) {
            this.wait = parseInt(wait, 10);
        }
    },
    /** PrivateFunction: _disconnect
     *  _Private_ part of Connection.disconnect for Bosh
     *
     *  Parameters:
     *    (Request) pres - This stanza will be sent before disconnecting.
     */
    _disconnect: function(pres) {
        this._sendTerminate(pres);
    },
    /** PrivateFunction: _doDisconnect
     *  _Private_ function to disconnect.
     *
     *  Resets the SID and RID.
     */
    _doDisconnect: function() {
        this.sid = null;
        this.rid = Math.floor(Math.random() * 4294967295);
    },
    /** PrivateFunction: _emptyQueue
     * _Private_ function to check if the Request queue is empty.
     *
     *  Returns:
     *    True, if there are no Requests queued, False otherwise.
     */
    _emptyQueue: function() {
        return this._requests.length === 0;
    },
    /** PrivateFunction: _hitError
     *  _Private_ function to handle the error count.
     *
     *  Requests are resent automatically until their error count reaches
     *  5.  Each time an error is encountered, this function is called to
     *  increment the count and disconnect if the count is too high.
     *
     *  Parameters:
     *    (Integer) reqStatus - The request status.
     */
    _hitError: function(reqStatus) {
        this.errors++;
        Strophe.warn("request errored, status: " + reqStatus + ", number of errors: " + this.errors);
        if (this.errors > 4) {
            this._onDisconnectTimeout();
        }
    },
    /** PrivateFunction: _no_auth_received
     *
     * Called on stream start/restart when no stream:features
     * has been received and sends a blank poll request.
     */
    _no_auth_received: function(_callback) {
        if (_callback) {
            _callback = _callback.bind(this._conn);
        } else {
            _callback = this._conn._connect_cb.bind(this._conn);
        }
        var body = this._buildBody();
        this._requests.push(new Strophe.Request(body.tree(), this._onRequestStateChange.bind(this, _callback.bind(this._conn)), body.tree().getAttribute("rid")));
        this._throttledRequestHandler();
    },
    /** PrivateFunction: _onDisconnectTimeout
     *  _Private_ timeout handler for handling non-graceful disconnection.
     *
     *  Cancels all remaining Requests and clears the queue.
     */
    _onDisconnectTimeout: function() {
        var req;
        while (this._requests.length > 0) {
            req = this._requests.pop();
            req.abort = true;
            req.xhr.abort();
            // jslint complains, but this is fine. setting to empty func
            // is necessary for IE6
            req.xhr.onreadystatechange = function() {};
        }
    },
    /** PrivateFunction: _onIdle
     *  _Private_ handler called by Strophe.Connection._onIdle
     *
     *  Sends all queued Requests or polls with empty Request if there are none.
     */
    _onIdle: function() {
        var data = this._conn._data;
        // if no requests are in progress, poll
        if (this._conn.authenticated && this._requests.length === 0 && data.length === 0 && !this._conn.disconnecting) {
            Strophe.info("no requests during idle cycle, sending " + "blank request");
            data.push(null);
        }
        if (this._requests.length < 2 && data.length > 0 && !this._conn.paused) {
            var body = this._buildBody();
            for (var i = 0; i < data.length; i++) {
                if (data[i] !== null) {
                    if (data[i] === "restart") {
                        body.attrs({
                            to: this._conn.domain,
                            "xml:lang": "en",
                            "xmpp:restart": "true",
                            "xmlns:xmpp": Strophe.NS.BOSH
                        });
                    } else {
                        body.cnode(data[i]).up();
                    }
                }
            }
            delete this._conn._data;
            this._conn._data = [];
            this._requests.push(new Strophe.Request(body.tree(), this._onRequestStateChange.bind(this, this._conn._dataRecv.bind(this._conn)), body.tree().getAttribute("rid")));
            this._processRequest(this._requests.length - 1);
        }
        if (this._requests.length > 0) {
            var time_elapsed = this._requests[0].age();
            if (this._requests[0].dead !== null) {
                if (this._requests[0].timeDead() > Math.floor(Strophe.SECONDARY_TIMEOUT * this.wait)) {
                    this._throttledRequestHandler();
                }
            }
            if (time_elapsed > Math.floor(Strophe.TIMEOUT * this.wait)) {
                Strophe.warn("Request " + this._requests[0].id + " timed out, over " + Math.floor(Strophe.TIMEOUT * this.wait) + " seconds since last activity");
                this._throttledRequestHandler();
            }
        }
    },
    /** PrivateFunction: _onRequestStateChange
     *  _Private_ handler for Strophe.Request state changes.
     *
     *  This function is called when the XMLHttpRequest readyState changes.
     *  It contains a lot of error handling logic for the many ways that
     *  requests can fail, and calls the request callback when requests
     *  succeed.
     *
     *  Parameters:
     *    (Function) func - The handler for the request.
     *    (Strophe.Request) req - The request that is changing readyState.
     */
    _onRequestStateChange: function(func, req) {
        Strophe.debug("request id " + req.id + "." + req.sends + " state changed to " + req.xhr.readyState);
        if (req.abort) {
            req.abort = false;
            return;
        }
        // request complete
        var reqStatus;
        if (req.xhr.readyState == 4) {
            reqStatus = 0;
            try {
                reqStatus = req.xhr.status;
            } catch (e) {}
            if (typeof reqStatus == "undefined") {
                reqStatus = 0;
            }
            if (this.disconnecting) {
                if (reqStatus >= 400) {
                    this._hitError(reqStatus);
                    return;
                }
            }
            var reqIs0 = this._requests[0] == req;
            var reqIs1 = this._requests[1] == req;
            if (reqStatus > 0 && reqStatus < 500 || req.sends > 5) {
                // remove from internal queue
                this._removeRequest(req);
                Strophe.debug("request id " + req.id + " should now be removed");
            }
            // request succeeded
            if (reqStatus == 200) {
                // if request 1 finished, or request 0 finished and request
                // 1 is over Strophe.SECONDARY_TIMEOUT seconds old, we need to
                // restart the other - both will be in the first spot, as the
                // completed request has been removed from the queue already
                if (reqIs1 || reqIs0 && this._requests.length > 0 && this._requests[0].age() > Math.floor(Strophe.SECONDARY_TIMEOUT * this.wait)) {
                    this._restartRequest(0);
                }
                // call handler
                Strophe.debug("request id " + req.id + "." + req.sends + " got 200");
                func(req);
                this.errors = 0;
            } else {
                Strophe.error("request id " + req.id + "." + req.sends + " error " + reqStatus + " happened");
                if (reqStatus === 0 || reqStatus >= 400 && reqStatus < 600 || reqStatus >= 12e3) {
                    this._hitError(reqStatus);
                    if (reqStatus >= 400 && reqStatus < 500) {
                        this._conn._changeConnectStatus(Strophe.Status.DISCONNECTING, null);
                        this._conn._doDisconnect();
                    }
                }
            }
            if (!(reqStatus > 0 && reqStatus < 500 || req.sends > 5)) {
                this._throttledRequestHandler();
            }
        }
    },
    /** PrivateFunction: _processRequest
     *  _Private_ function to process a request in the queue.
     *
     *  This function takes requests off the queue and sends them and
     *  restarts dead requests.
     *
     *  Parameters:
     *    (Integer) i - The index of the request in the queue.
     */
    _processRequest: function(i) {
        var self = this;
        var req = this._requests[i];
        var reqStatus = -1;
        try {
            if (req.xhr.readyState == 4) {
                reqStatus = req.xhr.status;
            }
        } catch (e) {
            Strophe.error("caught an error in _requests[" + i + "], reqStatus: " + reqStatus);
        }
        if (typeof reqStatus == "undefined") {
            reqStatus = -1;
        }
        // make sure we limit the number of retries
        if (req.sends > this.maxRetries) {
            this._onDisconnectTimeout();
            return;
        }
        var time_elapsed = req.age();
        var primaryTimeout = !isNaN(time_elapsed) && time_elapsed > Math.floor(Strophe.TIMEOUT * this.wait);
        var secondaryTimeout = req.dead !== null && req.timeDead() > Math.floor(Strophe.SECONDARY_TIMEOUT * this.wait);
        var requestCompletedWithServerError = req.xhr.readyState == 4 && (reqStatus < 1 || reqStatus >= 500);
        if (primaryTimeout || secondaryTimeout || requestCompletedWithServerError) {
            if (secondaryTimeout) {
                Strophe.error("Request " + this._requests[i].id + " timed out (secondary), restarting");
            }
            req.abort = true;
            req.xhr.abort();
            // setting to null fails on IE6, so set to empty function
            req.xhr.onreadystatechange = function() {};
            this._requests[i] = new Strophe.Request(req.xmlData, req.origFunc, req.rid, req.sends);
            req = this._requests[i];
        }
        if (req.xhr.readyState === 0) {
            Strophe.debug("request id " + req.id + "." + req.sends + " posting");
            try {
                req.xhr.open("POST", this._conn.service, this._conn.options.sync ? false : true);
            } catch (e2) {
                Strophe.error("XHR open failed.");
                if (!this._conn.connected) {
                    this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, "bad-service");
                }
                this._conn.disconnect();
                return;
            }
            // Fires the XHR request -- may be invoked immediately
            // or on a gradually expanding retry window for reconnects
            var sendFunc = function() {
                req.date = new Date();
                if (self._conn.options.customHeaders) {
                    var headers = self._conn.options.customHeaders;
                    for (var header in headers) {
                        if (headers.hasOwnProperty(header)) {
                            req.xhr.setRequestHeader(header, headers[header]);
                        }
                    }
                }
                req.xhr.send(req.data);
            };
            // Implement progressive backoff for reconnects --
            // First retry (send == 1) should also be instantaneous
            if (req.sends > 1) {
                // Using a cube of the retry number creates a nicely
                // expanding retry window
                var backoff = Math.min(Math.floor(Strophe.TIMEOUT * this.wait), Math.pow(req.sends, 3)) * 1e3;
                setTimeout(sendFunc, backoff);
            } else {
                sendFunc();
            }
            req.sends++;
            if (this._conn.xmlOutput !== Strophe.Connection.prototype.xmlOutput) {
                if (req.xmlData.nodeName === this.strip && req.xmlData.childNodes.length) {
                    this._conn.xmlOutput(req.xmlData.childNodes[0]);
                } else {
                    this._conn.xmlOutput(req.xmlData);
                }
            }
            if (this._conn.rawOutput !== Strophe.Connection.prototype.rawOutput) {
                this._conn.rawOutput(req.data);
            }
        } else {
            Strophe.debug("_processRequest: " + (i === 0 ? "first" : "second") + " request has readyState of " + req.xhr.readyState);
        }
    },
    /** PrivateFunction: _removeRequest
     *  _Private_ function to remove a request from the queue.
     *
     *  Parameters:
     *    (Strophe.Request) req - The request to remove.
     */
    _removeRequest: function(req) {
        Strophe.debug("removing request");
        var i;
        for (i = this._requests.length - 1; i >= 0; i--) {
            if (req == this._requests[i]) {
                this._requests.splice(i, 1);
            }
        }
        // IE6 fails on setting to null, so set to empty function
        req.xhr.onreadystatechange = function() {};
        this._throttledRequestHandler();
    },
    /** PrivateFunction: _restartRequest
     *  _Private_ function to restart a request that is presumed dead.
     *
     *  Parameters:
     *    (Integer) i - The index of the request in the queue.
     */
    _restartRequest: function(i) {
        var req = this._requests[i];
        if (req.dead === null) {
            req.dead = new Date();
        }
        this._processRequest(i);
    },
    /** PrivateFunction: _reqToData
     * _Private_ function to get a stanza out of a request.
     *
     * Tries to extract a stanza out of a Request Object.
     * When this fails the current connection will be disconnected.
     *
     *  Parameters:
     *    (Object) req - The Request.
     *
     *  Returns:
     *    The stanza that was passed.
     */
    _reqToData: function(req) {
        try {
            return req.getResponse();
        } catch (e) {
            if (e != "parsererror") {
                throw e;
            }
            this._conn.disconnect("strophe-parsererror");
        }
    },
    /** PrivateFunction: _sendTerminate
     *  _Private_ function to send initial disconnect sequence.
     *
     *  This is the first step in a graceful disconnect.  It sends
     *  the BOSH server a terminate body and includes an unavailable
     *  presence if authentication has completed.
     */
    _sendTerminate: function(pres) {
        Strophe.info("_sendTerminate was called");
        var body = this._buildBody().attrs({
            type: "terminate"
        });
        if (pres) {
            body.cnode(pres.tree());
        }
        var req = new Strophe.Request(body.tree(), this._onRequestStateChange.bind(this, this._conn._dataRecv.bind(this._conn)), body.tree().getAttribute("rid"));
        this._requests.push(req);
        this._throttledRequestHandler();
    },
    /** PrivateFunction: _send
     *  _Private_ part of the Connection.send function for BOSH
     *
     * Just triggers the RequestHandler to send the messages that are in the queue
     */
    _send: function() {
        clearTimeout(this._conn._idleTimeout);
        this._throttledRequestHandler();
        this._conn._idleTimeout = setTimeout(this._conn._onIdle.bind(this._conn), 100);
    },
    /** PrivateFunction: _sendRestart
     *
     *  Send an xmpp:restart stanza.
     */
    _sendRestart: function() {
        this._throttledRequestHandler();
        clearTimeout(this._conn._idleTimeout);
    },
    /** PrivateFunction: _throttledRequestHandler
     *  _Private_ function to throttle requests to the connection window.
     *
     *  This function makes sure we don't send requests so fast that the
     *  request ids overflow the connection window in the case that one
     *  request died.
     */
    _throttledRequestHandler: function() {
        if (!this._requests) {
            Strophe.debug("_throttledRequestHandler called with " + "undefined requests");
        } else {
            Strophe.debug("_throttledRequestHandler called with " + this._requests.length + " requests");
        }
        if (!this._requests || this._requests.length === 0) {
            return;
        }
        if (this._requests.length > 0) {
            this._processRequest(0);
        }
        if (this._requests.length > 1 && Math.abs(this._requests[0].rid - this._requests[1].rid) < this.window) {
            this._processRequest(1);
        }
    }
};

/*
    This program is distributed under the terms of the MIT license.
    Please see the LICENSE file for details.

    Copyright 2006-2008, OGG, LLC
*/
/* jshint undef: true, unused: true:, noarg: true, latedef: true */
/*global document, window, clearTimeout, WebSocket,
    DOMParser, Strophe, $build */
/** Class: Strophe.WebSocket
 *  _Private_ helper class that handles WebSocket Connections
 *
 *  The Strophe.WebSocket class is used internally by Strophe.Connection
 *  to encapsulate WebSocket sessions. It is not meant to be used from user's code.
 */
/** File: websocket.js
 *  A JavaScript library to enable XMPP over Websocket in Strophejs.
 *
 *  This file implements XMPP over WebSockets for Strophejs.
 *  If a Connection is established with a Websocket url (ws://...)
 *  Strophe will use WebSockets.
 *  For more information on XMPP-over WebSocket see this RFC draft:
 *  http://tools.ietf.org/html/draft-ietf-xmpp-websocket-00
 *
 *  WebSocket support implemented by Andreas Guth (andreas.guth@rwth-aachen.de)
 */
/** PrivateConstructor: Strophe.Websocket
 *  Create and initialize a Strophe.WebSocket object.
 *  Currently only sets the connection Object.
 *
 *  Parameters:
 *    (Strophe.Connection) connection - The Strophe.Connection that will use WebSockets.
 *
 *  Returns:
 *    A new Strophe.WebSocket object.
 */
Strophe.Websocket = function(connection) {
    this._conn = connection;
    this.strip = "stream:stream";
    var service = connection.service;
    if (service.indexOf("ws:") !== 0 && service.indexOf("wss:") !== 0) {
        // If the service is not an absolute URL, assume it is a path and put the absolute
        // URL together from options, current URL and the path.
        var new_service = "";
        if (connection.options.protocol === "ws" && window.location.protocol !== "https:") {
            new_service += "ws";
        } else {
            new_service += "wss";
        }
        new_service += "://" + window.location.host;
        if (service.indexOf("/") !== 0) {
            new_service += window.location.pathname + service;
        } else {
            new_service += service;
        }
        connection.service = new_service;
    }
};

Strophe.Websocket.prototype = {
    /** PrivateFunction: _buildStream
     *  _Private_ helper function to generate the <stream> start tag for WebSockets
     *
     *  Returns:
     *    A Strophe.Builder with a <stream> element.
     */
    _buildStream: function() {
        return $build("stream:stream", {
            to: this._conn.domain,
            xmlns: Strophe.NS.CLIENT,
            "xmlns:stream": Strophe.NS.STREAM,
            version: "1.0"
        });
    },
    /** PrivateFunction: _check_streamerror
     * _Private_ checks a message for stream:error
     *
     *  Parameters:
     *    (Strophe.Request) bodyWrap - The received stanza.
     *    connectstatus - The ConnectStatus that will be set on error.
     *  Returns:
     *     true if there was a streamerror, false otherwise.
     */
    _check_streamerror: function(bodyWrap, connectstatus) {
        var errors = bodyWrap.getElementsByTagName("stream:error");
        if (errors.length === 0) {
            return false;
        }
        var error = errors[0];
        var condition = "";
        var text = "";
        var ns = "urn:ietf:params:xml:ns:xmpp-streams";
        for (var i = 0; i < error.childNodes.length; i++) {
            var e = error.childNodes[i];
            if (e.getAttribute("xmlns") !== ns) {
                break;
            }
            if (e.nodeName === "text") {
                text = e.textContent;
            } else {
                condition = e.nodeName;
            }
        }
        var errorString = "WebSocket stream error: ";
        if (condition) {
            errorString += condition;
        } else {
            errorString += "unknown";
        }
        if (text) {
            errorString += " - " + condition;
        }
        Strophe.error(errorString);
        // close the connection on stream_error
        this._conn._changeConnectStatus(connectstatus, condition);
        this._conn._doDisconnect();
        return true;
    },
    /** PrivateFunction: _reset
     *  Reset the connection.
     *
     *  This function is called by the reset function of the Strophe Connection.
     *  Is not needed by WebSockets.
     */
    _reset: function() {
        return;
    },
    /** PrivateFunction: _connect
     *  _Private_ function called by Strophe.Connection.connect
     *
     *  Creates a WebSocket for a connection and assigns Callbacks to it.
     *  Does nothing if there already is a WebSocket.
     */
    _connect: function() {
        // Ensure that there is no open WebSocket from a previous Connection.
        this._closeSocket();
        // Create the new WobSocket
        this.socket = new WebSocket(this._conn.service, "xmpp");
        this.socket.onopen = this._onOpen.bind(this);
        this.socket.onerror = this._onError.bind(this);
        this.socket.onclose = this._onClose.bind(this);
        this.socket.onmessage = this._connect_cb_wrapper.bind(this);
    },
    /** PrivateFunction: _connect_cb
     *  _Private_ function called by Strophe.Connection._connect_cb
     *
     * checks for stream:error
     *
     *  Parameters:
     *    (Strophe.Request) bodyWrap - The received stanza.
     */
    _connect_cb: function(bodyWrap) {
        var error = this._check_streamerror(bodyWrap, Strophe.Status.CONNFAIL);
        if (error) {
            return Strophe.Status.CONNFAIL;
        }
    },
    /** PrivateFunction: _handleStreamStart
     * _Private_ function that checks the opening stream:stream tag for errors.
     *
     * Disconnects if there is an error and returns false, true otherwise.
     *
     *  Parameters:
     *    (Node) message - Stanza containing the stream:stream.
     */
    _handleStreamStart: function(message) {
        var error = false;
        // Check for errors in the stream:stream tag
        var ns = message.getAttribute("xmlns");
        if (typeof ns !== "string") {
            error = "Missing xmlns in stream:stream";
        } else if (ns !== Strophe.NS.CLIENT) {
            error = "Wrong xmlns in stream:stream: " + ns;
        }
        var ns_stream = message.namespaceURI;
        if (typeof ns_stream !== "string") {
            error = "Missing xmlns:stream in stream:stream";
        } else if (ns_stream !== Strophe.NS.STREAM) {
            error = "Wrong xmlns:stream in stream:stream: " + ns_stream;
        }
        var ver = message.getAttribute("version");
        if (typeof ver !== "string") {
            error = "Missing version in stream:stream";
        } else if (ver !== "1.0") {
            error = "Wrong version in stream:stream: " + ver;
        }
        if (error) {
            this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, error);
            this._conn._doDisconnect();
            return false;
        }
        return true;
    },
    /** PrivateFunction: _connect_cb_wrapper
     * _Private_ function that handles the first connection messages.
     *
     * On receiving an opening stream tag this callback replaces itself with the real
     * message handler. On receiving a stream error the connection is terminated.
     */
    _connect_cb_wrapper: function(message) {
        if (message.data.indexOf("<stream:stream ") === 0 || message.data.indexOf("<?xml") === 0) {
            // Strip the XML Declaration, if there is one
            var data = message.data.replace(/^(<\?.*?\?>\s*)*/, "");
            if (data === "") return;
            //Make the initial stream:stream selfclosing to parse it without a SAX parser.
            data = message.data.replace(/<stream:stream (.*[^\/])>/, "<stream:stream $1/>");
            var streamStart = new DOMParser().parseFromString(data, "text/xml").documentElement;
            this._conn.xmlInput(streamStart);
            this._conn.rawInput(message.data);
            //_handleStreamSteart will check for XML errors and disconnect on error
            if (this._handleStreamStart(streamStart)) {
                //_connect_cb will check for stream:error and disconnect on error
                this._connect_cb(streamStart);
                // ensure received stream:stream is NOT selfclosing and save it for following messages
                this.streamStart = message.data.replace(/^<stream:(.*)\/>$/, "<stream:$1>");
            }
        } else if (message.data === "</stream:stream>") {
            this._conn.rawInput(message.data);
            this._conn.xmlInput(document.createElement("stream:stream"));
            this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, "Received closing stream");
            this._conn._doDisconnect();
            return;
        } else {
            var string = this._streamWrap(message.data);
            var elem = new DOMParser().parseFromString(string, "text/xml").documentElement;
            this.socket.onmessage = this._onMessage.bind(this);
            this._conn._connect_cb(elem, null, message.data);
        }
    },
    /** PrivateFunction: _disconnect
     *  _Private_ function called by Strophe.Connection.disconnect
     *
     *  Disconnects and sends a last stanza if one is given
     *
     *  Parameters:
     *    (Request) pres - This stanza will be sent before disconnecting.
     */
    _disconnect: function(pres) {
        if (this.socket.readyState !== WebSocket.CLOSED) {
            if (pres) {
                this._conn.send(pres);
            }
            var close = "</stream:stream>";
            this._conn.xmlOutput(document.createElement("stream:stream"));
            this._conn.rawOutput(close);
            try {
                this.socket.send(close);
            } catch (e) {
                Strophe.info("Couldn't send closing stream tag.");
            }
        }
        this._conn._doDisconnect();
    },
    /** PrivateFunction: _doDisconnect
     *  _Private_ function to disconnect.
     *
     *  Just closes the Socket for WebSockets
     */
    _doDisconnect: function() {
        Strophe.info("WebSockets _doDisconnect was called");
        this._closeSocket();
    },
    /** PrivateFunction _streamWrap
     *  _Private_ helper function to wrap a stanza in a <stream> tag.
     *  This is used so Strophe can process stanzas from WebSockets like BOSH
     */
    _streamWrap: function(stanza) {
        return this.streamStart + stanza + "</stream:stream>";
    },
    /** PrivateFunction: _closeSocket
     *  _Private_ function to close the WebSocket.
     *
     *  Closes the socket if it is still open and deletes it
     */
    _closeSocket: function() {
        if (this.socket) {
            try {
                this.socket.close();
            } catch (e) {}
        }
        this.socket = null;
    },
    /** PrivateFunction: _emptyQueue
     * _Private_ function to check if the message queue is empty.
     *
     *  Returns:
     *    True, because WebSocket messages are send immediately after queueing.
     */
    _emptyQueue: function() {
        return true;
    },
    /** PrivateFunction: _onClose
     * _Private_ function to handle websockets closing.
     *
     * Nothing to do here for WebSockets
     */
    _onClose: function() {
        if (this._conn.connected && !this._conn.disconnecting) {
            Strophe.error("Websocket closed unexcectedly");
            this._conn._doDisconnect();
        } else {
            Strophe.info("Websocket closed");
        }
    },
    /** PrivateFunction: _no_auth_received
     *
     * Called on stream start/restart when no stream:features
     * has been received.
     */
    _no_auth_received: function(_callback) {
        Strophe.error("Server did not send any auth methods");
        this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, "Server did not send any auth methods");
        if (_callback) {
            _callback = _callback.bind(this._conn);
            _callback();
        }
        this._conn._doDisconnect();
    },
    /** PrivateFunction: _onDisconnectTimeout
     *  _Private_ timeout handler for handling non-graceful disconnection.
     *
     *  This does nothing for WebSockets
     */
    _onDisconnectTimeout: function() {},
    /** PrivateFunction: _onError
     * _Private_ function to handle websockets errors.
     *
     * Parameters:
     * (Object) error - The websocket error.
     */
    _onError: function(error) {
        Strophe.error("Websocket error " + error);
        this._conn._changeConnectStatus(Strophe.Status.CONNFAIL, "The WebSocket connection could not be established was disconnected.");
        this._disconnect();
    },
    /** PrivateFunction: _onIdle
     *  _Private_ function called by Strophe.Connection._onIdle
     *
     *  sends all queued stanzas
     */
    _onIdle: function() {
        var data = this._conn._data;
        if (data.length > 0 && !this._conn.paused) {
            for (var i = 0; i < data.length; i++) {
                if (data[i] !== null) {
                    var stanza, rawStanza;
                    if (data[i] === "restart") {
                        stanza = this._buildStream();
                        rawStanza = this._removeClosingTag(stanza);
                        stanza = stanza.tree();
                    } else {
                        stanza = data[i];
                        rawStanza = Strophe.serialize(stanza);
                    }
                    this._conn.xmlOutput(stanza);
                    this._conn.rawOutput(rawStanza);
                    this.socket.send(rawStanza);
                }
            }
            this._conn._data = [];
        }
    },
    /** PrivateFunction: _onMessage
     * _Private_ function to handle websockets messages.
     *
     * This function parses each of the messages as if they are full documents. [TODO : We may actually want to use a SAX Push parser].
     *
     * Since all XMPP traffic starts with "<stream:stream version='1.0' xml:lang='en' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' id='3697395463' from='SERVER'>"
     * The first stanza will always fail to be parsed...
     * Addtionnaly, the seconds stanza will always be a <stream:features> with the stream NS defined in the previous stanza... so we need to 'force' the inclusion of the NS in this stanza!
     *
     * Parameters:
     * (string) message - The websocket message.
     */
    _onMessage: function(message) {
        var elem, data;
        // check for closing stream
        if (message.data === "</stream:stream>") {
            var close = "</stream:stream>";
            this._conn.rawInput(close);
            this._conn.xmlInput(document.createElement("stream:stream"));
            if (!this._conn.disconnecting) {
                this._conn._doDisconnect();
            }
            return;
        } else if (message.data.search("<stream:stream ") === 0) {
            //Make the initial stream:stream selfclosing to parse it without a SAX parser.
            data = message.data.replace(/<stream:stream (.*[^\/])>/, "<stream:stream $1/>");
            elem = new DOMParser().parseFromString(data, "text/xml").documentElement;
            if (!this._handleStreamStart(elem)) {
                return;
            }
        } else {
            data = this._streamWrap(message.data);
            elem = new DOMParser().parseFromString(data, "text/xml").documentElement;
        }
        if (this._check_streamerror(elem, Strophe.Status.ERROR)) {
            return;
        }
        //handle unavailable presence stanza before disconnecting
        if (this._conn.disconnecting && elem.firstChild.nodeName === "presence" && elem.firstChild.getAttribute("type") === "unavailable") {
            this._conn.xmlInput(elem);
            this._conn.rawInput(Strophe.serialize(elem));
            // if we are already disconnecting we will ignore the unavailable stanza and
            // wait for the </stream:stream> tag before we close the connection
            return;
        }
        this._conn._dataRecv(elem, message.data);
    },
    /** PrivateFunction: _onOpen
     * _Private_ function to handle websockets connection setup.
     *
     * The opening stream tag is sent here.
     */
    _onOpen: function() {
        Strophe.info("Websocket open");
        var start = this._buildStream();
        this._conn.xmlOutput(start.tree());
        var startString = this._removeClosingTag(start);
        this._conn.rawOutput(startString);
        this.socket.send(startString);
    },
    /** PrivateFunction: _removeClosingTag
     *  _Private_ function to Make the first <stream:stream> non-selfclosing
     *
     *  Parameters:
     *      (Object) elem - The <stream:stream> tag.
     *
     *  Returns:
     *      The stream:stream tag as String
     */
    _removeClosingTag: function(elem) {
        var string = Strophe.serialize(elem);
        string = string.replace(/<(stream:stream .*[^\/])\/>$/, "<$1>");
        return string;
    },
    /** PrivateFunction: _reqToData
     * _Private_ function to get a stanza out of a request.
     *
     * WebSockets don't use requests, so the passed argument is just returned.
     *
     *  Parameters:
     *    (Object) stanza - The stanza.
     *
     *  Returns:
     *    The stanza that was passed.
     */
    _reqToData: function(stanza) {
        return stanza;
    },
    /** PrivateFunction: _send
     *  _Private_ part of the Connection.send function for WebSocket
     *
     * Just flushes the messages that are in the queue
     */
    _send: function() {
        this._conn.flush();
    },
    /** PrivateFunction: _sendRestart
     *
     *  Send an xmpp:restart stanza.
     */
    _sendRestart: function() {
        clearTimeout(this._conn._idleTimeout);
        this._conn._onIdle.bind(this._conn)();
    }
};

// Generated by CoffeeScript 1.7.1
/*
 *Plugin to implement the MUC extension.
   http://xmpp.org/extensions/xep-0045.html
 *Previous Author:
    Nathan Zorn <nathan.zorn@gmail.com>
 *Complete CoffeeScript rewrite:
    Andreas Guth <guth@dbis.rwth-aachen.de>
 */
(function() {
    var Occupant, RoomConfig, XmppRoom, __bind = function(fn, me) {
        return function() {
            return fn.apply(me, arguments);
        };
    };
    Strophe.addConnectionPlugin("muc", {
        _connection: null,
        rooms: {},
        roomNames: [],
        /*Function
    Initialize the MUC plugin. Sets the correct connection object and
    extends the namesace.
     */
        init: function(conn) {
            this._connection = conn;
            this._muc_handler = null;
            Strophe.addNamespace("MUC_OWNER", Strophe.NS.MUC + "#owner");
            Strophe.addNamespace("MUC_ADMIN", Strophe.NS.MUC + "#admin");
            Strophe.addNamespace("MUC_USER", Strophe.NS.MUC + "#user");
            return Strophe.addNamespace("MUC_ROOMCONF", Strophe.NS.MUC + "#roomconfig");
        },
        /*Function
    Join a multi-user chat room
    Parameters:
    (String) room - The multi-user chat room to join.
    (String) nick - The nickname to use in the chat room. Optional
    (Function) msg_handler_cb - The function call to handle messages from the
    specified chat room.
    (Function) pres_handler_cb - The function call back to handle presence
    in the chat room.
    (Function) roster_cb - The function call to handle roster info in the chat room
    (String) password - The optional password to use. (password protected
    rooms only)
    (Object) history_attrs - Optional attributes for retrieving history
    (XML DOM Element) extended_presence - Optional XML for extending presence
     */
        join: function(room, nick, msg_handler_cb, pres_handler_cb, roster_cb, password, history_attrs) {
            var msg, room_nick;
            room_nick = this.test_append_nick(room, nick);
            msg = $pres({
                from: this._connection.jid,
                to: room_nick
            }).c("x", {
                xmlns: Strophe.NS.MUC
            });
            if (history_attrs != null) {
                msg = msg.c("history", history_attrs).up;
            }
            if (password != null) {
                msg.cnode(Strophe.xmlElement("password", [], password));
            }
            if (typeof extended_presence !== "undefined" && extended_presence !== null) {
                msg.up.cnode(extended_presence);
            }
            if (this._muc_handler == null) {
                this._muc_handler = this._connection.addHandler(function(_this) {
                    return function(stanza) {
                        var from, handler, handlers, id, roomname, x, xmlns, xquery, _i, _len;
                        from = stanza.getAttribute("from");
                        if (!from) {
                            return true;
                        }
                        roomname = from.split("/")[0];
                        if (!_this.rooms[roomname]) {
                            return true;
                        }
                        room = _this.rooms[roomname];
                        handlers = {};
                        if (stanza.nodeName === "message") {
                            handlers = room._message_handlers;
                        } else if (stanza.nodeName === "presence") {
                            xquery = stanza.getElementsByTagName("x");
                            if (xquery.length > 0) {
                                for (_i = 0, _len = xquery.length; _i < _len; _i++) {
                                    x = xquery[_i];
                                    xmlns = x.getAttribute("xmlns");
                                    if (xmlns && xmlns.match(Strophe.NS.MUC)) {
                                        handlers = room._presence_handlers;
                                        break;
                                    }
                                }
                            }
                        }
                        for (id in handlers) {
                            handler = handlers[id];
                            if (!handler(stanza, room)) {
                                delete handlers[id];
                            }
                        }
                        return true;
                    };
                }(this));
            }
            if (!this.rooms.hasOwnProperty(room)) {
                this.rooms[room] = new XmppRoom(this, room, nick, password);
                this.roomNames.push(room);
            }
            if (pres_handler_cb) {
                this.rooms[room].addHandler("presence", pres_handler_cb);
            }
            if (msg_handler_cb) {
                this.rooms[room].addHandler("message", msg_handler_cb);
            }
            if (roster_cb) {
                this.rooms[room].addHandler("roster", roster_cb);
            }
            return this._connection.send(msg);
        },
        /*Function
    Leave a multi-user chat room
    Parameters:
    (String) room - The multi-user chat room to leave.
    (String) nick - The nick name used in the room.
    (Function) handler_cb - Optional function to handle the successful leave.
    (String) exit_msg - optional exit message.
    Returns:
    iqid - The unique id for the room leave.
     */
        leave: function(room, nick, handler_cb, exit_msg) {
            var id, presence, presenceid, room_nick;
            id = this.roomNames.indexOf(room);
            delete this.rooms[room];
            if (id >= 0) {
                this.roomNames.splice(id, 1);
                if (this.roomNames.length === 0) {
                    this._connection.deleteHandler(this._muc_handler);
                    this._muc_handler = null;
                }
            }
            room_nick = this.test_append_nick(room, nick);
            presenceid = this._connection.getUniqueId();
            presence = $pres({
                type: "unavailable",
                id: presenceid,
                from: this._connection.jid,
                to: room_nick
            });
            if (exit_msg != null) {
                presence.c("status", exit_msg);
            }
            if (handler_cb != null) {
                this._connection.addHandler(handler_cb, null, "presence", null, presenceid);
            }
            this._connection.send(presence);
            return presenceid;
        },
        /*Function
    Parameters:
    (String) room - The multi-user chat room name.
    (String) nick - The nick name used in the chat room.
    (String) message - The plaintext message to send to the room.
    (String) html_message - The message to send to the room with html markup.
    (String) type - "groupchat" for group chat messages o
                    "chat" for private chat messages
    Returns:
    msgiq - the unique id used to send the message
     */
        message: function(room, nick, message, html_message, type) {
            var msg, msgid, parent, room_nick;
            room_nick = this.test_append_nick(room, nick);
            type = type || (nick != null ? "chat" : "groupchat");
            msgid = this._connection.getUniqueId();
            msg = $msg({
                to: room_nick,
                from: this._connection.jid,
                type: type,
                id: msgid
            }).c("body", {
                xmlns: Strophe.NS.CLIENT
            }).t(message);
            msg.up();
            if (html_message != null) {
                msg.c("html", {
                    xmlns: Strophe.NS.XHTML_IM
                }).c("body", {
                    xmlns: Strophe.NS.XHTML
                }).h(html_message);
                if (msg.node.childNodes.length === 0) {
                    parent = msg.node.parentNode;
                    msg.up().up();
                    msg.node.removeChild(parent);
                } else {
                    msg.up().up();
                }
            }
            msg.c("x", {
                xmlns: "jabber:x:event"
            }).c("composing");
            this._connection.send(msg);
            return msgid;
        },
        /*Function
    Convenience Function to send a Message to all Occupants
    Parameters:
    (String) room - The multi-user chat room name.
    (String) message - The plaintext message to send to the room.
    (String) html_message - The message to send to the room with html markup.
    Returns:
    msgiq - the unique id used to send the message
     */
        groupchat: function(room, message, html_message) {
            return this.message(room, null, message, html_message);
        },
        /*Function
    Send a mediated invitation.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) receiver - The invitation's receiver.
    (String) reason - Optional reason for joining the room.
    Returns:
    msgiq - the unique id used to send the invitation
     */
        invite: function(room, receiver, reason) {
            var invitation, msgid;
            msgid = this._connection.getUniqueId();
            invitation = $msg({
                from: this._connection.jid,
                to: room,
                id: msgid
            }).c("x", {
                xmlns: Strophe.NS.MUC_USER
            }).c("invite", {
                to: receiver
            });
            if (reason != null) {
                invitation.c("reason", reason);
            }
            this._connection.send(invitation);
            return msgid;
        },
        /*Function
    Send a direct invitation.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) receiver - The invitation's receiver.
    (String) reason - Optional reason for joining the room.
    (String) password - Optional password for the room.
    Returns:
    msgiq - the unique id used to send the invitation
     */
        directInvite: function(room, receiver, reason, password) {
            var attrs, invitation, msgid;
            msgid = this._connection.getUniqueId();
            attrs = {
                xmlns: "jabber:x:conference",
                jid: room
            };
            if (reason != null) {
                attrs.reason = reason;
            }
            if (password != null) {
                attrs.password = password;
            }
            invitation = $msg({
                from: this._connection.jid,
                to: receiver,
                id: msgid
            }).c("x", attrs);
            this._connection.send(invitation);
            return msgid;
        },
        /*Function
    Queries a room for a list of occupants
    (String) room - The multi-user chat room name.
    (Function) success_cb - Optional function to handle the info.
    (Function) error_cb - Optional function to handle an error.
    Returns:
    id - the unique id used to send the info request
     */
        queryOccupants: function(room, success_cb, error_cb) {
            var attrs, info;
            attrs = {
                xmlns: Strophe.NS.DISCO_ITEMS
            };
            info = $iq({
                from: this._connection.jid,
                to: room,
                type: "get"
            }).c("query", attrs);
            return this._connection.sendIQ(info, success_cb, error_cb);
        },
        /*Function
    Start a room configuration.
    Parameters:
    (String) room - The multi-user chat room name.
    (Function) handler_cb - Optional function to handle the config form.
    Returns:
    id - the unique id used to send the configuration request
     */
        configure: function(room, handler_cb, error_cb) {
            var config, stanza;
            config = $iq({
                to: room,
                type: "get"
            }).c("query", {
                xmlns: Strophe.NS.MUC_OWNER
            });
            stanza = config.tree();
            return this._connection.sendIQ(stanza, handler_cb, error_cb);
        },
        /*Function
    Cancel the room configuration
    Parameters:
    (String) room - The multi-user chat room name.
    Returns:
    id - the unique id used to cancel the configuration.
     */
        cancelConfigure: function(room) {
            var config, stanza;
            config = $iq({
                to: room,
                type: "set"
            }).c("query", {
                xmlns: Strophe.NS.MUC_OWNER
            }).c("x", {
                xmlns: "jabber:x:data",
                type: "cancel"
            });
            stanza = config.tree();
            return this._connection.sendIQ(stanza);
        },
        /*Function
    Save a room configuration.
    Parameters:
    (String) room - The multi-user chat room name.
    (Array) config- Form Object or an array of form elements used to configure the room.
    Returns:
    id - the unique id used to save the configuration.
     */
        saveConfiguration: function(room, config, success_cb, error_cb) {
            var conf, iq, stanza, _i, _len;
            iq = $iq({
                to: room,
                type: "set"
            }).c("query", {
                xmlns: Strophe.NS.MUC_OWNER
            });
            if (typeof Form !== "undefined" && config instanceof Form) {
                config.type = "submit";
                iq.cnode(config.toXML());
            } else {
                iq.c("x", {
                    xmlns: "jabber:x:data",
                    type: "submit"
                });
                for (_i = 0, _len = config.length; _i < _len; _i++) {
                    conf = config[_i];
                    iq.cnode(conf).up();
                }
            }
            stanza = iq.tree();
            return this._connection.sendIQ(stanza, success_cb, error_cb);
        },
        /*Function
    Parameters:
    (String) room - The multi-user chat room name.
    Returns:
    id - the unique id used to create the chat room.
     */
        createInstantRoom: function(room, success_cb, error_cb) {
            var roomiq;
            roomiq = $iq({
                to: room,
                type: "set"
            }).c("query", {
                xmlns: Strophe.NS.MUC_OWNER
            }).c("x", {
                xmlns: "jabber:x:data",
                type: "submit"
            });
            return this._connection.sendIQ(roomiq.tree(), success_cb, error_cb);
        },
        /*Function
    Set the topic of the chat room.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) topic - Topic message.
     */
        setTopic: function(room, topic) {
            var msg;
            msg = $msg({
                to: room,
                from: this._connection.jid,
                type: "groupchat"
            }).c("subject", {
                xmlns: "jabber:client"
            }).t(topic);
            return this._connection.send(msg.tree());
        },
        /*Function
    Internal Function that Changes the role or affiliation of a member
    of a MUC room. This function is used by modifyRole and modifyAffiliation.
    The modification can only be done by a room moderator. An error will be
    returned if the user doesn't have permission.
    Parameters:
    (String) room - The multi-user chat room name.
    (Object) item - Object with nick and role or jid and affiliation attribute
    (String) reason - Optional reason for the change.
    (Function) handler_cb - Optional callback for success
    (Function) error_cb - Optional callback for error
    Returns:
    iq - the id of the mode change request.
     */
        _modifyPrivilege: function(room, item, reason, handler_cb, error_cb) {
            var iq;
            iq = $iq({
                to: room,
                type: "set"
            }).c("query", {
                xmlns: Strophe.NS.MUC_ADMIN
            }).cnode(item.node);
            if (reason != null) {
                iq.c("reason", reason);
            }
            return this._connection.sendIQ(iq.tree(), handler_cb, error_cb);
        },
        /*Function
    Changes the role of a member of a MUC room.
    The modification can only be done by a room moderator. An error will be
    returned if the user doesn't have permission.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) nick - The nick name of the user to modify.
    (String) role - The new role of the user.
    (String) affiliation - The new affiliation of the user.
    (String) reason - Optional reason for the change.
    (Function) handler_cb - Optional callback for success
    (Function) error_cb - Optional callback for error
    Returns:
    iq - the id of the mode change request.
     */
        modifyRole: function(room, nick, role, reason, handler_cb, error_cb) {
            var item;
            item = $build("item", {
                nick: nick,
                role: role
            });
            return this._modifyPrivilege(room, item, reason, handler_cb, error_cb);
        },
        kick: function(room, nick, reason, handler_cb, error_cb) {
            return this.modifyRole(room, nick, "none", reason, handler_cb, error_cb);
        },
        voice: function(room, nick, reason, handler_cb, error_cb) {
            return this.modifyRole(room, nick, "participant", reason, handler_cb, error_cb);
        },
        mute: function(room, nick, reason, handler_cb, error_cb) {
            return this.modifyRole(room, nick, "visitor", reason, handler_cb, error_cb);
        },
        op: function(room, nick, reason, handler_cb, error_cb) {
            return this.modifyRole(room, nick, "moderator", reason, handler_cb, error_cb);
        },
        deop: function(room, nick, reason, handler_cb, error_cb) {
            return this.modifyRole(room, nick, "participant", reason, handler_cb, error_cb);
        },
        /*Function
    Changes the affiliation of a member of a MUC room.
    The modification can only be done by a room moderator. An error will be
    returned if the user doesn't have permission.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) jid  - The jid of the user to modify.
    (String) affiliation - The new affiliation of the user.
    (String) reason - Optional reason for the change.
    (Function) handler_cb - Optional callback for success
    (Function) error_cb - Optional callback for error
    Returns:
    iq - the id of the mode change request.
     */
        modifyAffiliation: function(room, jid, affiliation, reason, handler_cb, error_cb) {
            var item;
            item = $build("item", {
                jid: jid,
                affiliation: affiliation
            });
            return this._modifyPrivilege(room, item, reason, handler_cb, error_cb);
        },
        ban: function(room, jid, reason, handler_cb, error_cb) {
            return this.modifyAffiliation(room, jid, "outcast", reason, handler_cb, error_cb);
        },
        member: function(room, jid, reason, handler_cb, error_cb) {
            return this.modifyAffiliation(room, jid, "member", reason, handler_cb, error_cb);
        },
        revoke: function(room, jid, reason, handler_cb, error_cb) {
            return this.modifyAffiliation(room, jid, "none", reason, handler_cb, error_cb);
        },
        owner: function(room, jid, reason, handler_cb, error_cb) {
            return this.modifyAffiliation(room, jid, "owner", reason, handler_cb, error_cb);
        },
        admin: function(room, jid, reason, handler_cb, error_cb) {
            return this.modifyAffiliation(room, jid, "admin", reason, handler_cb, error_cb);
        },
        /*Function
    Change the current users nick name.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) user - The new nick name.
     */
        changeNick: function(room, user) {
            var presence, room_nick;
            room_nick = this.test_append_nick(room, user);
            presence = $pres({
                from: this._connection.jid,
                to: room_nick,
                id: this._connection.getUniqueId()
            });
            return this._connection.send(presence.tree());
        },
        /*Function
    Change the current users status.
    Parameters:
    (String) room - The multi-user chat room name.
    (String) user - The current nick.
    (String) show - The new show-text.
    (String) status - The new status-text.
     */
        setStatus: function(room, user, show, status) {
            var presence, room_nick;
            room_nick = this.test_append_nick(room, user);
            presence = $pres({
                from: this._connection.jid,
                to: room_nick
            });
            if (show != null) {
                presence.c("show", show).up();
            }
            if (status != null) {
                presence.c("status", status);
            }
            return this._connection.send(presence.tree());
        },
        /*Function
    List all chat room available on a server.
    Parameters:
    (String) server - name of chat server.
    (String) handle_cb - Function to call for room list return.
    (String) error_cb - Function to call on error.
     */
        listRooms: function(server, handle_cb, error_cb) {
            var iq;
            iq = $iq({
                to: server,
                from: this._connection.jid,
                type: "get"
            }).c("query", {
                xmlns: Strophe.NS.DISCO_ITEMS
            });
            return this._connection.sendIQ(iq, handle_cb, error_cb);
        },
        test_append_nick: function(room, nick) {
            var domain, node;
            node = Strophe.escapeNode(Strophe.getNodeFromJid(room));
            domain = Strophe.getDomainFromJid(room);
            return node + "@" + domain + (nick != null ? "/" + nick : "");
        }
    });
    XmppRoom = function() {
        function XmppRoom(client, name, nick, password) {
            this.client = client;
            this.name = name;
            this.nick = nick;
            this.password = password;
            this._roomRosterHandler = __bind(this._roomRosterHandler, this);
            this._addOccupant = __bind(this._addOccupant, this);
            this.roster = {};
            this._message_handlers = {};
            this._presence_handlers = {};
            this._roster_handlers = {};
            this._handler_ids = 0;
            if (client.muc) {
                this.client = client.muc;
            }
            this.name = Strophe.getBareJidFromJid(name);
            this.addHandler("presence", this._roomRosterHandler);
        }
        XmppRoom.prototype.join = function(msg_handler_cb, pres_handler_cb, roster_cb) {
            return this.client.join(this.name, this.nick, msg_handler_cb, pres_handler_cb, roster_cb, this.password);
        };
        XmppRoom.prototype.leave = function(handler_cb, message) {
            this.client.leave(this.name, this.nick, handler_cb, message);
            return delete this.client.rooms[this.name];
        };
        XmppRoom.prototype.message = function(nick, message, html_message, type) {
            return this.client.message(this.name, nick, message, html_message, type);
        };
        XmppRoom.prototype.groupchat = function(message, html_message) {
            return this.client.groupchat(this.name, message, html_message);
        };
        XmppRoom.prototype.invite = function(receiver, reason) {
            return this.client.invite(this.name, receiver, reason);
        };
        XmppRoom.prototype.directInvite = function(receiver, reason) {
            return this.client.directInvite(this.name, receiver, reason, this.password);
        };
        XmppRoom.prototype.configure = function(handler_cb) {
            return this.client.configure(this.name, handler_cb);
        };
        XmppRoom.prototype.cancelConfigure = function() {
            return this.client.cancelConfigure(this.name);
        };
        XmppRoom.prototype.saveConfiguration = function(config) {
            return this.client.saveConfiguration(this.name, config);
        };
        XmppRoom.prototype.queryOccupants = function(success_cb, error_cb) {
            return this.client.queryOccupants(this.name, success_cb, error_cb);
        };
        XmppRoom.prototype.setTopic = function(topic) {
            return this.client.setTopic(this.name, topic);
        };
        XmppRoom.prototype.modifyRole = function(nick, role, reason, success_cb, error_cb) {
            return this.client.modifyRole(this.name, nick, role, reason, success_cb, error_cb);
        };
        XmppRoom.prototype.kick = function(nick, reason, handler_cb, error_cb) {
            return this.client.kick(this.name, nick, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.voice = function(nick, reason, handler_cb, error_cb) {
            return this.client.voice(this.name, nick, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.mute = function(nick, reason, handler_cb, error_cb) {
            return this.client.mute(this.name, nick, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.op = function(nick, reason, handler_cb, error_cb) {
            return this.client.op(this.name, nick, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.deop = function(nick, reason, handler_cb, error_cb) {
            return this.client.deop(this.name, nick, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.modifyAffiliation = function(jid, affiliation, reason, success_cb, error_cb) {
            return this.client.modifyAffiliation(this.name, jid, affiliation, reason, success_cb, error_cb);
        };
        XmppRoom.prototype.ban = function(jid, reason, handler_cb, error_cb) {
            return this.client.ban(this.name, jid, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.member = function(jid, reason, handler_cb, error_cb) {
            return this.client.member(this.name, jid, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.revoke = function(jid, reason, handler_cb, error_cb) {
            return this.client.revoke(this.name, jid, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.owner = function(jid, reason, handler_cb, error_cb) {
            return this.client.owner(this.name, jid, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.admin = function(jid, reason, handler_cb, error_cb) {
            return this.client.admin(this.name, jid, reason, handler_cb, error_cb);
        };
        XmppRoom.prototype.changeNick = function(nick) {
            this.nick = nick;
            return this.client.changeNick(this.name, nick);
        };
        XmppRoom.prototype.setStatus = function(show, status) {
            return this.client.setStatus(this.name, this.nick, show, status);
        };
        /*Function
    Adds a handler to the MUC room.
      Parameters:
    (String) handler_type - 'message', 'presence' or 'roster'.
    (Function) handler - The handler function.
    Returns:
    id - the id of handler.
     */
        XmppRoom.prototype.addHandler = function(handler_type, handler) {
            var id;
            id = this._handler_ids++;
            switch (handler_type) {
              case "presence":
                this._presence_handlers[id] = handler;
                break;

              case "message":
                this._message_handlers[id] = handler;
                break;

              case "roster":
                this._roster_handlers[id] = handler;
                break;

              default:
                this._handler_ids--;
                return null;
            }
            return id;
        };
        /*Function
    Removes a handler from the MUC room.
    This function takes ONLY ids returned by the addHandler function
    of this room. passing handler ids returned by connection.addHandler
    may brake things!
      Parameters:
    (number) id - the id of the handler
     */
        XmppRoom.prototype.removeHandler = function(id) {
            delete this._presence_handlers[id];
            delete this._message_handlers[id];
            return delete this._roster_handlers[id];
        };
        /*Function
    Creates and adds an Occupant to the Room Roster.
      Parameters:
    (Object) data - the data the Occupant is filled with
    Returns:
    occ - the created Occupant.
     */
        XmppRoom.prototype._addOccupant = function(data) {
            var occ;
            occ = new Occupant(data, this);
            this.roster[occ.nick] = occ;
            return occ;
        };
        /*Function
    The standard handler that managed the Room Roster.
      Parameters:
    (Object) pres - the presence stanza containing user information
     */
        XmppRoom.prototype._roomRosterHandler = function(pres) {
            var data, handler, id, newnick, nick, _ref;
            data = XmppRoom._parsePresence(pres);
            nick = data.nick;
            newnick = data.newnick || null;
            switch (data.type) {
              case "error":
                return;

              case "unavailable":
                if (newnick) {
                    data.nick = newnick;
                    if (this.roster[nick] && this.roster[newnick]) {
                        this.roster[nick].update(this.roster[newnick]);
                        this.roster[newnick] = this.roster[nick];
                    }
                    if (this.roster[nick] && !this.roster[newnick]) {
                        this.roster[newnick] = this.roster[nick].update(data);
                    }
                }
                delete this.roster[nick];
                break;

              default:
                if (this.roster[nick]) {
                    this.roster[nick].update(data);
                } else {
                    this._addOccupant(data);
                }
            }
            _ref = this._roster_handlers;
            for (id in _ref) {
                handler = _ref[id];
                if (!handler(this.roster, this)) {
                    delete this._roster_handlers[id];
                }
            }
            return true;
        };
        /*Function
    Parses a presence stanza
      Parameters:
    (Object) data - the data extracted from the presence stanza
     */
        XmppRoom._parsePresence = function(pres) {
            var a, c, c2, data, _i, _j, _len, _len1, _ref, _ref1, _ref2, _ref3, _ref4, _ref5, _ref6, _ref7;
            data = {};
            a = pres.attributes;
            data.nick = Strophe.getResourceFromJid(a.from.textContent);
            data.type = ((_ref = a.type) != null ? _ref.textContent : void 0) || null;
            data.states = [];
            _ref1 = pres.childNodes;
            for (_i = 0, _len = _ref1.length; _i < _len; _i++) {
                c = _ref1[_i];
                switch (c.nodeName) {
                  case "status":
                    data.status = c.textContent || null;
                    break;

                  case "show":
                    data.show = c.textContent || null;
                    break;

                  case "x":
                    a = c.attributes;
                    if (((_ref2 = a.xmlns) != null ? _ref2.textContent : void 0) === Strophe.NS.MUC_USER) {
                        _ref3 = c.childNodes;
                        for (_j = 0, _len1 = _ref3.length; _j < _len1; _j++) {
                            c2 = _ref3[_j];
                            switch (c2.nodeName) {
                              case "item":
                                a = c2.attributes;
                                data.affiliation = ((_ref4 = a.affiliation) != null ? _ref4.textContent : void 0) || null;
                                data.role = ((_ref5 = a.role) != null ? _ref5.textContent : void 0) || null;
                                data.jid = ((_ref6 = a.jid) != null ? _ref6.textContent : void 0) || null;
                                data.newnick = ((_ref7 = a.nick) != null ? _ref7.textContent : void 0) || null;
                                break;

                              case "status":
                                if (c2.attributes.code) {
                                    data.states.push(c2.attributes.code.textContent);
                                }
                            }
                        }
                    }
                }
            }
            return data;
        };
        return XmppRoom;
    }();
    RoomConfig = function() {
        function RoomConfig(info) {
            this.parse = __bind(this.parse, this);
            if (info != null) {
                this.parse(info);
            }
        }
        RoomConfig.prototype.parse = function(result) {
            var attr, attrs, child, field, identity, query, _i, _j, _k, _len, _len1, _len2, _ref;
            query = result.getElementsByTagName("query")[0].childNodes;
            this.identities = [];
            this.features = [];
            this.x = [];
            for (_i = 0, _len = query.length; _i < _len; _i++) {
                child = query[_i];
                attrs = child.attributes;
                switch (child.nodeName) {
                  case "identity":
                    identity = {};
                    for (_j = 0, _len1 = attrs.length; _j < _len1; _j++) {
                        attr = attrs[_j];
                        identity[attr.name] = attr.textContent;
                    }
                    this.identities.push(identity);
                    break;

                  case "feature":
                    this.features.push(attrs["var"].textContent);
                    break;

                  case "x":
                    attrs = child.childNodes[0].attributes;
                    if (!attrs["var"].textContent === "FORM_TYPE" || !attrs.type.textContent === "hidden") {
                        break;
                    }
                    _ref = child.childNodes;
                    for (_k = 0, _len2 = _ref.length; _k < _len2; _k++) {
                        field = _ref[_k];
                        if (!!field.attributes.type) {
                            continue;
                        }
                        attrs = field.attributes;
                        this.x.push({
                            "var": attrs["var"].textContent,
                            label: attrs.label.textContent || "",
                            value: field.firstChild.textContent || ""
                        });
                    }
                }
            }
            return {
                identities: this.identities,
                features: this.features,
                x: this.x
            };
        };
        return RoomConfig;
    }();
    Occupant = function() {
        function Occupant(data, room) {
            this.room = room;
            this.update = __bind(this.update, this);
            this.admin = __bind(this.admin, this);
            this.owner = __bind(this.owner, this);
            this.revoke = __bind(this.revoke, this);
            this.member = __bind(this.member, this);
            this.ban = __bind(this.ban, this);
            this.modifyAffiliation = __bind(this.modifyAffiliation, this);
            this.deop = __bind(this.deop, this);
            this.op = __bind(this.op, this);
            this.mute = __bind(this.mute, this);
            this.voice = __bind(this.voice, this);
            this.kick = __bind(this.kick, this);
            this.modifyRole = __bind(this.modifyRole, this);
            this.update(data);
        }
        Occupant.prototype.modifyRole = function(role, reason, success_cb, error_cb) {
            return this.room.modifyRole(this.nick, role, reason, success_cb, error_cb);
        };
        Occupant.prototype.kick = function(reason, handler_cb, error_cb) {
            return this.room.kick(this.nick, reason, handler_cb, error_cb);
        };
        Occupant.prototype.voice = function(reason, handler_cb, error_cb) {
            return this.room.voice(this.nick, reason, handler_cb, error_cb);
        };
        Occupant.prototype.mute = function(reason, handler_cb, error_cb) {
            return this.room.mute(this.nick, reason, handler_cb, error_cb);
        };
        Occupant.prototype.op = function(reason, handler_cb, error_cb) {
            return this.room.op(this.nick, reason, handler_cb, error_cb);
        };
        Occupant.prototype.deop = function(reason, handler_cb, error_cb) {
            return this.room.deop(this.nick, reason, handler_cb, error_cb);
        };
        Occupant.prototype.modifyAffiliation = function(affiliation, reason, success_cb, error_cb) {
            return this.room.modifyAffiliation(this.jid, affiliation, reason, success_cb, error_cb);
        };
        Occupant.prototype.ban = function(reason, handler_cb, error_cb) {
            return this.room.ban(this.jid, reason, handler_cb, error_cb);
        };
        Occupant.prototype.member = function(reason, handler_cb, error_cb) {
            return this.room.member(this.jid, reason, handler_cb, error_cb);
        };
        Occupant.prototype.revoke = function(reason, handler_cb, error_cb) {
            return this.room.revoke(this.jid, reason, handler_cb, error_cb);
        };
        Occupant.prototype.owner = function(reason, handler_cb, error_cb) {
            return this.room.owner(this.jid, reason, handler_cb, error_cb);
        };
        Occupant.prototype.admin = function(reason, handler_cb, error_cb) {
            return this.room.admin(this.jid, reason, handler_cb, error_cb);
        };
        Occupant.prototype.update = function(data) {
            this.nick = data.nick || null;
            this.affiliation = data.affiliation || null;
            this.role = data.role || null;
            this.jid = data.jid || null;
            this.status = data.status || null;
            this.show = data.show || null;
            return this;
        };
        return Occupant;
    }();
}).call(this);

/*
  Copyright 2010, François de Metz <francois@2metz.fr>
*/
/**
 * Roster Plugin
 * Allow easily roster management
 *
 *  Features
 *  * Get roster from server
 *  * handle presence
 *  * handle roster iq
 *  * subscribe/unsubscribe
 *  * authorize/unauthorize
 *  * roster versioning (xep 237)
 */
Strophe.addConnectionPlugin("roster", {
    /** Function: init
     * Plugin init
     *
     * Parameters:
     *   (Strophe.Connection) conn - Strophe connection
     */
    init: function(conn) {
        this._connection = conn;
        this._callbacks = [];
        this._callbacks_request = [];
        /** Property: items
         * Roster items
         * [
         *    {
         *        name         : "",
         *        jid          : "",
         *        subscription : "",
         *        ask          : "",
         *        groups       : ["", ""],
         *        resources    : {
         *            myresource : {
         *                show   : "",
         *                status : "",
         *                priority : ""
         *            }
         *        }
         *    }
         * ]
         */
        items = [];
        /** Property: ver
               * current roster revision
               * always null if server doesn't support xep 237
               */
        ver = null;
        // Override the connect and attach methods to always add presence and roster handlers.
        // They are removed when the connection disconnects, so must be added on connection.
        var oldCallback, roster = this, _connect = conn.connect, _attach = conn.attach;
        var newCallback = function(status) {
            if (status == Strophe.Status.ATTACHED || status == Strophe.Status.CONNECTED) {
                try {
                    // Presence subscription
                    conn.addHandler(roster._onReceivePresence.bind(roster), null, "presence", null, null, null);
                    conn.addHandler(roster._onReceiveIQ.bind(roster), Strophe.NS.ROSTER, "iq", "set", null, null);
                } catch (e) {
                    Strophe.error(e);
                }
            }
            if (oldCallback !== null) oldCallback.apply(this, arguments);
        };
        conn.connect = function(jid, pass, callback, wait, hold, route) {
            oldCallback = callback;
            if (typeof jid == "undefined") jid = null;
            if (typeof pass == "undefined") pass = null;
            callback = newCallback;
            _connect.apply(conn, [ jid, pass, callback, wait, hold, route ]);
        };
        conn.attach = function(jid, sid, rid, callback, wait, hold, wind) {
            oldCallback = callback;
            if (typeof jid == "undefined") jid = null;
            if (typeof sid == "undefined") sid = null;
            if (typeof rid == "undefined") rid = null;
            callback = newCallback;
            _attach.apply(conn, [ jid, sid, rid, callback, wait, hold, wind ]);
        };
        Strophe.addNamespace("ROSTER_VER", "urn:xmpp:features:rosterver");
        Strophe.addNamespace("NICK", "http://jabber.org/protocol/nick");
    },
    /** Function: supportVersioning
     * return true if roster versioning is enabled on server
     */
    supportVersioning: function() {
        return this._connection.features && this._connection.features.getElementsByTagName("ver").length > 0;
    },
    /** Function: get
     * Get Roster on server
     *
     * Parameters:
     *   (Function) userCallback - callback on roster result
     *   (String) ver - current rev of roster
     *      (only used if roster versioning is enabled)
     *   (Array) items - initial items of ver
     *      (only used if roster versioning is enabled)
     *     In browser context you can use sessionStorage
     *     to store your roster in json (JSON.stringify())
     */
    get: function(userCallback, ver, items) {
        var attrs = {
            xmlns: Strophe.NS.ROSTER
        };
        this.items = [];
        if (this.supportVersioning()) {
            // empty rev because i want an rev attribute in the result
            attrs.ver = ver || "";
            this.items = items || [];
        }
        var iq = $iq({
            type: "get",
            id: this._connection.getUniqueId("roster")
        }).c("query", attrs);
        return this._connection.sendIQ(iq, this._onReceiveRosterSuccess.bind(this, userCallback), this._onReceiveRosterError.bind(this, userCallback));
    },
    /** Function: registerCallback
     * register callback on roster (presence and iq)
     *
     * Parameters:
     *   (Function) call_back
     */
    registerCallback: function(call_back) {
        this._callbacks.push(call_back);
    },
    registerRequestCallback: function(call_back) {
        this._callbacks_request.push(call_back);
    },
    /** Function: findItem
     * Find item by JID
     *
     * Parameters:
     *     (String) jid
     */
    findItem: function(jid) {
        for (var i = 0; i < this.items.length; i++) {
            if (this.items[i] && this.items[i].jid == jid) {
                return this.items[i];
            }
        }
        return false;
    },
    /** Function: removeItem
     * Remove item by JID
     *
     * Parameters:
     *     (String) jid
     */
    removeItem: function(jid) {
        for (var i = 0; i < this.items.length; i++) {
            if (this.items[i] && this.items[i].jid == jid) {
                this.items.splice(i, 1);
                return true;
            }
        }
        return false;
    },
    /** Function: subscribe
     * Subscribe presence
     *
     * Parameters:
     *     (String) jid
     *     (String) message (optional)
     *     (String) nick  (optional)
     */
    subscribe: function(jid, message, nick) {
        var pres = $pres({
            to: jid,
            type: "subscribe"
        });
        if (message && message !== "") {
            pres.c("status").t(message).up();
        }
        if (nick && nick !== "") {
            pres.c("nick", {
                xmlns: Strophe.NS.NICK
            }).t(nick).up();
        }
        this._connection.send(pres);
    },
    /** Function: unsubscribe
     * Unsubscribe presence
     *
     * Parameters:
     *     (String) jid
     *     (String) message
     */
    unsubscribe: function(jid, message) {
        var pres = $pres({
            to: jid,
            type: "unsubscribe"
        });
        if (message && message !== "") pres.c("status").t(message);
        this._connection.send(pres);
    },
    /** Function: authorize
     * Authorize presence subscription
     *
     * Parameters:
     *     (String) jid
     *     (String) message
     */
    authorize: function(jid, message) {
        var pres = $pres({
            to: jid,
            type: "subscribed"
        });
        if (message && message !== "") pres.c("status").t(message);
        this._connection.send(pres);
    },
    /** Function: unauthorize
     * Unauthorize presence subscription
     *
     * Parameters:
     *     (String) jid
     *     (String) message
     */
    unauthorize: function(jid, message) {
        var pres = $pres({
            to: jid,
            type: "unsubscribed"
        });
        if (message && message !== "") pres.c("status").t(message);
        this._connection.send(pres);
    },
    /** Function: add
     * Add roster item
     *
     * Parameters:
     *   (String) jid - item jid
     *   (String) name - name
     *   (Array) groups
     *   (Function) call_back
     */
    add: function(jid, name, groups, call_back) {
        var iq = $iq({
            type: "set"
        }).c("query", {
            xmlns: Strophe.NS.ROSTER
        }).c("item", {
            jid: jid,
            name: name
        });
        for (var i = 0; i < groups.length; i++) {
            iq.c("group").t(groups[i]).up();
        }
        this._connection.sendIQ(iq, call_back, call_back);
    },
    /** Function: update
     * Update roster item
     *
     * Parameters:
     *   (String) jid - item jid
     *   (String) name - name
     *   (Array) groups
     *   (Function) call_back
     */
    update: function(jid, name, groups, call_back) {
        var item = this.findItem(jid);
        if (!item) {
            throw "item not found";
        }
        var newName = name || item.name;
        var newGroups = groups || item.groups;
        var iq = $iq({
            type: "set"
        }).c("query", {
            xmlns: Strophe.NS.ROSTER
        }).c("item", {
            jid: item.jid,
            name: newName
        });
        for (var i = 0; i < newGroups.length; i++) {
            iq.c("group").t(newGroups[i]).up();
        }
        return this._connection.sendIQ(iq, call_back, call_back);
    },
    /** Function: remove
     * Remove roster item
     *
     * Parameters:
     *   (String) jid - item jid
     *   (Function) call_back
     */
    remove: function(jid, call_back) {
        var item = this.findItem(jid);
        if (!item) {
            throw "item not found";
        }
        var iq = $iq({
            type: "set"
        }).c("query", {
            xmlns: Strophe.NS.ROSTER
        }).c("item", {
            jid: item.jid,
            subscription: "remove"
        });
        this._connection.sendIQ(iq, call_back, call_back);
    },
    /** PrivateFunction: _onReceiveRosterSuccess
     *
     */
    _onReceiveRosterSuccess: function(userCallback, stanza) {
        this._updateItems(stanza);
        this._call_backs(this.items);
        userCallback(this.items);
    },
    /** PrivateFunction: _onReceiveRosterError
     *
     */
    _onReceiveRosterError: function(userCallback, stanza) {
        userCallback(this.items);
    },
    /** PrivateFunction: _onReceivePresence
     * Handle presence
     */
    _onReceivePresence: function(presence) {
        // TODO: from is optional
        var jid = presence.getAttribute("from");
        var from = Strophe.getBareJidFromJid(jid);
        var item = this.findItem(from);
        var type = presence.getAttribute("type");
        // not in roster
        if (!item) {
            // if 'friend request' presence
            if (type === "subscribe") {
                this._call_backs_request(from);
            }
            return true;
        }
        if (type == "unavailable") {
            delete item.resources[Strophe.getResourceFromJid(jid)];
        } else if (!type) {
            // TODO: add timestamp
            item.resources[Strophe.getResourceFromJid(jid)] = {
                show: presence.getElementsByTagName("show").length !== 0 ? Strophe.getText(presence.getElementsByTagName("show")[0]) : "",
                status: presence.getElementsByTagName("status").length !== 0 ? Strophe.getText(presence.getElementsByTagName("status")[0]) : "",
                priority: presence.getElementsByTagName("priority").length !== 0 ? Strophe.getText(presence.getElementsByTagName("priority")[0]) : ""
            };
        } else {
            // Stanza is not a presence notification. (It's probably a subscription type stanza.)
            return true;
        }
        this._call_backs(this.items, item);
        return true;
    },
    /** PrivateFunction: _call_backs_request
     * call all the callbacks waiting for 'friend request' presences
     */
    _call_backs_request: function(from) {
        for (var i = 0; i < this._callbacks_request.length; i++) // [].forEach my love ...
        {
            this._callbacks_request[i](from);
        }
    },
    /** PrivateFunction: _call_backs
     *
     */
    _call_backs: function(items, item) {
        for (var i = 0; i < this._callbacks.length; i++) // [].forEach my love ...
        {
            this._callbacks[i](items, item);
        }
    },
    /** PrivateFunction: _onReceiveIQ
     * Handle roster push.
     */
    _onReceiveIQ: function(iq) {
        var id = iq.getAttribute("id");
        var from = iq.getAttribute("from");
        // Receiving client MUST ignore stanza unless it has no from or from = user's JID.
        if (from && from !== "" && from != this._connection.jid && from != Strophe.getBareJidFromJid(this._connection.jid)) return true;
        var iqresult = $iq({
            type: "result",
            id: id,
            from: this._connection.jid
        });
        this._connection.send(iqresult);
        this._updateItems(iq);
        return true;
    },
    /** PrivateFunction: _updateItems
     * Update items from iq
     */
    _updateItems: function(iq) {
        var query = iq.getElementsByTagName("query");
        if (query.length !== 0) {
            this.ver = query.item(0).getAttribute("ver");
            var self = this;
            Strophe.forEachChild(query.item(0), "item", function(item) {
                self._updateItem(item);
            });
        }
    },
    /** PrivateFunction: _updateItem
     * Update internal representation of roster item
     */
    _updateItem: function(item) {
        var jid = item.getAttribute("jid");
        var name = item.getAttribute("name");
        var subscription = item.getAttribute("subscription");
        var ask = item.getAttribute("ask");
        var groups = [];
        Strophe.forEachChild(item, "group", function(group) {
            groups.push(Strophe.getText(group));
        });
        if (subscription == "remove") {
            this.removeItem(jid);
            this._call_backs(this.items, {
                jid: jid,
                subscription: "remove"
            });
            return;
        }
        item = this.findItem(jid);
        if (!item) {
            item = {
                name: name,
                jid: jid,
                subscription: subscription,
                ask: ask,
                groups: groups,
                resources: {}
            };
            this.items.push(item);
        } else {
            item.name = name;
            item.subscription = subscription;
            item.ask = ask;
            item.groups = groups;
        }
        this._call_backs(this.items, item);
    }
});

/*
  Copyright 2010, François de Metz <francois@2metz.fr>
*/
/**
 * Disco Strophe Plugin
 * Implement http://xmpp.org/extensions/xep-0030.html
 * TODO: manage node hierarchies, and node on info request
 */
Strophe.addConnectionPlugin("disco", {
    _connection: null,
    _identities: [],
    _features: [],
    _items: [],
    /** Function: init
     * Plugin init
     *
     * Parameters:
     *   (Strophe.Connection) conn - Strophe connection
     */
    init: function(conn) {
        this._connection = conn;
        this._identities = [];
        this._features = [];
        this._items = [];
        // disco info
        conn.addHandler(this._onDiscoInfo.bind(this), Strophe.NS.DISCO_INFO, "iq", "get", null, null);
        // disco items
        conn.addHandler(this._onDiscoItems.bind(this), Strophe.NS.DISCO_ITEMS, "iq", "get", null, null);
    },
    /** Function: addIdentity
     * See http://xmpp.org/registrar/disco-categories.html
     * Parameters:
     *   (String) category - category of identity (like client, automation, etc ...)
     *   (String) type - type of identity (like pc, web, bot , etc ...)
     *   (String) name - name of identity in natural language
     *   (String) lang - lang of name parameter
     *
     * Returns:
     *   Boolean
     */
    addIdentity: function(category, type, name, lang) {
        for (var i = 0; i < this._identities.length; i++) {
            if (this._identities[i].category == category && this._identities[i].type == type && this._identities[i].name == name && this._identities[i].lang == lang) {
                return false;
            }
        }
        this._identities.push({
            category: category,
            type: type,
            name: name,
            lang: lang
        });
        return true;
    },
    /** Function: addFeature
     *
     * Parameters:
     *   (String) var_name - feature name (like jabber:iq:version)
     *
     * Returns:
     *   boolean
     */
    addFeature: function(var_name) {
        for (var i = 0; i < this._features.length; i++) {
            if (this._features[i] == var_name) return false;
        }
        this._features.push(var_name);
        return true;
    },
    /** Function: removeFeature
     *
     * Parameters:
     *   (String) var_name - feature name (like jabber:iq:version)
     *
     * Returns:
     *   boolean
     */
    removeFeature: function(var_name) {
        for (var i = 0; i < this._features.length; i++) {
            if (this._features[i] === var_name) {
                this._features.splice(i, 1);
                return true;
            }
        }
        return false;
    },
    /** Function: addItem
     *
     * Parameters:
     *   (String) jid
     *   (String) name
     *   (String) node
     *   (Function) call_back
     *
     * Returns:
     *   boolean
     */
    addItem: function(jid, name, node, call_back) {
        if (node && !call_back) return false;
        this._items.push({
            jid: jid,
            name: name,
            node: node,
            call_back: call_back
        });
        return true;
    },
    /** Function: info
     * Info query
     *
     * Parameters:
     *   (Function) call_back
     *   (String) jid
     *   (String) node
     */
    info: function(jid, node, success, error, timeout) {
        var attrs = {
            xmlns: Strophe.NS.DISCO_INFO
        };
        if (node) attrs.node = node;
        var info = $iq({
            from: this._connection.jid,
            to: jid,
            type: "get"
        }).c("query", attrs);
        this._connection.sendIQ(info, success, error, timeout);
    },
    /** Function: items
     * Items query
     *
     * Parameters:
     *   (Function) call_back
     *   (String) jid
     *   (String) node
     */
    items: function(jid, node, success, error, timeout) {
        var attrs = {
            xmlns: Strophe.NS.DISCO_ITEMS
        };
        if (node) attrs.node = node;
        var items = $iq({
            from: this._connection.jid,
            to: jid,
            type: "get"
        }).c("query", attrs);
        this._connection.sendIQ(items, success, error, timeout);
    },
    /** PrivateFunction: _buildIQResult
     */
    _buildIQResult: function(stanza, query_attrs) {
        var id = stanza.getAttribute("id");
        var from = stanza.getAttribute("from");
        var iqresult = $iq({
            type: "result",
            id: id
        });
        if (from !== null) {
            iqresult.attrs({
                to: from
            });
        }
        return iqresult.c("query", query_attrs);
    },
    /** PrivateFunction: _onDiscoInfo
     * Called when receive info request
     */
    _onDiscoInfo: function(stanza) {
        var node = stanza.getElementsByTagName("query")[0].getAttribute("node");
        var attrs = {
            xmlns: Strophe.NS.DISCO_INFO
        };
        if (node) {
            attrs.node = node;
        }
        var iqresult = this._buildIQResult(stanza, attrs);
        for (var i = 0; i < this._identities.length; i++) {
            var attrs = {
                category: this._identities[i].category,
                type: this._identities[i].type
            };
            if (this._identities[i].name) attrs.name = this._identities[i].name;
            if (this._identities[i].lang) attrs["xml:lang"] = this._identities[i].lang;
            iqresult.c("identity", attrs).up();
        }
        for (var i = 0; i < this._features.length; i++) {
            iqresult.c("feature", {
                "var": this._features[i]
            }).up();
        }
        this._connection.send(iqresult.tree());
        return true;
    },
    /** PrivateFunction: _onDiscoItems
     * Called when receive items request
     */
    _onDiscoItems: function(stanza) {
        var query_attrs = {
            xmlns: Strophe.NS.DISCO_ITEMS
        };
        var node = stanza.getElementsByTagName("query")[0].getAttribute("node");
        if (node) {
            query_attrs.node = node;
            var items = [];
            for (var i = 0; i < this._items.length; i++) {
                if (this._items[i].node == node) {
                    items = this._items[i].call_back(stanza);
                    break;
                }
            }
        } else {
            var items = this._items;
        }
        var iqresult = this._buildIQResult(stanza, query_attrs);
        for (var i = 0; i < items.length; i++) {
            var attrs = {
                jid: items[i].jid
            };
            if (items[i].name) attrs.name = items[i].name;
            if (items[i].node) attrs.node = items[i].node;
            iqresult.c("item", attrs).up();
        }
        this._connection.send(iqresult.tree());
        return true;
    }
});

/**
 * Entity Capabilities (XEP-0115)
 *
 * Depends on disco plugin.
 *
 * See: http://xmpp.org/extensions/xep-0115.html
 *
 * Authors:
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   - Michael Weibel <michael.weibel@gmail.com>
 */
Strophe.addConnectionPlugin("caps", {
    /** Constant: HASH
	 * Hash used
	 *
	 * Currently only sha-1 is supported.
	 */
    HASH: "sha-1",
    /** Variable: node
	 * Client which is being used.
	 *
	 * Can be overwritten as soon as Strophe has been initialized.
	 */
    node: "http://strophe.im/strophejs/",
    /** PrivateVariable: _ver
	 * Own generated version string
	 */
    _ver: "",
    /** PrivateVariable: _connection
	 * Strophe connection
	 */
    _connection: null,
    /** PrivateVariable: _knownCapabilities
	 * A hashtable containing version-strings and their capabilities, serialized
	 * as string.
	 *
	 * TODO: Maybe those caps shouldn't be serialized.
	 */
    _knownCapabilities: {},
    /** PrivateVariable: _jidVerIndex
	 * A hashtable containing jids and their versions for better lookup of capabilities.
	 */
    _jidVerIndex: {},
    /** Function: init
	 * Initialize plugin:
	 *   - Add caps namespace
	 *   - Add caps feature to disco plugin
	 *   - Add handler for caps stanzas
	 *
	 * Parameters:
	 *   (Strophe.Connection) conn - Strophe connection
	 */
    init: function(conn) {
        this._connection = conn;
        Strophe.addNamespace("CAPS", "http://jabber.org/protocol/caps");
        if (!this._connection.disco) {
            throw "Caps plugin requires the disco plugin to be installed.";
        }
        this._connection.disco.addFeature(Strophe.NS.CAPS);
        this._connection.addHandler(this._delegateCapabilities.bind(this), Strophe.NS.CAPS);
    },
    /** Function: generateCapsAttrs
	 * Returns the attributes for generating the "c"-stanza containing the own version
	 *
	 * Returns:
	 *   (Object) - attributes
	 */
    generateCapsAttrs: function() {
        return {
            xmlns: Strophe.NS.CAPS,
            hash: this.HASH,
            node: this.node,
            ver: this.generateVer()
        };
    },
    /** Function: generateVer
	 * Returns the base64 encoded version string (encoded itself with sha1)
	 *
	 * Returns:
	 *   (String) - version
	 */
    generateVer: function() {
        if (this._ver !== "") {
            return this._ver;
        }
        var ver = "", identities = this._connection.disco._identities.sort(this._sortIdentities), identitiesLen = identities.length, features = this._connection.disco._features.sort(), featuresLen = features.length;
        for (var i = 0; i < identitiesLen; i++) {
            var curIdent = identities[i];
            ver += curIdent.category + "/" + curIdent.type + "/" + curIdent.lang + "/" + curIdent.name + "<";
        }
        for (var i = 0; i < featuresLen; i++) {
            ver += features[i] + "<";
        }
        this._ver = b64_sha1(ver);
        return this._ver;
    },
    /** Function: getCapabilitiesByJid
	 * Returns serialized capabilities of a jid (if available).
	 * Otherwise null.
	 *
	 * Parameters:
	 *   (String) jid - Jabber id
	 *
	 * Returns:
	 *   (String|null) - capabilities, serialized; or null when not available.
	 */
    getCapabilitiesByJid: function(jid) {
        if (this._jidVerIndex[jid]) {
            return this._knownCapabilities[this._jidVerIndex[jid]];
        }
        return null;
    },
    /** PrivateFunction: _delegateCapabilities
	 * Checks if the version has already been saved.
	 * If yes: do nothing.
	 * If no: Request capabilities
	 *
	 * Parameters:
	 *   (Strophe.Builder) stanza - Stanza
	 *
	 * Returns:
	 *   (Boolean)
	 */
    _delegateCapabilities: function(stanza) {
        var from = stanza.getAttribute("from"), c = stanza.querySelector("c"), ver = c.getAttribute("ver"), node = c.getAttribute("node");
        if (!this._knownCapabilities[ver]) {
            return this._requestCapabilities(from, node, ver);
        } else {
            this._jidVerIndex[from] = ver;
        }
        if (!this._jidVerIndex[from] || !this._jidVerIndex[from] !== ver) {
            this._jidVerIndex[from] = ver;
        }
        return true;
    },
    /** PrivateFunction: _requestCapabilities
	 * Requests capabilities from the one which sent the caps-info stanza.
	 * This is done using disco info.
	 *
	 * Additionally, it registers a handler for handling the reply.
	 *
	 * Parameters:
	 *   (String) to - Destination jid
	 *   (String) node - Node attribute of the caps-stanza
	 *   (String) ver - Version of the caps-stanza
	 *
	 * Returns:
	 *   (Boolean) - true
	 */
    _requestCapabilities: function(to, node, ver) {
        if (to !== this._connection.jid) {
            var id = this._connection.disco.info(to, node + "#" + ver);
            this._connection.addHandler(this._handleDiscoInfoReply.bind(this), Strophe.NS.DISCO_INFO, "iq", "result", id, to);
        }
        return true;
    },
    /** PrivateFunction: _handleDiscoInfoReply
	 * Parses the disco info reply and adds the version & it's capabilities to the _knownCapabilities variable.
	 * Additionally, it adds the jid & the version to the _jidVerIndex variable for a better lookup.
	 *
	 * Parameters:
	 *   (Strophe.Builder) stanza - Disco info stanza
	 *
	 * Returns:
	 *   (Boolean) - false, to automatically remove the handler.
	 */
    _handleDiscoInfoReply: function(stanza) {
        var query = stanza.querySelector("query"), node = query.getAttribute("node").split("#"), ver = node[1], from = stanza.getAttribute("from");
        if (!this._knownCapabilities[ver]) {
            var childNodes = query.childNodes, childNodesLen = childNodes.length;
            this._knownCapabilities[ver] = [];
            for (var i = 0; i < childNodesLen; i++) {
                var node = childNodes[i];
                this._knownCapabilities[ver].push({
                    name: node.nodeName,
                    attributes: node.attributes
                });
            }
            this._jidVerIndex[from] = ver;
        } else if (!this._jidVerIndex[from] || !this._jidVerIndex[from] !== ver) {
            this._jidVerIndex[from] = ver;
        }
        return false;
    },
    /** PrivateFunction: _sortIdentities
	 * Sorts two identities according the sorting requirements in XEP-0115.
	 *
	 * Parameters:
	 *   (Object) a - Identity a
	 *   (Object) b - Identity b
	 *
	 * Returns:
	 *   (Integer) - 1, 0 or -1; according to which one's greater.
	 */
    _sortIdentities: function(a, b) {
        if (a.category > b.category) {
            return 1;
        }
        if (a.category < b.category) {
            return -1;
        }
        if (a.type > b.type) {
            return 1;
        }
        if (a.type < b.type) {
            return -1;
        }
        if (a.lang > b.lang) {
            return 1;
        }
        if (a.lang < b.lang) {
            return -1;
        }
        return 0;
    }
});

/*
  mustache.js — Logic-less templates in JavaScript

  See http://mustache.github.com/ for more info.
*/
var Mustache = function() {
    var Renderer = function() {};
    Renderer.prototype = {
        otag: "{{",
        ctag: "}}",
        pragmas: {},
        buffer: [],
        pragmas_implemented: {
            "IMPLICIT-ITERATOR": true
        },
        context: {},
        render: function(template, context, partials, in_recursion) {
            // reset buffer & set context
            if (!in_recursion) {
                this.context = context;
                this.buffer = [];
            }
            // fail fast
            if (!this.includes("", template)) {
                if (in_recursion) {
                    return template;
                } else {
                    this.send(template);
                    return;
                }
            }
            template = this.render_pragmas(template);
            var html = this.render_section(template, context, partials);
            if (in_recursion) {
                return this.render_tags(html, context, partials, in_recursion);
            }
            this.render_tags(html, context, partials, in_recursion);
        },
        /*
      Sends parsed lines
    */
        send: function(line) {
            if (line != "") {
                this.buffer.push(line);
            }
        },
        /*
      Looks for %PRAGMAS
    */
        render_pragmas: function(template) {
            // no pragmas
            if (!this.includes("%", template)) {
                return template;
            }
            var that = this;
            var regex = new RegExp(this.otag + "%([\\w-]+) ?([\\w]+=[\\w]+)?" + this.ctag);
            return template.replace(regex, function(match, pragma, options) {
                if (!that.pragmas_implemented[pragma]) {
                    throw {
                        message: "This implementation of mustache doesn't understand the '" + pragma + "' pragma"
                    };
                }
                that.pragmas[pragma] = {};
                if (options) {
                    var opts = options.split("=");
                    that.pragmas[pragma][opts[0]] = opts[1];
                }
                return "";
            });
        },
        /*
      Tries to find a partial in the curent scope and render it
    */
        render_partial: function(name, context, partials) {
            name = this.trim(name);
            if (!partials || partials[name] === undefined) {
                throw {
                    message: "unknown_partial '" + name + "'"
                };
            }
            if (typeof context[name] != "object") {
                return this.render(partials[name], context, partials, true);
            }
            return this.render(partials[name], context[name], partials, true);
        },
        /*
      Renders inverted (^) and normal (#) sections
    */
        render_section: function(template, context, partials) {
            if (!this.includes("#", template) && !this.includes("^", template)) {
                return template;
            }
            var that = this;
            // CSW - Added "+?" so it finds the tighest bound, not the widest
            var regex = new RegExp(this.otag + "(\\^|\\#)\\s*(.+)\\s*" + this.ctag + "\n*([\\s\\S]+?)" + this.otag + "\\/\\s*\\2\\s*" + this.ctag + "\\s*", "mg");
            // for each {{#foo}}{{/foo}} section do...
            return template.replace(regex, function(match, type, name, content) {
                var value = that.find(name, context);
                if (type == "^") {
                    // inverted section
                    if (!value || that.is_array(value) && value.length === 0) {
                        // false or empty list, render it
                        return that.render(content, context, partials, true);
                    } else {
                        return "";
                    }
                } else if (type == "#") {
                    // normal section
                    if (that.is_array(value)) {
                        // Enumerable, Let's loop!
                        return that.map(value, function(row) {
                            return that.render(content, that.create_context(row), partials, true);
                        }).join("");
                    } else if (that.is_object(value)) {
                        // Object, Use it as subcontext!
                        return that.render(content, that.create_context(value), partials, true);
                    } else if (typeof value === "function") {
                        // higher order section
                        return value.call(context, content, function(text) {
                            return that.render(text, context, partials, true);
                        });
                    } else if (value) {
                        // boolean section
                        return that.render(content, context, partials, true);
                    } else {
                        return "";
                    }
                }
            });
        },
        /*
      Replace {{foo}} and friends with values from our view
    */
        render_tags: function(template, context, partials, in_recursion) {
            // tit for tat
            var that = this;
            var new_regex = function() {
                return new RegExp(that.otag + "(=|!|>|\\{|%)?([^\\/#\\^]+?)\\1?" + that.ctag + "+", "g");
            };
            var regex = new_regex();
            var tag_replace_callback = function(match, operator, name) {
                switch (operator) {
                  case "!":
                    // ignore comments
                    return "";

                  case "=":
                    // set new delimiters, rebuild the replace regexp
                    that.set_delimiters(name);
                    regex = new_regex();
                    return "";

                  case ">":
                    // render partial
                    return that.render_partial(name, context, partials);

                  case "{":
                    // the triple mustache is unescaped
                    return that.find(name, context);

                  default:
                    // escape the value
                    return that.escape(that.find(name, context));
                }
            };
            var lines = template.split("\n");
            for (var i = 0; i < lines.length; i++) {
                lines[i] = lines[i].replace(regex, tag_replace_callback, this);
                if (!in_recursion) {
                    this.send(lines[i]);
                }
            }
            if (in_recursion) {
                return lines.join("\n");
            }
        },
        set_delimiters: function(delimiters) {
            var dels = delimiters.split(" ");
            this.otag = this.escape_regex(dels[0]);
            this.ctag = this.escape_regex(dels[1]);
        },
        escape_regex: function(text) {
            // thank you Simon Willison
            if (!arguments.callee.sRE) {
                var specials = [ "/", ".", "*", "+", "?", "|", "(", ")", "[", "]", "{", "}", "\\" ];
                arguments.callee.sRE = new RegExp("(\\" + specials.join("|\\") + ")", "g");
            }
            return text.replace(arguments.callee.sRE, "\\$1");
        },
        /*
      find `name` in current `context`. That is find me a value
      from the view object
    */
        find: function(name, context) {
            name = this.trim(name);
            // Checks whether a value is thruthy or false or 0
            function is_kinda_truthy(bool) {
                return bool === false || bool === 0 || bool;
            }
            var value;
            if (is_kinda_truthy(context[name])) {
                value = context[name];
            } else if (is_kinda_truthy(this.context[name])) {
                value = this.context[name];
            }
            if (typeof value === "function") {
                return value.apply(context);
            }
            if (value !== undefined) {
                return value;
            }
            // silently ignore unkown variables
            return "";
        },
        // Utility methods
        /* includes tag */
        includes: function(needle, haystack) {
            return haystack.indexOf(this.otag + needle) != -1;
        },
        /*
      Does away with nasty characters
    */
        escape: function(s) {
            s = String(s === null ? "" : s);
            return s.replace(/&(?!\w+;)|["<>\\]/g, function(s) {
                switch (s) {
                  case "&":
                    return "&amp;";

                  case "\\":
                    return "\\\\";

                  case '"':
                    return '"';

                  case "<":
                    return "&lt;";

                  case ">":
                    return "&gt;";

                  default:
                    return s;
                }
            });
        },
        // by @langalex, support for arrays of strings
        create_context: function(_context) {
            if (this.is_object(_context)) {
                return _context;
            } else {
                var iterator = ".";
                if (this.pragmas["IMPLICIT-ITERATOR"]) {
                    iterator = this.pragmas["IMPLICIT-ITERATOR"].iterator;
                }
                var ctx = {};
                ctx[iterator] = _context;
                return ctx;
            }
        },
        is_object: function(a) {
            return a && typeof a == "object";
        },
        is_array: function(a) {
            return Object.prototype.toString.call(a) === "[object Array]";
        },
        /*
      Gets rid of leading and trailing whitespace
    */
        trim: function(s) {
            return s.replace(/^\s*|\s*$/g, "");
        },
        /*
      Why, why, why? Because IE. Cry, cry cry.
    */
        map: function(array, fn) {
            if (typeof array.map == "function") {
                return array.map(fn);
            } else {
                var r = [];
                var l = array.length;
                for (var i = 0; i < l; i++) {
                    r.push(fn(array[i]));
                }
                return r;
            }
        }
    };
    return {
        name: "mustache.js",
        version: "0.3.0",
        /*
      Turns a template and view into HTML
    */
        to_html: function(template, view, partials, send_fun) {
            var renderer = new Renderer();
            if (send_fun) {
                renderer.send = send_fun;
            }
            renderer.render(template, view, partials);
            if (!send_fun) {
                return renderer.buffer.join("\n");
            }
        }
    };
}();

/*!
 * jQuery i18n plugin
 * @requires jQuery v1.1 or later
 *
 * See https://github.com/recurser/jquery-i18n
 *
 * Licensed under the MIT license.
 *
 * Version: 1.1.1 (Sun, 05 Jan 2014 05:26:50 GMT)
 */
(function($) {
    /**
   * i18n provides a mechanism for translating strings using a jscript dictionary.
   *
   */
    var __slice = Array.prototype.slice;
    /*
   * i18n property list
   */
    var i18n = {
        dict: null,
        /**
     * load()
     *
     * Load translations.
     *
     * @param  property_list i18n_dict : The dictionary to use for translation.
     */
        load: function(i18n_dict) {
            if (this.dict !== null) {
                $.extend(this.dict, i18n_dict);
            } else {
                this.dict = i18n_dict;
            }
        },
        /**
     * _()
     *
     * Looks the given string up in the dictionary and returns the translation if
     * one exists. If a translation is not found, returns the original word.
     *
     * @param  string str           : The string to translate.
     * @param  property_list params.. : params for using printf() on the string.
     *
     * @return string               : Translated word.
     */
        _: function(str) {
            dict = this.dict;
            if (dict && dict.hasOwnProperty(str)) {
                str = dict[str];
            }
            args = __slice.call(arguments);
            args[0] = str;
            // Substitute any params.
            return this.printf.apply(this, args);
        },
        /*
     * printf()
     *
     * Substitutes %s with parameters given in list. %%s is used to escape %s.
     *
     * @param  string str    : String to perform printf on.
     * @param  string args   : Array of arguments for printf.
     *
     * @return string result : Substituted string
     */
        printf: function(str, args) {
            if (arguments.length < 2) return str;
            args = $.isArray(args) ? args : __slice.call(arguments, 1);
            return str.replace(/([^%]|^)%(?:(\d+)\$)?s/g, function(p0, p, position) {
                if (position) {
                    return p + args[parseInt(position) - 1];
                }
                return p + args.shift();
            }).replace(/%%s/g, "%s");
        }
    };
    /*
   * _t()
   *
   * Allows you to translate a jQuery selector.
   *
   * eg $('h1')._t('some text')
   *
   * @param  string str           : The string to translate .
   * @param  property_list params : Params for using printf() on the string.
   *
   * @return element              : Chained and translated element(s).
  */
    $.fn._t = function(str, params) {
        return $(this).html(i18n._.apply(i18n, arguments));
    };
    $.i18n = i18n;
})(jQuery);

/*
 * Date Format 1.2.3
 * (c) 2007-2009 Steven Levithan <stevenlevithan.com>
 * MIT license
 *
 * Includes enhancements by Scott Trenda <scott.trenda.net>
 * and Kris Kowal <cixar.com/~kris.kowal/>
 *
 * Accepts a date, a mask, or a date and a mask.
 * Returns a formatted version of the given date.
 * The date defaults to the current date/time.
 * The mask defaults to dateFormat.masks.default.
 *
 * @link http://blog.stevenlevithan.com/archives/date-time-format
 */
var dateFormat = function() {
    var token = /d{1,4}|m{1,4}|yy(?:yy)?|([HhMsTt])\1?|[LloSZ]|"[^"]*"|'[^']*'/g, timezone = /\b(?:[PMCEA][SDP]T|(?:Pacific|Mountain|Central|Eastern|Atlantic) (?:Standard|Daylight|Prevailing) Time|(?:GMT|UTC)(?:[-+]\d{4})?)\b/g, timezoneClip = /[^-+\dA-Z]/g, pad = function(val, len) {
        val = String(val);
        len = len || 2;
        while (val.length < len) val = "0" + val;
        return val;
    };
    // Regexes and supporting functions are cached through closure
    return function(date, mask, utc) {
        var dF = dateFormat;
        // You can't provide utc if you skip other args (use the "UTC:" mask prefix)
        if (arguments.length == 1 && Object.prototype.toString.call(date) == "[object String]" && !/\d/.test(date)) {
            mask = date;
            date = undefined;
        }
        // Passing date through Date applies Date.parse, if necessary
        date = date ? new Date(date) : new Date();
        if (isNaN(date)) throw SyntaxError("invalid date");
        mask = String(dF.masks[mask] || mask || dF.masks["default"]);
        // Allow setting the utc argument via the mask
        if (mask.slice(0, 4) == "UTC:") {
            mask = mask.slice(4);
            utc = true;
        }
        var _ = utc ? "getUTC" : "get", d = date[_ + "Date"](), D = date[_ + "Day"](), m = date[_ + "Month"](), y = date[_ + "FullYear"](), H = date[_ + "Hours"](), M = date[_ + "Minutes"](), s = date[_ + "Seconds"](), L = date[_ + "Milliseconds"](), o = utc ? 0 : date.getTimezoneOffset(), flags = {
            d: d,
            dd: pad(d),
            ddd: dF.i18n.dayNames[D],
            dddd: dF.i18n.dayNames[D + 7],
            m: m + 1,
            mm: pad(m + 1),
            mmm: dF.i18n.monthNames[m],
            mmmm: dF.i18n.monthNames[m + 12],
            yy: String(y).slice(2),
            yyyy: y,
            h: H % 12 || 12,
            hh: pad(H % 12 || 12),
            H: H,
            HH: pad(H),
            M: M,
            MM: pad(M),
            s: s,
            ss: pad(s),
            l: pad(L, 3),
            L: pad(L > 99 ? Math.round(L / 10) : L),
            t: H < 12 ? "a" : "p",
            tt: H < 12 ? "am" : "pm",
            T: H < 12 ? "A" : "P",
            TT: H < 12 ? "AM" : "PM",
            Z: utc ? "UTC" : (String(date).match(timezone) || [ "" ]).pop().replace(timezoneClip, ""),
            o: (o > 0 ? "-" : "+") + pad(Math.floor(Math.abs(o) / 60) * 100 + Math.abs(o) % 60, 4),
            S: [ "th", "st", "nd", "rd" ][d % 10 > 3 ? 0 : (d % 100 - d % 10 != 10) * d % 10]
        };
        return mask.replace(token, function($0) {
            return $0 in flags ? flags[$0] : $0.slice(1, $0.length - 1);
        });
    };
}();

// Some common format strings
dateFormat.masks = {
    "default": "ddd mmm dd yyyy HH:MM:ss",
    shortDate: "m/d/yy",
    mediumDate: "mmm d, yyyy",
    longDate: "mmmm d, yyyy",
    fullDate: "dddd, mmmm d, yyyy",
    shortTime: "h:MM TT",
    mediumTime: "h:MM:ss TT",
    longTime: "h:MM:ss TT Z",
    isoDate: "yyyy-mm-dd",
    isoTime: "HH:MM:ss",
    isoDateTime: "yyyy-mm-dd'T'HH:MM:ss",
    isoUtcDateTime: "UTC:yyyy-mm-dd'T'HH:MM:ss'Z'"
};

// Internationalization strings
dateFormat.i18n = {
    dayNames: [ "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" ],
    monthNames: [ "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" ]
};

// For convenience...
Date.prototype.format = function(mask, utc) {
    return dateFormat(this, mask, utc);
};
//# sourceMappingURL=libs.bundle.map