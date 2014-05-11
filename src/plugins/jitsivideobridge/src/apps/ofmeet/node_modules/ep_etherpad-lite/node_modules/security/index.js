/*!

  Copyright (c) 2011 Chad Weider

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

*/

var HTML_ENTITY_MAP = {
  '&': '&amp;'
, '<': '&lt;'
, '>': '&gt;'
, '"': '&quot;'
, "'": '&#x27;'
, '/': '&#x2F;'
};

// OSWASP Guidlines: &, <, >, ", ' plus forward slash.
var HTML_CHARACTERS_EXPRESSION = /[&"'<>\/]/gm;
function escapeHTML(text) {
  return text && text.replace(HTML_CHARACTERS_EXPRESSION, function (c) {
    return HTML_ENTITY_MAP[c] || c;
  });
}

// OSWASP Guidlines: escape all non alphanumeric characters in ASCII space.
var HTML_ATTRIBUTE_CHARACTERS_EXPRESSION =
    /[\x00-\x2F\x3A-\x40\x5B-\x60\x7B-\xFF]/gm;
function escapeHTMLAttribute(text) {
  return text && text.replace(HTML_ATTRIBUTE_CHARACTERS_EXPRESSION, function (c) {
    return HTML_ENTITY_MAP[c] || "&#x" + ('00' + c.charCodeAt(0).toString(16)).slice(-2) + ";";
  });
};

// OSWASP Guidlines: escape all non alphanumeric characters in ASCII space.
// Also include line breaks (for literal).
var JAVASCRIPT_CHARACTERS_EXPRESSION =
    /[\x00-\x2F\x3A-\x40\x5B-\x60\x7B-\xFF\u2028\u2029]/gm;
function encodeJavaScriptIdentifier(text) {
  return text && text.replace(JAVASCRIPT_CHARACTERS_EXPRESSION, function (c) {
    return "\\u" + ('0000' + c.charCodeAt(0).toString(16)).slice(-4);
  });
}
function encodeJavaScriptString(text) {
  return text && '"' + encodeJavaScriptIdentifier(text) + '"';
}

// This is not great, but it is useful.
var JSON_STRING_LITERAL_EXPRESSION =
    /"(?:\\.|[^"])*"/gm;
function encodeJavaScriptData(object) {
  return JSON.stringify(object).replace(JSON_STRING_LITERAL_EXPRESSION, function (string) {
    return encodeJavaScriptString(JSON.parse(string));
  });
}


// OSWASP Guidlines: escape all non alphanumeric characters in ASCII space.
var CSS_CHARACTERS_EXPRESSION =
    /[\x00-\x2F\x3A-\x40\x5B-\x60\x7B-\xFF]/gm;
function encodeCSSIdentifier(text) {
  return text && text.replace(CSS_CHARACTERS_EXPRESSION, function (c) {
    return "\\" + ('000000' + c.charCodeAt(0).toString(16)).slice(-6);
  });
}

function encodeCSSString(text) {
  return text && '"' + encodeCSSIdentifier(text) + '"';
}

exports.escapeHTML = escapeHTML;
exports.escapeHTMLAttribute = escapeHTMLAttribute;

exports.encodeJavaScriptIdentifier = encodeJavaScriptIdentifier;
exports.encodeJavaScriptString = encodeJavaScriptString;
exports.encodeJavaScriptData = encodeJavaScriptData;

exports.encodeCSSIdentifier = encodeCSSIdentifier;
exports.encodeCSSString = encodeCSSString;
