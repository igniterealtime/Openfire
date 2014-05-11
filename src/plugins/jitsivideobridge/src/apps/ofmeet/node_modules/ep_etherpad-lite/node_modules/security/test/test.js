var Security = require('../');
var assert = require('assert');

describe("escapeHTML", function () {
  it('should work', function () {
    assert.equal(Security.escapeHTML("&<>\"'/"), "&amp;&lt;&gt;&quot;&#x27;&#x2F;");
  });
  it('should double encode', function () {
    assert.equal(Security.escapeHTML("&amp;"), "&amp;amp;");
  });
});

describe("escapeHTMLAttribute", function () {
  it('should work', function () {
    assert.equal(Security.escapeHTMLAttribute("\n\t\""), "&#x0a;&#x09;&quot;");
    assert.equal(Security.escapeHTMLAttribute(
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
    , "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
  });
});

describe("encodeJavaScriptString", function () {
  it('should work', function () {
    assert.equal(Security.encodeJavaScriptString("\n\t\"\u2028\u2029"), "\"\\u000a\\u0009\\u0022\\u2028\\u2029\"");
    assert.equal(Security.encodeJavaScriptString(
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
    , "\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"");
  });
});

describe("encodeJavaScriptData", function () {
  it('should work', function () {
    assert.equal(Security.encodeJavaScriptData({"Funny\nKey": ["Funny\nValue"]}), "{\"Funny\\u000aKey\":[\"Funny\\u000aValue\"]}");
  });
});

describe("encodeCSSString", function () {
  it('should work', function () {
    assert.equal(Security.encodeCSSString("\n\t\""), "\"\\00000a\\000009\\000022\"");
    assert.equal(Security.encodeCSSString(
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
    , "\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"");
  });
});
